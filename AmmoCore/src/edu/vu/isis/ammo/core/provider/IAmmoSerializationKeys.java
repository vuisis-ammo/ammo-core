package edu.vu.isis.ammo.core.provider;

/**
 * Interface which declares constants used as keys for read/write operations
 * to the shared Bundle
 * @author Demetri Miller
 *
 */
public interface IAmmoSerializationKeys {
    // Intent action that should be used.
    public static final String SEND_SERIALIZED_ACTION = "SEND_SERIALIZED";

    public static final String SERIALIZED_STRING_KEY = "serializedString";
    public static final String SERIALIZED_BYTE_ARRAY_KEY = "serializedByteArray";
}
