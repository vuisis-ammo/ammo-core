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

import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import edu.vu.isis.ammo.INetPrefKeys;
import edu.vu.isis.ammo.core.MyCheckBoxPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference;
import edu.vu.isis.ammo.core.MyEditIntegerPreference.Type;
import edu.vu.isis.ammo.core.MyEditTextPreference;
import edu.vu.isis.ammo.core.R;

public class SerialPreferences extends AbstractAmmoPreference {

    private MyCheckBoxPreference serialOpEnablePref;

    private MyEditTextPreference serialDevicePref;

    private MyEditIntegerPreference serialSlotPref;
    private MyEditIntegerPreference serialRadiosInGroupPref;
    private MyEditIntegerPreference serialSlotDurationPref;
    private MyEditIntegerPreference serialTransmitDurationPref;
    private MyEditIntegerPreference serialBaudPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        logger.error("on create");
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(R.xml.serial_preferences);

        final Resources res = this.getResources();

        this.serialOpEnablePref = (MyCheckBoxPreference) this
                .findPreference(INetPrefKeys.SERIAL_DISABLED);
        this.serialOpEnablePref.setTrueTitle("Serial " + TRUE_TITLE_SUFFIX);
        this.serialOpEnablePref.setFalseTitle("Serial " + FALSE_TITLE_SUFFIX);
        this.serialOpEnablePref.setSummary("Tap to toggle");
        this.serialOpEnablePref
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        serialOpEnablePref.toggle();
                        return true;
                    }
                });

        this.serialDevicePref = (MyEditTextPreference) this
                .findPreference(INetPrefKeys.SERIAL_DEVICE);

        this.serialBaudPref =
                (MyEditIntegerPreference) this.findPreference(INetPrefKeys.SERIAL_BAUD_RATE);
        this.serialBaudPref.setType(Type.BAUDRATE);

        this.serialSlotPref = (MyEditIntegerPreference) this
                .findPreference(INetPrefKeys.SERIAL_SLOT_NUMBER);
        this.serialSlotPref.setType(Type.SLOT_NUMBER);
        this.serialSlotPref.setSummarySuffix(" (Set in Panthr Prefs)");
        this.serialSlotPref
                .setOnPreferenceClickListener(sendToPantherPrefsListener);

        this.serialRadiosInGroupPref = (MyEditIntegerPreference) this
                .findPreference(INetPrefKeys.SERIAL_RADIOS_IN_GROUP);
        this.serialRadiosInGroupPref.setType(Type.RADIOS_IN_GROUP);
        this.serialRadiosInGroupPref
                .setSummarySuffix(" (Set in Panthr Prefs)");
        this.serialRadiosInGroupPref
                .setOnPreferenceClickListener(sendToPantherPrefsListener);

        this.serialSlotDurationPref = (MyEditIntegerPreference) this
                .findPreference(INetPrefKeys.SERIAL_SLOT_DURATION);
        this.serialSlotDurationPref.setType(Type.SLOT_DURATION);

        this.serialTransmitDurationPref = (MyEditIntegerPreference) this
                .findPreference(INetPrefKeys.SERIAL_TRANSMIT_DURATION);
        this.serialTransmitDurationPref.setType(Type.TRANSMIT_DURATION);

        // These were removed from prefs
        // this.serialOpEnableSendingPref =
        // (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_SEND_ENABLED);
        // this.serialOpEnableReceivingPref =
        // (MyCheckBoxPreference)this.findPreference(INetPrefKeys.SERIAL_RECEIVE_ENABLED);
    }

    @Override
    protected void onResume() {
        logger.error("on resume");
        super.onResume();

        this.serialOpEnablePref.refresh();
        this.serialDevicePref.refresh();

        this.serialBaudPref.refresh();

        this.serialSlotPref.refresh();
        this.serialRadiosInGroupPref.refresh();
        this.serialSlotDurationPref.refresh();
        this.serialTransmitDurationPref.refresh();

        // These prefs were removed
        // this.serialOpEnableSendingPref.refresh();
        // this.serialOpEnableReceivingPref.refresh();
    }
}
