package edu.vu.isis.ammo.core.distributor.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

	private final static Logger logger = LoggerFactory.getLogger("class.store.presence");

	/*
	 * A map of keys to items.
	 */
	private final Map<Item.Key, Item> relMap;
	public int size() { return this.relMap.size(); }

	private Presence() {
		this.relMap = new ConcurrentHashMap<Item.Key, Item>();

		/*
		// a dummy item for testing
		final Builder build = Presence.newBuilder();
		build.operator("dummy").origin("self");
		this.relMap.put(build.buildKey(), build.buildItem()); 
		*/
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
		private final static Logger logger = LoggerFactory.getLogger("class.store.presence.builder");

		private String origin = "default origin";
		private String operator = "default operator";

		private long lifespan = 10L * 60L * 1000L; // ten minutes

		private Builder() {}

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
			final Item item = new Item(this);
			logger.debug("ctor [{}]", item);
			return item;
		}
		public Item.Key buildKey() {
			return new Item.Key(this);
		}

		@Override
		public String toString() {
			final Item.Key key = new Item.Key(this);
			return new StringBuilder().
					append("key={").append(key).append("}").
					toString();
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
		private final Logger logger = LoggerFactory.getLogger("class.store.presence.worker");

		private String device;
		private String operator;

		private Worker() {}
		
		@Override
		public String toString() {
			return new StringBuilder().
					append("device=\"").append(this.device).append("\",").
					append("operator=\"").append(this.operator).append("\"").
					toString();
		}

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

					if (relation.relMap.containsKey(key)) {
						final Item item = relation.relMap.get(key);
					
						item.latest = now;
						item.count++;
						PLogger.STORE_PRESENCE_DML.debug("updated item=[{}]", item);
						return 1;
					} else {
						final Item item = builder.buildItem();
						relation.relMap.put(key, item);
						PLogger.STORE_PRESENCE_DML.debug("inserted item=[{}]", item);
						return 1;
					} 
				} catch (IllegalArgumentException ex) {
					logger.error("update presence: ex=[{}]", ex.getLocalizedMessage());
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
				/* don't include id in hash code
				hc *= 31;
				hc += ((int) (this.id ^ (this.id >>> 32)));
				 */
				hc *= 31;
				if (this.origin != null) {
					hc += this.origin.hashCode();
				}
				hc *= 31;
				if (this.operator != null) {
					hc += this.operator.hashCode();
				}
				this.hashCode = hc;
			}

			@Override
			public boolean equals(Object o) {
				if (!(o instanceof Key)) return false;
				final Key that = (Key) o;
				if (! TextUtils.equals(this.origin, that.origin)) return false;
				if (! TextUtils.equals(this.operator, that.operator)) return false;
				return true;
			}

			@Override
			public String toString() {
				return new StringBuilder().
						append("id=\"").append(this.id).append("\",").
						append("origin=\"").append(this.origin).append("\",").
						append("operator=\"").append(this.operator).append("\"").
						toString();
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

		@Override 
		public String toString() {
			return new StringBuilder().
					append("key={").append(this.key).append("},").
					append("first=\"").append(this.first).append("\",").
					append("latest=\"").append(this.latest).append("\",").
					append("count=\"").append(this.count).append("\"").
					toString();
		}

		public final long expiration;
		public final long first;
		public long latest;
		public int count;

		/**
		 * Rather than using a big switch, this makes use of an EnumMap
		 */
		public Object[] getValues(final EnumSet<PresenceSchema> set) {
			final ArrayList<Object> row = new ArrayList<Object>(set.size());
			for (final PresenceSchema field : set) {
				final Getter getter = getters.get(field);
				if (getter == null) {
					logger.warn("missing getter for field {}", field);
					row.add(null);
					continue;
				}
				row.add(getter.getValue(this));
			}
			return row.toArray();
		}

		private interface Getter { public Object getValue(final Item item); }
		final static private Map<PresenceSchema,Getter> getters;
		static {
			getters = new EnumMap<PresenceSchema,Getter>(PresenceSchema.class);
			getters.put(PresenceSchema.ID, new Getter() { 
				@Override
				public Object getValue(final Item item) { return item.key.id; }
			});
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
		final Collection<Item> values = Presence.INSTANCE.relMap.values();
		return values;
	}


}
