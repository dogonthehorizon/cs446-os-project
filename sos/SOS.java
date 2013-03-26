package sos;

import java.util.*;

/**
 * This class contains the simulated operating system (SOS). Realistically it
 * would run on the same processor (CPU) that it is managing but instead it uses
 * the real-world processor in order to allow a focus on the essentials of
 * operating system design using a high level programming language.
 * 
 * @author Preben Ingvaldsen
 * @author Et Begert
 * @author Aaron Dobbe
 * @author Kyle DeFrancia
 * @author Daniel Jones
 * @author Raphael Ramos
 * 
 * @version February 18, 2013
 * 
 */

public class SOS implements CPU.TrapHandler
{
    // ======================================================================
    // Constants
    // ----------------------------------------------------------------------

    // These constants define the system calls this OS can currently handle
    public static final int SYSCALL_EXIT = 0; /* exit the current program */
    public static final int SYSCALL_OUTPUT = 1; /* outputs a number */
    public static final int SYSCALL_GETPID = 2; /* get current process id */
    public static final int SYSCALL_OPEN = 3; /* access a device */
    public static final int SYSCALL_CLOSE = 4; /* release a device */
    public static final int SYSCALL_READ = 5; /* get input from device */
    public static final int SYSCALL_WRITE = 6; /* send output to device */
    public static final int SYSCALL_EXEC    = 7;    /* spawn a new process */
    public static final int SYSCALL_YIELD   = 8;    /* yield the CPU to another process */
    public static final int SYSCALL_COREDUMP = 9; /*
                                                   * print process state and
                                                   * exit
                                                   */

    // Success and error code constants
    public static final int SUCCESS = 0;
    public static final int DEVICE_NOT_FOUND = -1;
    public static final int DEVICE_NOT_SHARABLE = -2;
    public static final int DEVICE_ALREADY_OPEN = -3;
    public static final int DEVICE_NOT_OPEN = -4;
    public static final int DEVICE_READ_ONLY = -5;
    public static final int DEVICE_WRITE_ONLY = -6;
    
    /**This process is used as the idle process' id*/
    public static final int IDLE_PROC_ID    = 999;  

    // ======================================================================
    // Member variables
    // ----------------------------------------------------------------------

    /**
     * This flag causes the SOS to print lots of potentially helpful status
     * messages
     **/
    public static final boolean m_verbose = true;
    
    /**
     * Position to load the next program into.
     **/
    private int m_nextLoadPos;
    
    /**
     * ID for the next process to load.
     **/
    private int m_nextProcessID;

    /**
     * The CPU the operating system is managing.
     **/
    private CPU m_CPU = null;

    /**
     * The RAM attached to the CPU.
     **/
    private RAM m_RAM = null;

    /**
     * The current process run by the CPU.
     **/
    private ProcessControlBlock m_currProcess = null;
    
    /**
     * All processes currently active.
     **/
    private Vector<ProcessControlBlock> m_processes = null;

    /**
     * All devices currently registered.
     **/
    private Vector<DeviceInfo> m_devices = null;
    
    /**
     * All programs currently running.
     **/
    private Vector<Program> m_programs = null;

    /*
     * ======================================================================
     * Constructors & Debugging
     * ----------------------------------------------------------------------
     */

    /**
     * Inititalize member variables
     */
    public SOS(CPU c, RAM r)
    {
        // Init member list
        m_CPU = c;
        m_RAM = r;
        m_nextLoadPos = 0;
        m_nextProcessID = 1001;
        m_CPU.registerTrapHandler(this);        
        m_processes = new Vector<ProcessControlBlock>();
        m_devices = new Vector<DeviceInfo>();
        m_programs = new Vector<Program>();
    }// SOS ctor

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
     * Process Management Methods
     *----------------------------------------------------------------------
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
     * Removes the currently running process from the process table,
     * and selects a new one to run.
     */
    public void removeCurrentProcess()
    {
        debugPrintln("Removing process ID " + m_currProcess.getProcessId());
        m_processes.remove(m_currProcess);
        scheduleNewProcess();
    }//removeCurrentProcess

