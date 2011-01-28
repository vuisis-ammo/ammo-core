package edu.vu.isis.ammo.core;

/**
 * Interface used by services as a callback when terminating execution. We need 
 * to use this sort of mechanism because of the asynchronous nature of 
 * intent broadcasts.
 * @author demetri
 *
 */
public interface IServiceCallback {
	public void finishSelfTeardown();
}
