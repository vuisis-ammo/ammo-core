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
