package com.magnuswikhog.remotedb;

import android.annotation.SuppressLint;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.os.AsyncTask;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.magnuswikhog.remotedb.database.LocalDatabase;
import com.magnuswikhog.remotedb.database.LocalEntry;

import org.jetbrains.annotations.NonNls;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;


@SuppressWarnings("HardCodedStringLiteral")
public class RemoteDb {
	private static final String TAG = "RemoteDb";

	public static boolean DEBUG = false;



	public interface RemoteDbInterface{
	    void onSendToServerSuccess();
	    void onSendToServerFailure();
    }




    private String mStoreUrl;
	private String mPassword;
    private RemoteDbInterface mInterface;

    private Context mContext;
    private RequestQueue mRequestQueue;
    private boolean mDeleteLocalEntriesAfterRemoteStoreSuccess;
    private LocalDatabase mDb;

    private Entry mRequestParams;

    /**
     * Each HTTP request to the server will contain at most this many entries. Useful to prevent
     * trying to send a single giant JSON request with thousands of entries.
     */
    private int mSendToServerEntryChunkSize = 100;


    private MutableLiveData<Long> mServerEntryCount;




    public RemoteDb(Context context, String filename, String storeUrl, String password, RemoteDbInterface aInterface, boolean deleteLocalEntriesAfterRemoteStoreSuccess){
        mContext = context.getApplicationContext();
        mStoreUrl = storeUrl;
        mPassword = password;
        mInterface = aInterface;
        mRequestQueue = Volley.newRequestQueue(context);
        mDeleteLocalEntriesAfterRemoteStoreSuccess = deleteLocalEntriesAfterRemoteStoreSuccess;

        mRequestParams = new Entry();
        mDb = LocalDatabase.create(context, filename);
        mServerEntryCount = new MutableLiveData<Long>();
    }




