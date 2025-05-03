package com.mvxgreen.ytdloader;

import static com.mvxgreen.ytdloader.MainActivity.ABS_PATH_DOCS;
import static com.mvxgreen.ytdloader.MediaManager.MIME_MP4;

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
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

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

    int pendingIntentId = 0;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "OnBind");
        return null;
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
        new DownloadVideoTask().execute(url);
    }

    //async method to extract audio from video in background
    public class DownloadVideoTask extends AsyncTask<String, Void, String> {

        public DownloadVideoTask() {
            Log.i(TAG, "DownloadVideoTask()");
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
                PyObject result = pyObject.callAttr("dl_video_with_audio",MainActivity.activityCurrent, videoUrl, ABS_PATH_DOCS, mPrefsManager.getFileName());
                res = result.toString();
                Log.i(TAG, "format_ids: "+ res);
                mPrefsManager.setFormatId(res);
            } catch (Exception e) {
                e.printStackTrace();
                String msg = "error downloading video! e="+e;
                Log.e(TAG, msg);

                // send finish broadcast without filepath
                Intent intent = new Intent("69");
                intent.putExtra("FILEPATH", "");
                sendBroadcast(intent);

                stopForeground(true);
                stopSelf();
            }

            // merge if necessary
            if (mPrefsManager.getFormatId().contains("+")) {
                // split format ids
                String fIds = mPrefsManager.getFormatId();
                String fIdVideo = fIds.substring(0, fIds.indexOf("+"));
                String fIdAudio = fIds.substring(fIds.indexOf("+")+1);

                // build filepaths
                String absFilepath = ABS_PATH_DOCS + mPrefsManager.getFileName() + ".mp4";
                String absFilepathVideo = absFilepath + ".f" + fIdVideo;
                String absFilepathAudio = absFilepath + ".f" + fIdAudio;

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
                    Log.i(TAG, ".mp4 video file detected");
                    absFilepathAudio = absFilepathAudio+".mp4";
                }

                // run ffmpeg merge
                ConcatRunner.merge(absFilepath, absFilepathVideo, absFilepathAudio);

                // delete temp files
                ConcatRunner.deleteTempFiles(absFilepathVideo, absFilepathAudio);
            }

            return res;
        }

        @Override
        protected void onPostExecute(String s) {
            Log.i(TAG, "OnPostExecute format_id=" + s);

            // scan new media
            String ext = mPrefsManager.getFileExt();
            String absFilePath = ABS_PATH_DOCS + mPrefsManager.getFileName() + "." + ext;
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
            sendBroadcast(intent);

            stopForeground(true);
            stopSelf();
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

            NotificationChannel channel = new NotificationChannel("SaveFrom", "SaveFrom", NotificationManager.IMPORTANCE_DEFAULT);
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
}
