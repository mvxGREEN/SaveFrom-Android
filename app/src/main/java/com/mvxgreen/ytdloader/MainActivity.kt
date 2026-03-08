package com.mvxgreen.ytdloader

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
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
import com.mvxgreen.ytdloader.manager.AdsManager
import com.mvxgreen.ytdloader.manager.PrefsManager
import java.net.InetAddress
import java.time.LocalDate
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.FirebaseApp
import com.mvxgreen.ytdloader.databinding.DialogCutterBinding
import com.mvxgreen.ytdloader.databinding.DialogRateBinding
import com.mvxgreen.ytdloader.databinding.DialogUpgradeBinding
import com.mvxgreen.ytdloader.databinding.DialogVideBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlin.toString

class MainActivity : AppCompatActivity(), PurchasesUpdatedListener, AdapterView.OnItemSelectedListener {
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var mDownloadService: DownloadService? = null
    lateinit var binding: ActivityMainBinding
    private lateinit var fadeIn: Animation
    private lateinit var fadeOut: Animation

    private lateinit var prefsManager: PrefsManager
    private lateinit var mFinishReceiver: FinishReceiver

    private var downloadedFileUri: Uri? = null

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

    enum class UIState { EMPTY, LOADING, PREVIEW, DOWNLOADING, FINISHED }

    companion object {
        private val TAG = MainActivity::class.java.canonicalName

        const val MIME_MP4: String = "video/mp4"

        @JvmField
        val ABS_PATH_TEMP: String = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS
        ).absolutePath + "/SaveFrom/"

