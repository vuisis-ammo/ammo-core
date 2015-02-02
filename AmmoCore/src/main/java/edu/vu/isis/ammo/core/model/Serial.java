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


package edu.vu.isis.ammo.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.R;
import edu.vu.isis.ammo.core.network.SerialChannel;

public class Serial extends ModelChannel {
    
    private static final Logger logger = LoggerFactory.getLogger("model.serial");
    
    public static String KEY = "SerialUiChannel";

    // We might want to provide an accessor for this at some point.
    public static SerialChannel mChannel;

    public static Serial getInstance(Context context, SerialChannel channel)
    {
        if (mInstance == null)
            mInstance = new Serial(context, "Serial Channel", channel);
        return mInstance;
    }

    @Override
    public void enable() {
        setElection(true);
    }

    @Override
    public void disable() {
        setElection(false);
    }

    @Override
    public void toggle() {
        setElection(!mElection);
    }

    @Override
    public boolean isEnabled() {
        return mElection;
    }

    @Override
    public View getView(View row, LayoutInflater inflater)
    {
        row = inflater.inflate(edu.vu.isis.ammo.core.R.layout.serial_item, null);

        TextView name = (TextView) row.findViewById(R.id.serial_name);
        TextView device = (TextView) row.findViewById(R.id.serial_device);
        ((TextView) row.findViewById(R.id.channel_type)).setText(Serial.KEY);

        name.setText(this.name);
        StringBuilder sb = new StringBuilder();
        sb.append(this.prefs.getString(INetPrefKeys.SERIAL_DEVICE, "def device"));
        sb.append("@");
        sb.append(this.prefs.getString(INetPrefKeys.SERIAL_BAUD_RATE, "9600"));
        device.setText(sb.toString());
        return row;
    }

    @Override
    public int[] getStatus() {
        return mStatus;
    }

    @Override
    public void setStatus(int[] statusCode) {
        mStatus = statusCode;
    }
    
    // /////////////////////////////////////////////////////////////////////////
    //
    // Private members
    //

    private Serial(Context context, String name, SerialChannel channel)
    {
        super(context, name);

        mChannel = channel;
        mElection = !this.prefs.getBoolean(INetPrefKeys.SERIAL_DISABLED,
                INetPrefKeys.DEFAULT_SERIAL_ENABLED);
    }

    // FIXME : Should this view, which is only user interface, be writing this
    // value?
    private void setElection(boolean b)
    {
        mElection = b;
        Editor editor = this.prefs.edit();
        editor.putBoolean(INetPrefKeys.SERIAL_DISABLED, !mElection);
        editor.commit();
    }

    private static Serial mInstance;

    private boolean mElection = false;
    private int[] mStatus = null;

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        logger.info("Not implemented for Serial");
    }
}
