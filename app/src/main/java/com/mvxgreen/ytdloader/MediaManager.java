package com.mvxgreen.ytdloader;

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;

import java.io.File;

public class MediaManager implements
        MediaScannerConnection.MediaScannerConnectionClient {
    private final String TAG = MediaManager.class.getCanonicalName();
    public static final String MIME_MP4 = "video/mp4",
                                EXT_MP4 = ".mp4";

    private final MediaScannerConnection CONNECTION;
    private final String PATH;
    private final String MIME_TYPE;
    private MainActivity main;


    // filePath - where to scan;
    // mime type of media to scan i.e. "image/jpeg".
    // use "*/*" for any media
    public MediaManager(MainActivity main, String filePath, String mime){
        PATH = filePath;
        MIME_TYPE = mime;
        CONNECTION = new MediaScannerConnection(main, this);
        this.main = main;
    }

    // do the scanning
    public void scanMedia() {
        CONNECTION.connect();
    }

    // start the scan when scanner is ready
    public void onMediaScannerConnected() {
        CONNECTION.scanFile(PATH, MIME_TYPE);
        Log.w(TAG, "media file scanned: " + PATH);
    }

    public void onScanCompleted(String path, Uri uri) {
        Log.i(TAG, "onScanCompleted");
    }
}