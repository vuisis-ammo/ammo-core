package edu.vu.isis.ammo.core.distributor.store;

import java.util.ArrayList;

import android.provider.BaseColumns;
import edu.vu.isis.ammo.util.EnumUtils;

public enum PresenceSchema {
	/** This is a locally unique identifier for the request */
	ID(BaseColumns._ID,"TEXT"),
	
	/** This is a universally unique identifier for the request */
	UUID("TEXT"),

	/** Device originating the request */
	ORIGIN("TEXT"),

	/** Who last modified the request */
	OPERATOR("TEXT"),


	/** The time when first observed (millisec); indicates the first time the peer was observed.*/
	FIRST("INTEGER"),

	/** when last observed (millisec);
	 * When the operator was last seen "speaking" on the channel.
	 * The latest field indicates the last time the peer was observed. */
	LATEST("INTEGER"),

	/** how many times seen since first.
	 * How many times the peer has been seen since FIRST.
	 * Each time LATEST is changed this COUNT should be incremented*/
	COUNT("INTEGER"),

	/** The time when no longer relevant (millisec);
	 * the request becomes stale and may be discarded. */
	EXPIRATION("INTEGER");
	
	/** textual field name */
	final public String field; 
	
	/** type */
	final public String t; 
	
	private PresenceSchema( String type) {
		this.field = this.name();
		this.t = type;
	}
	
	private PresenceSchema( String field, String type) {
		this.field = field;
		this.t = type;
	}
	
	/**
	 * an array of all field names
	 */
	public static final String[] FIELD_NAMES 
		= EnumUtils.buildFieldNames(PresenceSchema.class);
	
	/**
	 * map an array of field names to fields.
	 * 
	 * @param names an array of field names
	 * @return an array of fields
	 */
	public static ArrayList<PresenceSchema> mapFields(final String[] names) {
		return EnumUtils.getFields(PresenceSchema.class, names);
	}
	
}
