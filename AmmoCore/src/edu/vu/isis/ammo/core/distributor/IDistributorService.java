package edu.vu.isis.ammo.core.distributor;

/**
 * This interface declares different callback methods that will be used by 
 * different services within AmmoCore. 
 *
 */
public interface IDistributorService {
	
	public static final String LAUNCH = "edu.vu.isis.ammo.core.distributor.DistributorService.LAUNCH";
	public static final String BIND = "edu.vu.isis.ammo.core.distributor.DistributorService.BIND";
	public static final String PREPARE_FOR_STOP = "edu.vu.isis.ammo.core.distributor.DistributorService.PREPARE_FOR_STOP";
	public static final String SEND_SERIALIZED = "edu.vu.isis.ammo.core.distributor.DistributorService.SEND_SERIALIZED";
	
	/** Network Service callbacks */
	public void finishTeardown();
	
}
