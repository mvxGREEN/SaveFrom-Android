package com.mvxgreen.ytdloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Provide access to formatting & shared preferences routines & constants
 */
public class PrefsManager {
    private static final String TAG = PrefsManager.class.getCanonicalName();

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~  CONSTRUCTOR  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    public PrefsManager(Context ctx) {
        initSharedPrefs(ctx);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~  SHARED PREFERENCES  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    SharedPreferences sharedPrefs;
    private static final int[] KEY_IDS = {
            R.string.prefs_key_download_url,
            R.string.prefs_key_folder,
            R.string.prefs_key_filename,
            R.string.prefs_key_file_ext,
            R.string.prefs_key_original_url,
            R.string.prefs_key_title,
            R.string.prefs_key_thumbnail_url,
            R.string.prefs_key_token,

    };

    /**
     * Initialize Shared Preferences
     * @param ctx main activity
     */
    public void initSharedPrefs(Context ctx) {
        // Create & store default values, if necessary
        try {
            sharedPrefs = ctx.getSharedPreferences(
                    ctx.getString(R.string.prefs_file_name),
                    Context.MODE_PRIVATE
            );

            SharedPreferences.Editor editor = sharedPrefs.edit();

            // total runs
            if (!sharedPrefs.contains("TOTAL_RUNS")) {
                editor.putInt(
                        "TOTAL_RUNS",
                        0
                );
            }
            editor.apply();

            // other default values
            if (!sharedPrefs.contains("TOTAL_CONVERSIONS")) {
                editor.putInt(
                        "TOTAL_CONVERSIONS",
                        0
                );
            }

            // default string values
            for (int i = 0; i < KEY_IDS.length; ++i) {
                String key = ctx.getString(KEY_IDS[i]);
                if (!sharedPrefs.contains(key)) {
                    editor.putString(
                            key,
                            ""
                    );
                    editor.apply();
                }
            }

        } catch (NullPointerException e) {
            Log.e(TAG, "Edge Error: NullPointer during initSharedPrefs");
            e.printStackTrace();
        }

    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~  GETTERS/SETTERS  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    public SharedPreferences getSharedPrefs() {
        return sharedPrefs;
    }

    public void setFileName(String value) {
        String key = "FILE_NAME";

        Log.i(TAG, "set " + key + " in shared prefs: {" + key + "," + value + "}");

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(
                key,
                value
        );
        editor.apply();
    }

    public void setThumbnailUrl(String value) {
        String key = "THUMBNAIL_URL";

        Log.i(TAG, "set " + key + " in shared prefs: {" + key + "," + value + "}");

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(
                key,
                value
        );
        editor.apply();
    }

    public void setVideoTitle(String value) {
        String key = "VIDEO_TITLE";

        Log.i(TAG, "set " + key + " in shared prefs: {" + key + "," + value + "}");

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(
                key,
                value
        );
        editor.apply();
    }

    /**
     * Set folder for downloaded file
     * @param value folder name
     */
    public void setFileDir(String value) {
        String key = "FOLDER_NAME";

        Log.i(TAG, "set " + key + " in shared prefs: {" + key + "," + value + "}");

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(
                key,
                value
        );
        editor.apply();
    }

    /**
     * Set filename for downloaded file
     * @param value file name
     */
    public void setFileExt(String value) {
        String key = "FILE_EXTENSION";

        Log.i(TAG, "set " + key + " in shared prefs: {" + key + "," + value + "}");

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(
                key,
                value
        );
        editor.apply();
    }

    /**
     * @return filepath for new file
     */
    public String getFileName() {
        return sharedPrefs.getString("FILE_NAME", "");
    }

    /**
     * @return filepath for new file
     */
    public String getThumbnailUrl() {
        return sharedPrefs.getString("THUMBNAIL_URL", "");
    }

    /**
     * @return filepath for new file
     */
    public String getVideoTitle() {
        return sharedPrefs.getString("VIDEO_TITLE", "");
    }

    /**
     * @return filepath for new file
     */
    public String getFileDir() {
        return sharedPrefs.getString("FOLDER_NAME", "");
    }

    /**
     * @return filepath for new file
     */
    public String getFileExt() {
        return sharedPrefs.getString("FILE_EXTENSION", "");
    }

    public String getFormatId() {
        return sharedPrefs.getString("FORMAT_ID", "");
    }

    public void setFormatId(String value) {
        String key = "FORMAT_ID";

        Log.i(TAG, "set " + key + " in shared prefs: {" + key + "," + value + "}");

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(
                key,
                value
        );
        editor.apply();
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~  COUNT RUNS  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    public void incrementTotalRuns() {
        String key = "TOTAL_RUNS";
        int runs = sharedPrefs.getInt(key, 0);
        ++runs;
        Log.i(TAG, "set pref: {" + key + "," + runs + "}");

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(
                key,
                runs
        );
        editor.apply();
    }

    public int getTotalRuns() {
        return sharedPrefs.getInt("TOTAL_RUNS", 0);
    }

    public int incrementConversions() {
        String key = "TOTAL_CONVERSIONS";
        int convs =
                sharedPrefs.getInt(
                        key,
                        0);
        ++convs;
        Log.i(TAG, "set pref: {" + key + "," + convs + "}");

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(
                key,
                convs
        ).apply();
        return convs;
    }

    public String getOriginalUrl() {
        return sharedPrefs.getString("ORIGINAL_URL", "");
    }
    public void setOriginalUrl(String value) {
        String key = "ORIGINAL_URL";

        Log.i(TAG, "set " + key + " in shared prefs: {" + key + "," + value + "}");

        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(
                key,
                value
        );
        editor.apply();
    }
}
