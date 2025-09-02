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

import androidx.annotation.NonNull;

public class Timestamp {

    public static final int NO_ID = -1;

    private final int id;
    private final int recordId;
    private final long timeMillis;
    private String description;
    private final long createdAt;
    private long updatedAt;

    public Timestamp(int id, int recordId, long timeMillis, String description, long createdAt, long updatedAt) {
        this.id = id;
        this.recordId = recordId;
        this.timeMillis = timeMillis;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public int getRecordId() {
        return recordId;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    @NonNull
    @Override
    public String toString() {
        return "Timestamp{" +
                "id=" + id +
                ", recordId=" + recordId +
                ", timeMillis=" + timeMillis +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}