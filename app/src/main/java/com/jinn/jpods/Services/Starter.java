package com.jinn.jpods.Services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jinn.jpods.Config;

/**
 * A simple starter class that starts the service when the device is booted, or after an update
 */
public class Starter extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        startPodsService(context);
    }

    public static final void startPodsService(Context context){
        context.startService(new Intent(context, PodsService.class));
    }

    public static final void restartPodsService(Context context){
        Config.serviceDisable = true;
        try{Thread.sleep(1200);}catch(Throwable t){}
        context.stopService(new Intent(context, PodsService.class));
        try{Thread.sleep(500);}catch(Throwable t){}
        Config.serviceDisable = false;
        context.startService(new Intent(context, PodsService.class));
    }
}
