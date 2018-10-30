package com.magnuswikhog.remotedb.database;

import android.arch.persistence.room.TypeConverter;

import com.magnuswikhog.remotedb.Entry;

import org.json.JSONException;

public class Converters {
    @TypeConverter
    public static Entry fromString(String json) {
        try {
            return new Entry(json);
        } catch (JSONException e) {
            return new Entry();
        }
    }

    @TypeConverter
    public static String entryToString(Entry entry) {
        return String.valueOf(entry);
    }
}