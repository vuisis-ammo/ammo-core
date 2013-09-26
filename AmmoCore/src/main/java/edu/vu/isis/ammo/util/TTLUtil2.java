package edu.vu.isis.ammo.util;

import java.io.FileDescriptor;
import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.net.DatagramSocketImpl;
import java.net.MulticastSocket;

/**
 * Copied to ammocore to change signature of setTTLValue
 */
public class TTLUtil2 {

	private static boolean USE_TTL_FIX=false;

    /**
     * if the function is available then the library is already loaded.
     */
    static
    {
		try {
             setNativeTTL(0,0);
             USE_TTL_FIX = true;
        } catch ( UnsatisfiedLinkError e ) {
			e.printStackTrace();
        }
		try {
			if( System.getProperty("android.vm.dexfile") != null ) {
        		System.loadLibrary( "ammocore" );
				USE_TTL_FIX=true;
			}
		} catch ( SecurityException e ) {
			e.printStackTrace();
		} catch ( UnsatisfiedLinkError e ) {
			e.printStackTrace();
		} catch ( Exception e ) {
			// no ttl fix
			e.printStackTrace();
		}
    }
    
    public static int setTTLValue (DatagramSocket sock, int ttl) {
		if( USE_TTL_FIX ) {
			try {
				Field implField = sock.getClass().getSuperclass().getDeclaredField("impl");
			
				if (!implField.isAccessible())
					implField.setAccessible(true);

				Object value;
				value = implField.get(sock);

				DatagramSocketImpl impl = null;
				FileDescriptor fd = null;
				int actualfd = 0;

				if(value instanceof DatagramSocketImpl){
					impl = (DatagramSocketImpl)value;

					Field fdField = impl.getClass().getSuperclass().getDeclaredField("fd");

					if (!fdField.isAccessible())
						fdField.setAccessible(true);

					value = fdField.get(impl);

					if(value instanceof FileDescriptor){
						fd = (FileDescriptor)value;

						Field intfd = fd.getClass().getDeclaredField("descriptor");

						if (!intfd.isAccessible())
							intfd.setAccessible(true);

						actualfd = intfd.getInt(fd);
						
						return setNativeTTL (actualfd, ttl);
				}
			}
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			if( sock instanceof MulticastSocket ) {
				try {
					((MulticastSocket)sock).setTimeToLive(ttl);
					return ttl;
				} catch ( Exception e ) {
					e.printStackTrace();
				}
			}
		}
        return -1;
    }
    
    private static native int setNativeTTL (int sockd, int ttl);
    
}
