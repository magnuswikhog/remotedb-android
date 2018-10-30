package com.magnuswikhog.remotedb.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;
import android.support.annotation.WorkerThread;


@Database(entities = {LocalEntry.class}, exportSchema = false, version = 2)
@TypeConverters({Converters.class})
public abstract class LocalDatabase extends RoomDatabase {


    @WorkerThread
    public static synchronized LocalDatabase create(Context context, String filename) {
        return Room.databaseBuilder(
                context.getApplicationContext(),
                LocalDatabase.class,
                filename)
                .fallbackToDestructiveMigration()
                .build();
    }



    public abstract LocalEntryDao getLocalEntryDao();






}