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

