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
package edu.vu.isis.ammo.core.network;



/**
 * Two long running threads and one short.
 * The long threads are for sending and receiving messages.
 * The short thread is to connect the socket.
 * The sent messages are placed into a queue if the socket is connected.
 *
 */
public abstract class TcpChannelAbstract extends NetChannel {

	protected TcpChannelAbstract(String name) {
		super(name);
	}

	abstract public void putFromSecurityObject(AmmoGatewayMessage build);

	abstract public void finishedPuttingFromSecurityObject() ;

	abstract public void authorizationSucceeded(AmmoGatewayMessage agm);
 
}
