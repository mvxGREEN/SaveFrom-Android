package com.mvxgreen.ytdloader

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.google.android.ump.ConsentForm.OnConsentFormDismissedListener
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.OnConsentInfoUpdateFailureListener
import com.google.android.ump.ConsentInformation.OnConsentInfoUpdateSuccessListener
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.google.common.collect.ImmutableList
import com.google.firebase.analytics.FirebaseAnalytics
import com.mvxgreen.ytdloader.DownloadService.LocalBinder
import com.mvxgreen.ytdloader.databinding.ActivityMainBinding
import com.mvxgreen.ytdloader.frag.BigFragment
import com.mvxgreen.ytdloader.frag.FileFragment
import com.mvxgreen.ytdloader.manager.AdsManager
import com.mvxgreen.ytdloader.manager.MediaManager
import com.mvxgreen.ytdloader.manager.PrefsManager
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.net.InetAddress
import java.time.LocalDate
import kotlin.math.min

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener,
    AdapterView.OnItemSelectedListener {
    var mDownloadService: DownloadService? = null
    var mBinding: ActivityMainBinding? = null
    var fadeIn: Animation? = null
    var fadeOut: Animation? = null
    var fileFragment: FileFragment? = null

    var prefsManager: PrefsManager? = null
    var mFinishReceiver: FinishReceiver? = null

    var androidPlatform: AndroidPlatform? = null

    var isBackgroundEnabled: Boolean = false

    // admob
    var consentInformation: ConsentInformation? = null

    private var billingClient: BillingClient? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        if (mBinding == null) {
            mBinding = ActivityMainBinding.inflate(getLayoutInflater())
        }
        setContentView(mBinding!!.getRoot())

        // Fixes "strict-mode" error when fetching webpage... idek..
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        prefsManager = PrefsManager(this@MainActivity)
        isBackgroundEnabled = (prefsManager!!.backgroundEnabled)?.isNotEmpty() ?: false
        Log.i(TAG, "isBackgroundEnabled=" + isBackgroundEnabled)
        initMainViews()

        if (!Python.isStarted()) {
            androidPlatform = AndroidPlatform(this)
            Python.start(androidPlatform!!)
        }

        // check permissions
        //hasStoragePermissions();

        // register receivers
        mFinishReceiver = FinishReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mFinishReceiver, IntentFilter("69"), RECEIVER_EXPORTED)
        } else {
            registerReceiver(mFinishReceiver, IntentFilter("69"))
        }

        // init billing
        loadBillingClient()

        // init admob
        loadAdmob()
    }

    override fun onResume() {
        if (billingClient != null) {
            // check purchases
            checkSubscriptionStatus()
        }

        super.onResume()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(mFinishReceiver)
        } catch (ignored: Exception) {
        }
        super.onDestroy()
    }

    fun onYearlyClick(v: View?) {
        Log.i(TAG, "onYearlyClick")
        //launchBillingFlow("yearly");
        launchBillingFlow()
    }

    fun onUpgradeClick(v: View?) {
        onUpgradeClick()
    }

    fun onUpgradeClick() {
        Log.i(TAG, "onUpgradeClick")
        //launchBillingFlow("monthly");
        launchBillingFlow()
    }

    fun onGetInflyerClick(v: View?) {
        // open inflyer in google play
        val playStoreUrl =
            "https://play.google.com/store/apps/details?id=green.mobileapps.downloader4inflact"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(playStoreUrl))
        startActivity(intent)
    }

    fun launchBillingFlow() {
        Log.i(TAG, "launchBillingFlow")
        if (MBillingFlowParams != null) {
            val billingResult =
                billingClient!!.launchBillingFlow(this@MainActivity, MBillingFlowParams!!)
        } else {
            Log.e(TAG, "MBillingFlowParams is null")
        }
    }

    internal inner class MBillingClientListener : BillingClientStateListener {
        override fun onBillingServiceDisconnected() {
            this@MainActivity.establishBillingConnection()
        }

        override fun onBillingSetupFinished(billingResult: BillingResult) {
            Log.i(TAG, "onBillingSetupFinished")
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Billing Response Code == OK")
                // query available products
                val queryProductDetailsParams =
                    QueryProductDetailsParams.newBuilder()
                        .setProductList(
                            ImmutableList.of<QueryProductDetailsParams.Product?>(
                                QueryProductDetailsParams.Product.newBuilder()
                                    .setProductId("savefrom_gold")
                                    .setProductType(BillingClient.ProductType.SUBS)
                                    .build()
                            )
                        )
                        .build()

                billingClient!!.queryProductDetailsAsync(
                    queryProductDetailsParams,
                    object : ProductDetailsResponseListener {
                        override fun onProductDetailsResponse(
                            billingResult: BillingResult,
                            queryProductDetailsResult: QueryProductDetailsResult
                        ) {
                            Log.i(TAG, "onProductDetailsResponse")
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                Log.i(TAG, "Billing Response Code == OK")
                                if (queryProductDetailsResult.getProductDetailsList().size == 0) {
                                    Log.e(TAG, "no products found")
                                }
                                for (productDetails in queryProductDetailsResult.getProductDetailsList()) {
                                    Log.i(TAG, "found product details")

                                    // get product details
                                    val productDetailsParamsList =
                                        ImmutableList.of<ProductDetailsParams?>(
                                            ProductDetailsParams.newBuilder() // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                                                .setProductDetails(productDetails) // Get the offer token:
                                                // a. For one-time products, call ProductDetails.getOneTimePurchaseOfferDetailsList()
                                                // for a list of offers that are available to the user.
                                                // b. For subscriptions, call ProductDetails.subscriptionOfferDetails()
                                                // for a list of offers that are available to the user.
                                                .setOfferToken(
                                                    productDetails.getSubscriptionOfferDetails()!!
                                                        .get(0).getOfferToken()
                                                )
                                                .build()
                                        )

                                    // set billing flow params
                                    MBillingFlowParams = BillingFlowParams.newBuilder()
                                        .setProductDetailsParamsList(productDetailsParamsList)
                                        .build()
                                }

                                for (unfetchedProduct in queryProductDetailsResult.getUnfetchedProductList()) {
                                    // Handle any unfetched products as appropriate.
                                }
                            } else {
                                Log.w(TAG, "Billing Response Code != OK")
                            }
                        }
                    }
                )

                // check purchases
                checkSubscriptionStatus()
            }
        }
    }

    // TODO move to billing manager
    fun loadBillingClient() {
        Log.i(TAG, "loadBillingClient")

        // init billing client
        billingClient = BillingClient.newBuilder(this@MainActivity)
            .setListener(this@MainActivity)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        // connect to billing service
        billingClient!!.startConnection(MBillingClientListener())
    }

    fun establishBillingConnection() {
        // connect to billing service
        billingClient!!.startConnection(MBillingClientListener())
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
            && purchases != null
        ) {
            for (purchase in purchases) {
                val purchaseId = purchase.getProducts().get(0)

                if (purchaseId === "savefrom_gold") {
                    if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
                        Log.w(TAG, "purchase item not purchased")
                    } else if (!purchase.isAcknowledged()) {
                        Log.i(TAG, "purchase is not yet acknowledged")

                        handlePurchase(purchase)
                    }
                }
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "purchase canceled")
        } else {
            Log.i(TAG, "no purchases found")

            // update shared prefs
            val sharedPref = getSharedPreferences("SaveFromPrefs", MODE_PRIVATE)
            val editor = sharedPref.edit()
            editor.putBoolean("IS_GOLD", false)
            editor.apply()

            MIsGold = false

            // update toolbar icon
            this@MainActivity.runOnUiThread(Runnable {
                val toolbar = this@MainActivity.findViewById<Toolbar>(R.id.toolbar)
                val upgradeItem = toolbar.getMenu().findItem(R.id.action_upgrade)
                upgradeItem.setIcon(
                    ContextCompat.getDrawable(
                        this@MainActivity,
                        R.drawable.diamond_24
                    )
                )
            })
        }
    }

    fun checkSubscriptionStatus() {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient!!.queryPurchasesAsync(
            queryPurchasesParams,
            object : PurchasesResponseListener {
                override fun onQueryPurchasesResponse(
                    billingResult: BillingResult,
                    list: MutableList<Purchase>
                ) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        // return if empty
                        if (list.size == 0) {
                            Log.i(TAG, "no purchases found")

                            // update shared prefs
                            val sharedPref = getSharedPreferences("SaveFromPrefs", MODE_PRIVATE)
                            val editor = sharedPref.edit()
                            editor.putBoolean("IS_GOLD", false)
                            editor.apply()

                            MIsGold = false

                            // update ui to gold
                            this@MainActivity.runOnUiThread(Runnable {
                                val toolbar = this@MainActivity.findViewById<Toolbar>(R.id.toolbar)
                                val upgradeItem = toolbar.getMenu().findItem(R.id.action_upgrade)
                                upgradeItem.setIcon(
                                    ContextCompat.getDrawable(
                                        this@MainActivity,
                                        R.drawable.diamond_24
                                    )
                                )
                            })

                            return
                        }

                        // process purchases
                        for (purchase in list) {
                            if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
                                Log.w(TAG, "purchase state is not purchased")
                                return
                            } else if (!purchase.isAcknowledged()) {
                                this@MainActivity.handlePurchase(purchase)
                            } else {
                                // update shared prefs
                                val sharedPref = getSharedPreferences("SaveFromPrefs", MODE_PRIVATE)
                                val editor = sharedPref.edit()
                                editor.putBoolean("IS_GOLD", true)
                                editor.apply()

                                // set global variable
                                MIsGold = true

                                // hide banner ad
                                mBinding!!.bannerContainer.setVisibility(View.GONE)

                                // update toolbar icon
                                this@MainActivity.runOnUiThread(Runnable {
                                    val toolbar =
                                        this@MainActivity.findViewById<Toolbar>(R.id.toolbar)
                                    val upgradeItem =
                                        toolbar.getMenu().findItem(R.id.action_upgrade)
                                    upgradeItem.setIcon(
                                        ContextCompat.getDrawable(
                                            this@MainActivity,
                                            R.drawable.diamond_24_gold
                                        )
                                    )
                                })
                            }
                        }
                    }
                }
            })
    }

    fun handlePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.getPurchaseToken())
            .build()

        billingClient!!.acknowledgePurchase(
            acknowledgePurchaseParams,
            object : AcknowledgePurchaseResponseListener {
                override fun onAcknowledgePurchaseResponse(billingResult: BillingResult) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.i(TAG, "purchase acknowledged")

                        // update shared prefs
                        val sharedPref = getSharedPreferences("SaveFromPrefs", MODE_PRIVATE)
                        val editor = sharedPref.edit()
                        editor.putBoolean("IS_GOLD", true)
                        editor.apply()

                        // set global variable
                        MIsGold = true

                        // hide banner ad
                        mBinding!!.bannerContainer.setVisibility(View.GONE)

                        // update toolbar icon
                        this@MainActivity.runOnUiThread(Runnable {
                            Toast.makeText(
                                this@MainActivity,
                                "Thank you, enjoy! <3",
                                Toast.LENGTH_LONG
                            ).show()
                            val toolbar = this@MainActivity.findViewById<Toolbar>(R.id.toolbar)
                            val upgradeItem = toolbar.getMenu().findItem(R.id.action_upgrade)
                            upgradeItem.setIcon(
                                ContextCompat.getDrawable(
                                    this@MainActivity,
                                    R.drawable.diamond_24_gold
                                )
                            )
                            this@MainActivity.showEmptyLayout()
                        })
                    }
                }
            })
    }

    // TODO move to ads manager
    fun loadAdmob() {
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

        val params = ConsentRequestParameters.Builder() //.setConsentDebugSettings(debugSettings)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        //consentInformation.reset();
        consentInformation!!.requestConsentInfoUpdate(
            this,
            params,
            OnConsentInfoUpdateSuccessListener {
                // load and show the consent form.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    this,
                    OnConsentFormDismissedListener { loadAndShowError: FormError? ->
                        if (loadAndShowError != null) {
                            // Consent gathering failed.
                            Log.w(
                                TAG, String.format(
                                    "%s: %s",
                                    loadAndShowError.getErrorCode(),
                                    loadAndShowError.getMessage()
                                )
                            )
                        }
                        // consent gathered
                        if (consentInformation!!.canRequestAds() &&
                            !MIsGold
                        ) {
                            // initialize admob sdk
                            MobileAds.initialize(
                                this,
                                OnInitializationCompleteListener { initializationStatus: InitializationStatus? -> })

                            // load admob ads
                            mBinding!!.bannerContainer.setVisibility(View.VISIBLE)
                            AdsManager.loadAdmobInterstitialAd(this@MainActivity)
                            AdsManager.loadBanner(this@MainActivity, mBinding!!)
                        }
                        if (this.isPrivacyOptionsRequired) {
                            // Regenerate the options menu to include privacy settings
                            invalidateOptionsMenu()
                        }
                    }
                )
            },
            OnConsentInfoUpdateFailureListener { requestConsentError: FormError? ->
                // Consent gathering failed.
                Log.w(
                    TAG, String.format(
                        "%s: %s",
                        requestConsentError!!.getErrorCode(),
                        requestConsentError.getMessage()
                    )
                )
            })

        // TODO look into using IMA SDK
        // Check if you can initialize the IMA SDK in parallel
        // while checking for new consent information. Consent obtained in
        // the previous session can be used to request ads.
        if (consentInformation!!.canRequestAds() &&
            !MIsGold
        ) {
            // initialize Google Admob SDK
            MobileAds.initialize(
                this,
                OnInitializationCompleteListener { initializationStatus: InitializationStatus? -> })

            // load admob ads
            mBinding!!.bannerContainer.setVisibility(View.VISIBLE)
            AdsManager.loadAdmobInterstitialAd(this@MainActivity)
            AdsManager.loadBanner(this@MainActivity, mBinding!!)
        }
    }

    val isPrivacyOptionsRequired: Boolean
        get() = (consentInformation!!.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED)

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        } else {
            return true
        }
    }

    val isCurrentDateBeforeSpecificDate: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val currentDate = LocalDate.now()

                val specificDate = LocalDate.of(2025, 12, 5)
                return currentDate.isBefore(specificDate)
            } else {
                return true
            }
        }

    private fun initMainViews() {
        initAnimations()
        mBinding!!.mainScroll.setSmoothScrollingEnabled(true)

        // toolbar
        val toolbar = findViewById<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // search bar
        mBinding!!.etMainInput.addTextChangedListener(object : TextWatcher {
            var oldCount: Int = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                oldCount = count
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (count == 0 && oldCount != 0) {
                    // text cleared
                    killKeyboard()
                    showEmptyLayout()
                } else if (count - oldCount > 1) {
                    var input = s.toString()

                    var delay = false
                    if (input.contains("youtube.com") || input.contains("youtu.be")) {
                        // validate date
                        delay = isCurrentDateBeforeSpecificDate
                        val msg = "delay=" + delay
                        Log.i(TAG, msg)
                    }

                    // validate input
                    if (!input.contains("https://") || delay) {
                        // log invalid input and exit
                        try {
                            val bundle = Bundle()
                            bundle.putString("app_name", "savefrom")
                            bundle.putString("input", input)
                            FirebaseAnalytics.getInstance(this@MainActivity)
                                .logEvent("invalid_input", bundle)
                        } catch (ignored: Exception) {
                        }

                        Toast.makeText(
                            this@MainActivity,
                            "Video unavailable, try again later",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    } else if (input.contains("instagram.com")) {
                        showBigFrag("InFlyer")
                        return
                    }

                    // if 'https://' duplicated, trim to second instance
                    if (input.lastIndexOf("https://") != input.indexOf("https://")) {
                        input = input.substring(input.lastIndexOf("https://"))
                    }
                    val inputText = input

                    // log valid input & domain
                    var domain = input.substring(input.indexOf("https://") + 8)
                    if (domain.contains("/")) {
                        domain = domain.substring(0, domain.indexOf("/"))
                    }
                    try {
                        val bundle = Bundle()
                        bundle.putString("app_name", "savefrom")
                        bundle.putString("input", input)
                        bundle.putString("domain", domain)
                        FirebaseAnalytics.getInstance(this@MainActivity)
                            .logEvent("valid_input", bundle)
                    } catch (ignored: Exception) {
                    }

                    // update ui
                    killKeyboard()
                    showLoadingLayout()

                    // check for internet
                    if (isInternetAvailable) {
                        // save input url
                        prefsManager!!.originalUrl = inputText

                        // start loading preview
                        Thread(Runnable {
                            loadVideoInfo(inputText)
                        }).start()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.msg_no_internet),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        // init spinner
        val spinner = findViewById<View?>(R.id.res_spinner) as Spinner
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.res_array,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.setAdapter(adapter)

        // set default resolution
        val sharedPref = getSharedPreferences("SaveFromPrefs", MODE_PRIVATE)
        val selectionIndex = sharedPref.getInt("RES_POSITION", 0)
        spinner.setSelection(selectionIndex)

        // spinner item selected listener
        spinner.setOnItemSelectedListener(this)

        // permission frag
        if (!isBackgroundEnabled) {
            Log.i(TAG, "showing permission holder")
            mBinding!!.permissionHolder.setVisibility(View.VISIBLE)
        }
    }

    private fun initAnimations() {
        // Load entrance animation
        fadeIn = AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in)
        fadeIn!!.setDuration(432)
        fadeIn!!.setInterpolator(AccelerateInterpolator())

        // Load exit animation
        fadeOut = AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out)
        fadeOut!!.setDuration(432)
        fadeOut!!.setInterpolator(AccelerateInterpolator())
    }

    fun killKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(
            findViewById<View?>(R.id.etMainInput).getWindowToken(),
            0
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = getMenuInflater()
        inflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection.
        when (item.getItemId()) {
            else -> return super.onOptionsItemSelected(item)
        }
    }

    fun onUpgradeClick(menuItem: MenuItem) {
        showBigFrag(menuItem)
    }

    // open about page
    fun onAboutClick(menuItem: MenuItem?) {
        val aboutUrl = "https://mobileapps.green/"
        val aboutIntent = Intent(Intent.ACTION_VIEW, Uri.parse(aboutUrl))
        this@MainActivity.startActivity(aboutIntent)
    }

    // open privacy policy page
    fun onPrivacyClick(menuItem: MenuItem?) {
        val privacyUrl = "https://mobileapps.green/privacy-policy"
        val privacyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl))
        this@MainActivity.startActivity(privacyIntent)
    }

    fun onRateClick(menuItem: MenuItem?) {
        val appPackageName = getPackageName() // getPackageName() from Context or Activity object
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + appPackageName)
                )
            )
        } catch (anfe: ActivityNotFoundException) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)
                )
            )
        }
    }

    /**
     * Create fragment corresponding to clicked view
     * @param mi clicked menu item
     * 
     * By Max Green  12/5/2020
     */
    fun showBigFrag(mi: MenuItem) {
        showBigFrag(mi.getTitle().toString())
    }

    fun showBigFrag(title: String?) {
        val extras = Bundle()
        extras.putString(getString(R.string.key_extra_menu_item_title), title)

        // Create fragment, add extras
        val bigFragment = BigFragment()
        bigFragment.setArguments(extras)

        // Inflate fragment
        val fragView = this.findViewById<ConstraintLayout>(R.id.big_frag_holder)
        fragView.removeAllViews()
        fragView.setVisibility(View.VISIBLE)
        getSupportFragmentManager().beginTransaction()
            .setReorderingAllowed(true)
            .add(R.id.big_frag_holder, bigFragment, null)
            .commitAllowingStateLoss()
    }

    /**
     * Hide fragment (holder)
     * @param v close button (clicked)
     */
    fun closeBigFrag(v: View?) {
        closeBigFrag()
    }

    fun closeBigFrag() {
        val fragHolder = findViewById<ConstraintLayout>(R.id.big_frag_holder)
        fragHolder.setVisibility(View.GONE)
    }

    fun showFileFrag() {
        val fileName = prefsManager!!.fileName
        val fileExt = prefsManager!!.fileExt
        val absPath = ABS_PATH_DOCS + fileName + "." + fileExt

        // inflate fragment
        runOnUiThread(Runnable {
            val extras = Bundle()
            extras.putString(getString(R.string.key_extra_abs_filepath), absPath)
            // Create fragment
            val fileFragment = FileFragment()
            fileFragment.setArguments(extras)
            val fragView = this@MainActivity.findViewById<ConstraintLayout>(R.id.file_hint_holder)
            fragView.removeAllViews()
            fragView.startAnimation(fadeIn)
            fragView.setVisibility(View.VISIBLE)
            getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.file_hint_holder, fileFragment, null)
                .commitAllowingStateLoss()
        })
    }

    fun closeFileFrag() {
        if (fileFragment != null) {
            val fm = getSupportFragmentManager()
            val transaction = fm.beginTransaction()
            transaction.remove(fileFragment!!).commitAllowingStateLoss()
        }

        // hide holder view
        val fragHolder = findViewById<ConstraintLayout>(R.id.file_hint_holder)
        fragHolder.setVisibility(View.GONE)
    }

    private fun showEmptyLayout() {
        Log.i(TAG, "showEmptyLayout")

        closeBigFrag()
        closeFileFrag()

        mBinding!!.imgPreview.setVisibility(View.GONE)
        mBinding!!.glowingLoader.setVisibility(View.GONE)
        mBinding!!.etMainInput.setText("")
        mBinding!!.btnDownload.setVisibility(View.GONE)
        mBinding!!.btnDownload.setEnabled(false)
        mBinding!!.numProgress.setVisibility(View.GONE)
        mBinding!!.ivCircle.setVisibility(View.GONE)
        mBinding!!.btnPaste.setVisibility(View.VISIBLE)
        mBinding!!.filenameEdittext.setEnabled(false)
        mBinding!!.filenameEdittext.setHintTextColor(getColor(R.color.shadowInvisible))
        mBinding!!.filenameEdittext.setText("")
        if (!isBackgroundEnabled) {
            Log.i(TAG, "showing permission holder")
            mBinding!!.permissionHolder.setVisibility(View.VISIBLE)
        }
    }

    private fun showLoadingLayout() {
        Log.i(TAG, "showLoadingLayout()")

        Toast.makeText(this, "Loading… this may take a moment", Toast.LENGTH_LONG).show()

        closeFileFrag()
        mBinding!!.imgPreview.setVisibility(View.INVISIBLE)
        mBinding!!.btnDownload.setVisibility(View.GONE)
        mBinding!!.btnDownload.setEnabled(false)
        mBinding!!.numProgress.setVisibility(View.GONE)
        mBinding!!.glowingLoader.startAnimation(fadeIn)
        mBinding!!.glowingLoader.setVisibility(View.VISIBLE)
        mBinding!!.ivCircle.setVisibility(View.INVISIBLE)
        mBinding!!.btnPaste.setVisibility(View.GONE)

        // show interstitial ad
        runOnUiThread(Runnable {
            if (!MIsGold) {
                AdsManager.showInterstitialAd(this@MainActivity)
            }
        })
    }

    private fun showPreviewLayout() {
        Log.i(TAG, "showPreviewLayout()")

        val thumbnailUrl = prefsManager!!.thumbnailUrl

        updateEditFilenameView(prefsManager!!.fileName)
        mBinding!!.btnPaste.setVisibility(View.GONE)
        mBinding!!.imgPreview.setAlpha(1.0f)
        mBinding!!.imgPreview.setVisibility(View.VISIBLE)
        val builder = Picasso.Builder(this@MainActivity)
        builder.listener(object : Picasso.Listener {
            override fun onImageLoadFailed(picasso: Picasso?, uri: Uri?, exception: Exception?) {
                // TODO handle picasso error
            }
        })
        if (thumbnailUrl?.isNotEmpty() == true) {
            builder.build().load(thumbnailUrl)
                .fit()
                .centerCrop()
                .into(mBinding!!.imgPreview, previewCallback)
        }
    }

    private val previewCallback: Callback = object : Callback {
        override fun onSuccess() {
            Log.i(TAG, "onSuccess() decorating preview...")
            mBinding!!.glowingLoader.startAnimation(fadeOut)
            mBinding!!.glowingLoader.setVisibility(View.GONE)
            mBinding!!.numProgress.setVisibility(View.GONE)
            // draw circle
            mBinding!!.ivCircle.setVisibility(View.VISIBLE)
            (mBinding!!.ivCircle.getDrawable() as Animatable).start()
            // ...after circle is drawn
            Handler().postDelayed(Runnable {
                mBinding!!.btnDownload.setVisibility(View.VISIBLE)
                mBinding!!.btnDownload.setEnabled(true)
                mBinding!!.btnDownload.startAnimation(fadeIn)
                mBinding!!.filenameEdittext.setEnabled(true)
                mBinding!!.filenameEdittext.setHintTextColor(getColor(R.color.shadowInverse))
            }, 432)
        }

        override fun onError(e: Exception) {
            Log.e("onPicassoFinished", ".onError()")
            e.printStackTrace()
            mBinding!!.btnPaste.setVisibility(View.GONE)
            mBinding!!.glowingLoader.startAnimation(fadeOut)
            mBinding!!.glowingLoader.setVisibility(View.GONE)
            mBinding!!.imgPreview.setVisibility(View.GONE)
            // draw circle
            mBinding!!.ivCircle.setVisibility(View.VISIBLE)
            (mBinding!!.ivCircle.getDrawable() as Animatable).start()
            // ...after circle is drawn
            Handler().postDelayed(Runnable {
                mBinding!!.btnDownload.setVisibility(View.VISIBLE)
                mBinding!!.btnDownload.setEnabled(true)
                mBinding!!.filenameEdittext.setEnabled(true)
                mBinding!!.filenameEdittext.setHintTextColor(getColor(R.color.shadowInverse))
                mBinding!!.mainScroll.smoothScrollTo(0, mBinding!!.fileHintHolder.getBottom())
            }, 432)
        }
    }

    private fun showDownloadingLayout() {
        Log.i(TAG, "showDownloadingLayout()")

        Toast.makeText(this, "Downloading in background…", Toast.LENGTH_SHORT).show()

        updateFilenamePref()
        mBinding!!.btnDownload.setEnabled(false)
        mBinding!!.ivCircle.startAnimation(fadeOut)
        mBinding!!.btnDownload.startAnimation(fadeOut)
        mBinding!!.ivCircle.setVisibility(View.GONE)
        mBinding!!.btnDownload.setVisibility(View.GONE)
        mBinding!!.imgPreview.setAlpha(0.69f)
        mBinding!!.numProgress.setVisibility(View.VISIBLE)
        mBinding!!.numProgress.setProgress(0)
        //mBinding.permissionHolder.setVisibility(View.GONE);
        Handler().postDelayed(Runnable {
            mBinding!!.glowingLoader.startAnimation(fadeIn)
            mBinding!!.glowingLoader.setVisibility(View.VISIBLE)
            mBinding!!.filenameEdittext.setEnabled(false)
            mBinding!!.filenameEdittext.setHintTextColor(getColor(R.color.shadowInvisible))
        }, 200)
    }

    private fun showFinishLayout() {
        Log.i(TAG, "showFinishLayout()")
        //mBinding.permissionHolder.setVisibility(View.GONE);
        showFileFrag()

        mBinding!!.ivCircle.setVisibility(View.GONE)
        mBinding!!.glowingLoader.setVisibility(View.GONE)
        mBinding!!.imgPreview.setAlpha(1.0f)
        mBinding!!.btnDownload.setVisibility(View.GONE)
        mBinding!!.btnDownload.setEnabled(false)
        mBinding!!.btnPaste.setVisibility(View.VISIBLE)
        mBinding!!.btnPaste.setEnabled(true)
        mBinding!!.ivCircle.setVisibility(View.GONE)
        mBinding!!.numProgress.setVisibility(View.GONE)
        mBinding!!.numProgress.setProgress(0)
        mBinding!!.mainScroll.smoothScrollTo(0, mBinding!!.mainScroll.getBottom())
    }

    fun updateFilenamePref() {
        val input = mBinding!!.filenameEdittext.getText().toString()
        if (input.isEmpty()) {
            prefsManager!!.fileName = "VIDEO_LOADER_DOWNLOAD"
        } else {
            prefsManager!!.fileName = input
        }
    }

    fun updateEditFilenameView(fileName: String?) {
        var fileName = fileName
        if (fileName?.indexOf('.') != -1) {
            fileName = fileName?.substring(0, fileName.lastIndexOf('.'))
        }
        mBinding!!.filenameEdittext.setText(fileName)
    }

    private fun loadVideoInfo(url: String?) {
        var titleStr = ""
        var extStr = ""
        var thumbStr = ""

        // run async
        //calling python function with it's object to extract audio
        val py = Python.getInstance()
        val pyObject = py.getModule("vidloader")

        // extract video information
        try {
            val title = pyObject.callAttr(
                "extract_video_title",
                url,
                mResolution.replace("\\D".toRegex(), "")
            )
            titleStr = title.toString()
            if (titleStr.length > 25) {
                titleStr = titleStr.substring(0, 25)
            }

            val thumbnail = pyObject.callAttr(
                "extract_video_thumbnail",
                url,
                mResolution.replace("\\D".toRegex(), "")
            )
            thumbStr = thumbnail.toString()
            //PyObject ext = pyObject.callAttr("extract_video_ext", url);
            //extStr = ext.toString();
            extStr = "mp4"
        } catch (e: Exception) {
            Log.e(TAG, e.toString())

            runOnUiThread(Runnable {
                Toast.makeText(this@MainActivity, "Unsupported URL", Toast.LENGTH_SHORT).show()
                showEmptyLayout()
            })

            return
        }


        Log.i(
            TAG, ("Extracted video info: "
                    + "filename: " + titleStr + "\n"
                    + "ext: " + extStr + "\n"
                    + "thumbnail url: " + thumbStr)
        )

        prefsManager!!.fileName = titleStr
        prefsManager!!.thumbnailUrl = thumbStr
        prefsManager!!.fileExt = extStr

        runOnUiThread(Runnable { this.showPreviewLayout() })
    }

    /**
     * Respond to clicks on paste button
     * @param v clicked view
     */
    fun onPasteClick(v: View?) {
        // clear search bar
        mBinding!!.etMainInput.setText("")

        // paste from clipboard
        val clipboardManager =
            this@MainActivity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        var primaryStr = ""
        val primaryClip = clipboardManager.getPrimaryClip()
        if (primaryClip != null) {
            primaryStr = primaryClip.getItemAt(0).getText().toString()
            primaryStr = primaryStr.trim { it <= ' ' }

            mBinding!!.etMainInput.setText(primaryStr)
        } else {
            Toast.makeText(
                this@MainActivity, "Please copy a video link",
                Toast.LENGTH_LONG
            ).show()

            mBinding!!.etMainInput.setText(primaryStr)
        }
    }

    /**
     * On Download button clicked
     */
    fun onDownloadClick(v: View?) {
        Log.i(TAG, ".onDownloadClicked()")

        showDownloadingLayout()

        // start download service
        val intent = Intent(this@MainActivity, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this@MainActivity.startForegroundService(intent)
        } else {
            this@MainActivity.startService(intent)
        }
        bindService(intent, dlServiceConn, BIND_AUTO_CREATE)
    }

    fun onEnableBackgroundClicked(v: View?) {
        mBinding!!.permissionHolder.setVisibility(View.GONE)
        prefsManager!!.backgroundEnabled = "TRUE"

        val intent = Intent(Settings.ACTION_SETTINGS)
        intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        //intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()))
        startActivity(intent)
    }

    fun OnEnableNotifClicked(v: View?) {
        closeBigFrag(mBinding!!.bigFragHolder)
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf<String>(Manifest.permission.POST_NOTIFICATIONS),
            1
        )
    }

    fun showRateAd() {
        Log.i(TAG, "Showing rate ad")

        val appPackageName = this.getApplicationContext().getPackageName()

        val dialog = Dialog(ContextThemeWrapper(this, R.style.DialogDrip))
        dialog.setTitle(getString(R.string.msg_rate_dialog_title))

        val ll = LinearLayout(this@MainActivity)
        ll.setOrientation(LinearLayout.VERTICAL)

        val tv = TextView(this@MainActivity)
        val msg = getString(R.string.msg_rate_dialog_body)
        tv.setText(msg)
        tv.setWidth(280)
        tv.setPadding(4, 0, 4, 43)
        tv.setTextAppearance(R.style.TextAppFragBody)
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER)
        ll.addView(tv)

        val l2 = LinearLayout(this@MainActivity)
        l2.setOrientation(LinearLayout.HORIZONTAL)
        l2.setBottom(ll.getBottom())
        l2.setForegroundGravity(Gravity.BOTTOM)

        val b3 = Button(ContextThemeWrapper(this@MainActivity, R.style.ButtonDripBad))
        b3.setText(getString(R.string.msg_rate_button2))
        b3.setOnClickListener(View.OnClickListener { v: View? -> dialog.dismiss() })
        l2.addView(b3)

        val b1 = Button(ContextThemeWrapper(this@MainActivity, R.style.ButtonDripGood))
        b1.setText(getString(R.string.msg_rate_button1))
        b1.setOnClickListener(View.OnClickListener { v: View? ->
            this@MainActivity.startActivity(
                Intent(
                    Intent.ACTION_VIEW, Uri.parse(
                        "market://details?id="
                                + appPackageName
                    )
                )
            )
            dialog.dismiss()
        })
        l2.addView(b1)

        ll.addView(l2)
        dialog.setContentView(ll)
        if (!this@MainActivity.isFinishing()) {
            try {
                dialog.show()
            } catch (e: Exception) {
                Log.w(TAG, "caught bad token exception")
            }
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        Log.i(TAG, "onItemSelected position=" + position)
        val spinnerItem = parent.getSelectedItem().toString()
        Log.i(TAG, "spinnerItem=" + spinnerItem)

        val p = position

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
        mResolution = spinnerItem

        // update resolution in shared prefs
        val sharedPref = getSharedPreferences("ULOADER_PREFS", MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt("RES_POSITION", p)
        editor.apply()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
    }

    inner class FinishReceiver : BroadcastReceiver() {
        private val TAG: String = FinishReceiver::class.java.getCanonicalName()
        override fun onReceive(context: Context?, intent: Intent) {
            Log.i(TAG, "onReceive")
            val absFilePath = intent.getStringExtra("FILEPATH")

            // end download service
            if (mDownloadService != null) {
                mDownloadService!!.stopForeground(true)
                mDownloadService!!.stopSelf()
            }

            // show error UI if missing filepath
            if (absFilePath == null || absFilePath.isEmpty()) {
                runOnUiThread(Runnable {
                    Toast.makeText(
                        this@MainActivity,
                        "unknown error, please try again",
                        Toast.LENGTH_LONG
                    ).show()
                    showEmptyLayout()
                })
                return
            }

            // scan new media
            MediaManager(
                this@MainActivity,
                absFilePath, MediaManager.MIME_MP4
            ).scanMedia()

            // count runs
            prefsManager!!.incrementTotalRuns()
            val runs = prefsManager!!.totalRuns

            // show rate dialog ?
            if (runs % 3 == 1) {
                showRateAd()
            }

            // update ui
            runOnUiThread(Runnable {
                Toast.makeText(
                    this@MainActivity, "Download finished!",
                    Toast.LENGTH_SHORT
                ).show()
                showFinishLayout()
            })

            //unregisterReceiver(this);
        }
    }

    /**
     * Bind client to Service
     */
    protected var dlServiceConn: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "onServiceConnected")
            val binder = service as LocalBinder
            mDownloadService = binder.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "onServiceDisconnected")
        }
    }

    init {
        activityCurrent = this
    }

    companion object {
        private val TAG: String = MainActivity::class.java.getCanonicalName()
        val ABS_PATH_DOCS: String = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS
        )
            .getAbsolutePath() + "/"

        lateinit var activityCurrent: MainActivity
        var mResolution: String = "2160p"

        // billing
        var MIsGold: Boolean = false
        var MBillingFlowParams: BillingFlowParams? = null

        // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~  PERMISSIONS  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        var req_permissions_old: Array<String?> = arrayOf<String?>(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
        var req_permissions: Array<String?> = arrayOf<String?>(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val storagePermissions: Array<String?>?
            get() {
                val p: Array<String?>?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    p = req_permissions
                } else {
                    p = req_permissions_old
                }
                return p
            }

        val isInternetAvailable: Boolean
            /**
             * Check if device is connected to internet
             * @return is device connected to internet?
             */
            get() {
                try {
                    val ipAddr = InetAddress.getByName("google.com")
                    return !ipAddr.toString().isEmpty()
                } catch (e: Exception) {
                    return false
                }
            }

        @JvmStatic
        fun setProgress(progressStr: String) {
            var progressStr = progressStr
            Log.i(TAG, "setProgress: " + progressStr)
            progressStr = progressStr.trim { it <= ' ' }.replace("%", "")
            val progress = progressStr.toDouble().toInt()

            // update activity ui
            activityCurrent.runOnUiThread(Runnable {
                activityCurrent.mBinding!!.numProgress.setVisibility(View.VISIBLE)
                activityCurrent.mBinding!!.numProgress.setProgress(progress)
            })

            // update notification
            activityCurrent.mDownloadService!!.setProgress(100, progress)
        }

        fun printLongLog(l: String) {
            val maxLogSize = 1000
            for (i in 0..l.length / maxLogSize) {
                val start = i * maxLogSize
                var end = (i + 1) * maxLogSize
                end = min(end, l.length)
                Log.i(TAG, l.substring(start, end))
            }
        }
    }
}