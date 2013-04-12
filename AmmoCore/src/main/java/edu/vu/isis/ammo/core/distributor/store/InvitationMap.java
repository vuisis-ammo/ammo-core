
package edu.vu.isis.ammo.core.distributor.store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.text.TextUtils;
import edu.vu.isis.ammo.api.type.Notice;
import edu.vu.isis.ammo.api.type.Topic;
import edu.vu.isis.ammo.core.PLogger;
import edu.vu.isis.ammo.core.distributor.Dispersal;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore;
import edu.vu.isis.ammo.core.distributor.DistributorDataStore.DisposalState;
import edu.vu.isis.ammo.core.distributor.store.Presence.Worker;
import edu.vu.isis.ammo.core.pb.AmmoMessages.SubtopicInvitation.Invitee;
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
    private final Map<Invitation.Key, Invitation> mapTopic;
    private final Map<UUID, Invitation> mapUuid;
    private final ConcurrentLinkedQueue<Invitation> toSendQueue;

    public ConcurrentLinkedQueue<Invitation> toSendQueue() {
        return this.toSendQueue;
    }

    public int size() {
        return this.mapTopic.size();
    }

    private InvitationMap() {
        this.mapTopic = new ConcurrentHashMap<Invitation.Key, Invitation>();
        this.mapUuid = new ConcurrentHashMap<UUID, Invitation>();
        this.toSendQueue = new ConcurrentLinkedQueue<Invitation>();
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
     * Used for building invitations. These invitations will be placed in the
     * map.
     */
    public static class Builder {
        private final static Logger logger = LoggerFactory
                .getLogger("class.store.invitation.builder");

        protected String topic = "default topic";
        protected String[] subtopic = null;
        protected List<String> invitee = null;

        protected Object expiration;
        protected DisposalState disposition;
        protected UUID uuid;
        protected Dispersal route;

        protected Notice notice;
        protected String auid;

        private Builder() {
        }

        public Builder topic(String value) {
            this.topic = value;
            return this;
        }

        public Builder subtopic(final Topic[] value) {
            this.subtopic = new String[value.length];
            for (int ix = 0; ix < value.length; ++ix) {
                this.subtopic[ix] = value[ix].asString();
            }
            return this;
        }

        public Builder subtopic(final String[] value) {
            this.subtopic = value;
            return this;
        }

        public Builder uuid(final UUID value) {
            this.uuid = value;
            return this;
        }

        public Builder expiration(final long value) {
            this.expiration = value;
            return this;
        }

        public Builder disposition(final DisposalState value) {
            this.disposition = value;
            return this;
        }

        public Builder route(final Dispersal value) {
            this.route = value;
            return this;
        }

        public Builder invitee(final String value) {
            this.invitee.add(value);
            return this;
        }

        public Builder invitee(final String[] value) {
            this.invitee.addAll(Arrays.asList(value));
            return this;
        }

        /**
         * builds the invitation and adds it to the work queues.
         * 
         * @return
         */
        public Invitation build() {
            final Invitation item = new Invitation(this);
            logger.debug("ctor [{}]", item);
            InvitationMap.INSTANCE.mapTopic.put(item.key, item);
            InvitationMap.INSTANCE.mapUuid.put(item.key.uuid, item);
            item.enqueue();

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

        public Builder auid(String auid) {
            this.auid = auid;
            return this;
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

        public Worker invitee(Map<String, Object> value) {
            this.invitee = value;
            return this;
        }
        

        public Worker invitee(List<Invitee> value) {
            final Map<String,Object> invited = new HashMap<String, Object>(value.size());
            for (final Invitee entry : value) {
                invited.put(entry.getName(), null);
            }
            return this;
        }

        /**
         * upsert the tuple indicated use the key to determine if the item is
         * already present
         * 
         * @return
         */
        public long upsert() {
            PLogger.STORE_INVITATION_DML.trace("upsert invitation: device=[{}] @ {}", this);
            final InvitationMap relation = InvitationMap.INSTANCE;
            synchronized (relation) {

                final Builder builder = newBuilder()
                        .topic(this.topic)
                        .subtopic(this.subtopic);
                try {
                    final Invitation.Key key = builder.buildKey();

                    if (relation.mapTopic.containsKey(key)) {
                        final Invitation invitation = relation.mapTopic.get(key);
                        invitation.update();
                        PLogger.STORE_INVITATION_DML.debug("updated item=[{}]", invitation);
                        return 1;
                    } else {
                        final Invitation item = builder.build();
                        relation.mapTopic.put(key, item);
                        relation.mapUuid.put(key.uuid, item);
                        PLogger.STORE_INVITATION_DML.debug("inserted item=[{}]", item);
                        return 1;
                    }

                } catch (IllegalArgumentException ex) {
                    logger.error("update invitation", ex);
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
        public int delete() {
            PLogger.STORE_INVITATION_DML.trace("delete invitation: device=[{}] @ {}",
                    this);
            final InvitationMap relation = InvitationMap.INSTANCE;
            synchronized (relation) {

                try {
                    final Invitation.Key key = newBuilder()
                            .topic(this.topic)
                            .subtopic(this.subtopic)
                            .buildKey();

                    final Invitation invitation = relation.mapTopic.remove(key);
                    if (invitation == null) {
                        PLogger.STORE_INVITATION_DML.debug("deleted invite=[{}]", this);
                        return -1;
                    }
                    invitation.setState(DistributorDataStore.DisposalState.CANCELLED);
                    relation.mapUuid.remove(invitation.key.uuid);

                } catch (IllegalArgumentException ex) {
                    logger.error("deleting invitation", ex);
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
        public static final class Key {
            public final long id;
            public final UUID uuid;
            public final String topic;
            public final String[] subtopic;
            public final long created;

            final private int hashCode;

            @Override
            public int hashCode() {
                return this.hashCode;
            }

            private Key(Builder that) {
                InvitationMap._id_seq++;
                this.created = System.currentTimeMillis();
                this.id = InvitationMap._id_seq;
                this.uuid = that.uuid;
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
                if (this.subtopic.length != that.subtopic.length) {
                    return false;
                }
                for (int ix = 0; ix < this.subtopic.length; ++ix) {
                    if (!TextUtils.equals(this.subtopic[ix], that.subtopic[ix]))
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
        private final AtomicReference<DisposalState> disposition =
                new AtomicReference<DisposalState>(DisposalState.NEW);
        public final Notice notice;
        public final Dispersal route;

        public Invitation(final Builder that) {
            super();
            this.key = new Key(that);
            this.invitee = that.invitee;
            this.disposition.set(that.disposition);
            this.notice = that.notice;
            this.route = that.route;
        }

        public DisposalState setState(final DisposalState state) {
            final DisposalState oldState = this.disposition.getAndSet(state);
            this.enqueue();
            return oldState;
        }

        public void enqueue() {
            switch (this.disposition.get()) {
                case CANCELLED:
                    break;
                case NEW:
                    InvitationMap.INSTANCE.toSendQueue.offer(this);
                    break;
                case PENDING:
                    InvitationMap.INSTANCE.toSendQueue.offer(this);
                    break;
                case BAD:
                    break;
                case BUSY:
                    InvitationMap.INSTANCE.toSendQueue.offer(this);
                    break;
                case DELIVERED:
                    break;
                case QUEUED:
                    break;
                case REJECTED:
                    break;
                case SENT:
                    break;
                case TOLD:
                    break;
                default:
                    break;
            }
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
        return InvitationMap.INSTANCE.mapTopic.values();
    }

  

    public static enum Select {
        /** topic + subtopic */
        BY_SUBTOPIC;

    }

    public int deleteGarbage() {
        // TODO determine which expired invitations to remove.
        return 0;
    }
    
    

}
