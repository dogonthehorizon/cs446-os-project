package sos;

import java.util.*;

/**
 * @author Dr. Nuxoll
 * @author Robert Rodriguez
 * @author Emilia Holbik
 * @version 2/20/2013
 * 
 * This class is the centerpiece of a simulation of the essential
 * hardware of a microcomputer. This includes a processor chip, RAM and
 * I/O devices. It is designed to demonstrate a simulated operating
 * system (SOS).
 * 
 * @see RAM
 * @see SOS
 * @see Program
 * @see Sim
 */

public class CPU implements Runnable
{

    // ======================================================================
    // Constants
    // ----------------------------------------------------------------------

    // These constants define the instructions available on the chip
    public static final int SET = 0; /* set value of reg */
    public static final int ADD = 1; // put reg1 + reg2 into reg3
    public static final int SUB = 2; // put reg1 - reg2 into reg3
    public static final int MUL = 3; // put reg1 * reg2 into reg3
    public static final int DIV = 4; // put reg1 / reg2 into reg3
    public static final int COPY = 5; // copy reg1 to reg2
    public static final int BRANCH = 6; // goto address in reg
    public static final int BNE = 7; // branch if not equal
    public static final int BLT = 8; // branch if less than
    public static final int POP = 9; // load value from stack
    public static final int PUSH = 10; // save value to stack
    public static final int LOAD = 11; // load value from heap
    public static final int SAVE = 12; // save value to heap
    public static final int TRAP = 15; // system call

    // These constants define the indexes to each register
    public static final int R0 = 0; // general purpose registers
    public static final int R1 = 1;
    public static final int R2 = 2;
    public static final int R3 = 3;
    public static final int R4 = 4;
    public static final int PC = 5; // program counter
    public static final int SP = 6; // stack pointer
    public static final int BASE = 7; // bottom of currently accessible RAM
    public static final int LIM = 8; // top of accessible RAM
    public static final int NUMREG = 9; // number of registers

    // Misc constants
    public static final int NUMGENREG = PC; // the number of general registers
    public static final int INSTRSIZE = 4; // number of ints in a single instr+
                                           // args. (Set to a fixed value for
                                           // simplicity.)
    public static final int INTSIZE = 1; // the size representation of an int

    // ======================================================================
    // Member variables
    // ----------------------------------------------------------------------
    
    /**
     * specifies whether the CPU should output details of its work
     **/
    private boolean m_verbose = false;

    /**
     * This array contains all the registers on the "chip".
     **/
    private int m_registers[];

    /**
     * A pointer to the RAM used by this CPU
     * 
     * @see RAM
     **/
    private RAM m_RAM = null;
    
    /**
     * A pointer to the Interrupt Controller used by this CPU.
     */
    
    private InterruptController m_IC = null;

    //======================================================================
    //Callback Interface
    //----------------------------------------------------------------------
    
    /**
     * TrapHandler
     *
     * This interface should be implemented by the operating system to allow 
     * the simulated CPU to generate hardware interrupts and system calls.
     */
    public interface TrapHandler
    {
        void interruptIllegalMemoryAccess(int addr);
        void interruptDivideByZero();
        void interruptIllegalInstruction(int[] instr);
        void systemCall();
        
        //======================================================================
        //Callback Interface
        //----------------------------------------------------------------------
        
            public void interruptIOReadComplete(int devID, int addr, int data);
            public void interruptIOWriteComplete(int devID, int addr);

        
    };//interface TrapHandler
    
    /**
     * a reference to the trap handler for this CPU.  On a real CPU this would
     * simply be an address that the PC register is set to.
     */
    private TrapHandler m_TH = null;

    /**
     * registerTrapHandler
     *
     * allows SOS to register itself as the trap handler 
     */
    public void registerTrapHandler(TrapHandler th)
    {
        m_TH = th;
    }
    
    // ======================================================================
    // Methods
    // ----------------------------------------------------------------------

    /**
     * CPU ctor
     * 
     * Initializes all member variables.
     */
    public CPU(RAM ram, InterruptController ic)
    {
        m_registers = new int[NUMREG];
        for (int i = 0; i < NUMREG; i++)
        {
            m_registers[i] = 0;
        }
        m_RAM = ram;
        m_IC = ic;
    }// CPU ctor

    /**
     * getPC
     * 
     * @return the value of the program counter
     */
    public int getPC()
    {
        return m_registers[PC];
    }

    /**
     * getSP
     * 
     * @return the value of the stack pointer
     */
    public int getSP()
    {
        return m_registers[SP];
    }

    /**
     * getBASE
     * 
     * @return the value of the base register
     */
    public int getBASE()
    {
        return m_registers[BASE];
    }

    /**
     * getLIMIT
     * 
     * @return the value of the limit register
     */
    public int getLIM()
    {
        return m_registers[LIM];
    }

