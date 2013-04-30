package edu.vanderbilt.isis.ammo.ui;

import java.util.List;
import java.util.logging.Logger;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import edu.vu.isis.ammo.core.AmmoService;
import edu.vu.isis.ammo.core.network.INetworkService;
import edu.vu.isis.ammo.core.model.Gateway;
import edu.vu.isis.ammo.core.model.ModelChannel;
import edu.vu.isis.ammo.core.model.Multicast;
import edu.vu.isis.ammo.core.model.ReliableMulticast;
import edu.vu.isis.ammo.core.model.Serial;
import edu.vu.isis.ammo.ui.AmmoCore;
import edu.vu.isis.ammo.ui.ChannelAdapter;
import edu.vu.isis.ammo.ui.ChannelListView;
import edu.vu.isis.ammo.core.ui.GatewayPreferences;
import edu.vu.isis.ammo.core.ui.MulticastPreferences;
import edu.vu.isis.ammo.core.ui.ReliableMulticastPreferences;
import edu.vu.isis.ammo.core.ui.SerialPreferences;

public class AmmoTest extends Activity{
    ComponentName svc1;
	private INetworkService networkServiceBinder;
	private List<ModelChannel> channelModel = null;
	private ChannelAdapter channelAdapter = null;
	private ChannelListView channelList = null;

	
    ServiceConnection networkServiceConnection = new ServiceConnection(){

    	
		public void onServiceConnected(ComponentName name, IBinder service) {
			Toast.makeText(getApplicationContext(), "Service Connected!", Toast.LENGTH_SHORT).show();
			final AmmoService.DistributorServiceAidl binder = (AmmoService.DistributorServiceAidl) service;
			(AmmoTest.this).networkServiceBinder = binder.getService();
		}

		public void onServiceDisconnected(ComponentName name) {
			Toast.makeText(getApplicationContext(), "Service DISConnected!", Toast.LENGTH_SHORT).show();
		}
    	
    };
    
    
	private void initializeGatewayAdapter() {
		channelModel = networkServiceBinder.getGatewayList();

		// set gateway view references
		channelList = (ChannelListView) findViewById(R.id.gateway_list);

		edu.vu.isis.ammo.ui.AmmoCore _parent = null;
		channelAdapter = new ChannelAdapter(_parent, channelModel);
		channelList.setAdapter(channelAdapter);

		// reset all rows
		for (int ix = 0; ix < channelList.getChildCount(); ix++) {
			View row = channelList.getChildAt(ix);
			row.setBackgroundColor(Color.TRANSPARENT);
		}

		// add click listener to channelList
		channelList.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Intent intent = new Intent();
				ModelChannel selectedChannel = channelAdapter.getItem(position);
				if (selectedChannel instanceof Gateway) {
					intent.setClass(AmmoTest.this, GatewayPreferences.class);
				} else if (selectedChannel instanceof Serial) {
					intent.setClass(AmmoTest.this, SerialPreferences.class);
				} else if (selectedChannel instanceof ReliableMulticast) {
					intent.setClass(AmmoTest.this, ReliableMulticastPreferences.class);
				} else if (selectedChannel instanceof Multicast) {
					intent.setClass(AmmoTest.this, MulticastPreferences.class);
				} else {
					Toast.makeText(AmmoTest.this, "Did not recognize channel",
							Toast.LENGTH_SHORT).show();
					return;
				}
				AmmoTest.this.startActivity(intent);
			}

		});

	}
    
    
	@Override
	public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ammotest);
        AmmoService xx;
        Button startButton = (Button) findViewById(R.id.Button01a);
        startButton.setOnClickListener(new View.OnClickListener() {
			 
			public void onClick(View v) {
				Intent intent = new Intent();
			    intent.setComponent(ComponentName.unflattenFromString("edu.vu.isis.ammo.core/edu.vanderbilt.isis.ammo.core.AmmoService"));
			    //intent.addCategory("android.intent.category.LAUNCHER");
				svc1 = startService(intent);
			}
		});
        
        Button stopButton = (Button) findViewById(R.id.Button02a);
        stopButton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View arg0) {
			    Logger logger = Logger.getLogger("logger1");
				final Intent networkServiceIntent = new Intent("android.intent.action.MAIN").setComponent(ComponentName.unflattenFromString("edu.vu.isis.ammo.core/edu.vu.isis.ammo.core.AmmoService"));
				boolean result = bindService(networkServiceIntent,
						networkServiceConnection, BIND_AUTO_CREATE);
				
				if (!result){
					logger.warning("AmmoActivity failed to bind to the AmmoService!");
					Toast.makeText(getApplicationContext(), "AmmoActivity failed to bind to the AmmoService!", Toast.LENGTH_SHORT).show();
				}else{
					Toast.makeText(getApplicationContext(), "AmmoActivity SUCCESFULLY BOUND to AmmoService!", Toast.LENGTH_SHORT).show();
				}
					

			}
		});
	}
}
