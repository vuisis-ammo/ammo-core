
package edu.vu.isis.ammo.core.distributor.store;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.text.TextUtils;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.provider.InviteSchema;

/**
 * In the traditional Pub/Sub model this would have been called publish.
 * <p>
 * The enum is a singleton of all the invitations currently registered.
 */

public enum InvitationMap {
    INSTANCE;

    private final static Logger logger = LoggerFactory.getLogger("class.store.invitation");

    /*
     * A map of keys to items.
     */
    private final Map<Invitation.Key, Invitation> impl;

    public int size() {
        return this.impl.size();
    }

    private InvitationMap() {
        this.impl = new ConcurrentHashMap<Invitation.Key, Invitation>();
    }

    /**
     * The builder is used to construct new invitation and invitation.Key.
     * 
     * @return new builder
     */
    static public Builder newBuilder() {
        return new Builder();
    }

    private static volatile long _id_seq = Long.MIN_VALUE;

    /**
     * Used for building invitations.
     * These invitations will be placed in the map.
     */
    public static class Builder {
        private final static Logger logger = LoggerFactory
                .getLogger("class.store.invitation.builder");

        protected String topic = "default topic";
        protected List<String> subtopic = null;
        protected List<String> invitee = null;

        private Builder() {
        }

        public Builder topic(String value) {
            this.topic = value;
            return this;
        }

        public Builder subtopic(final List<String> value) {
            this.subtopic = value;
            return this;
        }

        public Invitation build() {
            final Invitation item = new Invitation(this);
            logger.debug("ctor [{}]", item);
            return item;
        }

        public Invitation.Key buildKey() {
            return new Invitation.Key(this);
        }

