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
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

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
        new DownloadVideoTask(MainActivity.activityCurrent).execute(url);
    }

    //async method to extract audio from video in background
    public static class DownloadVideoTask extends AsyncTask<String, Void, String> {
        private static final String TAG = DownloadVideoTask.class.getCanonicalName();
        PrefsManager prefsManager;

        public DownloadVideoTask(Context ctx) {
            prefsManager = new PrefsManager(ctx);
        }

        //this method will download the audio file by using python script
        @Override
        protected String doInBackground(String... urls) {
            Log.i(TAG, "doInBackground()");
            String videoUrl = urls[0];

            Python py = Python.getInstance();
            PyObject pyObject = py.getModule("vidloader");

            String res = "";
            try {
                Log.i(TAG, "trying download with audio...");
                PyObject result = pyObject.callAttr("dl_video_with_audio",
                        MainActivity.activityCurrent,
                        videoUrl,
                        ABS_PATH_DOCS,
                        prefsManager.getFileName(),
                        mResolution.replaceAll("\\D", ""));
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

            // merge if necessary
            if (prefsManager.getFormatId().contains("+")) {

            }

            return res;
        }

        @Override
        protected void onPostExecute(String s) {
            Log.i(TAG, "OnPostExecute format_id=" + s);

            /*
            try {
                // split format ids
                String fIds = prefsManager.getFormatId();
                Log.i(TAG, "fIds=" + fIds);

                // build filepaths
                String absFilepath = ABS_PATH_DOCS + prefsManager.getFileName();
                String absFilepathVideo = absFilepath;
                String absFilepathAudio = absFilepath;
                absFilepath += ".mp4";

                // append file extensions
                File v = new File(absFilepathVideo+".webm");
                File a = new File(absFilepathAudio+".webm");
                if (v.exists()) {
                    Log.i(TAG, ".webm video file detected");
                    absFilepathVideo = absFilepathVideo+".webm";
                } else {
                    Log.i(TAG, ".mp4 video file detected");
                    absFilepathVideo = absFilepathVideo+".mp4";
                }
                if (a.exists()) {
                    Log.i(TAG, ".webm audio file detected");
                    absFilepathAudio = absFilepathAudio+".webm";
                } else {
                    Log.i(TAG, ".m4a audio file detected");
                    absFilepathAudio = absFilepathAudio+".m4a";
                }

                Log.i(TAG, "absFilePathVideo=" + absFilepathVideo
                        + ", absFilePathAudio=" + absFilepathAudio);

                Log.w(TAG, "skipping audio merge");

                // merge video and audio
                // TODO uncomment
                //ConcatRunner.mergeAV(absFilepath, absFilepathVideo, absFilepathAudio);

                // delete temp files
                // TODO uncomment
                //ConcatRunner.deleteTempFiles(absFilepathVideo, absFilepathAudio);
            } catch (Exception e) {
                Log.e(TAG, "merge failed!");
                e.printStackTrace();
            }
             */

            // scan new media
            String ext = prefsManager.getFileExt();
            String absFilePath = ABS_PATH_DOCS + prefsManager.getFileName() + "." + ext;
            Log.i(TAG, "absolute filepath: " + absFilePath);

            File dl = new File(absFilePath);
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

            Intent intent = new Intent("69");
            intent.putExtra("FILEPATH", absFilePath);
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
