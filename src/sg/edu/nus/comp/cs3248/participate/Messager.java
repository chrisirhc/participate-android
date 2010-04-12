package sg.edu.nus.comp.cs3248.participate;

import java.lang.reflect.Array;
import java.util.Arrays;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;

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
    private static final String BACKEND_MODEL_HOSTNAME = "participate-android.smart.joyent.com";

    /** Start Psession */
    public static final String ACTION_START = "start";
    /** Stop Psession */
    public static final String ACTION_STOP = "stop";
    /** Psession indicator */
    public static final String ACTION_PIND = "pind";
    /**
     * profileId for the current user. Might need to update this when userId is
     * changed
     */
    private String profileId = "0";

    private String classId = "0";

    public class LocalBinder extends Binder {
        Messager getService() {
            return Messager.this;
        }
    }

    IMqttClient mqttClient = null;
    HttpClient httpClient;

    @Override
    public void onCreate() {
        super.onCreate();
        // Create the client

        new Thread(new Runnable() {
            @Override
            public void run() {
                httpClient = new DefaultHttpClient();
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
        httpClient.getConnectionManager().shutdown();
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
        // This should be in the format: command/class/somethingelse
        Log.d("message arrived", payload.toString());
        String[] topicArr = topicName.split("\\/");
        JSONObject payloadObj;
        try {
            payloadObj = new JSONObject(new String(payload));
        } catch (JSONException e) {
            // TODO handle this exception
            Log.e("publish arrived json", e.getMessage());
            return;
        }
        // Process into a bundle
        
        Bundle bnd = new Bundle();
        /*
         * TODO Three types of actions.
         * - Start
         *   Here we need to tell the interface who has started talking
         * - Stop
         *   Here we need to tell the interface that the person has stopped talking
         * - Psession indicator
         *   Here we need to notify the interface on what is the psession of the
         *   that the user is rating for that interface element (button or whatever)
         *   Interface may hold a queue or whatever to keep this...
         */
        try {
            bnd.putString("name", payloadObj.getString("name"));
            bnd.putString("userId", payloadObj.getString("userId"));
            bnd.putString("profileId", payloadObj.getString("id"));
            if (topicArr[0].equals(ACTION_START)) {
            } else if (topicArr[0].equals(ACTION_STOP)) {
                // TODO
            } else if (topicArr[0].equals(ACTION_PIND)) {
                // TODO
            }
        } catch (JSONException e) {
            Log.e("publish json", e.getMessage());
        }
        
        bnd.putString("action", topicArr[0]);
        // bnd.putString("payload", new String(payload));

        // TODO This is a bit lame. Fix later.
        if (!Participate.psessionOngoing) {
            // Either do some processing here or throw it in.
            startActivity((new Intent(Participate.ACTION_PSESSION, null, 
                    Messager.this, Participate.class))
                    .putExtra(Participate.ACTION_PSESSION, bnd)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /*
     * Called if the application loses it's connection to the message broker.
     */
    @Override
    public void connectionLost() throws Exception {
        Toast.makeText(Messager.this, "Connection Lost", Toast.LENGTH_SHORT);
        // TODO Check whether this is the correct way to do this?
        // Reconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                connectToBroker();
            }
        }).start();
    }

    /**
     * TODO List classes? Should this even be inside?!
     */

    /**
     * TODO Pick a class
     */
    public String pickClass(String classId) {
        return null;
    }
    
    /** Store the JSONObject of the current class */
    private JSONObject pclass;
    /** Store the JSONObject of the current profile */
    private JSONObject profile;

    /**
     * Register the user on the server.<br/>
     * This method will also begin subscribing to the Pclass. The method will
     * also update the profileId variable
     * 
     * @param userId
     *            Not sure whether this is needed...
     * @return the classId
     */
    public Bundle registerUser(String userId) {
        // Unsubscribe from the old class
        if (!classId.equals("0")) {
            String[] unsubtopics = { "start/" + classId , "stop/" + classId };
            try {
                mqttClient.unsubscribe(unsubtopics);
            } catch (Exception e) {
                Log.e("register", e.getMessage());
            }
        }

        Bundle result = new Bundle();
        // Get the class
        HttpGet toget =
                new HttpGet("http://"+ BACKEND_MODEL_HOSTNAME +"/getProfileId/"
                        + userId);
        HttpResponse resp;
        JSONObject respObj;
        try {
            resp = httpClient.execute(toget);
            respObj = new JSONObject(EntityUtils.toString(resp.getEntity()));
            if (respObj.getBoolean("ok")) {
                // result.putString("profileId", respObj.getString("profileId"));
                profile = respObj.getJSONObject("profile");
                result.putString("name", profile.getString("name"));
                profileId = profile.getString("id");

                HttpGet togetclass =
                        new HttpGet(
                                "http://"+ BACKEND_MODEL_HOSTNAME +"/register/"
                                        + profileId);

                resp = httpClient.execute(togetclass);
                respObj =
                        new JSONObject(EntityUtils.toString(resp.getEntity()));
                if (respObj.getBoolean("ok")) {
                    pclass = respObj.getJSONObject("pclass");
                    classId = pclass.getString("id");
                    result.putString("classTitle", pclass.getString("classTitle"));
                }
                // TODO failure not handled here as well
            } else {
                // TODO Some unknown failure? What to do here
            }
        } catch (Exception e) {
            Log.e("register user", e.getMessage());
        }
        String[] topics = { "start/" + classId , "stop/" + classId };
        int[] qoses = { 0, 0 };
        try {
            mqttClient.subscribe(topics, qoses);
        } catch (Exception e) {
            Log.e("register", e.getMessage());
        }
        Log.d("register", "Done");

        return result;
    }

    /**
     * Begin a participation session (psession) Sends Topic: start/[classId]
     * Body JSONObjects...
     * 
     * @return the psessionId
     */
    public String startPsession() {
        sendMsg("start/" + classId, profile.toString());
        
//        new JSONStringer()
//        .object()
//            .key("profile").value(profile)
//        .endObject()
        new Thread(new Runnable() {
            @Override
            public void run() {
                // TODO Psession indicator mentioned above
                // Here we may need to do another sendMsg so that the others know
                // the current Psession? This is to support multiple Psessions
            }
        }).start();
        // Do HTTP operation to get the psession id from the server in another thread
        return null;
    }

    /**
     * Stop a participation session Sends stop/[classId]/[userId]/[psessionId]
     * to the broker
     * 
     * @return not sure what
     */
    public String stopPsession() {
        sendMsg(ACTION_STOP + "/" + classId, profileId);
        new Thread(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                // Send the record to the server to keep
                // This for the flower to show? and statistics
            }
        }).start();
        // Finish up the session on the server on another thread
        return null;
    }

    /**
     * Sends via HTTP? rate/[classId]/[userId]/[psessionId]/[rating] Is this to
     * be sent or HTTPed? may not be time-sensitive so... nah
     * 
     * @return
     */
    public String ratePsession() {
        return null;
    }

    /**
     * Helper function for publishing
     * 
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
        try {
            mqttClient.publish(topic, body.getBytes(), 0, false);
        } catch (Exception e) {

        }
        Log.i("sent message", body);
    }
}