    public void addEntry(final Entry entry){
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                mDb.getLocalEntryDao().insert(new LocalEntry(UUID.randomUUID().toString(), entry));

                if( DEBUG ) {
                    long allEntriesCount = mDb.getLocalEntryDao().countAllEntries();
                    long unstoredEntriesCount = mDb.getLocalEntryDao().countEntriesNotStoredOnServer();
                    Log.d(TAG, "addEntry()    allEntriesCount=" + allEntriesCount + "    unstoredEntriesCount=" + unstoredEntriesCount);
                }
            }
        });
    }




    public synchronized void sendToServer(final boolean sendRequestEvenIfEmpty) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    long unstoredEntriesCount = mDb.getLocalEntryDao().countEntriesNotStoredOnServer();
                    if( DEBUG && unstoredEntriesCount == 0){
                        long allEntriesCount = mDb.getLocalEntryDao().countAllEntries();
                        Log.d(TAG, "sendToServer()    Nothing to send to server!    allEntriesCount=" + allEntriesCount + "    unstoredEntriesCount=" + unstoredEntriesCount);
                    }

                    for(int i=((unstoredEntriesCount>0 || !sendRequestEvenIfEmpty) ? 0 : -1); i<unstoredEntriesCount; i+=mSendToServerEntryChunkSize) {
                        List<LocalEntry> unstoredEntries = mDb.getLocalEntryDao().getEntriesNotStoredOnServer(i, mSendToServerEntryChunkSize);

                        JSONArray entries = new JSONArray();
                        for (LocalEntry unstoredEntry : unstoredEntries) {
                            Entry entry = unstoredEntry.entry;
                            entry.put("_s", unstoredEntry.id);
                            entry.put("_u", unstoredEntry.uuid);
                            entries.put(entry);
                        }


                        JSONObject jsonRequest = new JSONObject();
                        jsonRequest.put("_pw", mPassword);
                        jsonRequest.put("_did", getUniqueDeviceIdentifier(mContext));
                        jsonRequest.put("_ent", entries);

                        JSONArray paramNames = mRequestParams.names();
                        if (paramNames != null) {
                            for (int n = 0; n < paramNames.length(); n++) {
                                String paramName = (String) paramNames.get(n);
                                jsonRequest.put(paramName, mRequestParams.get(paramName));
                            }
                        }


                        if (DEBUG) {
                            long allEntriesCount = mDb.getLocalEntryDao().countAllEntries();
                            Log.d(TAG, "sendToServer()    allEntriesCount=" + allEntriesCount + "    unstoredEntriesCount=" + unstoredEntriesCount + "   chunkSize="+unstoredEntries.size()+"   jsonRequest=" + String.valueOf(jsonRequest));
                        }


                        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                                Request.Method.POST,
                                mStoreUrl,
                                jsonRequest,
                                onRequestSuccess,
                                onRequestError);

                        mRequestQueue.add(jsonObjectRequest);
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }


    private Response.Listener<JSONObject> onRequestSuccess = new Response.Listener<JSONObject>() {
        @Override
        public void onResponse(JSONObject response) {
            if( !response.optString("status", "").equals("ok") ) {
                if( DEBUG )
                    Log.e(TAG, "Server error: "+response.toString());

                if( mInterface != null )
                    mInterface.onSendToServerFailure();
            }
            else {
                JSONArray storedUuids = null;
                try {
                    storedUuids = new JSONArray( response.optString("stored_uuids", "[]") );
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if( DEBUG )
                    Log.d(TAG, "onResponse()   storedUuids.length()="+storedUuids.length());

                markEntriesAsStored(storedUuids, mDeleteLocalEntriesAfterRemoteStoreSuccess);

                if( mInterface == null )
                    mInterface.onSendToServerSuccess();

                try {
                    Long serverEntryCount = response.getLong("total_count");
                    mServerEntryCount.setValue( serverEntryCount );
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    };


    private Response.ErrorListener onRequestError = new Response.ErrorListener() {
        @Override
        public void onErrorResponse(VolleyError error) {
            @NonNls String errorStr = "Volley error: ";
            errorStr += "HTTP code " + (error != null && error.networkResponse != null ? error.networkResponse.statusCode : "(none)") + "   ";
            errorStr += "Message: " + (error != null && error.getMessage() != null ? error.getMessage() : "(none)");

            Log.e(TAG, errorStr);
            if( error != null ) {
                for (StackTraceElement stackTraceElement : error.getStackTrace()) {
                    Log.e("RemoteDb/VolleyError", "at " + String.valueOf(stackTraceElement));
                }
            }

            if( mInterface != null )
                mInterface.onSendToServerFailure();
        }
    };




    public void markEntriesAsStored(final JSONArray uuids, final boolean deleteEntries){
        if( null != uuids ) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    if( DEBUG )
                        Log.d(TAG, "Marking "+uuids.length()+" local entries as stored");


                    /*
                    We're using a transaction and deleting one-by-one instead of making a single
                    DELETE FROM ... WHERE uuid IN (:uuids) because there is a limit to how many
                    variables can be in a single SQLite statement (default seems to be 999). If we
                    sent for example 1000 entries in a single request, we'd get an exception when
                    trying to execute a SQL statement with all 1000 stored UUID's.
                     */
                    mDb.beginTransaction();
                    try {
                        for (int i = 0; i < uuids.length(); i++) {
                            mDb.getLocalEntryDao().markAsStored(uuids.optString(i));
                        }
                        mDb.setTransactionSuccessful(); // Commmit
                    }
                    finally {
                        mDb.endTransaction();
                    }

                    if( DEBUG ) {
                        long allEntriesCount = mDb.getLocalEntryDao().countAllEntries();
                        long unstoredEntriesCount = mDb.getLocalEntryDao().countEntriesNotStoredOnServer();
                        Log.d(TAG, "markEntriesAsStored()    allEntriesCount=" + allEntriesCount + "    unstoredEntriesCount=" + unstoredEntriesCount);
                    }

                    if( deleteEntries )
                        removeStoredEntries();
                }
            });
        }
    }



    public void removeStoredEntries(){
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                if( DEBUG )
                    Log.d(TAG, "Removing all stored entries from local DB");

                mDb.getLocalEntryDao().deleteStored();

                if( DEBUG ) {
                    long allEntriesCount = mDb.getLocalEntryDao().countAllEntries();
                    long unstoredEntriesCount = mDb.getLocalEntryDao().countEntriesNotStoredOnServer();
                    Log.d(TAG, "addEntry()    allEntriesCount=" + allEntriesCount + "    unstoredEntriesCount=" + unstoredEntriesCount);
                }

            }
        });
    }



    public void clearLocalDatabase(){
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                if( DEBUG )
                    Log.d(TAG, "Clearing local DB");

                mDb.getLocalEntryDao().deleteAll();
            }
        });
    }



    @SuppressLint("HardwareIds")
    private String getUniqueDeviceIdentifier(Context context){
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }




    public LiveData<Long> getLocalEntriesCountLive(){
        return mDb.getLocalEntryDao().countAllEntriesLive();
    }

    public LiveData<Long> getUnstoredLocalEntriesCountLive(){
        return mDb.getLocalEntryDao().countEntriesNotStoredOnServerLive();
    }

    public MutableLiveData<Long> getServerEntriesCountLive(){
        return mServerEntryCount;
    }








    public void setDeleteLocalEntriesAfterRemoteStoreSuccess(boolean deleteLocalEntriesAfterRemoteStoreSuccess) {
        mDeleteLocalEntriesAfterRemoteStoreSuccess = deleteLocalEntriesAfterRemoteStoreSuccess;
    }

    public boolean getDeleteLocalEntriesAfterRemoteStoreSuccess() {
        return mDeleteLocalEntriesAfterRemoteStoreSuccess;
    }


    public Entry getRequestParams() {
        return mRequestParams;
    }

    public void setRequestParams(Entry requestParams) {
        mRequestParams = requestParams;
    }


    public int getSendToServerEntryChunk() {
        return mSendToServerEntryChunkSize;
    }


    /**
     * Each HTTP request to the server will contain at most this many entries. Useful to prevent
     * trying to send a single giant JSON request with thousands of entries.
     */
    public void setSendToServerEntryChunkSize(int entryCount) {
        this.mSendToServerEntryChunkSize = entryCount;
    }
}
