package com.example.ai

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WeatherScreen(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null
) {
  BackHandler(enabled = onBack != null) {
    onBack?.invoke()
  }
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val keyboardController = LocalSoftwareKeyboardController.current

  var searchQuery by remember { mutableStateOf("") }
  var selectedCity by remember { mutableStateOf("San Francisco") }
  var weatherData by remember { mutableStateOf<AiService.WeatherData?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  // Gemini AI Weather Advice
  var aiWeatherAdvice by remember { mutableStateOf<String?>(null) }
  var isAiAdviceLoading by remember { mutableStateOf(false) }

  // Location Permission & Choice Dialog
  var showLocationDialog by remember { mutableStateOf(false) }
  var pendingAccuracyPreference by remember { mutableStateOf(LocationAccuracyPreference.PRECISE) }

  // Notification status
  var notificationSentMessage by remember { mutableStateOf<String?>(null) }

  // Day/Night preview mode (null = Live Auto, true = Forced Day, false = Forced Night)
  var dayNightModeOverride by remember { mutableStateOf<Boolean?>(null) }
  val effectiveIsDay = dayNightModeOverride ?: (weatherData?.isDay ?: true)

  val popularCities = listOf("San Francisco", "New York", "London", "Tokyo", "Paris", "Sydney")

  // Function to load weather using live GPS/network location
  fun loadLiveLocation(preference: LocationAccuracyPreference) {
    isLoading = true
    errorMessage = null
    coroutineScope.launch {
      val locResult = LocationHelper.getCurrentLocation(context, preference)
      if (locResult != null) {
        val result = AiService.fetchWeatherByCoordinates(
          latitude = locResult.latitude,
          longitude = locResult.longitude,
          locationName = locResult.resolvedPlaceName,
          isLiveLocation = true,
          isPrecise = locResult.isPrecise,
          accuracyMeters = locResult.accuracyMeters
        )
        result.onSuccess { data ->
          weatherData = data
          selectedCity = data.cityName
          isLoading = false
        }.onFailure { err ->
          errorMessage = err.message ?: "Failed to fetch local weather"
          isLoading = false
        }
      } else {
        // Fallback to default if no provider returned fix
        val fallback = AiService.fetchRealTimeWeather("San Francisco")
        fallback.onSuccess { data ->
          weatherData = data
          isLoading = false
        }.onFailure { err ->
          errorMessage = err.message ?: "Failed to fetch weather"
          isLoading = false
        }
      }
    }
  }

  // Location Permission Launchers
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
      Toast.makeText(context, "Location permission not granted. Using manual search.", Toast.LENGTH_SHORT).show()
    }
  }

  // Notification Permission Launcher
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      weatherData?.let { data ->
        NotificationHelper.sendWeatherBriefingNotification(
          context = context,
          cityName = data.cityName,
          temp = data.currentTemp,
          condition = data.weatherDesc,
          advice = aiWeatherAdvice
        )
        notificationSentMessage = "Push notification sent to status bar!"
      }
    } else {
      Toast.makeText(context, "Notifications are disabled for Reos App", Toast.LENGTH_SHORT).show()
    }
  }

  fun triggerWeatherNotification() {
    weatherData?.let { data ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (NotificationHelper.hasNotificationPermission(context)) {
          NotificationHelper.sendWeatherBriefingNotification(
            context = context,
            cityName = data.cityName,
            temp = data.currentTemp,
            condition = data.weatherDesc,
            advice = aiWeatherAdvice
          )
          notificationSentMessage = "Push notification sent to status bar!"
        } else {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      } else {
        NotificationHelper.sendWeatherBriefingNotification(
          context = context,
          cityName = data.cityName,
          temp = data.currentTemp,
          condition = data.weatherDesc,
          advice = aiWeatherAdvice
        )
        notificationSentMessage = "Push notification sent to status bar!"
      }
    }
  }

  fun loadWeather(city: String) {
    isLoading = true
    errorMessage = null
    aiWeatherAdvice = null
    coroutineScope.launch {
      val result = AiService.fetchRealTimeWeather(city)
      result.onSuccess { data ->
        weatherData = data
        selectedCity = data.cityName
        isLoading = false
      }.onFailure { err ->
        errorMessage = err.message ?: "Failed to fetch weather"
        isLoading = false
      }
    }
  }

  fun requestAiAdvice(data: AiService.WeatherData) {
    isAiAdviceLoading = true
    coroutineScope.launch {
      val prompt = """
        Based on the current real-time weather in ${data.cityName}:
        - Temperature: ${data.currentTemp}°C (Feels like: ${data.apparentTemp}°C)
        - Condition: ${data.weatherDesc}
        - Humidity: ${data.humidity}%
        - Wind Speed: ${data.windSpeed} km/h (Gusts: ${data.windGusts} km/h, Direction: ${data.windDirection}°)
        - UV Index: ${data.uvIndex}
        - Air Pressure: ${data.surfacePressure} hPa
        - Sunrise: ${data.sunrise} | Sunset: ${data.sunset}

        Provide concise, friendly, and practical advice (under 120 words):
        1. Recommended outfit / what to wear today.
        2. Outdoor activities suitability (e.g. running, commute, dining).
        3. One smart meteorological tip for the day.
      """.trimIndent()

      val result = AiService.generateContent(
        context = context,
        prompt = prompt,
        model = "gemini-3.8-flash",
        systemInstruction = "You are a concise, helpful meteorologist and daily lifestyle advisor. Ground all insights with live Google Search data.",
        useSearchGrounding = true
      )

      result.onSuccess { advice ->
        aiWeatherAdvice = advice
          .replace(Regex("""(?i)\n*---\s*\n*\*?Grounded with[^\n]*\*?"""), "")
          .replace(Regex("""(?i)\*?Grounded with[^\n]*\*?"""), "")
          .replace(Regex("""(?i)\n*---\s*\n*\*\*Grounded Sources[^\n]*\*\*.*""", RegexOption.DOT_MATCHES_ALL), "")
          .trim()
        isAiAdviceLoading = false
      }.onFailure {
        aiWeatherAdvice = """
          ### 🌤️ Weather Insights
          - **Outfit:** Dress comfortably for ${data.currentTemp.toInt()}°C with breathable layers.
          - **Conditions:** ${data.weatherDesc} with winds at ${data.windSpeed.toInt()} km/h.
          - **Tip:** Check precipitation probability before heading out!
        """.trimIndent()
        isAiAdviceLoading = false
      }
    }
  }

  LaunchedEffect(Unit) {
    // If location permission already granted, auto-load live location
    if (LocationHelper.hasAnyLocationPermission(context)) {
      val pref = if (LocationHelper.hasFineLocationPermission(context)) LocationAccuracyPreference.PRECISE else LocationAccuracyPreference.APPROXIMATE
      loadLiveLocation(pref)
    } else {
      loadWeather(selectedCity)
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

  val scrollState = rememberScrollState()

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    val viewportHeight = maxHeight

    // Main Weather Content State
    if (isLoading) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          Text("Fetching live meteorological data...", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        }
      }
    } else if (errorMessage != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weather Information Unavailable", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(errorMessage ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = { loadWeather(selectedCity) }) {
              Text("Retry")
            }
          }
        }
      }
    } else weatherData?.let { data ->
      val isScrolledPastHero by remember { derivedStateOf { scrollState.value > 380 } }

      Column(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth()
          .widthIn(max = 680.dp)
          .align(Alignment.TopCenter)
          .verticalScroll(scrollState)
      ) {
        // --- 1. HERO SECTION: TEMP BAR ABOVE & 3D VIEW BELOW (Fades out when scrolled down) ---
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = viewportHeight)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("fullscreen_3d_temp_window"),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Top Section: Search Bar, Notifications, & City Chips
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Top Search Row with Live Location & Push Notification Actions
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              if (onBack != null) {
                FilledTonalIconButton(
                  onClick = onBack,
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("weather_back_button")
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Reos Apps",
                    tint = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
              OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                  .weight(1f)
                  .testTag("weather_search_input"),
                placeholder = { Text("Search city (e.g. London, Tokyo)...", fontSize = 13.sp) },
                leadingIcon = {
                  Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                  if (searchQuery.isNotBlank()) {
                    IconButton(
                      onClick = {
                        keyboardController?.hide()
                        loadWeather(searchQuery)
                        searchQuery = ""
                      },
                      modifier = Modifier.testTag("weather_search_submit")
                    ) {
                      Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go", tint = MaterialTheme.colorScheme.primary)
                    }
                  }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                  focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                  unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                  if (searchQuery.isNotBlank()) {
                    keyboardController?.hide()
                    loadWeather(searchQuery)
                    searchQuery = ""
                  }
                })
              )

              // Live Location Button (Opens Location Dialog with Approximate & Precise choices)
              FilledTonalIconButton(
                onClick = { showLocationDialog = true },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("weather_live_location_button")
              ) {
                Icon(
                  imageVector = if (weatherData?.isLiveLocation == true) Icons.Default.MyLocation else Icons.Default.LocationOn,
                  contentDescription = "Use Live Location",
                  tint = if (weatherData?.isLiveLocation == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              // Push Notification Button for AI Weather
              FilledTonalIconButton(
                onClick = { triggerWeatherNotification() },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("weather_push_notification_button")
              ) {
                Icon(
                  imageVector = Icons.Default.NotificationsActive,
                  contentDescription = "Send Weather Push Notification",
                  tint = MaterialTheme.colorScheme.tertiary
                )
              }
            }

            // Notification toast indicator if triggered
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

            // Popular City Chips
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              popularCities.forEach { city ->
                val isSelected = selectedCity.startsWith(city, ignoreCase = true) && weatherData?.isLiveLocation != true
                FilterChip(
                  selected = isSelected,
                  onClick = { loadWeather(city) },
                  label = { Text(city, fontSize = 12.sp) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surface
                  ),
                  shape = RoundedCornerShape(16.dp)
                )
              }
            }
          }

          // UNIFIED HERO WINDOW: Temp bar above & 3D view below in the SAME WINDOW
          DynamicTemperatureHud(
            data = data,
            onTriggerNotification = { triggerWeatherNotification() },
            isEffectiveDay = effectiveIsDay,
            dayNightModeOverride = dayNightModeOverride,
            onToggleDayNight = {
              dayNightModeOverride = when (dayNightModeOverride) {
                null -> false // from Auto -> Force Night
                false -> true  // from Force Night -> Force Day
                true -> null  // from Force Day -> back to Auto
              }
            },
            isScreenVisible = !isScrolledPastHero
          )

          // Bottom Section: Animated Scroll Down Indicator
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            ScrollDownHintIndicator()
          }
        }

        // --- 2. OTHER INFO SECTION (Appears seamlessly when scrolled down) ---
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("weather_other_info_section"),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          // Section header with quick return to 3D View
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "Detailed Forecast & Readings",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalButton(
              onClick = { coroutineScope.launch { scrollState.animateScrollTo(0) } },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.height(30.dp)
            ) {
              Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
              Text("3D View", fontSize = 11.sp)
            }
          }

          // Atmospheric Metrics Highlights Card
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
              horizontalArrangement = Arrangement.SpaceAround
            ) {
              WeatherStatItem(Icons.Default.WaterDrop, "Humidity", "${data.humidity}%")
              WeatherStatItem(Icons.Default.Air, "Wind", "${data.windSpeed.toInt()} km/h")
              WeatherStatItem(Icons.Default.WbSunny, "UV Index", String.format(Locale.US, "%.1f", data.uvIndex))
              WeatherStatItem(Icons.Default.Compress, "Pressure", "${data.surfacePressure.toInt()} hPa")
            }
          }

          // Detailed Atmospheric Metrics Grid
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text(
                text = "Accurate Atmospheric Readings",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Wind Direction", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                  Text("${data.windDirection}° (${getWindDirectionName(data.windDirection)})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text("Peak Gusts", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                  Text("${data.windGusts.toInt()} km/h", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Sunrise", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                  Text(data.sunrise, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text("Sunset", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                  Text(data.sunset, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
              }
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Lunar & Night Astronomy", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                  ) {
                    Text(data.moonInfo.moonEmoji, fontSize = 18.sp)
                    Text(
                      "${data.moonInfo.phaseName} • ${data.moonInfo.illuminationPercent}%",
                      fontSize = 13.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                  Text(
                    data.moonInfo.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 1.dp)
                  )
                }
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = if (effectiveIsDay) Color(0x22F59E0B) else Color(0x33818CF8),
                  border = BorderStroke(1.dp, if (effectiveIsDay) Color(0xFFF59E0B) else Color(0xFF818CF8))
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(
                      if (effectiveIsDay) Icons.Default.WbSunny else Icons.Default.NightlightRound,
                      contentDescription = null,
                      tint = if (effectiveIsDay) Color(0xFFF59E0B) else Color(0xFF818CF8),
                      modifier = Modifier.size(13.dp)
                    )
                    Text(
                      if (effectiveIsDay) "Daylight Active" else "Night Active",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = if (effectiveIsDay) Color(0xFFF59E0B) else Color(0xFF818CF8)
                    )
                  }
                }
              }
            }
          }

          // Hourly Forecast with Precipitation Probability
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Hourly Outlook & Rain Probability", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Next 24h", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
              }

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                data.hourlyForecast.forEach { item ->
                  HourlyItemCard(item)
                }
              }
            }
          }

          // 7-Day Forecast
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("7-Day Accurate Outlook", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              data.dailyForecast.forEach { item ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(item.dayName, modifier = Modifier.width(64.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.precipProbMax > 20) {
                      Icon(Icons.Default.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                      Text("${item.precipProbMax}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                  }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Text("${item.maxTemp.toInt()}°", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${item.minTemp.toInt()}°", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                  }
                }
              }
            }
          }

          // Gemini AI Meteorologist Advisor Card
          Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("weather_ai_advisor_card")
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                  )
                  Text(
                    text = "AI Meteorologist Advice",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                  )
                }

                // Notification button right in AI Card
                FilledTonalButton(
                  onClick = { triggerWeatherNotification() },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.height(32.dp)
                ) {
                  Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Notify", fontSize = 11.sp)
                }
              }

              if (aiWeatherAdvice != null) {
                val displayAdvice = remember(aiWeatherAdvice) {
                  aiWeatherAdvice.orEmpty()
                    .replace(Regex("""(?i)\n*---\s*\n*\*?Grounded with[^\n]*\*?"""), "")
                    .replace(Regex("""(?i)\*?Grounded with[^\n]*\*?"""), "")
                    .replace(Regex("""(?i)\n*---\s*\n*\*\*Grounded Sources[^\n]*\*\*.*""", RegexOption.DOT_MATCHES_ALL), "")
                    .trim()
                }
                AiMarkdownView(
                  content = displayAdvice,
                  textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.fillMaxWidth()
                )
              } else {
                Text(
                  text = "Get real-time AI outfit recommendations, activity advice, and meteorological insights customized to ${data.cityName}'s live atmosphere.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
              }

              Button(
                onClick = { requestAiAdvice(data) },
                enabled = !isAiAdviceLoading,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                if (isAiAdviceLoading) {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Analyzing atmosphere...")
                } else {
                  Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (aiWeatherAdvice == null) "Ask AI Meteorologist" else "Refresh AI Insights")
                }
              }
            }
          }

          // Bottom clearance for floating bubble dock
          Spacer(Modifier.height(100.dp))
        }
      }

      // --- 3. STICKY COMPACT TOP BAR (Appears when scrolled down so city/temp info is always visible) ---
      AnimatedVisibility(
        visible = isScrolledPastHero,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = Modifier.align(Alignment.TopCenter)
      ) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
          shape = RoundedCornerShape(24.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
          shadowElevation = 8.dp
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              if (onBack != null) {
                IconButton(
                  onClick = onBack,
                  modifier = Modifier
                    .size(32.dp)
                    .testTag("weather_sticky_back_button")
                ) {
                  Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Reos Apps",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
              Icon(
                imageVector = when {
                  data.weatherCode in 0..1 -> if (effectiveIsDay) Icons.Default.WbSunny else Icons.Default.NightlightRound
                  data.weatherCode in 2..3 -> if (effectiveIsDay) Icons.Default.Cloud else Icons.Default.NightsStay
                  data.weatherCode in 51..67 || data.weatherCode in 80..82 -> Icons.Default.WaterDrop
                  data.weatherCode in 71..77 || data.weatherCode in 85..86 -> Icons.Default.AcUnit
                  data.weatherCode >= 95 -> Icons.Default.Thunderstorm
                  else -> Icons.Default.CloudQueue
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
              )
              Column {
                Text(data.cityName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${data.currentTemp.toInt()}°C • ${data.weatherDesc}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            FilledTonalButton(
              onClick = { coroutineScope.launch { scrollState.animateScrollTo(0) } },
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              shape = RoundedCornerShape(16.dp)
            ) {
              Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Back to 3D", modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("3D View", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}

fun getWindDirectionName(degrees: Int): String {
  val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
  val index = ((degrees + 11.25) / 22.5).toInt() % 16
  return directions[index]
}

@Composable
fun WeatherStatItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  value: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
  }
}

@Composable
fun HourlyItemCard(item: AiService.HourlyWeather) {
  val isNight = !item.isDay
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isNight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                       else MaterialTheme.colorScheme.surface
    ),
    border = if (isNight) BorderStroke(1.dp, Color(0x33818CF8)) else null,
    modifier = Modifier.width(68.dp)
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        item.time,
        fontSize = 11.sp,
        color = if (isNight) Color(0xFFA5B4FC) else MaterialTheme.colorScheme.outline,
        fontWeight = if (isNight) FontWeight.SemiBold else FontWeight.Normal
      )
      val icon = when {
        item.weatherCode in 0..1 -> if (item.isDay) Icons.Default.WbSunny else Icons.Default.NightlightRound
        item.weatherCode in 2..3 -> if (item.isDay) Icons.Default.Cloud else Icons.Default.NightsStay
        item.weatherCode in 51..67 || item.weatherCode in 80..82 -> Icons.Default.WaterDrop
        item.weatherCode in 71..77 || item.weatherCode in 85..86 -> Icons.Default.AcUnit
        item.weatherCode >= 95 -> Icons.Default.Thunderstorm
        else -> Icons.Default.CloudQueue
      }
      val iconTint = if (isNight) Color(0xFFA5B4FC) else MaterialTheme.colorScheme.primary
      Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
      Text("${item.temp.toInt()}°", fontWeight = FontWeight.Bold, fontSize = 13.sp)
      if (item.precipProb > 10) {
        Text("${item.precipProb}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
      } else {
        Text("0%", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
      }
    }
  }
}

@Composable
fun DynamicTemperatureHud(
  data: AiService.WeatherData,
  onTriggerNotification: () -> Unit,
  modifier: Modifier = Modifier,
  isEffectiveDay: Boolean = data.isDay,
  dayNightModeOverride: Boolean? = null,
  onToggleDayNight: () -> Unit = {},
  isScreenVisible: Boolean = true
) {
  // Dynamic Theme Accent based on Temperature & Day/Night
  val tempColor = when {
    !isEffectiveDay -> if (data.currentTemp >= 24.0) Color(0xFFC084FC) else Color(0xFF818CF8) // Mystical Moonlit Violet / Indigo
    data.currentTemp >= 26.0 -> Color(0xFFF59E0B) // Radiant Warm Amber / Orange
    data.currentTemp in 16.0..25.9 -> Color(0xFF10B981) // Pleasant Emerald / Mint
    else -> Color(0xFF38BDF8) // Crisp Alpine Cyan / Sky
  }

  val dynamicAtmosphere = if (!isEffectiveDay) {
    when {
      data.currentTemp >= 26.0 -> "Warm & Starry Tropical Night"
      data.currentTemp >= 20.0 && data.windSpeed < 15.0 -> "Pleasant Moonlit Calm"
      data.currentTemp >= 18.0 && data.windSpeed >= 15.0 -> "Breezy Night Atmosphere"
      data.currentTemp in 12.0..17.9 && data.windSpeed >= 18.0 -> "Crisp & Windy Night"
      data.currentTemp in 10.0..17.9 -> "Cool Moonlit Evening"
      data.currentTemp in 1.0..9.9 -> "Chilly Starry Night"
      data.currentTemp <= 0.0 -> "Freezing Moonlight & Frost"
      else -> "Quiet Night Atmosphere"
    }
  } else {
    when {
      data.currentTemp >= 28.0 -> "Hot & Radiant Sunshine"
      data.currentTemp >= 22.0 && data.windSpeed < 15.0 -> "Warm & Pleasant Calm"
      data.currentTemp >= 20.0 && data.windSpeed >= 15.0 -> "Warm & Breezy Atmosphere"
      data.currentTemp in 12.0..19.9 && data.windSpeed >= 18.0 -> "Cool & Brisk Winds"
      data.currentTemp in 10.0..19.9 -> "Mild & Refreshing Air"
      data.currentTemp in 1.0..9.9 -> "Chilly & Crisp Climate"
      data.currentTemp <= 0.0 -> "Freezing Sub-Zero Frost"
      else -> data.weatherDesc
    }
  }

  // Floating Glassmorphic HUD Container
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("dynamic_temperature_hud"),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    tonalElevation = 6.dp,
    shadowElevation = 8.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header: Location Name & Live Accuracy Status
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = data.cityName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (data.isLiveLocation) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.padding(top = 2.dp)
            ) {
              Icon(
                imageVector = if (data.isPreciseLocation) Icons.Default.GpsFixed else Icons.Default.NearMe,
                contentDescription = null,
                tint = if (data.isPreciseLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(13.dp)
              )
              Text(
                text = if (data.isPreciseLocation) "Live GPS • Precise (±${data.accuracyMeters.toInt()}m)"
                else "Live Area • Approximate (±${(data.accuracyMeters / 1000).toInt()}km)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (data.isPreciseLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
              )
            }
          } else {
            Text(
              text = "Live Station: ${String.format(Locale.US, "%.2f°, %.2f°", data.latitude, data.longitude)}",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.outline
            )
          }
        }

        // Weather Condition Icon Badge
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(tempColor.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center
        ) {
          val icon = when {
            data.weatherCode in 0..1 -> if (isEffectiveDay) Icons.Default.WbSunny else Icons.Default.NightlightRound
            data.weatherCode in 2..3 -> if (isEffectiveDay) Icons.Default.Cloud else Icons.Default.NightsStay
            data.weatherCode in 45..48 -> Icons.Default.BlurOn
            data.weatherCode in 51..67 || data.weatherCode in 80..82 -> Icons.Default.WaterDrop
            data.weatherCode in 71..77 || data.weatherCode in 85..86 -> Icons.Default.AcUnit
            data.weatherCode >= 95 -> Icons.Default.Thunderstorm
            else -> Icons.Default.CloudQueue
          }
          Icon(
            imageVector = icon,
            contentDescription = data.weatherDesc,
            tint = tempColor,
            modifier = Modifier.size(28.dp)
          )
        }
      }

      // Middle: Big Bold Dynamic Temperature & Status
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = "${data.currentTemp.toInt()}°",
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            color = tempColor,
            lineHeight = 64.sp
          )
          Column(modifier = Modifier.padding(bottom = 6.dp)) {
            Text(
              text = data.weatherDesc,
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Feels like ${data.apparentTemp.toInt()}°C",
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // High / Low summary chip
        val todayForecast = data.dailyForecast.firstOrNull()
        if (todayForecast != null) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text("H: ${todayForecast.maxTemp.toInt()}°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
              Text("L: ${todayForecast.minTemp.toInt()}°", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
          }
        }
      }

      // Atmospheric Status Dynamic Pill
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = tempColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tempColor.copy(alpha = 0.35f))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            PulsingStatusDot(color = tempColor)
            Text(
              text = dynamicAtmosphere,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          Text(
            text = "Wind ${data.windSpeed.toInt()} km/h",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
          )
        }
      }

      // 3D Weather View (Unified inside the SAME window, directly below the temperature bar)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(210.dp)
          .clip(RoundedCornerShape(20.dp))
          .background(
            if (isEffectiveDay) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            else Color(0xFF0F172A)
          )
          .testTag("weather_3d_embedded_view")
      ) {
        SmoothWeather3DView(
          weatherCode = data.weatherCode,
          isDay = isEffectiveDay,
          currentTemp = data.currentTemp,
          windSpeed = data.windSpeed,
          isScreenVisible = isScreenVisible,
          modifier = Modifier.fillMaxSize()
        )

        // Day / Night Switcher Pill in Top Left of 3D frame
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = when (dayNightModeOverride) {
            false -> Color(0xDD1E1B4B)
            true -> Color(0xDDFFFBEB)
            null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
          },
          border = BorderStroke(
            1.dp,
            when (dayNightModeOverride) {
              false -> Color(0xFF818CF8)
              true -> Color(0xFFF59E0B)
              null -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
          ),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clickable { onToggleDayNight() }
            .testTag("weather_day_night_toggle")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = if (isEffectiveDay) Icons.Default.WbSunny else Icons.Default.NightlightRound,
              contentDescription = "Toggle Day / Night preview",
              tint = if (isEffectiveDay) Color(0xFFF59E0B) else Color(0xFF818CF8),
              modifier = Modifier.size(12.dp)
            )
            Text(
              text = when (dayNightModeOverride) {
                false -> "🌙 Night (Forced)"
                true -> "☀️ Day (Forced)"
                null -> if (data.isDay) "☀️ Day (Live)" else "🌙 Night (Live)"
              },
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (isEffectiveDay) Color(0xFFB45309) else Color(0xFFA5B4FC)
            )
          }
        }

        // Interactive Caption Pill in top corner of 3D frame
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              Icons.Default.TouchApp,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(11.dp)
            )
            Text(
              "3D Scene • Drag to tilt",
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Quick Glance Metrics Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        WeatherStatItem(
          icon = Icons.Default.WaterDrop,
          label = "Humidity",
          value = "${data.humidity}%"
        )
        WeatherStatItem(
          icon = Icons.Default.Air,
          label = "Wind",
          value = "${data.windSpeed.toInt()} km/h"
        )
        WeatherStatItem(
          icon = Icons.Default.WbSunny,
          label = "UV Index",
          value = String.format(Locale.US, "%.1f", data.uvIndex)
        )
        WeatherStatItem(
          icon = Icons.Default.Compress,
          label = "Pressure",
          value = "${data.surfacePressure.toInt()} hPa"
        )
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun SmoothWeather3DView(
  weatherCode: Int,
  isDay: Boolean,
  modifier: Modifier = Modifier,
  currentTemp: Double = 20.0,
  windSpeed: Double = 10.0,
  isScreenVisible: Boolean = true
) {
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  // Sync meteorological condition, temperature, and wind with Three.js scene
  LaunchedEffect(weatherCode, isDay, currentTemp, windSpeed, webViewRef) {
    try {
      webViewRef?.evaluateJavascript(
        "if (window.setWeatherFromAndroid) { window.setWeatherFromAndroid($weatherCode, $isDay, $currentTemp, $windSpeed); }",
        null
      )
    } catch (_: Exception) {}
  }

  // Seamlessly pause Three.js rendering when off-screen to save 100% GPU/CPU
  LaunchedEffect(isScreenVisible, webViewRef) {
    if (isScreenVisible) {
      webViewRef?.onResume()
      webViewRef?.evaluateJavascript("if (window.setRenderPaused) { window.setRenderPaused(false); }", null)
    } else {
      webViewRef?.evaluateJavascript("if (window.setRenderPaused) { window.setRenderPaused(true); }", null)
      webViewRef?.onPause()
    }
  }

  Box(
    modifier = modifier
      .background(Color.Transparent)
      .testTag("smooth_weather_3d_view")
  ) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { ctx ->
        WebView(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          setBackgroundColor(0x00000000)
          setLayerType(View.LAYER_TYPE_NONE, null)
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
          }
          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
              safeBrowsingEnabled = false
            }
          }
          webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
              super.onPageFinished(view, url)
              try {
                view?.evaluateJavascript(
                  "if (window.setWeatherFromAndroid) { window.setWeatherFromAndroid($weatherCode, $isDay, $currentTemp, $windSpeed); }",
                  null
                )
              } catch (_: Exception) {}
            }

            override fun onRenderProcessGone(
              view: WebView?,
              detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
              try {
                (view?.parent as? ViewGroup)?.removeView(view)
                view?.destroy()
              } catch (_: Exception) {}
              webViewRef = null
              return true
            }
          }
          loadUrl("file:///android_asset/weather_3d.html")
          webViewRef = this
        }
      },
      update = { view ->
        webViewRef = view
        view.visibility = if (isScreenVisible) View.VISIBLE else View.INVISIBLE
      },
      onRelease = { view ->
        try {
          view.stopLoading()
          view.loadUrl("about:blank")
          (view.parent as? ViewGroup)?.removeView(view)
          view.destroy()
        } catch (_: Exception) {}
      }
    )
  }
}

@Composable
fun PulsingStatusDot(color: Color, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(8.dp)
      .background(color, CircleShape)
  )
}

@Composable
fun ScrollDownHintIndicator(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(
      "Scroll down for detailed forecast & other info",
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    )
    Icon(
      Icons.Default.KeyboardArrowDown,
      contentDescription = "Scroll down",
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(16.dp)
    )
  }
}
