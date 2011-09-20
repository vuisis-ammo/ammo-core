package edu.vu.isis.ammo.core.distributor;

import edu.vu.isis.ammo.api.type.Payload;
import edu.vu.isis.ammo.api.type.Provider;
import edu.vu.isis.ammo.core.distributor.DistributorPolicy.Encoding;
import edu.vu.isis.ammo.core.network.AmmoGatewayMessage;

/**
 * The purpose of these objects is lazily serialize an object.
 * Once it has been serialized once a copy is kept.
 *
 */
public class RequestSerializer {
	
	public interface OnReady  {
		public AmmoGatewayMessage run(Encoding encode, byte[] serialized);
	}
	public interface OnSerialize  {
		public byte[] run(Encoding encode);
	}

	public final Provider provider;
	public final Payload payload;
	private OnReady action;
	private OnSerialize onSerialize;
	private AmmoGatewayMessage terse;
	private AmmoGatewayMessage json;

	private RequestSerializer(Provider provider, Payload payload) {
		this.provider = provider;
		this.payload = payload;
		this.terse = null;
		this.json = null;
	}

	public static RequestSerializer newInstance() {
		return new RequestSerializer(null, null);
	}
	public static RequestSerializer newInstance(Provider provider, Payload payload) {
		return new RequestSerializer(provider, payload);
	}

	public AmmoGatewayMessage act(Encoding encode) {
		switch (encode.getPayload()) {
		case JSON: 
			if (this.json != null) return this.json;
			final byte[] jsonBytes = this.onSerialize.run(encode);
			this.json = this.action.run(encode, jsonBytes);
			return this.json;
		case TERSE: 
			if (this.terse == null) return this.terse;
			final byte[] terseBytes = this.onSerialize.run(encode);
			this.terse = this.action.run(encode, terseBytes);
			return this.terse;
		}
		return null;
	}

	public void setAction(OnReady action) {
		this.action = action;
	}

	public void setSerializer(OnSerialize onSerialize) {
		this.onSerialize = onSerialize;
	}


}