    /**
     * getRegisters
     * 
     * @return the registers
     */
    public int[] getRegisters()
    {
        return m_registers;
    }

    /**
     * setPC
     * 
     * @param v
     *            the new value of the program counter
     */
    public void setPC(int v)
    {
        m_registers[PC] = v;
    }

    /**
     * setSP
     * 
     * @param v
     *            the new value of the stack pointer
     */
    public void setSP(int v)
    {
        m_registers[SP] = v;
    }

    /**
     * setBASE
     * 
     * @param v
     *            the new value of the base register
     */
    public void setBASE(int v)
    {
        m_registers[BASE] = v;
    }

    /**
     * setLIM
     * 
     * @param v
     *            the new value of the limit register
     */
    public void setLIM(int v)
    {
        m_registers[LIM] = v;
    }


    /**
     * regDump
     * 
     * Prints the values of the registers. Useful for debugging.
     */
    public void regDump()
    {
        for (int i = 0; i < NUMGENREG; i++)
        {
            System.out.print("r" + i + "=" + m_registers[i] + " ");
        }// for
        System.out.print("PC=" + m_registers[PC] + " ");
        System.out.print("SP=" + m_registers[SP] + " ");
        System.out.print("BASE=" + m_registers[BASE] + " ");
        System.out.print("LIM=" + m_registers[LIM] + " ");
        System.out.println("");
    }// regDump

    /**
     * printIntr
     * 
     * Prints a given instruction in a user readable format. Useful for
     * debugging.
     * 
     * @param instr
     *            the current instruction
     */
    public void printInstr(int[] instr)
    {
        switch (instr[0])
        {
            case SET:
                System.out.println("SET R" + instr[1] + " = " + instr[2]);
                break;
            case ADD:
                System.out.println("ADD R" + instr[1] + " = R" + instr[2]
                        + " + R" + instr[3]);
                break;
            case SUB:
                System.out.println("SUB R" + instr[1] + " = R" + instr[2]
                        + " - R" + instr[3]);
                break;
            case MUL:
                System.out.println("MUL R" + instr[1] + " = R" + instr[2]
                        + " * R" + instr[3]);
                break;
            case DIV:
                System.out.println("DIV R" + instr[1] + " = R" + instr[2]
                        + " / R" + instr[3]);
                break;
            case COPY:
                System.out.println("COPY R" + instr[1] + " = R" + instr[2]);
                break;
            case BRANCH:
                System.out.println("BRANCH @" + instr[1]);
                break;
            case BNE:
                System.out.println("BNE (R" + instr[1] + " != R" + instr[2]
                        + ") @" + instr[3]);
                break;
            case BLT:
                System.out.println("BLT (R" + instr[1] + " < R" + instr[2]
                        + ") @" + instr[3]);
                break;
            case POP:
                System.out.println("POP R" + instr[1]);
                break;
            case PUSH:
                System.out.println("PUSH R" + instr[1]);
                break;
            case LOAD:
                System.out.println("LOAD R" + instr[1] + " <-- @R" + instr[2]);
                break;
            case SAVE:
                System.out.println("SAVE R" + instr[1] + " --> @R" + instr[2]);
                break;
            case TRAP:
                System.out.println("TRAP ");
                break;
            default: // should never be reached
                m_TH.interruptIllegalInstruction(instr);
                break;
        }// switch
    }// printInstr

    /**
     * pop
     * 
     * Helper method that pops a value off the stack, as long as the stack
     * contains at least one valid value.
     * 
     * @return the popped value
     */
    public int pop()
    {
        int returnVal = 0;
        
        //Checks to make sure that the stack has at least something on it
        if (checkMemBounds(m_registers[SP] + INTSIZE))
        {
            returnVal = m_RAM.read(m_registers[SP]);
            m_registers[SP] = m_registers[SP] + INTSIZE;
        }
        return returnVal;
    }

    /**
     * push
     * 
     * Helper method that pushes a value onto the stack as long as the stack
     * pointer is within the base and limit registers.
     * 
     * @param pushVal
     *            the integer to push onto the stack
     */
    public void push(int pushVal)
    {
        //Before decrement the stack pointer, check to see if that address
        //is within the program's allocated address bounds.
        if (checkMemBounds(m_registers[SP] - INTSIZE))
        {
            m_registers[SP] = m_registers[SP] - INTSIZE;
            m_RAM.write(m_registers[SP], pushVal);
        }
    }

    /**
     * checkMemBounds
     * 
     * Helper method which checks whether the memory address parameter is
     * located within the base and limit registers. If the address is out of
     * bounds, print an error message.
     * 
     * @param address
     *            the memory address to test
     * @return true if the address is >= to the base register and < the limit
     *         register; otherwise it returns false
     */
    public boolean checkMemBounds(int address)
    {
        if (address < m_registers[BASE]
                || address >= (m_registers[LIM] + m_registers[BASE]))
        {
            m_TH.interruptIllegalMemoryAccess(address);
            return false;
        } else
            return true;
    }

