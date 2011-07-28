package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import edu.vu.isis.ammo.api.IAmmoRequest;

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
public class DistributorPolicy implements ContentHandler {
    private static final Logger logger = LoggerFactory.getLogger(DistributorPolicy.class);
    
    public final Trie<String, Load> policy;
    public final static String policy_file = "distribution_policy.xml";
    
    private LoadBuilder lb;
    
    /**
     * Presume a context specific xml file is present.
     *  e.g. /data/data/edu.vu.isis.ammo/app_distribution_policy.xml
     *  
     * @param context
     */
    public static DistributorPolicy newInstance(Context context) {
        File file = context.getDir(policy_file, Context.MODE_WORLD_READABLE);
        try {
            InputSource is = new InputSource(new InputStreamReader(new FileInputStream(file)));
            return new DistributorPolicy(is);
        } catch (FileNotFoundException ex) {
            logger.error("no policy file {}", ex.getStackTrace());
        }
        return null;
    }
    /**
     * Load the policy information from an xml file
     * @param file
     */
    public DistributorPolicy(InputSource is ) {
        this.policy = new PatriciaTrie<String, Load>(StringKeyAnalyzer.BYTE);
        this.lb = new LoadBuilder();
        
        try {
            SAXParserFactory parserFactory=SAXParserFactory.newInstance();
            SAXParser saxParser=parserFactory.newSAXParser();
            XMLReader reader=saxParser.getXMLReader();
            
            reader.setContentHandler(this);
            
            reader.parse(is);
            
        } catch (MalformedURLException ex) {
            logger.warn("malformed file name {}", ex.getStackTrace());
            this.setDefaultRule();
        } catch (ParserConfigurationException ex) {
            logger.warn("parse error {}", ex.getStackTrace());
            this.setDefaultRule();
        } catch (SAXException ex) {
            logger.warn("sax error {}", ex.getStackTrace());
            this.setDefaultRule();
        } catch (IOException ex) {
            logger.warn("general io error {}", ex.getStackTrace());
            this.setDefaultRule();
        }
    }
    
    /**
     * The following constructor is for testing only.
     * 
     * @param context
     * @param dummy
     */
    public DistributorPolicy(Context context, int testSetId) {
        this.policy = new PatriciaTrie<String, Load>(StringKeyAnalyzer.BYTE);
        this.lb = new LoadBuilder();
        
        switch (testSetId) {
        default:
            this.policy.put("urn:test:domain/trial/both",  
                    this.lb
                    .isGateway(true)
                    .isMulticast(true)
                    .build());
            this.policy.put("urn:test:domain/trial/gw-only",  
                    this.lb
                    .isGateway(true)
                    .isMulticast(false)
                    .build());
            this.policy.put("urn:test:domain/trial/mc-only",  
                    this.lb
                    .isGateway(false)
                    .isMulticast(true)
                    .build());
            this.policy.put("urn:test:domain/trial/neither",  
                    this.lb
                    .isGateway(false)
                    .isMulticast(false)
                    .build());
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
        public final int priority;
        
        private Load(LoadBuilder builder) {
            this.isMulticast = builder.isMulticast();
            this.isGateway = builder.isGateway();
            this.priority = builder.priority();
        }
    }
    
    class LoadBuilder {
        private boolean isMulticast;
        public boolean isMulticast() { return this.isMulticast; }
        public LoadBuilder isMulticast(boolean val) {  this.isMulticast = val; return this; }
        
        private boolean isGateway;
        public boolean isGateway() { return this.isGateway; }
        public LoadBuilder isGateway(boolean val) {  this.isGateway = val; return this; }
        
        private int priority;
        public int priority() { return this.priority; }
        public LoadBuilder priority(int val) {  this.priority = val; return this; }

        public LoadBuilder() {}
        public Load build() { return new Load(this); }
    }


        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {}

        @Override
        public void endDocument() throws SAXException {}

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {}

        @Override
        public void endPrefixMapping(String prefix) throws SAXException {}

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}

        @Override
        public void processingInstruction(String target, String data) throws SAXException {}

        @Override
        public void setDocumentLocator(Locator locator) {}

        @Override
        public void skippedEntity(String name) throws SAXException {}

        @Override
        public void startDocument() throws SAXException {
            this.setDefaultRule();
        }
        
        /**
         * A rule to catch all patterns which don't match anything else.
         */
        public void setDefaultRule() {
            this.policy.put("application/vnd.com.aterrasys.nevada.", 
                 this.lb
                     .isGateway(true)
                     .isMulticast(true)
                     .build());
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes atts) throws SAXException {
            
            if (localName.equals("topic")) {
                String type = atts.getValue(uri, "type");
                if (type == null) return;
                this.policy.put(type,  
                    this.lb
                        .isGateway(extractBoolean(uri,"isGateway", true, atts))
                        .isMulticast(extractBoolean(uri,"isMulticast", false, atts))
                        .priority(extractInteger(uri,"priority", IAmmoRequest.NORMAL_PRIORITY, atts))
                        .build());
            }
        }
        
        /**
         * A helper routine to extract an attribute from the xml element and 
         * convert it into a boolean.
         * 
         * @param uri
         * @param attrname
         * @param def
         * @param atts
         * @return
         */
        private boolean extractBoolean(String uri, String attrname, boolean def, Attributes atts) {
            String value = atts.getValue(uri, attrname);
            if (value == null) return def;
            return Boolean.parseBoolean(value);
        }
        
        
        /**
         * A helper routine to extract an attribute from the xml element and 
         * convert it into a boolean.
         * 
         * @param uri
         * @param attrname
         * @param def
         * @param atts
         * @return
         */
        private int extractInteger(String uri, String attrname, int def, Attributes atts) {
            String value = atts.getValue(uri, attrname);
            if (value == null) return def;
            return Integer.parseInt(value);
        }

        @Override
        public void startPrefixMapping(String prefix, String uri)  throws SAXException { }
    
}
