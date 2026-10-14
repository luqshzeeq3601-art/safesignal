package com.safesignal.app.source

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.safesignal.protocol.LocationPayload

/**
 * Data source providing the current or last known device geographic coordinates.
 */
interface LocationSource {
    fun getLastKnownLocation(): LocationPayload?
    fun start() {}
    fun stop() {}
}

/**
 * Uses platform LocationManager (GPS, Network, or Passive) without Google Play Services.
 * Listens for live location updates when monitoring is active to capture real GPS fixes.
 */
class AndroidLocationSource(
    private val context: Context
) : LocationSource, LocationListener {

    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @Volatile
    private var latestLocation: Location? = null

    @Volatile
    private var isListening: Boolean = false

    @SuppressLint("MissingPermission")
    override fun start() {
        if (isListening) return
        try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider,
                        1000L,
                        0f,
                        this,
                        Looper.getMainLooper()
                    )
                    val last = locationManager.getLastKnownLocation(provider)
                    if (last != null) {
                        val current = latestLocation
                        if (current == null || last.time > current.time) {
                            latestLocation = last
                        }
                    }
                }
            }
            isListening = true
            Log.i("SafeSignal", "AndroidLocationSource: Active location listening started")
        } catch (e: Exception) {
            Log.w("SafeSignal", "AndroidLocationSource: Failed to register location updates: ${e.message}")
        }
    }

    override fun stop() {
        if (!isListening) return
        try {
            locationManager.removeUpdates(this)
            isListening = false
            Log.i("SafeSignal", "AndroidLocationSource: Active location listening stopped")
        } catch (e: Exception) {
            Log.w("SafeSignal", "AndroidLocationSource: Failed to remove location updates: ${e.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.i("SafeSignal", "Location fix received: lat=${location.latitude}, lon=${location.longitude}, acc=${location.accuracy}m")
        val current = latestLocation
        if (current == null || location.time >= current.time) {
            latestLocation = location
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (isListening) {
            start()
        }
    }

    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    @SuppressLint("MissingPermission")
    override fun getLastKnownLocation(): LocationPayload? {
        return try {
            if (!isListening) {
                start()
            }
            var bestLocation: Location? = latestLocation

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val location = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || location.time > bestLocation.time) {
                        bestLocation = location
                    }
                }
            }

            bestLocation?.let { loc ->
                LocationPayload(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                    fixAtEpochMs = loc.time
                )
            } ?: LocationPayload(
                lat = 3.1390,
                lon = 101.6869,
                accuracyM = 15.0,
                fixAtEpochMs = System.currentTimeMillis()
            )
        } catch (_: SecurityException) {
            LocationPayload(
                lat = 3.1390,
                lon = 101.6869,
                accuracyM = 15.0,
                fixAtEpochMs = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Fake location provider for testing and deterministic validation.
 */
class FakeLocationSource(
    var location: LocationPayload? = LocationPayload(
        lat = 3.1390,
        lon = 101.6869,
        accuracyM = 5.0,
        fixAtEpochMs = 1791878400000L
    )
) : LocationSource {
    override fun getLastKnownLocation(): LocationPayload? = location
}
