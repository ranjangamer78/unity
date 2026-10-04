package com.example

/**
 * Central configuration for Unity Ads.
 *
 * All Ad Units and Game ID are defined here in one single place.
 */
object AdsConfig {
    /**
     * Unity Game ID configured directly for this project.
     * As specified by user requirement: 5846818
     */
    const val UNITY_GAME_ID: String = "5846818"

    /**
     * Official Unity Ad Unit / Placement IDs for Android.
     * These correspond to the official placement names generated in the Unity Ads Dashboard.
     */
    const val INTERSTITIAL_AD_UNIT_ID: String = "Interstitial_Android"
    const val REWARDED_AD_UNIT_ID: String = "Rewarded_Android"
    const val BANNER_AD_UNIT_ID: String = "Banner_Android"

    /**
     * Unity Ads Android SDK Version integrated into this application.
     */
    const val SDK_VERSION: String = "4.12.5"

    /**
     * Test Mode toggle:
     * - Set to [true] for development / testing (delivers test ads).
     * - Set to [false] for production builds (delivers live real ads).
     */
    const val TEST_MODE: Boolean = false

    /**
     * Privacy & Consent configuration flags.
     * Compatible with official Unity Ads privacy requirements.
     */
    const val GDPR_CONSENT: Boolean = true
    const val CCPA_CONSENT: Boolean = true
}
