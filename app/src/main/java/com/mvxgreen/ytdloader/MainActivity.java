package com.mvxgreen.ytdloader;

import static android.view.View.GONE;
import static com.mvxgreen.ytdloader.manager.MediaManager.MIME_MP4;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;
import com.android.billingclient.api.UnfetchedProduct;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.google.common.collect.ImmutableList;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.mvxgreen.ytdloader.databinding.ActivityMainBinding;
import com.mvxgreen.ytdloader.frag.BigFragment;
import com.mvxgreen.ytdloader.frag.FileFragment;
import com.mvxgreen.ytdloader.manager.AdsManager;
import com.mvxgreen.ytdloader.manager.MediaManager;
import com.mvxgreen.ytdloader.manager.PrefsManager;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.net.InetAddress;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PurchasesUpdatedListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = MainActivity.class.getCanonicalName();
    public static final String ABS_PATH_DOCS = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOCUMENTS)
                    .getAbsolutePath() + "/";

    public static MainActivity activityCurrent;
    DownloadService mDownloadService;
    public ActivityMainBinding mBinding;
    Animation fadeIn, fadeOut;
    FileFragment fileFragment;

    PrefsManager prefsManager;
    FinishReceiver mFinishReceiver;

    AndroidPlatform androidPlatform;

    boolean isBackgroundEnabled = false;

    static String mResolution = "2160p";

    // admob
    ConsentInformation consentInformation;

    // billing
    public static boolean MIsGold = false;
    private BillingClient billingClient;
    public static BillingFlowParams MBillingFlowParams;

    public MainActivity() {
        activityCurrent = this;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
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

        prefsManager = new PrefsManager(MainActivity.this);
        isBackgroundEnabled = !(prefsManager.getBackgroundEnabled()).isEmpty();
        Log.i(TAG, "isBackgroundEnabled="+isBackgroundEnabled);
        initMainViews();

        if (!Python.isStarted()) {
            androidPlatform = new AndroidPlatform(this);
            Python.start(androidPlatform);
        }

        // check permissions
        hasStoragePermissions();

        // register receivers
        mFinishReceiver = new FinishReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mFinishReceiver, new IntentFilter("69"), RECEIVER_EXPORTED);
        } else {
            registerReceiver(mFinishReceiver, new IntentFilter("69"));
        }

        // init billing
        loadBillingClient();

        // init admob
        loadAdmob();
    }

    @Override
    protected void onResume() {
        if (billingClient != null) {
            // check purchases
            checkSubscriptionStatus();
        }

        super.onResume();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(mFinishReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    public void onYearlyClick(View v) {
        Log.i(TAG, "onYearlyClick");
        //launchBillingFlow("yearly");
        launchBillingFlow();
    }

    public void onUpgradeClick(View v) {
        onUpgradeClick();
    }

    public void onUpgradeClick() {
        Log.i(TAG, "onUpgradeClick");
        //launchBillingFlow("monthly");
        launchBillingFlow();
    }

    public void onGetInflyerClick(View v) {
        // open inflyer in google play
        String playStoreUrl = "https://play.google.com/store/apps/details?id=green.mobileapps.downloader4inflact";
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(playStoreUrl));
        startActivity(intent);
    }

    public void launchBillingFlow() {
        Log.i(TAG, "launchBillingFlow");
        if (MBillingFlowParams != null) {
            BillingResult billingResult = billingClient.launchBillingFlow(MainActivity.this, MBillingFlowParams);
        } else {
            Log.e(TAG, "MBillingFlowParams is null");
        }

    }

    class MBillingClientListener implements BillingClientStateListener {

        MBillingClientListener() {}

        @Override
        public void onBillingServiceDisconnected() {
            MainActivity.this.establishBillingConnection();
        }

        @Override
        public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
            Log.i(TAG, "onBillingSetupFinished");
            if (billingResult.getResponseCode() ==  BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Billing Response Code == OK");
                // query available products
                QueryProductDetailsParams queryProductDetailsParams =
                        QueryProductDetailsParams.newBuilder()
                                .setProductList(
                                        ImmutableList.of(
                                                QueryProductDetailsParams.Product.newBuilder()
                                                        .setProductId("savefrom_gold")
                                                        .setProductType(BillingClient.ProductType.SUBS)
                                                        .build()))
                                .build();

                billingClient.queryProductDetailsAsync(
                        queryProductDetailsParams,
                        new ProductDetailsResponseListener() {
                            public void onProductDetailsResponse(@NonNull BillingResult billingResult,
                                                                 @NonNull QueryProductDetailsResult queryProductDetailsResult) {
                                Log.i(TAG, "onProductDetailsResponse");
                                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                    Log.i(TAG, "Billing Response Code == OK");
                                    if (queryProductDetailsResult.getProductDetailsList().size() == 0) {
                                        Log.e(TAG, "no products found");
                                    }
                                    for (ProductDetails productDetails : queryProductDetailsResult.getProductDetailsList()) {
                                        Log.i(TAG, "found product details");

                                        // get product details
                                        ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                                                ImmutableList.of(
                                                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                                                // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                                                                .setProductDetails(productDetails)
                                                                // Get the offer token:
                                                                // a. For one-time products, call ProductDetails.getOneTimePurchaseOfferDetailsList()
                                                                // for a list of offers that are available to the user.
                                                                // b. For subscriptions, call ProductDetails.subscriptionOfferDetails()
                                                                // for a list of offers that are available to the user.
                                                                .setOfferToken(productDetails.getSubscriptionOfferDetails().get(0).getOfferToken())
                                                                .build()
                                                );

                                        // set billing flow params
                                        MBillingFlowParams = BillingFlowParams.newBuilder()
                                                .setProductDetailsParamsList(productDetailsParamsList)
                                                .build();
                                    }

                                    for (UnfetchedProduct unfetchedProduct : queryProductDetailsResult.getUnfetchedProductList()) {
                                        // Handle any unfetched products as appropriate.
                                    }
                                } else {
                                    Log.w(TAG, "Billing Response Code != OK");
                                }
                            }
                        }
                );

                // check purchases
                checkSubscriptionStatus();
            }
        }
    }

    // TODO move to billing manager
    public void loadBillingClient() {
        Log.i(TAG, "loadBillingClient");

        // init billing client
        billingClient = BillingClient.newBuilder(MainActivity.this)
                .setListener(MainActivity.this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
                .enableAutoServiceReconnection()
                .build();

        // connect to billing service
        billingClient.startConnection(new MBillingClientListener());
    }

    public void establishBillingConnection() {
        // connect to billing service
        billingClient.startConnection(new MBillingClientListener());
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                && purchases != null) {
            for (Purchase purchase : purchases) {
                String purchaseId = purchase.getProducts().get(0);

                if (purchaseId == "savefrom_gold") {
                    if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
                        Log.w(TAG, "purchase item not purchased");
                    } else if (!purchase.isAcknowledged()) {
                        Log.i(TAG, "purchase is not yet acknowledged");

                        handlePurchase(purchase);
                    }
                }
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "purchase canceled");
        } else {
            Log.i(TAG, "no purchases found");

            // update shared prefs
            SharedPreferences sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean("IS_GOLD", false);
            editor.apply();

            MIsGold = false;

            // update toolbar icon
            MainActivity.this.runOnUiThread(() -> {
                Toolbar toolbar = MainActivity.this.findViewById(R.id.toolbar);
                MenuItem upgradeItem = toolbar.getMenu().findItem(R.id.action_upgrade);
                upgradeItem.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.diamond_24));
            });
        }
    }

    public void checkSubscriptionStatus() {
        QueryPurchasesParams queryPurchasesParams = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build();
        billingClient.queryPurchasesAsync(queryPurchasesParams, new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> list) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // return if empty
                    if (list.size() == 0) {
                        Log.i(TAG, "no purchases found");

                        // update shared prefs
                        SharedPreferences sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putBoolean("IS_GOLD", false);
                        editor.apply();

                        MIsGold = false;

                        // update ui to gold
                        MainActivity.this.runOnUiThread(() -> {
                            Toolbar toolbar = MainActivity.this.findViewById(R.id.toolbar);
                            MenuItem upgradeItem = toolbar.getMenu().findItem(R.id.action_upgrade);
                            upgradeItem.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.diamond_24));
                        });

                        return;
                    }

                    // process purchases
                    for (Purchase purchase :
                            list) {
                        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
                            Log.w(TAG, "purchase state is not purchased");
                            return;
                        } else if (!purchase.isAcknowledged()) {
                            MainActivity.this.handlePurchase(purchase);
                        } else {
                            // update shared prefs
                            SharedPreferences sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.putBoolean("IS_GOLD", true);
                            editor.apply();

                            // set global variable
                            MIsGold = true;

                            // hide banner ad
                            mBinding.bannerContainer.setVisibility(GONE);

                            // update toolbar icon
                            MainActivity.this.runOnUiThread(() -> {
                                Toolbar toolbar = MainActivity.this.findViewById(R.id.toolbar);
                                MenuItem upgradeItem = toolbar.getMenu().findItem(R.id.action_upgrade);
                                upgradeItem.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.diamond_24_gold));
                            });
                        }
                    }
                }
            }
        });
    }

    public void handlePurchase(Purchase purchase) {
        AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
            @Override
            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "purchase acknowledged");

                    // update shared prefs
                    SharedPreferences sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putBoolean("IS_GOLD", true);
                    editor.apply();

                    // set global variable
                    MIsGold = true;

                    // hide banner ad
                    mBinding.bannerContainer.setVisibility(GONE);

                    // update toolbar icon
                    MainActivity.this.runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Thank you, enjoy! <3", Toast.LENGTH_LONG).show();
                        Toolbar toolbar = MainActivity.this.findViewById(R.id.toolbar);
                        MenuItem upgradeItem = toolbar.getMenu().findItem(R.id.action_upgrade);
                        upgradeItem.setIcon(ContextCompat.getDrawable(MainActivity.this, R.drawable.diamond_24_gold));
                        MainActivity.this.showEmptyLayout();
                    });
                }
            }
        });
    }

    // TODO move to ads manager
    public void loadAdmob() {
        // init admob
        /*
        // Google UMP debug settings
        // test EU location (GDPR privacy form)
        ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(this)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId("2C119C1870497A18CD6315F6A9D8537E")
                .build();
         */

        // create a consent request parameters object
        ConsentRequestParameters params = new ConsentRequestParameters
                .Builder()
                //.setConsentDebugSettings(debugSettings)
                .build();

        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        //consentInformation.reset();
        consentInformation.requestConsentInfoUpdate(
                this,
                params,
                (ConsentInformation.OnConsentInfoUpdateSuccessListener) () -> {
                    // load and show the consent form.
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            this,
                            (ConsentForm.OnConsentFormDismissedListener) loadAndShowError -> {
                                if (loadAndShowError != null) {
                                    // Consent gathering failed.
                                    Log.w(TAG, String.format("%s: %s",
                                            loadAndShowError.getErrorCode(),
                                            loadAndShowError.getMessage()));
                                }

                                // consent gathered
                                if (consentInformation.canRequestAds() &&
                                        !MIsGold) {
                                    // initialize admob sdk
                                    MobileAds.initialize(this, initializationStatus -> {});

                                    // load admob ads
                                    mBinding.bannerContainer.setVisibility(View.VISIBLE);
                                    AdsManager.loadAdmobInterstitialAd(MainActivity.this);
                                    AdsManager.loadBanner(MainActivity.this, mBinding);
                                }

                                if (isPrivacyOptionsRequired()) {
                                    // Regenerate the options menu to include privacy settings
                                    invalidateOptionsMenu();
                                }
                            }
                    );
                },
                (ConsentInformation.OnConsentInfoUpdateFailureListener) requestConsentError -> {
                    // Consent gathering failed.
                    Log.w(TAG, String.format("%s: %s",
                            requestConsentError.getErrorCode(),
                            requestConsentError.getMessage()));
                });

        // TODO look into using IMA SDK
        // Check if you can initialize the IMA SDK in parallel
        // while checking for new consent information. Consent obtained in
        // the previous session can be used to request ads.
        if (consentInformation.canRequestAds() &&
                !MIsGold) {
            // initialize Google Admob SDK
            MobileAds.initialize(this, initializationStatus -> {});

            // load admob ads
            mBinding.bannerContainer.setVisibility(View.VISIBLE);
            AdsManager.loadAdmobInterstitialAd(MainActivity.this);
            AdsManager.loadBanner(MainActivity.this, mBinding);
        }
    }

    public boolean isPrivacyOptionsRequired() {
        return consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~  PERMISSIONS  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    public static String[] req_permissions_old = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static String[] req_permissions = {
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
    };

    public static String[] getStoragePermissions() {
        String[] p;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            p = req_permissions;
        } else {
            p = req_permissions_old;
        }
        return p;
    }

    private boolean hasStoragePermissions() {
        if ((ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                && (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    getStoragePermissions(),
                    1);
            return false;
        } else {
            return true;
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        } else {
            return true;
        }
    }

    private void initMainViews() {
        initAnimations();
        mBinding.mainScroll.setSmoothScrollingEnabled(true);

        // toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // search bar
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
                    String input = s.toString();

                    // validate input
                    if (!input.contains("https://")) {// log event
                        // log invalid input and exit
                        try {
                            Bundle bundle = new Bundle();
                            bundle.putString("app_name", "savefrom");
                            bundle.putString("input", input);
                            FirebaseAnalytics.getInstance(MainActivity.this)
                                    .logEvent("invalid_input", bundle);
                        } catch (Exception ignored) {}

                        Toast.makeText(MainActivity.this, "Please copy a video URL", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (input.contains("instagram.com")) {
                        showBigFrag("InFlyer");
                        return;
                    }

                    final String inputText = input;
                    if (!input.contains("https://")) {
                        input = "https://" + input;
                    }
                    String domain = input.substring(input.indexOf("https://")+8);
                    if (input.contains("/")) {
                        domain = input.substring(0, input.indexOf("/"));
                    }

                    // log valid input
                    try {
                        Bundle bundle = new Bundle();
                        bundle.putString("app_name", "savefrom");
                        bundle.putString("input", input);
                        bundle.putString("domain", domain);
                        FirebaseAnalytics.getInstance(MainActivity.this)
                                .logEvent("valid_input", bundle);
                    } catch (Exception ignored) {}

                    killKeyboard();
                    showLoadingLayout();

                    // check for internet & valid url
                    if (isInternetAvailable()) {
                        prefsManager.setOriginalUrl(inputText);
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

        // init spinner
        Spinner spinner = (Spinner) findViewById(R.id.res_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.res_array,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // set default resolution
        SharedPreferences sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE);
        int selectionIndex = sharedPref.getInt("RES_POSITION", 0);
        spinner.setSelection(selectionIndex);

        // spinner item selected listener
        spinner.setOnItemSelectedListener(this);

        // permission frag
        if (!isBackgroundEnabled) {
            Log.i(TAG, "showing permission holder");
            mBinding.permissionHolder.setVisibility(View.VISIBLE);
        }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onUpgradeClick(MenuItem menuItem) {
        showBigFrag(menuItem);
    }

    // open about page
    public void onAboutClick(MenuItem menuItem) {
        String aboutUrl = "https://mobileapps.green/";
        Intent aboutIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(aboutUrl));
        MainActivity.this.startActivity(aboutIntent);
    }

    // open privacy policy page
    public void onPrivacyClick(MenuItem menuItem) {
        String privacyUrl = "https://mobileapps.green/privacy-policy";
        Intent privacyIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl));
        MainActivity.this.startActivity(privacyIntent);
    }

    public void onRateClick(MenuItem menuItem) {
        final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

    public static void setProgress(String progressStr) {
        Log.i(TAG, "setProgress: " + progressStr);
        progressStr = progressStr.trim().replace("%", "");
        int progress = (int)Double.parseDouble(progressStr);

        // update activity ui
        MainActivity.activityCurrent.runOnUiThread(() -> {
            MainActivity.activityCurrent.mBinding.numProgress.setVisibility(View.VISIBLE);
            MainActivity.activityCurrent.mBinding.numProgress.setProgress(progress);
        });

        // update notification
        MainActivity.activityCurrent.mDownloadService.setProgress(100, progress);
    }

    /**
     * Create fragment corresponding to clicked view
     * @param mi clicked menu item
     *
     * By Max Green  12/5/2020
     */
    public void showBigFrag(MenuItem mi) {
        showBigFrag(mi.getTitle().toString());
    }

    public void showBigFrag(String title) {
        Bundle extras = new Bundle();
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
        closeBigFrag();
    }

    public void closeBigFrag() {
        ConstraintLayout fragHolder = findViewById(R.id.big_frag_holder);
        fragHolder.setVisibility(GONE);
    }

    public void showFileFrag() {
        String fileName = prefsManager.getFileName();
        String fileExt = prefsManager.getFileExt();
        final String absPath = ABS_PATH_DOCS + fileName + "." + fileExt;

        // inflate fragment
        runOnUiThread(() -> {
            Bundle extras = new Bundle();
            extras.putString(getString(R.string.key_extra_abs_filepath), absPath);
            // Create fragment
            FileFragment fileFragment = new FileFragment();
            fileFragment.setArguments(extras);
            ConstraintLayout fragView = MainActivity.this.findViewById(R.id.file_hint_holder);
            fragView.removeAllViews();
            fragView.startAnimation(fadeIn);
            fragView.setVisibility(View.VISIBLE);
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.file_hint_holder, fileFragment, null)
                    .commitAllowingStateLoss();
        });
    }

    public void closeFileFrag() {
        if (fileFragment != null) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction transaction = fm.beginTransaction();
            transaction.remove(fileFragment).commitAllowingStateLoss();
        }

        // hide holder view
        ConstraintLayout fragHolder = findViewById(R.id.file_hint_holder);
        fragHolder.setVisibility(GONE);
    }

    private void showEmptyLayout() {
        Log.i(TAG, "showEmptyLayout");

        closeBigFrag();
        closeFileFrag();

        mBinding.imgPreview.setVisibility(GONE);
        mBinding.glowingLoader.setVisibility(GONE);
        mBinding.mainSearchBar.setText("");
        mBinding.btnDownload.setVisibility(GONE);
        mBinding.btnDownload.setEnabled(false);
        mBinding.numProgress.setVisibility(GONE);
        mBinding.ivCircle.setVisibility(View.VISIBLE);
        mBinding.btnPaste.setVisibility(View.VISIBLE);
        mBinding.filenameEdittext.setEnabled(false);
        mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInvisible));
        mBinding.filenameEdittext.setText("");
        if (!isBackgroundEnabled) {
            Log.i(TAG, "showing permission holder");
            mBinding.permissionHolder.setVisibility(View.VISIBLE);
        }

    }

    private void showLoadingLayout() {
        Log.i(TAG, "showLoadingLayout()");

        Toast.makeText(this, "Loading… this may take a moment", Toast.LENGTH_LONG).show();

        closeFileFrag();
        mBinding.imgPreview.setVisibility(View.INVISIBLE);
        mBinding.btnDownload.setVisibility(GONE);
        mBinding.btnDownload.setEnabled(false);
        mBinding.numProgress.setVisibility(GONE);
        mBinding.glowingLoader.startAnimation(fadeIn);
        mBinding.glowingLoader.setVisibility(View.VISIBLE);
        mBinding.ivCircle.setVisibility(View.INVISIBLE);
        mBinding.btnPaste.setVisibility(GONE);

        // show interstitial ad
        runOnUiThread(() -> {
            if (!MIsGold) {
                AdsManager.showInterstitialAd(MainActivity.this);
            }
        });
    }

    private void showPreviewLayout() {
        Log.i(TAG, "showPreviewLayout()");

        String thumbnailUrl = prefsManager.getThumbnailUrl();

        updateEditFilenameView(prefsManager.getFileName());
        mBinding.btnPaste.setVisibility(GONE);
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
            mBinding.glowingLoader.setVisibility(GONE);
            mBinding.numProgress.setVisibility(GONE);
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
            mBinding.btnPaste.setVisibility(GONE);
            mBinding.glowingLoader.startAnimation(fadeOut);
            mBinding.glowingLoader.setVisibility(GONE);
            mBinding.imgPreview.setVisibility(GONE);
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

        Toast.makeText(this, "Downloading in background…", Toast.LENGTH_SHORT).show();

        updateFilenamePref();
        mBinding.btnDownload.setEnabled(false);
        mBinding.ivCircle.startAnimation(fadeOut);
        mBinding.btnDownload.startAnimation(fadeOut);
        mBinding.ivCircle.setVisibility(GONE);
        mBinding.btnDownload.setVisibility(GONE);
        mBinding.imgPreview.setAlpha(0.69f);
        mBinding.numProgress.setVisibility(View.VISIBLE);
        mBinding.numProgress.setProgress(0);
        //mBinding.permissionHolder.setVisibility(View.GONE);
        new Handler().postDelayed(() -> {
            mBinding.glowingLoader.startAnimation(fadeIn);
            mBinding.glowingLoader.setVisibility(View.VISIBLE);
            mBinding.filenameEdittext.setEnabled(false);
            mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInvisible));
        }, 200);
    }

    private void showFinishLayout() {
        Log.i(TAG, "showFinishLayout()");
        //mBinding.permissionHolder.setVisibility(View.GONE);
        showFileFrag();

        mBinding.ivCircle.setVisibility(GONE);
        mBinding.glowingLoader.setVisibility(GONE);
        mBinding.imgPreview.setAlpha(1.0f);
        mBinding.btnDownload.setVisibility(GONE);
        mBinding.btnDownload.setEnabled(false);
        mBinding.btnPaste.setVisibility(View.VISIBLE);
        mBinding.btnPaste.setEnabled(true);
        mBinding.ivCircle.setVisibility(View.VISIBLE);
        mBinding.numProgress.setVisibility(GONE);
        mBinding.numProgress.setProgress(0);
        mBinding.mainScroll.smoothScrollTo(0, mBinding.mainScroll.getBottom());
    }

    public void updateFilenamePref() {
        String input = mBinding.filenameEdittext.getText().toString();
        if (input.isEmpty()) {
            prefsManager.setFileName("VIDEO_LOADER_DOWNLOAD");
        } else {
            prefsManager.setFileName(input);
        }

    }

    public void updateEditFilenameView(String fileName) {
        if (fileName.indexOf('.') != -1) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        mBinding.filenameEdittext.setText(fileName);
    }

    private void loadVideoInfo(String url) {
        String titleStr = "", extStr = "", thumbStr ="";

        // run async
        //calling python function with it's object to extract audio
        Python py = Python.getInstance();
        PyObject pyObject = py.getModule("vidloader");

        // extract video information

        try {
            PyObject title = pyObject.callAttr("extract_video_title",
                    url,
                    mResolution.replaceAll("\\D", ""));
            titleStr = title.toString();
            if (titleStr.length() > 25) {
                titleStr = titleStr.substring(0, 25);
            }

            PyObject thumbnail = pyObject.callAttr("extract_video_thumbnail",
                    url,
                    mResolution.replaceAll("\\D", ""));
            thumbStr = thumbnail.toString();
            //PyObject ext = pyObject.callAttr("extract_video_ext", url);
            //extStr = ext.toString();
            extStr = "mp4";
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

        prefsManager.setFileName(titleStr);
        prefsManager.setThumbnailUrl(thumbStr);
        prefsManager.setFileExt(extStr);

        runOnUiThread(this::showPreviewLayout);
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

    /**
     * Respond to clicks on paste button
     * @param v clicked view
     */
    public void onPasteClick(View v) {
        // clear search bar
        mBinding.mainSearchBar.setText("");

        // paste from clipboard
        ClipboardManager clipboardManager = (ClipboardManager) MainActivity.this.getSystemService(CLIPBOARD_SERVICE);
        String primaryStr = "";
        ClipData primaryClip = clipboardManager.getPrimaryClip();
        if (primaryClip != null) {
            primaryStr = primaryClip.getItemAt(0).getText().toString();
            primaryStr = primaryStr.trim();

            mBinding.mainSearchBar.setText(primaryStr);
        } else {
            Toast.makeText(MainActivity.this, "Please copy a video link",
                    Toast.LENGTH_LONG).show();

            mBinding.mainSearchBar.setText(primaryStr);
        }
    }

    /**
     * On Download button clicked
     */
    public void onDownloadClick(View v) {
        Log.i(TAG, ".onDownloadClicked()");

        showDownloadingLayout();

        // start download service
        Intent intent = new Intent(MainActivity.this, DownloadService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            MainActivity.this.startForegroundService(intent);
        } else {
            MainActivity.this.startService(intent);
        }
        bindService(intent, dlServiceConn, Context.BIND_AUTO_CREATE);
    }

    public void onEnableBackgroundClicked(View v) {
        mBinding.permissionHolder.setVisibility(GONE);
        prefsManager.setBackgroundEnabled("TRUE");

        Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        //intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    public void OnEnableNotifClicked(View v) {

        closeBigFrag(mBinding.bigFragHolder);
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[] {Manifest.permission.POST_NOTIFICATIONS},
                1);
    }

    public void showRateAd() {
        Log.i(TAG, "Showing rate ad");

        final String appPackageName = this.getApplicationContext().getPackageName();

        final Dialog dialog = new Dialog(new ContextThemeWrapper(this, R.style.DialogDrip));
        dialog.setTitle(getString(R.string.msg_rate_dialog_title));

        LinearLayout ll = new LinearLayout(MainActivity.this);
        ll.setOrientation(LinearLayout.VERTICAL);

        TextView tv = new TextView(MainActivity.this);
        String msg = getString(R.string.msg_rate_dialog_body);
        tv.setText(msg);
        tv.setWidth(280);
        tv.setPadding(4, 0, 4, 43);
        tv.setTextAppearance(R.style.TextAppFragBody);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        ll.addView(tv);

        LinearLayout l2 = new LinearLayout(MainActivity.this);
        l2.setOrientation(LinearLayout.HORIZONTAL);
        l2.setBottom(ll.getBottom());
        l2.setForegroundGravity(Gravity.BOTTOM);

        Button b3 = new Button(new ContextThemeWrapper(MainActivity.this, R.style.ButtonDripBad));
        b3.setText(getString(R.string.msg_rate_button2));
        b3.setOnClickListener(v -> dialog.dismiss());
        l2.addView(b3);

        Button b1 = new Button(new ContextThemeWrapper(MainActivity.this, R.style.ButtonDripGood));
        b1.setText(getString(R.string.msg_rate_button1));
        b1.setOnClickListener(v -> {
            MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id="
                    + appPackageName)));
            dialog.dismiss();
        });
        l2.addView(b1);

        ll.addView(l2);
        dialog.setContentView(ll);
        if (!MainActivity.this.isFinishing()) {
            try {
                dialog.show();
            } catch (Exception e) {
                Log.w(TAG, "caught bad token exception");
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Log.i(TAG, "onItemSelected position=" + position);
        String spinnerItem = parent.getSelectedItem().toString();
        Log.i(TAG, "spinnerItem=" + spinnerItem);

        int p = position;

        /* check if resolution restricted
        if (position == 0 || position == 1) {
            Log.i(TAG, "gold resolution selected");
            if (MIsGold) {
                Log.i(TAG, "allowing gold resolution");
            } else {
                // show upgrade fragment
                showBigFrag("Upgrade");

                // set spinner item to 1080p
                spinnerItem = "1080p";
                p = 2;
                parent.setSelection(p);
            }
        }
        */

        // update var
        mResolution = spinnerItem;

        // update resolution in shared prefs
        SharedPreferences sharedPref = getSharedPreferences("ULOADER_PREFS", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("RES_POSITION", p);
        editor.apply();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private class FinishReceiver extends BroadcastReceiver {
        private final String TAG = FinishReceiver.class.getCanonicalName();
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive");
            String absFilePath = intent.getStringExtra("FILEPATH");

            // end download service
            if (mDownloadService != null) {
                mDownloadService.stopForeground(true);
                mDownloadService.stopSelf();
            }

            // show error UI if missing filepath
            if (absFilePath == null || absFilePath.isEmpty()) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "unknown error, please try again", Toast.LENGTH_LONG).show();
                    showEmptyLayout();
                });
                return;
            }

            // scan new media
            new MediaManager(MainActivity.this,
                    absFilePath, MIME_MP4).scanMedia();

            // count runs
            prefsManager.incrementTotalRuns();
            int runs = prefsManager.getTotalRuns();

            // show rate dialog ?
            if (runs%3==1) {
                showRateAd();
            }

            // update ui
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Download finished!",
                        Toast.LENGTH_SHORT).show();
                showFinishLayout();
            });

            //unregisterReceiver(this);
        }
    }

    /**
     * Bind client to Service
     */
    protected ServiceConnection dlServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            DownloadService.LocalBinder binder = (DownloadService.LocalBinder) service;
            mDownloadService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected");

        }
    };
}