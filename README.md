# Introduction 

This library lets you quickly and easily send data from your Android app to your server and store it in a database.

> This is the Android part of the RemoteDb project. The PHP backend part can be found at https://github.com/magnuswikhog/remotedb-php




# Installation

The recommended way to add RemoteDb to your Android project is via [JitPack](http://jitpack.io).

First, add JitPack to your project level `build.gradle`:

    allprojects {
        repositories {
            ...
            maven { url 'https://jitpack.io' }
        }
    }

Then add RemoteDb as a dependency in your app level `build.gradle`:

    dependencies {
        ...
        implementation 'com.github.magnuswikhog:remotedb-android:x.x.x'
    }

Where `x.x.x` is the latest release version number, which can be found at [the repos release page](https://github.com/magnuswikhog/remotedb-android/releases).



# Usage

    RemoteDb remoteDb = new RemoteDb(
            getApplicationContext(),
            "demodatabase.db",
            "https://example.com/remotedb-php/store.php",
            "demopassword",
            new RemoteDb.RemoteDbInterface() {
                @Override
                public void onSendToServerSuccess() {
                    // Called when the server has acknowledged that it has successfully
                    // stored the sent entries
                }

                @Override
                public void onSendToServerFailure() {
                    // Called if there was a problem when sending or storing the entries
                    // on the server.
                }
            },
            true
    );

This will create a new instance of RemoteDb which will

* use a local on-device database named "demodatabase.db"
* send entries to the supplied server URL
* send the password "demopassword" to the server
* use the supplied RemoteDbInterface to receive callbacks when a server request succeeds or fails
* delete entries in the local on-device database after they have been successfully stored on the server


# Example

See [the demo project](https://github.com/magnuswikhog/remotedb-android/blob/master/demo/src/main/java/com/magnuswikhog/remotedbproject/MainActivity.java) for a usage example.
