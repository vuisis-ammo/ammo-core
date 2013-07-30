
package edu.vu.isis.ammoui.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

public class SendToPanthrPrefsListener implements OnClickListener {

    private Context mContext;

    public SendToPanthrPrefsListener(Context context) {
        mContext = context;
    }

    @Override
    public void onClick(View v) {
        Toast.makeText(mContext, "Sending you to Panthr Prefs...", Toast.LENGTH_LONG).show();
        mContext.startActivity(new Intent().setComponent(new ComponentName("transapps.settings",
                "transapps.settings.SettingsActivity")));
    }

}
