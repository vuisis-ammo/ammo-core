package edu.vu.isis.ammoui;

public enum ChannelState {
    
    PENDING(R.string.status_pending, R.color.status_pending),
    EXCEPTION(R.string.status_exception, R.color.status_exception),
    
    CONNECTING(R.string.status_connecting, R.color.status_connecting),
    CONNECTED(R.string.status_connected, R.color.status_connected),
    BUSY(R.string.status_busy, R.color.status_busy),
    READY(R.string.status_ready, R.color.status_ready),
    
    DISCONNECTED(R.string.status_disconnected, R.color.status_disconnected),
    STALE(R.string.status_stale, R.color.status_stale),
    LINK_WAIT(R.string.status_link_wait, R.color.status_link_wait),
    LINK_ACTIVE(R.string.status_link_active, R.color.status_link_active),
    DISABLED(R.string.status_disabled, R.color.status_disabled),
    
    WAIT_CONNECT(R.string.status_waiting, R.color.status_waiting_conn),
    SENDING(R.string.status_sending, R.color.status_sending),
    TAKING(R.string.status_taking, R.color.status_taking),
    INTERRUPTED(R.string.status_interrupted, R.color.status_interrupted),
    
    SHUTDOWN(R.string.status_shutdown, R.color.status_shutdown),
    START(R.string.status_start, R.color.status_start),
    RESTART(R.string.status_start, R.color.status_start),
    WAIT_RECONNECT(R.string.status_waiting, R.color.status_waiting_conn),
    STARTED(R.string.status_started, R.color.status_started),
    SIZED(R.string.status_sized, R.color.status_sized),
    CHECKED(R.string.status_checked, R.color.status_checked),
    DELIVER(R.string.status_deliver, R.color.status_deliver),
    
    // "Extra" states that AmmoEngine does not explicitly use right now
    // but are nonetheless important for the UI.  These are remnants of the way
    // the channel adapter used to work in the old UI and will probably be
    // changed once the channels are refactored.
    WAIT_RECEIVER_CONNECT(R.string.status_waiting, R.color.status_waiting_recv),
    WAIT_RECEIVER_RECONNECT(R.string.status_waiting, R.color.status_waiting_recv),
    WAIT_SENDER_CONNECT(R.string.status_waiting, R.color.status_waiting_send),
    WAIT_SENDER_RECONNECT(R.string.status_waiting, R.color.status_waiting_send);
    
    private int mStringResId, mColorResId;
    
    private ChannelState(int stringResId, int colorResId) {
        mStringResId = stringResId;
        mColorResId = colorResId;
    }
    
    public int getStringResId() {
        return mStringResId;
    }
    
    public int getColorResId() {
        return mColorResId;
    }
    
}
