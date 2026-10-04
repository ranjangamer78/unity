package com.example

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.metadata.MetaData
import com.unity3d.services.banners.BannerErrorCode
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/**
 * Central manager for all Unity Ads operations.
 *
 * Handles:
 * - SDK initialization and privacy/consent compliance
 * - Interstitial ad loading, showing, and automatic reloading (UNCHANGED)
 * - Rewarded ad loading, showing, reward validation, and automatic reloading (UNCHANGED)
 * - Top and bottom banner ad loading, container attachment, and failure recovery
 * - Comprehensive Logcat and UI error reporting for easy debugging
 */
object UnityAdsManager {

    private const val TAG = "UnityAdsManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    // Initialization state
    var isInitialized: Boolean = false
        private set

    // Ad readiness states
    var isInterstitialReady: Boolean = false
        private set
    var isInterstitialLoading: Boolean = false
        private set

    var isRewardedReady: Boolean = false
        private set
    var isRewardedLoading: Boolean = false
        private set

    var isTopBannerReady: Boolean = false
        private set
    var isTopBannerLoading: Boolean = false
        private set

    var isBottomBannerReady: Boolean = false
        private set
    var isBottomBannerLoading: Boolean = false
        private set

    // Banner view references
    private var topBannerView: BannerView? = null
    private var bottomBannerView: BannerView? = null

    // Pending banner requests if called before initialization completes
    private var pendingActivity: Activity? = null
    private var pendingTopHolder: ViewGroup? = null
    private var pendingTopStatus: TextView? = null
    private var pendingBottomHolder: ViewGroup? = null
    private var pendingBottomStatus: TextView? = null

    // UI event listeners
    interface AdStateListener {
        fun onInitSuccess(version: String)
        fun onInitFailed(error: String)
        fun onInterstitialStatusChanged(isReady: Boolean, isLoading: Boolean, message: String)
        fun onRewardedStatusChanged(isReady: Boolean, isLoading: Boolean, message: String)
        fun onTopBannerStatusChanged(isReady: Boolean, isLoading: Boolean, message: String)
        fun onBottomBannerStatusChanged(isReady: Boolean, isLoading: Boolean, message: String)
        fun onLogMessage(message: String)
    }

    private val listeners = mutableListOf<AdStateListener>()

    fun addListener(listener: AdStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: AdStateListener) {
        listeners.remove(listener)
    }

    private fun postToMain(action: () -> Unit) {
        mainHandler.post(action)
    }

    private fun notifyLog(message: String) {
        Log.d(TAG, message)
        postToMain {
            listeners.forEach { it.onLogMessage(message) }
        }
    }

    /**
     * Initializes Unity Ads with privacy consent and configuration.
     * Should be called when the application starts.
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            notifyLog("Unity Ads is already initialized (SDK v${AdsConfig.SDK_VERSION})")
            return
        }

        notifyLog("Configuring privacy & consent metadata...")
        applyPrivacyConsent(context)

        notifyLog("Initializing Unity Ads SDK with Game ID: ${AdsConfig.UNITY_GAME_ID} (TestMode: ${AdsConfig.TEST_MODE})...")

        UnityAds.initialize(
            context.applicationContext,
            AdsConfig.UNITY_GAME_ID,
            AdsConfig.TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    isInitialized = true
                    val version = AdsConfig.SDK_VERSION
                    notifyLog("Unity Ads initialized successfully (SDK v$version)")

                    postToMain {
                        listeners.forEach { it.onInitSuccess(version) }
                    }

                    // Preload Interstitial and Rewarded ads
                    loadInterstitial()
                    loadRewarded()

                    // If banners were requested before init completed, trigger them now
                    val act = pendingActivity
                    val topH = pendingTopHolder
                    val topS = pendingTopStatus
                    val btmH = pendingBottomHolder
                    val btmS = pendingBottomStatus
                    if (act != null && topH != null && btmH != null) {
                        postToMain {
                            loadAllBanners(act, topH, topS, btmH, btmS)
                        }
                    }
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError,
                    message: String
                ) {
                    isInitialized = false
                    val errorMsg = "Unity Ads initialization failed [$error]: $message"
                    Log.e(TAG, errorMsg)
                    notifyLog(errorMsg)

                    postToMain {
                        listeners.forEach { it.onInitFailed(errorMsg) }
                    }
                }
            }
        )
    }

    /**
     * Applies GDPR and CCPA privacy metadata required by advertising regulations and Unity Ads.
     */
    private fun applyPrivacyConsent(context: Context) {
        try {
            val gdprMetaData = MetaData(context.applicationContext)
            gdprMetaData.set("gdpr.consent", AdsConfig.GDPR_CONSENT)
            gdprMetaData.commit()

            val privacyMetaData = MetaData(context.applicationContext)
            privacyMetaData.set("privacy.consent", AdsConfig.CCPA_CONSENT)
            privacyMetaData.set("privacy.mode", "mixed")
            privacyMetaData.commit()
        } catch (e: Exception) {
            Log.e(TAG, "Error applying privacy metadata: ${e.message}", e)
        }
    }

