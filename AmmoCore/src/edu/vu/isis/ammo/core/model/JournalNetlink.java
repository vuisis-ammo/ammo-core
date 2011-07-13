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
        // int[] status = new int[]{ 3 };
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
    public void teardown() {
        ;
    }

}

