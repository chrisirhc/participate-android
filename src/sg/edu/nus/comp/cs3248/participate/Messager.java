package sg.edu.nus.comp.cs3248.participate;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.ibm.mqtt.IMqttClient;
import com.ibm.mqtt.MqttClient;
import com.ibm.mqtt.MqttSimpleCallback;

public class Messager extends Service implements MqttSimpleCallback {
    /** Our hostname for the backend server */
    private static final String BACKEND_HOSTNAME = "participate.vorce.net";

    public class LocalBinder extends Binder {
        Messager getService() {
            return Messager.this;
        }
    }

    IMqttClient mqttClient = null;

    @Override
    public void onCreate() {
        super.onCreate();
        // Create the client

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mqttClient =
                            MqttClient.createMqttClient("tcp://"
                                    + BACKEND_HOSTNAME + "@1883", null);
                    // register this client app has being able to receive
                    // messages
                    mqttClient.registerSimpleHandler(Messager.this);
                } catch (Exception e) {

                }
                // connect
                connectToBroker();
            }
        }).start();
    }

    private void connectToBroker() {
        Log.i("Connecting", "Attempt");
        try {

            Log.i("Connecting", "connecting to broker");
            mqttClient.connect(getString(R.string.app_name), false, (short) 5);
            // check whether the keepalive works when the device is asleep
            Log.i("Connecting", "connected");
        } catch (Exception e) {
            // do nothing
            Toast.makeText(Messager.this, "Failed to connect"
                    + e.getCause().getMessage(), Toast.LENGTH_SHORT);
            Log
                    .e("Connecting", "Failed to connect"
                            + e.getCause().getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // disconnect
        try {
            mqttClient.disconnect();
        } catch (Exception e) {
            // do nothing
        }
    }

    final private LocalBinder alocalBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent i) {
        return alocalBinder;
    }

    public void sessionStarted() {
        // startActivity((new Intent(Messager.this,
        // Participate.class)).putExtra(name, value));
    }

    public void publishArrived(String topicName, byte[] payload, int qos, boolean retained) {
        Bundle bnd = new Bundle();
        bnd.putString("topic", topicName);
        bnd.putString("payload", new String(payload));

        if (!Participate.psessionOngoing) {
            // Either do some processing here or throw it in.
            startActivity((new Intent(Messager.this, Participate.class))
                    .putExtras(bnd).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /*
     * Called if the application loses it's connection to the message broker.
     */
    @Override
    public void connectionLost() throws Exception {
        Toast.makeText(Messager.this, "Connection Lost", Toast.LENGTH_SHORT);

        // Reconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                connectToBroker();
            }
        }).start();
    }

    /**
     * List classes? Should this even be?!
     */

    /**
     * Pick a class
     */
    public String pickClass(String classId) {
        return null;
    }

    /**
     * Register the user on the server.<br/>
     * This method will also begin subscribing to the Pclass.
     * @param userId Not sure whether this is needed...
     * @return the classId
     */
    public String registerUser(String userId) {
        String[] topics = {"start/0"};
        int[] qoses = {0};
        try {
            mqttClient.subscribe(topics, qoses);
        } catch (Exception e) {
            
        }
        return null;
    }

    /**
     * Begin a participation session (psession)
     * Sends
     * Topic: start/[classId]
     * Body: [userId]/[psessionId] to the broker
     * @return the psessionId
     */
    public String startPsession() {
        sendMsg("start/0", "0");
        return null;
    }

    /**
     * Stop a participation session
     * Sends stop/[classId]/[userId]/[psessionId] to the broker
     * @return not sure what
     */
    public String stopPsession() {
        sendMsg("stop/0", "0");
        return null;
    }
    /**
     * Sends via HTTP? rate/[classId]/[userId]/[psessionId]/[rating]
     * Is this to be sent or HTTPed? may not be time-sensitive so... nah 
     * @return
     */
    public String ratePsession() {
        return null;
    }

    /**
     * Helper function for publishing
     * @param topic
     * @param body
     */
    public void sendMsg(String topic, String body) {
        sendMsg(topic, body, 0);
    }
    
    /**
     * @see #sendMsg(String, String)
     */
    public void sendMsg(String topic, String body, int qos) {
        try 
        {
            mqttClient.publish(topic, 
                               body.getBytes(),
                               0, 
                               false);
        } catch (Exception e) {
            
        }
    }
}