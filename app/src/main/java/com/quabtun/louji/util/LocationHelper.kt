package com.quabtun.louji.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {
    sealed interface Result {
        data class Success(val place: String) : Result
        data object PermissionMissing : Result
        data object LocationDisabled : Result
        data object Timeout : Result
        data class Failed(val message: String? = null) : Result
    }

    fun hasPermission(context: Context): Boolean =
        hasFinePermission(context) || hasCoarsePermission(context)

    suspend fun currentPlace(context: Context): Result = withContext(Dispatchers.IO) {
        try {
            currentPlaceInternal(context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.Failed(error.message)
        }
    }

    private suspend fun currentPlaceInternal(context: Context): Result {
        if (!hasPermission(context)) return Result.PermissionMissing
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return Result.Failed("设备没有定位服务")

        val fine = hasFinePermission(context)
        val allowedProviders = buildList {
            if (isProviderEnabled(manager, LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (fine && isProviderEnabled(manager, LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (isProviderEnabled(manager, LocationManager.PASSIVE_PROVIDER)) {
                add(LocationManager.PASSIVE_PROVIDER)
            }
        }
        if (allowedProviders.isEmpty()) return Result.LocationDisabled

        val recentLastKnown = lastKnownLocation(manager, allowedProviders)
            ?.takeIf { System.currentTimeMillis() - it.time <= RECENT_LAST_KNOWN_MS }
        if (recentLastKnown != null) {
            return Result.Success(reverseGeocode(context, recentLastKnown))
        }

        // Active requests are intentionally ordered network -> GPS: a quick network fix is
        // usually enough for a building name, and GPS gets a longer window when available.
        val activeProviders = allowedProviders.filter { it != LocationManager.PASSIVE_PROVIDER }
        var location: Location? = null
        for (provider in activeProviders) {
            location = requestCurrentLocation(
                context = context,
                manager = manager,
                provider = provider,
                timeoutMs = if (provider == LocationManager.NETWORK_PROVIDER) {
                    NETWORK_TIMEOUT_MS
                } else {
                    GPS_TIMEOUT_MS
                },
            )
            if (location != null) break
        }
        if (location == null) {
            location = lastKnownLocation(manager, allowedProviders)
                ?.takeIf { System.currentTimeMillis() - it.time <= FALLBACK_LAST_KNOWN_MS }
        }
        if (location == null) {
            return if (activeProviders.isEmpty()) {
                Result.LocationDisabled
            } else {
                Result.Timeout
            }
        }
        return Result.Success(reverseGeocode(context, location))
    }

    private fun hasFinePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasCoarsePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isProviderEnabled(manager: LocationManager, provider: String): Boolean =
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(
        manager: LocationManager,
        providers: List<String>,
    ): Location? = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(
        context: Context,
        manager: LocationManager,
        provider: String,
        timeoutMs: Long,
    ): Location? = withTimeoutOrNull(timeoutMs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getCurrentLocation(context, manager, provider)
        } else {
            requestSingleUpdate(manager, provider)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(
        context: Context,
        manager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        try {
            manager.getCurrentLocation(
                provider,
                signal,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (_: Throwable) {
            if (continuation.isActive) continuation.resume(null)
        }
        continuation.invokeOnCancellation { signal.cancel() }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(
        manager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (continuation.isActive) continuation.resume(location)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }
        try {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (_: Throwable) {
            if (continuation.isActive) continuation.resume(null)
        }
        continuation.invokeOnCancellation { manager.removeUpdates(listener) }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(context: Context, location: Location): String {
        val name = runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            Geocoder(context, Locale.CHINA)
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
        }.getOrNull()?.let { item ->
            listOfNotNull(item.subLocality, item.thoroughfare, item.featureName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("")
                .takeIf { it.isNotBlank() }
        }
        return name?.take(60) ?: String.format(
            Locale.US,
            "%.5f, %.5f",
            location.latitude,
            location.longitude,
        )
    }

    private const val RECENT_LAST_KNOWN_MS = 2 * 60 * 1000L
    private const val FALLBACK_LAST_KNOWN_MS = 10 * 60 * 1000L
    private const val NETWORK_TIMEOUT_MS = 6_000L
    private const val GPS_TIMEOUT_MS = 8_000L
}
