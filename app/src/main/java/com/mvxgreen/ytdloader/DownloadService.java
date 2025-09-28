package com.mvxgreen.ytdloader;

import static com.mvxgreen.ytdloader.MainActivity.ABS_PATH_DOCS;
import static com.mvxgreen.ytdloader.MainActivity.mResolution;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.mvxgreen.ytdloader.manager.PrefsManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

public class DownloadService extends Service {
    private static final String TAG = DownloadService.class.getCanonicalName();
    private PrefsManager mPrefsManager;
    private final IBinder binder = new LocalBinder();
    int pendingIntentId = 0;

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public DownloadService getService() {
            // Return this instance of LocalService so clients can call public methods
            return DownloadService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "OnBind");
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");

        mPrefsManager = new PrefsManager(getApplicationContext());
        registerNotification();

        // get original url from prefs
        String ogUrl = mPrefsManager.getOriginalUrl();

        // start download
        downloadVideo(ogUrl);

        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.i(TAG, "onStart");
    }

    private void downloadVideo(String url) {
        try {
            Bundle bundle = new Bundle();
            bundle.putString("app_name", "savefrom");
            bundle.putString("url", url);
            FirebaseAnalytics.getInstance(this)
                    .logEvent("download_start", bundle);
        } catch (Exception ignored) {}

        new DownloadAudioTask(MainActivity.activityCurrent).execute(url);
    }

    // async download audio
    public static class DownloadAudioTask extends AsyncTask<String, Void, String> {
        private static final String TAG = DownloadAudioTask.class.getCanonicalName();
        String videoUrl;
        DownloadVideoTask dvt;
        AndroidPlatform ap;
        PrefsManager prefsManager;

        public DownloadAudioTask(Context ctx) {
            this.dvt = new DownloadVideoTask((MainActivity)ctx);
            prefsManager = new PrefsManager(ctx);
            ap = new AndroidPlatform(ctx);
        }

        //this method will download the audio file by using python script
        @Override
        protected String doInBackground(String... urls) {
            Log.i(TAG, "doInBackground()");
            this.videoUrl = urls[0];
            String resolution = mResolution.replaceAll("\\D", "");

            if (!Python.isStarted()) {
                Python.start(ap);
            }

            Python py = Python.getInstance();
            PyObject pyObject = py.getModule("vidloader");

            // get video file extension
            //PyObject resExt = pyObject.callAttr("extract_video_ext", videoUrl, resolution);
            //vidExt = "." + resExt.toString();

            String res = "";
            try {
                Log.i(TAG, "trying dl_audio");

                PyObject result = pyObject.callAttr("dl_audio",
                        MainActivity.activityCurrent,
                        this.videoUrl,
                        ABS_PATH_DOCS,
                        prefsManager.getFileName());
                res = result.toString();

                Log.i(TAG, "format_ids: "+ res);
                prefsManager.setFormatId(res);
            } catch (Exception e) {
                e.printStackTrace();
                String msg = "error downloading audio: e="+e;
                Log.e(TAG, msg);
            }

            return res;
        }

        @Override
        protected void onPostExecute(String s) {
            Log.i(TAG, "OnPostExecute");

            // start dl_video
            dvt.execute(this.videoUrl);
        }
    }

    // async download video
    public static class DownloadVideoTask extends AsyncTask<String, Void, String> {
        private static final String TAG = DownloadVideoTask.class.getCanonicalName();
        String vidExt = ".mp4", audExt = ".m4a";
        MergeAVTask mavt;
        AndroidPlatform ap;
        PrefsManager prefsManager;

        public DownloadVideoTask(Context ctx) {
            prefsManager = new PrefsManager(ctx);
            ap = new AndroidPlatform(ctx);
            mavt = new MergeAVTask((MainActivity)ctx);
        }

        //this method will download the audio file by using python script
        @Override
        protected String doInBackground(String... urls) {
            Log.i(TAG, "doInBackground()");
            String videoUrl = urls[0];
            String resolution = mResolution.replaceAll("\\D", "");

            Python py = Python.getInstance();
            PyObject pyObject = py.getModule("vidloader");

            // get video file extension
            //PyObject resExt = pyObject.callAttr("extract_video_ext", videoUrl, resolution);
            //vidExt = "." + resExt.toString();

            if (!Python.isStarted()) {
                Python.start(ap);
            }

            String res = "";
            try {
                Log.i(TAG, "trying dl_video");

                if (!Python.isStarted()) {
                    Python.start(ap);
                }

                PyObject result = pyObject.callAttr("dl_video_with_audio",
                        MainActivity.activityCurrent,
                        videoUrl,
                        ABS_PATH_DOCS,
                        prefsManager.getFileName(),
                        resolution);
                res = result.toString();
                Log.i(TAG, "format_ids: "+ res);
                prefsManager.setFormatId(res);
            } catch (Exception e) {
                e.printStackTrace();
                String msg = "error downloading video! e="+e;
                Log.e(TAG, msg);

                // send finish broadcast
                Intent intent = new Intent("69");
                intent.putExtra("FILEPATH", "");
                MainActivity.activityCurrent.sendBroadcast(intent);
            }

            return res;
        }

        @Override
        protected void onPostExecute(String s) {
            Log.i(TAG, "OnPostExecute");

            // build filepaths
            String absFilename = prefsManager.getFileName() + vidExt;
            String absFilepath = ABS_PATH_DOCS + prefsManager.getFileName();
            String absFilepathVideo = absFilepath + "_v" + vidExt;
            String absFilepathAudio = absFilepath + "_a" + audExt;
            absFilepath += vidExt;

            mavt.setFilepaths(absFilepath, absFilepathVideo, absFilepathAudio);

            Log.i(TAG, "absFilePathVideo=" + absFilepathVideo
                    + ", absFilePathAudio=" + absFilepathAudio);
            try {
                Bundle bundle = new Bundle();
                bundle.putString("app_name", "savefrom");
                bundle.putString("filename", absFilename);
                FirebaseAnalytics.getInstance(MainActivity.activityCurrent)
                        .logEvent("download_finish", bundle);
            } catch (Exception ignored) {}

            // scan video file (no audio)
            File dl = new File(absFilepathVideo);
            if (dl.exists()) {
                FileTime now = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    now = FileTime.fromMillis(System.currentTimeMillis());
                    try {
                        Files.setLastModifiedTime(dl.toPath(), now);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            // start merge task
            mavt.execute(absFilepathVideo);
        }
    }

    // async download audio
    public static class MergeAVTask extends AsyncTask<String, Void, String> {
        private static final String TAG = MergeAVTask.class.getCanonicalName();
        AndroidPlatform ap;
        PrefsManager prefsManager;
        String absFilepath, absFilepathVideo, absFilepathAudio;

        public MergeAVTask(Context ctx) {
            prefsManager = new PrefsManager(ctx);
            ap = new AndroidPlatform(ctx);
        }

        public void setFilepaths(String fp, String fpv, String fpa) {
            this.absFilepath = fp;
            this.absFilepathVideo = fpv;
            this.absFilepathAudio = fpa;
        }

        //this method will download the audio file by using python script
        @Override
        protected String doInBackground(String... urls) {
            Log.i(TAG, "doInBackground()");

            String res = "done";

            // merge video and audio
            ConcatRunner.mergeAV(absFilepath, absFilepathVideo, absFilepathAudio);

            // delete temp AV files
            ConcatRunner.deleteTempFiles(absFilepathVideo, absFilepathAudio);

            return res;
        }

        @Override
        protected void onPostExecute(String s) {
            Log.i(TAG, "OnPostExecute");

            // scan merged AV file
            File dl = new File(absFilepathVideo);
            if (dl.exists()) {
                FileTime now = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    now = FileTime.fromMillis(System.currentTimeMillis());
                    try {
                        Files.setLastModifiedTime(dl.toPath(), now);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            // send finish broadcast
            Intent intent = new Intent("69");
            intent.putExtra("FILEPATH", absFilepath);
            MainActivity.activityCurrent.sendBroadcast(intent);
        }
    }

    private void registerNotification() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }
        try {
            Intent intent = new Intent(DownloadService.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingIntentFlags = (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
            PendingIntent pi = PendingIntent.getActivity(MainActivity.activityCurrent, pendingIntentId++, intent, pendingIntentFlags);

            NotificationChannel channel = new NotificationChannel("SaveFrom", "SaveFrom", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);

            Notification notification =
                    new NotificationCompat.Builder(DownloadService.this, "SaveFrom")
                            .setContentTitle("Downloading…")
                            .setSmallIcon(R.drawable.downloader_raw)
                            .setProgress(100,0, true)
                            .setOngoing(true)
                            .setContentIntent(pi)
                            .build();
            manager.notify(43, notification);

            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            }
            ServiceCompat.startForeground(
                    /* service = */ this,
                    /* id = */ 43, // Cannot be 0
                    /* notification = */ notification,
                    /* foregroundServiceType = */ type
            );
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e instanceof ForegroundServiceStartNotAllowedException
            ) {
                // App not in a valid state to start foreground service
                // (e.g started from bg)
            }
            // ...
        }
    }

    public void setProgress(int max_progress, int progress) {
        Intent intent = new Intent(DownloadService.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntentFlags = (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        PendingIntent pi = PendingIntent.getActivity(MainActivity.activityCurrent, pendingIntentId++, intent, pendingIntentFlags);
        Notification notification = new NotificationCompat.Builder(DownloadService.this, "SaveFrom")
                .setContentTitle("Downloading Video…")
                .setSmallIcon(R.drawable.downloader_raw)
                .setProgress(max_progress, progress, false)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(43, notification);
    }
}
