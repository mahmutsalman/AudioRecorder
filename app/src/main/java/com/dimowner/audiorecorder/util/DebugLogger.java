/*
 * Copyright 2024 Mahmut Salman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.util;

import android.content.Context;
import android.os.Environment;
import timber.log.Timber;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Debug logger utility that writes debug information to a file.
 */
public class DebugLogger {
    private static final String DEBUG_FILENAME = "debug.txt";
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    
    private static File debugFile = null;
    
    /**
     * Initialize the debug logger with the specified file path.
     * @param filePath The path where debug.txt should be created
     */
    public static void init(String filePath) {
        try {
            debugFile = new File(filePath);
            if (debugFile.getParentFile() != null && !debugFile.getParentFile().exists()) {
                debugFile.getParentFile().mkdirs();
            }
            
            // Create the file if it doesn't exist
            if (!debugFile.exists()) {
                debugFile.createNewFile();
            }
            
            log("DebugLogger", "Debug logging initialized at: " + filePath);
        } catch (IOException e) {
            Timber.e(e, "Failed to initialize DebugLogger");
        }
    }
    
    /**
     * Log a debug message to the file.
     * @param tag The tag to identify the source
     * @param message The message to log
     */
    public static void log(String tag, String message) {
        if (debugFile == null) {
            // Fallback to default location if not initialized
            String defaultPath = Environment.getExternalStorageDirectory() + "/AudioRecorder/" + DEBUG_FILENAME;
            init(defaultPath);
        }
        
        String timestamp = TIME_FORMAT.format(new Date());
        String logEntry = String.format("[%s] %s: %s\n", timestamp, tag, message);
        
        try (FileWriter writer = new FileWriter(debugFile, true)) {
            writer.write(logEntry);
            writer.flush();
        } catch (IOException e) {
            Timber.e(e, "Failed to write debug log");
        }
        
        // Also log to Timber for development
        Timber.d("[%s] %s", tag, message);
    }
    
    /**
     * Log a debug message with additional details.
     * @param tag The tag to identify the source
     * @param message The main message
     * @param details Additional details to log
     */
    public static void log(String tag, String message, String details) {
        log(tag, message + " - " + details);
    }
    
    /**
     * Log timestamp creation event.
     * @param recordId The record ID
     * @param timestampId The created timestamp ID  
     * @param timeMillis The timestamp time in milliseconds
     * @param description The timestamp description
     */
    public static void logTimestampCreation(int recordId, long timestampId, long timeMillis, String description) {
        String message = String.format("Timestamp created: id=%d, recordId=%d, time=%dms, desc='%s'", 
            timestampId, recordId, timeMillis, description != null ? description : "");
        log("TimestampCreation", message);
    }
    
    /**
     * Log button click event.
     * @param buttonName The name of the button clicked
     */
    public static void logButtonClick(String buttonName) {
        log("ButtonClick", "Button clicked: " + buttonName);
    }
    
    /**
     * Log service action.
     * @param action The action being performed
     * @param details Additional details about the action
     */
    public static void logServiceAction(String action, String details) {
        log("ServiceAction", action, details);
    }
    
    /**
     * Get the current debug file path.
     * @return The path to the debug file, or null if not initialized
     */
    public static String getDebugFilePath() {
        return debugFile != null ? debugFile.getAbsolutePath() : null;
    }
    
    /**
     * Clear the debug log file.
     */
    public static void clearLog() {
        if (debugFile != null && debugFile.exists()) {
            try (FileWriter writer = new FileWriter(debugFile, false)) {
                writer.write("");
                writer.flush();
                log("DebugLogger", "Debug log cleared");
            } catch (IOException e) {
                Timber.e(e, "Failed to clear debug log");
            }
        }
    }
}