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


    /**
     * The callback interface used by the RemoteDb class.
     */
	public interface RemoteDbInterface{
        /** Called when the server has acknowledged that it has successfully stored the sent entries. */
        void onSendToServerSuccess();

        /** Called if there was a problem when sending or storing the entrieson the server. */
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


    /**
     * Creates a new RemoteDb instance.
     *
     * @param context       The context to use for this instance.
     * @param filename      The filename of the local database. If you are using separate instances of this class to store different types of entries, make sure you use different names for the databases of each instance.
     * @param storeUrl      The URL for the RemoteDb server script.
     * @param password      The password to send to the RemoteDb server script.
     * @param aInterface    An optional callback interface.
     * @param deleteLocalEntriesAfterRemoteStoreSuccess     If true, entries will be removed from the local database when they have been successfully stored on the server. If false, the local entries will be kept in the local database even after they have been stored on the server.
     */
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


    /**
     * Adds a new entry to the local database. You must first add any data that you want to store to
     * the entry, before calling this method. After calling this method, the entry is stored in the
     * local database on the users device, but is not sent to the remote server until you call
     * {@link com.magnuswikhog.remotedb.RemoteDb#sendToServer(boolean)}.
     * @param entry The entry you want to store in the local database.
     */
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


    /**
     * Sends all entries in the database that have not yet been stored on the server, to the server.
     * This is done asynchronously, and if you have set a {@link RemoteDbInterface}, the appropriate
     * callback method will be invoked when the server responds.
     * @param sendRequestEvenIfEmpty    It true, a HTTP request will be sent to the server even if
     *                                  there are no entries to send. Useful for retrieving the number
     *                                  of entries on the server in {@link RemoteDb#getServerEntriesCountLive()}.
     */
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


    /**
     * Marks the entries with the supplied UUID's in the local database as "stored on the server".
     * @param uuids A list of UUID's, one for each entry to mark as stored.
     * @param deleteEntries If true, all entries that are marked as stored (not only the ones supplied
     *                      in the uuids parameter) will be deleted from the local database.
     */
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


    /**
     * Removes all entries that are marked as "stored on the server" from the local database.
     */
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


    /**
     * Removes all entries from the local database, regardless of if they have been stored on the server or not.
     */
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


    /**
     * Returns a LiveData object representing the total number of entries in the local database.
     * @return A LiveData object which can be used to observe this property.
     */
    public LiveData<Long> getLocalEntriesCountLive(){
        return mDb.getLocalEntryDao().countAllEntriesLive();
    }


    /**
     * Returns a LiveData object representing the number of entries in the local database which have
     * not yet been stored on the server.
     * @return A LiveData object which can be used to observe this property.
     */
    public LiveData<Long> getUnstoredLocalEntriesCountLive(){
        return mDb.getLocalEntryDao().countEntriesNotStoredOnServerLive();
    }


    /**
     * Returns a LiveData object representing the number of entries in the remote server database.
     * Note that this number is only updated when you call {@link RemoteDb#sendToServer(boolean)}.
     * @return A LiveData object which can be used to observe this property.
     */
    public MutableLiveData<Long> getServerEntriesCountLive(){
        return mServerEntryCount;
    }


    /**
     * @param deleteLocalEntriesAfterRemoteStoreSuccess If true, all entries that have been stored on the
     *                                                  server will be removed the next time a call to
     *                                                  {@link RemoteDb#sendToServer(boolean)} is
     *                                                  successful.
     */
    public void setDeleteLocalEntriesAfterRemoteStoreSuccess(boolean deleteLocalEntriesAfterRemoteStoreSuccess) {
        mDeleteLocalEntriesAfterRemoteStoreSuccess = deleteLocalEntriesAfterRemoteStoreSuccess;
    }

    public boolean getDeleteLocalEntriesAfterRemoteStoreSuccess() {
        return mDeleteLocalEntriesAfterRemoteStoreSuccess;
    }


    public Entry getRequestParams() {
        return mRequestParams;
    }


    /**
     * Sets an entry that will be supplied with each request to the remote server. This can be used to
     * supply data that should be stored along with each sent entry, but which is common for all entries.
     * It's much more efficient to provide such static data this way than to supply it within each entry,
     * since it will result in a smaller and faster HTTP request.
     * @param requestParams An entry containing data that will be supplied with each HTTP request to the
     *                      server.
     */
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
