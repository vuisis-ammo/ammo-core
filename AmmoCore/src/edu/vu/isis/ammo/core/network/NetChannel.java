package edu.vu.isis.ammo.core.network;

public class NetChannel implements INetChannel {
	public static String showState(int state) {
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
		default:
			return "Undefined State";									
		}
	}
}