        @Override
        public String toString() {
            final Invitation.Key key = new Invitation.Key(this);
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

    public static class Worker extends Builder {
        private final Logger logger = LoggerFactory.getLogger("class.store.invitation.worker");

        private Map<String, Object> invitee;

        private Worker() {
        }

        @Override
        public String toString() {
            return new StringBuilder().
                    append("topic=\"").append(this.topic).append("\",").
                    append("subtopic=\"").append(this.subtopic).append("\"").
                    append("invitee=\"").append(this.invitee).append("\"").
                    toString();
        }

        public Worker topic(String value) {
            this.topic = value;
            return this;
        }
        
        public Worker subtopic(List<String> value) {
            this.subtopic = value;
            return this;
        }
        
        public Worker invitee(Map<String, Object> value) {
            this.invitee = value;
            return this;
        }

        /**
         * upsert the tuple indicated use the key to determine if the item is
         * already present
         * 
         * @return
         */
        public long upsert() {
            PLogger.STORE_CAPABILITY_DML.trace("upsert invitation: device=[{}] @ {}", this);
            final InvitationMap relation = InvitationMap.INSTANCE;
            synchronized (relation) {

                final Builder builder = newBuilder()
                        .topic(this.topic)
                        .subtopic(this.subtopic);
                try {
                    final Invitation.Key key = builder.buildKey();

                    if (relation.impl.containsKey(key)) {
                        final Invitation invitation = relation.impl.get(key);
                        invitation.update();
                        PLogger.STORE_INVITATION_DML.debug("updated item=[{}]", invitation);
                        return 1;
                    } else {
                        final Invitation item = builder.build();
                        relation.impl.put(key, item);
                        PLogger.STORE_INVITATION_DML.debug("inserted item=[{}]", item);
                        return 1;
                    }

                } catch (IllegalArgumentException ex) {
                    logger.error("update capablity", ex);
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
        public int delete(final String tupleId) {
            PLogger.STORE_CAPABILITY_DML.trace("delete invitation: device=[{}] @ {}",
                    this);
            final InvitationMap relation = InvitationMap.INSTANCE;
            synchronized (relation) {

                final Builder builder = newBuilder()
                        .topic(this.topic)
                        .subtopic(this.subtopic);
                try {
                    final Invitation.Key key = builder.buildKey();

                    final Invitation item = relation.impl.get(key);
                    if (item == null) {
                        PLogger.STORE_CAPABILITY_DML.debug("updated cap=[{}]", this);
                        return -1;
                    }
                    item.update();
                } catch (IllegalArgumentException ex) {
                    logger.error("update capablity", ex);
                } finally {
                    // this.db.endTransaction();
                }
                return -1;
            }
        }
    }

    /**
     * Invitation to a subtopic stored in the map.
     */
    public static class Invitation extends TemporalItem {
        /**
         * The fields of the invitation which are keys into the map.
         */
        public static final class Key extends Object {
            public final long id;
            public final String topic;
            public final List<String> subtopic;

            final private int hashCode;

            @Override
            public int hashCode() {
                return this.hashCode;
            }

            private Key(Builder that) {
                InvitationMap._id_seq++;
                this.id = InvitationMap._id_seq;
                this.topic = that.topic;
                this.subtopic = that.subtopic;

                int hc = 17;
                /* don't include id in hash code */
                hc *= 31;
                if (this.topic != null) {
                    hc += this.topic.hashCode();
                }
                hc *= 31;
                if (this.subtopic != null) {
                    hc += this.subtopic.hashCode();
                }
                this.hashCode = hc;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof Key))
                    return false;
                final Key that = (Key) o;
                if (!TextUtils.equals(this.topic, that.topic))
                    return false;
                if (this.subtopic == null) {
                    if (that.subtopic == null)
                        return true;
                    return false;
                }
                if (this.subtopic.size() != that.subtopic.size()) {
                    return false;
                }
                for (int ix = 0; ix < this.subtopic.size(); ++ix) {
                    if (!TextUtils.equals(this.subtopic.get(ix), that.subtopic.get(ix)))
                        return false;
                }
                return true;
            }

            @Override
            public String toString() {
                return new StringBuilder().
                        append("topic=\"").append(this.topic).append("\",").
                        append("subtopic=\"").append(this.subtopic).append("\"").
                        toString();
            }

        }

        public final Key key;
        public final List<String> invitee;

        public Invitation(final Builder that) {
            super();
            this.key = new Key(that);
            this.invitee = that.invitee;
        }

        @Override
        public String toString() {
            return new StringBuilder().
                    append("key={").append(this.key).append("},").
                    append(super.toString()).
                    toString();
        }

        /**
         * Rather than using a big switch, this makes use of an EnumMap
         */
        public Object[] getValues(final EnumSet<InviteSchema> set) {
            final ArrayList<Object> row = new ArrayList<Object>(set.size());
            for (final InviteSchema field : set) {
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

        private interface Getter {
            public Object getValue(final Invitation item);
        }

        /**
         * a set of getters to be used in the populating of a cursor
         */
        final static private Map<InviteSchema, Getter> getters;
        static {
            getters = new EnumMap<InviteSchema, Getter>(InviteSchema.class);
            getters.put(InviteSchema.UUID, new Getter() {
                @Override
                public Object getValue(final Invitation invitation) {
                    return invitation.key.id;
                }
            });

            getters.put(InviteSchema.TOPIC, new Getter() {
                @Override
                public Object getValue(final Invitation invitation) {
                    return invitation.key.topic;
                }
            });
            getters.put(InviteSchema.SUBTOPIC, new Getter() {
                @Override
                public Object getValue(final Invitation invitation) {
                    return invitation.key.subtopic;
                }
            });

            getters.put(InviteSchema.EXPIRATION, new Getter() {
                @Override
                public Object getValue(final Invitation invitation) {
                    return invitation.getExpiration();
                }
            });
            
            getters.put(InviteSchema.SUBTOPIC, new Getter() {
                @Override
                public Object getValue(final Invitation invitation) {
                    return invitation.getInvitee();
                }
            });

        }
        
        public Object getInvitee() {
            return this.invitee;
        }

    }

    public static Collection<Invitation> queryAll() {
        return InvitationMap.INSTANCE.impl.values();
    }

}
