package sos;

import java.util.*;

/**
 * This class is the centerpiece of a simulation of the essential hardware of a
 * microcomputer. This includes a processor chip, RAM and I/O devices. It is
 * designed to demonstrate a simulated operating system (SOS).
 * 
 * @see RAM
 * @see SOS
 * @see Program
 * @see Sim
 * 
 * @author Vincent Clasgens, Aaron Dobbe, Fernando Freire
 */

public class CPU {

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
	public static final int INSTRSIZE = 4; // number of ints in a single instr +
											// args. (Set to a fixed value for
											// simplicity.)
	
    //These constants define the system calls this OS can currently handle
    public static final int SYSCALL_EXIT     = 0;    /* exit the current program */
    public static final int SYSCALL_OUTPUT   = 1;    /* outputs a number */
    public static final int SYSCALL_GETPID   = 2;    /* get current process id */
    public static final int SYSCALL_COREDUMP = 9;    /* print process state and exit */

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

	// ======================================================================
	// Methods
	// ----------------------------------------------------------------------

	/**
	 * CPU ctor
	 * 
	 * Intializes all member variables.
	 */
	public CPU(RAM ram) {
		m_registers = new int[NUMREG];
		for (int i = 0; i < NUMREG; i++) {
			m_registers[i] = 0;
		}
		m_RAM = ram;

	}// CPU ctor

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
        void systemCall();
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
	
	/**
	 * getPC
	 * 
	 * @return the value of the program counter
	 */
	public int getPC() {
		return m_registers[PC];
	}

	/**
	 * getSP
	 * 
	 * @return the value of the stack pointer
	 */
	public int getSP() {
		return m_registers[SP];
	}

	/**
	 * getBASE
	 * 
	 * @return the value of the base register
	 */
	public int getBASE() {
		return m_registers[BASE];
	}

	/**
	 * getLIMIT
	 * 
	 * @return the value of the limit register
	 */
	public int getLIM() {
		return m_registers[LIM];
	}

	/**
	 * getRegisters
	 * 
	 * @return the registers
	 */
	public int[] getRegisters() {
		return m_registers;
	}

	/**
	 * setPC
	 * 
	 * @param v
	 *            the new value of the program counter
	 */
	public void setPC(int v) {
		m_registers[PC] = v;
	}

	/**
	 * setSP
	 * 
	 * @param v
	 *            the new value of the stack pointer
	 */
	public void setSP(int v) {
		m_registers[SP] = v;
	}

	/**
	 * setBASE
	 * 
	 * @param v
	 *            the new value of the base register
	 */
	public void setBASE(int v) {
		m_registers[BASE] = v;
	}

	/**
	 * setLIM
	 * 
	 * @param v
	 *            the new value of the limit register
	 */
	public void setLIM(int v) {
		m_registers[LIM] = v;
	}

	/**
	 * regDump
	 * 
	 * Prints the values of the registers. Useful for debugging.
	 */
	public void regDump() {
		for (int i = 0; i < NUMGENREG; i++) {
			System.out.print("r" + i + "=" + m_registers[i] + " ");
		}// for
		System.out.print("PC=" + m_registers[PC] + " ");
		System.out.print("SP=" + m_registers[SP] + " ");
		System.out.print("BASE=" + m_registers[BASE] + " ");
		System.out.print("LIM=" + m_registers[LIM] + " ");
		System.out.println("");
	}// regDump

