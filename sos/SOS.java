package sos;

import java.util.*;

/**
 * @author Dr. Nuxoll
 * @author Robert Rodriguez
 * @author Emilia Holbik
 * @version 2/20/2013
 * 
 * This class contains the simulated operating system (SOS).
 * Realistically it would run on the same processor (CPU) that it is
 * managing but instead it uses the real-world processor in order to
 * allow a focus on the essentials of operating system design using a
 * high level programming language.
 */

public class SOS implements CPU.TrapHandler {
    // ======================================================================
    // Constants
    // ----------------------------------------------------------------------

    /** These constants define the system calls this OS can currently handle */

    /** Exit the current program */
    public static final int SYSCALL_EXIT = 0;

    /** Outputs a number */
    public static final int SYSCALL_OUTPUT = 1;

    /** Get current process id */
    public static final int SYSCALL_GETPID = 2;

    /** access a device */
    public static final int SYSCALL_OPEN = 3;

    /** release a device */
    public static final int SYSCALL_CLOSE = 4;

    /** get input from device */
    public static final int SYSCALL_READ = 5;

    /** send output to device */
    public static final int SYSCALL_WRITE = 6;

    /** spawn a new process */
    public static final int SYSCALL_EXEC = 7;
    
    /** yield the CPU to another process */
    public static final int SYSCALL_YIELD = 8;    

    /** Print process state and exit */
    public static final int SYSCALL_COREDUMP = 9;
    
    /**This process is used as the idle process' id*/
    public static final int IDLE_PROC_ID    = 999; 

    /**
     * Success and error constants to define system call handler routine errors
     */
    public static final int SUCCESS_CODE = 0;
    public static final int ERROR_CODE = -1;
    public static final int ERR_DEV_NOT_EXIST = -2;
    public static final int ERR_DEV_NOT_SHARABLE = -3;
    public static final int ERR_DEV_ALREADY_OPEN = -4;
    public static final int ERR_DEV_NOT_OPEN = -5;
    public static final int ERR_DEV_NOT_WRITEABLE = -6;
    public static final int ERR_DEV_NOT_READABLE = -7;

    /**
     * Offset to ensure that stack pointer does not point to the memory 
     * location BASE + LIMIT, since it is out of bounds.
     */
    public static final int MEM_OFFSET = 1;

    // ======================================================================
    // Member variables
    // ----------------------------------------------------------------------

    /**
     * This flag causes the SOS to print lots of potentially helpful status
     * messages
     **/
    public static final boolean m_verbose = true;

    /**
     * The CPU the operating system is managing.
     **/
    private CPU m_CPU = null;

    /**
     * The RAM attached to the CPU.
     **/
    private RAM m_RAM = null;

    /**
     * This references the information about the current process running 
     * on SOS.
     */
    private ProcessControlBlock m_currProcess = null;

    /**
     * This is a list of the devices installed on SOS.
     */
    private Vector<DeviceInfo> m_devices = null;

    /**
     * A list of currently running programs
     */
    private Vector<Program> m_programs = null;
    
    /**
     * Contains information for where the BASE register of the next program
     * will be located in memory.
     */
    private int m_nextLoadPos = 0;
    
    /**
     * Contains the PID of the next process to be created.
     */
    private int m_nextProcessID = 1001;
    
    /**
     * Contains a list of all of the processes in RAM and in either ready,
     * running, or blocked state.
     */
    private Vector<ProcessControlBlock> m_processes = null;
    
    /*
     * ======================================================================
     * Constructors & Debugging
     * ----------------------------------------------------------------------
     */

