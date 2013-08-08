package com.google.protobuf;

/**
 * Exposes some parts of protobuf that google hides.  Thanks google!
 * 
 * @author mriley
 */
public class WireFormatUtils {

	/** Given a tag value, determines the wire type (the lower 3 bits). */
	public static int getTagWireType(final int tag) {
		return WireFormat.getTagWireType(tag);
	}

	/** Given a tag value, determines the field number (the upper 29 bits). */
	public static int getTagFieldNumber(final int tag) {
		return WireFormat.getTagFieldNumber(tag);
	}

	/** Makes a tag value given a field number and wire type. */
	public static int makeTag(final int fieldNumber, final int wireType) {
		return WireFormat.makeTag(fieldNumber, wireType);
	}
}
