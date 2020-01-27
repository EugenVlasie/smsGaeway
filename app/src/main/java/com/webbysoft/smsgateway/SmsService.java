package com.webbysoft.smsgateway;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static org.kaazing.gateway.client.impl.http.HttpRequest.ReadyState.SENT;

public class SmsService extends Service {
    private static final String TAG_FOREGROUND_SERVICE = "FOREGROUND_SERVICE";

    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";

    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";

    public static final String ACTION_PAUSE = "ACTION_PAUSE";

    public static final String ACTION_PLAY = "ACTION_PLAY";

    private String channelId;
    private ApiInterface apiInterface;
    private Socket socket;
    private SmsManager smsManager;
    private String userId;

    public SmsService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG_FOREGROUND_SERVICE, "My foreground service onCreate().");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            switch (action) {
                case ACTION_START_FOREGROUND_SERVICE:
                    userId = intent.getStringExtra("userId");
                    startForegroundService();
                    initializeSocket();
                    /*Toast.makeText(getApplicationContext(), "Foreground service is started.", Toast.LENGTH_LONG).show();*/
                    break;
                case ACTION_STOP_FOREGROUND_SERVICE:
                    stopForegroundService();
                    /*Toast.makeText(getApplicationContext(), "Foreground service is stopped.", Toast.LENGTH_LONG).show();*/
                    break;
                case ACTION_PLAY:
                    /*Toast.makeText(getApplicationContext(), "You click Play button.", Toast.LENGTH_LONG).show();*/
                    reconnectSocket();
                    break;
                case ACTION_PAUSE:
                    /*Toast.makeText(getApplicationContext(), "You click Pause button.", Toast.LENGTH_LONG).show();*/
                    disconnectSocket();
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }


    /* Used to build and start foreground service. */
    private void startForegroundService() {
        Log.d(TAG_FOREGROUND_SERVICE, "Start foreground service.");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel("smsService", "My Background Service");
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            channelId = "";
        }

        // Create notification default intent.
        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // Create notification builder.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);

        // Make notification show big text.
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("Sms Gateway");
        bigTextStyle.bigText("Porneste sau opreste serviciul de trimitere Sms.");
        // Set big text style.
        builder.setStyle(bigTextStyle);

        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.mipmap.ic_launcher);
        //Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.icon_music_32);
        //builder.setLargeIcon(largeIconBitmap);
        // Make the notification max priority.
        builder.setPriority(Notification.PRIORITY_MAX);
        // Make head-up notification.
        builder.setFullScreenIntent(pendingIntent, true);

        // Add Play button intent in notification.
        Intent playIntent = new Intent(this, SmsService.class);
        playIntent.setAction(ACTION_PLAY);
        PendingIntent pendingPlayIntent = PendingIntent.getService(this, 0, playIntent, 0);
        NotificationCompat.Action playAction = new NotificationCompat.Action(android.R.drawable.ic_media_play, "Porneste", pendingPlayIntent);
        builder.addAction(playAction);

        // Add Pause button intent in notification.
        Intent pauseIntent = new Intent(this, SmsService.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pendingPrevIntent = PendingIntent.getService(this, 0, pauseIntent, 0);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Opreste", pendingPrevIntent);
        builder.addAction(prevAction);

        // Build the notification.
        Notification notification = builder.build();

        // Start foreground service.
        startForeground(1, notification);
    }

    private void stopForegroundService() {
        disconnectSocket();
        Log.d(TAG_FOREGROUND_SERVICE, "Stop foreground service.");

        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName) {
        NotificationChannel chan = new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelId;
    }

    private void initializeSocket() {
        try {
            apiInterface = ApiClient.getRetrofitInstance().create(ApiInterface.class);
            socket = IO.socket("http://207.154.239.11:3000");
            socket.connect();
            socket.emit("pair", userId);
            socket.on(userId, onNewMessage);
            smsManager = SmsManager.getDefault();
        } catch (URISyntaxException ignored) {
        }
    }

    private Emitter.Listener onNewMessage = args -> {
        JSONObject data = (JSONObject) args[0];

        try {
            if (data != null) {
                Log.e("sendsms", "send sms id: " + data.get("_id"));
                sendLongSmsMessage(this, data.getString("message"), data.getString("_id"),
                        data.getString("to"));
            /*sendSMS(data.get("to").toString(), getASCIIFormattedMessage(data.get("message").toString()),
                    data.get("_id").toString());*/
            }
            socket.emit("pong", "pong");

        } catch (JSONException ignored) {
        }
    };

    private void sendLongSmsMessage(Context context, String message, String msgId, String phoneNumber) {

        // Receive when each part of the SMS has been sent
        final int[] nMsgParts = {0};
        final boolean[] messageSent = {true};
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // We need to make all the parts succeed before we say we have succeeded.
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        //resendSMS(message, msgId, phoneNumber);
                        /*Toast.makeText(getBaseContext(), "SMS sent",
                                Toast.LENGTH_SHORT).show();*/
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        //resendSMS(message, msgId, phoneNumber);
                        messageSent[0] = false;
                        /*Toast.makeText(getBaseContext(), "Generic failure",
                                Toast.LENGTH_SHORT).show();*/
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        //resendSMS(message, msgId, phoneNumber);
                        messageSent[0] = false;
                        /*Toast.makeText(getBaseContext(), "No service",
                                Toast.LENGTH_SHORT).show();*/
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        //resendSMS(message, msgId, phoneNumber);
                        messageSent[0] = false;
                        /*Toast.makeText(getBaseContext(), "Null PDU",
                                Toast.LENGTH_SHORT).show();*/
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                                /*resendSMS(((MainActivity)arg0).phoneNo,
                                        ((MainActivity)arg0).msg,((MainActivity)arg0).smsId);*/
                        messageSent[0] = false;
                        /*Toast.makeText(getBaseContext(), "Radio off",
                                Toast.LENGTH_SHORT).show();*/
                        break;
                }

                nMsgParts[0]--;
                if (nMsgParts[0] <= 0) {
                    // Stop us from getting any other broadcasts (may be for other messages)
                    Log.e("Error", "All message part responses received, unregistering message Id: " + msgId);
                    context.unregisterReceiver(this);
                    if (!messageSent[0])
                        resendSMS(message, msgId, phoneNumber);
                } else
                    Log.e("Messages Sent", String.valueOf(nMsgParts[0]));
            }
        };

        context.registerReceiver(broadcastReceiver, new IntentFilter(SENT + msgId));

        SmsManager smsManager = SmsManager.getDefault();

        ArrayList<String> messageParts = smsManager.divideMessage(message);
        ArrayList<PendingIntent> pendingIntents = new ArrayList<>(messageParts.size());
        nMsgParts[0] = messageParts.size();

        for (int i = 0; i < messageParts.size(); i++) {
            Intent sentIntent = new Intent(SENT + msgId);
            pendingIntents.add(PendingIntent.getBroadcast(context, 0, sentIntent, 0));
        }

        smsManager.sendMultipartTextMessage(phoneNumber, null, messageParts, pendingIntents, null);
    }

    private void resendSMS(String message, String msgId, String phoneNumber) {
        JsonObject resendSmsRequestBody = new JsonObject();
        resendSmsRequestBody.addProperty("phoneId", userId);
        resendSmsRequestBody.addProperty("message", message);
        resendSmsRequestBody.addProperty("to", phoneNumber);

        Call<Void> verifyEmailCall = apiInterface
                .resendSms(
                        "application/json",
                        "3700E4997507CFBD3C8FD0F94D8956C96B2603CA86BDA0CE010D5119EBD724A3",
                        resendSmsRequestBody
                );
        verifyEmailCall.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.code() == 200) {
                    response.code();
                } else {
                    response.code();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                t.toString();
            }
        });
    }

    private void disconnectSocket() {
        socket.off(userId, onNewMessage);
        socket.disconnect();
        socket.close();
    }

    private void reconnectSocket() {
        socket.off(userId, onNewMessage);
        socket.disconnect();
        socket.close();
        socket.open();
        socket.connect();
        socket.emit("pair", userId);
        socket.on(userId, onNewMessage);
    }
}
