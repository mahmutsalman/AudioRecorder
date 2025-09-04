/*
 * MediaButtonReceiver for handling volume buttons when screen is locked
 * This works at the system level to intercept media button events
 */

package com.dimowner.audiorecorder.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.util.VolumeDebugLogger;

public class VolumeButtonReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        VolumeDebugLogger.log(context, "VolumeButtonReceiver.onReceive", 
            String.format("Received intent: %s", intent.getAction()));
        
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                VolumeDebugLogger.log(context, "VolumeButtonReceiver.onReceive", 
                    String.format("Media button event: %d", event.getKeyCode()));
                
                // Check if volume navigation is enabled
                boolean isVolumeNavEnabled = ARApplication.getInjector()
                    .providePrefs(context)
                    .isVolumeButtonNavigationEnabled();
                
                if (isVolumeNavEnabled) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_VOLUME_UP:
                            VolumeDebugLogger.log(context, "VolumeButtonReceiver.onReceive", 
                                "Volume Up - sending previous timestamp broadcast");
                            context.sendBroadcast(new Intent(PlaybackService.ACTION_PREVIOUS_TIMESTAMP));
                            break;
                        case KeyEvent.KEYCODE_VOLUME_DOWN:
                            VolumeDebugLogger.log(context, "VolumeButtonReceiver.onReceive", 
                                "Volume Down - sending next timestamp broadcast");
                            context.sendBroadcast(new Intent(PlaybackService.ACTION_NEXT_TIMESTAMP));
                            break;
                    }
                } else {
                    VolumeDebugLogger.log(context, "VolumeButtonReceiver.onReceive", 
                        "Volume navigation disabled - ignoring media button");
                }
            }
        }
    }
}