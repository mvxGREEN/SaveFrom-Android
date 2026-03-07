package com.mvxgreen.ytdloader

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
//import androidx.activity.EdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.billingclient.api.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.common.collect.ImmutableList
import com.google.firebase.analytics.FirebaseAnalytics
import com.mvxgreen.ytdloader.databinding.ActivityMainBinding
import com.mvxgreen.ytdloader.frag.BigFragment
import com.mvxgreen.ytdloader.frag.FileFragment
import com.mvxgreen.ytdloader.manager.AdsManager
import com.mvxgreen.ytdloader.manager.MediaManager
import com.mvxgreen.ytdloader.manager.MediaManager.Companion.MIME_MP4
import com.mvxgreen.ytdloader.manager.PrefsManager
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.net.InetAddress
import java.time.LocalDate
import androidx.core.net.toUri
import com.google.firebase.FirebaseApp
import com.mvxgreen.ytdloader.databinding.DialogCutterBinding
import com.mvxgreen.ytdloader.databinding.DialogRateBinding
import com.mvxgreen.ytdloader.databinding.DialogUpgradeBinding
import com.mvxgreen.ytdloader.databinding.DialogVideBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener, AdapterView.OnItemSelectedListener {
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var mDownloadService: DownloadService? = null
    lateinit var mBinding: ActivityMainBinding
    private lateinit var fadeIn: Animation
    private lateinit var fadeOut: Animation
    private var fileFragment: FileFragment? = null

    private lateinit var prefsManager: PrefsManager
    private lateinit var mFinishReceiver: FinishReceiver

    private lateinit var androidPlatform: AndroidPlatform

    private var isBackgroundEnabled = false
    private val mediaPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO) // Removed READ_MEDIA_AUDIO
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun checkPermissions() {
        val needed = mediaPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (!needed.isEmpty()) {
            Log.w(TAG, "missing permissions: $needed")
            requestStoragePermissionLauncher.launch(needed.toTypedArray())
        } else {
            prepareFileDirs()
        }
    }

    private val requestStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                Log.i(TAG, "All media permissions granted")
                prepareFileDirs() // Ensure directories exist once permissions are granted
            } else {
                Log.w(TAG, "Some media permissions were denied")
                Toast.makeText(this, "Storage permission is required to save downloads.", Toast.LENGTH_LONG).show()
            }
        }

    // admob
    private lateinit var consentInformation: ConsentInformation

    // billing
    private var billingClient: BillingClient? = null

    companion object {
        private val TAG = MainActivity::class.java.canonicalName

        @JvmField
        val ABS_PATH_TEMP: String = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS
        ).absolutePath + "/SaveFrom/"
        val ABS_PATH_MOVIES: String = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES
        ).absolutePath + "/SaveFrom/"

        @SuppressLint("StaticFieldLeak")
        @JvmField
        var activityCurrent: MainActivity? = null

        @JvmField
        var mResolution = "2160p"

        @JvmField
        var MIsGold = false

        @JvmField
        var MBillingFlowParams: BillingFlowParams? = null

        val req_permissions_old = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
        val req_permissions = arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )

        @JvmStatic
        fun getStoragePermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                req_permissions
            } else {
                req_permissions_old
            }
        }

        @JvmStatic
        fun isInternetAvailable(): Boolean {
            return try {
                val ipAddr = InetAddress.getByName("google.com")
                ipAddr.toString().isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }

        @JvmStatic
        fun setProgress(progressStr: String) {
            val cleanProgressStr = progressStr.trim().replace("%", "")
            val progress = cleanProgressStr.toDouble().toInt()

            activityCurrent?.runOnUiThread {
                activityCurrent?.mBinding?.numProgress?.visibility = View.VISIBLE
                activityCurrent?.mBinding?.numProgress?.progress = progress
            }

            activityCurrent?.mDownloadService?.setProgress(100, progress)
        }

        @JvmStatic
        fun printLongLog(l: String) {
            val maxLogSize = 1000
            for (i in 0..l.length / maxLogSize) {
                val start = i * maxLogSize
                var end = (i + 1) * maxLogSize
                end = end.coerceAtMost(l.length)
                Log.i(TAG, l.substring(start, end))
            }
        }
    }

    init {
        activityCurrent = this
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //EdgeToEdge.enable(this)
        FirebaseApp.initializeApp(this)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        // Fixes "strict-mode" error when fetching webpage
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        prefsManager = PrefsManager(this@MainActivity)
        isBackgroundEnabled = prefsManager.backgroundEnabled?.isNotEmpty() ?: false
        Log.i(TAG, "isBackgroundEnabled=$isBackgroundEnabled")

        initMainViews()

        if (!Python.isStarted()) {
            androidPlatform = AndroidPlatform(this)
            Python.start(androidPlatform)
        }

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

        checkPermissions()
    }

    override fun onResume() {
        if (billingClient != null) {
            checkSubscriptionStatus()
        }
        if (!Python.isStarted()) {
            androidPlatform = AndroidPlatform(this)
            Python.start(androidPlatform)
        }
        super.onResume()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(mFinishReceiver)
        } catch (ignored: Exception) {}
        super.onDestroy()
    }

    fun prepareFileDirs() {
        val tempDir = File(ABS_PATH_TEMP)
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val moviesDir = File(ABS_PATH_MOVIES)
        if (!moviesDir.exists()) {
            moviesDir.mkdirs()
        }
    }

    fun onYearlyClick(v: View?) {
        Log.i(TAG, "onYearlyClick")
        launchBillingFlow()
    }

    fun onUpgradeClick(v: View?) {
        onUpgradeClick()
    }

    fun onUpgradeClick() {
        Log.i(TAG, "onUpgradeClick")
        launchBillingFlow()
    }

    fun onGetInflyerClick(v: View?) {
        val playStoreUrl = "https://play.google.com/store/apps/details?id=green.mobileapps.downloader4inflact"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(playStoreUrl)
        startActivity(intent)
    }

    fun launchBillingFlow() {
        Log.i(TAG, "launchBillingFlow")
        MBillingFlowParams?.let { params ->
            billingClient?.launchBillingFlow(this@MainActivity, params)
        } ?: Log.e(TAG, "MBillingFlowParams is null")
    }

    inner class MBillingClientListener : BillingClientStateListener {
        override fun onBillingServiceDisconnected() {
            this@MainActivity.establishBillingConnection()
        }

        override fun onBillingSetupFinished(billingResult: BillingResult) {
            Log.i(TAG, "onBillingSetupFinished")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Billing Response Code == OK")

                val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        ImmutableList.of(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId("savefrom_gold")
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build()
                        )
                    )
                    .build()

                billingClient?.queryProductDetailsAsync(queryProductDetailsParams) { result, queryProductDetailsResult ->
                    Log.i(TAG, "onProductDetailsResponse")
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.i(TAG, "Billing Response Code == OK")
                        if (queryProductDetailsResult.productDetailsList?.isEmpty() == true) {
                            Log.e(TAG, "no products found")
                        }
                        queryProductDetailsResult.productDetailsList?.forEach { productDetails ->
                            Log.i(TAG, "found product details")

                            val productDetailsParamsList = ImmutableList.of(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .setOfferToken(productDetails.subscriptionOfferDetails?.get(0)?.offerToken ?: "")
                                    .build()
                            )

                            MBillingFlowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(productDetailsParamsList)
                                .build()
                        }
                    } else {
                        Log.w(TAG, "Billing Response Code != OK")
                    }
                }

                checkSubscriptionStatus()
            }
        }
    }

    fun loadBillingClient() {
        Log.i(TAG, "loadBillingClient")
        billingClient = BillingClient.newBuilder(this@MainActivity)
            .setListener(this@MainActivity)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()

        billingClient?.startConnection(MBillingClientListener())
    }

    fun establishBillingConnection() {
        billingClient?.startConnection(MBillingClientListener())
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                val purchaseId = purchase.products[0]
                if (purchaseId == "savefrom_gold") {
                    if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
                        Log.w(TAG, "purchase item not purchased")
                    } else if (!purchase.isAcknowledged) {
                        Log.i(TAG, "purchase is not yet acknowledged")
                        handlePurchase(purchase)
                    }
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "purchase canceled")
        } else {
            Log.i(TAG, "no purchases found")
            val sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("IS_GOLD", false).apply()
            MIsGold = false

            runOnUiThread {
                val toolbar = findViewById<Toolbar>(R.id.toolbar)
                val upgradeItem = toolbar.menu.findItem(R.id.action_upgrade)
                upgradeItem?.icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.diamond_24)
            }
        }
    }

    fun checkSubscriptionStatus() {
        val queryPurchasesParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient?.queryPurchasesAsync(queryPurchasesParams) { billingResult, list ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (list.isEmpty()) {
                    Log.i(TAG, "no purchases found")
                    val sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE)
                    sharedPref.edit().putBoolean("IS_GOLD", false).apply()
                    MIsGold = false

                    runOnUiThread {
                        val toolbar = findViewById<Toolbar>(R.id.toolbar)
                        val upgradeItem = toolbar.menu.findItem(R.id.action_upgrade)
                        upgradeItem?.icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.diamond_24)
                    }
                    return@queryPurchasesAsync
                }

                for (purchase in list) {
                    if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
                        Log.w(TAG, "purchase state is not purchased")
                        return@queryPurchasesAsync
                    } else if (!purchase.isAcknowledged) {
                        handlePurchase(purchase)
                    } else {
                        val sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE)
                        sharedPref.edit().putBoolean("IS_GOLD", true).apply()
                        MIsGold = true

                        mBinding.bannerContainer.visibility = View.GONE

                        runOnUiThread {
                            val toolbar = findViewById<Toolbar>(R.id.toolbar)
                            val upgradeItem = toolbar.menu.findItem(R.id.action_upgrade)
                            upgradeItem?.icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.diamond_24_gold)
                        }
                    }
                }
            }
        }
    }

    fun handlePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "purchase acknowledged")
                val sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE)
                sharedPref.edit().putBoolean("IS_GOLD", true).apply()
                MIsGold = true
                mBinding.bannerContainer.visibility = View.GONE

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Thank you, enjoy! <3", Toast.LENGTH_LONG).show()
                    val toolbar = findViewById<Toolbar>(R.id.toolbar)
                    val upgradeItem = toolbar.menu.findItem(R.id.action_upgrade)
                    upgradeItem?.icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.diamond_24_gold)
                    showEmptyLayout()
                }
            }
        }
    }

    fun loadAdmob() {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { loadAndShowError ->
                    if (loadAndShowError != null) {
                        Log.w(TAG, "${loadAndShowError.errorCode}: ${loadAndShowError.message}")
                    }

                    if (consentInformation.canRequestAds() && !MIsGold) {
                        MobileAds.initialize(this) {}
                        mBinding.bannerContainer.visibility = View.VISIBLE
                        AdsManager.loadAdmobInterstitialAd(this@MainActivity)
                        AdsManager.loadBanner(this@MainActivity, mBinding)
                    }

                    if (isPrivacyOptionsRequired()) {
                        invalidateOptionsMenu()
                    }
                }
            },
            { requestConsentError ->
                Log.w(TAG, "${requestConsentError.errorCode}: ${requestConsentError.message}")
            }
        )

        if (consentInformation.canRequestAds() && !MIsGold) {
            MobileAds.initialize(this) {}
            mBinding.bannerContainer.visibility = View.VISIBLE
            AdsManager.loadAdmobInterstitialAd(this@MainActivity)
            AdsManager.loadBanner(this@MainActivity, mBinding)
        }
    }

    fun isPrivacyOptionsRequired(): Boolean {
        return consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            false
        } else {
            true
        }
    }

    fun isCurrentDateBeforeSpecificDate(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val currentDate = LocalDate.now()
            val specificDate = LocalDate.of(2025, 12, 5)
            currentDate.isBefore(specificDate)
        } else {
            true
        }
    }

    private fun initMainViews() {
        initAnimations()
        mBinding.mainScroll.isSmoothScrollingEnabled = true

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        mBinding.etMainInput.addTextChangedListener(object : TextWatcher {
            var oldCount = 0

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                oldCount = count
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (count == 0 && oldCount != 0) {
                    killKeyboard()
                    showEmptyLayout()
                } else if (count - oldCount > 1) {
                    var input = s.toString()
                    var delay = false

                    if (input.contains("youtube.com") || input.contains("youtu.be")) {
                        delay = isCurrentDateBeforeSpecificDate()
                        Log.i(TAG, "delay=$delay")
                    }

                    if (!input.contains("https://") || delay) {
                        try {
                            val bundle = Bundle().apply {
                                putString("app_name", "savefrom")
                                putString("input", input)
                            }
                            FirebaseAnalytics.getInstance(this@MainActivity).logEvent("invalid_input", bundle)
                        } catch (ignored: Exception) {}

                        Toast.makeText(this@MainActivity, "Video unavailable, try again later", Toast.LENGTH_SHORT).show()
                        return
                    } else if (input.contains("instagram.com")) {
                        showBigFrag("InFlyer")
                        return
                    }

                    if (input.lastIndexOf("https://") != input.indexOf("https://")) {
                        input = input.substring(input.lastIndexOf("https://"))
                    }
                    val inputText = input

                    var domain = input.substring(input.indexOf("https://") + 8)
                    if (domain.contains("/")) {
                        domain = domain.substring(0, domain.indexOf("/"))
                    }
                    try {
                        val bundle = Bundle().apply {
                            putString("app_name", "savefrom")
                            putString("input", input)
                            putString("domain", domain)
                        }
                        FirebaseAnalytics.getInstance(this@MainActivity).logEvent("valid_input", bundle)
                    } catch (ignored: Exception) {}

                    killKeyboard()
                    showLoadingLayout()

                    if (isInternetAvailable()) {
                        prefsManager.originalUrl = inputText
                        Thread { loadVideoInfo(inputText) }.start()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.msg_no_internet), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun afterTextChanged(s: Editable) {
                mBinding.btnClear.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
            }
        })

        mBinding.btnClear.setOnClickListener {
            mBinding.etMainInput.setText("")
            showEmptyLayout()
        }

        val spinner = findViewById<Spinner>(R.id.res_spinner)

        val resArray = resources.getStringArray(R.array.res_array)

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, resArray) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)

                // 3. Alternate the background based on odd/even positions
                if (position % 2 == 0) {
                    view.setBackgroundResource(R.drawable.bg_spinner_item)
                } else {
                    view.setBackgroundResource(R.drawable.bg_spinner_item_alt)
                }

                return view
            }
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE)
        val selectionIndex = sharedPref.getInt("RES_POSITION", 0)
        spinner.setSelection(selectionIndex)
        spinner.onItemSelectedListener = this

        if (!isBackgroundEnabled) {
            Log.i(TAG, "showing permission holder")
            mBinding.permissionHolder.visibility = View.VISIBLE
        }
    }

    private fun initAnimations() {
        fadeIn = AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_in).apply {
            duration = 432
            interpolator = AccelerateInterpolator()
        }
        fadeOut = AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out).apply {
            duration = 432
            interpolator = AccelerateInterpolator()
        }
    }

    fun killKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(findViewById<View>(R.id.etMainInput).windowToken, 0)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    fun onUpgradeClick(menuItem: MenuItem) {
        showUpgradeDialog()
    }

    fun onAboutClick(menuItem: MenuItem) {
        val aboutUrl = "https://mobileapps.green/"
        val aboutIntent = Intent(Intent.ACTION_VIEW, aboutUrl.toUri())
        startActivity(aboutIntent)
    }

    fun onPrivacyClick(menuItem: MenuItem) {
        val privacyUrl = "https://mobileapps.green/privacy-policy"
        val privacyIntent = Intent(Intent.ACTION_VIEW, privacyUrl.toUri())
        startActivity(privacyIntent)
    }

    fun onRateClick(menuItem: MenuItem) {
        val appPackageName = packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$appPackageName".toUri()))
        } catch (anfe: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$appPackageName".toUri()))
        }
    }

    fun showBigFrag(mi: MenuItem) {
        showBigFrag(mi.title.toString())
    }

    fun showBigFrag(title: String) {
        val extras = Bundle().apply {
            putString(getString(R.string.key_extra_menu_item_title), title)
        }

        val bigFragment = BigFragment().apply { arguments = extras }

        val fragView = findViewById<ConstraintLayout>(R.id.big_frag_holder)
        fragView.removeAllViews()
        fragView.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(R.id.big_frag_holder, bigFragment, null)
            .commitAllowingStateLoss()
    }

    fun closeBigFrag(v: View?) {
        closeBigFrag()
    }

    fun closeBigFrag() {
        val fragHolder = findViewById<ConstraintLayout>(R.id.big_frag_holder)
        fragHolder.visibility = View.GONE
    }

    fun showFileFrag() {
        val fileName = prefsManager.fileName
        val fileExt = prefsManager.fileExt
        val absPath = "$ABS_PATH_MOVIES$fileName.$fileExt"

        runOnUiThread {
            val extras = Bundle().apply {
                putString(getString(R.string.key_extra_abs_filepath), absPath)
            }
            fileFragment = FileFragment().apply { arguments = extras }

            val fragView = findViewById<ConstraintLayout>(R.id.file_hint_holder)
            fragView.removeAllViews()
            fragView.startAnimation(fadeIn)
            fragView.visibility = View.VISIBLE

            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.file_hint_holder, fileFragment!!, null)
                .commitAllowingStateLoss()
        }
    }

    fun closeFileFrag() {
        fileFragment?.let {
            supportFragmentManager.beginTransaction()
                .remove(it)
                .commitAllowingStateLoss()
        }
        val fragHolder = findViewById<ConstraintLayout>(R.id.file_hint_holder)
        fragHolder.visibility = View.GONE
    }

    private fun showEmptyLayout() {
        Log.i(TAG, "showEmptyLayout")

        closeBigFrag()
        closeFileFrag()

        mBinding.imgPreview.visibility = View.GONE
        mBinding.glowingLoader.visibility = View.GONE
        mBinding.etMainInput.setText("")
        mBinding.btnDownload.visibility = View.GONE
        mBinding.btnDownload.isEnabled = false
        mBinding.numProgress.visibility = View.GONE
        mBinding.ivCircle.visibility = View.GONE
        mBinding.filenameEdittext.isEnabled = false
        mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInvisible))
        mBinding.filenameEdittext.setText("")
        mBinding.btnPaste.isEnabled = true

        if (!isBackgroundEnabled) {
            Log.i(TAG, "showing permission holder")
            mBinding.permissionHolder.visibility = View.VISIBLE
        }
    }

    private fun showLoadingLayout() {
        Log.i(TAG, "showLoadingLayout()")
        Toast.makeText(this, "Loading… this may take a moment", Toast.LENGTH_LONG).show()

        closeFileFrag()
        mBinding.imgPreview.visibility = View.INVISIBLE
        mBinding.btnDownload.visibility = View.GONE
        mBinding.btnDownload.isEnabled = false
        mBinding.numProgress.visibility = View.GONE
        mBinding.glowingLoader.startAnimation(fadeIn)
        mBinding.glowingLoader.visibility = View.VISIBLE
        mBinding.ivCircle.visibility = View.GONE
        mBinding.btnPaste.isEnabled = false

        runOnUiThread {
            if (!MIsGold) {
                AdsManager.showInterstitialAd(this@MainActivity)
            }
        }
    }

    private fun showPreviewLayout() {
        Log.i(TAG, "showPreviewLayout()")
        val thumbnailUrl = prefsManager.thumbnailUrl

        updateEditFilenameView(prefsManager.fileName!!)
        mBinding.imgPreview.alpha = 1.0f
        mBinding.imgPreview.visibility = View.VISIBLE
        mBinding.btnPaste.isEnabled = true

        val builder = Picasso.Builder(this@MainActivity)
        builder.listener { _, _, _ ->
            // TODO handle picasso error
        }

        if (thumbnailUrl?.isNotEmpty() ?: false) {
            builder.build().load(thumbnailUrl)
                .fit()
                .centerCrop()
                .into(mBinding.imgPreview, previewCallback)
        }
    }

    private val previewCallback = object : Callback {
        override fun onSuccess() {
            Log.i(TAG, "onSuccess() decorating preview...")
            mBinding.glowingLoader.startAnimation(fadeOut)
            mBinding.glowingLoader.visibility = View.GONE
            mBinding.numProgress.visibility = View.GONE
            mBinding.ivCircle.visibility = View.VISIBLE
            (mBinding.ivCircle.drawable as Animatable).start()

            Handler(Looper.getMainLooper()).postDelayed({
                mBinding.btnDownload.visibility = View.VISIBLE
                mBinding.btnDownload.isEnabled = true
                mBinding.btnDownload.startAnimation(fadeIn)
                mBinding.filenameEdittext.isEnabled = true
                mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInverse))
            }, 432)
        }

        override fun onError(e: Exception) {
            Log.e("onPicassoFinished", ".onError()")
            e.printStackTrace()
            mBinding.glowingLoader.startAnimation(fadeOut)
            mBinding.glowingLoader.visibility = View.GONE
            mBinding.imgPreview.visibility = View.GONE
            mBinding.ivCircle.visibility = View.GONE
            //(mBinding.ivCircle.drawable as Animatable).start()

            Handler(Looper.getMainLooper()).postDelayed({
                mBinding.btnDownload.visibility = View.VISIBLE
                mBinding.btnDownload.isEnabled = true
                mBinding.filenameEdittext.isEnabled = true
                mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInverse))
                mBinding.mainScroll.smoothScrollTo(0, mBinding.fileHintHolder.bottom)
            }, 432)
        }
    }

    private fun showDownloadingLayout() {
        Log.i(TAG, "showDownloadingLayout()")
        Toast.makeText(this, "Downloading in background…", Toast.LENGTH_SHORT).show()

        updateFilenamePref()
        mBinding.btnDownload.isEnabled = false
        mBinding.btnDownload.startAnimation(fadeOut)
        mBinding.ivCircle.visibility = View.GONE
        mBinding.btnDownload.visibility = View.GONE
        mBinding.imgPreview.alpha = 0.69f
        mBinding.numProgress.visibility = View.VISIBLE
        mBinding.numProgress.progress = 0
        mBinding.btnPaste.isEnabled = false

        Handler(Looper.getMainLooper()).postDelayed({
            mBinding.glowingLoader.startAnimation(fadeIn)
            mBinding.glowingLoader.visibility = View.VISIBLE
            mBinding.filenameEdittext.isEnabled = false
            mBinding.filenameEdittext.setHintTextColor(getColor(R.color.shadowInvisible))
        }, 200)
    }

    private fun showFinishLayout() {
        Log.i(TAG, "showFinishLayout()")
        showFileFrag()

        mBinding.glowingLoader.visibility = View.GONE
        mBinding.imgPreview.alpha = 1.0f
        mBinding.btnDownload.visibility = View.GONE
        mBinding.btnDownload.isEnabled = false
        mBinding.btnPaste.isEnabled = true
        mBinding.ivCircle.visibility = View.GONE
        mBinding.numProgress.visibility = View.GONE
        mBinding.numProgress.progress = 0
        mBinding.mainScroll.smoothScrollTo(0, mBinding.mainScroll.bottom)
    }

    fun updateFilenamePref() {
        val input = mBinding.filenameEdittext.text.toString()
        if (input.isEmpty()) {
            prefsManager.fileName = "VIDEO_LOADER_DOWNLOAD"
        } else {
            prefsManager.fileName = input
        }
    }

    fun updateEditFilenameView(fileName: String) {
        var name = fileName
        if (name.indexOf('.') != -1) {
            name = name.substring(0, name.lastIndexOf('.'))
        }
        mBinding.filenameEdittext.setText(name)
    }

    private fun loadVideoInfo(url: String) {
        var titleStr = ""
        var extStr = ""
        var thumbStr = ""

        val py = Python.getInstance()
        val pyObject = py.getModule("vidloader")

        try {
            val res = pyObject.callAttr("extract_video_title_thumbnail", url, mResolution.replace("\\D".toRegex(), "")).toString()
            titleStr = res.substringBeforeLast("|||")

            // trim title to 25 characters
            if (titleStr.length > 25) {
                titleStr = titleStr.substring(0, 25)
            }

            thumbStr = res.substringAfter("|||")
            thumbStr = thumbStr.replace("|", "")

            extStr = "mp4"
        } catch (e: Exception) {
            Log.e(TAG, e.toString())

            runOnUiThread {
                Toast.makeText(this@MainActivity, "error loading, please try again", Toast.LENGTH_SHORT).show()
                showEmptyLayout()
            }
            return
        }

        Log.i(TAG, "Extracted video info: filename: $titleStr\next: $extStr\nthumbnail url: $thumbStr")

        prefsManager.fileName = titleStr
        prefsManager.thumbnailUrl = thumbStr
        prefsManager.fileExt = extStr

        runOnUiThread { showPreviewLayout() }
    }

    fun onPasteClick(v: View?) {
        mBinding.etMainInput.setText("")
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        var primaryStr = ""
        val primaryClip = clipboardManager.primaryClip

        if (primaryClip != null && primaryClip.itemCount > 0) {
            primaryStr = primaryClip.getItemAt(0).text.toString().trim()
            mBinding.etMainInput.setText(primaryStr)
        } else {
            Toast.makeText(this@MainActivity, "Please copy a video link", Toast.LENGTH_LONG).show()
            mBinding.etMainInput.setText(primaryStr)
        }
    }

    fun onDownloadClick(v: View?) {
        Log.i(TAG, ".onDownloadClicked()")
        showDownloadingLayout()

        val intent = Intent(this@MainActivity, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, dlServiceConn, Context.BIND_AUTO_CREATE)
    }

    fun onEnableBackgroundClicked(v: View?) {
        mBinding.permissionHolder.visibility = View.GONE
        prefsManager.backgroundEnabled = "TRUE"

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    fun OnEnableNotifClicked(v: View?) {
        closeBigFrag(mBinding.bigFragHolder)
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            1
        )
    }

    private fun incrementSuccessfulRuns() {
        Log.i(TAG, "incrementSuccessfulRuns()")
        val prefs = getSharedPreferences("green.mobileapps.savefrom.prefs", Context.MODE_PRIVATE)
        val currentCount = prefs.getInt("SUCCESS_RUNS", 0) + 1
        prefs.edit().putInt("SUCCESS_RUNS", currentCount).apply()
        Log.i(TAG, "successful runs count: $currentCount")
        if (currentCount > 0) {
            Log.i(TAG, "showing a dialog: $currentCount")
            // skip if gold
            if(MIsGold) {
                Log.i("MainActivity", "is gold, skipping dialog")
            } else {
                if (currentCount % 4 == 1) {
                    showUpgradeDialog()
                } else if (currentCount % 4 == 2) {
                    showVideDialog()
                } else if (currentCount % 4 == 3) {
                    showRateDialog()
                } else {
                    showCutterDialog()
                }
            }
        }
    }

    private fun showVideDialog() {
        logEvent("sf_vide_dialog", "", "")
        val musiBinding = DialogVideBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(musiBinding.root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        musiBinding.btnNah.setOnClickListener { dialog.dismiss() }
        musiBinding.btnGetMusi.setOnClickListener {
            logEvent("vf_get_vide", "", "")
            dialog.dismiss()
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=green.mobileapps.offlinevideoplayer"))) }
            catch (e: ActivityNotFoundException) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=green.mobileapps.offlinevideoplayer"))) }
        }
        dialog.show()
    }

    private fun showCutterDialog() {
        logEvent("sf_cutter_dialog", "", "")
        val taggerBinding = DialogCutterBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(taggerBinding.root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        taggerBinding.btnNah.setOnClickListener { dialog.dismiss() }
        taggerBinding.btnGetTagger.setOnClickListener {
            logEvent("sf_get_cutter", "", "")
            dialog.dismiss()
            try { startActivity(Intent(Intent.ACTION_VIEW,
                "market://details?id=green.mobileapps.clippervideocutter".toUri())) }
            catch (e: ActivityNotFoundException) { startActivity(Intent(Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=green.mobileapps.clippervideocutter".toUri())) }
        }
        dialog.show()
    }

    private fun showRateDialog() {
        logEvent("sf_rate_dialog", "", "")
        val rateBinding = DialogRateBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(rateBinding.root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        rateBinding.btnNah.setOnClickListener { dialog.dismiss() }
        rateBinding.btnRate.setOnClickListener {
            logEvent("sf_rate_click", "", "")
            dialog.dismiss()
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }
            catch (e: ActivityNotFoundException) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))) }
        }
        dialog.show()
    }

    private fun showUpgradeDialog() {
        logEvent("sf_upgrade_show", "", "")
        val dialogBinding = DialogUpgradeBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogBinding.btnNah.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnUpgrade.setOnClickListener {
            logEvent("sf_upgrade_click", "", "")
            dialog.dismiss()
            launchBillingFlow()
        }
        dialog.show()
    }

    private fun logEvent(eventName: String, input_url: String?, more: String?) {
        val bundle = Bundle()
        if (input_url != null) bundle.putString("input_url", input_url)
        if (more != null) bundle.putString("more", more)
        firebaseAnalytics.logEvent(eventName, bundle)
        Log.d("Analytics", "Logged event: $eventName")
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
        Log.i(TAG, "onItemSelected position=$position")
        val spinnerItem = parent.selectedItem.toString()
        Log.i(TAG, "spinnerItem=$spinnerItem")

        mResolution = spinnerItem

        val sharedPref = getSharedPreferences("ULOADER_PREFS", Context.MODE_PRIVATE)
        sharedPref.edit().putInt("RES_POSITION", position).apply()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    private inner class FinishReceiver : BroadcastReceiver() {
        private val TAG = FinishReceiver::class.java.canonicalName

        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive")
            val absFilePath = intent.getStringExtra("FILEPATH")
            var savedFilePath = absFilePath
            var savedUriStr = ""

            mDownloadService?.let {
                it.stopForeground(true)
                it.stopSelf()
            }

            if (absFilePath.isNullOrEmpty()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "unknown error, please try again", Toast.LENGTH_LONG).show()
                    showEmptyLayout()
                }
                return
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        savedUriStr = moveFileToMovies(absFilePath)
                        // TODO use for share button
                    } catch (e: Exception) {
                        Log.e(TAG, "Move failed: ${e.message}")
                    }

                    deleteTempFiles()

                    //var moviesFilepath = absFilePath
                    if (absFilePath.contains(ABS_PATH_TEMP))
                        savedFilePath = absFilePath.replace(ABS_PATH_TEMP, ABS_PATH_MOVIES)
                    MediaManager(this@MainActivity, absFilePath, MIME_MP4).scanMedia()

                    runOnUiThread {
                        incrementSuccessfulRuns()
                        Toast.makeText(this@MainActivity, "Download finished!", Toast.LENGTH_SHORT).show()
                        showFinishLayout()
                    }
                }
            }
        }
    }

    suspend fun moveFileToMovies(privatePath: String): String = withContext(Dispatchers.IO) {
        val sourceFile = File(privatePath)
        if (!sourceFile.exists()) return@withContext ""

        val displayName = privatePath.substringAfterLast("/")
            .replace("/", "")
        val resolver = activityCurrent!!.contentResolver

        Log.i(TAG, "moveFileToMovies: $displayName")

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.TITLE, displayName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/SaveFrom")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        try {
            val uri = resolver.insert(collection, contentValues) ?: return@withContext ""
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            } else {
                MediaScannerConnection.scanFile(activityCurrent, arrayOf(sourceFile.absolutePath), null, null)
            }
            return@withContext uri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}")
            logEvent("sf_export_exception", e.message ?: "error exporting", privatePath)
            return@withContext ""
        }
    }

    suspend fun deleteTempFiles() = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(ABS_PATH_TEMP)
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
            Log.d(TAG, "Temp files cleared.")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed: ${e.message}")
        }
    }

    private val dlServiceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i(TAG, "onServiceConnected")
            val binder = service as DownloadService.LocalBinder
            mDownloadService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.i(TAG, "onServiceDisconnected")
        }
    }
}