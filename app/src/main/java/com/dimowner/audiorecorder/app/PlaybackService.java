/*
 * Copyright 2020 Dmytro Ponomarenko
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

package com.dimowner.audiorecorder.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import android.view.View;
import android.widget.RemoteViews;

import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.ColorMap;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.main.MainActivity;
import com.dimowner.audiorecorder.audio.player.PlayerContractNew;
import com.dimowner.audiorecorder.exception.AppException;
import com.dimowner.audiorecorder.util.ExtensionsKt;
import com.dimowner.audiorecorder.util.TimeUtils;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.data.RecordDataSource;
import com.dimowner.audiorecorder.data.database.Timestamp;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import androidx.core.app.NotificationManagerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import androidx.media.VolumeProviderCompat;
import android.content.ComponentName;
import com.dimowner.audiorecorder.util.VolumeDebugLogger;
import timber.log.Timber;

public class PlaybackService extends Service {

	private final static String CHANNEL_NAME = "Default";
	private final static String CHANNEL_ID = "com.dimowner.audiorecorder.NotificationId";

	public static final String ACTION_START_PLAYBACK_SERVICE = "ACTION_START_PLAYBACK_SERVICE";

	public static final String ACTION_PAUSE_PLAYBACK = "ACTION_PAUSE_PLAYBACK";

	public static final String ACTION_CLOSE = "ACTION_CLOSE";
	
	// Broadcast actions for timestamp navigation when screen is locked
	public static final String ACTION_NEXT_TIMESTAMP = "com.dimowner.audiorecorder.ACTION_NEXT_TIMESTAMP";
	public static final String ACTION_PREVIOUS_TIMESTAMP = "com.dimowner.audiorecorder.ACTION_PREVIOUS_TIMESTAMP";
	
	// Action for refreshing volume navigation preference
	public static final String ACTION_REFRESH_VOLUME_NAV = "ACTION_REFRESH_VOLUME_NAV";

	public static final String EXTRAS_KEY_RECORD_NAME = "record_name";

	private static final int NOTIF_ID = 101;
	private NotificationManagerCompat notificationManager;
	private PendingIntent contentPendingIntent;
	private RemoteViews remoteViewsSmall;
	private RemoteViews remoteViewsBig;
	private String recordName = "";
	private boolean started = false;

	private PlayerContractNew.Player audioPlayer;
	private PlayerContractNew.PlayerCallback playerCallback;
	private ColorMap colorMap;
	
	// Database access for timestamp navigation
	private LocalRepository localRepository;
	private RecordDataSource recordDataSource;
	
	// MediaSession for handling volume buttons when screen is locked
	private MediaSessionCompat mediaSession;
	private VolumeProviderCompat volumeProvider;

	public PlaybackService() {
	}

	public static void startServiceForeground(Context context, String name) {
		Intent intent = new Intent(context, PlaybackService.class);
		intent.setAction(PlaybackService.ACTION_START_PLAYBACK_SERVICE);
		intent.putExtra(PlaybackService.EXTRAS_KEY_RECORD_NAME, name);
		context.startService(intent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		audioPlayer = ARApplication.getInjector().provideAudioPlayer();
		colorMap = ARApplication.getInjector().provideColorMap(getApplicationContext());
		localRepository = ARApplication.getInjector().provideLocalRepository(getApplicationContext());
		recordDataSource = ARApplication.getInjector().provideRecordDataSource(getApplicationContext());

		if (playerCallback == null) {
			playerCallback = new PlayerContractNew.PlayerCallback() {
				@Override public void onError(@NotNull AppException throwable) {
					stopForegroundService();
				}
				@Override public void onStopPlay() {
					stopForegroundService();
				}
				@Override public void onSeek(long mills) { }
				@Override public void onPausePlay() {
					onPausePlayback();
				}
				@Override public void onPlayProgress(long mills) { }
				@Override public void onStartPlay() {
					onStartPlayback();
				}
			};
			this.audioPlayer.addPlayerCallback(playerCallback);
		}
		
		// Initialize MediaSession for handling volume buttons when screen is locked
		initializeMediaSession();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {

			String action = intent.getAction();
			if (action != null && !action.isEmpty()) {
				switch (action) {
					case ACTION_START_PLAYBACK_SERVICE:
						if (!started && intent.hasExtra(EXTRAS_KEY_RECORD_NAME)) {
							recordName = intent.getStringExtra(EXTRAS_KEY_RECORD_NAME);
							startForegroundService();
						}
						break;
					case ACTION_PAUSE_PLAYBACK:
						if (audioPlayer.isPlaying()) {
							audioPlayer.pause();
						} else if (audioPlayer.isPaused()) {
							audioPlayer.unpause();
						}
						break;
					case ACTION_CLOSE:
						audioPlayer.stop();
						stopForegroundService();
						break;
					case ACTION_REFRESH_VOLUME_NAV:
						// Refresh MediaSession state when volume navigation preference changes
						updateMediaSessionState();
						break;
				}
			}
		}
		return super.onStartCommand(intent, flags, startId);
	}

	@SuppressLint("WrongConstant")
	private void startForegroundService() {
		notificationManager = NotificationManagerCompat.from(this);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(CHANNEL_ID, CHANNEL_NAME);
		}

		boolean isNightMode = ExtensionsKt.isUsingNightModeResources(getApplicationContext());

		remoteViewsSmall = new RemoteViews(getPackageName(), R.layout.layout_play_notification_small);
		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_pause, getPendingSelfIntent(getApplicationContext(), ACTION_PAUSE_PLAYBACK));
		remoteViewsSmall.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(getApplicationContext(), ACTION_CLOSE));
		remoteViewsSmall.setTextViewText(R.id.txt_name, recordName);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			remoteViewsSmall.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));
			remoteViewsSmall.setInt(R.id.app_logo, "setVisibility", View.VISIBLE);
		} else {
			remoteViewsSmall.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(R.color.transparent));
			remoteViewsSmall.setInt(R.id.app_logo, "setVisibility", View.GONE);
			if (isNightMode) {
				remoteViewsSmall.setInt(R.id.txt_name, "setTextColor", this.getResources().getColor(R.color.text_primary_light));
				remoteViewsSmall.setInt(R.id.txt_app_label, "setTextColor", this.getResources().getColor(R.color.text_primary_light));
				remoteViewsSmall.setInt(R.id.txt_playback_progress, "setTextColor", this.getResources().getColor(R.color.text_secondary_light));
				remoteViewsSmall.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_stop);
				remoteViewsSmall.setInt(R.id.btn_pause, "setImageResource", R.drawable.ic_pause_light);
			} else {
				remoteViewsSmall.setInt(R.id.txt_name, "setTextColor", this.getResources().getColor(R.color.text_primary_dark));
				remoteViewsSmall.setInt(R.id.txt_app_label, "setTextColor", this.getResources().getColor(R.color.text_primary_dark));
				remoteViewsSmall.setInt(R.id.txt_playback_progress, "setTextColor", this.getResources().getColor(R.color.text_secondary_dark));
				remoteViewsSmall.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_stop_dark);
				remoteViewsSmall.setInt(R.id.btn_pause, "setImageResource", R.drawable.ic_pause_dark);
			}
		}

		remoteViewsBig = new RemoteViews(getPackageName(), R.layout.layout_play_notification_big);
		remoteViewsBig.setOnClickPendingIntent(R.id.btn_pause, getPendingSelfIntent(getApplicationContext(), ACTION_PAUSE_PLAYBACK));
		remoteViewsBig.setOnClickPendingIntent(R.id.btn_close, getPendingSelfIntent(getApplicationContext(), ACTION_CLOSE));
		remoteViewsBig.setTextViewText(R.id.txt_name, recordName);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			remoteViewsBig.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(colorMap.getPrimaryColorRes()));
			remoteViewsBig.setInt(R.id.app_logo, "setVisibility", View.VISIBLE);
		} else {
			remoteViewsBig.setInt(R.id.container, "setBackgroundColor", this.getResources().getColor(R.color.transparent));
			remoteViewsBig.setInt(R.id.app_logo, "setVisibility", View.GONE);
			if (isNightMode) {
				remoteViewsBig.setInt(R.id.txt_name, "setTextColor", this.getResources().getColor(R.color.text_primary_light));
				remoteViewsBig.setInt(R.id.txt_app_label, "setTextColor", this.getResources().getColor(R.color.text_primary_light));
				remoteViewsBig.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_stop);
				remoteViewsBig.setInt(R.id.btn_pause, "setImageResource", R.drawable.ic_pause_light);
			} else {
				remoteViewsBig.setInt(R.id.txt_name, "setTextColor", this.getResources().getColor(R.color.text_primary_dark));
				remoteViewsBig.setInt(R.id.txt_app_label, "setTextColor", this.getResources().getColor(R.color.text_primary_dark));
				remoteViewsBig.setInt(R.id.btn_close, "setImageResource", R.drawable.ic_stop_dark);
				remoteViewsBig.setInt(R.id.btn_pause, "setImageResource", R.drawable.ic_pause_dark);
			}
		}

		// Create notification default intent.
		Intent intent = new Intent(getApplicationContext(), MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
		contentPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, AppConstants.PENDING_INTENT_FLAGS);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			startForeground(NOTIF_ID, buildNotification());
		} else {
			startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
		}
		started = true;
	}

	private Notification buildNotification() {
		// Create notification builder.
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);

		builder.setWhen(System.currentTimeMillis());
		builder.setContentTitle(getResources().getString(R.string.app_name));
		builder.setSmallIcon(R.drawable.ic_play_circle);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			builder.setPriority(NotificationManagerCompat.IMPORTANCE_LOW);
		} else {
			builder.setPriority(Notification.PRIORITY_LOW);
		}
		// Make head-up notification.
		builder.setContentIntent(contentPendingIntent);
		builder.setCustomContentView(remoteViewsSmall);
		builder.setCustomBigContentView(remoteViewsBig);
		builder.setOngoing(true);
		builder.setOnlyAlertOnce(true);
		builder.setDefaults(0);
		builder.setSound(null);
		return builder.build();
	}

	public void stopForegroundService() {
		audioPlayer.removePlayerCallback(playerCallback);
		
		// Clean up MediaSession
		if (mediaSession != null) {
			mediaSession.setActive(false);
			mediaSession.release();
			mediaSession = null;
		}
		
		stopForeground(true);
		stopSelf();
		started = false;
	}

	@SuppressLint("WrongConstant")
	protected PendingIntent getPendingSelfIntent(Context context, String action) {
		Intent intent = new Intent(context, StopPlaybackReceiver.class);
		intent.setAction(action);
		return PendingIntent.getBroadcast(context, 10, intent, AppConstants.PENDING_INTENT_FLAGS);
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private void createNotificationChannel(String channelId, String channelName) {
		NotificationChannel channel = notificationManager.getNotificationChannel(channelId);
		if (channel == null) {
			NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
			chan.setLightColor(Color.BLUE);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
			chan.setSound(null,null);
			chan.enableLights(false);
			chan.enableVibration(false);

			notificationManager.createNotificationChannel(chan);
		} else {
			Timber.d("Channel already exists: %s", CHANNEL_ID);
		}
	}

	private void updateNotification(long mills) {
		if (started && remoteViewsSmall != null) {
			remoteViewsSmall.setTextViewText(R.id.txt_playback_progress,
					getResources().getString(R.string.playback, TimeUtils.formatTimeIntervalHourMinSec2(mills)));

		remoteViewsBig.setTextViewText(R.id.txt_playback_progress,
				getResources().getString(R.string.playback, TimeUtils.formatTimeIntervalHourMinSec2(mills)));

			notificationManager.notify(NOTIF_ID, buildNotification());
		}
	}

	public void onPausePlayback() {
		if (started && remoteViewsSmall != null) {
			boolean isNightMode = ExtensionsKt.isUsingNightModeResources(getApplicationContext());
			if (isNightMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
				if (remoteViewsBig != null) {
					remoteViewsBig.setImageViewResource(R.id.btn_pause, R.drawable.ic_play_light);
				}
				remoteViewsSmall.setImageViewResource(R.id.btn_pause, R.drawable.ic_play_light);
			} else {
				if (remoteViewsBig != null) {
					remoteViewsBig.setImageViewResource(R.id.btn_pause, R.drawable.ic_play_dark);
				}
				remoteViewsSmall.setImageViewResource(R.id.btn_pause, R.drawable.ic_play_dark);
			}
			notificationManager.notify(NOTIF_ID, buildNotification());
		}
	}

	public void onStartPlayback() {
		if (started && remoteViewsSmall != null) {
			boolean isNightMode = ExtensionsKt.isUsingNightModeResources(getApplicationContext());
			if (isNightMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
				if (remoteViewsBig != null) {
					remoteViewsBig.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause_light);
				}
				remoteViewsSmall.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause_light);
			} else {
				if (remoteViewsBig != null) {
					remoteViewsBig.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause_dark);
				}
				remoteViewsSmall.setImageViewResource(R.id.btn_pause, R.drawable.ic_pause_dark);
			}
			notificationManager.notify(NOTIF_ID, buildNotification());
		}
	}
	
	/**
	 * Initialize MediaSession for handling volume buttons when screen is locked
	 */
	private void initializeMediaSession() {
		VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.initializeMediaSession", "Creating MediaSession");
		
		// Create MediaSession
		mediaSession = new MediaSessionCompat(this, "PlaybackService");
		
		// Create VolumeProvider for handling volume button events
		volumeProvider = new VolumeProviderCompat(
			VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, // Allow relative volume changes
			100, // Max volume (not actually used for navigation)
			50   // Current volume (not actually used for navigation)
		) {
			@Override
			public void onAdjustVolume(int direction) {
				String directionStr = direction > 0 ? "UP" : (direction < 0 ? "DOWN" : "ZERO");
				VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.onAdjustVolume", 
					String.format("Direction: %s (%d) | VolumeNav: %s", directionStr, direction, isVolumeNavigationEnabled()));
				
				if (isVolumeNavigationEnabled()) {
					// Handle volume button as timestamp navigation
					if (direction > 0) {
						// Volume Up = Previous timestamp (reverse like typical audio apps)
						VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.onAdjustVolume", "Executing Previous Timestamp navigation");
						navigateToPreviousTimestamp();
						// Also send broadcast for UI updates when MainActivity is active
						sendTimestampNavigationBroadcast(ACTION_PREVIOUS_TIMESTAMP);
					} else if (direction < 0) {
						// Volume Down = Next timestamp
						VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.onAdjustVolume", "Executing Next Timestamp navigation");
						navigateToNextTimestamp();
						// Also send broadcast for UI updates when MainActivity is active
						sendTimestampNavigationBroadcast(ACTION_NEXT_TIMESTAMP);
					}
				} else {
					VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.onAdjustVolume", "Volume navigation disabled - no action taken");
				}
			}
		};
		
		// Set up MediaSession but don't activate yet (will activate when needed)
		mediaSession.setPlaybackToRemote(volumeProvider);
		
		// Set up MediaMetadata so system recognizes this as an active media session
		MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
		metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Audio Recording");
		metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Audio Recorder");
		metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1); // Unknown duration
		mediaSession.setMetadata(metadataBuilder.build());
		
		// Set PlaybackState to indicate we're actively playing
		PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
		stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | 
							   PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
		stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f);
		mediaSession.setPlaybackState(stateBuilder.build());
		
		// Connect MediaButtonReceiver to handle system-level media button events
		ComponentName mediaButtonReceiver = new ComponentName(this, VolumeButtonReceiver.class);
		Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
		mediaButtonIntent.setComponent(mediaButtonReceiver);
		PendingIntent mediaButtonPendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 
			PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		mediaSession.setMediaButtonReceiver(mediaButtonPendingIntent);
		
		VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.initializeMediaSession", 
			"MediaSession configured with metadata, playback state, and MediaButtonReceiver");
		
		// Update MediaSession state based on preference
		updateMediaSessionState();
	}
	
	/**
	 * Check if volume button navigation is enabled in preferences
	 */
	private boolean isVolumeNavigationEnabled() {
		// Get preferences from ARApplication
		return ARApplication.getInjector().providePrefs(getApplicationContext()).isVolumeButtonNavigationEnabled();
	}
	
	/**
	 * Update MediaSession active state based on preference
	 */
	private void updateMediaSessionState() {
		if (mediaSession != null) {
			boolean enabled = isVolumeNavigationEnabled();
			VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.updateMediaSessionState", 
				String.format("Setting MediaSession active: %s", enabled));
			mediaSession.setActive(enabled);
		}
	}
	
	/**
	 * Send broadcast to MainActivity for timestamp navigation
	 */
	private void sendTimestampNavigationBroadcast(String action) {
		VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.sendTimestampNavigationBroadcast", 
			String.format("Sending broadcast: %s", action));
		Intent intent = new Intent(action);
		sendBroadcast(intent);
		Timber.d("Sent timestamp navigation broadcast: " + action);
	}
	
	/**
	 * Public method to update MediaSession state when preferences change
	 * Can be called from MainActivity when preference is toggled
	 */
	public void refreshVolumeNavigationState() {
		updateMediaSessionState();
	}
	
	/**
	 * Direct timestamp navigation methods for when screen is locked
	 */
	
	/**
	 * Navigate to next timestamp (Volume Down when locked)
	 */
	private void navigateToNextTimestamp() {
		VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToNextTimestamp", "Starting next timestamp navigation");
		
		// Get current playback position
		long currentPosition = audioPlayer.getPauseTime();
		VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToNextTimestamp", 
			String.format("Current position: %d ms", currentPosition));
		
		// Get current active record
		final Record record = recordDataSource.getActiveRecord();
		if (record == null) {
			VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToNextTimestamp", "No active record found");
			return;
		}
		
		// Load timestamps for current record
		final List<Timestamp> timestamps = localRepository.getTimestampsForRecord(record.getId());
		if (timestamps == null || timestamps.isEmpty()) {
			VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToNextTimestamp", 
				String.format("No timestamps found for record %d", record.getId()));
			return;
		}
		
		// Find next timestamp after current position
		Timestamp nextTimestamp = null;
		for (Timestamp timestamp : timestamps) {
			if (timestamp.getTimeMillis() > currentPosition) {
				nextTimestamp = timestamp;
				break;
			}
		}
		
		if (nextTimestamp != null) {
			long seekPosition = nextTimestamp.getTimeMillis();
			VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToNextTimestamp", 
				String.format("Seeking to next timestamp at %d ms", seekPosition));
			audioPlayer.seek(seekPosition);
		} else {
			VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToNextTimestamp", 
				String.format("No more timestamps after current position %d ms", currentPosition));
		}
	}
	
	/**
	 * Navigate to previous timestamp (Volume Up when locked)
	 */
	private void navigateToPreviousTimestamp() {
		VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToPreviousTimestamp", "Starting previous timestamp navigation");
		
		// Get current playback position
		long currentPosition = audioPlayer.getPauseTime();
		VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToPreviousTimestamp", 
			String.format("Current position: %d ms", currentPosition));
		
		// Get current active record
		final Record record = recordDataSource.getActiveRecord();
		if (record == null) {
			VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToPreviousTimestamp", "No active record found");
			return;
		}
		
		// Load timestamps for current record
		final List<Timestamp> timestamps = localRepository.getTimestampsForRecord(record.getId());
		if (timestamps == null || timestamps.isEmpty()) {
			VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToPreviousTimestamp", 
				String.format("No timestamps found for record %d", record.getId()));
			return;
		}
		
		// Find previous timestamp before current position (search in reverse)
		Timestamp previousTimestamp = null;
		for (int i = timestamps.size() - 1; i >= 0; i--) {
			Timestamp timestamp = timestamps.get(i);
			if (timestamp.getTimeMillis() < currentPosition) {
				previousTimestamp = timestamp;
				break;
			}
		}
		
		if (previousTimestamp != null) {
			long seekPosition = previousTimestamp.getTimeMillis();
			VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToPreviousTimestamp", 
				String.format("Seeking to previous timestamp at %d ms", seekPosition));
			audioPlayer.seek(seekPosition);
		} else {
			VolumeDebugLogger.log(getApplicationContext(), "PlaybackService.navigateToPreviousTimestamp", 
				String.format("No timestamps before current position %d ms", currentPosition));
		}
	}

	public static class StopPlaybackReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			Intent stopIntent = new Intent(context, PlaybackService.class);
			stopIntent.setAction(intent.getAction());
			context.startService(stopIntent);
		}
	}
}