        @JvmField
        val ABS_PATH_MOVIES: String = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES
        ).absolutePath + "/SaveFrom/"

        @SuppressLint("StaticFieldLeak")
        @JvmField
        var activityCurrent: MainActivity? = null

        @JvmField
        var mResolution = "2160p"

        @JvmField
        var mFileType = "video"

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
            val progress = cleanProgressStr.toDouble()
            val progressInt = progress.toInt()
            val progressMsg = "Downloading…\n$progressInt%"

            activityCurrent?.runOnUiThread {
                activityCurrent?.binding?.progressRingDlr?.isIndeterminate = false
                activityCurrent?.binding?.dlProgressText?.text = progressMsg
                activityCurrent?.binding?.progressRingDlr?.max = 100
                activityCurrent?.binding?.progressRingDlr?.progress = progressInt
            }
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fixes "strict-mode" error when fetching webpage
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        prefsManager = PrefsManager(this@MainActivity)
        isBackgroundEnabled = prefsManager.backgroundEnabled?.isNotEmpty() ?: false
        Log.i(TAG, "isBackgroundEnabled=$isBackgroundEnabled")

        setupMainViews()
        setupListeners()

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

                        binding.bannerContainer.visibility = View.GONE

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
                binding.bannerContainer.visibility = View.GONE

                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Thank you, enjoy! <3", Toast.LENGTH_LONG).show()
                    val toolbar = findViewById<Toolbar>(R.id.toolbar)
                    val upgradeItem = toolbar.menu.findItem(R.id.action_upgrade)
                    upgradeItem?.icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.diamond_24_gold)
                    updateUI(UIState.EMPTY)
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
                        binding.bannerContainer.visibility = View.VISIBLE
                        AdsManager.loadAdmobInterstitialAd(this@MainActivity)
                        AdsManager.loadBanner(this@MainActivity, binding)
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
            binding.bannerContainer.visibility = View.VISIBLE
            AdsManager.loadAdmobInterstitialAd(this@MainActivity)
            AdsManager.loadBanner(this@MainActivity, binding)
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

    private fun setupMainViews() {
        initAnimations()
        binding.mainScroll.isSmoothScrollingEnabled = true

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // resolution spinner
        val resSpinner = findViewById<Spinner>(R.id.res_spinner)
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
        resSpinner.adapter = adapter
        val sharedPref = getSharedPreferences("SaveFromPrefs", Context.MODE_PRIVATE)
        val selectionIndex = sharedPref.getInt("RES_POSITION", 0)
        resSpinner.setSelection(selectionIndex)
        resSpinner.onItemSelectedListener = this

        // filetype spinner
        val typeSpinner = findViewById<Spinner>(R.id.type_spinner)
        val typeArray = resources.getStringArray(R.array.type_array)
        val typeAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, typeArray) {
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
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter
        typeSpinner.setSelection(0)
        typeSpinner.onItemSelectedListener = this

        if (!isBackgroundEnabled) {
            Log.i(TAG, "showing permission holder")
            binding.permissionHolder.visibility = View.VISIBLE
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

    private fun setupListeners() {
        binding.etMainInput.addTextChangedListener(object : TextWatcher {
            var oldCount = 0

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                oldCount = count
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (count == 0 && oldCount != 0) {
                    killKeyboard()
                    updateUI(UIState.EMPTY)
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
                    updateUI(UIState.LOADING)

                    if (isInternetAvailable()) {
                        prefsManager.originalUrl = inputText
                        Thread { loadVideoInfo(inputText) }.start()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.msg_no_internet), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun afterTextChanged(s: Editable) {
                binding.btnClear.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
            }
        })

        binding.btnClear.setOnClickListener {
            binding.etMainInput.setText("")
            updateUI(UIState.EMPTY)
        }

        binding.dlBtn.setOnClickListener {
            updateUI(UIState.DOWNLOADING)

            val intent = Intent(this@MainActivity, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, dlServiceConn, Context.BIND_AUTO_CREATE)
        }

        binding.shareBtn.setOnClickListener {
            shareDownloadedFile()
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

    private fun updateUI(state: UIState) {
        binding.etMainInput.isEnabled = true
        binding.btnPaste.isEnabled = true
        binding.btnPaste.alpha = 1.0f

        // get input_url for analytics event
        var url = binding.etMainInput.text.toString()
        if (url.contains("https://")) {
            url = url.substringAfter("https://"+8)
        }

        when (state) {
            UIState.EMPTY -> {
                Log.d("MainActivity", "sf_ui_empty")
                //logEvent("sl_ui_empty")
                binding.loadingLayout.visibility = View.INVISIBLE
                binding.previewCard.visibility = View.INVISIBLE
                binding.previewCard.alpha = 0.0f
                binding.downloaderCard.visibility = View.INVISIBLE
                binding.overlayDownloading.visibility = View.INVISIBLE

                binding.shareBtn.visibility = View.INVISIBLE
                binding.shareBtn.alpha = 0.0f

                binding.previewTitle.text = ""
                binding.previewSubtitle.text = ""

                binding.progressLabel.text = ""
            }
            UIState.LOADING -> {
                Log.d("MainActivity", "sf_ui_loading")
                logEvent("sf_ui_loading", url, "")

                binding.previewCard.visibility = View.INVISIBLE
                binding.downloaderCard.visibility = View.INVISIBLE
                binding.overlayDownloading.visibility = View.INVISIBLE // Was GONE
                binding.etMainInput.isEnabled = false

                binding.progressRingDlr.isIndeterminate = true
                binding.progressLabel.text = getString(R.string.loading)
                binding.loadingLayout.visibility = View.VISIBLE

                binding.btnPaste.isEnabled = false
                binding.btnPaste.alpha = 0.5f
                binding.shareBtn.visibility = View.INVISIBLE
                binding.shareBtn.alpha = 0.0f
            }
            UIState.PREVIEW -> {
                Log.d("MainActivity", "sf_ui_preview")
                logEvent("sf_ui_preview", url, "")

                binding.loadingLayout.visibility = View.INVISIBLE
                binding.previewCard.alpha = 1.0f
                binding.previewCard.visibility = View.VISIBLE
                binding.downloaderCard.alpha = 1.0f
                binding.downloaderCard.visibility = View.VISIBLE
                binding.overlayDownloading.visibility = View.INVISIBLE // Was GONE
                binding.dlBtn.visibility = View.VISIBLE

                binding.shareBtn.visibility = View.INVISIBLE
                binding.shareBtn.alpha = 0.0f

                binding.resSpinner.visibility = View.VISIBLE

                Glide.with(this@MainActivity)
                    .load(prefsManager.thumbnailUrl)
                    .centerCrop()
                    .into(binding.imgPreview)

                binding.previewTitle.text = prefsManager.fileName
                binding.previewSubtitle.text = prefsManager.fileSize

                // Show Interstitial if not Gold
                AdsManager.showInterstitialAd(this)
            }
            UIState.DOWNLOADING -> {
                Log.d("MainActivity", "sf_ui_downloading")
                logEvent("sf_ui_downloading", url, "")
                binding.dlProgressText.text = getString(R.string.downloading)
                binding.progressRingDlr.isIndeterminate = true
                binding.loadingLayout.visibility = View.INVISIBLE
                binding.previewCard.visibility = View.VISIBLE
                binding.downloaderCard.visibility = View.VISIBLE
                binding.overlayDownloading.visibility = View.VISIBLE
                binding.dlBtn.visibility = View.INVISIBLE
                binding.etMainInput.isEnabled = false

                binding.btnPaste.isEnabled = false
                binding.btnPaste.alpha = 0.5f
                binding.shareBtn.visibility = View.INVISIBLE
                binding.shareBtn.alpha = 0.0f
            }
            UIState.FINISHED -> {
                Log.d("MainActivity", "sf_ui_finished")
                logEvent("sf_ui_finished", url, "")
                binding.overlayDownloading.visibility = View.INVISIBLE

                binding.resSpinner.visibility = View.GONE

                binding.shareBtn.visibility = View.VISIBLE
                binding.shareBtn.animate().alpha(1.0f)

                incrementSuccessfulRuns()
            }
        }
    }

    private fun loadVideoInfo(url: String) {
        var titleStr = ""
        var extStr = ""
        var thumbStr = ""
        var bytesStr = ""

        lifecycleScope.launch {
            val py = Python.getInstance()
            val pyObject = py.getModule("vidloader")

            try {
                val res = pyObject.callAttr("extract_video_info", url, mResolution.replace("\\D".toRegex(), "")).toString()
                titleStr = res.substringBefore("|||")

                // trim title to 25 characters
                if (titleStr.length > 25) {
                    titleStr = titleStr.substring(0, 25)
                }

                var thumbAndBytesStr = res.substringAfter("|||")

                thumbStr = thumbAndBytesStr.substringBeforeLast("|||")
                thumbStr = thumbStr.replace("|", "")

                bytesStr = thumbAndBytesStr.substringAfterLast("|||")
                bytesStr = bytesStr.replace("|", "")
                if (bytesStr != "0") {
                    bytesStr = Formatter.formatFileSize(this@MainActivity, bytesStr.toLong()).toString()
                } else {
                    bytesStr = ""
                }

                extStr = "mp4"

                Log.i(TAG, "Extracted video info: filename: $titleStr\next: $extStr" +
                        "\nthumbnail url: $thumbStr\nbytes: $bytesStr")

                prefsManager.fileName = titleStr
                prefsManager.thumbnailUrl = thumbStr
                prefsManager.fileSize = bytesStr
                prefsManager.fileExt = extStr

                updateUI(UIState.PREVIEW)
            } catch (e: Exception) {
                Log.e(TAG, e.toString())

                // try extracting info from HTML
                val success = loadHtml(url)
                if (success) {
                    updateUI(UIState.PREVIEW)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "error loading, please try again", Toast.LENGTH_SHORT).show()
                        updateUI(UIState.EMPTY)
                    }
                }
            }
        }
    }

    suspend fun loadHtml(url: String): Boolean = withContext(Dispatchers.IO) {
        Log.i("loadHtml", "starting loadHtml")

        delay(34) // Added network delay

        var fileName = ""
        var thumbnailUrl = ""
        var downloadUrl = ""
        var fileSize = "0"

        try {
            val doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                .timeout(8000)
                .get()
            val html = doc.html().toString()

            Log.i("loadHtml", "html length: ${html.length}")

            // extract filename from title
            fileName = sanitizeFilename(doc.title().toString())

            // extract thumbnail url
            thumbnailUrl = doc.select("meta[property=og:image]")
                .attr("content")

            // extract download url
            downloadUrl = getFirstMp4Url(html) ?: ""

            // check for rnd parameter (ex. thisvid.com)
            if (html.contains("rnd: ")) {
                val rnd = html.substringAfter("rnd: '").substringBefore("'")
                Log.i("loadHtml", "extracted rnd parameter: $rnd")
                downloadUrl = downloadUrl + "?rnd=$rnd"
            }

            // save info
            Log.i(TAG, "Extracted video info: filename: $fileName" +
                    "\ndownload url: $downloadUrl\next: mp4" +
                    "\nthumbnail url: $thumbnailUrl\nbytes: 0")
            prefsManager.fileName = fileName
            prefsManager.thumbnailUrl = thumbnailUrl
            prefsManager.fileSize = fileSize
            prefsManager.fileExt = "mp4"

            return@withContext true
        } catch (e: Exception) {
            Log.e("loadHtml", "Exception: ${e.message}")
            return@withContext false
        }
    }

    fun getFirstMp4Url(html: String): String? {
        // Regex pattern:
        // https?://       -> Matches http:// or https://
        // [^\s"'<>]+?     -> Matches 1 or more characters that are NOT whitespace, quotes, or HTML brackets (lazy)
        // \.mp4           -> Matches exactly ".mp4"
        // [^\s"'<>]* -> Matches any trailing characters like query parameters (e.g., ?v=123)
        val regex = """https?://[^\s"'<>]+?\.mp4[^\s"'<>]*""".toRegex(RegexOption.IGNORE_CASE)

        val matchResult = regex.find(html)

        return matchResult?.value
    }

    fun sanitizeFilename(input: String): String {
        // 1. Replace spaces with underscores
        var filename = input.replace(" ", "_")

        // 2. Remove characters that are illegal in Android/Windows/Unix file systems
        val illegalChars = "[\\\\/:*?\"<>|]".toRegex()
        filename = filename.replace(illegalChars, "")

        // 3. Truncate to 25 characters maximum
        filename = filename.take(25)

        // 4. Provide a fallback in case the string was entirely illegal characters or empty
        if (filename.isEmpty()) {
            return "default_filename"
        }

        return filename
    }

    private suspend fun loadNetworkResponse(urlStr: String): String = withContext(Dispatchers.IO) {
        delay(34) // Added network delay
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
            conn.setRequestProperty("Referer", "https://www.google.com/")
            conn.setRequestProperty("Origin", "https://www.google.com/")
            conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            conn.connect()

            val code = conn.responseCode
            if (code == 200) {
                return@withContext conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                logEvent("sf_network_response_fail", "HTTP Code: $code", urlStr)
                return@withContext ""
            }
        } catch (e: Exception) {
            logEvent("sf_network_exception", e.message ?: "Unknown Error", urlStr)
            return@withContext ""
        }
    }

    suspend fun loadJson(urlStr: String) = withContext(Dispatchers.IO) {
        delay(34) // Added network delay
        try {
            val json = URL(urlStr).readText()
            val jsonObj = JSONObject(json)

            // TODO extract video title, thumbnail, and/or download url

        } catch (e: Exception) {
            logEvent("sf_json_parse_exception", e.message ?: "Unknown Error", urlStr)
        }
    }

    fun onPasteClick(v: View?) {
        binding.etMainInput.setText("")
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        var primaryStr = ""
        val primaryClip = clipboardManager.primaryClip

        if (primaryClip != null && primaryClip.itemCount > 0) {
            primaryStr = primaryClip.getItemAt(0).text.toString().trim()
            binding.etMainInput.setText(primaryStr)
        } else {
            Toast.makeText(this@MainActivity, "Please copy a video link", Toast.LENGTH_LONG).show()
            binding.etMainInput.setText(primaryStr)
        }
    }

    fun onDownloadClick(v: View?) {
        Log.i(TAG, ".onDownloadClicked()")
        updateUI(UIState.DOWNLOADING)

        val intent = Intent(this@MainActivity, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, dlServiceConn, Context.BIND_AUTO_CREATE)
    }

    fun onEnableBackgroundClicked(v: View?) {
        binding.permissionHolder.visibility = View.GONE
        prefsManager.backgroundEnabled = "TRUE"

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    fun OnEnableNotifClicked(v: View?) {
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            1
        )
    }

    private fun incrementSuccessfulRuns() {
        Log.i(TAG, "incrementSuccessfulRuns")
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
                val cycleCount = currentCount % 4
                Log.i(TAG, "cycle count: $cycleCount")
                if (cycleCount == 1) {
                    showUpgradeDialog()
                } else if (cycleCount == 2) {
                    showVideDialog()
                } else if (cycleCount == 3) {
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
        val spinnerItemStr = parent.selectedItem.toString()
        Log.i(TAG, "spinnerItem=$spinnerItemStr")

        if (spinnerItemStr == "video" || spinnerItemStr == "audio") {
            // handle type spinner clicks
            mFileType = spinnerItemStr
        } else {
            // handle res spinner clicks
            mResolution = spinnerItemStr

            val sharedPref = getSharedPreferences("ULOADER_PREFS", Context.MODE_PRIVATE)
            sharedPref.edit().putInt("RES_POSITION", position).apply()
        }
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
                    updateUI(UIState.EMPTY)
                }
                return
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        downloadedFileUri = moveFileToMovies(absFilePath).toUri()
                        // TODO use for share button
                    } catch (e: Exception) {
                        Log.e(TAG, "Move failed: ${e.message}")
                    }

                    deleteTempFiles()

                    //var moviesFilepath = absFilePath
                    if (absFilePath.contains(ABS_PATH_TEMP))
                        savedFilePath = absFilePath.replace(ABS_PATH_TEMP, ABS_PATH_MOVIES)

                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Download finished!", Toast.LENGTH_SHORT).show()
                        updateUI(UIState.FINISHED)
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

    private fun shareDownloadedFile() {
        downloadedFileUri?.let { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Audio"))
        } ?: run {
            Toast.makeText(this, "File not found to share.", Toast.LENGTH_SHORT).show()
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