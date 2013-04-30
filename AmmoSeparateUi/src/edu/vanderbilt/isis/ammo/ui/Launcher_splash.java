package edu.vanderbilt.isis.ammo.ui;


import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class Launcher_splash extends Activity{
	public static int x = 0;
	
	@Override
	public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.launcher); 
        
        Button ammo = (Button) findViewById(R.id.ammo_btn);
        ammo.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intenta = new Intent(getApplicationContext(), edu.vu.isis.ammo.ui.AmmoCoreCursorAdapter.class);
			    startActivity(intenta);
			}
		});
        
        Button ammo2 = (Button) findViewById(R.id.ammo_btn_2);
        ammo2.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intentb = new Intent(getApplicationContext(), edu.vu.isis.ammo.ui.AmmoCore.class);
			    startActivity(intentb);
			}
		});
        
        Button ammo3 = (Button) findViewById(R.id.ammo_btn_3);
        ammo3.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent intentd = new Intent(getApplicationContext(), AmmoTest.class);
			    startActivity(intentd);
			}
		});
        
        Button other=(Button)findViewById(R.id.other_btn);
        other.setOnClickListener(new View.OnClickListener(){
        	public void onClick(View v){
				Intent intentc = new Intent(getApplicationContext(), Service_testActivity.class);
			    startActivity(intentc);
        	}
        });
	}
}
