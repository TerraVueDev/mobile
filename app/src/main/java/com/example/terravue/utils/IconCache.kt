package com.example.terravue.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Icon cache utility to avoid reloading icons repeatedly
 */
object IconCache {
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    suspend fun getAppIcon(context: Context, packageName: String): Drawable? = withContext(Dispatchers.IO) {
        // Check cache first
        iconCache[packageName]?.let { return@withContext it }

        // Load from package manager
        try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val icon = appInfo.loadIcon(packageManager)

            // Cache for future use
            iconCache[packageName] = icon
            icon
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        iconCache.clear()
    }
}