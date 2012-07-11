package edu.vu.isis.ammo.core.distributor;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

// import org.apache.tools.ant.BuildException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ContractParser {
	private static final Logger logger = LoggerFactory.getLogger("dist.contractparser");
	private static HashMap<String, Relation> typeContracts;
	
	/**
	 * convert the string to snake case
	 * 
	 * @param name
	 * @return
	 */
	static public String snake_case(String name) {
		StringBuilder sb = new StringBuilder();
		for (String seg : name.split(" ")) {
			sb.append(seg.toLowerCase());
			sb.append('_');
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	/**
	 * convert the string to camel case
	 * 
	 * @param name
	 * @return
	 */
	static public String camelCase(String name) {
		if (name.length() < 1)
			return "";
		StringBuilder sb = new StringBuilder();
		String[] sl = name.split(" ");
		if (sl.length < 1)
			return "";
		sb.append(sl[0].toLowerCase());
		for (int ix = 1; ix < sl.length; ++ix) {
			if (sl[ix].length() < 1)
				continue;
			sb.append(sl[ix].substring(0, 1).toUpperCase());
			if (sl[ix].length() > 0)
				sb.append(sl[ix].substring(1).toLowerCase());
		}
		return sb.toString();
	}

	/**
	 * Used for multiple presentation types.
	 *
	 */
	public static class Name {
		private final String norm;
		private final String camel;
		private final String snake;

		public String getNorm() {
			return norm;
		}

		public String getCamel() {
			return camel;
		}

		public String getSnake() {
			return snake;
		}

		private Name(final String norm, final String camel, final String snake) {
			this.norm = norm;
			this.camel = camel;
			this.snake = snake;
		}

		public Name(final String name) {
			this(name, camelCase(name), snake_case(name));
		}

		public Name(final NodeList seq) {
			this(seq.item(0).getTextContent());
		}

		private static String capitalize(String str) {
			if (str.length() < 1)
				return "";
			return str.substring(0, 1).toUpperCase() + str.substring(1);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			return sb.append("name :").append(norm)
					.append(" ").append(camel)
					.append(" ").append(snake)
					.toString();
		}

		public String getCobra() {
			return snake.toUpperCase();
		}

		public String getBactrian() {
			return capitalize(camel);
		}
	}

	/**
	 * The root object
	 *
	 */
	public static class Contract {
		private Name name;
		private String sponsor;
		private List<Relation> relations;

		public Name getName() {
			return name;
		}

		public String getSponsor() {
			return sponsor;
		}

		public File getSponsorPath(File basedir) {
			File wip = basedir.getAbsoluteFile();
			for (String dir : this.sponsor.split("\\.")) {
				wip = new File(wip, dir);
			}
			wip = new File(wip, "provider");
			return wip;
		}

		public List<Relation> getRelations() {
			return relations;
		}

		private Contract(Name name, String sponsor,
				List<Relation> relations) {
			this.name = name;
			this.sponsor = sponsor;
			this.relations = relations;
		}

		static public Contract newInstance(Element xml) {
			String sponsor = "";
			NodeList sl = xml.getElementsByTagName("sponsor");
			for (int ix = 0; ix < sl.getLength();) {
				Element el = (Element) sl.item(ix);
				sponsor = el.getAttribute("name");
				break;
			}

			List<Relation> relation_set = new ArrayList<Relation>();
			NodeList nl = xml.getElementsByTagName("relation");
			for (int ix = 0; ix < nl.getLength(); ++ix) {
				Relation relation = Relation.newInstance((Element) nl.item(ix));
				relation_set.add(relation);
			}

			return new Contract(extract_name(xml,"name"), sponsor, relation_set);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("<provider name=\"").append(name.norm).append("\" ")
			.append(" sponsor=\"").append(sponsor).append("\">\n");
			for (Relation relation : this.relations) {
				sb.append(relation.toString());
			}
			sb.append("\n</provider>");
			return sb.toString();
		}
	}

	/**
	 * Collect table information.
	 */
	public static class Relation {
		final private Name name;
		final private RMode mode;
		final private List<Field> fields;
		final private List<FieldRef> keycols;

		public Name getName() { return name; }
		public RMode getMode() { return mode; }
		public List<Field> getFields() { return fields; }
		public List<FieldRef> getKeycols() { return keycols; }

		private Relation(Name name, RMode mode, List<Field> fields, List<FieldRef> keycols) {
			this.name = name;
			this.mode = mode;
			this.fields = fields;
			this.keycols = keycols;
		}

		static public Relation newInstance(Element xml) {
			List<Field> field_set = new ArrayList<Field>();
			NodeList nl = xml.getElementsByTagName("field");
			for (int ix = 0; ix < nl.getLength(); ++ix) {
				field_set.add(Field.newInstance((Element) nl.item(ix)));
			}

			// process the key columns
			List<FieldRef> keycol_set = new ArrayList<FieldRef>();
			NodeList kl = xml.getElementsByTagName("key");
			for (int ix = 0; ix < kl.getLength(); ++ix) {
				System.out.println("keys: "+ix);
				NodeList rl = xml.getElementsByTagName("ref");
				for (int jx = 0; jx < rl.getLength(); ++jx) {
					System.out.println("ref: "+jx);
					keycol_set.add(FieldRef.newInstance((Element) rl.item(jx)));
				}
			}

			RMode mode = null;
			NodeList ml = xml.getElementsByTagName("mode");
			for (int ix = 0; ix < ml.getLength();) {
				mode = RMode.newInstance((Element) ml.item(ix));
				break;
			}

			return new Relation(extract_name(xml,"name"), mode, field_set, keycol_set);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("<relation name=").append(name.norm).append(">\n");
			for (Field field : this.fields) {
				sb.append(field.toString());
			}
			for (FieldRef ref : this.keycols) {
				sb.append(ref.toString());
			}
			sb.append("\n</relation>");
			return sb.toString();
		}
	}

	public static class RMode {
		private final Name name;
		private final String dtype;
		private final String description;

		public Name getName() {
			return name;
		}

		public String getDtype() {
			return dtype;
		}

		public String getDescription() {
			return description;
		}

		private RMode(final Name name, final String dtype,
				final String description) {
			this.name = name;
			this.dtype = dtype;
			this.description = description.trim();
		}

		static public RMode newInstance(Element xml) {
			String type = xml.getAttribute("type");
			return new RMode(extract_name(xml,"name"), type, xml.getTextContent());
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			return sb
					.append("<mode name='")
					.append(name.norm)
					.append("' type='").append(dtype)
					.append("'>").append(description)
					.append("</field>")
					.toString();
		}
	}

	static public Name extract_name(Element xml, String attr) {
		return new Name(xml.getAttribute(attr));
	}

	static public RMode extract_mode(Element xml) {

		return null;
	}

	/**
	 * A column in a table (relation).
	 *
	 */
	public static class Field {
		private final Name name;
		private final String dtype;
		private final String initial;
		private final String description;
		private final List<Enumeration> enums;

		public Name getName() {
			return name;
		}

		public String getDtype() {
			return dtype;
		}

		public String getDefault() {
			return initial;
		}

		public String getDescription() {
			return description;
		}
		public List<Enumeration> getEnums() {
			return enums;
		}

		private Field(Name name, String dtype, String initial, String description, 
				List<Enumeration> enum_set)
		{
			this.name = name;
			this.dtype = dtype;
			this.initial = initial;
			this.description = description.trim();
			this.enums = enum_set;
		}

		static public Field newInstance(Element xml) {
			String initial = xml.getAttribute("default");

			List<Enumeration> enum_set = new ArrayList<Enumeration>();
			NodeList enum_list = xml.getElementsByTagName("enum");
			for (int ix = 0; ix < enum_list.getLength(); ++ix) {
				enum_set.add(Enumeration.newInstance((Element) enum_list.item(ix)));
			}

			String type = xml.getAttribute("type");
			return new Field(extract_name(xml,"name"), type, initial,
					xml.getTextContent(), enum_set);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append("<field name='").append(name.norm).append("' type='" ).append("'>");
			sb.append(description);
			for( Enumeration en : this.enums) {
				sb.append(en.toString());
			}
			sb.append("</field>\n");
			return sb.toString();
		}
	}

	public static class FieldRef {
		private final Name name;

		public Name getName() {
			return name;
		}

		private FieldRef(Name name)
		{
			this.name = name;
		}

		static public FieldRef newInstance(Element xml) {
			return new FieldRef(extract_name(xml, "field"));
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			return sb
					.append("<ref field='").append(name.norm)
					.append("' />")
					.toString();
		}
	}


	/**
	 * A column in a table (relation).
	 *
	 */
	public static class Enumeration {
		private final Name key;
		private final int ordinal;

		public Name getKey() { return key; }
		public String getOrdinal() { return Integer.toString(ordinal); }

		private Enumeration(final Name key, final int ordinal) {
			this.key = key;
			this.ordinal = ordinal;
		}

		static public Enumeration newInstance(Element xml) {
			Name key = new Name(xml.getAttribute("key"));
			int ordinal = Integer.parseInt(xml.getAttribute("value"));
			return new Enumeration(key, ordinal);
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("<enum key=\"").append(key).append('"');
			sb.append(' ');
			sb.append("value=\"").append(ordinal).append("\"/>\n");
			return sb.toString();
		}
	}
	
	private static Contract parseContractFromFile(File contractFile) throws ParserConfigurationException, SAXException, IOException {
		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		final Document contractXml;
		
		final DocumentBuilder db = dbf.newDocumentBuilder();
		contractXml = db.parse(contractFile);
		
		final Element de = contractXml.getDocumentElement();
		final Contract contract = Contract.newInstance(de);
		return contract;
	}
	
	public static void loadContracts() {
		//TODO: Do something to get a list of contract files
		List<File> contractFiles = new ArrayList<File>();
		
		for(File f : contractFiles) {
			try {
				Contract contract = parseContractFromFile(f);
				for(Relation rel : contract.getRelations()) {
					//Generate type name; it is of the format:
					// ammo/{sponsor name}.{relation name}
					StringBuilder sb = new StringBuilder();
					sb.append("ammo/").append(contract.getSponsor()).append(".").append(rel.getName().getCamel());
					
					typeContracts.put(sb.toString(), rel);
				}
			} catch(ParserConfigurationException e) {
				
			} catch(SAXException e) {
				
			} catch(IOException e) {
				
			}
		}
	}
	
	/**
	 * Gets the Relation object corresponding to a given MIME type.
	 * 
	 * @param mimeType The type to look up
	 * @return A relation matching this type, or null if a relation is
	 *         not found.
	 */
	public static Relation getRelationForType(String mimeType) {
		//we assume that custom type names are generated by appending some value
		//to the MIME type specified in the contract
		
		for(String type : typeContracts.keySet()) {
			if(mimeType.startsWith(type)) {
				return typeContracts.get(type);
			}
		}
		
		logger.warn("Couldn't find a relation for type {}", mimeType);
		return null;
	}
}
