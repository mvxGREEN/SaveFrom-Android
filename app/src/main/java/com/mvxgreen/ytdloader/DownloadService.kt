package com.mvxgreen.ytdloader

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.AsyncTask
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.firebase.analytics.FirebaseAnalytics
import com.mvxgreen.ytdloader.MainActivity.Companion.ABS_PATH_DOCS
import com.mvxgreen.ytdloader.manager.PrefsManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class DownloadService : Service() {
    private lateinit var mPrefsManager: PrefsManager
    private val binder: IBinder = LocalBinder()
    private var pendingIntentId = 0

    companion object {
        private val TAG = DownloadService::class.java.canonicalName
    }

    /**
     * Class used for the client Binder. Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "OnBind")
        return binder
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")

        mPrefsManager = PrefsManager(applicationContext)
        registerNotification()

        // start download
        downloadVideo(mPrefsManager.originalUrl!!)

        return START_STICKY
    }

    @Suppress("DEPRECATION")
    override fun onStart(intent: Intent?, startId: Int) {
        super.onStart(intent, startId)
        Log.i(TAG, "onStart")
    }

    private fun downloadVideo(url: String) {
        try {
            val bundle = Bundle().apply {
                putString("app_name", "savefrom")
                putString("url", url)
            }
            FirebaseAnalytics.getInstance(this).logEvent("download_start", bundle)
        } catch (ignored: Exception) {}

        MainActivity.activityCurrent?.let {
            DownloadVideoTask(it).execute(url)
        }
    }

    // async download video
    @Suppress("DEPRECATION")
    class DownloadVideoTask(ctx: Context) : AsyncTask<String, Void, String>() {
        private val vidExt = ".mp4"
        private val ap = AndroidPlatform(ctx)
        private val prefsManager = PrefsManager(ctx)

        companion object {
            private val TAG = DownloadVideoTask::class.java.canonicalName
        }

        // this method will download the audio file by using python script
        override fun doInBackground(vararg urls: String): String {
            Log.i(TAG, "doInBackground()")

            // init python
            if (!Python.isStarted()) {
                Python.start(ap)
            }
            val py = Python.getInstance()
            val pyObject = py.getModule("vidloader")

            // get video url and resolution
            val videoUrl = urls[0]
            val resolution = MainActivity.mResolution.replace("\\D".toRegex(), "")

            if (!Python.isStarted()) {
                Python.start(ap)
            }

            var res = ""
            try {
                Log.i(TAG, "trying dl_video_without_audio")

                if (!Python.isStarted()) {
                    Python.start(ap)
                }

                val result = pyObject.callAttr(
                    "dl_video_without_audio",
                    MainActivity.activityCurrent,
                    videoUrl,
                    ABS_PATH_DOCS,
                    prefsManager.fileName,
                    resolution
                )
                res = result.toString()
                Log.i(TAG, "format_ids: $res")
                prefsManager.formatId = res
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = "error downloading video! e=$e"
                Log.e(TAG, msg)

                // send finish broadcast
                val intent = Intent("69").apply {
                    putExtra("FILEPATH", "")
                }
                MainActivity.activityCurrent?.sendBroadcast(intent)
            }

            return res
        }

        override fun onPostExecute(s: String?) {
            Log.i(TAG, "OnPostExecute")

            // build filepaths
            var absFilepath = ABS_PATH_DOCS + prefsManager.fileName
            absFilepath += vidExt

            // scan video file (no audio)
            val dl = File(absFilepath)
            if (dl.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val now = FileTime.fromMillis(System.currentTimeMillis())
                    try {
                        Files.setLastModifiedTime(dl.toPath(), now)
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                }
            }

            // send finish broadcast
            val intent = Intent("69").apply {
                putExtra("FILEPATH", absFilepath)
            }
            MainActivity.activityCurrent?.sendBroadcast(intent)
        }
    }

    private fun registerNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            var pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            }
            val pi = PendingIntent.getActivity(
                MainActivity.activityCurrent,
                pendingIntentId++,
                intent,
                pendingIntentFlags
            )

            val channel = NotificationChannel("SaveFrom", "SaveFrom", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, "SaveFrom")
                .setContentTitle("Downloading…")
                .setSmallIcon(R.drawable.downloader_raw)
                .setProgress(100, 0, true)
                .setOngoing(true)
                .setContentIntent(pi)
                .build()
            manager.notify(43, notification)

            var type = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            ServiceCompat.startForeground(this, 43, notification, type)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                // App not in a valid state to start foreground service
            }
        }
    }

    fun setProgress(maxProgress: Int, progress: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        var pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }
        val pi = PendingIntent.getActivity(
            MainActivity.activityCurrent,
            pendingIntentId++,
            intent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, "SaveFrom")
            .setContentTitle("Downloading Video…")
            .setSmallIcon(R.drawable.downloader_raw)
            .setProgress(maxProgress, progress, false)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(43, notification)
    }
}