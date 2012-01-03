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

package edu.vu.isis.ammo.core.distributor;

/**
 * These fields are replicated in the generated code.
 * see AmmoGenerator/content_provider/template/java/ammo_content_provider.stg
 */
public class AmmoProviderSchema {

	   public static final String _DISPOSITION = "_disp"; 
	   public static final String _RECEIVED_DATE = "_received_date";
	   
	   /**
	    * REMOTE : the last update for this record is from
	    *   a received message.
	    * LOCAL  : the last update was produced locally 
	    *   (probably the creation).
	    */
	   public enum Disposition {
		   REMOTE, LOCAL;
	   }
}
