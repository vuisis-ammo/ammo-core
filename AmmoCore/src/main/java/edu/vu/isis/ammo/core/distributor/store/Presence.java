/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */



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
 * The presence set, is the set of all items known by the device.
 * <p>
 * There is one item for each principle. There is a builder for creating items
 * and keys. These items with their keys are added to the presence set.
 * <p>
 * A worker is used to perform various operations on the presence set.
 * <p>
 * <code>
   Presence.getWorker()
           .device(dm.getOriginDevice())
           .operator(dm.getUserId())
           .upsert();
   </code>
 */

public enum Presence {
    INSTANCE;

    private final static Logger logger = LoggerFactory.getLogger("class.store.presence");

    /*
     * A map of keys to items.
     */
    private final Map<Item.Key, Item> relMap;

    public int size() {
        return this.relMap.size();
    }

    private Presence() {
        this.relMap = new ConcurrentHashMap<Item.Key, Item>();

        /**
         * a dummy item for testing <code> 
         * final Builder build = Presence.newBuilder(); 
         * build.operator("dummy").origin("self");
         * this.relMap.put(build.buildKey(), build.buildItem()); 
         * </code>
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
        private final static Logger logger = LoggerFactory
                .getLogger("class.store.presence.builder");

        private String origin = "default origin";
        private String operator = "default operator";

        private Builder() {
        }

        public Builder origin(String value) {
            this.origin = value;
            return this;
        }

        public Builder operator(String value) {
            this.operator = value;
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
     * returns a worker object which modifies the object to which it is
     * associated.
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

        private Worker() {
        }

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
         * upsert the tuple indicated use the key to determine if the item is
         * already present
         * 
         * @return
         */
        public long upsert() {
            PLogger.STORE_PRESENCE_DML.trace("upsert presence: device=[{}] @ {}",
                    this.device, this);
            final Presence relation = Presence.INSTANCE;
            synchronized (relation) {

                final Builder builder = newBuilder()
                        .operator(this.operator)
                        .origin(this.device);
                try {
                    final Item.Key key = builder.buildKey();

                    if (relation.relMap.containsKey(key)) {
                        final Item item = relation.relMap.get(key);
                        item.update();
                        PLogger.STORE_PRESENCE_DML.debug("updated item=[{}]", item);
                        return 1;
                    } else {
                        final Item item = builder.buildItem();
                        relation.relMap.put(key, item);
                        PLogger.STORE_PRESENCE_DML.debug("inserted item=[{}]", item);
                        return 1;
                    }
                } catch (IllegalArgumentException ex) {
                    logger.error("update presence", ex);
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
            synchronized (relation) {
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
                    cap.update();

                } catch (IllegalArgumentException ex) {
                    logger.error("update presence", ex);
                } finally {
                    // this.db.endTransaction();
                }
                return -1;
            }
        }
    }

    public static class Item extends TemporalItem {
        /**
         * 
         */
        public static final class Key {
            public final long id;
            public final String origin;
            public final String operator;

            final private int hashCode;

            @Override
            public int hashCode() {
                return this.hashCode;
            }

            private Key(Builder that) {
                Presence._id_seq++;
                this.id = Presence._id_seq;
                this.origin = that.origin;
                this.operator = that.operator;

                int hc = 17;
                /*
                 * don't include id in hash code <code> hc *= 31; hc += ((int)
                 * (this.id ^ (this.id >>> 32))); </code>
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
                if (!(o instanceof Key))
                    return false;
                final Key that = (Key) o;
                if (!TextUtils.equals(this.origin, that.origin))
                    return false;
                if (!TextUtils.equals(this.operator, that.operator))
                    return false;
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
            super();
            this.key = new Key(that);
        }

        @Override
        public String toString() {
            return new StringBuilder().
                    append("key={").append(this.key).append("},").
                    append(super.toString()).
                    toString();
        }

        /**
         * Rather than using a big switch, this makes use of an EnumMap to
         * return a row in the same order as the presence fields.
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
                final Object val = getter.getValue(this);
                logger.trace("get value field={} val={}", field, val);
                row.add(val);
            }
            return row.toArray();
        }

        private interface Getter {
            public Object getValue(final Item item);
        }

        /**
         * a set of getters to be used in the populating of a cursor
         */
        final static private Map<PresenceSchema, Getter> getters;
        static {
            getters = new EnumMap<PresenceSchema, Getter>(PresenceSchema.class);
            getters.put(PresenceSchema.ID, new Getter() {
                @Override
                public Object getValue(final Item item) {
                    return item.key.id;
                }
            });
            getters.put(PresenceSchema.UUID, new Getter() {
                @Override
                public Object getValue(final Item item) {
                    return item.key.id;
                }
            });
            getters.put(PresenceSchema.ORIGIN, new Getter() {
                @Override
                public Object getValue(final Item item) {
                    return item.key.origin;
                }
            });
            getters.put(PresenceSchema.OPERATOR, new Getter() {
                @Override
                public Object getValue(final Item item) {
                    return item.key.operator;
                }
            });

            getters.put(PresenceSchema.FIRST, new Getter() {
                @Override
                public Object getValue(final Item item) {
                    return item.first;
                }
            });
            getters.put(PresenceSchema.LATEST, new Getter() {
                @Override
                public Object getValue(final Item item) {
                    return item.latest;
                }
            });
            getters.put(PresenceSchema.COUNT, new Getter() {
                @Override
                public Object getValue(final Item item) {
                    return item.count;
                }
            });

            getters.put(PresenceSchema.EXPIRATION, new Getter() {
                @Override
                public Object getValue(final Item item) {
                    return item.getExpiration();
                }
            });
            getters.put(PresenceSchema.STATE, new Getter() {
                @Override
                public Object getValue(final Item item) {
                    return item.getDominantState().code;
                }
            });
        }
    }

    /**
     * used by query methods in DistributorDataStore
     * 
     * @return
     */
    public static Collection<Item> queryAll() {
        return Presence.INSTANCE.relMap.values();
    }

}