    /**
     * getRandomProcess
     *
     * selects a non-Blocked process at random from the ProcessTable.
     *
     * @return a reference to the ProcessControlBlock struct of the selected process
     * -OR- null if no non-blocked process exists
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
     * Select a new process to run and load it into the CPU.
     */
    public void scheduleNewProcess()
    {
        printProcessTable();
        
        if (m_processes.size() == 0)
        {
            // No processes to run, just end the simulation
            System.exit(0);
        }
        
        int oldID = m_currProcess.getProcessId();
                
        m_currProcess.save(m_CPU);
        
        ProcessControlBlock nextProc = getRandomProcess();
        if (nextProc == null)
        {
            createIdleProcess();
            return;
        }
        int newID = nextProc.getProcessId();
        debugPrintln("Process ID " + oldID +
                " moving to Ready; process ID " + newID + " running");
        
        // Load up the next process
        m_currProcess = nextProc;
        m_currProcess.restore(m_CPU);

    }//scheduleNewProcess
    
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

    /*
     * ======================================================================
     * Program Management Methods
     * ----------------------------------------------------------------------
     */

    /**
     * createProcess
     * 
     * loads a program into RAM
     * 
     * @param prog
     *            the program to load into memory
     * @param allocSize
     *            the size allocated to the program in memory
     */
    public void createProcess(Program prog, int allocSize)
    {
        debugPrintln("Creating process ID " + m_nextProcessID);
        
        // export the program and set necessary values in the CPU
        int[] programInstructions = new int[prog.getSize()];
        programInstructions = prog.export();
        
        // If a process is currently running, save its registers
        if (m_currProcess != null)
        {
            m_currProcess.save(m_CPU);
        }
        
        m_CPU.setBASE(m_nextLoadPos);
        m_CPU.setLIM(m_CPU.getBASE() + allocSize);
        m_CPU.setPC(m_CPU.getBASE());
        m_CPU.setSP(m_CPU.getLIM()-1);
        
        // Make sure there is sufficient space in RAM
        if (m_CPU.getLIM() >= m_RAM.getSize())
        {
            debugPrintln("Process ID " + m_nextProcessID +
                    "failed to load due to insufficient memory");
            System.exit(0);
        }

        // move through the allocated memory and load the program instructions
        // into RAM,
        // one at a time, breaking if we go past the memory limit
        for (int i = 0; i < programInstructions.length; i++)
        {
            m_RAM.write(m_CPU.getBASE() + i, programInstructions[i]);
        }
        
        // Load up the new process
        ProcessControlBlock newProc = new ProcessControlBlock(m_nextProcessID);
        m_processes.add(newProc);
        m_currProcess = newProc;
        m_currProcess.save(m_CPU);

        // Prepare for the next process to load
        m_nextLoadPos += allocSize;
        m_nextProcessID++;
    }// createProcess
    
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
     * @return the process to unblock -OR- null if none match the given criteria
     */
    public ProcessControlBlock selectBlockedProcess(Device dev, int op, int addr)
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
     * pop
     * 
     * pops top value of off the stack
     * 
     * @return 0 if the stack is empty; else return the value on top of stack
     */
    private int pop()
    {
        if (m_CPU.getSP() < m_CPU.getLIM() - 1)
        {
            m_CPU.setSP(m_CPU.getSP() + 1);
            return m_RAM.read(m_CPU.getSP());
        }
        else
        {
            System.out.println("ERROR: STACK IS EMPTY");
            System.exit(1);
            return 0;
        }
    }

