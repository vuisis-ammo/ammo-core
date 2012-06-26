package edu.vu.isis.ammo.core.distributor.store;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.text.TextUtils;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.provider.PresenceSchema;

/**
 * The presence set, is the set of all capabilities known by the device.
 * The presence may be indexed by 
 *
 */

public enum Presence {
	INSTANCE;

	private final static Logger logger = LoggerFactory.getLogger("class.store.presence.set");

	/*
	 * A map of keys to items.
	 */
	private final Map<Item.Key, Item> relMap;
	public int size() { return this.relMap.size(); }

	private Presence() {
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

		private String device;
		private String operator;

		private Worker() {}

		public Worker device(String value) {
			this.device = value;
			return this;
		}

		public Worker operator(String value) {
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
			PLogger.STORE_PRESENCE_DML.trace("upsert presence: device=[{}] @ {}",
					this.device, this);
			final Presence relation = Presence.INSTANCE;
			synchronized(relation) {
				final Long now = Long.valueOf(System.currentTimeMillis());

				final Builder builder = newBuilder()
						.operator(this.operator)
						.origin(this.device);
				try {
					final Item.Key key = builder.buildKey();

					final Item cap = relation.relMap.get(key);
					if (cap == null) {
						PLogger.STORE_PRESENCE_DML.debug("updated cap=[{}]", 
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
			PLogger.STORE_PRESENCE_DML.trace("delete presence: device=[{}] @ {}",
					this.device, this);
			final Presence relation = Presence.INSTANCE;
			synchronized(relation) {
				final Long now = Long.valueOf(System.currentTimeMillis());

				final Builder builder = newBuilder()
						.operator(this.operator)
						.origin(this.device);
				try {
					final Item.Key key = builder.buildKey();

					final Item cap = relation.relMap.get(key);
					if (cap == null) {
						PLogger.STORE_PRESENCE_DML.debug("updated cap=[{}]", 
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
		public static final class Key {
			public final long id;
			public final String origin;
			public final String operator;

			final private int hashCode;
			@Override
		    public int hashCode() { return this.hashCode; }
			
			private Key(Builder that) {
				Presence._id_seq++;
				this.id = Presence._id_seq;
				this.origin = that.origin;
				this.operator = that.operator;
				
				int hc = 17;
				hc *= 31;
				hc += ((int) (this.id ^ (this.id >>> 32)));
				hc *= 31;
				hc += this.origin.hashCode();
				hc *= 31;
				hc += this.operator.hashCode();
		        this.hashCode = hc;
			}
			
			@Override
			public boolean equals(Object o) {
				if (!(o instanceof Key)) return false;
				final Key that = (Key) o;
				if (this.id != that.id) return false;
				if (! TextUtils.equals(this.origin, that.origin)) return false;
				if (! TextUtils.equals(this.operator, that.operator)) return false;
				return true;
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
		public Object[] getValues(final PresenceSchema[] fields) {
			final Object[] row = new Object[fields.length];
			int ix = 0;
			for (final PresenceSchema field : fields) {
				row[ix] = getters.get(field).getValue(this);
			}
			return row;
		}
		private interface Getter { public Object getValue(final Item item); }
		final static private Map<PresenceSchema,Getter> getters;
		static {
			getters = new EnumMap<PresenceSchema,Getter>(PresenceSchema.class);
			getters.put(PresenceSchema.UUID, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.key.id; }
			});
			getters.put(PresenceSchema.ORIGIN, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.key.origin; }
			});
			getters.put(PresenceSchema.OPERATOR, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.key.operator; }
			});
			
			
			getters.put(PresenceSchema.EXPIRATION, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.expiration; }
			});
			getters.put(PresenceSchema.FIRST, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.first; }
			});
			getters.put(PresenceSchema.LATEST, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.latest; }
			});
			getters.put(PresenceSchema.COUNT, new Getter() {
				@Override
				public Object getValue(final Item item) { return item.count; }
			});
		}
	}

	public static Collection<Item> query() {
		return Presence.INSTANCE.relMap.values();
	}


}
