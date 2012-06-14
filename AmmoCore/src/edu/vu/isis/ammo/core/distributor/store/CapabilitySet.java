package edu.vu.isis.ammo.core.distributor.store;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapabilitySet {
	private final static Logger logger = LoggerFactory.getLogger("class.store.capability.set");
	
	private final Map<String, Capability> capSet;

	private CapabilitySet() {
		this.capSet = new HashMap<String, Capability>();
	}
	
}
