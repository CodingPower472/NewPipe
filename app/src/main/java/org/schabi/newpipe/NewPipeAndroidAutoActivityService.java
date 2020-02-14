package org.schabi.newpipe;

import android.util.Log;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;
import com.google.android.gms.car.CarActivityHost;

public class NewPipeAndroidAutoActivityService extends CarActivityService {
    @Override
    public Class<? extends CarActivity> getCarActivity() {
        return NewPipeAndroidAutoActivity.class;
    }
}
