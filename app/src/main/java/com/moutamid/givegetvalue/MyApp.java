package com.moutamid.givegetvalue;

import android.app.Application;

import com.fxn.stash.Stash;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Stash.init(this);
    }
}
