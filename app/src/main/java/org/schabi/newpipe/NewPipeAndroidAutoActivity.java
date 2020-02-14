package org.schabi.newpipe;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.apps.auto.sdk.CarActivity;

import org.schabi.newpipe.player.BasePlayer;

public class NewPipeAndroidAutoActivity extends CarActivity {

    private String getNewTextForPlayPause() {
        BasePlayer lp = LastBasePlayerHackyClass.lastPlayer;
        if (lp != null && !lp.isPlaying()) {
            return "Play";
        } else {
            return "Pause";
        }
    }

    private void updateSongInfo(TextView name, TextView artist) {
        BasePlayer lp = LastBasePlayerHackyClass.lastPlayer;
        if (lp == null || lp.getVideoTitle().equalsIgnoreCase("[unknown]")) return;
        name.setText(lp.getVideoTitle());
        artist.setText(lp.getUploaderName());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i("on_create_aa", "On create android auto");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_android_auto);
        getCarUiController().getStatusBarController().setTitle("");
        Button rewind = (Button) findViewById(R.id.rewind_aa);
        Button pause = (Button) findViewById(R.id.pause_aa);
        Button skip = (Button) findViewById(R.id.skip_aa);
        TextView currentSong = (TextView) findViewById(R.id.current_song_aa);
        TextView currentArtist = (TextView) findViewById(R.id.current_artist_aa);

        pause.setText(getNewTextForPlayPause());

        rewind.setOnClickListener(v -> {
            BasePlayer lp = LastBasePlayerHackyClass.lastPlayer;
            if (lp != null) lp.hackyRewindOne();
        });
        pause.setOnClickListener(v -> {
            BasePlayer lp = LastBasePlayerHackyClass.lastPlayer;
            if (lp != null) {
                if (lp.isPlaying()) {
                    lp.onPause();
                } else {
                    lp.onPlay();
                }
            }
            pause.setText(getNewTextForPlayPause());
        });
        skip.setOnClickListener(v -> {
            BasePlayer lp = LastBasePlayerHackyClass.lastPlayer;
            if (lp != null) lp.hackySkip();
        });
        /*Button button = (Button) findViewById(R.id.playlists_btn_aa);
        button.setOnClickListener(view -> {
            Intent intent = new Intent(this, NewPipePlaylistViewAndroidAutoActivity.class);
            //startActivity(intent);
            startCarActivity(intent);
        });*/
        Handler handler = new Handler();
        int checkSongInfoDelay = 1000;

        handler.postDelayed(new Runnable() {
            public void run() {
                updateSongInfo(currentSong, currentArtist);
                handler.postDelayed(this, checkSongInfoDelay);
            }
        }, checkSongInfoDelay);
    }

}