    /**
     * checkForIOInterrupt
     *
     * Checks the databus for signals from the interrupt controller and, if
     * found, invokes the appropriate handler in the operating system.
     *
     */
    private void checkForIOInterrupt()
    {
        //If there is no interrupt to process, do nothing
        if (m_IC.isEmpty())
        {
            return;
        }
        
        //Retreive the interrupt data
        int[] intData = m_IC.getData();

        //Report the data if in verbose mode
        if (m_verbose)
        {
            System.out.println("CPU received interrupt: type=" + intData[0]
                               + " dev=" + intData[1] + " addr=" + intData[2]
                               + " data=" + intData[3]);
        }

        //Dispatch the interrupt to the OS
        switch(intData[0])
        {
            case InterruptController.INT_READ_DONE:
                m_TH.interruptIOReadComplete(intData[1], intData[2], intData[3]);
                break;
            case InterruptController.INT_WRITE_DONE:
                m_TH.interruptIOWriteComplete(intData[1], intData[2]);
                break;
            default:
                System.out.println("CPU ERROR:  Illegal Interrupt Received.");
                System.exit(-1);
                break;
        }//switch

    }//checkForIOInterrupt

    /**
     * run
     * 
     * Continually fetches the next instruction from RAM using the PC register
     * and decodes and executes those instructions.
     */
    public void run()
    {
        // Loop executes until Pidgin compiler finds "TRAP" command
        while (true)
        {
        	checkForIOInterrupt();
        	
            int instr[] = m_RAM.fetch(m_registers[PC]);

            // Print extra debugging statements upon request
            if (m_verbose)
            {
                regDump();
                printInstr(instr);
            }

            switch (instr[0])
            {
                case SET:
                    m_registers[instr[1]] = instr[2];
                    break;
                case ADD:
                    m_registers[instr[1]] = m_registers[instr[2]]
                            + m_registers[instr[3]];
                    break;
                case SUB:
                    m_registers[instr[1]] = m_registers[instr[2]]
                            - m_registers[instr[3]];
                    break;
                case MUL:
                    m_registers[instr[1]] = m_registers[instr[2]]
                            * m_registers[instr[3]];
                    break;
                case DIV:
                    if (m_registers[instr[3]] != 0)
                    m_registers[instr[1]] = m_registers[instr[2]]
                            / m_registers[instr[3]];
                    else {
                        m_TH.interruptDivideByZero();
                        return;
                    }
                    break;
                case COPY:
                    m_registers[instr[1]] = m_registers[instr[2]];
                    break;
                case BRANCH:
                    // Update the program counter to execute the given
                    // instruction; offset the program counter by -INSTRSIZE to
                    // offset the addition of INSTRSIZE after the switch
                    if(checkMemBounds(m_registers[BASE] + instr[1]))
                        m_registers[PC] = m_registers[BASE] + instr[1] - 
                            INSTRSIZE;
                    else return;
                    break;
                case BNE:
                    // Update the program counter if arguments 2 and 3 are
                    // not equal
                    if (m_registers[instr[1]] != m_registers[instr[2]]) {
                        if(checkMemBounds(m_registers[BASE] + instr[3]))
                            m_registers[PC] = m_registers[BASE] + instr[3]
                                - INSTRSIZE;
                        else return;
                    }                    
                    break;
                case BLT:
                    // Update program counter if argument 2 < argument 3
                    if (m_registers[instr[1]] < m_registers[instr[2]]) {
                        if(checkMemBounds(m_registers[BASE] + instr[3]))
                            m_registers[PC] = m_registers[BASE] + instr[3]
                                - INSTRSIZE;
                        else return;
                    }                    
                    break;
                case POP:
                    m_registers[instr[1]] = pop();
                    break;
                case PUSH:
                    int val = m_registers[instr[1]];
                    push(val);
                    break;
                case LOAD:
                    if (checkMemBounds(m_registers[BASE] + 
                            m_registers[instr[2]]))
                        m_registers[instr[1]] = m_RAM.read(m_registers[BASE]
                                + m_registers[instr[2]]);
                    else return;
                    break;
                case SAVE:
                    if (checkMemBounds(m_registers[BASE] + 
                            m_registers[instr[2]]))
                        m_RAM.write(m_registers[BASE] + m_registers[instr[2]], 
                                m_registers[instr[1]]);
                    else return;
                    break;
                case TRAP:
                    m_TH.systemCall();
                    break;
                default: // should never be reached
                    m_TH.interruptIllegalInstruction(instr);
                    break;
            }// switch
             // Increment the program counter by the size of one instruction
            m_registers[PC] = m_registers[PC] + INSTRSIZE;
        }// while
    }// run

};// class CPU
