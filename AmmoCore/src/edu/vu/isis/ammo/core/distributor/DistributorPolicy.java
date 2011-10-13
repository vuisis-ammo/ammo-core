package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
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
import android.net.Uri;
import edu.vu.isis.ammo.api.IAmmoRequest;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.ChannelDisposal;

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
 * <li>routing</li>
 * </ul>
 * 
 *
 */
public class DistributorPolicy implements ContentHandler {
	private static final Logger logger = LoggerFactory.getLogger("ammo-dp");

	public static final String DEFAULT = "_default_";

	public final Trie<String, Topic> postalPolicy;
	public final Trie<String, Topic> subscribePolicy;
	public final Trie<String, Topic> retrievalPolicy;
	
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

		final InputStream inputStream;
		if (file.exists()) {
			try {
				inputStream = new FileInputStream(file);
			} catch (FileNotFoundException ex) {
				logger.error("no policy file {} {}", file, ex.getStackTrace());
				return null;
			}
		}
		else {
			logger.warn("no policy file {}, using default instead", file);
			try {
				inputStream = context.getResources().openRawResource(R.raw.distribution_policy);
			} catch (NotFoundException ex) {
				logger.error("asset not available {}", ex.getMessage());
				return null;
			}
		}
		final InputSource is = new InputSource(new InputStreamReader(inputStream));
		final DistributorPolicy policy = new DistributorPolicy(is);
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
		this.postalPolicy = new PatriciaTrie<String, Topic>(StringKeyAnalyzer.BYTE);
		this.subscribePolicy = new PatriciaTrie<String, Topic>(StringKeyAnalyzer.BYTE);
		this.retrievalPolicy = new PatriciaTrie<String, Topic>(StringKeyAnalyzer.BYTE);
		
		this.builder = new TopicBuilder(Category.POSTAL, IAmmoRequest.PRIORITY_NORMAL);

