/*
 * TimestampNavigationReceiver for handling timestamp navigation broadcasts
 * This works at the system level to ensure broadcasts work when screen is locked
 */

package com.dimowner.audiorecorder.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.dimowner.audiorecorder.util.VolumeDebugLogger;

public class TimestampNavigationReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        VolumeDebugLogger.log(context, "TimestampNavigationReceiver.onReceive", 
            String.format("Received broadcast: %s", action != null ? action : "null"));
        
        if (action != null) {
            // Send broadcast to MainActivity with system-level delivery
            Intent mainActivityIntent = new Intent(action);
            mainActivityIntent.setPackage(context.getPackageName());
            mainActivityIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            
            VolumeDebugLogger.log(context, "TimestampNavigationReceiver.onReceive", 
                String.format("Forwarding to MainActivity: %s", action));
            context.sendBroadcast(mainActivityIntent);
        }
    }
}