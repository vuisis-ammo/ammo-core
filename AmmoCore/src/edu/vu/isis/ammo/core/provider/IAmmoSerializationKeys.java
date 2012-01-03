/*
Copyright(c) 2010-2012

This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under contract [contract citation, subcontract and prime contract]. 
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.

 */

package edu.vu.isis.ammo.core.provider;

/** 
 * Interface which declares constants used as keys for read/write operations
 * to the shared Bundle
 * @author Demetri Miller
 *
 */
public interface IAmmoSerializationKeys {
	// Intent action that should be used.
	public static final String SEND_SERIALIZED_ACTION = "SEND_SERIALIZED";
	
	public static final String SERIALIZED_STRING_KEY = "serializedString";
	public static final String SERIALIZED_BYTE_ARRAY_KEY = "serializedByteArray";
}