    // ==============================================================
    // INTERSTITIAL ADS (EXACTLY AS PREVIOUSLY IMPLEMENTED - UNCHANGED)
    // ==============================================================

    fun loadInterstitial() {
        if (!isInitialized) {
            notifyLog("Cannot load Interstitial: Unity Ads not initialized yet.")
            return
        }

        if (isInterstitialLoading) {
            notifyLog("Interstitial ad is already loading.")
            return
        }

        isInterstitialLoading = true
        isInterstitialReady = false
        notifyLog("Loading Interstitial Ad [${AdsConfig.INTERSTITIAL_AD_UNIT_ID}]...")

        postToMain {
            listeners.forEach {
                it.onInterstitialStatusChanged(isReady = false, isLoading = true, message = "Loading Interstitial Ad...")
            }
        }

        UnityAds.load(
            AdsConfig.INTERSTITIAL_AD_UNIT_ID,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isInterstitialLoading = false
                    isInterstitialReady = true
                    val msg = "Interstitial Ad ready for display ($placementId)"
                    notifyLog(msg)

                    postToMain {
                        listeners.forEach {
                            it.onInterstitialStatusChanged(isReady = true, isLoading = false, message = msg)
                        }
                    }
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String
                ) {
                    isInterstitialLoading = false
                    isInterstitialReady = false
                    val msg = "Failed to load Interstitial ($placementId) [$error]: $message"
                    Log.w(TAG, msg)
                    notifyLog(msg)

                    postToMain {
                        listeners.forEach {
                            it.onInterstitialStatusChanged(isReady = false, isLoading = false, message = msg)
                        }
                    }

                    mainHandler.postDelayed({
                        if (!isInterstitialReady && !isInterstitialLoading && isInitialized) {
                            loadInterstitial()
                        }
                    }, 12000)
                }
            }
        )
    }

    fun showInterstitial(
        activity: Activity,
        onUnavailable: (() -> Unit)? = null
    ): Boolean {
        if (!isInterstitialReady) {
            val msg = "Interstitial ad is not available. Please try again."
            notifyLog(msg)
            onUnavailable?.invoke()
            if (!isInterstitialLoading) {
                loadInterstitial()
            }
            return false
        }

        notifyLog("Showing Interstitial Ad [${AdsConfig.INTERSTITIAL_AD_UNIT_ID}]...")
        isInterstitialReady = false

        UnityAds.show(
            activity,
            AdsConfig.INTERSTITIAL_AD_UNIT_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {
                    notifyLog("Interstitial Ad started ($placementId)")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    notifyLog("Interstitial Ad clicked ($placementId)")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    notifyLog("Interstitial Ad closed ($placementId, state: $state)")
                    postToMain {
                        listeners.forEach {
                            it.onInterstitialStatusChanged(isReady = false, isLoading = false, message = "Ad closed ($state)")
                        }
                    }
                    loadInterstitial()
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    val msg = "Failed to show Interstitial ($placementId) [$error]: $message"
                    Log.e(TAG, msg)
                    notifyLog(msg)
                    postToMain {
                        listeners.forEach {
                            it.onInterstitialStatusChanged(isReady = false, isLoading = false, message = msg)
                        }
                    }
                    loadInterstitial()
                }
            }
        )
        return true
    }

    // ==============================================================
    // REWARDED ADS (EXACTLY AS PREVIOUSLY IMPLEMENTED - UNCHANGED)
    // ==============================================================

    fun loadRewarded() {
        if (!isInitialized) {
            notifyLog("Cannot load Rewarded: Unity Ads not initialized yet.")
            return
        }

        if (isRewardedLoading) {
            notifyLog("Rewarded ad is already loading.")
            return
        }

        isRewardedLoading = true
        isRewardedReady = false
        notifyLog("Loading Rewarded Ad [${AdsConfig.REWARDED_AD_UNIT_ID}]...")

        postToMain {
            listeners.forEach {
                it.onRewardedStatusChanged(isReady = false, isLoading = true, message = "Loading Rewarded Ad...")
            }
        }

        UnityAds.load(
            AdsConfig.REWARDED_AD_UNIT_ID,
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    isRewardedLoading = false
                    isRewardedReady = true
                    val msg = "Rewarded Ad ready for display ($placementId)"
                    notifyLog(msg)

                    postToMain {
                        listeners.forEach {
                            it.onRewardedStatusChanged(isReady = true, isLoading = false, message = msg)
                        }
                    }
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String
                ) {
                    isRewardedLoading = false
                    isRewardedReady = false
                    val msg = "Failed to load Rewarded ($placementId) [$error]: $message"
                    Log.w(TAG, msg)
                    notifyLog(msg)

                    postToMain {
                        listeners.forEach {
                            it.onRewardedStatusChanged(isReady = false, isLoading = false, message = msg)
                        }
                    }

                    mainHandler.postDelayed({
                        if (!isRewardedReady && !isRewardedLoading && isInitialized) {
                            loadRewarded()
                        }
                    }, 12000)
                }
            }
        )
    }

    fun showRewarded(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onUnavailable: (() -> Unit)? = null
    ): Boolean {
        if (!isRewardedReady) {
            val msg = "Rewarded ad is not available. Please try again."
            notifyLog(msg)
            onUnavailable?.invoke()
            if (!isRewardedLoading) {
                loadRewarded()
            }
            return false
        }

        notifyLog("Showing Rewarded Ad [${AdsConfig.REWARDED_AD_UNIT_ID}]...")
        isRewardedReady = false

        UnityAds.show(
            activity,
            AdsConfig.REWARDED_AD_UNIT_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowStart(placementId: String) {
                    notifyLog("Rewarded Ad playback started ($placementId)")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    notifyLog("Rewarded Ad clicked ($placementId)")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    notifyLog("Rewarded Ad playback complete ($placementId, state: $state)")

                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        notifyLog("Reward verified by Unity Ads! User completed the advertisement.")
                        postToMain {
                            onRewardEarned.invoke()
                        }
                    } else {
                        notifyLog("No reward granted: Ad was skipped or terminated early ($state).")
                    }

                    postToMain {
                        listeners.forEach {
                            it.onRewardedStatusChanged(isReady = false, isLoading = false, message = "Ad closed ($state)")
                        }
                    }

                    loadRewarded()
                }

                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    val msg = "Failed to show Rewarded ($placementId) [$error]: $message"
                    Log.e(TAG, msg)
                    notifyLog(msg)

                    postToMain {
                        listeners.forEach {
                            it.onRewardedStatusChanged(isReady = false, isLoading = false, message = msg)
                        }
                    }

                    loadRewarded()
                }
            }
        )
        return true
    }

    // ==============================================================
    // TOP & BOTTOM BANNER ADS (PROPERLY FIXED & COMPATIBLE)
    // ==============================================================

    /**
     * Loads both Top and Bottom banners cleanly with sequencing to prevent placement conflicts.
     */
    fun loadAllBanners(
        activity: Activity,
        topHolder: ViewGroup,
        topStatus: TextView?,
        bottomHolder: ViewGroup,
        bottomStatus: TextView?
    ) {
        pendingActivity = activity
        pendingTopHolder = topHolder
        pendingTopStatus = topStatus
        pendingBottomHolder = bottomHolder
        pendingBottomStatus = bottomStatus

        if (!isInitialized) {
            val waitMsg = "Waiting for Unity Ads initialization to complete..."
            notifyLog(waitMsg)
            topStatus?.text = "[ $waitMsg ]"
            bottomStatus?.text = "[ $waitMsg ]"
            return
        }

        // 1. Load Top Banner
        loadTopBanner(activity, topHolder, topStatus)

        // 2. Load Bottom Banner after a brief delay so both don't flood the SDK simultaneously
        mainHandler.postDelayed({
            loadBottomBanner(activity, bottomHolder, bottomStatus)
        }, 1500)
    }

    /**
     * Loads the real Unity Top Banner.
     * Uses official Unity BannerView API (extends RelativeLayout) with proper sizing and listener.
     */
    fun loadTopBanner(
        activity: Activity,
        adHolder: ViewGroup,
        statusView: TextView?
    ) {
        if (!isInitialized) {
            val msg = "Cannot load Top Banner: SDK not initialized yet."
            Log.w(TAG, msg)
            notifyLog(msg)
            statusView?.text = "[ $msg ]"
            return
        }

        postToMain {
            try {
                isTopBannerLoading = true
                isTopBannerReady = false

                statusView?.visibility = android.view.View.VISIBLE
                statusView?.text = "[ Requesting Top Banner (320x50)... ]"
                adHolder.visibility = android.view.View.GONE
                adHolder.removeAllViews()

                Log.d(TAG, "Instantiating Top BannerView for placement '${AdsConfig.BANNER_AD_UNIT_ID}' (320x50)")
                val banner = BannerView(activity, AdsConfig.BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
                topBannerView = banner

                val density = activity.resources.displayMetrics.density
                val widthPx = (320 * density).toInt()
                val heightPx = (50 * density).toInt()
                val params = FrameLayout.LayoutParams(widthPx, heightPx, Gravity.CENTER)
                banner.layoutParams = params

                // Attach to holder FIRST so the Android View has a valid hierarchy and layout params
                adHolder.addView(banner)

                banner.listener = object : BannerView.IListener {
                    override fun onBannerLoaded(bannerAdView: BannerView?) {
                        isTopBannerLoading = false
                        isTopBannerReady = true
                        val successMsg = "Top Banner loaded & displayed successfully!"
                        Log.i(TAG, successMsg)
                        notifyLog(successMsg)

                        postToMain {
                            adHolder.visibility = android.view.View.VISIBLE
                            statusView?.visibility = android.view.View.GONE
                            listeners.forEach {
                                it.onTopBannerStatusChanged(isReady = true, isLoading = false, message = "Top Banner Active")
                            }
                        }
                    }

                    override fun onBannerShown(bannerAdView: BannerView?) {
                        Log.i(TAG, "Top Banner ad view is now visible on screen")
                    }

                    override fun onBannerFailedToLoad(
                        bannerAdView: BannerView?,
                        errorInfo: BannerErrorInfo?
                    ) {
                        isTopBannerLoading = false
                        isTopBannerReady = false
                        val code = errorInfo?.errorCode ?: BannerErrorCode.UNKNOWN
                        val rawMsg = errorInfo?.errorMessage ?: "Unknown error"

                        // Detailed Logcat output for developer debugging
                        Log.e(TAG, "==================================================")
                        Log.e(TAG, "[BANNER LOAD ERROR] Top Banner failed to load!")
                        Log.e(TAG, "Placement ID: ${AdsConfig.BANNER_AD_UNIT_ID}")
                        Log.e(TAG, "Error Code: $code")
                        Log.e(TAG, "Error Message: $rawMsg")
                        Log.e(TAG, "Test Mode: ${AdsConfig.TEST_MODE}")
                        Log.e(TAG, "==================================================")

                        val userMsg = when (code) {
                            BannerErrorCode.NO_FILL -> "NO_FILL: Unity Ads has no live ad inventory currently in this country"
                            BannerErrorCode.WEBVIEW_ERROR -> "WEBVIEW_ERROR: Failed to initialize banner webview"
                            BannerErrorCode.NATIVE_ERROR -> "NATIVE_ERROR: $rawMsg"
                            else -> "$rawMsg ($code)"
                        }

                        notifyLog("[Top Banner Error] $userMsg")

                        postToMain {
                            adHolder.visibility = android.view.View.GONE
                            statusView?.visibility = android.view.View.VISIBLE
                            statusView?.text = "[ Top Banner: $userMsg ]"
                            listeners.forEach {
                                it.onTopBannerStatusChanged(isReady = false, isLoading = false, message = userMsg)
                            }
                        }
                    }

                    override fun onBannerClick(bannerAdView: BannerView?) {
                        Log.d(TAG, "Top Banner was clicked by user")
                        notifyLog("Top Banner clicked")
                    }

                    override fun onBannerLeftApplication(bannerAdView: BannerView?) {
                        Log.d(TAG, "Top Banner left application")
                        notifyLog("Top Banner left application")
                    }
                }

                Log.d(TAG, "Calling top banner.load()...")
                banner.load()
            } catch (e: Exception) {
                isTopBannerLoading = false
                val err = "Exception initializing Top Banner: ${e.message}"
                Log.e(TAG, err, e)
                notifyLog(err)
                statusView?.text = "[ Top Banner Error: ${e.message} ]"
            }
        }
    }

    /**
     * Loads the real Unity Bottom Banner.
     */
    fun loadBottomBanner(
        activity: Activity,
        adHolder: ViewGroup,
        statusView: TextView?
    ) {
        if (!isInitialized) {
            val msg = "Cannot load Bottom Banner: SDK not initialized yet."
            Log.w(TAG, msg)
            notifyLog(msg)
            statusView?.text = "[ $msg ]"
            return
        }

        postToMain {
            try {
                isBottomBannerLoading = true
                isBottomBannerReady = false

                statusView?.visibility = android.view.View.VISIBLE
                statusView?.text = "[ Requesting Bottom Banner (320x50)... ]"
                adHolder.visibility = android.view.View.GONE
                adHolder.removeAllViews()

                Log.d(TAG, "Instantiating Bottom BannerView for placement '${AdsConfig.BANNER_AD_UNIT_ID}' (320x50)")
                val banner = BannerView(activity, AdsConfig.BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
                bottomBannerView = banner

                val density = activity.resources.displayMetrics.density
                val widthPx = (320 * density).toInt()
                val heightPx = (50 * density).toInt()
                val params = FrameLayout.LayoutParams(widthPx, heightPx, Gravity.CENTER)
                banner.layoutParams = params

                adHolder.addView(banner)

                banner.listener = object : BannerView.IListener {
                    override fun onBannerLoaded(bannerAdView: BannerView?) {
                        isBottomBannerLoading = false
                        isBottomBannerReady = true
                        val successMsg = "Bottom Banner loaded & displayed successfully!"
                        Log.i(TAG, successMsg)
                        notifyLog(successMsg)

                        postToMain {
                            adHolder.visibility = android.view.View.VISIBLE
                            statusView?.visibility = android.view.View.GONE
                            listeners.forEach {
                                it.onBottomBannerStatusChanged(isReady = true, isLoading = false, message = "Bottom Banner Active")
                            }
                        }
                    }

                    override fun onBannerShown(bannerAdView: BannerView?) {
                        Log.i(TAG, "Bottom Banner ad view is now visible on screen")
                    }

                    override fun onBannerFailedToLoad(
                        bannerAdView: BannerView?,
                        errorInfo: BannerErrorInfo?
                    ) {
                        isBottomBannerLoading = false
                        isBottomBannerReady = false
                        val code = errorInfo?.errorCode ?: BannerErrorCode.UNKNOWN
                        val rawMsg = errorInfo?.errorMessage ?: "Unknown error"

                        Log.e(TAG, "==================================================")
                        Log.e(TAG, "[BANNER LOAD ERROR] Bottom Banner failed to load!")
                        Log.e(TAG, "Placement ID: ${AdsConfig.BANNER_AD_UNIT_ID}")
                        Log.e(TAG, "Error Code: $code")
                        Log.e(TAG, "Error Message: $rawMsg")
                        Log.e(TAG, "Test Mode: ${AdsConfig.TEST_MODE}")
                        Log.e(TAG, "==================================================")

                        val userMsg = when (code) {
                            BannerErrorCode.NO_FILL -> "NO_FILL: Unity Ads has no live ad inventory currently in this country"
                            BannerErrorCode.WEBVIEW_ERROR -> "WEBVIEW_ERROR: Failed to initialize banner webview"
                            BannerErrorCode.NATIVE_ERROR -> "NATIVE_ERROR: $rawMsg"
                            else -> "$rawMsg ($code)"
                        }

                        notifyLog("[Bottom Banner Error] $userMsg")

                        postToMain {
                            adHolder.visibility = android.view.View.GONE
                            statusView?.visibility = android.view.View.VISIBLE
                            statusView?.text = "[ Bottom Banner: $userMsg ]"
                            listeners.forEach {
                                it.onBottomBannerStatusChanged(isReady = false, isLoading = false, message = userMsg)
                            }
                        }
                    }

                    override fun onBannerClick(bannerAdView: BannerView?) {
                        Log.d(TAG, "Bottom Banner was clicked by user")
                        notifyLog("Bottom Banner clicked")
                    }

                    override fun onBannerLeftApplication(bannerAdView: BannerView?) {
                        Log.d(TAG, "Bottom Banner left application")
                        notifyLog("Bottom Banner left application")
                    }
                }

                Log.d(TAG, "Calling bottom banner.load()...")
                banner.load()
            } catch (e: Exception) {
                isBottomBannerLoading = false
                val err = "Exception initializing Bottom Banner: ${e.message}"
                Log.e(TAG, err, e)
                notifyLog(err)
                statusView?.text = "[ Bottom Banner Error: ${e.message} ]"
            }
        }
    }

    fun destroyTopBanner() {
        try {
            topBannerView?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying top banner: ${e.message}")
        }
        topBannerView = null
        isTopBannerReady = false
        isTopBannerLoading = false
    }

    fun destroyBottomBanner() {
        try {
            bottomBannerView?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying bottom banner: ${e.message}")
        }
        bottomBannerView = null
        isBottomBannerReady = false
        isBottomBannerLoading = false
    }

    fun destroyBanners() {
        destroyTopBanner()
        destroyBottomBanner()
        pendingActivity = null
        pendingTopHolder = null
        pendingTopStatus = null
        pendingBottomHolder = null
        pendingBottomStatus = null
    }
}
