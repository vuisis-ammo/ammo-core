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

package edu.vu.isis.ammo.core.network;

public abstract class NetChannel implements INetChannel {
	protected static final boolean HEARTBEAT_ENABLED = true;

	// The values in the INetChannel that we are translating here could
    // probably be made into an enum and the translation to strings
    // would be handled for us.
    public String showState(int state) {
    	
        switch (state){
        case  PENDING        :     return "PENDING";
        case  EXCEPTION      :     return "EXCEPTION";

        case  CONNECTING     :     return "CONNECTING";
        case  CONNECTED      :     return "CONNECTED";

        case  DISCONNECTED   :     return "DISCONNECTED";
        case  STALE          :     return "STALE";
        case  LINK_WAIT      :     return "LINK_WAIT";

        case  WAIT_CONNECT   :     return "WAIT CONNECT";
        case  SENDING        :     return "SENDING";
        case  TAKING         :     return "TAKING";
        case  INTERRUPTED    :     return "INTERRUPTED";

        case  SHUTDOWN       :     return "SHUTDOWN";
        case  START          :     return "START";
        case  RESTART        :     return "RESTART";
        case  WAIT_RECONNECT :     return "WAIT_RECONNECT";
        case  STARTED        :     return "STARTED";
        case  SIZED          :     return "SIZED";
        case  CHECKED        :     return "CHECKED";
        case  DELIVER        :     return "DELIVER";
        case  DISABLED       :     return "DISABLED";
        default:
            return "Undefined State [" + state +"]";
        }
    }
    
    // a string uniquely naming the channel
    final public String name;
    
    protected NetChannel(String name) {
    	this.name = name;
    }
    
    @Override
    public String toString() { return this.name; }
}
