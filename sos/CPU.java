package sos;

import java.util.*;

/**
 * This class is the centerpiece of a simulation of the essential hardware of a
 * microcomputer.  This includes a processor chip, RAM and I/O devices.  It is
 * designed to demonstrate a simulated operating system (SOS).
 * 
 * @author Preben Ingvaldsen
 * @author Et Begert
 * @author Aaron Dobbe
 * @author Kyle DeFrancia
 * @author Daniel Jones
 * @author Raphael Ramos
 * @author Scott Matsuo
 *
 * @version March 23, 2013
 * 
 * @see RAM
 * @see SOS
 * @see Program
 * @see Sim
 */

public class CPU implements Runnable
{

	//======================================================================
	//Constants
	//----------------------------------------------------------------------

	//These constants define the instructions available on the chip
	public static final int SET    = 0;    /* set value of reg */
	public static final int ADD    = 1;    // put reg1 + reg2 into reg3
	public static final int SUB    = 2;    // put reg1 - reg2 into reg3
	public static final int MUL    = 3;    // put reg1 * reg2 into reg3
	public static final int DIV    = 4;    // put reg1 / reg2 into reg3
	public static final int COPY   = 5;    // copy reg1 to reg2
	public static final int BRANCH = 6;    // goto address in reg
	public static final int BNE    = 7;    // branch if not equal
	public static final int BLT    = 8;    // branch if less than
	public static final int POP    = 9;    // load value from stack
	public static final int PUSH   = 10;   // save value to stack
	public static final int LOAD   = 11;   // load value from heap
	public static final int SAVE   = 12;   // save value to heap
	public static final int TRAP   = 15;   // system call

	//These constants define the indexes to each register
	public static final int R0   = 0;     // general purpose registers
	public static final int R1   = 1;
	public static final int R2   = 2;
	public static final int R3   = 3;
	public static final int R4   = 4;
	public static final int PC   = 5;     // program counter
	public static final int SP   = 6;     // stack pointer
	public static final int BASE = 7;     // bottom of currently accessible RAM
	public static final int LIM  = 8;     // top of accessible RAM
	public static final int NUMREG = 9;   // number of registers

	//Misc constants
	public static final int NUMGENREG = PC; // the number of general registers
	public static final int INSTRSIZE = 4;  // number of ints in a single instr +
	// args.  (Set to a fixed value for simplicity.)
	public static final int CLOCK_FREQ = 5; // Number of cycles between clock interrupts

	//======================================================================
	//Member variables
	//----------------------------------------------------------------------
	/**
	 * specifies whether the CPU should output details of its work
	 **/
	private boolean m_verbose = true;

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
	
	private InterruptController m_IC = null;
	
	// Number of ticks that have passed during the simulation
	private int m_ticks = 0;

	//======================================================================
	//Methods
	//----------------------------------------------------------------------

	/**
	 * CPU ctor
	 *
	 * Intializes all member variables.
	 */
	public CPU(RAM ram, InterruptController initIC)
	{
		m_registers = new int[NUMREG];
		for(int i = 0; i < NUMREG; i++)
		{
			m_registers[i] = 0;
		}
		m_RAM = ram;
		m_IC = initIC;

	}//CPU ctor

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
	 * getTicks
	 * 
	 * @return the number of ticks that have passed for this simulation
	 */
	public int getTicks()
	{
	    return m_ticks;
	}

	/**
	 * setPC
	 *
	 * @param v the new value of the program counter
	 */
	public void setPC(int v)
	{
		m_registers[PC] = v;
	}

	/**
	 * setSP
	 *
	 * @param v the new value of the stack pointer
	 */
	public void setSP(int v)
	{
		m_registers[SP] = v;
	}

	/**
	 * setBASE
	 *
	 * @param v the new value of the base register
	 */
	public void setBASE(int v)
	{
		m_registers[BASE] = v;
	}

	/**
	 * setLIM
	 *
	 * @param v the new value of the limit register
	 */
	public void setLIM(int v)
	{
		m_registers[LIM] = v;
	}
	
	/**
	 * addTicks()
	 * 
	 * adds the given number of CPU ticks to the current total
	 * @param amount  the number of cycles to add
	 */
	public void addTicks(int amount)
	{
	    m_ticks += amount;
	}

	/**
	 * regDump
	 *
	 * Prints the values of the registers.  Useful for debugging.
	 */
	public void regDump()
	{
		for(int i = 0; i < NUMGENREG; i++)
		{
			System.out.print("r" + i + "=" + m_registers[i] + " ");
		}//for
		System.out.print("PC=" + m_registers[PC] + " ");
		System.out.print("SP=" + m_registers[SP] + " ");
		System.out.print("BASE=" + m_registers[BASE] + " ");
		System.out.print("LIM=" + m_registers[LIM] + " ");
		System.out.println("");
	}//regDump

	/**
	 * printIntr
	 *
	 * Prints a given instruction in a user readable format.  Useful for
	 * debugging.
	 *
	 * @param instr the current instruction
	 */
	public void printInstr(int[] instr)
	{
		switch(instr[0])
		{
		case SET:
			System.out.println("SET R" + instr[1] + " = " + instr[2]);
			break;
		case ADD:
			System.out.println("ADD R" + instr[1] + " = R" + instr[2] + " + R" + instr[3]);
			break;
		case SUB:
			System.out.println("SUB R" + instr[1] + " = R" + instr[2] + " - R" + instr[3]);
			break;
		case MUL:
			System.out.println("MUL R" + instr[1] + " = R" + instr[2] + " * R" + instr[3]);
			break;
		case DIV:
			System.out.println("DIV R" + instr[1] + " = R" + instr[2] + " / R" + instr[3]);
			break;
		case COPY:
			System.out.println("COPY R" + instr[1] + " = R" + instr[2]);
			break;
		case BRANCH:
			System.out.println("BRANCH @" + instr[1]);
			break;
		case BNE:
			System.out.println("BNE (R" + instr[1] + " != R" + instr[2] + ") @" + instr[3]);
			break;
		case BLT:
			System.out.println("BLT (R" + instr[1] + " < R" + instr[2] + ") @" + instr[3]);
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
		default:        // should never be reached
			System.out.println("?? ");
			break;          
		}//switch

	}//printInstr


