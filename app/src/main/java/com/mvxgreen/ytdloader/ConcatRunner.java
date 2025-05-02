package com.mvxgreen.ytdloader;

import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;

public final class ConcatRunner {
    private static final String TAG = ConcatRunner.class.getCanonicalName();

    private static final String[] TAG_KEYS = {
            "title",
            "artist",
            "album",
            "album_artist",
            "date",
            "genre",
            "disc",
            "track",
            "composer"
    };

    public static int totalChunks = 0, downloadedChunks = 0;
    private static String command;

    public static void merge(MainActivity main, String filepath, String vFilepath, String aFilepath) {
        String msg = "MERGING:\nfilepath=" + filepath
                + "\nvFilepath=" + vFilepath
                + "\naFilepath=" + aFilepath;
        Log.i(TAG, msg);
        // add chunks
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append("-i \"");
        cmdBuilder.append(vFilepath);
        cmdBuilder.append("\" -i \"");
        cmdBuilder.append(aFilepath);
        cmdBuilder.append("\" -c copy ");
        cmdBuilder.append(filepath);
        command = cmdBuilder.toString();

        // run ffmpeg command
        Log.i(TAG, "running ffmpeg command: " + command);
        FFmpegSession session = FFmpegKit.execute(command);
        int result = session.getReturnCode().getValue();

        // TODO log event
        if (result == ReturnCode.SUCCESS) {
            Log.i(TAG, "merge successful!");
        } else {
            Log.e(TAG, "merge failed!");
        }

        deleteTempFiles(vFilepath, aFilepath);
    }

    /**
     * Delete chunk files
     */
    public static boolean deleteTempFiles(String vfp, String afp) {
        Log.i(TAG, "deleteTempFiles");
        new File(afp).delete();
        return new File(vfp).delete();
    }
}
