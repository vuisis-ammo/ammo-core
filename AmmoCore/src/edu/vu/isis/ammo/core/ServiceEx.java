package edu.vu.isis.ammo.core;

import android.app.Service;
import android.content.Context;

public abstract class ServiceEx extends Service {
    public Context getAppContext(){
        return (ApplicationEx) getApplicationContext();
    }
}