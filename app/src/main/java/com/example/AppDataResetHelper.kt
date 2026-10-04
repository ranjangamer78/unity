package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * Handles complete application data wiping and a clean relaunch.
 *
 * Guarantees:
 * - Destruction of existing banner views and ad bridge references.
 * - Complete deletion of all SharedPreferences XML files and cache.
 * - Deletion of all internal databases and app storage files.
 * - Relaunching a fresh root activity task without previous saved states or backstack.
 */
object AppDataResetHelper {

    private const val TAG = "AppDataReset"

    fun resetAppDataAndRelaunch(activity: Activity) {
        try {
            Log.i(TAG, "Starting complete application data wipe...")

            // 1. Destroy all current banner ad views and clear references
            UnityAdsManager.destroyBanners()

            // 2. Wipe all SharedPreferences (every .xml file in shared_prefs)
            try {
                val dataDir = File(activity.applicationInfo.dataDir)
                val sharedPrefsDir = File(dataDir, "shared_prefs")
                if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
                    sharedPrefsDir.listFiles()?.forEach { file ->
                        try {
                            val prefName = file.name.removeSuffix(".xml")
                            activity.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                                .edit()
                                .clear()
                                .commit()
                            file.delete()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete pref file ${file.name}: ${e.message}")
                        }
                    }
                }
                // Explicitly clear standard app prefs
                activity.getSharedPreferences("app_user_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing SharedPreferences: ${e.message}")
            }

            // 3. Clear application caches (internal & external)
            try {
                activity.cacheDir?.deleteRecursively()
                activity.codeCacheDir?.deleteRecursively()
                activity.externalCacheDir?.deleteRecursively()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing cache directories: ${e.message}")
            }

            // 4. Clear internal files and noBackup directories
            try {
                activity.filesDir?.listFiles()?.forEach { it.deleteRecursively() }
                activity.noBackupFilesDir?.listFiles()?.forEach { it.deleteRecursively() }
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing filesDir: ${e.message}")
            }

            // 5. Delete all SQLite databases
            try {
                activity.databaseList()?.forEach { dbName ->
                    activity.deleteDatabase(dbName)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting databases: ${e.message}")
            }

            Log.i(TAG, "Application data wipe finished successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during data wipe: ${e.message}", e)
        }

        // 6. Relaunch Activity in a brand-new task as a clean, first-install run
        try {
            val freshIntent = Intent(activity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("EXTRA_RESET_FRESH_LAUNCH", true)
            }
            activity.startActivity(freshIntent)
            activity.finishAffinity()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch clean intent: ${e.message}", e)
            activity.recreate()
        }
    }
}
