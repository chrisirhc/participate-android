package sg.edu.nus.comp.cs3248.participate;

import java.net.URLEncoder;
import java.util.Random;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.telephony.TelephonyManager;
import android.util.Log;

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
    
    private String psessionId = "0";
    
    private Handler requestHandler;
    private HandlerThread requestHandlerThread;
    
    /**
     * The clientId for communicating with the MQTT server
     * This is set to the DeviceId during at the beginning.
     * IF the DeviceId is null (emulator? or no SIM?) then
     * set to a random number converted into a hex string.
     */
    private String clientId;

    public class LocalBinder extends Binder {
        Messager getService() {
            return Messager.this;
        }
    }

    IMqttClient mqttClient = null;
    HttpClient httpClient;
    public boolean isConnected = false;

    @Override
    public void onCreate() {
        super.onCreate();
        clientId = ((TelephonyManager) 
                getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        /*
         *  If for some reason it's too long for MQTT spec or
         *  it can't be obtained... (emulator??) 
         *  This may still cause collisions with other clients
         *  but just restart if that happens?
         */
        if(clientId == null || clientId.equals("000000000000000") || clientId.length() > 23) {
            clientId = Integer.toHexString(new Random().nextInt());
        }
        
        // since requests must be handled in order create a looper for it
        requestHandlerThread = new HandlerThread("requestHandler", Process.THREAD_PRIORITY_MORE_FAVORABLE) {
            @Override
            protected void onLooperPrepared() {
                super.onLooperPrepared();
                requestHandler = new Handler(this.getLooper());
                // Create the client
                requestHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // code from http://code.google.com/p/android/issues/detail?id=2690#c10
                        HttpParams params = new BasicHttpParams();
                        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
                        HttpProtocolParams.setContentCharset(params, "UTF-8");
                        HttpProtocolParams.setUseExpectContinue(params, true);
                        SchemeRegistry supportedSchemes = new SchemeRegistry();
                        supportedSchemes.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

                        httpClient = new DefaultHttpClient(
                                new ThreadSafeClientConnManager(params, supportedSchemes), params);
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
                        isConnected = connectToBroker();
                    }
                });
            }
        };
        requestHandlerThread.start();
    }

    /**
     * Connect to the broker
     * @return true if connected, otherwise false. This is helpful for 
     * attempting to reconnect.
     */
    private boolean connectToBroker() {
        try {
            Log.i("Connecting", "connecting to broker with clientId: " + clientId);
            mqttClient.connect(clientId, true, (short) 5);
            // check whether the keepalive works when the device is asleep
            Log.i("Connecting", "connected");
        } catch (Exception e) {
            // do nothing
            /*
            Toast.makeText(Messager.this, "Failed to connect"
                    + e.getCause().getMessage(), Toast.LENGTH_SHORT);
*/
            Log
                    .e("Connecting", "Failed to connect"
                            + e.getCause().getMessage());
            return false;
        }
        return true;
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
        JSONObject payloadObj, payloadProfile;
        try {
            payloadObj = new JSONObject(new String(payload));
        } catch (JSONException e) {
            // TODO handle this exception
            Log.e("participate", e.getMessage());
            return;
        }
        try {
            // Check that it's not myself. :)
            // if there is no profile, do nothing. I'm not sure what to do...
            if (payloadObj.has("profile") 
                    && !payloadObj.getJSONObject("profile").getString("id").equals(profileId)) {
                payloadProfile = payloadObj.getJSONObject("profile");
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
                bnd.putString("name", payloadProfile.getString("name"));
                bnd.putString("userId", payloadProfile.getString("userId"));
                bnd.putString("profileId", payloadProfile.getString("id"));
                if (topicArr[0].equals(ACTION_START)) {
                } else if (topicArr[0].equals(ACTION_STOP)) {
                    // TODO
                    bnd.putString("psessionId", payloadObj.getString("psessionId"));
                } else if (topicArr[0].equals(ACTION_PIND)) {
                    // TODO
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
        } catch (JSONException e) {
            Log.e("publish json", e.getMessage());
        }
    }

    /*
     * Called if the application loses it's connection to the message broker.
     */
    @Override
    public void connectionLost() throws Exception {
        Log.i("messager connection", "lost");
        // TODO Notify the UI thread that the connection has been lost?
        // Reconnect
        requestHandler.postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                if(mqttClient.isConnected())
                    return;
                boolean success = false;
                for (int i = 0; i < 5; i++) {
                    if(success = connectToBroker())
                        break;
                    else {
                        try {
                            // sleep a bit before retry? better to be exponential
                            Thread.sleep((long) Math.pow(3, i));
                        } catch (InterruptedException e) {
                        }
                    }
                }
                if (!success) {
                   // TODO Perform stuff to say we have given up! 
                } else {
                    subscribeClass();
                }
            }
        });
    }
    
    /**
     * Helper to subscribe to the current classId
     */
    private void subscribeClass() {
        String[] topics = { ACTION_STOP + "/" + classId , ACTION_START + "/" + classId };
        // TODO change qos
        int[] qoses = { 1, 1 };
        try {
            mqttClient.subscribe(topics, qoses);
        } catch (Exception e) {
            Log.e("register", e.getMessage() + "");
        }
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
        Bundle result = new Bundle();
        // Ensure that blanks are filled
        if (userId.equals(""))
            userId = "0";
        else {
            try {
                // Ensure it's safe to put in URL
                userId = URLEncoder.encode(userId, "UTF-8");
            } catch (Exception e) {
                Log.e("register encoding", e.getMessage());
            }
        }
        // Unsubscribe from the old class
        if (!classId.equals("0")) {
            String[] unsubtopics = { ACTION_START+ "/" + classId , ACTION_STOP + "/" + classId };
            try {
                mqttClient.unsubscribe(unsubtopics);
            } catch (Exception e) {
                Log.e("register", e.getMessage());
            }
        }

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

                HttpPost togetclass =
                    new HttpPost(
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
            Log.e("register user", "" + e.getMessage());
        }
        subscribeClass();
        Log.d("register", "Done");
        return result;
    }

    /**
     * Begin a participation session (psession) Sends Topic: start/[classId]
     * Body JSONObjects...
     * 
     */
    public void startPsession() {
        try {
            sendMsg(ACTION_START + "/" + classId, new JSONStringer().object()
            .key("profile").value(profile).endObject().toString());
        } catch (JSONException e) {
            Log.e("participate", "stop session: " + e.getMessage());
        }
        
        // TODO if within a threshold time then don't send out HTTP so that it won't be recorded.
        requestHandler.post(new Runnable() {
            @Override
            public void run() {
                // TODO Psession indicator mentioned above
                // Here we may need to do another sendMsg so that the others know
                // the current Psession? This is to support multiple Psessions
                try {
                    HttpPost toget =
                        new HttpPost("http://"+ BACKEND_MODEL_HOSTNAME +"/psession/start/"
                                + profileId);
                    HttpResponse resp = httpClient.execute(toget);
                    String respString = EntityUtils.toString(resp.getEntity());
                    Log.i("participate", "received from the server: " + respString);
                    // set the psessionId to be
                    psessionId = new JSONObject(respString)
                                .getJSONObject("psession").getString("id");
                } catch (Exception e) {
                    Log.e("participate", "http error" + e.getMessage());
                }
            }
        });
        // Do HTTP operation to get the psession id from the server in another thread
    }

    /**
     * Stop a participation session Sends stop/[classId]/[userId]/[psessionId]
     * to the broker
     * 
     * @return not sure what
     */
    public String stopPsession() {
        try {
            sendMsg(ACTION_STOP + "/" + classId, new JSONStringer().object()
            .key("psessionId").value(psessionId)
            .key("profile").value(profile).endObject().toString());
        } catch (JSONException e) {
            Log.e("stopPsession", e.getMessage());
        }
        requestHandler.post(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                // Send the record to the server to keep
                // This for the flower to show? and statistics
                try {
                    Log.i("participate", "attempting");
                    HttpPost topost =
                        new HttpPost("http://"+ BACKEND_MODEL_HOSTNAME +"/psession/stop/"
                                + profileId);
                    HttpResponse resp = httpClient.execute(topost);
                    Log.i("participate", EntityUtils.toString(resp.getEntity()));
                } catch (Exception e) {
                    Log.e("participate", e.getMessage());
                }
            }
        });
        // Finish up the session on the server on another thread
        return null;
    }

    /**
     * Sends via HTTP? rate/[classId]/[userId]/[psessionId]/[rating] Is this to
     * be sent or HTTPed? may not be time-sensitive so... nah
     * 
     */
    // TODO test whether this works
    public void ratePsession(final String psId) {
        // TODO if a rating is made earlier, just let the interface keep it until stop is sent
        requestHandler.post(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                // Send the record to the server to keep
                // This for the flower to show? and statistics
                try {
                    Log.i("participate", "attempting " + "http://"+ BACKEND_MODEL_HOSTNAME +"/rate/"
                                + profileId + "/" + psId);
                    HttpPost toget =
                        new HttpPost("http://"+ BACKEND_MODEL_HOSTNAME +"/rate/"
                                + profileId + "/" + psId);
                    HttpResponse resp = httpClient.execute(toget);
                    Log.i("participate", EntityUtils.toString(resp.getEntity()));
                } catch (Exception e) {
                    Log.e("participate", e.getMessage());
                }
            }
        });
        
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