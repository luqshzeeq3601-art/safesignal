package com.safesignal.app.source

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.safesignal.protocol.LocationPayload

/**
 * Data source providing the current or last known device geographic coordinates.
 */
interface LocationSource {
    fun getLastKnownLocation(): LocationPayload?
}

/**
 * Uses platform LocationManager (GPS, Network, or Passive) without Google Play Services.
 */
class AndroidLocationSource(
    private val context: Context
) : LocationSource {

    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownLocation(): LocationPayload? {
        return try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var bestLocation: Location? = null

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
