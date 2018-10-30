package com.magnuswikhog.remotedb.database;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface LocalEntryDao {
    @Query("SELECT * FROM remotedb_localentry WHERE storedOnServer=0 LIMIT :maxCount OFFSET :offset")
    List<LocalEntry> getEntriesNotStoredOnServer(long offset, long maxCount);

    @Query("SELECT COUNT(*) FROM remotedb_localentry")
    long countAllEntries();

    @Query("SELECT COUNT(*) FROM remotedb_localentry")
    LiveData<Long> countAllEntriesLive();


    @Query("SELECT COUNT(*) FROM remotedb_localentry WHERE storedOnServer=0")
    long countEntriesNotStoredOnServer();

    @Query("SELECT COUNT(*) FROM remotedb_localentry WHERE storedOnServer=0")
    LiveData<Long> countEntriesNotStoredOnServerLive();



    @Insert
    void insert(LocalEntry... entries);



    @Query("UPDATE remotedb_localentry SET storedOnServer=1 WHERE uuid=:uuid")
    void markAsStored(String uuid);




    @Delete
    void delete(LocalEntry entry);

    @Query("DELETE FROM remotedb_localentry")
    void deleteAll();

    @Query("DELETE FROM remotedb_localentry WHERE storedOnServer=1")
    void deleteStored();

    @Query("DELETE FROM remotedb_localentry WHERE uuid=:uuid")
    void deleteWithUuid(String uuid);
}