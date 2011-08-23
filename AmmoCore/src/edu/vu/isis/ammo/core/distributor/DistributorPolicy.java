package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

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
import android.content.res.Resources.NotFoundException;
import edu.vu.isis.ammo.api.AmmoRequest;
import edu.vu.isis.ammo.api.IAmmoRequest;
import edu.vu.isis.ammo.core.R;

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
	private static final Logger logger = LoggerFactory.getLogger("ammo.dp");

	public final Trie<String, Topic> policy;
	public final static String policy_dir = "policy";
	public final static String policy_file = "distribution_policy.xml";

	private TopicBuilder builder;

	/**
	 * Presume a context specific xml file is present.
	 *  e.g. /data/data/edu.vu.isis.ammo/app_distribution_policy.xml
	 *  
	 * @param context
	 */
	public static DistributorPolicy newInstance(Context context) {
		final File dir = context.getDir(policy_dir, Context.MODE_WORLD_READABLE);
		final File file = new File(dir, policy_file);

		InputStream inputStream = null;
		if (file.exists()) {
			try {
				inputStream = new FileInputStream(file);
			} catch (FileNotFoundException ex) {
				logger.error("no policy file {} {}", file, ex.getStackTrace());
			}
		}
		else {
			try {
				inputStream = context.getResources().openRawResource(R.raw.routing_policy);
			} catch (NotFoundException ex) {
				logger.error("asset not available {}", ex.getMessage());
			}
		}
		InputSource is = new InputSource(new InputStreamReader(inputStream));
		DistributorPolicy policy = new DistributorPolicy(is);
		try {
			inputStream.close();
		} catch (IOException ex) {
			logger.error("could not close distributor configuration file {}",
					ex.getStackTrace());
		}
		return policy;
	}
	/**
	 * Load the policy information from an xml file
	 * @param file
	 */
	public DistributorPolicy(InputSource is ) {
		this.policy = new PatriciaTrie<String, Topic>(StringKeyAnalyzer.BYTE);
		this.builder = new TopicBuilder();

		if (is == null) {
			logger.debug("loading default rule");
			this.setDefaultRule();
			return;
		}
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
	
		logger.info("routing policy:\n {}",this);
	}
	
	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer();
		for (Entry<String, Topic> entry : this.policy.entrySet()) {
			sb
			.append('\n').append(" topic \"").append(entry.getKey()).append('"')
			.append(entry.getValue() );
		}
		return sb.toString();
	}

	/**
	 * The following constructor is for testing only.
	 * 
	 * @param context
	 * @param dummy
	 */
	public DistributorPolicy(Context context, int testSetId) {
		this.policy = new PatriciaTrie<String, Topic>(StringKeyAnalyzer.BYTE);
		this.builder = new TopicBuilder();

		switch (testSetId) {
		default:
			this.setDefaultRule();
			this.builder.priority(AmmoRequest.PRIORITY_NORMAL);
			
			this.builder.type("urn:test:domain/trial/both")
			.addClause()
			    .addLiteral(Term.GATEWAY, true)
			    .addLiteral(Term.MULTICAST, true)
			.build();
		}
	}


	/**
	 * A rule to catch all patterns which don't match anything else.
	 */
	public void setDefaultRule() {
		this.builder
		.type("")
		.priority(AmmoRequest.PRIORITY_NORMAL)
		.addClause().addLiteral(Term.GATEWAY, true)
		.build();
	}

	/**
	 * Find the 'best' key match.
	 * The best match is the shortest string which matches the key.
	 * 
	 * 
	 * @param key
	 * @return
	 */
	public Topic match(String key) {
		return this.policy.selectValue(key);
	}

	private int indent = 0;
	private String indent() {
		StringBuilder sb = new StringBuilder();
		for (int ix = 0; ix < indent; ++ix) sb.append("  ");
		return sb.toString();
	}
	
	public class Topic {
		public final int priority;
		public final Routing routing;

		public Topic(TopicBuilder builder) {
			this.priority = builder.priority();
			this.routing = builder.routing();
		}
		@Override
		public String toString() {
			DistributorPolicy.this.indent++;
			final String ind = DistributorPolicy.this.indent();
			
			final StringBuffer sb = new StringBuffer();
			sb.append('\n').append(ind).append("priority: ").append(this.priority)
			  .append('\n').append(ind).append("routing:  ").append(this.routing);
			DistributorPolicy.this.indent--;
			return sb.toString();
		}
	}

	public class Routing {
		public final List<Clause> clauses;
		public Routing() {
			this.clauses = new ArrayList<Clause>();
		}
		@Override
		public String toString() {
			DistributorPolicy.this.indent++;
			
			final StringBuffer sb = new StringBuffer();
			for (Clause clause : this.clauses) { 
			  sb.append(clause);
			}
			DistributorPolicy.this.indent--;
			return sb.toString();
		}
		
		public void clear() {
			this.clauses.clear();
		}
		
		private Clause workingClause;
		public void addClause() {
			this.workingClause = new Clause();
			this.clauses.add(this.workingClause);
		}
		public void addLiteral(Term term, Boolean condition) {
			this.workingClause.addLiteral(new Literal(term, condition));
		}
	}

	public class Clause {
		public final List<Literal> literals;
		public Clause() {
			this.literals = new ArrayList<Literal>();
		}
		@Override
		public String toString() {
			DistributorPolicy.this.indent++;
			final String ind = DistributorPolicy.this.indent();
			
			final StringBuffer sb = new StringBuffer();
			sb.append('\n').append(ind).append("clause:");
			for (Literal literal : this.literals) { 
			  sb.append(literal);
			}
			DistributorPolicy.this.indent--;
			return sb.toString();
		}
		public void addLiteral(Literal literal) {
			this.literals.add(literal);
		}
	}

	public class Literal {
		public final Term term;
		public final boolean condition;
		public Literal(Term term, boolean condition) {
			this.term = term;
			this.condition = condition;
		}
		@Override
		public String toString() {
			DistributorPolicy.this.indent++;
			final String ind = DistributorPolicy.this.indent();
			
			final StringBuffer sb = new StringBuffer();
			sb.append('\n').append(ind)
			  .append("term: ").append(this.term).append(' ')
			  .append("condition: ").append(this.condition);
			DistributorPolicy.this.indent--;
			return sb.toString();
		}
	}

	public enum Term {
		MULTICAST, GATEWAY, SERIAL;
		@Override
		public String toString() {
			switch (this) {
			case MULTICAST: return "M";
			case GATEWAY: return "G";
			case SERIAL: return "S";
			}
			return "U";
		}
	}

	public class TopicBuilder {

		public TopicBuilder() {
			this.routing = new Routing();
		}
		public Topic build() { return new Topic(this); }

		private int priority;
		public int priority() { return this.priority; }
		public TopicBuilder priority(int val) {  this.priority = val; return this; }


		private Routing routing;
		public Routing routing() { return this.routing; }
		public TopicBuilder newRouting() { 
			this.routing = new Routing();
			return this;
		}

		private String type;
		public TopicBuilder type(String val) {
			this.type = val;
			return this;
		}
		public String type() {
			return this.type;
		}
		
		public TopicBuilder addClause() {
			this.routing.addClause();
			return this;
		}
		public TopicBuilder addLiteral(Term term, Boolean condition) {
			this.routing.addLiteral(term, condition);
			return this;
		}
	}


	@Override
	public void startDocument() throws SAXException {
		this.setDefaultRule();
	}

	@Override
	public void endDocument() throws SAXException {}


	private boolean inPolicy = false;
	private boolean inTopic = false;
	private boolean inPriority = false;
	private boolean inRouting = false;
	private boolean inClause = false;
	private boolean inLiteral = false;
	
	private boolean inDescription = false;

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {
		if (! this.inPolicy) {  
			if (localName.equals("policy")) {
				logger.info("begin 'policy'");
				this.inPolicy = true;
				return;
			}
			logger.warn("expecting begin 'policy': got {}", localName);
			return;
		} 
		// in policy
		if (! this.inTopic) {  
			if (localName.equals("topic")) {
				logger.info("begin 'topic'");
				this.inTopic = true;
				String type = atts.getValue(uri, "type");
				if (type == null) return;
				this.builder.type(type);
				return;
			}
			logger.warn("expecting begin 'topic': got {}", localName);
			return;
		} 
		// in policy/topic
		if (! this.inDescription && ! this.inPriority && ! this.inRouting) { 
			if (localName.equals("routing")) { 
				logger.info("begin 'routing'");
				this.inRouting = true;
				this.builder.newRouting();
				return;
			}
			if (localName.equals("priority")) {
				logger.info("begin 'priority'");
				this.inPriority = true;
				this.builder.priority(extractPriority(uri,"value", 
						IAmmoRequest.PRIORITY_NORMAL, atts));
				return;
			}
			if (localName.equals("description")) {
				logger.info("begin topic 'description'");
				this.inDescription = true;
				return;
			}
			logger.warn("expecting begin 'routing' or 'priority': got {}", localName);
			return; 
		}
		// in policy/topic/routing
		if (! this.inDescription && ! this.inClause){
			if (localName.equals("clause")) {
				logger.info("begin 'clause'");
				this.inClause = true;
				this.builder.addClause();
				return;
			}
			if (localName.equals("description")) {
				logger.info("begin routing 'description'");
				this.inDescription = true;
				logger.info("processing route description");
				return;
			}
			logger.warn("expecting begin 'clause' or 'description': got {}", localName);
			return;
		}
		// in policy/topic/routing/clause
		if (! this.inLiteral) {
			if (localName.equals("literal")) {
				logger.info("begin 'literal'");
				this.inLiteral = true;
				Term term = extractTerm(uri,"term" , Term.GATEWAY, atts);
				Boolean condition = extractCondition(uri,"condition", true, atts);
				this.builder.addLiteral(term, condition);
				return;
			}
			logger.warn("expecting begin 'literal': got {}", localName);
		}
		// in policy/topic/routing/clause/literal
		logger.warn("expecting <nothing>: got {}", localName);
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (! this.inPolicy) { 
			logger.error("excess elements {}", localName);
			return;
		} 
		// in policy
		if (! this.inTopic) { 
			if (localName.equals("policy")) {
				logger.info("end 'policy'");
				this.inPolicy = false;
				return;
			}
			logger.error("topic ended prematurely expecting policy got {}", localName);
			return;
		} 
		// in policy/topic
		if (! this.inRouting) { 
			if (localName.equals("topic")) {
				Topic topic = builder.build();
				this.policy.put(builder.type(), topic );
				logger.info("end 'topic'");
				this.inTopic = false;
				return;
			}
			if (localName.equals("priority")) {
				logger.info("end 'topic/priority'");
				this.inPriority = false;
				return;
			}
			if (localName.equals("description")) {
				logger.info("end 'topic/description'");
				this.inDescription = false;
				return;
			}
			logger.error("expecting end 'topic' or 'description' got {}", localName);
			return;
		} 
		// in policy/topic/routing
		if (! this.inClause){
			if (localName.equals("routing")) { 
				logger.info("end 'routing'");
				this.inRouting = false;
				return;
			}
			if (localName.equals("description")) {
				logger.info("end 'routing/description'");
				this.inDescription = false;
				return;
			}
			logger.error("expecting end 'routing' or 'description' got {}", localName);
			return;
		} 
		if (! this.inLiteral) {
			// in policy/topic/routing/clause
			if (localName.equals("clause")) {
				logger.info("end 'clause'");
				this.inClause = false;
				return;
			}
			logger.error("expecting end 'clause' got {}", localName);
			return;
		}
		// in policy/topic/routing/clause/literal
		if (localName.equals("literal")) {
			logger.info("end 'literal'");
			this.inLiteral = false;
			return;
		}
		logger.error("expecting end 'literal' got {}", localName);
		return;
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
	private boolean extractCondition(String uri, String attrname, boolean def, Attributes atts) {
		String value = atts.getValue(uri, attrname);
		if (value == null) return def;
		if (value.equalsIgnoreCase("success")) return true;
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
	private Term extractTerm(String uri, String attrname, Term def, Attributes atts) {
		String value = atts.getValue(uri, attrname);
		if (value == null) return def;
		if (value.equalsIgnoreCase("multicast")) return Term.MULTICAST;
		if (value.equalsIgnoreCase("serial")) return Term.SERIAL;
		if (value.equalsIgnoreCase("gateway")) return Term.GATEWAY;
		logger.error("invalid term {}", value);
		return null;
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
	private int extractPriority(String uri, String attrname, int def, Attributes atts) {
		String value = atts.getValue(uri, attrname);
		if (value == null) return def;
		if (value.equalsIgnoreCase("default")) return IAmmoRequest.PRIORITY_DEFAULT;
		if (value.equalsIgnoreCase("background")) return IAmmoRequest.PRIORITY_BACKGROUND;
		if (value.equalsIgnoreCase("low")) return IAmmoRequest.PRIORITY_LOW;
		if (value.equalsIgnoreCase("normal")) return IAmmoRequest.PRIORITY_NORMAL;
		if (value.equalsIgnoreCase("high")) return IAmmoRequest.PRIORITY_HIGH;
		if (value.equalsIgnoreCase("urgent")) return IAmmoRequest.PRIORITY_URGENT;
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			return def;
		}
	}

	@Override
	public void startPrefixMapping(String prefix, String uri)  throws SAXException { }
	
	@Override
	public void endPrefixMapping(String prefix) throws SAXException {}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}

	@Override
	public void processingInstruction(String target, String data) throws SAXException {}

	@Override
	public void setDocumentLocator(Locator locator) {}

	@Override
	public void skippedEntity(String name) throws SAXException {}


}