package edu.vu.isis.ammo.util;

import java.nio.ByteBuffer;

/**
 * Generates a checksum for some bytes
 * 
 * @author vandy
 * @author mriley
 */
public abstract class CheckSum implements Comparable<CheckSum> {

	private static final long BYTE_MASK_LONG = 0x0FFL;
	
	public static CheckSum newInstance() {
		if( ZlibCRC32.isAvailable ) {
			return new ZlibCRC32();
		} else {
			return new ZipCRC32();
		}
	}
	
	public static CheckSum newInstance(byte[] data) {
		return new ImmutableCheckSum(data);
	}
	
	public static CheckSum newInstance(long data) {
		return new ImmutableCheckSum(data);
	}
	
	protected CheckSum() {}
	
	public CheckSum update( byte[] buffer ) {
		return update(buffer, 0, buffer.length);
	}
	
	public abstract CheckSum update( byte[] buffer, int off, int len );
		
	public abstract CheckSum update( ByteBuffer buffer );
	
	public abstract long getValue();
	
	@Override
	public int compareTo(CheckSum o) {
		if( o == null )
			return 1;
		if( this == o )
			return 0;
		long l1 = getValue();
		long l2 = o.getValue();
		if( l1 < l2 )
			return -1;
		if( l1 > l2 )
			return 1;
		return 0;
	}
	
    /**
     * The four least significant bytes of a long make the checksum array.
     * 
     * @param cvalue
     * @return
     */
    public byte[] asByteArray() {
    	long cs = getValue();    	
        return new byte[] {
                (byte) cs,
                (byte) (cs >>> 8),
                (byte) (cs >>> 16),
                (byte) (cs >>> 24)
        };
    }
    
    public long asLong() {
        return getValue();
    }
   
    public boolean equals(long value) {
        return (getValue() == value);
    }
    
    public boolean equals(byte[] checkBytes) {
        return (getValue() == convert(checkBytes));
    }
    
    private static long convert(byte[] checkBytes) {
        return  ((BYTE_MASK_LONG & checkBytes[0]) << 0)
                | ((BYTE_MASK_LONG & checkBytes[1]) << 8)
                | ((BYTE_MASK_LONG & checkBytes[2]) << 16)
                | ((BYTE_MASK_LONG & checkBytes[3]) << 24);
    }
    
    @Override 
    public boolean equals(Object obj) {
        if (!(obj instanceof CheckSum)) return false;
        final CheckSum that = (CheckSum)obj;
        return (getValue() == that.getValue());
    }
    
    @Override
    public String toString() {
    	return toHexString();
    }
    
    public String toHexString() {
        return Long.toHexString(getValue());
    }
    
    private static final class ImmutableCheckSum extends CheckSum {
    	
    	private final long crc;
    	
    	public ImmutableCheckSum(byte[] data) {
    		this(convert(data));
		}
    	
    	public ImmutableCheckSum(long data) {
    		this.crc = data;
		}
    	
    	@Override
    	public long getValue() {
    		return crc;
    	}
    	@Override
    	public CheckSum update(byte[] buffer, int off, int len) {
    		return this;
    	}
    	@Override
    	public CheckSum update(ByteBuffer buffer) {
    		return this;
    	}    	
    }
}
