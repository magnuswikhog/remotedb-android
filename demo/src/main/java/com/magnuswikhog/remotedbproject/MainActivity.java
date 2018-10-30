package com.magnuswikhog.remotedbproject;

import android.arch.lifecycle.Observer;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.magnuswikhog.remotedb.Entry;
import com.magnuswikhog.remotedb.RemoteDb;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String REMOTE_DB_PHP_URL = "http://example.com/remotedb-php/store.php";


    RemoteDb mDemoRemoteDb;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mDemoRemoteDb = new RemoteDb(
                getApplicationContext(),
                "demodatabase.db",
                REMOTE_DB_PHP_URL,
                "demopassword",
                mRemoteDbInterface,
                false
        );
        mDemoRemoteDb.DEBUG = true;


        mDemoRemoteDb.setRequestParams(mDemoRemoteDb.getRequestParams().put("static", "static_"+System.currentTimeMillis()));



        ((CheckBox) findViewById(R.id.deleteStoredLocalEntriesCheckBox)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mDemoRemoteDb.setDeleteLocalEntriesAfterRemoteStoreSuccess( isChecked );
            }
        });


        findViewById(R.id.insertRandomEntryButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDemoRemoteDb.addEntry(new Entry()
                        .put("t", System.currentTimeMillis())
                        .put("m", "Random message: "+ UUID.randomUUID().toString()));
            }
        });

        findViewById(R.id.insertOneHundredEntriesButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for(int i=0; i<100; i++) {
                    mDemoRemoteDb.addEntry(new Entry()
                            .put("t", System.currentTimeMillis())
                            .put("m", "Entry #"+i));
                }
            }
        });


        findViewById(R.id.insertEmptyEntryButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDemoRemoteDb.addEntry(new Entry()
                        .put("t", System.currentTimeMillis()));
            }
        });


        findViewById(R.id.sendToServerInOneThousandEntryChunksButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDemoRemoteDb.setSendToServerEntryChunkSize(1000);
                mDemoRemoteDb.sendToServer(true);
            }
        });

        findViewById(R.id.sendToServerInOneHundredEntryChunksButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDemoRemoteDb.setSendToServerEntryChunkSize(100);
                mDemoRemoteDb.sendToServer(true);
            }
        });

        findViewById(R.id.sendToServerInTenEntryChunksButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDemoRemoteDb.setSendToServerEntryChunkSize(10);
                mDemoRemoteDb.sendToServer(true);
            }
        });

        findViewById(R.id.clearLocalDbButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDemoRemoteDb.clearLocalDatabase();
            }
        });



        /**
         * Get observable live data from RemoteDb
         */

        mDemoRemoteDb.getLocalEntriesCountLive().observe(this, new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long entryCount) {
                ((TextView) findViewById(R.id.allEntriesCountText)).setText("Total local entries: "+entryCount);
            }
        });

        mDemoRemoteDb.getUnstoredLocalEntriesCountLive().observe(this, new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long entryCount) {
                ((TextView) findViewById(R.id.unstoredEntriesCountText)).setText("Unstored local entries: "+entryCount);
            }
        });

        mDemoRemoteDb.getServerEntriesCountLive().observe(this, new Observer<Long>() {
            @Override
            public void onChanged(@Nullable Long entryCount) {
                ((TextView) findViewById(R.id.serverEntriesCountText)).setText("Total server entries: "+entryCount);
            }
        });


    }


    private RemoteDb.RemoteDbInterface mRemoteDbInterface = new RemoteDb.RemoteDbInterface(){

        @Override
        public void onSendToServerSuccess() {
            Log.i("MainActivity", "onSendToServerSuccess()");
        }

        @Override
        public void onSendToServerFailure() {
            Log.i("MainActivity", "onSendToServerFailure()");
        }

    };



}