	/**
	 * printInstr
	 * 
	 * Prints a given instruction in a user readable format. Useful for
	 * debugging.
	 * 
	 * @param instr
	 *            the current instruction
	 */
	public void printInstr(int[] instr) {
		switch (instr[0]) {
		case SET:
			System.out.println("SET R" + instr[1] + " = " + instr[2]);
			break;
		case ADD:
			System.out.println("ADD R" + instr[1] + " = R" + instr[2] + " + R"
					+ instr[3]);
			break;
		case SUB:
			System.out.println("SUB R" + instr[1] + " = R" + instr[2] + " - R"
					+ instr[3]);
			break;
		case MUL:
			System.out.println("MUL R" + instr[1] + " = R" + instr[2] + " * R"
					+ instr[3]);
			break;
		case DIV:
			System.out.println("DIV R" + instr[1] + " = R" + instr[2] + " / R"
					+ instr[3]);
			break;
		case COPY:
			System.out.println("COPY R" + instr[1] + " = R" + instr[2]);
			break;
		case BRANCH:
			System.out.println("BRANCH @" + instr[1]);
			break;
		case BNE:
			System.out.println("BNE (R" + instr[1] + " != R" + instr[2] + ") @"
					+ instr[3]);
			break;
		case BLT:
			System.out.println("BLT (R" + instr[1] + " < R" + instr[2] + ") @"
					+ instr[3]);
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
			System.out.print("TRAP ");
			break;
		default: // should never be reached
			System.out.println("?? ");
			break;
		}// switch

	}// printInstr

	/**
	 * pop
	 * 
	 * Pops the top register off the stack and returns it
	 * 
	 * @returns The value that was on the top of the stack
	 */
	private int pop() {
		checkLimit(getBASE() + getSP() + 1);
		int stackTop = m_RAM.read(getBASE()+getSP());

		// decrement the stack pointer
		setSP(getSP() + 1);

		return stackTop;
	}

	/**
	 * push
	 * 
	 * Increments the stack and pushes a value to it
	 * 
	 * @param the
	 *            value to be pushed to RAM
	 */
	private void push(int toPush) {
		checkLimit(getBASE() + getSP() - 1);

		// increment the stack pointer
		setSP(getSP() - 1);

		m_RAM.write(getBASE() + getSP(), toPush);
	}

	/**
	 * checkLimit
	 * 
     * ensures the given address is in legal memory
     * calls a interrupt if it is not
     *
	 * @param the address to check the bounds of
	 *
	 */
	private void checkLimit(int addr) {

		if (addr < getBASE()) {
			m_TH.interruptIllegalMemoryAccess(addr);
		} else if (getBASE() + getLIM() < addr) {
			m_TH.interruptIllegalMemoryAccess(addr);
		}
	}

	/**
     * run
     *
     * Determines which instruction to execute based on the 
     * contents of RAM at the appropriate PC location
     *
     */
	public void run() {
		while (getPC() < getBASE()) {
			
			// Fetch the next instruction and increment the PC
			int[] instr = m_RAM.fetch(getBASE() + getPC());
			setPC(getPC() + INSTRSIZE);

			// print debugging info if we are in verbose mode
			if (m_verbose == true) {
				regDump();
				printInstr(instr);
			}

			// execute the instruction
			switch (instr[0]) {
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
				if (m_registers[instr[3]] == 0) {
					m_TH.interruptDivideByZero();
				}
				m_registers[instr[1]] = m_registers[instr[2]]
						/ m_registers[instr[3]];
				break;
			case COPY:
				m_registers[instr[1]] = m_registers[instr[2]];
				break;
			case BRANCH:
				checkLimit(instr[1]+getBASE());
				setPC(instr[1]);
				break;
			case BNE:
				checkLimit(instr[3]+getBASE());
				if (m_registers[instr[1]] != m_registers[instr[2]]) {
					setPC(instr[3]);
				}
				break;
			case BLT:
				checkLimit(instr[3]+getBASE());
				if (m_registers[instr[1]] < m_registers[instr[2]]) {
					setPC(instr[3]);
				}
				break;
			case POP:
				m_registers[instr[1]] = pop();
				break;
			case PUSH:
				push(m_registers[instr[1]]);
				break;
			case LOAD:
				// make sure the register to load is in bounds
				checkLimit(m_registers[instr[2]] + getBASE());
				m_registers[instr[1]] = m_RAM.read(m_registers[instr[2]]
						+ getBASE());
				break;
			case SAVE:
				// make sure the register to save is in bounds
				checkLimit(m_registers[instr[2]] + getBASE());
				
				m_RAM.write(m_registers[instr[2]] + getBASE(),
						m_registers[instr[1]]);
				break;
			case TRAP:
				m_TH.systemCall();
				break;
			default: // should never be reached
				System.out.println("?? ");
				break;
			}// switch
		}// while
	}// run
	

};// class CPU
