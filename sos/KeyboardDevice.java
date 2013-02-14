package sos;

import java.util.*;

/**
 * This class implements a device driver for a keyboard.
 * 
 * @authors Et Begert, Fernando Freire
 * @see ConsoleDevice Comments graciously borrowed from Porf. Nuxoll.
 *
 */

public class KeyboardDevice implements Device{
	
	private int m_id = -999; //OS sets the device id

    /**
     * getId
     *
     * @return the device id of this device
     */
    public int getId() {
	    return m_id;
    }

    /**
     * setId
     *
     * sets the device id of this device
     *
     * @param id the new id
     */
    public void setId(int id) {
	    m_id = id;
	    
    }

    /**
     * isSharable
     *
     * This device can be used simultaneously by multiple processes
     *
     * @return true
     */
    public boolean isSharable() {
	    return false;
    }

    /**
     * isAvailable
     *
     * this device is available if no requests are currently being processed
     */
    public boolean isAvailable() {
	    return true;
    }

    /**
     * isReadable
     *
     * @return whether this device can be read from (true/false)
     */
    public boolean isReadable() {
	    return true;
    }

    /**
     * isWriteable
     *
     * @return whether this device can be written to (true/false)
     */
    public boolean isWriteable() {
	    return false;
    }

    /**
     * read
     *
     * @return For now, return a random integer between 0 and 9.
     * 
     */
    public int read(int addr) {
    	Random generator = new Random();
	    return generator.nextInt(10);
    }

    /**
     * write
     *
     * Not implemented.
     */
    public void write(int addr, int data) {
	    // do nothing
    }
	
	
}