package rocketradio.broadcast;

import android.content.Context;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    Context context;
    ConnectivityManager connManager;
    Boolean isPlaying = false;
    Boolean isConnected = false;
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
        try {
            mPlayer.setDataSource(url);
            mPlayer.prepareAsync(); // might take long! (for buffering, etc)
        } catch (IOException e) {
            e.printStackTrace();
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
        Log.i("Player", "Stopped!");
        isPlaying = false;
        Button playButton = (Button) findViewById(R.id.button);
        playButton.setBackgroundResource(R.drawable.playfilled);
    }

/*
    public boolean isConnectingToInternet(Context context){
        boolean result = false;
        if (context != null) {
            Log.d("Prova", "Sembra che vada");
            ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                NetworkInfo info = connectivity.getActiveNetworkInfo();
                if (info != null) {
                    result = info.isConnected();
                    Log.i("connection", Boolean.toString(result));
                } else {
                    result = false;
                }
            }
        } else {
            Log.d("isConnectingToInternet", "No dioporco");
        }
        Log.i("live", "Offline");
        isConnected = result;
        return result;
    }
*/

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        Log.d("ultimo", Boolean.toString(info.isConnected()));
        isConnected = info.isConnected();
        return info.isConnected();
    }

    public String milliSecondsToTimer(long milliseconds){
        String hourString = "";
        String minuteString = "";
        String secondString = "";
        String finalTimerString = "";

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
        Log.i("Connection", Boolean.toString(isConnected));
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
            if (isNetworkAvailable()){
                task.execute("http://149.202.34.23:8000/status-json.xsl");
                Log.i("Title content", titleString);
                textLabel.setText(titleString);
                textLabel.postDelayed(this, 2000);
            }else{
                textLabel.removeCallbacks(this);
                task.cancel(true);
                Toast.makeText(MainActivity.this, "Internet connection not available", Toast.LENGTH_SHORT).show();
            }
        }
    };



    private Runnable updateTime = new Runnable() {
        public void run() {
            int currentDuration;
            TextView timeLabel = (TextView) findViewById(R.id.currentTime);
            SeekBar seekBar = (SeekBar) findViewById(R.id.seekBar);
            if (mPlayer.isPlaying() && isConnected) {
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




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        updateTitle.run();


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
