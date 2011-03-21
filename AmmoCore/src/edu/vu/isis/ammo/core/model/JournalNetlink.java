package edu.vu.isis.ammo.core.model;

import android.content.SharedPreferences;
import edu.vu.isis.ammo.core.ui.ActivityEx;


public class JournalNetlink extends Netlink {

	private JournalNetlink(ActivityEx context, String type) {
		super(context, type);
	}

	public static Netlink getInstance(ActivityEx context) {
		// initialize the gateway from the shared preferences
		return new JournalNetlink(context, "Journal Netlink");
	}


	/** 
	 * When the status changes update the local variable and any user interface.
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

		//	if (key.equals(INetPrefKeys.CORE_IS_JOURNALED)) {
		//		this.journalingSwitch = prefs.getBoolean(INetPrefKeys.CORE_IS_JOURNALED, this.journalingSwitch);
		//		if (this.journalingSwitch)
		//			this.journalChannel.enable();
		//		else this.journalChannel.disable();
		//		return;
		//	prefChannelJournal = (MyCheckBoxPreference) findPreference(INetPrefKeys.CORE_IS_JOURNALED);
		//	prefChannelJournal.setSummaryPrefix(res.getString(R.string.channel_journal_label));
		//	prefChannelJournal.setType(MyCheckBoxPreference.Type.JOURNAL);
		//	
	}

}

