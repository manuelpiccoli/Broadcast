package rocketradio.broadcast;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.RemoteControlClient;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.media.MediaPlayer;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static rocketradio.broadcast.MediaNotificationManager.ACTION_NEXT;
import static rocketradio.broadcast.MediaNotificationManager.ACTION_PREV;

public class MainActivity extends AppCompatActivity {

    Context context;
    ConnectivityManager connManager;
    Boolean isPlaying = false;
    String titleString = "";
    MediaPlayer mPlayer;
    private GoogleApiClient client;
    private static final String TAG = "MyFirebaseMsgService";


    public void buttonPressed(View view) {
        if (isPlaying == false) {
            play();
            live();
        } else {
            stop();
        }
    }

    public void live() {

        String url = "http://149.202.34.23:8000/stream"; // your URL here
        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        if (networkAvailble()) {
            updateTitle.run();
            try {
                mPlayer.setDataSource(url);
                mPlayer.prepareAsync(); // might take long! (for buffering, etc)
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            stop();
            Log.i("live", "Non c'è audio");
        }

        //mp3 will be started after completion of preparing...
        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

            @Override
            public void onPrepared(MediaPlayer player) {
                player.start();
                updateTime.run();
            }

        });
    }

    public void play() {
        Log.i("Player", "Playing!");
        isPlaying = true;
        Button playButton = (Button) findViewById(R.id.button);
        playButton.setBackgroundResource(R.drawable.pausefilled);
    }

    public void stop() {
        mPlayer.stop();
        mPlayer.stop();
        Log.i("Player", "Stopped!");
        isPlaying = false;
        Button playButton = (Button) findViewById(R.id.button);
        playButton.setBackgroundResource(R.drawable.playfilled);
    }

    public boolean networkAvailble() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (null != activeNetwork) {
            if (activeNetwork.isConnected()) {
                Log.i("networkAvailable", Boolean.toString(activeNetwork.isConnected()));
                return true;
            } else {
                Log.i("networkAvailable", "Non c'è connessione a internet");
            }
        } else {
            Log.i("networkAvailable", "Non Credo");
            return false;
        }
        return false;
    }

    public String milliSecondsToTimer(long milliseconds){
        String hourString;
        String minuteString;
        String secondString;
        String finalTimerString;

        // Convert total duration into time
        int hours = (int)( milliseconds / (1000*60*60));
        int minutes = (int)(milliseconds % (1000*60*60)) / (1000*60);
        int seconds = (int) ((milliseconds % (1000*60*60)) % (1000*60) / 1000);

        // Prepending 0 to minutes if it is one digit
        if (hours < 10) { hourString = "0" + hours;
        } else { hourString = "" + hours;}

        // Prepending 0 to minutes if it is one digit
        if (minutes < 10) { minuteString = "0" + minutes;
        } else { minuteString = "" + minutes;}

        // Prepending 0 to seconds if it is one digit
        if (seconds < 10) { secondString = "0" + seconds;
        } else { secondString = "" + seconds;}

        finalTimerString = hourString + ":" + minuteString + ":" + secondString;

        // return timer string
        return finalTimerString;
    }


    private void updatePlayer(int currentDuration){
        TextView timeLabel = (TextView) findViewById(R.id.currentTime);
        Log.i("Time", milliSecondsToTimer(currentDuration));
        timeLabel.setText(milliSecondsToTimer((long) currentDuration));
    }



    public class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {

            String result = "";
            URL url;
            HttpURLConnection urlConnection = null;

            if (!isCancelled()) {
                try {
                    url = new URL(urls[0]);
                    urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = urlConnection.getInputStream();
                    InputStreamReader reader = new InputStreamReader(in);
                    int data = reader.read();
                    while (data != -1) {
                        char current = (char) data;
                        result += current;
                        data = reader.read();
                    }
                    return result;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            try {
                JSONObject jsonObject = new JSONObject(result);
                JSONObject icestatObject = jsonObject.getJSONObject("icestats");
                JSONObject sourceObject = icestatObject.getJSONObject("source");
                titleString = sourceObject.getString("title");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private Runnable updateTitle = new Runnable() {
        @Override
        public void run() {
            TextView textLabel = (TextView) findViewById(R.id.titleText);
            DownloadTask task = new DownloadTask();
            if (networkAvailble()){
                task.execute("http://149.202.34.23:8000/status-json.xsl");
                Log.i("Title content", titleString);
                textLabel.setText(titleString);
                textLabel.postDelayed(this, 2000);
            }else{
                textLabel.removeCallbacks(this);
                task.cancel(true);
                textLabel.setText("No Internet Connection");
                textLabel.postDelayed(this, 5000);
            }
        }
    };



    private Runnable updateTime = new Runnable() {
        public void run() {
            int currentDuration;
            TextView timeLabel = (TextView) findViewById(R.id.currentTime);
            SeekBar seekBar = (SeekBar) findViewById(R.id.seekBar);
            if (isPlaying && networkAvailble()) {
                currentDuration = mPlayer.getCurrentPosition();
                updatePlayer(currentDuration);
                seekBar.setProgress(1);
                timeLabel.postDelayed(this, 1000);
            }else {
                timeLabel.removeCallbacks(this);
                seekBar.setProgress(0);
            }
        }
    };



    private PendingIntent retreivePlaybackAction(int which) {
        Intent action;
        PendingIntent pendingIntent;
        final ComponentName serviceName = new ComponentName(this, YourPlaybackService.class);
        switch (which) {
            case 1:
                // Play and pause
                action = new Intent(ACTION_TOGGLE_PLAYBACK);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(this, 1, action, 0);
                return pendingIntent;
            case 2:
                // Skip tracks
                action = new Intent(ACTION_NEXT);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(this, 2, action, 0);
                return pendingIntent;
            case 3:
                // Previous tracks
                action = new Intent(ACTION_PREV);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(this, 3, action, 0);
                return pendingIntent;
            default:
                break;
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        MediaSession mediaSession;

        Notification notification = new Notification.Builder(context)
                // show controls on lockscreen even when user hides sensitive content.
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_default_art)
                // Add media control buttons that invoke intents in your media service
                .addAction(R.drawable.ic_prev, "Previous", prevPendingIntent) // #0
                .addAction(R.drawable.ic_pause, "Pause", pausePendingIntent)  // #1
                .addAction(R.drawable.ic_next, "Next", nextPendingIntent)     // #2
                // Apply the media style template
                .setStyle(new Notification.MediaStyle()
                        .setShowActionsInCompactView(1 /* #1: pause button */)
                        .setMediaSession(
                                myRemoteControlClient.getMediaSession().getSessionToken()))
                .setContentTitle("Wonderful music")
                .setContentText("My Awesome Band")
                .setLargeIcon(albumArtBitmap)
                .build();

        updateTitle.run();

        mMediaPlayer = new MediaPlayer();
        mManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        mSession = mManager.createSession("sample session"); //shows compile error

        final Bitmap artwork = Bitmap.createBitmap(R.drawable.logo);

        // Create a new MediaSession
        final MediaSession mediaSession;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = new MediaSession(this, "debug tag");
        }
        // Update the current metadata
        mediaSession.setMetadata(new MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Pink Floyd")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Dark Side of the Moon")
                .putString(MediaMetadata.METADATA_KEY_TITLE, "The Great Gig in the Sky")
                .build());
        // Indicate you're ready to receive media commands
        mediaSession.setActive(true);
        // Attach a new Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSession.Callback() {

            // Implement your callbacks

        });
        // Indicate you want to receive transport controls via your Callback
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // Create a new Notification
        final Notification noti = new Notification.Builder(this)
                // Hide the timestamp
                .setShowWhen(false)
                // Set the Notification style
                .setStyle(new Notification.MediaStyle()
                        // Attach our MediaSession token
                        .setMediaSession(mediaSession.getSessionToken())
                        // Show our playback controls in the compat view
                        .setShowActionsInCompactView(0, 1, 2))
                // Set the Notification color
                .setColor(0xFFDB4437)
                // Set the large and small icons
                .setLargeIcon(artwork)
                .setSmallIcon(R.drawable.logo)
                // Set Notification content information
                .setContentText("Pink Floyd")
                .setContentInfo("Dark Side of the Moon")
                .setContentTitle("The Great Gig in the Sky")
                // Add some playback controls
                .addAction(R.drawable.common_google_signin_btn_text_dark_pressed, "prev", retreivePlaybackAction(3))
                .addAction(R.drawable.pausefilled, "pause", retreivePlaybackAction(1))
                .addAction(R.drawable.common_google_signin_btn_icon_dark_focused, "next", retreivePlaybackAction(2))
                .build();

        // Do something with your TransportControls
        final MediaController.TransportControls controls = mediaSession.getController().getTransportControls();

        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, noti);



        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }




    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Main Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client, getIndexApiAction());
        client.disconnect();
    }
}
