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
package edu.vu.isis.ammo.core.model;

import android.content.SharedPreferences;
import edu.vu.isis.ammo.core.ui.util.TabActivityEx;


public class JournalNetlink extends Netlink {

    private JournalNetlink(TabActivityEx context) {
        super(context, "Journal Netlink", "file");
    }

    public static Netlink getInstance(TabActivityEx context) {
        // initialize the gateway from the shared preferences
        return new JournalNetlink(context);
    }

    public void initialize() {
        @SuppressWarnings("unused")
        int[] status = new int[]{ 3 };
        //this.statusListener.onStatusChange(this.statusView, status);
    }

    public void updateStatus() {}

    /**
     * When the status changes update the local variable and any user interface.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        //  if (key.equals(INetPrefKeys.CORE_IS_JOURNALED)) {
        //      this.journalingSwitch = prefs.getBoolean(INetPrefKeys.CORE_IS_JOURNALED, this.journalingSwitch);
        //      if (this.journalingSwitch)
        //          this.journalChannel.enable();
        //      else this.journalChannel.disable();
        //      return;
        //  prefChannelJournal = (MyCheckBoxPreference) findPreference(INetPrefKeys.CORE_IS_JOURNALED);
        //  prefChannelJournal.setSummaryPrefix(res.getString(R.string.channel_journal_label));
        //  prefChannelJournal.setType(MyCheckBoxPreference.Type.JOURNAL);
        //
    }

    @Override
    public void teardown() {;}

}

