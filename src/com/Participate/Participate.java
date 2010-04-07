package com.Participate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class Participate extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

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
}