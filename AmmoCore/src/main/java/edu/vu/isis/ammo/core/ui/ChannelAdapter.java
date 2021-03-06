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

package edu.vu.isis.ammo.core.ui;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.OnNameChangeListener;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.ModelChannel;
import edu.vu.isis.ammo.core.model.Multicast;
import edu.vu.isis.ammo.core.model.ReliableMulticast;
import edu.vu.isis.ammo.core.model.Serial;
import edu.vu.isis.ammo.core.network.INetChannel;
import edu.vu.isis.ammo.core.network.SerialChannel;


/**
 * An adapter for the channels.
 *
 */
public class ChannelAdapter extends ArrayAdapter<ModelChannel>
    implements OnTouchListener, OnNameChangeListener
{
    public static final Logger logger = LoggerFactory.getLogger("ui.channel");

    private final AmmoCore parent;
    private final Resources res;
    private final List<ModelChannel> model;
    private SharedPreferences prefs = null;


    public ChannelAdapter( AmmoCore parent, List<ModelChannel> model )
    {
        super( parent,
               android.R.layout.simple_list_item_1,
               model);
        this.parent = parent;
        this.res = this.parent.getResources();
        this.model = model;
        this.prefs = PreferenceManager.getDefaultSharedPreferences( parent );

        for ( ModelChannel c : model ) {
            if ( Gateway.class.isInstance( c )) {
                if ( prefs.getBoolean( INetPrefKeys.GATEWAY_DISABLED, 
                                       INetPrefKeys.DEFAULT_GATEWAY_ENABLED ))
                    c.disable();
                else
                    c.enable();
            } else if ( Multicast.class.isInstance( c )) {
                if ( prefs.getBoolean( INetPrefKeys.MULTICAST_DISABLED,
                                       INetPrefKeys.DEFAULT_MULTICAST_ENABLED ))
                    c.disable();
                else
                    c.enable();
            } else if ( ReliableMulticast.class.isInstance( c )) {
                if ( prefs.getBoolean( INetPrefKeys.RELIABLE_MULTICAST_DISABLED, 
                                       INetPrefKeys.DEFAULT_RELIABLE_MULTICAST_ENABLED ))
                    c.disable();
                else
                    c.enable();
            } else if ( Serial.class.isInstance( c ) ) {
                if ( prefs.getBoolean(INetPrefKeys.SERIAL_DISABLED,
                                      INetPrefKeys.DEFAULT_SERIAL_ENABLED ))
                    c.disable();
                else
                    c.enable();
            } else {
                logger.error( "Invalid channel type." );
            }
            c.setOnNameChangeListener(this);
        }
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
    	ModelChannel channel = this.model.get(position);
    	View row = channel.getView(convertView, this.parent.getLayoutInflater());
    	onStatusChange( row, channel );
    	return row;
    }


    @Override
    public boolean onTouch(View view, MotionEvent event) {
    	Toast.makeText(this.getContext(), "Event: " + event.getAction(), Toast.LENGTH_SHORT).show();
    	// Only perform this transform on image buttons for now.
        if (view.getClass() != RelativeLayout.class) return false;

        RelativeLayout item = (RelativeLayout) view;
        int action = event.getAction();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE:
            // NOTE: Do nothing here for now, since no functionality
            // is implemented behind a click action...
            break;

        default:
            item.setBackgroundColor(Color.TRANSPARENT);
        }

        return false;
    }


    private boolean onStatusChange( View item, ModelChannel channel )
    {
        int[] status = channel.getStatus();
        
        if ( status == null )
            return false;
        if ( status.length < 1 )
            return false;

        final View row = item;

        TextView text_one = null;
        TextView text_two = null;
        TextView text_send = null;
        TextView text_receive = null;
        TextView text = null;
        ToggleButton icon = null;

        String channelType = (String) ((TextView) row.findViewById( R.id.channel_type )).getText();
        if ( channelType.equals(Gateway.KEY) )
        {
	        text_one = (TextView)row.findViewById(R.id.gateway_status_text_one);
	        text_two = (TextView)row.findViewById(R.id.gateway_status_text_two);

	        text_send = (TextView) row.findViewById( R.id.gateway_send_stats );
	        text_receive = (TextView) row.findViewById( R.id.gateway_receive_stats );
        }
        else if ( channelType.equals(Multicast.KEY) )
        {
	        text_one = (TextView)row.findViewById(R.id.multicast_status_one);
	        text_two = (TextView)row.findViewById(R.id.multicast_status_two);

	        text_send = (TextView) row.findViewById( R.id.multicast_send_stats );
	        text_receive = (TextView) row.findViewById( R.id.multicast_receive_stats );
        }
        else if ( channelType.equals(ReliableMulticast.KEY) )
        {
	        text_one = (TextView)row.findViewById(R.id.reliable_multicast_status_one);
	        text_two = (TextView)row.findViewById(R.id.reliable_multicast_status_two);

	        text_send = (TextView) row.findViewById( R.id.reliable_multicast_send_stats );
	        text_receive = (TextView) row.findViewById( R.id.reliable_multicast_receive_stats );
        }
        else if ( channelType.equals(Serial.KEY) )
        {
	        text_one = (TextView) row.findViewById(R.id.serial_status_one);
	        text_two = (TextView) row.findViewById(R.id.serial_status_two);

	        text_send = (TextView) row.findViewById( R.id.serial_send_stats );
	        text_receive = (TextView) row.findViewById( R.id.serial_receive_stats );
        }

        if ( text_one == null ) {
            logger.error("text field is null");
            return false;
        }

        if ( text_two != null )
            text_two.setVisibility(TextView.INVISIBLE);

        text = text_one;

        // There isn't enough room for these stats when we're in
        // portrait mode, so hide the widgets in that case.
        if ( res.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT ) {
            text_send.setVisibility( View.INVISIBLE );
            text_receive.setVisibility( View.INVISIBLE );
        }

        if ( parent.netlinkAdvancedView )
        {
            if ( channel.getClass() == Serial.class ) {
                // If the Channel is a Serial, handle things separately here.
                // In debug mode we are displaying stats about packets sent,
                // packets received, etc., instead of the normal text.

                SerialChannel ch = Serial.mChannel;

                switch ( status[0] ) {
                case INetChannel.DISABLED:
                    setColor( icon, text_one, R.color.status_disabled );
                    setColor( icon, text_two, R.color.status_disabled );
                    break;
                case INetChannel.LINK_WAIT:
                    setColor( icon, text_one, R.color.status_connecting );
                    setColor( icon, text_two, R.color.status_connecting );
                    break;
                case INetChannel.CONNECTED:
                    setColor( icon, text_one, R.color.status_transmitting );
                    setColor( icon, text_two, R.color.status_transmitting );
                    break;
                case INetChannel.BUSY:
                    setColor( icon, text_one, R.color.status_busy );
                    setColor( icon, text_two, R.color.status_busy );
                    break;
                default:
                    setColor( icon, text_one, R.color.status_unknown );
                    setColor( icon, text_two, R.color.status_unknown );
                    break;
                }

                // Display the send/receive counts on line one.
                StringBuilder countsString = new StringBuilder();
                countsString.append( "S:" ).append( ch.getMessagesSent() ).append( " " );
                countsString.append( "R:" ).append( ch.getMessagesReceived() );
                text_one.setText( countsString.toString() );

                // Display the error counts on line two.
                StringBuilder errorString = new StringBuilder();
                errorString.append( "@:" ).append( ch.getCorruptMessages() ).append( " " );
                errorString.append( ch.getReceiverSubstate() );
                errorString.append( ":" ).append( ch.getBytesSinceMagic() ).append( " " );
                errorString.append( "N:" ).append( ch.getSecondsSinceByteRead() );
                text_two.setText( errorString.toString() );
                text_two.setVisibility( TextView.VISIBLE );

                if ( text_send != null )
                    text_send.setText( ch.getSendBitStats());
                if ( text_receive != null )
                    text_receive.setText( ch.getReceiveBitStats());
            } else {
                // Channels that are not Serial.
                switch (status[0]) {

                case INetChannel.DISABLED:
                    setColor(icon, text, R.color.status_disabled);
                    text.setText(R.string.status_disabled);
                    break;
                case INetChannel.PENDING:
                    setColor(icon, text, R.color.status_pending);
                    text.setText(R.string.status_pending);
                    break;
                case INetChannel.EXCEPTION:
                    setColor(icon, text, R.color.status_exception);
                    text.setText(R.string.status_exception);
                    break;
                case INetChannel.CONNECTING:
                    setColor(icon, text, R.color.status_connecting);
                    text.setText(R.string.status_connecting);
                    break;
                case INetChannel.BUSY:
                case INetChannel.CONNECTED:
                    //START INetChannel.CONNECTED
                    if (status.length < 1) break;
                    switch (status[1]) {
                    case INetChannel.SENDING:
                        setColor(icon, text, R.color.status_sending);
                        text.setText(R.string.status_sending);
                        break;
                    case INetChannel.TAKING:
                        setColor(icon, text, R.color.status_taking);
                        text.setText(R.string.status_taking);
                        break;
                    case INetChannel.WAIT_CONNECT:
                    case INetChannel.WAIT_RECONNECT:
                        setColor(icon, text, R.color.status_waiting_recv);
                        text.setText(R.string.status_waiting);
                        break;
                    default:
                        logger.error("missing sender status handling {}", status[1]);
                        setColor(icon, text, R.color.status_unknown);
                        text.setText(R.string.status_unknown);
                    }

                    if (status.length < 2) break;
                    text = text_two;
                    text.setVisibility(TextView.VISIBLE);

                    switch (status[2]) {
                    case INetChannel.SIZED:
                        text.setText(R.string.status_sized);
                        break;
                    case INetChannel.CHECKED:
                        setColor(icon, text, R.color.status_checked);
                        text.setText(R.string.status_checked);
                        break;
                    case INetChannel.DELIVER:
                        setColor(icon, text, R.color.status_deliver);
                        text.setText(R.string.status_deliver);
                        break;
                    case INetChannel.WAIT_CONNECT:
                    case INetChannel.WAIT_RECONNECT:
                        setColor(icon, text, R.color.status_waiting_recv);
                        text.setText(R.string.status_waiting);
                        break;
                    case INetChannel.START:
                    case INetChannel.RESTART:
                        setColor(icon, text, R.color.status_start);
                        text.setText(R.string.status_start);
                        break;
                    default:
                        logger.error("missing receiver status handling {}", status[2]);
                        setColor(icon, text, R.color.status_unknown);
                        text.setText(R.string.status_unknown);
                    }
                    break;
                    //END INetChannel.CONNECTED

                case INetChannel.DISCONNECTED:
                    setColor(icon, text, R.color.status_disconnected);
                    text.setText(R.string.status_disconnected);
                    break;
                case INetChannel.STALE:
                    setColor(icon, text, R.color.status_stale);
                    text.setText(R.string.status_stale);
                    break;
                case INetChannel.LINK_WAIT:
                    setColor(icon, text, R.color.status_link_wait);
                    text.setText(R.string.status_link_wait);
                    break;
                case INetChannel.LINK_ACTIVE:
                    setColor(icon, text, R.color.status_link_active);
                    text.setText(R.string.status_link_active);
                    break;
                case INetChannel.WAIT_CONNECT:
                case INetChannel.WAIT_RECONNECT:
                    setColor(icon, text, R.color.status_waiting_conn);
                    text.setText(R.string.status_waiting);
                    break;

                case INetChannel.INTERRUPTED:
                    setColor(icon, text, R.color.status_interrupted);
                    text.setText(R.string.status_interrupted);
                    break;
                case INetChannel.SHUTDOWN:
                    setColor(icon, text, R.color.status_shutdown);
                    text.setText(R.string.status_shutdown);
                    break;
                case INetChannel.START:
                case INetChannel.RESTART:
                    setColor(icon, text, R.color.status_start);
                    text.setText(R.string.status_start);
                    break;
                case INetChannel.STARTED:
                    setColor(icon, text, R.color.status_started);
                    text.setText(R.string.status_started);
                    break;

                default:
                    setColor(icon, text, R.color.status_unknown);
                    // text.setText(R.string.status_unknown);
                    text.setText("unknown ["+status[0]+"]");
                }
                // Display the send/receive counts on line one.
                text_one.setText( channel.getNetChannel().getSendReceiveStats());
                if ( text_send != null )
                    text_send.setText( channel.getNetChannel().getSendBitStats());
                if ( text_receive != null )
                    text_receive.setText( channel.getNetChannel().getReceiveBitStats());
            }
        }

        //User Mode
        else
        {
            /*
                Pending         |   Transmitting
                Sending         |   Transmitting
                Taking          |   Transmitting
                Start           |   Transmitting
                Started         |   Transmitting
                Exception       |   Error: Restart Omma
                Waiting         |   Error: Restart Omma
                Interrupted     |   Error: Restart Omma
                Connecting      |   Connecting
                Linking         |   Connecting
                Linked          |   Connecting
                Disconnected    |   Disconnected
                Shutdown        |   Disconnected
                Stale           |   Checking Status
            */
            switch (status[0]) {

            case INetChannel.DISABLED:
                setColor(icon, text, R.color.status_disabled);
                text.setText(R.string.status_disabled);
                break;
            case INetChannel.START:
            case INetChannel.RESTART:
            case INetChannel.PENDING:
            case INetChannel.STARTED:
                setColor(icon, text, R.color.status_transmitting);
                text.setText(R.string.status_transmitting);
                break;

            case INetChannel.EXCEPTION:
            case INetChannel.INTERRUPTED:
            case INetChannel.WAIT_CONNECT:
            case INetChannel.WAIT_RECONNECT:
                setColor(icon, text, R.color.status_error);
                text.setText(R.string.status_error);
                break;

            case INetChannel.CONNECTING:
            case INetChannel.LINK_WAIT:
            case INetChannel.LINK_ACTIVE:
                setColor(icon, text, R.color.status_connecting);
                text.setText(R.string.status_connecting);
                break;

            case INetChannel.DISCONNECTED:
            case INetChannel.SHUTDOWN:
                setColor(icon, text, R.color.status_disconnected);
                text.setText(R.string.status_disconnected);
                break;

            case INetChannel.STALE:
                setColor(icon, text, R.color.status_checking);
                text.setText(R.string.status_checking);
                break;

            case INetChannel.BUSY:
            case INetChannel.CONNECTED:
                //START INetChannel.CONNECTED
                if (status.length < 1) break;
                switch (status[1]) {
                case INetChannel.SENDING:
                case INetChannel.TAKING:
                    setColor(icon, text, R.color.status_transmitting);
                    text.setText(R.string.status_transmitting);
                    break;
                case INetChannel.WAIT_CONNECT:
                case INetChannel.WAIT_RECONNECT:
                    setColor(icon, text, R.color.status_error);
                    text.setText(R.string.status_error);
                    break;
                default:
                    logger.error("missing sender status handling {}", status[1]);
                    setColor(icon, text, R.color.status_unknown);
                    text.setText(R.string.status_unknown);
                }
                break;
                //END INetChannel.CONNECTED

            default:
                setColor(icon, text, R.color.status_unknown);
                // text.setText(R.string.status_unknown);
                text.setText("unknown ["+status[0]+"]");
            }
        }

        item.refreshDrawableState();
        return true;
    }
    
    private void setColor(ToggleButton icon, TextView text, int resColor) {
        int color = this.res.getColor(resColor);
        if (icon != null) icon.setTextColor(R.color.togglebutton_default);
        if (text != null) text.setTextColor(color);
    }
    
    @Override
    public boolean onNameChange() {
        parent.refreshList();
        return false;
    }

    //public Channel getItemByName(String name) {
    //    for (int ix=0; ix < this.model.size(); ix++) {
    //        Channel item = this.model.get(ix);
    //        if (! item.getName().equalsIgnoreCase(name)) continue;
    //        return item;
    //    }
    //    return null;
    //}
}

