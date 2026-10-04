package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Unity Ads Demo", appName)
    assertEquals("5846818", AdsConfig.UNITY_GAME_ID)
    assertEquals("Interstitial_Android", AdsConfig.INTERSTITIAL_AD_UNIT_ID)
    assertEquals("Rewarded_Android", AdsConfig.REWARDED_AD_UNIT_ID)
    assertEquals("Banner_Android", AdsConfig.BANNER_AD_UNIT_ID)
    assertEquals(false, AdsConfig.TEST_MODE)
  }

  @Test
  fun `verify data reset functionality`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("app_user_prefs", Context.MODE_PRIVATE)
    prefs.edit().putInt("earned_coins", 150).commit()
    assertEquals(150, prefs.getInt("earned_coins", 0))

    // Clear preferences
    prefs.edit().clear().commit()
    assertEquals(0, prefs.getInt("earned_coins", 0))
  }
}
