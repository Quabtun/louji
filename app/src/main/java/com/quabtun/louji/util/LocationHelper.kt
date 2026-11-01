package com.quabtun.louji.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocationHelper {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentPlace(context: Context): String? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null
        val manager = context.getSystemService(LocationManager::class.java) ?: return@withContext null
        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            }.getOrNull()
        }
        val location = candidates.maxByOrNull { it.time } ?: return@withContext null
        val address = runCatching {
            Geocoder(context, Locale.CHINA)
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
        }.getOrNull()
        val name = address?.let { item ->
            listOfNotNull(item.subLocality, item.thoroughfare, item.featureName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("")
                .takeIf { it.isNotBlank() }
        }
        name?.take(60) ?: String.format(
            Locale.US,
            "%.5f, %.5f",
            location.latitude,
            location.longitude,
        )
    }
}
