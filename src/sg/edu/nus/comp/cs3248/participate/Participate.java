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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/* TODO
 * - Should be  
 */

public class Participate extends Activity {
    public static final String ACTION_PSESSION_STARTED = 
        "sg.edu.nus.comp.cs3248.particpate.ACTION_PSESSION_STARTED";
    
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
    
    private Messager mBoundService;
    
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((Messager.LocalBinder)service).getService();
            
            // Attempt to register
            mBoundService.registerUser(prefs.getString("userId", "0"));
            // mBoundService.registerUser();
            // Tell the user about this for our demo.
            Toast.makeText(Participate.this, "connected to Messager",
                    Toast.LENGTH_SHORT).show();
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
                                    mBoundService.registerUser(input.getText().toString());
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
        Log.i("pressed", Integer.toString(keyCode));
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                togglePsession();
                return true;
        }
        return false;
    }

    @Override
    protected void onNewIntent (Intent intent) {
        if (intent.getAction() == Participate.ACTION_PSESSION_STARTED) {
        textbox.setText(intent.getByteArrayExtra("topic").toString() 
                + intent.getByteArrayExtra("payload").toString());
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
    }
}