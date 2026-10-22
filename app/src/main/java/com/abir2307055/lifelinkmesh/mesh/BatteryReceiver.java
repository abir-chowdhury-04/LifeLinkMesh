package com.abir2307055.lifelinkmesh.mesh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Android calls onReceive when a broadcast we registered for arrives.
public class BatteryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        MeshHub hub = MeshHub.getInstance(context);
        if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            hub.setBatterySaver(true, "Battery is low");
        } else if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
            hub.setBatterySaver(false, "Battery is OK again");
        }
    }
}