    /**
     * The constructor does nothing special
     */
    public SOS(CPU c, RAM r) 
    {
        // Init member list
        m_CPU = c;
        m_RAM = r;
        m_CPU.registerTrapHandler(this);
        //m_currProcess = new ProcessControlBlock(42);
        m_devices = new Vector<DeviceInfo>(0);
        m_programs = new Vector<Program>();
        m_processes = new Vector<ProcessControlBlock>();
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

    // ======================================================================
    // Inner Classes
    // ----------------------------------------------------------------------

    /**
     * class ProcessControlBlock
     * 
     * This class contains information about a currently active process.
     */
    private class ProcessControlBlock {
        /**
         * a unique id for this process
         */
        private int processId = 0;

        /**
         * These are the process' current registers.  If the process is in the
         * "running" state then these are out of date
         */
        private int[] registers = null;

        /**
         * If this process is blocked, a reference to the Device is stored here
         */
        private Device blockedForDevice = null;
        
        /**
         * If this process is blocked, a reference to the type of I/O operation
         * is stored here (use the SYSCALL constants defined in SOS)
         */
        private int blockedForOperation = -1;
        
        /**
         * If this process is blocked reading from a device, the requested
         * address is stored here.
         */
        private int blockedForAddr = -1;
        
        /**
         * constructor
         * 
         * @param pid
         *            a process id for the process. The caller is responsible
         *            for making sure it is unique.
         */
        public ProcessControlBlock(int pid) 
        {
            this.processId = pid;
        }
        
        /**
         * save
         *
         * saves the current CPU registers into this.registers
         *
         * @param cpu  the CPU object to save the values from
         */
        public void save(CPU cpu)
        {
            int[] regs = cpu.getRegisters();
            this.registers = new int[CPU.NUMREG];
            for(int i = 0; i < CPU.NUMREG; i++)
            {
                this.registers[i] = regs[i];
            }
        }//save
         
        /**
         * restore
         *
         * restores the saved values in this.registers to the current CPU's
         * registers
         *
         * @param cpu  the CPU object to restore the values to
         */
        public void restore(CPU cpu)
        {
            int[] regs = cpu.getRegisters();
            for(int i = 0; i < CPU.NUMREG; i++)
            {
                regs[i] = this.registers[i];
            }

        }//restore
         
        /**
         * block
         *
         * blocks the current process to wait for I/O.  This includes saving 
         * the process' registers.   The caller is responsible for calling
         * {@link CPU#scheduleNewProcess} after calling this method.
         *
         * @param cpu   the CPU that the process is running on
         * @param dev   the Device that the process must wait for
         * @param op    the operation that the process is performing on the
         *              device.  Use the SYSCALL constants for this value.
         * @param addr  the address the process is reading from.  If the
         *              operation is a Write or Open then this value can be
         *              anything
         */
        public void block(CPU cpu, Device dev, int op, int addr)
        {
            blockedForDevice = dev;
            blockedForOperation = op;
            blockedForAddr = addr;
            
        }//block
        
        /**
         * unblock
         *
         * moves this process from the Blocked (waiting) state to the Ready
         * state. 
         *
         */
        public void unblock()
        {
            blockedForDevice = null;
            blockedForOperation = -1;
            blockedForAddr = -1;
            
        }//block
        
        /**
         * isBlocked
         *
         * @return true if the process is blocked
         */
        public boolean isBlocked()
        {
            return (blockedForDevice != null);
        }//isBlocked
         
        /**
         * isBlockedForDevice
         *
         * Checks to see if the process is blocked for the given device,
         * operation and address.  If the operation is not an open, the given
         * address is ignored.
         *
         * @param dev   check to see if the process is waiting for this device
         * @param op    check to see if the process is waiting for this 
         *              operation
         * @param addr  check to see if the process is reading from this address
         *
         * @return true if the process is blocked by the given parameters
         */
        public boolean isBlockedForDevice(Device dev, int op, int addr)
        {
            if ( (blockedForDevice == dev) && (blockedForOperation == op) )
            {
                if (op == SYSCALL_OPEN)
                {
                    return true;
                }

                if (addr == blockedForAddr)
                {
                    return true;
                }
            }//if

            return false;
        }//isBlockedForDevice
         
        /**
         * compareTo              
         *
         * compares this to another ProcessControlBlock object based on the 
         * BASE addr register.  Read about Java's Collections class for info on
         * how this method can be quite useful to you.
         */
        public int compareTo(ProcessControlBlock pi)
        {
            return this.registers[CPU.BASE] - pi.registers[CPU.BASE];
        }


        
        /**
         * @return the current process' id
         */
        public int getProcessId() {
            return this.processId;
        }
        
        /**
         * getRegisterValue
         *
         * Retrieves the value of a process' register that is stored in this
         * object (this.registers).
         * 
         * @param idx the index of the register to retrieve.  Use the constants
         *            in the CPU class
         * @return one of the register values stored in in this object or -999
         *         if an invalid index is given 
         */
        public int getRegisterValue(int idx)
        {
            if ((idx < 0) || (idx >= CPU.NUMREG))
            {
                return -999;    // invalid index
            }
            
            return this.registers[idx];
        }//getRegisterValue
         
        /**
         * setRegisterValue
         *
         * Sets the value of a process' register that is stored in this
         * object (this.registers).  
         * 
         * @param idx the index of the register to set.  Use the constants
         *            in the CPU class.  If an invalid index is given, this
         *            method does nothing.
         * @param val the value to set the register to
         */
        public void setRegisterValue(int idx, int val)
        {
            if ((idx < 0) || (idx >= CPU.NUMREG))
            {
                return;    // invalid index
            }
            
            this.registers[idx] = val;
        }//setRegisterValue
         
    

        /**
         * toString       **DEBUGGING**
         *
         * @return a string representation of this class
         */
        public String toString()
        {
            String result = "Process id " + processId + " ";
            if (isBlocked())
            {
                result = result + "is BLOCKED for ";
                if (blockedForOperation == SYSCALL_OPEN)
                {
                    result = result + "OPEN";
                }
                else
                {
                    result = result + "WRITE @" + blockedForAddr;
                }
                for(DeviceInfo di : m_devices)
                {
                    if (di.getDevice() == blockedForDevice)
                    {
                        result = result + " on device #" + di.getId();
                        break;
                    }
                }
                result = result + ": ";
            }
            else if (this == m_currProcess)
            {
                result = result + "is RUNNING: ";
            }
            else
            {
                result = result + "is READY: ";
            }

            if (registers == null)
            {
                result = result + "<never saved>";
                return result;
            }
            
            for(int i = 0; i < CPU.NUMGENREG; i++)
            {
                result = result + ("r" + i + "=" + registers[i] + " ");
            }//for
            result = result + ("PC=" + registers[CPU.PC] + " ");
            result = result + ("SP=" + registers[CPU.SP] + " ");
            result = result + ("BASE=" + registers[CPU.BASE] + " ");
            result = result + ("LIM=" + registers[CPU.LIM] + " ");

            return result;
        }//toString
        
    }// class ProcessControlBlock

    /**
     * class DeviceInfo
     * 
     * This class contains information about a device that is currently
     * registered with the system.
     */
    private class DeviceInfo {
        /** every device has a unique id */
        private int id;
        /** a reference to the device driver for this device */
        private Device device;
        /** a list of processes that have opened this device */
        private Vector<ProcessControlBlock> procs;

        /**
         * constructor
         * 
         * @param d
         *            a reference to the device driver for this device
         * @param initID
         *            the id for this device. The caller is responsible for
         *            guaranteeing that this is a unique id.
         */
        public DeviceInfo(Device d, int initID) {
            this.id = initID;
            this.device = d;
            this.device.setId(initID);
            this.procs = new Vector<ProcessControlBlock>();
        }

        /** @return the device's id */
        public int getId() {
            return this.id;
        }

        /** @return this device's driver */
        public Device getDevice() {
            return this.device;
        }

        /** Register a new process as having opened this device */
        public void addProcess(ProcessControlBlock pi) {
            procs.add(pi);
        }

        /** Register a process as having closed this device */
        public void removeProcess(ProcessControlBlock pi) {
            procs.remove(pi);
        }

        /** Does the given process currently have this device opened? */
        public boolean containsProcess(ProcessControlBlock pi) {
            return procs.contains(pi);
        }

        /** Is this device currently not opened by any process? */
        public boolean unused() {
            return procs.size() == 0;
        }
    }// class DeviceInfo

    /*
     * ======================================================================
     * Device Management Methods
     * ----------------------------------------------------------------------
     */

    /**
     * registerDevice
     * 
     * adds a new device to the list of devices managed by the OS
     * 
     * @param dev
     *            the device driver
     * @param id
     *            the id to assign to this device
     * 
     */
    public void registerDevice(Device dev, int id) {
        m_devices.add(new DeviceInfo(dev, id));
    }// registerDevice

    // ======================================================================
    // System Call and Interrupt Methods
    // ----------------------------------------------------------------------

    /**
     * systemCall
     * 
     * Pops a system call instruction off the process stack and calls the
     * appropriate handler method for that system call.
     */
    public void systemCall() {
        int whichCall = pop();

        switch (whichCall) {
        case SYSCALL_EXIT:
            syscallExit();
            break;
        case SYSCALL_OUTPUT:
            syscallOutput();
            break;
        case SYSCALL_GETPID:
            syscallGetPid();
            break;
        case SYSCALL_OPEN:
            syscallOpen();
            break;
        case SYSCALL_CLOSE:
            syscallClose();
            break;
        case SYSCALL_READ:
            syscallRead();
            break;
        case SYSCALL_WRITE:
            syscallWrite();
            break;
        case SYSCALL_EXEC:
            syscallExec();
            break;
        case SYSCALL_YIELD:
            syscallYield();
            break;
        case SYSCALL_COREDUMP:
            syscallCoredump();
            break;
        default: // should never be reached
            System.out.println("ERROR: Illegal System Call.");
            System.exit(0);
            break;
        }
    }// systemCall

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
    
    /**
     * printProcessTable      **DEBUGGING**
     *
     * prints all the processes in the process table
     */
    private void printProcessTable()
    {
        debugPrintln("");
        debugPrintln("Process Table (" + m_processes.size() + " processes)");
        debugPrintln("======================================================================");
        for(ProcessControlBlock pi : m_processes)
        {
            debugPrintln("    " + pi);
        }//for
        debugPrintln("----------------------------------------------------------------------");

    }//printProcessTable

    /**
     * removeCurrentProcess
     * 
     * Removes the current process and selects a new process to run.
     */
    public void removeCurrentProcess()
    {
        debugPrintln("Removing process with id " + m_currProcess.getProcessId()
        		+ " at " + m_CPU.getBASE());
        
        m_processes.remove(m_currProcess);
        scheduleNewProcess();
    }//removeCurrentProcess

    /**
     * getRandomProcess
     *
     * selects a non-Blocked process at random from the ProcessTable.
     *
     * @return a reference to the ProcessControlBlock struct of the selected 
     * process -OR- null if no non-blocked process exists
     */
    ProcessControlBlock getRandomProcess()
    {
        //Calculate a random offset into the m_processes list
        int offset = ((int)(Math.random() * 2147483647)) % m_processes.size();
            
        //Iterate until a non-blocked process is found
        ProcessControlBlock newProc = null;
        for(int i = 0; i < m_processes.size(); i++)
        {
            newProc = m_processes.get((i + offset) % m_processes.size());
            if ( ! newProc.isBlocked())
            {
                return newProc;
            }
        }//for

        return null;        // no processes are Ready
    }//getRandomProcess
    
    /**
     * scheduleNewProcess
     * 
     * Schedules a new process by selecting a random process to run and saving
     * the register values of the current process.
     */
    public void scheduleNewProcess()
    {
        if(m_processes.size() == 0)
        {
            debugPrintln("No more processes to run. Stopping.");
            System.exit(0);
        }
        
        m_currProcess.save(m_CPU);
        
        ProcessControlBlock randomProcess = getRandomProcess();
        
        if(randomProcess == null)
        {
        	//this case should not happen yet, but will happen in future
        	
            System.out.println("There are no nonblocked processes " +
                    "left to run.");
        }
        
        m_currProcess = randomProcess;
        m_currProcess.restore(m_CPU);
        
        debugPrintln("Switched to process " + m_currProcess.getProcessId());
    }//scheduleNewProcess

    /**
     * addProgram
     *
     * registers a new program with the simulated OS that can be used when the
     * current process makes an Exec system call.  (Normally the program is
     * specified by the process via a filename but this is a simulation so the
     * calling process doesn't actually care what program gets loaded.)
     *
     * @param prog  the program to add
     *
     */
    public void addProgram(Program prog)
    {
        m_programs.add(prog);
    }//addProgram
    
    /**
     * createIdleProcess
     *
     * creates a one instruction process that immediately exits.  This is used
     * to buy time until device I/O completes and unblocks a legitimate
     * process.
     *
     */
    public void createIdleProcess()
    {
        int progArr[] = { 0, 0, 0, 0,   //SET r0=0
                          0, 0, 0, 0,   //SET r0=0 (repeated instruction to account for vagaries in student implementation of the CPU class)
                         10, 0, 0, 0,   //PUSH r0
                         15, 0, 0, 0 }; //TRAP

        //Initialize the starting position for this program
        int baseAddr = m_nextLoadPos;

        //Load the program into RAM
        for(int i = 0; i < progArr.length; i++)
        {
            m_RAM.write(baseAddr + i, progArr[i]);
        }

        //Save the register info from the current process (if there is one)
        if (m_currProcess != null)
        {
            m_currProcess.save(m_CPU);
        }
        
        //Set the appropriate registers
        m_CPU.setPC(baseAddr);
        m_CPU.setSP(baseAddr + progArr.length + 10);
        m_CPU.setBASE(baseAddr);
        m_CPU.setLIM(baseAddr + progArr.length + 20);

        //Save the relevant info as a new entry in m_processes
        m_currProcess = new ProcessControlBlock(IDLE_PROC_ID);  
        m_processes.add(m_currProcess);

    }//createIdleProcess

    /*
     * ======================================================================
     * Program Management Methods
     * ----------------------------------------------------------------------
     */

    /**
     * pop
     * 
     * Helper method that pops a value off the stack, as long as the stack
     * contains at least one valid value.
     * 
     * @return the popped value
     */
    public int pop() {
        int returnVal = 0;

        // Checks to make sure that the stack has at least something on it
        if (m_CPU.checkMemBounds(m_CPU.getSP() + CPU.INTSIZE)) {
            returnVal = m_RAM.read(m_CPU.getSP());
            m_CPU.setSP(m_CPU.getSP() + CPU.INTSIZE);
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
    public void push(int pushVal) {
        // Before decrement the stack pointer, check to see if that address
        // is within the program's allocated address bounds.
        if (m_CPU.checkMemBounds(m_CPU.getSP() - CPU.INTSIZE)) {
            m_CPU.setSP(m_CPU.getSP() - CPU.INTSIZE);
            m_RAM.write(m_CPU.getSP(), pushVal);
        }
    }// push

    /**
     * createProcess
     * 
     * Compiles the program into an array of integers, sets the BASE and LIMIT
     * registers, sets the program counter to the beginning of the program
     * instructions, sets heap register to start of the heap, and sets the 
     * stack pointer to the top of the allocated space.
     * 
     * @param prog
     *            the code to compile
     * @param allocSize
     *            space available or the difference between the physical memory
     *            addresses of the BASE and LIMIT registers
     */
    public void createProcess(Program prog, int allocSize) {
        int compiledCode[] = prog.export();

        //Checks to see if process to load is not within memory bounds
        if(m_nextLoadPos + allocSize > m_RAM.getSize())
        {
            System.out.println("ERROR: Process id to create is out of memory " +
            		"bounds.");
            System.exit(0);
        }
        
        //if another process is running, save registers
        if(m_currProcess != null)
        {
            m_currProcess.save(m_CPU);   
        }
        
        //Since this processes is in memory bounds, allocate it and set up the
        //variables for the next process.
        m_CPU.setBASE(m_nextLoadPos);
        m_CPU.setLIM(m_nextLoadPos + allocSize);
        m_nextLoadPos = m_nextLoadPos + allocSize;
        m_currProcess = new ProcessControlBlock(m_nextProcessID);
        m_nextProcessID++;
        
        // For each integer of compiled code, write that integer to memory if
        // there is enough memory allocated to the program.
        for (int i = 0; i < compiledCode.length; i++) {
            int base = m_CPU.getBASE();
            if (m_CPU.checkMemBounds(base + i))
                m_RAM.write(base + i, compiledCode[i]);
        }

        m_CPU.setPC(m_CPU.getBASE());
        m_CPU.setSP(m_CPU.getLIM() - MEM_OFFSET);

        m_processes.add(m_currProcess);
        
        debugPrintln("Installed program of size " + allocSize + " with " +
        		"process id " + m_currProcess.getProcessId() + 
        		" at position " + m_CPU.getBASE());
    }// createProcess

    /*
     * ======================================================================
     * Interrupt Handlers
     * ----------------------------------------------------------------------
     */

    /**
     * interruptIllegalMemoryAccess
     * 
     * Prints an error signaling that the process attempted to access memory
     * outside of the BASE and LIMIT registers and exits.
     * 
     * @param addr
     *            the illegal memory address
     */
    @Override
    public void interruptIllegalMemoryAccess(int addr) {
        System.out.println("ERROR: Illegal memory access attempt at " + addr
                + ".");
        System.exit(0);
    }

    /**
     * interruptDivideByZero
     * 
     * Prints an error signaling that the process attempted a division by zero
     * and exits.
     */
    @Override
    public void interruptDivideByZero() {
        System.out.println("ERROR: Division by zero.");
        System.exit(0);
    }

    /**
     * interruptIllegalInstruction
     * 
     * Prints an error signaling that the programmer wrote an instruction
     * unrecognized by the Pidgin compiler.
     * 
     * @param instr
     *            the illegal instruction
     */
    @Override
    public void interruptIllegalInstruction(int[] instr) {
        System.out.println("ERROR: Illegal instruction.");
        System.exit(0);
    }
    
    // TODO Add function headers
	@SuppressWarnings("static-access")
    @Override
    public void interruptIOReadComplete(int devID, int addr, int data) {
	    Device dev = null;
	    ProcessControlBlock pcb = null;
	    
	    for (DeviceInfo d : m_devices) {
	    	if (d.getId() == devID) {
				dev = d.device;
			}
	    }
	    
		if (dev == null) {
			push(ERR_DEV_NOT_EXIST);
			return;
		}
		
	    for (ProcessControlBlock p : m_processes) {
	    	if (p.isBlockedForDevice(dev, SYSCALL_READ, addr)) {
	    		pcb = p;
	    		pcb.unblock();
	    		break; //Only execute this once.
	    	}
	    }
	    
	    int pcb_SP = pcb.getRegisterValue(m_CPU.SP);
	    int pcb_BASE = pcb.getRegisterValue(m_CPU.BASE);
	    
	    m_RAM.write(pcb_SP + pcb_BASE, data);
	    pcb_SP -= 1;
	    pcb.setRegisterValue(m_CPU.SP, pcb_SP);
	    
	    m_RAM.write(pcb_SP + pcb_BASE, SUCCESS_CODE);
	    pcb_SP -= 1;
	    pcb.setRegisterValue(m_CPU.SP, pcb_SP);
    } //interruptIOReadComplete

	@SuppressWarnings("static-access")
    @Override
    public void interruptIOWriteComplete(int devID, int addr) {
	    Device dev = null;
	    ProcessControlBlock pcb = null;
	    
	    for (DeviceInfo d : m_devices) {
	    	if (d.getId() == devID) {
				dev = d.device;
			}
	    }
	    
	    // If for any reason the device doesn't exist
	    // we don't want to continue.
		if (dev == null) {
			push(ERR_DEV_NOT_EXIST);
			return;
		}
		
	    for (ProcessControlBlock p : m_processes) {
	    	if (p.isBlockedForDevice(dev, SYSCALL_WRITE, addr)) {
	    		pcb = p;
	    		pcb.unblock();
	    		break; //Only execute this once.
	    	}
	    }
	    
	    int pcb_SP = pcb.getRegisterValue(m_CPU.SP);
	    int pcb_BASE = pcb.getRegisterValue(m_CPU.BASE);
	    
	    m_RAM.write(pcb_SP + pcb_BASE, SUCCESS_CODE);
	    pcb_SP -= 1;
	    pcb.setRegisterValue(m_CPU.SP, pcb_SP);
    }

    /*
     * ======================================================================
     * System Calls
     * ----------------------------------------------------------------------
     */

    /**
     * syscallExec
     *
     * creates a new process.  The program used to create that process is chosen
     * randomly from all the programs that have been registered with the OS via
     * {@link #addProgram}.  If no programs have been registered then this system
     * call does nothing.
     *
     */
    private void syscallExec()
    {
        if (m_programs.size() > 0)
        {
            //Select a random program
            int pn = ((int)(Math.random() * 2147483647)) % m_programs.size();
            Program prog = m_programs.get(pn);

            //Determine the address space size using the default if available.
            //Otherwise, use a multiple of the program size.
            int allocSize = prog.getDefaultAllocSize();
            if (allocSize <= 0)
            {
                allocSize = prog.getSize() * 2;
            }

            //Load the program into RAM
            createProcess(prog, allocSize);

            //Adjust the PC since it's about to be incremented by the CPU
            m_CPU.setPC(m_CPU.getPC() - CPU.INSTRSIZE);
        }//if
    }//syscallExec

    /**
     * syscallYield
     * 
     * Allows a process to change its state from Running to Ready.
     */
    private void syscallYield()
    {
        scheduleNewProcess();
    }//syscallYield
    
    /**
     * selectBlockedProcess
     *
     * select a process to unblock that might be waiting to perform a given
     * action on a given device.  This is a helper method for system calls
     * and interrupts that deal with devices.
     *
     * @param dev   the Device that the process must be waiting for
     * @param op    the operation that the process wants to perform on the
     *              device.  Use the SYSCALL constants for this value.
     * @param addr  the address the process is reading from.  If the
     *              operation is a Write or Open then this value can be
     *              anything
     *
     * @return the process to unblock -OR- null if none match the given 
     *         criteria
     */
    public ProcessControlBlock selectBlockedProcess(Device dev, int op, 
    		int addr)
    {
        ProcessControlBlock selected = null;
        for(ProcessControlBlock pi : m_processes)
        {
            if (pi.isBlockedForDevice(dev, op, addr))
            {
                selected = pi;
                break;
            }
        }//for

        return selected;
    }//selectBlockedProcess
    
    /**
     * syscallWrite
     * 
     * Checks if device exists, it is open, and it is writeable before writing
     * to it.
     */
    private void syscallWrite() {
        int data = pop();
        int address = pop();
        int deviceId = pop();

        // Find the Device and DeviceInfo objects associated with the device id
        Device writeDevice = null;
        DeviceInfo writeDeviceInfo = null;
        for (DeviceInfo di : m_devices) {
            if (di.getDevice().getId() == deviceId) {
                writeDevice = di.getDevice();
                writeDeviceInfo = di;
                break;
            }
        }

        // Check for errors
        if (writeDevice == null && writeDeviceInfo == null) {
            System.out.println("ERROR: Device to write not found.");
            push(ERR_DEV_NOT_EXIST);
            return;
        }

        if (!(writeDeviceInfo.containsProcess(m_currProcess))) {
            System.out.println("ERROR: Cannot write to an unopened device.");
            push(ERR_DEV_NOT_OPEN);
            return;
        }

        if (!(writeDevice.isWriteable())) {
            System.out.println("ERROR: Device is not writeable.");
            push(ERR_DEV_NOT_WRITEABLE);
            return;
        }

        // All error checks have passed if writing to device
        writeDevice.write(address, data);
        push(SUCCESS_CODE);
    }

    /**
     * syscallRead
     * 
     * Checks if the device exists, is opened, and is readable before reading
     * data from it. If data was read correctly, push a SUCCESS_CODE onto the
     * stack; else push an ERROR_CODE.
     */
    private void syscallRead() {
        int address = pop();
        int deviceId = pop();

        // Find the Device and DeviceInfo objects associated with the device id
        Device readDevice = null;
        DeviceInfo readDeviceInfo = null;
        for (DeviceInfo di : m_devices) {
            if (di.getDevice().getId() == deviceId) {
                readDevice = di.getDevice();
                readDeviceInfo = di;
                break;
            }
        }

        // Check for errors
        if (readDevice == null) {
            System.out.println("ERROR: Device to read not found.");
            push(ERR_DEV_NOT_EXIST);
            return;
        }

        if (!(readDeviceInfo.containsProcess(m_currProcess))) {
            System.out.println("ERROR: Cannot read an unopened device.");
            push(ERR_DEV_NOT_OPEN);
            return;
        }

        if (!(readDevice.isReadable())) {
            System.out.println("ERROR: Device is not readable.");
            push(ERR_DEV_NOT_READABLE);
            return;
        }

        // All error checks have passed if reading from device; notify the
        // process of the value of the data read.
        push(readDevice.read(address));
        push(SUCCESS_CODE);
    }

    /**
     * syscallClose
     * 
     * Closes a device if it exists and if it was previously opened.
     */
    private void syscallClose() {
        int deviceId = pop();

        // Find the Device and DeviceInfo objects associated with the device id
        DeviceInfo closeDeviceInfo = null;
        for (DeviceInfo di : m_devices) {
            if (di.getId() == deviceId) {
                closeDeviceInfo = di;
                break;
            }
        }

        // Check for errors
        if (closeDeviceInfo == null) {
            System.out.println("ERROR: DeviceInfo to close not found.");
            push(ERR_DEV_NOT_EXIST);
            return;
        }

        if (!(closeDeviceInfo.containsProcess(m_currProcess))) {
            System.out.println("ERROR: Cannot close an unopened device.");
            push(ERR_DEV_NOT_OPEN);
            return;
        }

        // All error checks have passed if closing device.
        closeDeviceInfo.removeProcess(m_currProcess);
        
        // If a different process wants to use the device, unblock it and allow
        // access.
        ProcessControlBlock procToUnblock = selectBlockedProcess
                (closeDeviceInfo.getDevice(), SYSCALL_CLOSE, 0);
        if(procToUnblock != null)
        {
            procToUnblock.unblock();
            System.out.println("Process " + procToUnblock.getProcessId() 
            		+ " has been unblocked.");
        }
        
        push(SUCCESS_CODE);
    }

    /**
     * syscallOpen
     * 
     * Gets the id number of the device to open, checks if it is already open by
     * the process, and checks if it exists. If the device is currently unused
     * or it is sharable, it is successfully opened.
     */
    private void syscallOpen() {
        int deviceNum = pop();

        // Find the DeviceInfo object associated with the device id
        DeviceInfo openDevice = null;
        for (DeviceInfo di : m_devices) {
            if (di.getId() == deviceNum) 
            {
                openDevice = di;
                if (openDevice.containsProcess(m_currProcess)) 
                {
                    System.out.println("ERROR: Device is already open.");
                    push(ERR_DEV_ALREADY_OPEN);
                    return;
                } 
                else 
                {
                    // Else we have selected the correct process to open
                    // and to save computation power, break out of the loop
                    break;
                }
            }// if
        }// for

        // Check for errors
        if (openDevice == null) 
        {
            System.out.println("ERROR: DeviceInfo to open not found.");
            push(ERR_DEV_NOT_EXIST);
            return;
        }

        //Push success regardless of whether blocking process
        push(SUCCESS_CODE);
        
        //If blocking a process, schedule a new process to run as well as add
        //it to the device's process vector.
        if (!openDevice.unused() && !(openDevice.getDevice().isSharable()))
        {
            m_currProcess.block(m_CPU, openDevice.getDevice(), SYSCALL_OPEN,0);
            System.out.println("Process " + m_currProcess.getProcessId() 
            		+ " has been blocked.");
            openDevice.addProcess(m_currProcess);
            scheduleNewProcess();
        }
        //Allow current process to continue running
        else
        {
        	openDevice.addProcess(m_currProcess);
        }
    }//syscallOpen

    /**
     * syscallOutput
     * 
     * Prints the output of the system call.
     */
    private void syscallOutput() 
    {
        System.out.println("OUTPUT: " + pop());
    }

    /**
     * syscallCoredump
     * 
     * Prints the values of each process register and the top three items on the
     * process' stack and then exits the program.
     */
    private void syscallCoredump() 
    {
        m_CPU.regDump();
        for (int i = 0; i < 3; i++) {
            System.out.println(pop());
        }
        syscallExit();
    }

    /**
     * syscallGetpid
     * 
     * Pushes the currently running process id value onto the stack.
     */
    private int syscallGetPid() 
    {
        return m_currProcess.getProcessId();
    }

    /**
     * syscallExit
     * 
     * Exits the current process.
     */
    private void syscallExit() 
    {
        removeCurrentProcess();
    }

};// class SOS
