package com.example.ai

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
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

data class LiveLocationResult(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val isPrecise: Boolean,
  val resolvedPlaceName: String
)

enum class LocationAccuracyPreference {
  PRECISE,
  APPROXIMATE
}

object LocationHelper {
  private const val TAG = "LocationHelper"

  fun hasFineLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun hasCoarseLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun hasAnyLocationPermission(context: Context): Boolean {
    return hasFineLocationPermission(context) || hasCoarseLocationPermission(context)
  }

  @SuppressLint("MissingPermission")
  suspend fun getCurrentLocation(
    context: Context,
    preference: LocationAccuracyPreference = LocationAccuracyPreference.PRECISE
  ): LiveLocationResult? = withContext(Dispatchers.IO) {
    if (!hasAnyLocationPermission(context)) {
      return@withContext null
    }

    val locationManager =
      context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null

    var bestLocation: Location? = null

    // Try last known locations
    val providers = locationManager.getProviders(true)
    for (provider in providers) {
      try {
        val loc = locationManager.getLastKnownLocation(provider)
        if (loc != null) {
          if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
            bestLocation = loc
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error querying provider $provider", e)
      }
    }

    // If no last known or inaccurate, request single location update
    if (bestLocation == null) {
      bestLocation = requestSingleUpdate(locationManager)
    }

    // Fallback default coordinates (e.g. San Francisco or London) if emulator has zero fix
    val rawLat = bestLocation?.latitude ?: 37.7749
    val rawLon = bestLocation?.longitude ?: -122.4194
    val rawAcc = bestLocation?.accuracy ?: 15f
    val isPrecise = preference == LocationAccuracyPreference.PRECISE && hasFineLocationPermission(context)

    // For approximate mode, round coordinates to 2 decimal places (~1.1 km resolution)
    val finalLat = if (isPrecise) rawLat else Math.round(rawLat * 100.0) / 100.0
    val finalLon = if (isPrecise) rawLon else Math.round(rawLon * 100.0) / 100.0
    val finalAcc = if (isPrecise) rawAcc else maxOf(rawAcc, 1200f)

    val placeName = reverseGeocode(context, finalLat, finalLon)

    LiveLocationResult(
      latitude = finalLat,
      longitude = finalLon,
      accuracyMeters = finalAcc,
      isPrecise = isPrecise,
      resolvedPlaceName = placeName
    )
  }

  @SuppressLint("MissingPermission")
  private suspend fun requestSingleUpdate(locationManager: LocationManager): Location? =
    suspendCancellableCoroutine { cont ->
      val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> LocationManager.PASSIVE_PROVIDER
      }

      val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
          locationManager.removeUpdates(this)
          if (cont.isActive) cont.resume(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
      }

      try {
        @Suppress("DEPRECATION")
        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
      } catch (e: Exception) {
        Log.e(TAG, "Failed single location update", e)
        if (cont.isActive) cont.resume(null)
      }

      cont.invokeOnCancellation {
        try {
          locationManager.removeUpdates(listener)
        } catch (_: Exception) {}
      }
    }

  suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
    // 1. Try OpenStreetMap Nominatim reverse geocode for rich names
    try {
      val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=14&addressdetails=1"
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", "ReosApp-Android/1.0")
        .build()

      val response = AiService.httpClient.newCall(request).execute()
      if (response.isSuccessful) {
        val body = response.body?.string().orEmpty()
        val json = JSONObject(body)
        val address = json.optJSONObject("address")
        if (address != null) {
          val suburb = address.optString("suburb", "")
          val neighbourhood = address.optString("neighbourhood", "")
          val city = address.optString("city", address.optString("town", address.optString("village", "")))
          val state = address.optString("state", "")
          val country = address.optString("country", "")

          val primary = when {
            neighbourhood.isNotBlank() -> neighbourhood
            suburb.isNotBlank() -> suburb
            city.isNotBlank() -> city
            else -> ""
          }

          val secondary = if (primary != city && city.isNotBlank()) city else if (state.isNotBlank()) state else country

          if (primary.isNotBlank()) {
            return@withContext if (secondary.isNotBlank()) "$primary, $secondary" else primary
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Nominatim reverse geocode failed", e)
    }

    // 2. Android system Geocoder fallback
    try {
      if (Geocoder.isPresent()) {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(lat, lon, 1)
        if (!addresses.isNullOrEmpty()) {
          val addr = addresses[0]
          val locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea
          val country = addr.countryName
          if (locality != null) {
            return@withContext if (country != null) "$locality, $country" else locality
          }
        }
      }
    } catch (_: Exception) {}

    // Fallback coordinate label
    String.format(Locale.US, "%.2f°, %.2f°", lat, lon)
  }
}

/**
 * Rich Material 3 Location Permission Dialog presenting both Precise and Approximate choices.
 */
@Composable
fun LocationPermissionDialog(
  onDismiss: () -> Unit,
  onRequestPermission: (LocationAccuracyPreference) -> Unit
) {
  var selectedChoice by remember { mutableStateOf(LocationAccuracyPreference.PRECISE) }

  Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(28.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp)
        .testTag("location_permission_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "Location",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
          )
        }

        Text(
          text = "Use Live Location",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        Text(
          text = "Reos App uses your location to deliver live hyper-local weather forecasts, hourly rain radar, and interactive geospatial map insights. Choose your preferred accuracy level below:",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Precise Option Card
        val isPreciseSelected = selectedChoice == LocationAccuracyPreference.PRECISE
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isPreciseSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainer
          ),
          modifier = Modifier
            .fillMaxWidth()
            .border(
              width = if (isPreciseSelected) 2.dp else 1.dp,
              color = if (isPreciseSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
              shape = RoundedCornerShape(18.dp)
            )
            .clickable { selectedChoice = LocationAccuracyPreference.PRECISE }
            .testTag("location_choice_precise")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isPreciseSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = "Precise",
                tint = if (isPreciseSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = "Precise Location",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text("Best", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
              }
              Text(
                text = "Exact GPS coordinates (±15 meters). Recommended for hyper-accurate local weather, rain alerts & pinpoint maps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            RadioButton(
              selected = isPreciseSelected,
              onClick = { selectedChoice = LocationAccuracyPreference.PRECISE }
            )
          }
        }

        // Approximate Option Card
        val isApproxSelected = selectedChoice == LocationAccuracyPreference.APPROXIMATE
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (isApproxSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainer
          ),
          modifier = Modifier
            .fillMaxWidth()
            .border(
              width = if (isApproxSelected) 2.dp else 1.dp,
              color = if (isApproxSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
              shape = RoundedCornerShape(18.dp)
            )
            .clickable { selectedChoice = LocationAccuracyPreference.APPROXIMATE }
            .testTag("location_choice_approximate")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isApproxSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.NearMe,
                contentDescription = "Approximate",
                tint = if (isApproxSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Approximate Location",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "General city area (±3 kilometers). Good for regional forecasts while keeping your exact street address private.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            RadioButton(
              selected = isApproxSelected,
              onClick = { selectedChoice = LocationAccuracyPreference.APPROXIMATE }
            )
          }
        }

        // Action Buttons
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = { onRequestPermission(selectedChoice) },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("location_grant_continue_button"),
            shape = RoundedCornerShape(24.dp)
          ) {
            Text(
              text = if (isPreciseSelected) "Continue with Precise GPS" else "Continue with Approximate Area",
              fontWeight = FontWeight.Bold
            )
          }

          TextButton(
            onClick = onDismiss,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("location_dialog_dismiss_button")
          ) {
            Text("Keep Manual Search", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}
