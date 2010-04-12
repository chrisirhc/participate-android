package sg.edu.nus.comp.cs3248.participate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/* TODO
 * - Should be  
 */

public class Participate extends Activity {
    public static final String ACTION_PSESSION = 
        "sg.edu.nus.comp.cs3248.particpate.ACTION_PSESSION";
    
    private TextView textbox;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsedit;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        textbox = (TextView) findViewById(R.id.textbox);
        prefs = getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE);
        prefsedit = prefs.edit();
        
        // put this in a thread?
        bindService(new Intent(Participate.this, 
                Messager.class), mConnection, Context.BIND_AUTO_CREATE);
    }
    
    @Override
    public void onDestroy() {
        unbindService(mConnection);
    }
    
    private Messager mBoundService;
    
    /** Registered information bundle */
    private Bundle regInfo;
    
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((Messager.LocalBinder)service).getService();
            
            // Attempt to register
            Participate.this.registerUser();
            // Should this be in the above function?
            Button togglePsession_button = (Button) findViewById(R.id.togglePsessionButton);
            togglePsession_button.setVisibility(View.VISIBLE);
            togglePsession_button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePsession();
                }
            });
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            Toast.makeText(Participate.this, "disconnected!!",
                    Toast.LENGTH_SHORT).show();
        }
    };

    /** Menu constants */
    private static final int MENU_REGISTER = 0;

    /** Nice menu */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_REGISTER, Menu.NONE, "Register");
        // getMenuInflater().inflate(R.menu.logviewer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_REGISTER:
            final EditText input = new EditText(this);
            input.setText(prefs.getString("userId", ""));
            // t, TextView.BufferType.EDITABLE
            new AlertDialog.Builder(Participate.this).setTitle("Registration")
                    .setMessage("Matric Number").setView(input)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    // save the preferences here
                                    // also attempt to logout (if logged in) and
                                    // login by checking whether this user is in
                                    // a class
                                    // perform necessary stuff.
                                    prefsedit.putString("userId", input.getText().toString());
                                    prefsedit.commit();
                                    registerUser();
                                }
                            }).setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    // Do nothing.
                                }
                            }).show();
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                togglePsession();
                return true;
        }
        return false;
    }

    @Override
    protected void onNewIntent (Intent intent) {
        Log.d("newintent", "intent received" + intent.getAction());
        if (intent.getAction().equals(Participate.ACTION_PSESSION)) {

            Log.d("bundle", "here");
            Bundle b = intent.getBundleExtra(Participate.ACTION_PSESSION);
            /*
             * TODO Possibly remove the coupling by moving all the constants
             * to Participate class.
             */
            if (b.getString("action").equals(Messager.ACTION_START)) {
                // TODO
                textbox.setText(b.getString("name") + " has started speaking");
            } else if (b.getString("action").equals(Messager.ACTION_STOP)) {
                // TODO
                textbox.setText(b.getString("name") + " has stopped speaking");
            } else if (b.getString("action").equals(Messager.ACTION_PIND)) {
                // TODO
            }
            Log.d("bundle", b.getString("action"));
        }
    }
    
    /**
     * Toggle whether starting or stopping
     */
    protected static boolean psessionOngoing = false;
    private void togglePsession() {
        if (!psessionOngoing)
            mBoundService.startPsession();
        else
            mBoundService.stopPsession();
        psessionOngoing = !psessionOngoing;
        

//        if(psessionOngoing)
//            ((Button) v).setText("Stop");
//        else
//            ((Button) v).setText("Start");
    }
    
    /**
     * Tell Messager to register the user
     * also updates the text fields and the registration information
     */
    protected void registerUser() {
        TextView userId_box = (TextView) findViewById(R.id.userId);
        TextView classTitle_box = (TextView) findViewById(R.id.classTitle);
        regInfo = mBoundService.registerUser(prefs.getString("userId", "0"));

        // TODO Friendly messages. Adjust later. 
        userId_box.setText("Hi there " + regInfo.getString("name") + "!");
        classTitle_box.setText("You are in " + regInfo.getString("classTitle") + " now.");
    }
}