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


package edu.vu.isis.ammo.util;

import java.util.UUID;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;

/**
 * Utility functions for getting unique identifiers.
 * 
 */
public class UniqueIdentifiers {

    /**
     * <dl>
     * <dt>device id</dt>
     * <dd>can sometimes be null, if it isn't a phone</dd>
     * <dt>serial number</dt>
     * <dd>only valid for SIM card equipped devices</dd>
     * <dt>android id</dt>
     * <dd>only reliably set if the device has a google account</dd>
     * <dt>mac address</dt>
     * <dd>only available if ethernet is present</dd>
     * </dl>
     * The "android:" on prefix is to help identify the origin.
     * 
     * @param context
     * @return
     */
    static public String device(Context context) {
        if (context == null)
            return "ammo:0123456789";

        final TelephonyManager tm = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);

        if (tm == null) {
            return "test:1234567890";
        }
        final String device = (tm.getDeviceId() == null) ? "null-device" : tm.getDeviceId();
        final String serial = (tm.getSimSerialNumber() == null) ? "null-sim" : tm
                .getSimSerialNumber();
        String androidId;
        try {
            androidId = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ANDROID_ID);
        } catch (NullPointerException ex) {
            androidId = "android id unavailable";
        }

        final WifiManager wfm = (WifiManager) (context.getSystemService(Context.WIFI_SERVICE));
        final String macAddr = (wfm == null) ? "wifi-manager" : wfm.getConnectionInfo()
                .getMacAddress();
        final String macCode = (macAddr != null) ? macAddr : "null";

        UUID deviceUuid = new UUID(
                ((long) androidId.hashCode() << 32) | macCode.hashCode(),
                ((long) device.hashCode() << 32) | serial.hashCode());
        return "ammo:" + deviceUuid.toString();
    }
}
