
package edu.vu.isis.ammoui.util;

import edu.vu.isis.ammo.api.IAmmo;
import edu.vu.isis.ammoui.ChannelState;
import android.util.SparseArray;

/**
 * Class for storing various static utility methods that help with the UI
 * 
 * @author nick
 */
public final class UiUtils {

    private UiUtils() {
        throw new AssertionError("Do not instantiate this class");
    }

    private static final SparseArray<ChannelState> CHANNEL_STATE_MAP;
    private static final int NUM_CHANNEL_STATES = 23;

    static {
        CHANNEL_STATE_MAP = new SparseArray<ChannelState>(NUM_CHANNEL_STATES);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.PENDING, ChannelState.PENDING);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.EXCEPTION, ChannelState.EXCEPTION);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.CONNECTING, ChannelState.CONNECTING);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.CONNECTED, ChannelState.CONNECTED);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.BUSY, ChannelState.BUSY);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.READY, ChannelState.READY);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.DISCONNECTED, ChannelState.DISCONNECTED);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.STALE, ChannelState.STALE);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.LINK_WAIT, ChannelState.LINK_WAIT);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.LINK_ACTIVE, ChannelState.LINK_ACTIVE);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.DISABLED, ChannelState.DISABLED);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.WAIT_CONNECT, ChannelState.WAIT_CONNECT);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.SENDING, ChannelState.SENDING);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.TAKING, ChannelState.TAKING);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.INTERRUPTED, ChannelState.INTERRUPTED);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.SHUTDOWN, ChannelState.SHUTDOWN);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.START, ChannelState.START);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.RESTART, ChannelState.RESTART);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.WAIT_RECONNECT, ChannelState.WAIT_RECONNECT);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.STARTED, ChannelState.STARTED);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.SIZED, ChannelState.SIZED);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.CHECKED, ChannelState.CHECKED);
        CHANNEL_STATE_MAP.put(IAmmo.NetChannelState.DELIVER, ChannelState.DELIVER);
    }

    public static ChannelState getChannelStateFromInt(int state) {
        return CHANNEL_STATE_MAP.get(state);
    }

    public static ChannelState getEffectiveChannelState(int cState, int sState, int rState) {
        ChannelState effectiveState;
        if (cState == IAmmo.NetChannelState.CONNECTED) {
            // Prefer to show receiver state if it is not pending.
            // Otherwise show sender state.
            if (rState != IAmmo.NetChannelState.PENDING) {
                effectiveState = getChannelStateFromInt(rState);
                if (effectiveState.equals(ChannelState.WAIT_CONNECT)) {
                    effectiveState = ChannelState.WAIT_RECEIVER_CONNECT;
                } else if (effectiveState.equals(ChannelState.WAIT_RECONNECT)) {
                    effectiveState = ChannelState.WAIT_RECEIVER_RECONNECT;
                }
            } else if (sState != IAmmo.NetChannelState.PENDING) {
                effectiveState = getChannelStateFromInt(sState);
                if (effectiveState.equals(ChannelState.WAIT_CONNECT)) {
                    effectiveState = ChannelState.WAIT_SENDER_CONNECT;
                } else if (effectiveState.equals(ChannelState.WAIT_RECONNECT)) {
                    effectiveState = ChannelState.WAIT_SENDER_RECONNECT;
                }
            } else {
                effectiveState = ChannelState.CONNECTED;
            }
        } else {
            effectiveState = getChannelStateFromInt(cState);
        }
        return effectiveState;
    }

}
