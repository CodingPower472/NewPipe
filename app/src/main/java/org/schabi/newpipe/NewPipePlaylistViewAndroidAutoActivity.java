package org.schabi.newpipe;

import android.os.Bundle;
import android.util.Log;

import com.google.android.apps.auto.sdk.CarActivity;

public class NewPipePlaylistViewAndroidAutoActivity extends CarActivity {

    @Override
    public void onCreate(Bundle bundle) {
        Log.i("transit", "Transitioned to playlist view activity in Android Auto");
        super.onCreate(bundle);
        setContentView(R.layout.activity_new_pipe_playlist_view_android_auto);
        getCarUiController().getStatusBarController().setTitle("playlists");
    }

}
