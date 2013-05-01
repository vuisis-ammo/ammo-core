
package edu.vu.isis.ammo.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides methods for examining network objects.
 */
public class InetInfo {
    final static private Logger logger = LoggerFactory.getLogger("util.inetinfo");

    /*
     * static { System.loadLibrary("inet_info"); }
     */

    /**
     * This method returns the concatenation of the usb vender and product id.
     * e.g. 19a5:000c
     * 
     * .../usb2/2-1/2-1.2/2-1.2:1.0/net/eth1
     * 
     * @param address
     * @return
     */

    public static String getUsbId(final SocketAddress address) {
        try {
            final MulticastSocket sock = new MulticastSocket(address);
            final NetworkInterface nif = sock.getNetworkInterface();
            final String nifName = nif.getDisplayName();
            
            final File inetPath = new File("/sys/class/net", nifName);
            final File ethPath = inetPath.getCanonicalFile();
            
            for (File wipPath = ethPath.getParentFile(); wipPath != null; wipPath = wipPath.getParentFile()) {
                final File vendorPath = new File(wipPath, "idVendor");
                if (! vendorPath.exists()) {
                    continue;
                }
                final StringBuilder sb = new StringBuilder();
                final FileInputStream vendorStream = new FileInputStream(vendorPath);
                try {
                  final FileChannel fc = vendorStream.getChannel();
                  final MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                  sb.append(Charset.defaultCharset().decode(bb).toString().trim());
                }
                finally {
                    vendorStream.close();
                }
                sb.append(":");
                final File productPath = new File(wipPath, "idProduct");
                if (! productPath.exists()) {
                    continue;
                }
                final FileInputStream productStream = new FileInputStream(productPath);
                try {
                  final FileChannel fc = productStream.getChannel();
                  final MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                  sb.append(Charset.defaultCharset().decode(bb).toString().trim());
                }
                finally {
                    productStream.close();
                }
                return sb.toString();
            }
            return null;

        } catch (IOException ex) {
            logger.error("could not open socket", ex);
        }
        return null;

    }
}
