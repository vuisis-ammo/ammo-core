package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.ardverk.collection.PatriciaTrie;
import org.ardverk.collection.StringKeyAnalyzer;
import org.ardverk.collection.Trie;
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
public class DistributionPolicy implements ContentHandler {
    private static final Logger logger = LoggerFactory.getLogger(DistributionPolicy.class);
    
    public final Trie<String, Load> policy;
    public final String policy_file = "distribution_policy.xml";
    
    private LoadBuilder lb;
    
    public DistributionPolicy(Context context) {
        this.policy = new PatriciaTrie<String, Load>(StringKeyAnalyzer.BYTE);
        this.lb = new LoadBuilder();
        
        try {
            File file = context.getDir(policy_file, Context.MODE_WORLD_READABLE);
            SAXParserFactory parserFactory=SAXParserFactory.newInstance();
            SAXParser saxParser=parserFactory.newSAXParser();
            XMLReader reader=saxParser.getXMLReader();
            
            reader.setContentHandler(this);
            
            reader.parse(new InputSource(new InputStreamReader(new FileInputStream(file))));
            
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
    
    /**
     * Find the 'best' key match.
     * The best match is the shortest string which matches the key.
     * 
     * 
     * @param key
     * @return
     */
    public Load match(String key) {
        return this.policy.selectValue(key);
    }
    
    class Load {
        public final boolean isMulticast;
        public final boolean isGateway;
        
        private Load(LoadBuilder builder) {
            this.isMulticast = builder.isMulticast();
            this.isGateway = builder.isGateway();
        }
    }
    
    class LoadBuilder {
        private boolean isMulticast;
        private boolean isGateway;
        public boolean isMulticast() { return this.isMulticast; }
        public boolean isGateway() { return this.isGateway; }
        public LoadBuilder isMulticast(boolean val) {  this.isMulticast = val; return this; }
        public LoadBuilder isGateway(boolean val) {  this.isGateway = val; return this; }
        
        public LoadBuilder() {}
        public Load build() { return new Load(this); }
    }


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
            this.policy.put("application/vnd.com.aterrasys.nevada.", 
                 this.lb.isGateway(true).isMulticast(true).build());
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes atts) throws SAXException {
            if (localName.equals("topic")) {
                this.policy.put(atts.getValue(uri, "type"),  this.lb
                    .isGateway(extractBoolean(uri,"isGateway", true, atts))
                    .isMulticast(extractBoolean(uri,"isMulticast", false, atts))
                    .build());
            }
        }
        
        private boolean extractBoolean(String uri, String attrname, boolean def, Attributes atts) {
            String value = atts.getValue(uri, attrname);
            if (value == null) return def;
            return Boolean.parseBoolean(value);
        }

        @Override
        public void startPrefixMapping(String prefix, String uri)
                throws SAXException {
            // TODO Auto-generated method stub
            
        }
    
}
