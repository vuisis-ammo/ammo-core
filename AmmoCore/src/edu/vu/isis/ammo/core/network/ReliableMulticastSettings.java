package edu.vu.isis.ammo.core.network;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import ch.qos.logback.classic.Logger;


import android.content.Context;

public class ReliableMulticastSettings {
	
//	Context context_ = null;
//	File configFile_ = null;
    private static final Logger logger = (Logger) LoggerFactory.getLogger("net.rmcastset");
    
    private final static Object lock = new Object();
	
	public static void setIpAddress (String ipaddress, Context context) {
		
			String value = "${jgroups.udp.mcast_addr:" + ipaddress + "}";
			setAttribute ("mcast_addr", value, context);
	}
	
	public static void setPort (String port, Context context) {
			
			String value = "${jgroups.udp.mcast_port:" + port + "}";
			setAttribute ("mcast_port", value, context);
	}
	
	private static void setAttribute (String itemName, 
									  String itemValue, 
									  Context context) {
		
		synchronized(lock) {
			final File dir = 
					context.getDir(
							ReliableMulticastChannel.config_dir,
							Context.MODE_WORLD_READABLE);

			File configFile = 
					new File(dir, ReliableMulticastChannel.config_file);

			if (configFile == null)
				return;

			logger.trace("File name is " + configFile.getAbsolutePath());

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder;
			try {
				dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(configFile);
				//doc.getDocumentElement().normalize();

				NodeList nList = doc.getElementsByTagName("UDP");
				NamedNodeMap np = nList.item(0).getAttributes();


				Node mcast_addr_node = np.getNamedItem(itemName);

				mcast_addr_node.setTextContent(itemValue);

				writeXml(configFile, doc);

			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TransformerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
		}
	}

	private static void writeXml(File configFile, Document doc)
			throws TransformerFactoryConfigurationError,
			TransformerConfigurationException, TransformerException {
		
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
	
		DOMSource source = new DOMSource(doc);
		
		StringWriter sw = new StringWriter();
		StreamResult result = new StreamResult(sw);
		
		transformer.transform(source, result);
		
		String xmlString = sw.toString();
		
		
		byte bytes[] = xmlString.getBytes();
		
		try {
			
		    File configFile1 = new File (configFile.getParentFile(), "jgroups.xml");
		    
		    FileWriter out = new FileWriter (configFile1);
			out.write(xmlString);
		    out.close();
		    
		    if (! configFile1.renameTo(configFile))
		    	logger.error("Jgroups configuration file cannot be moved from {} to {}", configFile1, configFile);
		    
		} catch (Exception e) {
		    e.printStackTrace();
		}
		
		
		//System.out.println ("xmlString is [" + xmlString + "]");
		logger.trace("xmlString is {}", xmlString );
	}
	
}
