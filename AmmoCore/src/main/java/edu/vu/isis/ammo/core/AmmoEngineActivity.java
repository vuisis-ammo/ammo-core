package edu.vu.isis.ammo.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class AmmoEngineActivity extends Activity {
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.ammo_activity);
    }
    
    public void hardResetClick(View v) {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        Intent intent = new Intent();
                        intent.setAction("edu.vu.isis.ammo.AMMO_HARD_RESET");
                        intent.setClass(AmmoEngineActivity.this, AmmoService.class);
                        startService(intent);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }

            }
        };
        AlertDialog.Builder bldr = new AlertDialog.Builder(this);
        bldr.setMessage("Are you sure you want to reset the service?")
                .setPositiveButton("Yes", listener)
                .setNegativeButton("No", listener).show();
    }


}
