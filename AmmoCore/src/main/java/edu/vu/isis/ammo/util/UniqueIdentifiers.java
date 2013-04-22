/*Copyright (C) 2010-2012 Institute for Software Integrated Systems (ISIS)
This software was developed by the Institute for Software Integrated
Systems (ISIS) at Vanderbilt University, Tennessee, USA for the 
Transformative Apps program under DARPA, Contract # HR011-10-C-0175.
The United States Government has unlimited rights to this software. 
The US government has the right to use, modify, reproduce, release, 
perform, display, or disclose computer software or computer software 
documentation in whole or in part, in any manner and for any 
purpose whatsoever, and to have or authorize others to do so.
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

        final UUID deviceUuid = new UUID(
                ((long) androidId.hashCode() << 32) | macCode.hashCode(),
                ((long) device.hashCode() << 32) | serial.hashCode());
        return "ammo:" + deviceUuid.toString();
    }
}
