package com.magnuswikhog.remotedb.database;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;

import com.magnuswikhog.remotedb.Entry;

import org.json.JSONObject;

@Entity(tableName = "remotedb_localentry")
public class LocalEntry {

    /**
     * Used as sequence number when sending to server
     */
    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Unique UUID for this entry, ensures that the same entry is never stored on the server twice.
     */
    public String uuid;

    /**
     * The client entry.
     */
    public Entry entry;

    /**
     * False by default, gets set to true after the entry has been stored on the server.
     */
    public boolean storedOnServer;



    public LocalEntry(){}

    @Ignore
    public LocalEntry(String uuid, Entry entry){
        this.uuid = uuid;
        this.entry = entry;
    }

}