	/**
	 * run
	 * 
	 * Runs the program that has been loaded into RAM
	 * 
	 */
	public void run()
	{	
		int[] instruction = new int[4];
		while (true) {
            
		    // Check the data bus for IO signals
		    checkForIOInterrupt();

			//fetch the program instruction from RAM using the PC
			instruction = m_RAM.fetch(getPC());

			if(m_verbose) {
				regDump();
				printInstr(instruction);
			}

			//determine how to handle the fetched instruction
			switch(instruction[0]) {
			case SET:
				m_registers[instruction[1]] = instruction[2];
				break;
			case ADD:
				m_registers[instruction[1]] = 
					m_registers[instruction[2]] + m_registers[instruction[3]];
				break;
			case SUB:
				m_registers[instruction[1]] = 
					m_registers[instruction[2]] - m_registers[instruction[3]];
				break;
			case MUL:
					m_registers[instruction[1]] = 
					m_registers[instruction[2]] * m_registers[instruction[3]];
				break;
			case DIV:
				if(m_registers[instruction[3]] == 0){
					m_TH.interruptDivideByZero();
				}
				m_registers[instruction[1]] =
					m_registers[instruction[2]] / m_registers[instruction[3]];
				break;
			case COPY:
				m_registers[instruction[1]] = m_registers[instruction[2]];
				break;
			case BRANCH:
				//if the address is invalid, display an error message and exit the program
				isValidAddress(instruction[1]);
				//change the program counter to the desired address
				setPC(instruction[1] + getBASE() - 4);
				break;
			case BNE:
				if (m_registers[instruction[1]] != m_registers[instruction[2]]) {
					//if the address is invalid, display an error message
					//and exit the program
					isValidAddress(instruction[3]);
					//change the program counter to the desired address
					setPC(instruction[3] + getBASE() - 4);
				}
				break; 
			case BLT:
				if (m_registers[instruction[1]] < m_registers[instruction[2]]) {
					//if the address is invalid, display an error message
					//and exit the program
					isValidAddress(instruction[3]);
					//change the program counter to the desired address
					setPC(instruction[3] + getBASE() - 4);
				}
				break;
			case POP:
				if (!pop(instruction[1])) {
					return;
				}
				break;
			case PUSH:
				if (!push(instruction[1])) {
					return;
				}
				break;
			case LOAD:
				isValidAddress(instruction[2]);
				m_registers[instruction[1]] = m_RAM.read(instruction[2]);
				break;
			case SAVE:
				isValidAddress(instruction[2]);
				m_RAM.write(instruction[2], m_registers[instruction[1]]);
				break;
			case TRAP:
				m_TH.systemCall();
				break;
			default:        //this case should never occur
				m_TH.interruptIllegalInstruction(instruction);
				break;          
			}

			// Increment the number of ticks and check for clock interrupts
            m_ticks++;
            if (m_ticks % CLOCK_FREQ == 0)
            {
                m_TH.interruptClock();
            }
            
			setPC(getPC() + 4);

		}//run

	}

	/**
	 * isValidAddress
	 * 
	 * checks whether a given address is part of the allocated memory
	 * for the program
	 * 
	 * @param address the method is to check
	 * calls an interrupt if the address is invalid; otherwise allows the parent method to continue
	 */
	private void isValidAddress(int address) {
		if(!((address + getBASE() < getLIM())
				|| (address + getBASE() >= getBASE())))
		{
			m_TH.interruptIllegalMemoryAccess(address);
		}
	}
	
	/**
	 * pop
	 * 
	 * pops the value in the specified register off the stack
	 * 
	 * @param register the register whose value to pop off the stack
	 * @return false if the stack is empty; else true
	 */
	private boolean pop(int register) {
		if (getSP() < getLIM() - 1) {
			setSP(getSP() + 1);
			m_registers[register] = m_RAM.read(getSP());
			return true;
		}
		else {
			System.out.println("ERROR: STACK IS EMPTY");
			return false;
		}
	}
	
	/**
	 * push
	 * 
	 * pushes the value in the specified register on the stack
	 * 
	 * @param register the register whose value to put on the stack
	 * @return false if the stack is full, else true
	 */
	private boolean push(int register) {
		if (getSP() >= getBASE()) {
			m_RAM.write(getSP(), m_registers[register]);
			setSP(getSP() - 1);
			return true;
		}
		System.out.println("ERROR: STACK IS FULL");
		return false;
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
	
    //======================================================================
    //Callback Interface
    //----------------------------------------------------------------------
    /**
     * TrapHandler
     *
     * This interface should be implemented by the operating system to allow the
     * simulated CPU to generate hardware interrupts and system calls.
     */
    public interface TrapHandler
    {
        void interruptIllegalMemoryAccess(int addr);
        void interruptDivideByZero();
        void interruptIllegalInstruction(int[] instr);
        void interruptClock();
        void systemCall();
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
    
    
    
    

};//class CPU