    /**
     * push
     * 
     * pushes the value in the specified register on the stack
     * 
     * @param value
     *            the value to put on the stack
     * @return false if the stack is full, else true
     */
    private boolean push(int value)
    {
        if (m_CPU.getSP() >= m_CPU.getBASE())
        {
            m_RAM.write(m_CPU.getSP(), value);
            m_CPU.setSP(m_CPU.getSP() - 1);
            return true;
        }
        System.out.println("ERROR: STACK IS FULL");
        return false;
    }

    // Method used by SOS for system calls: depending on what the value of
    // SYSCALL_ID is, will call a helper method that corresponds to
    // that value
    public void systemCall()
    {
        int syscallId = pop();
        switch (syscallId)
        {
        case SYSCALL_EXIT:
            syscallExit();
            break;
        case SYSCALL_OUTPUT:
            syscallOutput();
            break;
        case SYSCALL_GETPID:
            syscallPID();
            break;
        case SYSCALL_COREDUMP:
            syscallDump();
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
        }

    }

    /*
     * ======================================================================
     * Interrupt Handlers
     * ----------------------------------------------------------------------
     */

    /**
     * interruptIllegalMemoryAccess
     * 
     * Called when a process attempts to access memory outside its allocated
     * space. Prints an error and exits.
     * 
     * @param addr
     *            the address that was attempted to be accessed
     */
    @Override
    public void interruptIllegalMemoryAccess(int addr)
    {
        System.out.println("Illegal memory access at: " + addr);
        System.exit(0);
    }

    /**
     * interruptDivideByZero
     * 
     * Called when a process attempts to divide by zero. Prints an error and
     * exits.
     */
    @Override
    public void interruptDivideByZero()
    {
        System.out.println("Cannot divide by zero");
        System.exit(0);
    }

    /**
     * interruptIllegalInstruction
     * 
     * Called when a process attempts to execute an illegal instruction. Prints
     * an error and exits.
     * 
     * @param instr
     *            the illegal instruction that triggered the interrupt
     */
    @Override
    public void interruptIllegalInstruction(int[] instr)
    {
        System.out.println("Illegal instruction given: " + instr);
        System.exit(0);
    }
    
    public void interruptIOReadComplete(int devID, int addr, int data)
    {
        // Find referenced device
        for (DeviceInfo devInfo : m_devices)
        {
            if (devInfo.getId() == devID)
            {
                ProcessControlBlock blockedProcess = 
                        selectBlockedProcess(devInfo.getDevice(), SYSCALL_READ, addr);
                blockedProcess.unblock();
                
                // Push data and success code onto the process's stack
                int sp = blockedProcess.getRegisterValue(CPU.SP);
                m_RAM.write(sp, data);
                sp--;
                blockedProcess.setRegisterValue(CPU.SP, sp);
                m_RAM.write(sp, SUCCESS);
                sp--;
                blockedProcess.setRegisterValue(CPU.SP, sp);
                return;
            }
        }
    }
    
    public void interruptIOWriteComplete(int devID, int addr)
    {
        // Find referenced device
        for (DeviceInfo devInfo : m_devices)
        {
            if (devInfo.getId() == devID)
            {
                ProcessControlBlock blockedProcess = 
                        selectBlockedProcess(devInfo.getDevice(), SYSCALL_WRITE, addr);
                blockedProcess.unblock();
                
                // Push success code onto the process's stack
                int sp = blockedProcess.getRegisterValue(CPU.SP);
                m_RAM.write(sp, SUCCESS);
                sp--;
                blockedProcess.setRegisterValue(CPU.SP, sp);
                return;
            }
        }
    }

    /*
     * ======================================================================
     * System Calls
     * ----------------------------------------------------------------------
     */

    /**
     * syscallExit
     * 
     * Called when handling a SYSCALL_EXIT. Ends the process and schedules
     * a new one.
     */
    private void syscallExit()
    {
        removeCurrentProcess();
    }

    /**
     * syscallOutput
     * 
     * Called when handling a SYSCALL_OUTPUT. Pops the top value off of the
     * process's stack and prints it to console.
     */
    private void syscallOutput()
    {
        System.out.println("OUTPUT: " + pop());
    }

