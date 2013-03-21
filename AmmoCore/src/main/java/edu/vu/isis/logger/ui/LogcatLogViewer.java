
package edu.vu.isis.logger.ui;

import java.util.List;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class LogcatLogViewer extends LogViewerBase {

    public static final int CONCAT_DATA_MSG = 0;
    public static final int NOTIFY_INVALID_REGEX_MSG = 1;

    private static final int OPEN_PREFS_MENU = Menu.NONE + 3;
    private static final int CLEAR_SCREEN_MENU = Menu.NONE + 4;

    private SharedPreferences mPrefs;
    private String mRegex;
    private boolean mShowTimestamps;

    public Handler mHandler = new Handler() {

        LogcatLogViewer parent = LogcatLogViewer.this;

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case CONCAT_DATA_MSG:
                    if (msg.obj != null) {
                        @SuppressWarnings("unchecked")
                        final List<LogElement> elemList = (List<LogElement>) msg.obj;
                        refreshList(elemList);
                    }
                    break;
                case NOTIFY_INVALID_REGEX_MSG:
                    Toast.makeText(parent, "Syntax of regex is invalid",
                            Toast.LENGTH_LONG).show();
                    break;
                default:
                    parent.logger.error("Handler received malformed message");
            }

        }

    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mRegex = mPrefs.getString("regular_expression", "");
        mShowTimestamps = mPrefs.getBoolean("show_logcat_timestamps", false);

        if(mLogReader == null) {
            mLogReader = new LogcatLogReader(this, mHandler, mRegex, mShowTimestamps);
            mLogReader.start();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        // This code reconfigures things according to the set preferences
        configureMaxLinesFromPrefs();
        String regex = mPrefs.getString("regular_expression", "");

        if (mLogReader != null) {
            ((LogcatLogReader) mLogReader).setRegex(regex);
            return;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        final boolean returnValue = true && super.onPrepareOptionsMenu(menu);

        menu.add(Menu.NONE, OPEN_PREFS_MENU, Menu.NONE, "Open preferences");
        menu.add(Menu.NONE, CLEAR_SCREEN_MENU, Menu.NONE, "Clear screen");

        return returnValue;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == OPEN_PREFS_MENU) {
            final Intent intent = new Intent().setClass(this,
                    LogViewerPreferences.class);
            startActivityForResult(intent, 0);
            return true;
        } else if (item.getItemId() == CLEAR_SCREEN_MENU) {
            mAdapter.clear();
            mListView.invalidateViews();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Pause reading until we're done resetting the preferences
        mLogReader.pause();
        configureMaxLinesFromPrefs();

        String newRegex = mPrefs.getString("regular_expression", "");
        boolean newTimestamps = mPrefs.getBoolean("show_logcat_timestamps", false);

        if (!newRegex.equals(mRegex) || newTimestamps != mShowTimestamps) {
            mRegex = newRegex;
            mShowTimestamps = newTimestamps;
            mLogReader.terminate();
            mAdapter.clear();
            mLogReader = new LogcatLogReader(this, mHandler, mRegex, mShowTimestamps);
            mLogReader.pause();
            mLogReader.start();
        }

        if (!isPaused.get())
            mLogReader.resume();

    }

    private void configureMaxLinesFromPrefs() {
        mAdapter.setMaxLines(Math.abs(Integer.parseInt(mPrefs.getString(
                "logcat_max_lines", "1000"))));
    }

    private void refreshList(List<LogElement> elemList) {

        updateAdapter(elemList);
        if (isAutoJump.get()) {
            setScrollToBottom();
        }

    }

    private void updateAdapter(List<LogElement> elemList) {
        mAdapter.addAll(elemList);
        mAdapter.notifyDataSetChanged();
    }

}
