package sos;

import java.util.*;

/**
 * This class contains the simulated operating system (SOS).  Realistically it
 * would run on the same processor (CPU) that it is managing but instead it uses
 * the real-world processor in order to allow a focus on the essentials of
 * operating system design using a high level programming language.
 *
 * @author Vincent Clasgens, Aaron Dobbe
 */
   
public class SOS implements CPU.TrapHandler 
{
	
    //======================================================================
    //Constants
    //----------------------------------------------------------------------

    //These constants define the system calls this OS can currently handle
    public static final int SYSCALL_EXIT     = 0;    /* exit the current program */
    public static final int SYSCALL_OUTPUT   = 1;    /* outputs a number */
    public static final int SYSCALL_GETPID   = 2;    /* get current process id */
    public static final int SYSCALL_COREDUMP = 9;    /* print process state and exit */
    
    //======================================================================
    //Member variables
    //----------------------------------------------------------------------

    /**
     * This flag causes the SOS to print lots of potentially helpful
     * status messages
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

    /*======================================================================
     * Constructors & Debugging
     *----------------------------------------------------------------------
     */
    
    /**
     * The constructor does nothing special
     */
    public SOS(CPU c, RAM r)
    {
        //Init member list
        m_CPU = c;
        m_RAM = r;
        m_CPU.registerTrapHandler(this);
    }//SOS ctor
    
    /**
     * Does a System.out.print as long as m_verbose is true
     **/
    public static void debugPrint(String s)
    {
        if (m_verbose)
        {
            System.out.print(s);
        }
    }
    
    /**
     * Does a System.out.println as long as m_verbose is true
     **/
    public static void debugPrintln(String s)
    {
        if (m_verbose)
        {
            System.out.println(s);
        }
    }
    
    /*======================================================================
     * Memory Block Management Methods
     *----------------------------------------------------------------------
     */

    //None yet!
    
    /*======================================================================
     * Device Management Methods
     *----------------------------------------------------------------------
     */

    //None yet!
    
    /*======================================================================
     * Process Management Methods
     *----------------------------------------------------------------------
     */

    //None yet!
    
    /*======================================================================
     * Program Management Methods
     *----------------------------------------------------------------------
     */

    //insert method header here
    public void createProcess(Program prog, int allocSize)
    {
        int[] prog_instructions = prog.export();
        
        // allocate memory
        int base = 500;
        int limit = allocSize;
        
        for(int i = 0; i < prog_instructions.length; i++)
        {
        	m_RAM.write(base + i, prog_instructions[i]);
        }
        
        m_CPU.setBASE(base);
        m_CPU.setLIM(limit);
        m_CPU.setPC(0);
        m_CPU.setSP(limit);
        
    }//createProcess
        
    /*======================================================================
     * Interrupt Handlers
     *----------------------------------------------------------------------
     */

    public void interruptIllegalMemoryAccess(int addr) {
    	System.err.print("Illegal memory access attempted on PC " + m_CPU.getPC() + " at memory address " + addr);
    	System.exit(0);
    }
    
    public void interruptDivideByZero() {
    	System.err.print("Divide by zero error at PC " + m_CPU.getPC());
    	System.exit(0);
    }
    
    public void interruptIllegalInstruction(int[] instr) {
    	System.err.print("Illegal instruction attempted at PC " + m_CPU.getPC());
    	System.exit(0);
    }
    
    /*======================================================================
     * System Calls
     *----------------------------------------------------------------------
     */
    
    //<insert header comment here>
    public void systemCall()
    {
    	int syscall = popHelper();
        switch(syscall){
        	case SYSCALL_EXIT: syscall_exit();
        		break;
        	case SYSCALL_OUTPUT: syscall_output();
        		break;
        	case SYSCALL_GETPID: syscall_getpid();
        		break;
        	case SYSCALL_COREDUMP: syscall_coredump();
        		break;
        	default:
        		break;
        }
    }
    
    private void syscall_exit () {
    	System.exit(0);
    }
    
    private void syscall_output () {
    	int val = popHelper();
    	System.out.println("OUTPUT: " + val);
    }
    
    private void syscall_getpid () {
    	pushHelper(42);
    }
    
    private void syscall_coredump () {
    	m_CPU.regDump();
    	System.out.println("Top three items on the process stack (descending order): " + popHelper() + " " + popHelper() + " " + popHelper());
    	syscall_exit();
    }
    
    private void pushHelper (int val) {
    	m_CPU.setSP(m_CPU.getSP()-1);
    	m_RAM.write(m_CPU.getBASE() + m_CPU.getSP(), val);
    }
    
    private int popHelper () {
    	int syscallOption = m_RAM.read(m_CPU.getBASE()+m_CPU.getSP());
    	m_CPU.setSP(m_CPU.getSP()+1);
    	return syscallOption;
    }
    
};//class SOS
