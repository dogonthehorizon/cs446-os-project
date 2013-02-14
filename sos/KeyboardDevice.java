package sos;

/**
 * This class implements a device driver for a keyboard.
 * 
 * @authors Et Begert, Fernando Freire
 *
 */

public class KeyboardDevice implements Device{

	@Override
    public int getId() {
	    // TODO Auto-generated method stub
	    return 0;
    }

	@Override
    public void setId(int id) {
	    // TODO Auto-generated method stub
	    
    }

	@Override
    public boolean isSharable() {
	    // TODO Auto-generated method stub
	    return false;
    }

	@Override
    public boolean isAvailable() {
	    // TODO Auto-generated method stub
	    return false;
    }

	@Override
    public boolean isReadable() {
	    // TODO Auto-generated method stub
	    return false;
    }

	@Override
    public boolean isWriteable() {
	    // TODO Auto-generated method stub
	    return false;
    }

	@Override
    public int read(int addr) {
	    // TODO Auto-generated method stub
	    return 0;
    }

	@Override
    public void write(int addr, int data) {
	    // TODO Auto-generated method stub
	    
    }
	
	
}