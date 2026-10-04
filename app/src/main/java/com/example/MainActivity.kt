package com.example

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), UnityAdsManager.AdStateListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var earnedCoins: Int = 0
    private var completedAdCount: Int = 0
    private val logLines = mutableListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize local persistence for reward system
        prefs = getSharedPreferences("app_user_prefs", Context.MODE_PRIVATE)

        val isFreshReset = intent.getBooleanExtra("EXTRA_RESET_FRESH_LAUNCH", false)
        if (isFreshReset) {
            earnedCoins = 0
            completedAdCount = 0
            Toast.makeText(this, "App reset to fresh install state!", Toast.LENGTH_SHORT).show()
        } else {
            earnedCoins = prefs.getInt("earned_coins", 0)
            completedAdCount = prefs.getInt("completed_ads_count", 0)
        }

        setupUI()
        setupListeners()

        // Register for ad state notifications
        UnityAdsManager.addListener(this)

        // Fetch Current Public IP and Country Location
        fetchIpAndLocation()

        // Initialize Unity Ads or trigger banner loading
        if (UnityAdsManager.isInitialized) {
            updateSdkStatus(true, "SDK Active")
            loadBanners()
        } else {
            updateSdkStatus(false, "Initializing SDK...")
            // Register containers so they load automatically as soon as initialization completes
            UnityAdsManager.loadAllBanners(
                activity = this,
                topHolder = binding.topBannerAdHolder,
                topStatus = binding.tvTopBannerStatus,
                bottomHolder = binding.bottomBannerAdHolder,
                bottomStatus = binding.tvBottomBannerStatus
            )
            UnityAdsManager.initialize(this)
        }
    }

    private fun setupUI() {
        binding.tvGameIdBadge.text = "Game ID: ${AdsConfig.UNITY_GAME_ID}"
        binding.tvTestModeBadge.text = if (AdsConfig.TEST_MODE) "TEST MODE: ON" else "REAL ADS MODE"
        binding.tvTestModeBadge.setTextColor(
            getColor(if (AdsConfig.TEST_MODE) R.color.status_loading else R.color.status_ready)
        )

        updateRewardUI()
        updateInterstitialButtonState()
        updateRewardedButtonState()

        val currentTestMode = AdsConfig.isTestModeEnabled(this)
        binding.switchTestMode.isChecked = currentTestMode
        updateTestModeUI(currentTestMode)
    }

    private fun updateTestModeUI(isTestMode: Boolean) {
        if (isTestMode) {
            binding.tvAdModeTitle.text = "Test Ads Mode (Active)"
            binding.tvAdModeDescription.text = "Guarantees 100% ad fill rate for APK testing on real phone"
            binding.tvTestModeBadge.text = "TEST ADS (100% FILL)"
            binding.tvTestModeBadge.setTextColor(getColor(R.color.status_ready))
        } else {
            binding.tvAdModeTitle.text = "Live Real Ads Mode (Active)"
            binding.tvAdModeDescription.text = "Live traffic from Game ID 5846818 (requires Unity approval/fill)"
            binding.tvTestModeBadge.text = "LIVE ADS MODE"
            binding.tvTestModeBadge.setTextColor(getColor(R.color.primary))
        }
    }

    private fun setupListeners() {
        // Mode Switch (Test vs Live Ads)
        binding.switchTestMode.setOnCheckedChangeListener { _, isChecked ->
            updateTestModeUI(isChecked)
            appendLog("Switched Ad Mode -> TestMode = $isChecked")
            UnityAdsManager.reinitialize(
                activity = this,
                enableTestMode = isChecked,
                topHolder = binding.topBannerAdHolder,
                topStatus = binding.tvTopBannerStatus,
                bottomHolder = binding.bottomBannerAdHolder,
                bottomStatus = binding.tvBottomBannerStatus
            )
        }

        // Quick Reload All Ads Button
        binding.btnQuickReloadAll.setOnClickListener {
            appendLog("Reloading all ads (Interstitial, Rewarded, Banners)...")
            val isTest = binding.switchTestMode.isChecked
            UnityAdsManager.reinitialize(
                activity = this,
                enableTestMode = isTest,
                topHolder = binding.topBannerAdHolder,
                topStatus = binding.tvTopBannerStatus,
                bottomHolder = binding.bottomBannerAdHolder,
                bottomStatus = binding.tvBottomBannerStatus
            )
        }

        // 1. Interstitial Ad Button (Unchanged)
        binding.btnShowInterstitial.setOnClickListener {
            handleInterstitialButtonClick()
        }

        // 2. Rewarded Ad Button (Unchanged)
        binding.btnWatchRewarded.setOnClickListener {
            handleRewardedButtonClick()
        }

        // 3. Reload Banner Ads Button
        binding.btnReloadBanners.setOnClickListener {
            appendLog("Manual request: Reloading Top & Bottom Banners...")
            loadBanners()
        }

        // 4. Refresh IP & Location Button (Unchanged)
        binding.btnRefreshGeoIp.setOnClickListener {
            fetchIpAndLocation()
        }

        // 5. Restart App Button (Unchanged)
        binding.btnRestartApp.setOnClickListener {
            showRestartAppDialog()
        }

        // Clear Live Log Button
        binding.btnClearLog.setOnClickListener {
            logLines.clear()
            binding.tvLiveLog.text = "[Log Cleared]"
        }
    }

    /**
     * Fetches current real public IP and country location.
     */
    private fun fetchIpAndLocation() {
        binding.tvCurrentIp.text = "Detecting IP..."
        binding.tvCurrentCountry.text = "Detecting Country..."

        GeoIpHelper.fetchIpAndCountry(
            onSuccess = { geo ->
                binding.tvCurrentIp.text = geo.ip
                val locationText = if (geo.city.isNotEmpty()) {
                    "${geo.country} (${geo.countryCode}) - ${geo.city}"
                } else {
                    "${geo.country} (${geo.countryCode})"
                }
                binding.tvCurrentCountry.text = locationText
                appendLog("[GEO] IP: ${geo.ip} | Country: ${geo.country} (${geo.countryCode})")
            },
            onError = { errorMsg ->
                binding.tvCurrentIp.text = "Unavailable"
                binding.tvCurrentCountry.text = "Unknown"
                appendLog("[GEO ERROR] $errorMsg")
            }
        )
    }

    /**
     * Loads both real Unity Top and Bottom Banners sequentially.
     */
    private fun loadBanners() {
        binding.tvBannerStatusSummary.text = "Top & Bottom Banners: Requesting..."
        binding.tvBannerStatusSummary.setTextColor(getColor(R.color.status_loading))
        UnityAdsManager.loadAllBanners(
            activity = this,
            topHolder = binding.topBannerAdHolder,
            topStatus = binding.tvTopBannerStatus,
            bottomHolder = binding.bottomBannerAdHolder,
            bottomStatus = binding.tvBottomBannerStatus
        )
    }

    // ==============================================================
    // INTERSTITIAL AD HANDLING (EXACTLY AS PREVIOUSLY - UNCHANGED)
    // ==============================================================

    private fun handleInterstitialButtonClick() {
        if (!UnityAdsManager.isInterstitialReady) {
            val message = getString(R.string.ad_not_ready_interstitial)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            appendLog(message)

            if (!UnityAdsManager.isInterstitialLoading) {
                UnityAdsManager.loadInterstitial()
            }
            return
        }

        appendLog("Triggering real Unity Interstitial ad...")
        UnityAdsManager.showInterstitial(
            activity = this,
            onUnavailable = {
                Toast.makeText(this, R.string.ad_not_ready_interstitial, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun updateInterstitialButtonState() {
        if (UnityAdsManager.isInterstitialLoading) {
            binding.btnShowInterstitial.text = getString(R.string.btn_loading_ad)
            binding.btnShowInterstitial.isEnabled = false
            binding.tvInterstitialStatus.text = "Status: Loading ad from Unity..."
            binding.tvInterstitialStatus.setTextColor(getColor(R.color.status_loading))
        } else if (UnityAdsManager.isInterstitialReady) {
            binding.btnShowInterstitial.text = getString(R.string.btn_show_interstitial)
            binding.btnShowInterstitial.isEnabled = true
            binding.tvInterstitialStatus.text = "Status: Ready to show"
            binding.tvInterstitialStatus.setTextColor(getColor(R.color.status_ready))
        } else {
            binding.btnShowInterstitial.text = getString(R.string.btn_show_interstitial)
            binding.btnShowInterstitial.isEnabled = true
            binding.tvInterstitialStatus.text = "Status: Not loaded (tap to request)"
            binding.tvInterstitialStatus.setTextColor(getColor(R.color.text_secondary))
        }
    }

    // ==============================================================
    // REWARDED AD HANDLING (EXACTLY AS PREVIOUSLY - UNCHANGED)
    // ==============================================================

    private fun handleRewardedButtonClick() {
        if (!UnityAdsManager.isRewardedReady) {
            val message = getString(R.string.ad_not_ready_rewarded)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            appendLog(message)

            if (!UnityAdsManager.isRewardedLoading) {
                UnityAdsManager.loadRewarded()
            }
            return
        }

        appendLog("Triggering real Unity Rewarded ad...")
        UnityAdsManager.showRewarded(
            activity = this,
            onRewardEarned = {
                onRewardEarned()
            },
            onUnavailable = {
                Toast.makeText(this, R.string.ad_not_ready_rewarded, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun onRewardEarned() {
        earnedCoins += 50
        completedAdCount += 1

        prefs.edit()
            .putInt("earned_coins", earnedCoins)
            .putInt("completed_ads_count", completedAdCount)
            .apply()

        updateRewardUI()

        val rewardMessage = "Reward Earned! +50 Coins added to your account!"
        Toast.makeText(this, rewardMessage, Toast.LENGTH_LONG).show()
        appendLog("[REWARD CONFIRMED] +50 coins earned! Total: $earnedCoins")
    }

    private fun updateRewardUI() {
        binding.tvCoins.text = getString(R.string.coins_label, earnedCoins)
        binding.tvRewardCount.text = "$completedAdCount completed"
    }

    private fun updateRewardedButtonState() {
        if (UnityAdsManager.isRewardedLoading) {
            binding.btnWatchRewarded.text = getString(R.string.btn_loading_ad)
            binding.btnWatchRewarded.isEnabled = false
            binding.tvRewardedStatus.text = "Status: Loading rewarded ad..."
            binding.tvRewardedStatus.setTextColor(getColor(R.color.status_loading))
        } else if (UnityAdsManager.isRewardedReady) {
            binding.btnWatchRewarded.text = getString(R.string.btn_watch_rewarded)
            binding.btnWatchRewarded.isEnabled = true
            binding.tvRewardedStatus.text = "Status: Ready to watch (+50 Coins)"
            binding.tvRewardedStatus.setTextColor(getColor(R.color.status_ready))
        } else {
            binding.btnWatchRewarded.text = getString(R.string.btn_watch_rewarded)
            binding.btnWatchRewarded.isEnabled = true
            binding.tvRewardedStatus.text = "Status: Not loaded (tap to request)"
            binding.tvRewardedStatus.setTextColor(getColor(R.color.text_secondary))
        }
    }

    // ==============================================================
    // RESTART APP FLOW (EXACTLY AS PREVIOUSLY - UNCHANGED)
    // ==============================================================

    private fun showRestartAppDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.restart_dialog_title))
            .setMessage(getString(R.string.restart_dialog_message))
            .setNegativeButton(getString(R.string.btn_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.btn_restart)) { dialog, _ ->
                dialog.dismiss()
                performAppRestart()
            }
            .show()
    }

    private fun performAppRestart() {
        appendLog("Resetting application: wiping all storage and relaunching...")
        AppDataResetHelper.resetAppDataAndRelaunch(this)
    }

    // ==============================================================
    // AD STATE LISTENER CALLBACKS
    // ==============================================================

    override fun onInitSuccess(version: String) {
        updateSdkStatus(true, "SDK v$version Active")
        loadBanners()
    }

    override fun onInitFailed(error: String) {
        updateSdkStatus(false, "Init Failed")
        appendLog("[ERROR] $error")
    }

    override fun onInterstitialStatusChanged(isReady: Boolean, isLoading: Boolean, message: String) {
        updateInterstitialButtonState()
        appendLog("[Interstitial] $message")
    }

    override fun onRewardedStatusChanged(isReady: Boolean, isLoading: Boolean, message: String) {
        updateRewardedButtonState()
        appendLog("[Rewarded] $message")
    }

    override fun onTopBannerStatusChanged(isReady: Boolean, isLoading: Boolean, message: String) {
        if (isReady) {
            binding.tvBannerStatusSummary.text = "Top Banner: Active | Bottom Banner: ${if (UnityAdsManager.isBottomBannerReady) "Active" else "Loading..."}"
            binding.tvBannerStatusSummary.setTextColor(getColor(R.color.status_ready))
        }
        appendLog("[Top Banner] $message")
    }

    override fun onBottomBannerStatusChanged(isReady: Boolean, isLoading: Boolean, message: String) {
        if (isReady) {
            binding.tvBannerStatusSummary.text = "Both Banners Active!"
            binding.tvBannerStatusSummary.setTextColor(getColor(R.color.status_ready))
        }
        appendLog("[Bottom Banner] $message")
    }

    override fun onLogMessage(message: String) {
        appendLog(message)
    }

    private fun updateSdkStatus(isActive: Boolean, statusText: String) {
        appendLog("[SDK Status] $statusText")
    }

    private fun appendLog(text: String) {
        val time = timeFormat.format(Date())
        val line = "[$time] $text"
        logLines.add(0, line)
        if (logLines.size > 25) {
            logLines.removeAt(logLines.lastIndex)
        }
        binding.tvLiveLog.text = logLines.joinToString("\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        UnityAdsManager.removeListener(this)
        UnityAdsManager.destroyBanners()
    }
}
