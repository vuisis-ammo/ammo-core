package edu.vu.isis.ammo.core.distributor.store;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.text.TextUtils;

import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.provider.CapabilitySchema;

/**
 * The capability set, is the set of all capabilities known by the device.
 * The capability may be indexed by 
 *
 */

public enum Capability {
	INSTANCE;

	private final static Logger logger = LoggerFactory.getLogger("class.store.capability.set");

	/*
	 * A map of keys to items.
	 */
	private final Map<Item.Key, Item> relMap;
	public int size() { return this.relMap.size(); }

	private Capability() {
		this.relMap = new HashMap<Item.Key, Item>();
	}
	
	/**
	 * The builder is used to construct new capabilities and capability.Key.
	 * 
	 * @return new builder
	 */
	static public Builder newBuilder() {
		return new Builder();
	}
	
	private static volatile long _id_seq = Long.MIN_VALUE;
	
	public static final class Builder {
		
		private String origin;
		private String operator;
		
		private String topic;
		private String subtopic;
		
		private long lifespan;

		private Builder() { }

		public Builder origin(String value) {
			this.origin = value;
			return this;
		}
		public Builder operator(String value) {
			this.operator = value;
			return this;
		}
		public Builder topic(String value) {
			this.topic = value;
			return this;
		}
		public Builder subtopic(String value) {
			this.subtopic = value;
			return this;
		}
		public Builder lifespan(long value) {
			this.lifespan = value;
			return this;
		}

		public Item buildItem() {
			final Item cap = new Item(this);
			logger.debug("ctor [{}]", cap);
			return cap;
		}
		public Item.Key buildKey() {
			return new Item.Key(this);
		}

		@Override
		public String toString() {
			final Item.Key key = new Item.Key(this);
			return new StringBuilder()
			.append("key=[").append(key)
			.toString();
		}
	}

	/**
	 * returns a worker object which modifies 
	 * the object to which it is associated.
	 * 
	 * @return
	 */
	public static Worker getWorker() {
		return new Worker();
	}

	public static class Worker {

		private String origin;
		private String operator;
		private String topic;
		private String subtopic;

		private Worker() {}

		public Worker origin(String value) {
			this.origin = value;
			return this;
		}
		public Worker operator(String value) {
			this.operator = value;
			return this;
		}
		public Worker topic(String value) {
			this.origin = value;
			return this;
		}
		public Worker subtopic(String value) {
			this.operator = value;
			return this;
		}

		/**
		 * upsert the tuple indicated
		 * 
		 * use the key to determine if the item is already present
		 * @return
		 */
		public long upsert() {
			PLogger.STORE_CAPABILITY_DML.trace("upsert capability: device=[{}] @ {}",
					this.origin, this);
			final Capability relation = Capability.INSTANCE;
			synchronized(relation) {
				final Long now = Long.valueOf(System.currentTimeMillis());

				final Builder builder = newBuilder()
						.operator(this.operator)
						.origin(this.origin)
						.topic(this.topic)
						.subtopic(this.subtopic);
				try {
					final Item.Key key = builder.buildKey();

					final Item cap = relation.relMap.get(key);
					if (cap == null) {
						PLogger.STORE_CAPABILITY_DML.debug("updated cap=[{}]", 
								this);
						return -1;
					} 
					cap.latest = now;
					cap.count++;
				} catch (IllegalArgumentException ex) {
					logger.error("update capablity: ex=[{}]", ex.getLocalizedMessage());
				} finally {
					// this.db.endTransaction();
				}
				return -1;
			}
		}

		/**
		 * delete the tuple indicated
		 * 
		 * @param tupleId
		 * @return
		 */
		public int delete(String tupleId) {
			PLogger.STORE_CAPABILITY_DML.trace("delete capability: device=[{}] @ {}",
					this.origin, this);
			final Capability relation = Capability.INSTANCE;
			synchronized(relation) {
				final Long now = Long.valueOf(System.currentTimeMillis());

				final Builder builder = newBuilder()
						.operator(this.operator)
						.origin(this.origin)
						.topic(this.topic)
						.subtopic(this.subtopic);
				try {
					final Item.Key key = builder.buildKey();

					final Item cap = relation.relMap.get(key);
					if (cap == null) {
						PLogger.STORE_CAPABILITY_DML.debug("updated cap=[{}]", 
								this);
						return -1;
					} 
					cap.latest = now;
					cap.count++;
				} catch (IllegalArgumentException ex) {
					logger.error("update capablity: ex=[{}]", ex.getLocalizedMessage());
				} finally {
					// this.db.endTransaction();
				}
				return -1;
			}
		}
	}

