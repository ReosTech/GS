package com.example.ai

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

data class MapLocationItem(
  val name: String,
  val latitude: Double,
  val longitude: Double,
  val isLive: Boolean = false,
  val isPrecise: Boolean = true,
  val accuracyMeters: Float = 15f
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapsScreen(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current

  var searchQuery by remember { mutableStateOf("") }
  var currentLocation by remember {
    mutableStateOf(
      MapLocationItem(
        name = "Tokyo",
        latitude = 35.6762,
        longitude = 139.6503
      )
    )
  }
  var mapLayerType by remember { mutableStateOf("Streets") } // "Streets", "Satellite", "Terrain"
  var isSearching by remember { mutableStateOf(false) }

  // AI Geospatial Guide
  var isAiAnalyzing by remember { mutableStateOf(false) }
  var aiInsights by remember { mutableStateOf<String?>(null) }

  // Location Permission Dialog state
  var showLocationDialog by remember { mutableStateOf(false) }
  var pendingAccuracyPreference by remember { mutableStateOf(LocationAccuracyPreference.PRECISE) }

  // Push notification sent indicator
  var notificationSentMessage by remember { mutableStateOf<String?>(null) }

  // Keep a reference to the WebView to inject JS pan/zoom
  var mapWebView by remember { mutableStateOf<WebView?>(null) }
  var isMapRenderError by remember { mutableStateOf(false) }
  var mapReloadKey by remember { mutableIntStateOf(0) }

  val popularLocations = listOf(
    MapLocationItem("Tokyo", 35.6762, 139.6503),
    MapLocationItem("San Francisco", 37.7749, -122.4194),
    MapLocationItem("Paris", 48.8566, 2.3522),
    MapLocationItem("New York", 40.7128, -74.0060),
    MapLocationItem("London", 51.5074, -0.1278),
    MapLocationItem("Singapore", 1.3521, 103.8198)
  )

  fun buildLeafletHtml(lat: Double, lon: Double, placeName: String, layer: String, isLive: Boolean, accuracy: Float): String {
    val tileUrl = when (layer) {
      "Satellite" -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
      "Terrain" -> "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png"
      else -> "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    }
    val attribution = when (layer) {
      "Satellite" -> "&copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community"
      "Terrain" -> "Map data: &copy; OpenStreetMap contributors, SRTM | Map style: &copy; OpenTopoMap"
      else -> "&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors"
    }
    val sanitizedName = placeName.replace("'", "\\'")

    return """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
          html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background-color: #1e262b; }
          .leaflet-control-attribution { font-size: 8px !important; background: rgba(255,255,255,0.7) !important; }
          .user-pulse-marker {
            width: 18px;
            height: 18px;
            background: #2563EB;
            border: 3px solid #FFFFFF;
            border-radius: 50%;
            box-shadow: 0 0 10px rgba(37,99,235,0.8);
          }
        </style>
      </head>
      <body>
        <div id="map"></div>
        <script>
          var map = L.map('map', {
            preferCanvas: true,
            zoomControl: false,
            attributionControl: true
          }).setView([$lat, $lon], 14);

          L.tileLayer('$tileUrl', {
            maxZoom: 19,
            attribution: '$attribution'
          }).addTo(map);

          L.control.zoom({ position: 'bottomright' }).addTo(map);

          var isLive = $isLive;
          if (isLive) {
            // Live location marker with accuracy circle
            var userIcon = L.divIcon({
              className: 'user-pulse-marker',
              iconSize: [18, 18],
              iconAnchor: [9, 9]
            });
            L.marker([$lat, $lon], { icon: userIcon })
              .addTo(map)
              .bindPopup('<b>📍 Live Location</b><br>$sanitizedName')
              .openPopup();

            L.circle([$lat, $lon], {
              radius: $accuracy,
              color: '#2563EB',
              fillColor: '#3B82F6',
              fillOpacity: 0.15,
              weight: 1
            }).addTo(map);
          } else {
            L.marker([$lat, $lon])
              .addTo(map)
              .bindPopup('<b>$sanitizedName</b><br>Lat: $lat, Lon: $lon')
              .openPopup();
          }
        </script>
      </body>
      </html>
    """.trimIndent()
  }

  fun updateMapHtml() {
    isMapRenderError = false
    val html = buildLeafletHtml(
      lat = currentLocation.latitude,
      lon = currentLocation.longitude,
      placeName = currentLocation.name,
      layer = mapLayerType,
      isLive = currentLocation.isLive,
      accuracy = currentLocation.accuracyMeters
    )
    mapWebView?.loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
  }

  fun requestGeospatialInsights(loc: MapLocationItem) {
    isAiAnalyzing = true
    coroutineScope.launch {
      val prompt = """
        Provide a curated, highly accurate geospatial explorer guide for ${loc.name} (Coordinates: ${loc.latitude}, ${loc.longitude}):
        1. Top 3 must-visit cultural or architectural landmarks with precise location context.
        2. Best walkable district for dining, coffee, or local life.
        3. One smart insider tip (transit navigation, peak viewing time, or hidden viewpoint).
        Keep it concise, engaging, and under 150 words.
      """.trimIndent()

      val result = AiService.generateContent(
        context = context,
        prompt = prompt,
        model = "gemini-3.8-flash",
        systemInstruction = "You are an expert geospatial explorer, city planner, and urban travel advisor."
      )

      result.onSuccess { text ->
        aiInsights = text
        isAiAnalyzing = false
      }.onFailure {
        aiInsights = "${loc.name} is a vibrant destination located at ${loc.latitude}, ${loc.longitude} with incredible landmarks, cultural quarters, and scenic streets to explore."
        isAiAnalyzing = false
      }
    }
  }

  fun loadLiveLocation(preference: LocationAccuracyPreference) {
    coroutineScope.launch {
      val locResult = LocationHelper.getCurrentLocation(context, preference)
      if (locResult != null) {
        currentLocation = MapLocationItem(
          name = locResult.resolvedPlaceName,
          latitude = locResult.latitude,
          longitude = locResult.longitude,
          isLive = true,
          isPrecise = locResult.isPrecise,
          accuracyMeters = locResult.accuracyMeters
        )
        updateMapHtml()
        requestGeospatialInsights(currentLocation)
      } else {
        Toast.makeText(context, "Could not acquire live GPS. Please verify location is enabled.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  // Location Permission Launcher
  val locationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (fineGranted || coarseGranted) {
      val effectivePref = if (fineGranted && pendingAccuracyPreference == LocationAccuracyPreference.PRECISE) {
        LocationAccuracyPreference.PRECISE
      } else {
        LocationAccuracyPreference.APPROXIMATE
      }
      loadLiveLocation(effectivePref)
    } else {
      Toast.makeText(context, "Location permission denied.", Toast.LENGTH_SHORT).show()
    }
  }

  // Notification Permission Launcher
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      NotificationHelper.sendAiNotification(
        context = context,
        title = "🗺️ Geospatial Guide: ${currentLocation.name}",
        message = aiInsights ?: "Explore live map coordinates and top local sights in ${currentLocation.name}.",
        subText = "AI Geospatial Guide",
        aiSubFeature = "maps"
      )
      notificationSentMessage = "AI Map Guide sent to notifications!"
    } else {
      Toast.makeText(context, "Notifications are disabled.", Toast.LENGTH_SHORT).show()
    }
  }

  fun triggerMapNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (NotificationHelper.hasNotificationPermission(context)) {
        NotificationHelper.sendAiNotification(
          context = context,
          title = "🗺️ Geospatial Guide: ${currentLocation.name}",
          message = aiInsights ?: "Explore live map coordinates and top local sights in ${currentLocation.name}.",
          subText = "AI Geospatial Guide",
          aiSubFeature = "maps"
        )
        notificationSentMessage = "AI Map Guide sent to notifications!"
      } else {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      NotificationHelper.sendAiNotification(
        context = context,
        title = "🗺️ Geospatial Guide: ${currentLocation.name}",
        message = aiInsights ?: "Explore live map coordinates and top local sights in ${currentLocation.name}.",
        subText = "AI Geospatial Guide",
        aiSubFeature = "maps"
      )
      notificationSentMessage = "AI Map Guide sent to notifications!"
    }
  }

  fun searchPlace(query: String) {
    if (query.isBlank()) return
    isSearching = true
    coroutineScope.launch {
      try {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${query.trim()}&count=1&language=en&format=json"
        val request = Request.Builder().url(url).build()
        val response = withContext(Dispatchers.IO) { AiService.httpClient.newCall(request).execute() }
        val body = withContext(Dispatchers.IO) { response.body?.string().orEmpty() }
        if (response.isSuccessful) {
          val json = JSONObject(body)
          val results = json.optJSONArray("results")
          if (results != null && results.length() > 0) {
            val first = results.getJSONObject(0)
            val lat = first.optDouble("latitude", 0.0)
            val lon = first.optDouble("longitude", 0.0)
            val name = first.optString("name", query)
            val country = first.optString("country", "")
            val resolved = if (country.isNotBlank()) "$name, $country" else name

            currentLocation = MapLocationItem(
              name = resolved,
              latitude = lat,
              longitude = lon,
              isLive = false
            )
            updateMapHtml()
            requestGeospatialInsights(currentLocation)
          } else {
            Toast.makeText(context, "Location not found: $query", Toast.LENGTH_SHORT).show()
          }
        }
      } catch (e: Exception) {
        Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
      } finally {
        isSearching = false
      }
    }
  }

  LaunchedEffect(mapLayerType) {
    updateMapHtml()
  }

  LaunchedEffect(Unit) {
    if (LocationHelper.hasAnyLocationPermission(context)) {
      val pref = if (LocationHelper.hasFineLocationPermission(context)) LocationAccuracyPreference.PRECISE else LocationAccuracyPreference.APPROXIMATE
      loadLiveLocation(pref)
    } else {
      requestGeospatialInsights(currentLocation)
    }
  }

  // Location Permission Dialog
  if (showLocationDialog) {
    LocationPermissionDialog(
      onDismiss = { showLocationDialog = false },
      onRequestPermission = { pref ->
        showLocationDialog = false
        pendingAccuracyPreference = pref
        if (pref == LocationAccuracyPreference.PRECISE) {
          locationPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION
            )
          )
        } else {
          locationPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_COARSE_LOCATION
            )
          )
        }
      }
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Search Bar with Live Location & Notification Actions
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        modifier = Modifier
          .weight(1f)
          .testTag("map_search_input"),
        placeholder = { Text("Search location (e.g. Paris, Tokyo)...", fontSize = 13.sp) },
        leadingIcon = {
          Icon(Icons.Default.Explore, contentDescription = "Explore", tint = MaterialTheme.colorScheme.primary)
        },
        trailingIcon = {
          if (searchQuery.isNotBlank()) {
            IconButton(onClick = {
              keyboardController?.hide()
              searchPlace(searchQuery)
              searchQuery = ""
            }) {
              Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go", tint = MaterialTheme.colorScheme.primary)
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
          if (searchQuery.isNotBlank()) {
            keyboardController?.hide()
            searchPlace(searchQuery)
            searchQuery = ""
          }
        })
      )

      // Live Location button (opens permission dialog)
      FilledTonalIconButton(
        onClick = { showLocationDialog = true },
        modifier = Modifier
          .size(48.dp)
          .testTag("map_live_location_button")
      ) {
        Icon(
          imageVector = if (currentLocation.isLive) Icons.Default.MyLocation else Icons.Default.LocationOn,
          contentDescription = "My Live Location",
          tint = if (currentLocation.isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Push notification button for AI Map guide
      FilledTonalIconButton(
        onClick = { triggerMapNotification() },
        modifier = Modifier
          .size(48.dp)
          .testTag("map_push_notification_button")
      ) {
        Icon(
          imageVector = Icons.Default.NotificationsActive,
          contentDescription = "Notify Map Guide",
          tint = MaterialTheme.colorScheme.tertiary
        )
      }
    }

    // Push Notification Confirmation Banner
    if (notificationSentMessage != null) {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(
              text = notificationSentMessage ?: "",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              fontWeight = FontWeight.Medium
            )
          }
          IconButton(onClick = { notificationSentMessage = null }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
          }
        }
      }
    }

    // Popular Location Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      popularLocations.forEach { loc ->
        val isSel = currentLocation.name.startsWith(loc.name, ignoreCase = true) && !currentLocation.isLive
        FilterChip(
          selected = isSel,
          onClick = {
            currentLocation = loc
            updateMapHtml()
            requestGeospatialInsights(loc)
          },
          label = { Text(loc.name, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
          ),
          shape = RoundedCornerShape(14.dp)
        )
      }
    }

    // Interactive Real High-Accuracy Map Card
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("map_canvas_card")
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E262B))
        ) {
          if (isMapRenderError) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Icon(
                Icons.Default.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
              )
              Spacer(Modifier.height(8.dp))
              Text(
                text = "${currentLocation.name} Map",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
              )
              Text(
                text = "Lat: ${String.format(Locale.US, "%.4f", currentLocation.latitude)}, Lon: ${String.format(Locale.US, "%.4f", currentLocation.longitude)}",
                color = Color.LightGray,
                fontSize = 12.sp
              )
              Spacer(Modifier.height(12.dp))
              Button(
                onClick = {
                  isMapRenderError = false
                  mapReloadKey++
                },
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reload Map")
              }
            }
          } else {
            key(mapReloadKey) {
              // Real OpenStreetMap Leaflet View with software rendering to avoid Mesa GPU crash
              AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                  WebView(ctx).apply {
                    setLayerType(View.LAYER_TYPE_NONE, null)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                      setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webViewClient = object : WebViewClient() {
                      override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                      ): Boolean {
                        // Crucial: Returning true prevents the host application process from being terminated
                        android.util.Log.w("MapsScreen", "WebView render process exited; handling gracefully.")
                        view?.let {
                          val parent = it.parent as? ViewGroup
                          parent?.removeView(it)
                          it.destroy()
                        }
                        mapWebView = null
                        isMapRenderError = true
                        return true
                      }
                    }
                    mapWebView = this
                    val html = buildLeafletHtml(
                      lat = currentLocation.latitude,
                      lon = currentLocation.longitude,
                      placeName = currentLocation.name,
                      layer = mapLayerType,
                      isLive = currentLocation.isLive,
                      accuracy = currentLocation.accuracyMeters
                    )
                    loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
                  }
                },
                update = { webView ->
                  mapWebView = webView
                }
              )
            }
          }

          // Top Info Badge
          Row(
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
              Text(
                text = "${currentLocation.name} • ${mapLayerType}",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          // Live Accuracy Pill on Map
          if (currentLocation.isLive) {
            Box(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                  imageVector = if (currentLocation.isPrecise) Icons.Default.GpsFixed else Icons.Default.NearMe,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(12.dp)
                )
                Text(
                  text = if (currentLocation.isPrecise) "Precise ±${currentLocation.accuracyMeters.toInt()}m"
                  else "Approx ±${(currentLocation.accuracyMeters / 1000).toInt()}km",
                  color = Color.White,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        // Map Layer Style Selectors (Streets, Satellite, Terrain)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Streets", "Satellite", "Terrain").forEach { layer ->
              FilterChip(
                selected = mapLayerType == layer,
                onClick = {
                  mapLayerType = layer
                  updateMapHtml()
                },
                label = { Text(layer, fontSize = 11.sp) },
                shape = RoundedCornerShape(12.dp)
              )
            }
          }
          Text(
            text = "${String.format(Locale.US, "%.3f", currentLocation.latitude)}, ${String.format(Locale.US, "%.3f", currentLocation.longitude)}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
          )
        }
      }
    }

    // AI Geospatial Advisor Card
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("map_ai_insights_card")
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = "AI",
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = "AI Geospatial Guide",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.onSecondaryContainer
            )
          }

          FilledTonalButton(
            onClick = { triggerMapNotification() },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp)
          ) {
            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Notify", fontSize = 11.sp)
          }
        }

        if (aiInsights != null) {
          AiMarkdownView(
            content = aiInsights ?: "",
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth()
          )
        } else {
          Text(
            text = "Analyzing geographic landmarks, pedestrian pathways, and hidden culinary gems around ${currentLocation.name}...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
          )
        }

        Button(
          onClick = { requestGeospatialInsights(currentLocation) },
          enabled = !isAiAnalyzing,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          if (isAiAnalyzing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Discovering sights...")
          } else {
            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (aiInsights == null) "Explore ${currentLocation.name}" else "Refresh AI Guide")
          }
        }
      }
    }
  }
}
