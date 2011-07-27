package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import android.content.Context;

/**
 * This class provides the base level mapping between distribution 
 * policy and data topic.
 * 
 * It is a wrapper around a hash of key value pairs.
 * The value is simply a set of flags which indicate the
 * manner objects for the various types are to be distributed.
 * 
 * The main values are:
 * <ul>
 * <li>priority</li>
 * <li>scope</li>
 * </ul>
 * 
 *
 */
public class DistributionPolicy {
	private static final Logger logger = LoggerFactory.getLogger(DistributionPolicy.class);
	
	public final Map<String, Load> policy;
	public final String policy_file = "distribution_policy.xml";
	
	public DistributionPolicy(Context context) {
		this.policy = new HashMap<String, Load>(20);
		this.policy.put("xyz", new Load(Scope.GATEWAY));
		this.policy.put("abc", new Load(Scope.MULTICAST));
		
		try {
			File file = context.getDir(policy_file, Context.MODE_WORLD_READABLE);
	        SAXParserFactory parserFactory=SAXParserFactory.newInstance();
	        SAXParser saxParser=parserFactory.newSAXParser();
	        XMLReader reader=saxParser.getXMLReader();
	        
	        PolicyHandler policyHandler = new PolicyHandler();
	        reader.setContentHandler(policyHandler);
	        
	        reader.parse(new InputSource(new InputStreamReader(new FileInputStream(file))));
	        //statePackage = policyHandler.getStatePackage();
	        
	    } catch (MalformedURLException ex) {
	        logger.warn("malformed file name {}", ex.getStackTrace());
	    } catch (ParserConfigurationException ex) {
	    	logger.warn("parse error {}", ex.getStackTrace());
	    } catch (SAXException ex) {
	    	logger.warn("sax error {}", ex.getStackTrace());
	    } catch (IOException ex) {
	    	logger.warn("general io error {}", ex.getStackTrace());
	    }
	}
	

	enum Scope {
		MULTICAST, GATEWAY;
	}
	class Load {
		public final Scope scope;
		public Load(Scope scope) {
			this.scope = scope;
		}
	}

	class PolicyHandler implements ContentHandler {

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void endDocument() throws SAXException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void endPrefixMapping(String prefix) throws SAXException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void ignorableWhitespace(char[] ch, int start, int length)
				throws SAXException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void processingInstruction(String target, String data)
				throws SAXException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setDocumentLocator(Locator locator) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void skippedEntity(String name) throws SAXException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void startDocument() throws SAXException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes atts) throws SAXException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void startPrefixMapping(String prefix, String uri)
				throws SAXException {
			// TODO Auto-generated method stub
			
		}
		
	}
	
}