    /**
     * syscallPID
     * 
     * Called when handling a SYSCALL_GETPID. Pushes the current process's ID
     * (currently defined to be 42) to its stack.
     */
    private void syscallPID()
    {
        push(m_currProcess.getProcessId());
    }

    /**
     * syscallDump
     * 
     * Called when handling a SYSCALL_COREDUMP. Prints the contents of the
     * registers and top three values on the stack, then exits.
     */
    private void syscallDump()
    {
        System.out.println("CORE DUMPING:");
        m_CPU.regDump();
        System.out.println(pop() + "\n" + pop() + "\n" + pop());
        syscallExit();
    }

    /**
     * syscallOpen
     * 
     * Pops a device ID off the stack, then adds the current process to that
     * device.
     */
    private void syscallOpen()
    {
        int devId = pop();
        for (DeviceInfo deviceInfo : m_devices)
        {
            if (deviceInfo.getId() == devId)
            {
                if (!deviceInfo.getDevice().isSharable()
                        && !deviceInfo.unused())
                {
                    // Reserve the device for this process, but block the
                    // process until the device is next available
                    deviceInfo.addProcess(m_currProcess);
                    push(SUCCESS);
                    m_currProcess.block(m_CPU, deviceInfo.getDevice(),
                            SYSCALL_OPEN, 0);
                    scheduleNewProcess();
                    return;
                }
                else if (deviceInfo.containsProcess(m_currProcess))
                {
                    // The process has already opened this device
                    push(DEVICE_ALREADY_OPEN);
                    return;
                }
                else
                {
                    deviceInfo.addProcess(m_currProcess);
                    push(SUCCESS);
                    return;
                }
            }
        }
        // If we're here, the device doesn't exist
        push(DEVICE_NOT_FOUND);
    }

    /**
     * syscallClose
     * 
     * Pops a device ID off the stack, then removes the current process from
     * that device.
     */
    private void syscallClose()
    {
        int devId = pop();
        for (DeviceInfo deviceInfo : m_devices)
        {
            if (deviceInfo.getId() == devId)
            {
                if (!deviceInfo.containsProcess(m_currProcess))
                {
                    // The process has not opened this device
                    push(DEVICE_NOT_OPEN);
                    return;
                }
                deviceInfo.removeProcess(m_currProcess);
                push(SUCCESS);
                
                // Check to see if any other processes are waiting
                // on this device. If so, set one of them to the
                // Running state.
                ProcessControlBlock nextProc = selectBlockedProcess(
                        deviceInfo.getDevice(),
                        SYSCALL_OPEN,
                        0);
                if (nextProc != null)
                {
                    nextProc.unblock();
                }
                return;
            }
        }
        // If we're here, the device doesn't exist
        push(DEVICE_NOT_FOUND);
    }

    /**
     * syscallRead
     * 
     * Pops an address and device ID from the stack. Then reads from the given
     * address on the specified device, pushing the result to the stack.
     */
    private void syscallRead()
    {
        int addr = pop();
        int devId = pop();

        for (DeviceInfo deviceInfo : m_devices)
        {
            if (deviceInfo.getId() == devId)
            {
                Device device = deviceInfo.getDevice();
                // If device is unavailable, move process to Ready and schedule
                // a new one
                if (!device.isAvailable())
                {
                    int pc = m_CPU.getPC();
                    m_CPU.setPC(pc - CPU.INSTRSIZE);
                    push(devId);
                    push(addr);
                    push(SYSCALL_READ);
                    scheduleNewProcess();
                    return;
                }
                
                if (!deviceInfo.containsProcess(m_currProcess))
                {
                    // The process has not opened this device
                    push(DEVICE_NOT_OPEN);
                    return;
                }
                else if (!device.isReadable())
                {
                    // Device is write-only
                    push(DEVICE_WRITE_ONLY);
                    return;
                }
                
                device.read(addr);
                m_currProcess.block(m_CPU, device, SYSCALL_READ, addr);
                scheduleNewProcess();
                return;
            }
        }
        // If we're here, the device doesn't exist
        push(DEVICE_NOT_FOUND);
    }

