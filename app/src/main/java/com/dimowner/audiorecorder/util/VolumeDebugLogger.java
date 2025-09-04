/*
 * Debug logger specifically for volume button navigation events
 * Helps diagnose issues with volume buttons when screen is locked/unlocked
 */

package com.dimowner.audiorecorder.util;

import android.app.KeyguardManager;
import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VolumeDebugLogger {
    
    private static final List<String> debugLogs = new ArrayList<>();
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    
    /**
     * Add a debug log entry with automatic timestamp and screen state
     */
    public static void log(Context context, String event, String details) {
        String timestamp = timeFormat.format(new Date());
        String screenState = getScreenState(context);
        
        String logEntry = String.format("[%s] Screen: %s | %s | %s", 
            timestamp, screenState, event, details);
        
        synchronized (debugLogs) {
            debugLogs.add(logEntry);
            
            // Keep only last 100 entries to prevent memory issues
            if (debugLogs.size() > 100) {
                debugLogs.remove(0);
            }
        }
        
        // Also log to Android log for development
        android.util.Log.d("VolumeDebug", logEntry);
    }
    
    /**
     * Get current screen state
     */
    private static String getScreenState(Context context) {
        try {
            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
                return "LOCKED";
            } else {
                return "UNLOCKED";
            }
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    /**
     * Get all debug logs as a formatted string
     */
    public static String getAllLogs() {
        synchronized (debugLogs) {
            if (debugLogs.isEmpty()) {
                return "No debug logs yet. Press volume buttons to generate logs.";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("=== VOLUME BUTTON DEBUG LOGS ===\n\n");
            
            for (int i = debugLogs.size() - 1; i >= 0; i--) {
                sb.append(debugLogs.get(i)).append("\n");
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Clear all debug logs
     */
    public static void clearLogs() {
        synchronized (debugLogs) {
            debugLogs.clear();
        }
    }
    
    /**
     * Get number of log entries
     */
    public static int getLogCount() {
        synchronized (debugLogs) {
            return debugLogs.size();
        }
    }
}