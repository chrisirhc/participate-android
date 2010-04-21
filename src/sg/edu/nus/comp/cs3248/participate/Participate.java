package sg.edu.nus.comp.cs3248.participate;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SlidingDrawer;
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
    private ImageView ratingImg;

    private SlidingDrawer theDrawer;
    private TextView theDrawerText, theDrawerBack;
    private FrameLayout theDrawerFrame, theDrawerBackFrame;
    private ImageView theDrawerHandle;

    Vibrator systemVibrator;

    final Handler mHandler = new Handler();

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        systemVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        textbox = (TextView) findViewById(R.id.textbox);

        theDrawer = (SlidingDrawer) findViewById(R.id.theDrawer);
        theDrawerBack = (TextView) findViewById(R.id.theDrawerBack);
        theDrawerText = (TextView) findViewById(R.id.theDrawerText);
        theDrawerHandle = (ImageView) findViewById(R.id.theDrawerHandle);
        theDrawerFrame = (FrameLayout) findViewById(R.id.theDrawerContent);

        prefs =
                getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE);
        prefsedit = prefs.edit();
        ratingImg = (ImageView) findViewById(R.id.rateImg);
        
        // set listeners only after it is opened
        theDrawer.open();

        // what happens when you open/close the drawer
        theDrawer.setOnDrawerOpenListener(new SlidingDrawer.OnDrawerOpenListener() {
            @Override
            public void onDrawerOpened() {
                green();
            }
        });
        theDrawer.setOnDrawerCloseListener(new SlidingDrawer.OnDrawerCloseListener() {
            @Override
            public void onDrawerClosed() {
                red();
            }
        });

        // put this in a thread?
        new Thread(new Runnable() {

            @Override
            public void run() {
                bindService(new Intent(Participate.this, Messager.class),
                        mConnection, Context.BIND_AUTO_CREATE);
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        green();
        unbindService(mConnection);
        super.onDestroy();
    }

    private Messager mBoundService;

    /** Registered information bundle */
    private Bundle regInfo;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((Messager.LocalBinder) service).getService();

            // Attempt to register
            Participate.this.registerUser();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            Toast.makeText(Participate.this, "Disconnected!!",
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
            input.setInputType(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            input.setHint("U081234A");
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

                                    // No changes, don't do anything.
                                    if (prefs.getString("userId", "").equals(
                                            input.getText().toString()))
                                        return;

                                    // Commit the changes and register the user.
                                    prefsedit.putString("userId", input
                                            .getText().toString());
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
        switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_CENTER:
            // togglePsession();
            return true;
        }
        return false;
    }
    
    final static float STROKETHRESHOLD = 100;
    float lastY = 0;
    // To implement the rating system
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // TODO Auto-generated method stub
        switch(event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            lastY = event.getY();
            break;
        case MotionEvent.ACTION_MOVE:
            if (event.getY() - lastY > STROKETHRESHOLD) {
                rateCurrent(false);
                return true;
            } else if (event.getY() - lastY < -STROKETHRESHOLD) {
                rateCurrent(true);
                return true;
            }
            Log.d("participate", "event history size:" + Float.toString(event.getY()));
            break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d("newintent", "intent received" + intent.getAction());
        if (intent.getAction().equals(Participate.ACTION_PSESSION)) {

            Log.d("bundle", "here");
            Bundle b = intent.getBundleExtra(Participate.ACTION_PSESSION);
            /*
             * TODO Possibly remove the coupling by moving all the constants to
             * Participate class.
             */
            if (b.getString("action").equals(Messager.ACTION_START)) {
                // TODO
                // someone is speaking
                grey(b.getString("name"));
                rateCurrent(ratedCurrent, false);
                ratingImg.setVisibility(View.VISIBLE);
            } else if (b.getString("action").equals(Messager.ACTION_STOP)) {
                // someone has stopped speaking
                if (ratedCurrent) {
                    mBoundService.ratePsession(b.getString("psessionId"));
                    ratedCurrent = false;
                }
                ratingImg.setVisibility(View.INVISIBLE);
                green();
            }
            Log.d("participate", "bundle received:" + b.getString("action"));
        }
    }

    /**
     * Toggle whether starting or stopping
     */
    protected static boolean psessionOngoing = false;
    
    final static long QUICKVIBRATE = 100;

    /**
     * Used for trackball interaction
     * @deprecated
     * @see Participate#green()
     */
    private void togglePsession() {
        if (!psessionOngoing) {
            mBoundService.startPsession();
            // Remember, close it when the session has started
            
        } else {
            // Stop the session
            mBoundService.stopPsession();
            // Remember, open it when the session has stopped
        }
        psessionOngoing = !psessionOngoing;
        systemVibrator.vibrate(QUICKVIBRATE);
    }

    final int REGISTER_PROGRESS = 0;

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case REGISTER_PROGRESS: 
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle("Registering");
            dialog.setMessage("We're logging you in, please be patient.");
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    // if it's not connected, close the app
                    if(!mBoundService.isConnected)
                        Participate.this.finish();
                }
            });
            return dialog;
        }
        return null;
    }

    final Runnable updateUiText = new Runnable() {
        @Override
        public void run() {
            setTitle(getString(R.string.app_name) + " (" + regInfo.getString("name") 
                    + " @ " + regInfo.getString("classTitle") + ")");

            /* TODO get the people who are currently in the middle of a psession. 
             * It's unable to do that now (always green)
             */
            // Green //
            green();

            // TODO Friendly messages. Adjust later.
            // Following line is unnecessary
            dismissDialog(REGISTER_PROGRESS);
        }
    };
    
    /**
     * Tell Messager to register the user also updates the text fields and the
     * registration information
     */
    protected void registerUser() {
        showDialog(REGISTER_PROGRESS);
        // set the title in case of halfway cancellation?
        setTitle(R.string.app_name);

        // Changing the views.
        new Thread(new Runnable() {
            @Override
            public void run() {
                regInfo =
                        mBoundService.registerUser(prefs.getString("userId",
                                "0"));
                mHandler.post(updateUiText);
            }
        }).start();
    }
    
    private boolean ratedCurrent = false;
    private void rateCurrent(boolean rc) {
        rateCurrent(rc, ratedCurrent != rc);
    }
    private void rateCurrent(boolean rc, boolean notify) {
        if (notify)
            systemVibrator.vibrate(QUICKVIBRATE);
        ratedCurrent = rc;
        if(rc) {
            ratingImg.setImageResource(R.drawable.rated);
            if (notify)
                Toast.makeText(Participate.this, "Rated! :)", Toast.LENGTH_SHORT).show();
        } else {
            ratingImg.setImageResource(R.drawable.unrated);
            if (notify)
                Toast.makeText(Participate.this, "Unrated", Toast.LENGTH_SHORT).show();
            }
        // vibrate when this is changed
    }
    
    /**
     * Helper to write strings beside {@code theDrawer} on the bottom
     * Strings should pertain to the psession information
     * @param s Given string.
     */
    private void drawerMsg(String s) {
        if(theDrawer.isOpened()) {
            theDrawerText.setText(s);
            theDrawerBack.setText("");
        } else {
            theDrawerBack.setText(s);
            theDrawerText.setText("");
        }
    }

    /**
     * Ready for a new psession to occur
     * assume that {@code theDrawer} is opened
     */
    private void green() {
        // show a friendly participate
        drawerMsg("Swipe right to participate");
        // enable the drawer
        theDrawer.unlock();

        // set to green ball
        theDrawerHandle.setBackgroundColor(Color.GREEN);
        // handle ratings?

        if (psessionOngoing) {
            psessionOngoing = false;
            mBoundService.stopPsession();
        }
    }

    /**
     * Currently in a psession. Can signal red to stop.
     * assume that {@code theDrawer} is closed
     */
    private void red() {
        // currently in a session
        drawerMsg("Swipe left to indicate end of participation");
        // set to red ball
        theDrawerHandle.setBackgroundColor(Color.RED);

        psessionOngoing = true;
        mBoundService.startPsession();
    }

    /**
     * Someone is currently speaking
     * assume that {@code theDrawer} is opened
     */
    private void grey(String name) {
        drawerMsg(name + " is speaking..");
        theDrawerHandle.setBackgroundColor(Color.GRAY);
        // TODO check whether this works
        theDrawer.lock();
        // set the whole area to grey
    }
}