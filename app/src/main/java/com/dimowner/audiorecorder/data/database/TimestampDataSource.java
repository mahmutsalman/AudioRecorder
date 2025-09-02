/*
 * Copyright 2025 Mahmut Salman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dimowner.audiorecorder.data.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.util.List;

import timber.log.Timber;

/**
 * Class to communicate with table: {@link SQLiteHelper#TABLE_TIMESTAMPS} in database.
 * @author Mahmut Salman
 */
public class TimestampDataSource extends DataSource<Timestamp> {

    private volatile static TimestampDataSource instance;

    public static TimestampDataSource getInstance(Context context) {
        if (instance == null) {
            synchronized (TimestampDataSource.class) {
                if (instance == null) {
                    instance = new TimestampDataSource(context);
                }
            }
        }
        return instance;
    }

    private TimestampDataSource(Context context) {
        super(context, SQLiteHelper.TABLE_TIMESTAMPS);
    }

    @Override
    public Timestamp recordToItem(Cursor cursor) {
        return new Timestamp(
                cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_TIMESTAMP_ID)),
                cursor.getInt(cursor.getColumnIndex(SQLiteHelper.COLUMN_RECORD_ID)),
                cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_TIME_MILLIS)),
                cursor.getString(cursor.getColumnIndex(SQLiteHelper.COLUMN_DESCRIPTION)),
                cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_CREATED_AT)),
                cursor.getLong(cursor.getColumnIndex(SQLiteHelper.COLUMN_UPDATED_AT))
        );
    }

    @Override
    public ContentValues itemToContentValues(Timestamp timestamp) {
        ContentValues cv = new ContentValues();
        if (timestamp.getId() != Timestamp.NO_ID) {
            cv.put(SQLiteHelper.COLUMN_TIMESTAMP_ID, timestamp.getId());
        }
        cv.put(SQLiteHelper.COLUMN_RECORD_ID, timestamp.getRecordId());
        cv.put(SQLiteHelper.COLUMN_TIME_MILLIS, timestamp.getTimeMillis());
        cv.put(SQLiteHelper.COLUMN_DESCRIPTION, timestamp.getDescription());
        cv.put(SQLiteHelper.COLUMN_CREATED_AT, timestamp.getCreatedAt());
        cv.put(SQLiteHelper.COLUMN_UPDATED_AT, timestamp.getUpdatedAt());
        return cv;
    }

    /**
     * Create a new timestamp for a recording.
     */
    public long createTimestamp(int recordId, long timeMillis, String description) {
        long currentTime = System.currentTimeMillis();
        Timestamp timestamp = new Timestamp(
                Timestamp.NO_ID,
                recordId,
                timeMillis,
                description != null ? description : "",
                currentTime,
                currentTime
        );
        
        Timestamp insertedTimestamp = insertItem(timestamp);
        if (insertedTimestamp != null) {
            long id = insertedTimestamp.getId();
            Timber.d("Created timestamp with id: %d for record: %d at time: %d", id, recordId, timeMillis);
            return id;
        } else {
            return -1;
        }
    }

    /**
     * Get all timestamps for a specific recording, ordered by time.
     */
    public List<Timestamp> getTimestampsForRecord(int recordId) {
        String where = SQLiteHelper.COLUMN_RECORD_ID + " = " + recordId;
        return getItems(where);
    }

    /**
     * Update timestamp description.
     */
    public boolean updateTimestampDescription(int timestampId, String newDescription) {
        Timestamp timestamp = getItem(timestampId);
        if (timestamp != null) {
            timestamp.setDescription(newDescription);
            int rowsAffected = updateItem(timestamp);
            Timber.d("Updated timestamp %d description. Rows affected: %d", timestampId, rowsAffected);
            return rowsAffected > 0;
        }
        return false;
    }

    /**
     * Delete a specific timestamp.
     */
    public boolean deleteTimestamp(int timestampId) {
        int rowsDeleted = deleteItem(timestampId);
        Timber.d("Deleted timestamp %d. Rows affected: %d", timestampId, rowsDeleted);
        return rowsDeleted > 0;
    }

    /**
     * Delete all timestamps for a specific recording.
     */
    public int deleteTimestampsForRecord(int recordId) {
        // Get all timestamps for this record first, then delete them one by one
        List<Timestamp> timestamps = getTimestampsForRecord(recordId);
        int deletedCount = 0;
        for (Timestamp timestamp : timestamps) {
            if (deleteItem(timestamp.getId()) > 0) {
                deletedCount++;
            }
        }
        Timber.d("Deleted %d timestamps for record: %d", deletedCount, recordId);
        return deletedCount;
    }

    /**
     * Get timestamp count for a specific recording.
     */
    public int getTimestampCount(int recordId) {
        List<Timestamp> timestamps = getTimestampsForRecord(recordId);
        return timestamps.size();
    }

    /**
     * Get a specific timestamp by ID.
     */
    public Timestamp getTimestamp(int timestampId) {
        return getItem(timestampId);
    }
}