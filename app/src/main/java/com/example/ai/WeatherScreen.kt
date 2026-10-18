package com.example.ai

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
  modifier: Modifier = Modifier
) {
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

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Top Search Row with Live Location & Push Notification Actions
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
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
          ),
          shape = RoundedCornerShape(16.dp)
        )
      }
    }

    // Main Weather Content State
    if (isLoading) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(280.dp),
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
    } else weatherData?.let { data ->
      // Primary Hero Card
      Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (data.isDay) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("weather_hero_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Location Header & Live Accuracy Badge
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
                  text = "Station: ${String.format(Locale.US, "%.2f°, %.2f°", data.latitude, data.longitude)}",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.outline
                )
              }
            }

            // Weather Icon
            Box(
              modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
              contentAlignment = Alignment.Center
            ) {
              val icon = when {
                data.weatherCode in 0..1 -> if (data.isDay) Icons.Default.WbSunny else Icons.Default.NightlightRound
                data.weatherCode in 2..3 -> Icons.Default.Cloud
                data.weatherCode in 45..48 -> Icons.Default.BlurOn
                data.weatherCode in 51..67 || data.weatherCode in 80..82 -> Icons.Default.WaterDrop
                data.weatherCode in 71..77 || data.weatherCode in 85..86 -> Icons.Default.AcUnit
                data.weatherCode >= 95 -> Icons.Default.Thunderstorm
                else -> Icons.Default.CloudQueue
              }
              Icon(
                imageVector = icon,
                contentDescription = data.weatherDesc,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
              )
            }
          }

          // Large Temperature & Condition
          Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Text(
              text = "${data.currentTemp.toInt()}°",
              fontSize = 52.sp,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
              Text(
                text = data.weatherDesc,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Feels like ${data.apparentTemp.toInt()}°",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          // Quick Highlights Pill Row
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
            AiMarkdownView(
              content = aiWeatherAdvice ?: "",
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
      Spacer(Modifier.height(88.dp))
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
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.width(68.dp)
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(item.time, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
      val icon = when {
        item.weatherCode in 0..1 -> Icons.Default.WbSunny
        item.weatherCode in 2..3 -> Icons.Default.Cloud
        item.weatherCode in 51..67 || item.weatherCode in 80..82 -> Icons.Default.WaterDrop
        item.weatherCode >= 95 -> Icons.Default.Thunderstorm
        else -> Icons.Default.CloudQueue
      }
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
      Text("${item.temp.toInt()}°", fontWeight = FontWeight.Bold, fontSize = 13.sp)
      if (item.precipProb > 10) {
        Text("${item.precipProb}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
      } else {
        Text("0%", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
      }
    }
  }
}
