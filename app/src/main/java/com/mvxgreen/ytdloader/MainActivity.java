package com.mvxgreen.ytdloader;

import static com.mvxgreen.ytdloader.MediaManager.MIME_MP4;

import android.Manifest;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceRequest;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.mvxgreen.ytdloader.databinding.ActivityMainBinding;
import com.mvxgreen.ytdloader.frag.BigFragment;
import com.mvxgreen.ytdloader.frag.FileFragment;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import kotlin.text.Charsets;
import okhttp3.Call;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getCanonicalName(),
            ABS_PATH_DOCS = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOCUMENTS)
                    .getAbsolutePath() + "/";

    public ActivityMainBinding mBinding;
    Animation fadeIn, fadeOut;
    FileFragment fileFragment;

    PrefsManager mPrefsManager;

    AndroidPlatform androidPlatform;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        if (mBinding == null) {
            mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        }
        setContentView(mBinding.getRoot());

        // Fixes "strict-mode" error when fetching webpage... idek..
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        initMainViews();
        mPrefsManager = new PrefsManager(MainActivity.this);

        if (!Python.isStarted()) {
            androidPlatform = new AndroidPlatform(this);
            Python.start(androidPlatform);
        }

        // check permissions
        hasPermissions();
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~  PERMISSIONS  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    public static String[] storage_permissions = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static String[] storage_permissions_33 = {
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
    };

    public static String[] permissions() {
        String[] p;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            p = storage_permissions_33;
        } else {
            p = storage_permissions;
        }
        return p;
    }
    private boolean hasPermissions() {
        if ((ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                && (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    permissions(),
                    1);
            return false;
        } else {
            return true;
        }
    }

    private void initMainViews() {
        initAnimations();
        mBinding.mainScroll.setSmoothScrollingEnabled(true);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Search Bar
        mBinding.mainSearchBar.addTextChangeListener(new TextWatcher() {
            int oldCount;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                oldCount = count;
            }

            @Override
            public void onTextChanged(final CharSequence s, int start, int before, int count) {
                if (count == 0 && oldCount != 0) {
                    // text cleared
                    killKeyboard();
                    showEmptyLayout();
                } else if (count - oldCount > 1) {
                    // text pasted
                    killKeyboard();
                    showLoadingLayout();

                    // get input
                    String inputText = s.toString().trim();

                    // check for internet & valid url
                    if (isInternetAvailable()) {
                        mPrefsManager.setOriginalUrl(inputText);
                        new Thread(() -> {
                            loadVideoInfo(inputText);
                        }).start();
                    } else {
                        Toast.makeText(MainActivity.this, getString(R.string.msg_no_internet), Toast.LENGTH_SHORT).show();
                    }
            }
        }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    private void initAnimations() {
        // Load entrance animation
        fadeIn = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_in);
        fadeIn.setDuration(432);
        fadeIn.setInterpolator(new AccelerateInterpolator());

        // Load exit animation
        fadeOut = AnimationUtils.loadAnimation(MainActivity.this, R.anim.fade_out);
        fadeOut.setDuration(432);
        fadeOut.setInterpolator(new AccelerateInterpolator());
    }

    public void killKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(
                findViewById(R.id.main_search_bar).getWindowToken(),
                0);
    }

    /**
     * Check if device is connected to internet
     * @return is device connected to internet?
     */
    public static boolean isInternetAvailable() {
        try {
            InetAddress ipAddr = InetAddress.getByName("google.com");
            return !ipAddr.toString().isEmpty();

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Respond to 'Rate' menu item click
     */
    public void onRateClick(MenuItem menuItem) {
        final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

    public static void setProgress(MainActivity mainActivity, String prog) {
        Log.i(TAG, "setProgress: " + prog);
        prog = prog.trim().replace("%", "");
        int p = (int)Double.parseDouble(prog);

        // TODO parse/convert prog into int and update ui
        mainActivity.runOnUiThread(() -> {
            mainActivity.mBinding.numProgress.setVisibility(View.VISIBLE);
            mainActivity.mBinding.numProgress.setProgress(p);
        });
    }

    /**
     * Create fragment corresponding to clicked view
     * @param mi clicked menu item
     *
     * By Max Green  12/5/2020
     */
    public void showBigFrag(MenuItem mi) {
        // bundle menu item title
        Bundle extras = new Bundle();
        String title = mi.getTitle().toString();
        extras.putString(getString(R.string.key_extra_menu_item_title), title);

        // Create fragment, add extras
        BigFragment bigFragment = new BigFragment();
        bigFragment.setArguments(extras);

        // Inflate fragment
        ConstraintLayout fragView = this.findViewById(R.id.big_frag_holder);
        fragView.removeAllViews();
        fragView.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.big_frag_holder, bigFragment, null)
                .commitAllowingStateLoss();
    }

    /**
     * Hide fragment (holder)
     * @param v close button (clicked)
     */
    public void closeBigFrag(View v) {
        ConstraintLayout fragHolder = findViewById(R.id.big_frag_holder);
        fragHolder.setVisibility(View.GONE);
    }

    public void showFileFrag() {
        /*
        String fileName = prefsManager.getFileName();
        String fileExt = prefsManager.getFileExt();
        final String absPath = ABSOLUTE_PATH_MUSIC + fileName + fileExt;

        // Inflate fragment
        runOnUiThread(() -> {
            Bundle extras = new Bundle();
            extras.putString(getString(R.string.key_extra_abs_filepath), absPath);
            // Create fragment
            FileFragment fileFragment = new FileFragment();
            fileFragment.setArguments(extras);
            ConstraintLayout fragView = MainActivity.this.findViewById(R.id.file_hint_holder);
            fragView.removeAllViews();
            fragView.startAnimation(mLayoutManager.fadeIn);
            fragView.setVisibility(View.VISIBLE);
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.file_hint_holder, fileFragment, null)
                    .commitAllowingStateLoss();
        });

         */
    }

    public void closeFileFrag() {
        if (fileFragment != null) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction transaction = fm.beginTransaction();
            transaction.remove(fileFragment).commitAllowingStateLoss();
        }

        // hide holder view
        ConstraintLayout fragHolder = findViewById(R.id.file_hint_holder);
        fragHolder.setVisibility(View.GONE);
    }

    private void showEmptyLayout() {
        Log.i(TAG, "showEmptyLayout()");

        closeFileFrag();

        mBinding.imgPreview.setVisibility(View.GONE);
        mBinding.glowingLoader.setVisibility(View.GONE);
        mBinding.mainSearchBar.setText("");
        mBinding.btnDownload.setVisibility(View.GONE);
        mBinding.btnDownload.setEnabled(false);
        mBinding.numProgress.setVisibility(View.GONE);
        mBinding.ivCircle.setVisibility(View.VISIBLE);
        mBinding.btnPaste.setVisibility(View.VISIBLE);
        mBinding.filenameEdittext.setEnabled(false);
        mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInvisible));
        mBinding.filenameEdittext.setText("");
    }

    private void showLoadingLayout() {
        Log.i(TAG, "showLoadingLayout()");

        Toast.makeText(this, "Loading, this may take awhile…", Toast.LENGTH_LONG).show();

        closeFileFrag();
        mBinding.imgPreview.setVisibility(View.INVISIBLE);
        mBinding.btnDownload.setVisibility(View.GONE);
        mBinding.btnDownload.setEnabled(false);
        mBinding.numProgress.setVisibility(View.GONE);
        mBinding.glowingLoader.startAnimation(fadeIn);
        mBinding.glowingLoader.setVisibility(View.VISIBLE);
        mBinding.ivCircle.setVisibility(View.GONE);
        mBinding.btnPaste.setVisibility(View.GONE);
    }

    private void showPreviewLayout() {
        Log.i(TAG, "showPreviewLayout()");

        String thumbnailUrl = mPrefsManager.getThumbnailUrl();

        updateEditFilenameView(mPrefsManager.getFileName());
        mBinding.btnPaste.setVisibility(View.GONE);
        mBinding.imgPreview.setAlpha(1.0f);
        mBinding.imgPreview.setVisibility(View.VISIBLE);
        Picasso.Builder builder = new Picasso.Builder(MainActivity.this);
        builder.listener(new Picasso.Listener()
        {
            @Override
            public void onImageLoadFailed(Picasso picasso, Uri uri, Exception exception)
            {
                // TODO handle picasso error
            }
        });
        if (!thumbnailUrl.isEmpty()) {
            builder.build().load(thumbnailUrl)
                    .fit()
                    .centerCrop()
                    .into(mBinding.imgPreview, previewCallback);
        }
    }

    private final Callback previewCallback = new Callback() {
        @Override
        public void onSuccess() {
            Log.i(TAG, "onSuccess() decorating preview...");
            mBinding.glowingLoader.startAnimation(fadeOut);
            mBinding.glowingLoader.setVisibility(View.GONE);
            mBinding.numProgress.setVisibility(View.GONE);
            // draw circle
            mBinding.ivCircle.setVisibility(View.VISIBLE);
            ((Animatable)mBinding.ivCircle.getDrawable()).start();
            // ...after circle is drawn
            new Handler().postDelayed(() -> {
                mBinding.btnDownload.setVisibility(View.VISIBLE);
                mBinding.btnDownload.setEnabled(true);
                mBinding.btnDownload.startAnimation(fadeIn);
                mBinding.filenameEdittext.setEnabled(true);
                mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInverse));
            }, 432);
        }
        @Override
        public void onError(Exception e) {
            Log.e("onPicassoFinished", ".onError()");
            e.printStackTrace();
            mBinding.btnPaste.setVisibility(View.GONE);
            mBinding.glowingLoader.startAnimation(fadeOut);
            mBinding.glowingLoader.setVisibility(View.GONE);
            mBinding.imgPreview.setVisibility(View.GONE);
            // draw circle
            mBinding.ivCircle.setVisibility(View.VISIBLE);
            ((Animatable)mBinding.ivCircle.getDrawable()).start();
            // ...after circle is drawn
            new Handler().postDelayed(() -> {
                mBinding.btnDownload.setVisibility(View.VISIBLE);
                mBinding.btnDownload.setEnabled(true);
                mBinding.filenameEdittext.setEnabled(true);
                mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInverse));
                mBinding.mainScroll.smoothScrollTo(0, mBinding.fileHintHolder.getBottom());
            }, 432);
        }
    };

    private void showDownloadingLayout() {
        Log.i(TAG, "showDownloadingLayout()");

        Toast.makeText(this, "Downloading…", Toast.LENGTH_SHORT).show();

        updateFilenamePref();
        mBinding.btnDownload.setEnabled(false);
        mBinding.ivCircle.startAnimation(fadeOut);
        mBinding.btnDownload.startAnimation(fadeOut);
        mBinding.ivCircle.setVisibility(View.GONE);
        mBinding.btnDownload.setVisibility(View.GONE);
        mBinding.imgPreview.setAlpha(0.69f);
        mBinding.numProgress.setVisibility(View.VISIBLE);
        mBinding.numProgress.setProgress(0);
        new Handler().postDelayed(() -> {
            mBinding.glowingLoader.startAnimation(fadeIn);
            mBinding.glowingLoader.setVisibility(View.VISIBLE);
            mBinding.filenameEdittext.setEnabled(false);
            mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInvisible));
        }, 200);
    }

    private void showFinishLayout() {
        Log.i(TAG, "showFinishLayout()");
        mBinding.ivCircle.setVisibility(View.GONE);
        mBinding.glowingLoader.setVisibility(View.GONE);
        mBinding.imgPreview.setAlpha(1.0f);
        mBinding.btnDownload.setVisibility(View.GONE);
        mBinding.btnDownload.setEnabled(false);
        mBinding.btnPaste.setVisibility(View.VISIBLE);
        mBinding.btnPaste.setEnabled(true);
        mBinding.ivCircle.setVisibility(View.VISIBLE);
        mBinding.numProgress.setVisibility(View.GONE);
        mBinding.numProgress.setProgress(0);
        mBinding.mainScroll.smoothScrollTo(0, mBinding.mainScroll.getBottom());
    }

    public void updateFilenamePref() {
        String input = mBinding.filenameEdittext.getText().toString();
        if (input.isEmpty()) {
            mPrefsManager.setFileName("VIDEO_LOADER_DOWNLOAD");
        } else {
            mPrefsManager.setFileName(input);
        }

    }

    public void updateEditFilenameView(String fileName) {
        if (fileName.indexOf('.') != -1) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        mBinding.filenameEdittext.setText(fileName);
    }


    private void downloadVideo(String videoUrl) {
        // run async
        new DownloadVideoTask().execute(videoUrl);

    }

    private void loadVideoInfo(String url) {
        String titleStr = "", extStr = "", thumbStr ="";

        // run async
        //calling python function with it's object to extract audio
        Python py = Python.getInstance();
        PyObject pyObject = py.getModule("download_video");

        // extract video information

        try {
            PyObject title = pyObject.callAttr("extract_video_title", url);
            titleStr = title.toString();
            if (titleStr.length() > 25) {
                titleStr = titleStr.substring(0, 25);
            }

            PyObject thumbnail = pyObject.callAttr("extract_video_thumbnail", url);
            thumbStr = thumbnail.toString();
            PyObject ext = pyObject.callAttr("extract_video_ext", url);
            extStr = ext.toString();
        } catch (Exception e) {
            Log.e(TAG, e.toString());

            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Unsupported URL", Toast.LENGTH_SHORT).show();
                showEmptyLayout();
            });

            return;
        }


        Log.i(TAG, "Extracted video info: "
                + "filename: " + titleStr + "\n"
                + "ext: " + extStr + "\n"
                + "thumbnail url: " + thumbStr);

        mPrefsManager.setFileName(titleStr);
        mPrefsManager.setThumbnailUrl(thumbStr);
        mPrefsManager.setFileExt(extStr);

        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Download prepared!",
                    Toast.LENGTH_SHORT).show();

            showPreviewLayout();
        });
    }

    public static void printLongLog(String l) {
        int maxLogSize = 1000;
        for(int i = 0; i <= l.length() / maxLogSize; i++) {
            int start = i * maxLogSize;
            int end = (i+1) * maxLogSize;
            end = Math.min(end, l.length());
            Log.i(TAG, l.substring(start, end));
        }
    }

    //async method to extract audio from video in background
    private class DownloadVideoTask extends AsyncTask<String, Void, String> {

        public DownloadVideoTask() {
            Log.i(TAG, "DownloadVideoTask()");
        }


        //this method will download the audio file by using python script
        @Override
        protected String doInBackground(String... urls) {
            Log.i(TAG, "doInBackground()");
            //get the url from model
            String videoUrl = urls[0];

            //calling python function with it's object to extract audio
            Python py = Python.getInstance();
            PyObject pyObject = py.getModule("download_video");

            //now again calling the function to get the audio file url
            PyObject result = pyObject.callAttr("download_video",MainActivity.this, videoUrl, ABS_PATH_DOCS);
            String res = result.toString();
            res = res.replace(" ", "_").replace("&","");
            Log.i(TAG, "video title: "+ res);

            return result.toString();
        }

        @Override
        protected void onPostExecute(String s) {
            Log.i(TAG, "onPostExecute()\n" + s);

            // scan media
            String title = mPrefsManager.getFileName();
            String ext = mPrefsManager.getFileExt();
            String absFilePath = ABS_PATH_DOCS + title + "." + ext;
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

            new MediaManager(MainActivity.this,
                    absFilePath, MIME_MP4).scanMedia();

            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Download finished!",
                        Toast.LENGTH_SHORT).show();

                showFinishLayout();
            });

        }
    }

    /**
     * Respond to clicks on paste button
     * @param v clicked view
     */
    public void onPasteClick(View v) {
        ClipboardManager clipboardManager = (ClipboardManager) MainActivity.this.getSystemService(CLIPBOARD_SERVICE);
        String primaryStr = "";
        ClipData primaryClip = clipboardManager.getPrimaryClip();
        if (primaryClip != null) {
            primaryStr = primaryClip.getItemAt(0).getText().toString();
            primaryStr = primaryStr.trim();

            mBinding.mainSearchBar.setText(primaryStr);
        } else {
            Toast.makeText(MainActivity.this, getString(R.string.msg_no_url_copied),
                    Toast.LENGTH_LONG).show();

            mBinding.mainSearchBar.setText(primaryStr);
        }
    }

    /**
     * On Download button clicked
     */
    public void onDownloadClick(View v) {
        Log.i(TAG, ".onDownloadClicked()");

        // check permissions before starting download
        if (!hasPermissions()) {
            Toast.makeText(v.getContext(), "Missing permission to\nwrite to storage", Toast.LENGTH_LONG).show();
            return;
        }

        showDownloadingLayout();
        new Thread(() -> {
            downloadVideo(mPrefsManager.getOriginalUrl());
        }).start();
    }

}