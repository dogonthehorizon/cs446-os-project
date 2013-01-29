package sos;

import java.util.*;

/**
 * This class contains the simulated operating system (SOS). Realistically it
 * would run on the same processor (CPU) that it is managing but instead it uses
 * the real-world processor in order to allow a focus on the essentials of
 * operating system design using a high level programming language.
 * 
 * @author Fernando Freire
 * @author Carl Lulay
 * 
 */

public class SOS {
	// ======================================================================
	// Member variables
	// ----------------------------------------------------------------------

	/**
	 * This flag causes the SOS to print lots of potentially helpful status
	 * messages
	 **/
	public static final boolean m_verbose = false;

	/**
	 * The CPU the operating system is managing.
	 **/
	private CPU m_CPU = null;

	/**
	 * The RAM attached to the CPU.
	 **/
	private RAM m_RAM = null;

	/*
	 * ======================================================================
	 * Constructors & Debugging
	 * ----------------------------------------------------------------------
	 */

	/**
	 * The constructor does nothing special
	 */
	public SOS(CPU c, RAM r) {
		// Init member list
		m_CPU = c;
		m_RAM = r;
	}// SOS ctor

	/**
	 * Does a System.out.print as long as m_verbose is true
	 **/
	public static void debugPrint(String s) {
		if (m_verbose) {
			System.out.print(s);
		}
	}

	/**
	 * Does a System.out.println as long as m_verbose is true
	 **/
	public static void debugPrintln(String s) {
		if (m_verbose) {
			System.out.println(s);
		}
	}

	/*
	 * ======================================================================
	 * Memory Block Management Methods
	 * ----------------------------------------------------------------------
	 */

	// None yet!

	/*
	 * ======================================================================
	 * Device Management Methods
	 * ----------------------------------------------------------------------
	 */

	// None yet!

	/*
	 * ======================================================================
	 * Process Management Methods
	 * ----------------------------------------------------------------------
	 */

	// None yet!

	/*
	 * ======================================================================
	 * Program Management Methods
	 * ----------------------------------------------------------------------
	 */

	// insert method header here
	public void createProcess(Program prog, int allocSize) {
		int[] inst = prog.export();

		m_CPU.setBASE(0);
		m_CPU.setLIM(m_CPU.getBASE() + allocSize);

		int addr = m_CPU.getBASE();
		for (int i : inst) {
			addr++;
			m_RAM.write(addr, i);
		}

		m_CPU.setPC(m_CPU.getBASE() + 1);
		m_CPU.setSP(allocSize - (allocSize / 3));
	}// createProcess

	/*
	 * ======================================================================
	 * Interrupt Handlers
	 * ----------------------------------------------------------------------
	 */

	// None yet!

	/*
	 * ======================================================================
	 * System Calls
	 * ----------------------------------------------------------------------
	 */

	// None yet!

};// class SOS
