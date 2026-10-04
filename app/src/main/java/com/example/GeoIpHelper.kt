package com.example

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Helper to fetch the device's real public IP address and country location.
 * Provides valuable diagnostics for Unity Ads geo-targeted ad inventory.
 */
object GeoIpHelper {

    private const val TAG = "GeoIpHelper"
    private val mainHandler = Handler(Looper.getMainLooper())

    data class GeoResult(
        val ip: String,
        val country: String,
        val countryCode: String,
        val city: String
    )

    fun fetchIpAndCountry(
        onSuccess: (GeoResult) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            // Try Primary: https://ipwho.is/
            var result = fetchFromUrl("https://ipwho.is/")
            if (result == null) {
                // Fallback 1: http://ip-api.com/json/
                result = fetchFromUrl("http://ip-api.com/json/")
            }
            if (result == null) {
                // Fallback 2: https://api.country.is/
                result = fetchFromCountryIs("https://api.country.is/")
            }

            withContext(Dispatchers.Main) {
                if (result != null) {
                    onSuccess(result)
                } else {
                    onError("Unable to resolve public IP. Please check internet connection.")
                }
            }
        }
    }

    private fun fetchFromUrl(urlString: String): GeoResult? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "UnityAdsDemo-Android/1.0")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val ip = json.optString("ip", json.optString("query", "Unknown"))
                val country = json.optString("country", "Unknown")
                val countryCode = json.optString("country_code", json.optString("countryCode", ""))
                val city = json.optString("city", "")

                if (ip.isNotEmpty() && ip != "Unknown") {
                    GeoResult(ip = ip, country = country, countryCode = countryCode, city = city)
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch from $urlString: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchFromCountryIs(urlString: String): GeoResult? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val ip = json.optString("ip", "Unknown")
                val country = json.optString("country", "Unknown")
                GeoResult(ip = ip, country = country, countryCode = country, city = "")
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch from $urlString: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
