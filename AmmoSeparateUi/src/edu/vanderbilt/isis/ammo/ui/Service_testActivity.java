package edu.vanderbilt.isis.ammo.ui;

import android.app.Activity;
//import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class Service_testActivity extends Activity {
    ComponentName svc1;
    int mol = 42;
    String string1 = "";
    Thread notifythread;  
    SharedPreferences testPrefs = null;
    SharedPreferences.Editor testEd= null; 
    String string2 = "";
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Button startButton = (Button) findViewById(R.id.Button01);
        startButton.setOnClickListener(new View.OnClickListener() {
             
            public void onClick(View v) {
                string1 = "TEST1";
                string2 = "TEST2";
                Intent intent = new Intent();
                intent.setComponent(ComponentName.unflattenFromString("edu.vanderbilt.isis.ammo/edu.vanderbilt.isis.ammo.Service_ammo"));
                //intent.addCategory("android.intent.category.LAUNCHER");
                svc1 = startService(intent.putExtra("string1", string1).putExtra("string2", string2));
                bindAndStuff();
            }
        });
          
        Button stopButton = (Button) findViewById(R.id.Button02);
        stopButton.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
                doUnbindService();
                Intent intent3 = new Intent();
                intent3.setComponent(ComponentName.unflattenFromString("edu.vanderbilt.isis.ammo/edu.vanderbilt.isis.ammo.Service_ammo"));
                stopService(intent3);
            }
        });
        
        Button rawrButton=(Button)findViewById(R.id.Button03);
        rawrButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                if (svc1 != null){
                    String x = svc1.flattenToString();
                    
                    Toast.makeText(Service_testActivity.this, x, Toast.LENGTH_LONG).show();

                } else {
                    Toast.makeText(Service_testActivity.this, "gahh", Toast.LENGTH_LONG).show();
                }
            }
        });
          
        Button sendButton = (Button) findViewById(R.id.Button04);
        sendButton.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
                
                try {
                    mService.send(Message.obtain(null, 3, mol, 0));
                } catch (RemoteException e) {
                }
            }
        });
        Button sendprButton = (Button) findViewById(R.id.Button05);
        sendprButton.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
                
                try {
                    mService.send(Message.obtain(null, 4, mol, 0));
                } catch (RemoteException e) {
                }
            }
        });
        Button prefsbutton = (Button) findViewById(R.id.prefs);
        prefsbutton.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
                Context myContext = null;
                try {
                    myContext = createPackageContext("edu.vanderbilt.isis.ammo",Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE);
                } catch (NameNotFoundException e) {e.printStackTrace();}
                testPrefs = myContext.getSharedPreferences("svcprefs", Context.MODE_WORLD_READABLE + Context.MODE_WORLD_WRITEABLE);
                testEd = testPrefs.edit();
                String valueFromPrefs = testPrefs.getString("test1asd", "svc -> ui FAILURE");
                TextView test1 = (TextView)findViewById(R.id.tvprefs);
                test1.setText(valueFromPrefs);
                testEd.putString("test1", "CHANGED IN UI");
                testEd.putString("test2", "testback");
                boolean edit_success = testEd.commit();
                /*notifythread = new Thread(new Runnable(){ 
                    public void run(){
                        synchronized (testPrefs){
                            testPrefs.notifyAll();
                        }
                    }
                    
                });*/

                valueFromPrefs = testPrefs.getString("test1", "FAILURE2");
                test1.setText(valueFromPrefs);
                if (edit_success){
                    Toast.makeText(Service_testActivity.this, "edit from UI SUCCESSFUL!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(Service_testActivity.this, "edit from UI FAILURE!", Toast.LENGTH_SHORT).show();
                }
                edit_success = testPrefs.contains("test2");
                String x = "LOLOL";
                if (edit_success)
                    x = "true";
                else
                    x = "false";
                Toast.makeText(Service_testActivity.this, x, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void bindAndStuff(){
        doBindService();
    }
     
    /** Messenger for communicating with service. */
    Messenger mService = null;
    /** Flag indicating whether we have called bind on the service. */
    boolean mIsBound;
    /** Some text view we are using to show state information. */
    TextView mCallbackText;

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 3:
                    mCallbackText = (TextView)findViewById(R.id.tv01);
                    mCallbackText.setText("Received from service: " + msg.arg1);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);
            mCallbackText = (TextView)findViewById(R.id.tv01);
            mCallbackText.setText("Attached.");

            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null,
                        1);
                msg.replyTo = mMessenger;
                mService.send(msg);

                // Give it some value as an example.
                msg = Message.obtain(null,
                        3, this.hashCode(), 0);
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }

            // As part of the sample, tell the user what happened.
            Toast.makeText(Service_testActivity.this, R.string.remote_service_connected,
                    Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mCallbackText = (TextView)findViewById(R.id.tv01);
            mCallbackText.setText("Disconnected.");

            // As part of the sample, tell the user what happened.
            Toast.makeText(Service_testActivity.this, R.string.remote_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        Intent intent2 = new Intent("android.intent.action.MAIN");
        intent2.setComponent(ComponentName.unflattenFromString("edu.vanderbilt.isis.ammo/edu.vanderbilt.isis.ammo.Service_ammo"));
        intent2.addCategory("android.intent.category.LAUNCHER");
        bindService(intent2, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        mCallbackText = (TextView)findViewById(R.id.tv01);
        mCallbackText.setText("Binding.");
    }

    void doUnbindService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null,
                            2);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            mCallbackText = (TextView)findViewById(R.id.tv01);
            mCallbackText.setText("Unbinding.");
        }
    }
}