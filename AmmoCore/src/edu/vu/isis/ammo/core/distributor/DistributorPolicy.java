/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
 */
package edu.vu.isis.ammo.core.distributor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

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
import android.content.res.AssetManager;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import edu.vu.isis.ammo.api.IAmmoRequest;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.util.PrefixList;

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
	private static final Logger logger = LoggerFactory.getLogger("dist.policy");

	public static final String DEFAULT = "_default_";

	public final PrefixList<Topic> publishPolicy;
	public final PrefixList<Topic> postalPolicy;
	public final PrefixList<Topic> subscribePolicy;
	public final PrefixList<Topic> retrievalPolicy;

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
			logger.warn("no policy file {}, using and writing default", file);
			try {
				final AssetManager am = context.getAssets();
				final InputStream copiable = am.open(policy_file);

				// final InputStream copiable = context.getResources().openRawResource(R.raw.distribution_policy);				
				final OutputStream out = new FileOutputStream(file);
				final byte[] buf = new byte[1024];
				int len;
				while ((len = copiable.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				copiable.close();
				out.close();

				inputStream = am.open(policy_file);	

			} catch (NotFoundException ex) {
				logger.error("asset not available {}", ex.getMessage());
				return null;
			} catch (FileNotFoundException ex) {
				logger.error("file not available {}", ex.getMessage());
				return null;
			} catch (IOException ex) {
				logger.error("file not writable {}", ex.getMessage());
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
		this.publishPolicy = new PrefixList<Topic>();
		this.postalPolicy = new PrefixList<Topic>();
		this.subscribePolicy = new PrefixList<Topic>();
		this.retrievalPolicy = new PrefixList<Topic>();

		this.builder = new TopicBuilder();

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

		logger.trace("routing policy:\n {}",this);
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer();
		if (this.postalPolicy != null)
			for (final Topic entry : this.postalPolicy.values()) {
				sb.append('\n').append("POSTAL: \n")
				.append(entry);
			}
		if (this.subscribePolicy != null)
			for (final Topic entry : this.subscribePolicy.values()) {
				sb.append('\n').append("SUBSCRIBE: \n")
				.append(entry);
			}
		if (this.retrievalPolicy != null)
			for (final Topic entry : this.retrievalPolicy.values()) {
				sb.append('\n').append("RETRIEVAL: \n")
				.append(entry);
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
		this.publishPolicy = new PrefixList<Topic>();
		this.postalPolicy = new PrefixList<Topic>();
		this.subscribePolicy = new PrefixList<Topic>();
		this.retrievalPolicy = new PrefixList<Topic>();

		this.builder = new TopicBuilder();

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
	 * @param key
	 * @return
	 */
	public Topic matchPostal(String key) {
		return this.postalPolicy.longestPrefix(key);
	}
	public Topic matchSubscribe(String key) {
		return this.subscribePolicy.longestPrefix(key);
	}
	public Topic matchRetrieval(String key) {
		return this.retrievalPolicy.longestPrefix(key);
	}

	private int indent = 0;
	private String indent() {
		final StringBuilder sb = new StringBuilder();
		for (int ix = 0; ix < indent; ++ix) sb.append("  ");
		return sb.toString();
	}

	/**
	 * The topic (and topic builder) is primarily concerned
	 * with fusing messages which belong to that topic.
	 * Membership in the topic is by the topic type.
	 * The topic is selected by a best prefix match on the type.
	 * Each topic has a routing object.
	 */
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
			sb.append('\n').append(ind).append("routing: \"").append(this.type).append("\"")
			.append(this.routing);
			DistributorPolicy.this.indent--;
			return sb.toString();
		}

		public DistributorState makeRouteMap(final String channel) {
			final DistributorState state = this.routing.makeMap(channel);
			return state.setType(this.type);
		}

		private String type;
		public void setType(String type) {
			this.type = type;
		}
		public String getType() {
			return this.type;
		}
	}

	/**
	 * The routing is a rule for disposing of requests 
	 * which belong to the topic.
	 * 
	 * The routing expresses the properties which are 
	 * used to route messages which belong to a topic set.
	 * The route further divides messages by exact category.
	 * 
	 * The route has quality of service attributes:
	 * <ul>
	 * <li> priority : larger priority a better
	 * <li> lifespan : the maximum amount of time the request should be allowed to live (in milliseconds)
	 * </ul>
	 * 
	 * It is possible for the application to ask for a reduced lifespan.
	 * If the application asks for a longer lifespan it is quietly rejected.
	 * 
	 * The routing also consists of a set of (at least one) clauses, 
	 * each clause containing a set of (at least one) literal.
	 * In order for the routing/rule to evaluate to true all
	 * of the clauses must be individually true (conjunction).
	 * In order for a clause to be true at least one of its 
	 * literals must evaluate to true (disjunction).
	 * A literal is true if, at some time, the message condition
	 * was successful over the specified channel term.
	 * In other words, if the message was sent over a channel.
	 * 
	 * The makeMap method builds a disposal (DistributorState) object.
	 */
	public class Routing {
		public final int priority;
		public final long lifespan;
		public final Category category;
		public final List<Clause> clauses;

		public Routing(Category category, int priority, long lifespan) {
			this.category = category;
			this.priority = priority;
			this.lifespan = lifespan;
			this.clauses = new ArrayList<Clause>();
		}

		public DistributorState makeMap(final String channel) {
			final DistributorState map = DistributorState.newInstance(this, channel);
			for (Clause clause : this.clauses) {
				for (Literal literal : clause.literals) {
					map.put(literal.term, DisposalState.PENDING);
				}
			}
			return map;
		}
		@Override
		public String toString() {
			DistributorPolicy.this.indent++;
			final String ind = DistributorPolicy.this.indent();

			final StringBuffer sb = new StringBuffer();		
			sb.append('\n').append(ind)
			.append(" priority: ").append(this.priority)
			.append(" lifespan: ").append(this.lifespan);

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

		/**
		 * Select the earlier of the two expirations.
		 * 
		 * return the expiration time in milliseconds.
		 * The default was passed in.
		 * The maximum allowed expiration is the current time plus the maximum lifespan.
		 * 
		 * @param appLifespanSeconds
		 * @return
		 */
		public long getExpiration(long appLifespanSeconds) {
			if (this.lifespan < 0) return appLifespanSeconds;
			final long maxExpiration = System.currentTimeMillis() + this.lifespan;

			if (maxExpiration < appLifespanSeconds) {
				return maxExpiration;
			} else {
				return appLifespanSeconds;
			}
		}

		/**
		 * Select whichever priority is lesser.
		 * 
		 * @param priority
		 * @return
		 */
		public Integer getPriority(Integer appPriority) {
			if (this.priority < 0) return appPriority;
			if (this.priority < appPriority) {
				return this.priority;
			} else {
				return appPriority;
			}
		}
	}

	/**
	 * see Routing
	 */
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
		PUBLISH, POSTAL, SUBSCRIBE, RETRIEVAL;
	}


	/**
	 * Encoding is an indicator to the distributor as to how to encode/decode requests.
	 * It is a wrapper around the encoding type.
	 * The custom encoding is passed to the application specific coder.
	 * The name is primarily for the application specific coder.
	 */
	public static class Encoding {
		public enum Type {
			TERSE, JSON, CUSTOM;
		}
		final private Type type;

		final private String name;
		public String name() {
			return this.name;
		}

		private Encoding(String name, Type type) {
			this.type = type;
			this.name = name;
		}
		private Encoding(Type type) {
			this.type = type;
			this.name = null;
		}
		public static Encoding getDefault() {
			return new Encoding(Type.JSON);
		}
		public static Encoding newInstance(Type type) {
			return new Encoding(type);
		}

		public static Encoding newInstance(String typeName) {

			if (typeName.equalsIgnoreCase("json")) {
				return new Encoding(typeName, Encoding.Type.JSON );
			}
			if (typeName.equalsIgnoreCase("terse")) {
				return new Encoding(typeName, Encoding.Type.JSON );
			}
			return new Encoding(typeName, Encoding.Type.CUSTOM);
		}

		public Type getType() {
			return this.type;
		}

		public String getPayloadSuffix() {
			switch (this.type) {
			case JSON: return "";
			case TERSE: return "";
			case CUSTOM: return "_serial/";
			default: return "_serial/";
			}
		}
		public Uri extendProvider(Uri provider) {
			switch (this.type) {
			case JSON: return provider;
			case TERSE: return provider;
			case CUSTOM: return Uri.withAppendedPath(provider, "_serial/");
			default: return Uri.withAppendedPath(provider, "_serial/");
			}
		}
		@Override
		public String toString() {
			return new StringBuilder().append('[')
					.append(type.name()).append(']').toString();
		}
		public static Encoding getInstanceByName(String encoding) {
			for (final Type type : Encoding.Type.values()) {
				if (! type.name().equalsIgnoreCase(encoding)) continue;
				return new Encoding( type );
			}
			return null;
		}
	}

	/**
	 * The term is the channel name
	 * The condition is the delivery condition of the message over the named channel
	 * The encoding is the payload encoding type to be sent over the named channel
	 */
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
			.append("condition: ").append(this.condition).append(' ')
			.append("encoding: ").append(this.encoding);
			DistributorPolicy.this.indent--;
			return sb.toString();
		}
	}

	/**
	 * A builder for topics.
	 * The meta-data for a topic is a routing object.
	 * The routing object defaults to postal values.
	 *
	 */
	public class TopicBuilder {

		public TopicBuilder() {
			this.routing = new Routing(Category.POSTAL, 
					IAmmoRequest.PRIORITY_NORMAL, 
					DistributorDataStore.DEFAULT_POSTAL_LIFESPAN);
		}
		public Topic build() { return new Topic(this); }

		private Routing routing;
		public Routing routing() { return this.routing; }

		public TopicBuilder newRouting(Category category, int priority, long lifespan) { 
			this.routing = new Routing(category, priority, lifespan);
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

	private boolean inTest = false;

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
		if (! this.inTopic && ! this.inTest) {  
			if (localName.equals("topic")) {
				logger.debug("begin 'topic'");
				this.inTopic = true;
				String type = atts.getValue(uri, "type");
				if (type == null) return;
				this.builder.type(type);
				return;
			}
			if (localName.equals("test")) {
				logger.debug("begin 'test'");
				this.inTest = true;
				final String type = atts.getValue(uri, "type");
				if (type == null) return;
				final String title = (null == atts.getValue(uri, "name"))
						? type : atts.getValue(uri, "name");

				final String postalMatch = atts.getValue(uri, "postal");
				if (postalMatch != null) {
					final Topic topic = this.matchPostal(type);
					if (! topic.type.equals(postalMatch)) {
						logger.error("postal test {} failed {} != {}",
								new String[]{ title, topic.type, postalMatch });
					}
				}

				final String subscribeMatch = atts.getValue(uri, "subscribe");
				if (subscribeMatch != null) {
					final Topic topic = this.matchSubscribe(type);
					if (! topic.type.equals(subscribeMatch)) {
						logger.error("subscribe test {} failed {} != {}",
								new String[]{ title, topic.type, subscribeMatch });
					}
				}

				final String retrievalMatch = atts.getValue(uri, "retrieval");
				if (retrievalMatch != null) {
					final Topic topic = this.matchRetrieval(type);
					if (! topic.type.equals(retrievalMatch)) {
						logger.error("retrieval test {} failed {} != {}",
								new String[]{ title, topic.type, retrievalMatch });
					}
				}

				return;
			}
			logger.warn("expecting begin 'topic' or 'test': got {}", localName);
			return;
		} 
		// in policy/topic
		if (! this.inDescription && ! this.inRouting) { 
			if (localName.equals("routing")) { 
				logger.debug("begin 'routing'");
				this.inRouting = true;
				final Category category = extractCategory(uri,"category", Category.POSTAL, atts);
				final int priority = extractPriority(uri,"priority", IAmmoRequest.PRIORITY_NORMAL, atts);
				final long lifespan;
				switch (category) {
				case RETRIEVAL:
					lifespan = extractLifespan(uri,"lifespan", 
							DistributorDataStore.DEFAULT_RETRIEVAL_LIFESPAN, atts);
					break;
				case SUBSCRIBE:
					lifespan = extractLifespan(uri,"lifespan", 
							DistributorDataStore.DEFAULT_SUBSCRIBE_LIFESPAN, atts);
					break;
				case POSTAL:
				default:
					lifespan = extractLifespan(uri,"lifespan", 
							DistributorDataStore.DEFAULT_POSTAL_LIFESPAN, atts);
					break;
				}

				this.builder.newRouting(category, priority, lifespan);
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

		if (this.inTest) {
			if (localName.equals("test")) {
				logger.debug("end 'test'");
				this.inTest = false;
				return;
			}
			logger.error("processing test and found {}", localName);
			return;
		}

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
				topic.setType(builder.type());
				switch (builder.routing.category) {
				case POSTAL:
					this.postalPolicy.insert(topic.getType(), topic );
					break;
				case SUBSCRIBE:
					this.subscribePolicy.insert(topic.getType(), topic );
					break;
				case RETRIEVAL:
					this.retrievalPolicy.insert(topic.getType(), topic );
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
		return Encoding.newInstance(value);
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
	 * The lifespan specified in the policy is in minutes.
	 * Internally all times are in milliseconds. (conversion necessary)
	 * This is a maximum allowed lifespan, the application may
	 * set an expiration sooner.
	 * 
	 * @param uri
	 * @param attrname
	 * @param def
	 * @param atts
	 * @return
	 */
	private long extractLifespan(String uri, String attrname, long def, Attributes atts) {
		final String value = atts.getValue(uri, attrname);
		if (value == null) return def;

		try {
			return DistributorDataStore.CONVERT_MINUTES_TO_MILLISEC * Integer.parseInt(value);
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
		if (value.equalsIgnoreCase("publish")) return Category.PUBLISH;
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
