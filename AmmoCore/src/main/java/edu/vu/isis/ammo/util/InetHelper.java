
package edu.vu.isis.ammo.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides methods for examining network objects.
 */
public enum InetHelper {
    INSTANCE;

    final static private Logger logger = LoggerFactory.getLogger("util.inetinfo");

    /*
     * static { System.loadLibrary("inet_info"); }
     */

    /**
     * This method returns the concatenation of the usb vender and product id.
     * e.g. 19a5:000c .../usb2/2-1/2-1.2/2-1.2:1.0/net/eth1
     * 
     * @param address
     * @return
     */

    public static String getUsbId(final SocketAddress address) {
        try {
            final MulticastSocket sock = new MulticastSocket(address);
            final NetworkInterface nif = sock.getNetworkInterface();
            final String nifName = nif.getDisplayName();

            return getUsbId(nifName);

        } catch (Exception ex) {

        }
        return "";
    }

    public static String getUsbId(final String interfaceName) {
        return getUsbId(new File("/sys/class/net", interfaceName));
    }

    /**
     * Examine the "/sys" filesystem to determine the usb vendor:product id.
     * Basically, the process is to climb the nodes in the file path until the
     * vendor:product information is found.
     * 
     * @param inetPath
     * @return
     */
    private static String getUsbId(final File inetPath) {
        try {
            final File ethPath = inetPath.getCanonicalFile();

            for (File wipPath = ethPath.getParentFile(); wipPath != null; wipPath = wipPath
                    .getParentFile()) {
                final File vendorPath = new File(wipPath, "idVendor");
                if (!vendorPath.exists()) {
                    continue;
                }
                final StringBuilder sb = new StringBuilder();
                final FileInputStream vendorStream = new FileInputStream(vendorPath);
                try {
                    final FileChannel fc = vendorStream.getChannel();
                    final MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                    sb.append(Charset.defaultCharset().decode(bb).toString().trim());
                } finally {
                    vendorStream.close();
                }
                sb.append(":");
                final File productPath = new File(wipPath, "idProduct");
                if (!productPath.exists()) {
                    continue;
                }
                final FileInputStream productStream = new FileInputStream(productPath);
                try {
                    final FileChannel fc = productStream.getChannel();
                    final MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                    sb.append(Charset.defaultCharset().decode(bb).toString().trim());
                } finally {
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

    /**
     * This should probably be loaded from a shared preference file. This is a
     * map of the devices which should not be used.
     */
    private static final Map<String, String> prohibitedDevices;
    static {
        prohibitedDevices = new HashMap<String, String>();
        /** Harris 7800T radio */
        prohibitedDevices.put("19a5:000c", "19a5:000c");
        prohibitedDevices.put("1b6b:0002", "1b6b:0002");		// phone - usb adapter
        prohibitedDevices.put("0b95:7720", "0b95:7720");		// usb - ethernet adapter
        		
    }

    /**
     * Examine all network interfaces, selecting the first allowed.
     * <ol>
     * <li>loop over all interfaces
     * <li>reject loop back interface
     * <li>reject specific interfaces based on usb vender:product
     * <li>otherwise select the first interface
     * </ol>
     * 
     * @return
     */
    public String acquireInterface() {
        final File sysClassNet = new File("/sys/class/net");
        for (final File file : sysClassNet.listFiles()) {
            final File operStatePath = new File(file, "operstate");
            if (!operStatePath.exists()) {
                continue;
            }
            
            BufferedReader operStateReader = null;
            final String operState;
            try {
                final FileReader operStateFileReader = new FileReader(operStatePath);
                operStateReader = new BufferedReader(operStateFileReader);
                final StringBuilder sb = new StringBuilder();
                String str;
                while(( str = operStateReader.readLine()) != null) {
                    sb.append(str);
                }
                operState = sb.toString().trim();
            } catch (FileNotFoundException ex) {
                logger.warn("file lost {}", operStatePath, ex);
                continue;
            } catch (IOException ex) {
                logger.warn("could not read file {}", operStatePath, ex);
                continue;
            } finally {
                if (operStateReader != null) {
                    try {
                        operStateReader.close();
                    } catch (IOException ex) {
                        logger.warn("could not read file {}", operStatePath, ex);
                    }
                }
            }
           
            if (!"up".equals(operState)) {
                continue;
            }
            final String usbId = getUsbId(file);
            if (prohibitedDevices.containsKey(usbId)) {
                logger.info("interface rejected {}", usbId);
                continue;
            }
            final String interfaceName = file.getName();
            if ("lo".equals(interfaceName)) {
                continue;
            }
            logger.info("interface acquired {}", interfaceName);
            return interfaceName;
        }
        return null;
    }
}