    /**
     * syscallWrite
     * 
     * Pops the value, address, and device ID from the stack. Then writes the
     * given data to the specified device.
     */
    private void syscallWrite()
    {
        int value = pop(); // TODO figure out how we aren't losing this
        int addr = pop();
        int devId = pop();

        for (DeviceInfo deviceInfo : m_devices)
        {
            if (deviceInfo.getId() == devId)
            {
                Device device = deviceInfo.getDevice();
                // If device is unavailable, move process to Ready and schedule
                // a new one
                if (!device.isAvailable())
                {
                    int pc = m_CPU.getPC();
                    m_CPU.setPC(pc - CPU.INSTRSIZE);
                    push(devId);
                    push(addr);
                    push(value);
                    push(SYSCALL_WRITE);
                    scheduleNewProcess();
                    return;
                }
                
                if (!deviceInfo.containsProcess(m_currProcess))
                {
                    // The process has not opened this device
                    push(DEVICE_NOT_OPEN);
                    return;
                }
                else if (!device.isWriteable())
                {
                    // Device is read-only
                    push(DEVICE_READ_ONLY);
                    return;
                }
                device.write(addr, value);
                m_currProcess.block(m_CPU, device, SYSCALL_WRITE, addr);
                scheduleNewProcess();
                return;
            }
        }
        // If we're here, the device doesn't exist
        push(DEVICE_NOT_FOUND);
    }
    
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
     * Called when a program can yield to another process.
     */
    private void syscallYield()
    {
        scheduleNewProcess();
    }//syscallYield

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
    public void registerDevice(Device dev, int id)
    {
        m_devices.add(new DeviceInfo(dev, id));
    }// registerDevice

    // ======================================================================
    // Inner Classes
    // ----------------------------------------------------------------------

    /**
     * class ProcessControlBlock
     * 
     * This class contains information about a currently active process.
     */
    private class ProcessControlBlock
    {
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
         * If this process is blocked a reference to the Device is stored here
         */
        private Device blockedForDevice = null;
        
        /**
         * If this process is blocked a reference to the type of I/O operation
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
         * @return the current process' id
         */
        public int getProcessId()
        {
            return this.processId;
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
         * blocks the current process to wait for I/O.  This includes saving the
         * process' registers.   The caller is responsible for calling
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
         * @param op    check to see if the process is waiting for this operation
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
         * compares this to another ProcessControlBlock object based on the BASE addr
         * register.  Read about Java's Collections class for info on
         * how this method can be quite useful to you.
         */
        public int compareTo(ProcessControlBlock pi)
        {
            return this.registers[CPU.BASE] - pi.registers[CPU.BASE];
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
    private class DeviceInfo
    {
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
        public DeviceInfo(Device d, int initID)
        {
            this.id = initID;
            this.device = d;
            this.procs = new Vector<ProcessControlBlock>();
        }

        /** @return the device's id */
        public int getId()
        {
            return this.id;
        }

        /** @return this device's driver */
        public Device getDevice()
        {
            return this.device;
        }

        /** Register a new process as having opened this device */
        public void addProcess(ProcessControlBlock pi)
        {
            procs.add(pi);
        }

        /** Register a process as having closed this device */
        public void removeProcess(ProcessControlBlock pi)
        {
            procs.remove(pi);
        }

        /** Does the given process currently have this device opened? */
        public boolean containsProcess(ProcessControlBlock pi)
        {
            return procs.contains(pi);
        }

        /** Is this device currently not opened by any process? */
        public boolean unused()
        {
            return procs.size() == 0;
        }

    }// class DeviceInfo

};// class SOS