		if (is == null) {
			logger.debug("loading default rule");
			this.setDefaultRule();
			return;
		}
		try {
			final SAXParserFactory parserFactory=SAXParserFactory.newInstance();
			final SAXParser saxParser=parserFactory.newSAXParser();
			final XMLReader reader=saxParser.getXMLReader();

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
		for (final Entry<String, Topic> entry : this.postalPolicy.entrySet()) {
			sb.append('\n').append("POSTAL")
			.append('\n').append(" topic \"").append(entry.getKey()).append('"')
			.append(entry.getValue() );
		}
		for (final Entry<String, Topic> entry : this.subscribePolicy.entrySet()) {
			sb.append('\n').append("SUBSCRIBE")
			.append('\n').append(" topic \"").append(entry.getKey()).append('"')
			.append(entry.getValue() );
		}
		for (final Entry<String, Topic> entry : this.retrievalPolicy.entrySet()) {
			sb.append('\n').append("RETRIEVAL")
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
		this.postalPolicy = new PatriciaTrie<String, Topic>(StringKeyAnalyzer.BYTE);
		this.subscribePolicy = new PatriciaTrie<String, Topic>(StringKeyAnalyzer.BYTE);
		this.retrievalPolicy = new PatriciaTrie<String, Topic>(StringKeyAnalyzer.BYTE);
		
		this.builder = new TopicBuilder(Category.POSTAL, IAmmoRequest.PRIORITY_NORMAL);

		switch (testSetId) {
		default:
			this.setDefaultRule();

			this.builder.type("urn:test:domain/trial/both")
			.addClause()
			.addLiteral("gateway", true, Encoding.getDefault())
			.addLiteral("multicast", true, Encoding.getDefault())
			.build();
		}
	}


	/**
	 * A rule to catch all patterns which don't match anything else.
	 */
	public void setDefaultRule() {
		this.builder
		.type("")
		.addClause().addLiteral("gateway", true, Encoding.getDefault())
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
	public Topic matchPostal(String key) {
		return this.postalPolicy.selectValue(key);
	}
	public Topic matchSubscribe(String key) {
		return this.subscribePolicy.selectValue(key);
	}
	public Topic matchRetrieval(String key) {
		return this.retrievalPolicy.selectValue(key);
	}

	private int indent = 0;
	private String indent() {
		final StringBuilder sb = new StringBuilder();
		for (int ix = 0; ix < indent; ++ix) sb.append("  ");
		return sb.toString();
	}

	public class Topic {
		public final Routing routing;

		public Topic(TopicBuilder builder) {
			this.routing = builder.routing();
		}
		@Override
		public String toString() {
			DistributorPolicy.this.indent++;
			final String ind = DistributorPolicy.this.indent();

			final StringBuffer sb = new StringBuffer();
			sb.append('\n').append(ind).append("routing:  ").append(this.routing);
			DistributorPolicy.this.indent--;
			return sb.toString();
		}

		public DistributorState makeRouteMap() {
			return this.routing.makeMap();
		}
	}

	public class Routing {
		public final int priority;
		public final Category category;
		public final List<Clause> clauses;
		
		public Routing(Category category, int priority) {
			this.category = category;
			this.priority = priority;
			this.clauses = new ArrayList<Clause>();
		}
		public DistributorState makeMap() {
			final DistributorState map = DistributorState.newInstance(this);
			for (Clause clause : this.clauses) {
				for (Literal literal : clause.literals) {
					map.put(literal.term, ChannelDisposal.PENDING);
				}
			}
			return map;
		}
		@Override
		public String toString() {
			DistributorPolicy.this.indent++;
			final String ind = DistributorPolicy.this.indent();

			final StringBuffer sb = new StringBuffer();		
			sb.append('\n').append(ind).append("priority: ").append(this.priority);
			
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
		public void addLiteral(String term, Boolean condition, Encoding encoding) {
			this.workingClause.addLiteral(new Literal(term, condition, encoding));
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
			for (final Literal literal : this.literals) { 
				sb.append(literal);
			}
			DistributorPolicy.this.indent--;
			return sb.toString();
		}
		public void addLiteral(Literal literal) {
			this.literals.add(literal);
		}
	}
	
	public enum Category {
		POSTAL, SUBSCRIBE, RETRIEVAL;
	}
	

	/**
	 * Encoding is an indicator to the distributor as to how to encode/decode requests.
	 * Encoding is stored as an array, where the first is the encoding of the 
	 */
	public static class Encoding implements Iterable<Encoding.Type> {
		public enum Type {
			TERSE, JSON, CUSTOM;
		}
		final private Type[] list;

		private Encoding(Type...types) {
			this.list = types;
		}
		public static Encoding getDefault() {
			return new Encoding(Type.JSON);
		}
		public static Encoding newInstance(Type...types) {
			return new Encoding(types);
		}
		@Override
		public Iterator<Type> iterator() {
			return Arrays.asList(this.list).iterator();
		}
		public Type[] asArray() {
			return this.list;
		}
		public Type getPayload() {
			switch (this.list.length){
			case 0: return Type.JSON;
			}
			return this.list[0];
		}
		public Type getMessage() {
			switch (this.list.length){
			case 0: return Type.JSON;
			case 1: return this.list[0];
			}
			return this.list[1];
		}
		public Type getHeader() {
			switch (this.list.length){
			case 0: return Type.JSON;
			case 1: return this.list[0];
			case 2: return this.list[1];
			}
			return this.list[2];
		}
		public String getPayloadSuffix() {
			switch (getPayload()) {
			case JSON: return "";
			case TERSE: return "";
			case CUSTOM: return "_serial/";
			default: return "_serial/";
			}
		}
		public Uri extendProvider(Uri provider) {
			switch (getPayload()) {
			case JSON: return provider;
			case TERSE: return provider;
			case CUSTOM: return Uri.withAppendedPath(provider, "_serial/");
			default: return Uri.withAppendedPath(provider, "_serial/");
			}
		}
		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder().append('[');
			for( final Type type : this.list) {
				sb.append(type.name()).append(',');
			}
			return sb.append(']').toString();
		}
		public static Encoding getInstanceByName(String...encoding) {
			final Type[] typeArray = new Type[encoding.length];
			for (int ix = 0; ix < encoding.length; ix++) {
				final String typeStr = encoding[ix];

				for (final Type type : Encoding.Type.values()) {
					if (! type.name().equalsIgnoreCase(typeStr)) continue;
					typeArray[ix] = type;
				}
			}
			return new Encoding(typeArray);
		}
	}

	public class Literal {
		public final String term;
		public final boolean condition;
		public final Encoding encoding;

		public Literal(String term, boolean condition, Encoding encoding) {
			this.term = term;
			this.condition = condition;
			this.encoding = encoding;
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

	public class TopicBuilder {

		public TopicBuilder(Category category, int priority) {
			this.routing = new Routing(category, priority);
		}
		public Topic build() { return new Topic(this); }

		private Routing routing;
		public Routing routing() { return this.routing; }
		public TopicBuilder newRouting(Category category, int priority) { 
			this.routing = new Routing(category, priority);
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
		public TopicBuilder addLiteral(String term, Boolean condition, Encoding encoding) {
			this.routing.addLiteral(term, condition, encoding);
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
	private boolean inRouting = false;
	private boolean inClause = false;
	private boolean inLiteral = false;

	private boolean inDescription = false;

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {
		if (! this.inPolicy) {  
			if (localName.equals("policy")) {
				logger.debug("begin 'policy'");
				this.inPolicy = true;
				return;
			}
			logger.warn("expecting begin 'policy': got {}", localName);
			return;
		} 
		// in policy
		if (! this.inTopic) {  
			if (localName.equals("topic")) {
				logger.debug("begin 'topic'");
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
		if (! this.inDescription && ! this.inRouting) { 
			if (localName.equals("routing")) { 
				logger.debug("begin 'routing'");
				this.inRouting = true;
				final Category category = extractCategory(uri,"category", Category.POSTAL, atts);
				final int priority = extractPriority(uri,"priority", IAmmoRequest.PRIORITY_NORMAL, atts);
				this.builder.newRouting(category, priority);
				return;
			}
			if (localName.equals("description")) {
				logger.debug("begin topic 'description'");
				this.inDescription = true;
				return;
			}
			logger.warn("expecting begin 'routing' or 'description': got {}", localName);
			return; 
		}
		// in policy/topic/routing
		if (! this.inDescription && ! this.inClause){
			if (localName.equals("clause")) {
				logger.debug("begin 'clause'");
				this.inClause = true;
				this.builder.addClause();
				return;
			}
			if (localName.equals("description")) {
				logger.debug("begin routing 'description'");
				this.inDescription = true;
				logger.debug("processing route description");
				return;
			}
			logger.warn("expecting begin 'clause' or 'description': got {}", localName);
			return;
		}
		// in policy/topic/routing/clause
		if (! this.inLiteral) {
			if (localName.equals("literal")) {
				logger.debug("begin 'literal'");
				this.inLiteral = true;
				final String term = extractTerm(uri,"term" , "gateway", atts);
				final Boolean condition = extractCondition(uri,"condition", true, atts);
				final Encoding encoding = extractEncoding(uri,"encoding", Encoding.getDefault(), atts);
				this.builder.addLiteral(term, condition, encoding);
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
				logger.debug("end 'policy'");
				this.inPolicy = false;
				return;
			}
			logger.error("topic ended prematurely expecting policy got {}", localName);
			return;
		} 
		// in policy/topic
		if (! this.inRouting) { 
			if (localName.equals("topic")) {
				
				logger.debug("end 'topic'");
				this.inTopic = false;
				return;
			}
			if (localName.equals("description")) {
				logger.debug("end 'topic/description'");
				this.inDescription = false;
				return;
			}
			logger.error("expecting end 'topic' or 'description' got {}", localName);
			return;
		} 
		// in policy/topic/routing
		if (! this.inClause){
			if (localName.equals("routing")) { 
				final Topic topic = builder.build();
				switch (builder.routing.category) {
				case POSTAL:
					this.postalPolicy.put(builder.type(), topic );
					break;
				case SUBSCRIBE:
					this.subscribePolicy.put(builder.type(), topic );
					break;
				case RETRIEVAL:
					this.retrievalPolicy.put(builder.type(), topic );
					break;
				}
				logger.debug("end 'routing'");
				this.inRouting = false;
				return;
			}
			if (localName.equals("description")) {
				logger.debug("end 'routing/description'");
				this.inDescription = false;
				return;
			}
			logger.error("expecting end 'routing' or 'description' got {}", localName);
			return;
		} 
		if (! this.inLiteral) {
			// in policy/topic/routing/clause
			if (localName.equals("clause")) {
				logger.debug("end 'clause'");
				this.inClause = false;
				return;
			}
			logger.error("expecting end 'clause' got {}", localName);
			return;
		}
		// in policy/topic/routing/clause/literal
		if (localName.equals("literal")) {
			logger.debug("end 'literal'");
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
		final String value = atts.getValue(uri, attrname);
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
	private Encoding extractEncoding(String uri, String attrname, Encoding def, Attributes atts) {
		final String value = atts.getValue(uri, attrname);
		if (value == null) return def;
		if (value.equalsIgnoreCase("verbose")) 
			return Encoding.newInstance( Encoding.Type.JSON, Encoding.Type.JSON, Encoding.Type.JSON);
		if (value.equalsIgnoreCase("verbose")) 
			return  Encoding.newInstance( Encoding.Type.TERSE, Encoding.Type.TERSE, Encoding.Type.TERSE );
		return def;
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
	private String extractTerm(String uri, String attrname, String def, Attributes atts) {
		final String value = atts.getValue(uri, attrname);
		if (value == null) return def;
		return value;
	}

	/**
	 * A helper routine to extract an attribute from the xml element and 
	 * convert it into an integer.
	 * 
	 * @param uri
	 * @param attrname
	 * @param def
	 * @param atts
	 * @return
	 */
	private int extractPriority(String uri, String attrname, int def, Attributes atts) {
		final String value = atts.getValue(uri, attrname);
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
	
	/**
	 * A helper routine to extract an attribute from the xml element and 
	 * convert it into an integer.
	 * 
	 * @param uri
	 * @param attrname
	 * @param def
	 * @param atts
	 * @return
	 */
	private Category extractCategory(String uri, String attrname, Category def, Attributes atts) {
		final String value = atts.getValue(uri, attrname);
		if (value == null) return def;
		if (value.equalsIgnoreCase("postal")) return Category.POSTAL;
		if (value.equalsIgnoreCase("subscribe")) return Category.SUBSCRIBE;
		if (value.equalsIgnoreCase("retrieval")) return Category.RETRIEVAL;
		return def;
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