	public static class Item {
		/**
		 *  The tuple identifier 
		 *  (required)
		 *  id 
		 *  
		 *  The device identifier
		 *  (required)
		 *  identifier
		 *  
		 *  This along with the cost is used to decide how to deliver the specific object.
		 *  (required)
		 *  topic
		 *  (optional)
		 *  subtopic
		 *  
		 * The name of the operator using the channel
		 */
		public static final class Key extends Object {
			public final long id;
			public final String origin;
			public final String operator;
			public final String topic;
			public final String subtopic;
			
			final private int hashCode;
			@Override
		    public int hashCode() { return this.hashCode; }		

			private Key(Builder that) {
				Capability._id_seq++;
				this.id = Capability._id_seq;
				this.origin = that.origin;
				this.operator = that.operator;
				this.topic = that.topic;
				this.subtopic = that.subtopic;
				
				int hc = 17;
				hc *= 31;
				hc += ((int) (this.id ^ (this.id >>> 32)));
				hc *= 31;
				hc += this.origin.hashCode();
				hc *= 31;
				hc += this.operator.hashCode();
				hc *= 31;
				hc += this.topic.hashCode();
				hc *= 31;
				hc += this.subtopic.hashCode();
		        this.hashCode = hc;
			}
			
			@Override
			public boolean equals(Object o) {
				if (!(o instanceof Key)) return false;
				final Key that = (Key) o;
				if (this.id != that.id) return false;
				if (! TextUtils.equals(this.origin, that.origin)) return false;
				if (! TextUtils.equals(this.operator, that.operator)) return false;
				if (! TextUtils.equals(this.topic, that.topic)) return false;
				if (! TextUtils.equals(this.subtopic, that.subtopic)) return false;
				return true;
			}
			
			@Override
			public String toString() {
				return new StringBuilder()
				.append("origin=\"").append(this.origin).append("\" ")
				.append("operator=\"").append(this.operator).append("\" ")
				.append("topic=\"").append(this.topic).append("\" ")
				.append("subtopic=\"").append(this.subtopic)
				.toString();
			}
			
		}
		public final Key key;

		public Item(Builder that) {
			this.key = new Key(that);

			this.first = System.currentTimeMillis();
			this.expiration = that.lifespan;

			this.latest = this.first;
			this.count = 1;
		}

		public final long expiration;
		public final long first;
		public long latest;
		public int count;


		/**
		 * Rather than using a big switch, this makes use of an EnumMap
		 */
		public Object[] getValues(final CapabilitySchema[] fields) {
			final Object[] row = new Object[fields.length];
			int ix = 0;
			for (final CapabilitySchema field : fields) {
				row[ix] = getters.get(field).getValue(this);
			}
			return row;
		}
		private interface Getter { public Object getValue(final Item item); }
		final static private Map<CapabilitySchema,Getter> getters;
		static {
			getters = new EnumMap<CapabilitySchema,Getter>(CapabilitySchema.class);
			getters.put(CapabilitySchema.UUID, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.key.id; }
			});
			getters.put(CapabilitySchema.ORIGIN, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.key.origin; }
			});
			getters.put(CapabilitySchema.OPERATOR, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.key.operator; }
			});
			getters.put(CapabilitySchema.TOPIC, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.key.topic; }
			});
			getters.put(CapabilitySchema.SUBTOPIC, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.key.subtopic; }
			});
			
			getters.put(CapabilitySchema.EXPIRATION, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.expiration; }
			});
			getters.put(CapabilitySchema.FIRST, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.first; }
			});
			getters.put(CapabilitySchema.LATEST, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.latest; }
			});
			getters.put(CapabilitySchema.COUNT, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.count; }
			});
		}
		
		

		// what about message rates?
	}

	public static Collection<Item> query() {
		return Capability.INSTANCE.relMap.values();
	}

}
