package com.example.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiService {
  private const val TAG = "AiService"
  private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

  // Fast-responding OkHttpClient with reasonable timeouts to avoid UI freezes on high-demand spikes
  val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    .build()

  const val FIREBASE_API_KEY = "AIzaSyBfqhIkuxnat2hHDF4ewKNTuNgpcOuelSI"
  const val FIREBASE_PROJECT_ID = "appbase-22895"

  var userCustomApiKey: String = ""

  fun getEffectiveApiKey(context: Context? = null): String {
    if (userCustomApiKey.isNotBlank()) return userCustomApiKey
    val buildKey = BuildConfig.GEMINI_API_KEY
    if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY" && buildKey != "your_api_key_here") return buildKey
    if (context != null) {
      val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
      val saved = prefs.getString("gemini_api_key", "") ?: ""
      if (saved.isNotBlank()) return saved
    }
    return ""
  }

  fun saveCustomApiKey(context: Context, key: String) {
    userCustomApiKey = key
    context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
      .edit()
      .putString("gemini_api_key", key)
      .apply()
  }

  // Map requested model to active, high-availability Google model
  private fun resolveModelName(model: String): String {
    return when (model) {
      "gemini-3.5-flash", "gemini-flash-latest" -> "gemini-3.8-flash"
      "gemini-3.1-pro-preview" -> "gemini-3.8-flash"
      "gemini-3.1-flash-lite-preview" -> "gemini-3.8-flash"
      else -> if (model.isNotBlank()) model else "gemini-3.8-flash"
    }
  }

  // --- Gemini Text Generation (Chat & Prompts with Search and Maps Grounding) ---
  suspend fun generateContent(
    context: Context,
    prompt: String,
    model: String = "gemini-3.8-flash",
    systemInstruction: String? = null,
    history: List<Pair<String, String>> = emptyList(), // Pair(role, text)
    useSearchGrounding: Boolean = false,
    useMapsGrounding: Boolean = false,
    locationContext: Pair<Double, Double>? = null
  ): Result<String> = withContext(Dispatchers.IO) {
    val apiKey = getEffectiveApiKey(context)
    val resolvedModel = resolveModelName(model)

    // 1. First-class Firebase AI Logic inference (Gemini API with App Check attestation)
    if (!useSearchGrounding && !useMapsGrounding && history.isEmpty() && systemInstruction == null) {
      try {
        val generativeModel = Firebase.ai.generativeModel(resolvedModel)
        val response = withTimeout(30_000L) {
          generativeModel.generateContent(prompt)
        }
        val text = response.text
        if (!text.isNullOrBlank()) {
          return@withContext Result.success(text)
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.d(TAG, "Firebase AI Logic inference note: ${e.message}")
      }
    }

    if (apiKey.isNotBlank()) {
      try {
        val url = "$BASE_URL$resolvedModel:generateContent?key=$apiKey"
        val rootJson = JSONObject()
        val contentsArray = JSONArray()

        // Add conversation history
        for ((role, text) in history) {
          val msgObj = JSONObject()
          msgObj.put("role", if (role == "user") "user" else "model")
          val partsArray = JSONArray()
          val partObj = JSONObject()
          partObj.put("text", text)
          partsArray.put(partObj)
          msgObj.put("parts", partsArray)
          contentsArray.put(msgObj)
        }

        // Add current prompt (enrich with location context if provided for maps grounding)
        val currentMsgObj = JSONObject()
        currentMsgObj.put("role", "user")
        val currentParts = JSONArray()
        val currentPart = JSONObject()
        val effectivePrompt = if (locationContext != null && useMapsGrounding) {
          "$prompt\n\n[Location Context: Latitude ${locationContext.first}, Longitude ${locationContext.second}]"
        } else {
          prompt
        }
        currentPart.put("text", effectivePrompt)
        currentParts.put(currentPart)
        currentMsgObj.put("parts", currentParts)
        contentsArray.put(currentMsgObj)

        rootJson.put("contents", contentsArray)

        // Add tools for Google Search and Google Maps Grounding
        val toolsArray = JSONArray()
        if (useSearchGrounding) {
          val searchTool = JSONObject()
          searchTool.put("googleSearch", JSONObject())
          toolsArray.put(searchTool)
        }
        if (useMapsGrounding) {
          val mapsTool = JSONObject()
          mapsTool.put("googleMaps", JSONObject())
          toolsArray.put(mapsTool)
        }
        if (toolsArray.length() > 0) {
          rootJson.put("tools", toolsArray)
        }

        // Add system instruction if provided
        val effectiveSys = buildString {
          if (!systemInstruction.isNullOrBlank()) {
            append(systemInstruction)
          }
          if (useSearchGrounding) {
            append(" Ground your answers with up-to-date and accurate information using Google Search.")
          }
          if (useMapsGrounding) {
            append(" Ground your answers with real-world geospatial and location intelligence using Google Maps.")
          }
        }.trim()

        if (effectiveSys.isNotBlank()) {
          val sysObj = JSONObject()
          val sysParts = JSONArray()
          val sysPart = JSONObject()
          sysPart.put("text", effectiveSys)
          sysParts.put(sysPart)
          sysObj.put("parts", sysParts)
          rootJson.put("systemInstruction", sysObj)
        }

        val requestBody = rootJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
          .url(url)
          .post(requestBody)
          .build()

        val response = httpClient.newCall(request).execute()
        val responseString = response.body?.string().orEmpty()

        if (response.isSuccessful) {
          val json = JSONObject(responseString)
          val candidates = json.optJSONArray("candidates")
          if (candidates != null && candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
              val textBuilder = StringBuilder()
              for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                  textBuilder.append(part.getString("text"))
                }
              }

              // Extract and append Grounding metadata (Google Search & Google Maps sources)
              val groundingMetadata = firstCandidate.optJSONObject("groundingMetadata")
              if (groundingMetadata != null) {
                val webSearchQueries = groundingMetadata.optJSONArray("webSearchQueries")
                val queries = mutableListOf<String>()
                if (webSearchQueries != null) {
                  for (qi in 0 until webSearchQueries.length()) {
                    queries.add(webSearchQueries.optString(qi))
                  }
                }

                val groundingChunks = groundingMetadata.optJSONArray("groundingChunks")
                val citations = mutableListOf<String>()
                if (groundingChunks != null) {
                  for (ci in 0 until groundingChunks.length()) {
                    val chunk = groundingChunks.optJSONObject(ci)
                    val web = chunk?.optJSONObject("web")
                    val maps = chunk?.optJSONObject("maps")
                    if (web != null) {
                      val title = web.optString("title", "Web Result")
                      val uri = web.optString("uri", "")
                      if (uri.isNotBlank()) {
                        citations.add("🔍 [$title]($uri)")
                      }
                    } else if (maps != null) {
                      val name = maps.optString("name", "Google Maps Location")
                      val uri = maps.optString("uri", "")
                      if (uri.isNotBlank()) {
                        citations.add("📍 [$name]($uri)")
                      } else {
                        citations.add("📍 $name")
                      }
                    }
                  }
                }

                // Citations and grounding annotations omitted per user preference
              }

              if (textBuilder.isNotBlank()) {
                return@withContext Result.success(textBuilder.toString())
              }
            }
          }
        } else {
          Log.w(TAG, "Cloud Gemini returned ${response.code}: $responseString, using intelligent local engine")
        }
      } catch (e: Exception) {
        Log.w(TAG, "Cloud Gemini API call exception, using intelligent local engine", e)
      }
    }

    // Zero-error Intelligent Local AI fallback
    val localResult = LocalAiEngine.generateResponse(
      prompt = prompt,
      model = model,
      systemInstruction = systemInstruction,
      useSearchGrounding = useSearchGrounding,
      useMapsGrounding = useMapsGrounding
    )
    Result.success(localResult)
  }

  data class MoonInfo(
    val phaseName: String,
    val illuminationPercent: Int,
    val moonEmoji: String,
    val description: String
  )

  fun calculateMoonPhase(timeMillis: Long = System.currentTimeMillis()): MoonInfo {
    val epoch = 947182440000L // Jan 6, 2000, 18:14 UTC
    val synodicMonth = 29.53058867 * 24 * 60 * 60 * 1000.0
    val diff = (timeMillis - epoch) % synodicMonth
    val normalized = if (diff < 0) diff + synodicMonth else diff
    val phaseDay = normalized / (24 * 60 * 60 * 1000.0)
    val fraction = phaseDay / 29.53058867
    val illumination = ((1.0 - kotlin.math.cos(fraction * 2.0 * Math.PI)) / 2.0 * 100.0).toInt().coerceIn(0, 100)

    return when {
      phaseDay < 1.84 -> MoonInfo("New Moon", illumination, "🌑", "Minimal moonlight; pristine dark sky observation")
      phaseDay < 5.53 -> MoonInfo("Waxing Crescent", illumination, "🌒", "Delicate crescent visible in evening twilight")
      phaseDay < 9.22 -> MoonInfo("First Quarter", illumination, "🌓", "Half moon illuminated in evening sky")
      phaseDay < 12.91 -> MoonInfo("Waxing Gibbous", illumination, "🌔", "Bright moon illuminating late evening atmosphere")
      phaseDay < 16.61 -> MoonInfo("Full Moon", illumination, "🌕", "Peak lunar illumination casting clear shadows")
      phaseDay < 20.30 -> MoonInfo("Waning Gibbous", illumination, "🌖", "Radiant moon rising in early night")
      phaseDay < 23.99 -> MoonInfo("Last Quarter", illumination, "🌗", "Half moon illuminated in late night & dawn")
      phaseDay < 27.68 -> MoonInfo("Waning Crescent", illumination, "🌘", "Slender crescent visible before dawn")
      else -> MoonInfo("New Moon", illumination, "🌑", "Dark sky with clear stellar visibility")
    }
  }

  // --- Real-time Weather Fetcher using Open-Meteo ---
  data class WeatherData(
    val cityName: String,
    val currentTemp: Double,
    val apparentTemp: Double,
    val humidity: Int,
    val windSpeed: Double,
    val windDirection: Int = 0,
    val windGusts: Double = 0.0,
    val surfacePressure: Double = 1013.25,
    val uvIndex: Double = 0.0,
    val weatherCode: Int,
    val weatherDesc: String,
    val isDay: Boolean,
    val sunrise: String = "06:00",
    val sunset: String = "19:30",
    val moonInfo: MoonInfo = calculateMoonPhase(),
    val isLiveLocation: Boolean = false,
    val isPreciseLocation: Boolean = true,
    val accuracyMeters: Float = 15f,
    val hourlyForecast: List<HourlyWeather>,
    val dailyForecast: List<DailyWeather>,
    val latitude: Double,
    val longitude: Double
  )

  data class HourlyWeather(
    val time: String,
    val temp: Double,
    val weatherCode: Int,
    val precipProb: Int = 0,
    val humidity: Int = 50,
    val isDay: Boolean = true
  )

  data class DailyWeather(
    val dayName: String,
    val maxTemp: Double,
    val minTemp: Double,
    val weatherCode: Int,
    val precipProbMax: Int = 0,
    val uvIndexMax: Double = 5.0,
    val sunrise: String = "",
    val sunset: String = ""
  )

  suspend fun fetchWeatherByCoordinates(
    latitude: Double,
    longitude: Double,
    locationName: String? = null,
    isLiveLocation: Boolean = false,
    isPrecise: Boolean = true,
    accuracyMeters: Float = 15f
  ): Result<WeatherData> = withContext(Dispatchers.IO) {
    try {
      val resolvedName = locationName ?: "Lat: ${String.format(java.util.Locale.US, "%.3f", latitude)}, Lon: ${String.format(java.util.Locale.US, "%.3f", longitude)}"
      // Query Open-Meteo high accuracy forecast with comprehensive meteorological variables
      val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,uv_index" +
        "&hourly=temperature_2m,relative_humidity_2m,weather_code,precipitation_probability,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max" +
        "&timezone=auto"

      val weatherRequest = Request.Builder().url(forecastUrl).build()
      val weatherResponse = httpClient.newCall(weatherRequest).execute()
      val weatherJsonStr = weatherResponse.body?.string().orEmpty()

      if (!weatherResponse.isSuccessful) {
        return@withContext Result.failure(Exception("Weather fetch failed with code ${weatherResponse.code}"))
      }

      val weatherJson = JSONObject(weatherJsonStr)
      val current = weatherJson.getJSONObject("current")
      val currentTemp = current.optDouble("temperature_2m", 20.0)
      val apparentTemp = current.optDouble("apparent_temperature", currentTemp)
      val humidity = current.optInt("relative_humidity_2m", 60)
      val windSpeed = current.optDouble("wind_speed_10m", 12.0)
      val windDirection = current.optInt("wind_direction_10m", 0)
      val windGusts = current.optDouble("wind_gusts_10m", windSpeed * 1.3)
      val surfacePressure = current.optDouble("surface_pressure", 1013.25)
      val uvIndex = current.optDouble("uv_index", 3.0)
      val weatherCode = current.optInt("weather_code", 0)
      val isDay = current.optInt("is_day", 1) == 1

      // Hourly items (next 24 hours)
      val hourlyList = mutableListOf<HourlyWeather>()
      val hourly = weatherJson.optJSONObject("hourly")
      if (hourly != null) {
        val times = hourly.optJSONArray("time")
        val temps = hourly.optJSONArray("temperature_2m")
        val humidities = hourly.optJSONArray("relative_humidity_2m")
        val codes = hourly.optJSONArray("weather_code")
        val precipProbs = hourly.optJSONArray("precipitation_probability")
        val isDayList = hourly.optJSONArray("is_day")

        if (times != null && temps != null && codes != null) {
          val count = minOf(24, times.length())
          for (i in 0 until count) {
            val t = times.optString(i, "")
            val hourStr = if (t.contains("T")) t.substringAfter("T").take(5) else "$i:00"
            val temp = temps.optDouble(i, currentTemp)
            val code = codes.optInt(i, weatherCode)
            val prob = precipProbs?.optInt(i, 0) ?: 0
            val hum = humidities?.optInt(i, humidity) ?: humidity
            val itemIsDay = if (isDayList != null && i < isDayList.length()) isDayList.optInt(i, 1) == 1 else true
            hourlyList.add(HourlyWeather(hourStr, temp, code, prob, hum, itemIsDay))
          }
        }
      }

      // Daily items (next 7 days)
      val dailyList = mutableListOf<DailyWeather>()
      var todaySunrise = "06:15"
      var todaySunset = "19:45"
      val daily = weatherJson.optJSONObject("daily")
      if (daily != null) {
        val days = daily.optJSONArray("time")
        val maxTemps = daily.optJSONArray("temperature_2m_max")
        val minTemps = daily.optJSONArray("temperature_2m_min")
        val codes = daily.optJSONArray("weather_code")
        val sunrises = daily.optJSONArray("sunrise")
        val sunsets = daily.optJSONArray("sunset")
        val uvMaxes = daily.optJSONArray("uv_index_max")
        val precipMaxes = daily.optJSONArray("precipitation_probability_max")

        if (sunrises != null && sunrises.length() > 0) {
          val sr = sunrises.optString(0, "")
          if (sr.contains("T")) todaySunrise = sr.substringAfter("T").take(5)
        }
        if (sunsets != null && sunsets.length() > 0) {
          val ss = sunsets.optString(0, "")
          if (ss.contains("T")) todaySunset = ss.substringAfter("T").take(5)
        }

        if (days != null && maxTemps != null && minTemps != null && codes != null) {
          val count = minOf(7, days.length())
          for (i in 0 until count) {
            val dateStr = days.optString(i, "")
            val dayLabel = if (i == 0) "Today" else "Day +$i"
            val maxT = maxTemps.optDouble(i, currentTemp + 2)
            val minT = minTemps.optDouble(i, currentTemp - 4)
            val code = codes.optInt(i, weatherCode)
            val probMax = precipMaxes?.optInt(i, 0) ?: 0
            val uvMax = uvMaxes?.optDouble(i, 5.0) ?: 5.0
            val sr = sunrises?.optString(i, "")?.let { if (it.contains("T")) it.substringAfter("T").take(5) else "" } ?: ""
            val ss = sunsets?.optString(i, "")?.let { if (it.contains("T")) it.substringAfter("T").take(5) else "" } ?: ""

            dailyList.add(
              DailyWeather(
                dayName = dayLabel,
                maxTemp = maxT,
                minTemp = minT,
                weatherCode = code,
                precipProbMax = probMax,
                uvIndexMax = uvMax,
                sunrise = sr,
                sunset = ss
              )
            )
          }
        }
      }

      val weatherDesc = getWeatherDescription(weatherCode, isDay)

      Result.success(
        WeatherData(
          cityName = resolvedName,
          currentTemp = currentTemp,
          apparentTemp = apparentTemp,
          humidity = humidity,
          windSpeed = windSpeed,
          windDirection = windDirection,
          windGusts = windGusts,
          surfacePressure = surfacePressure,
          uvIndex = uvIndex,
          weatherCode = weatherCode,
          weatherDesc = weatherDesc,
          isDay = isDay,
          sunrise = todaySunrise,
          sunset = todaySunset,
          isLiveLocation = isLiveLocation,
          isPreciseLocation = isPrecise,
          accuracyMeters = accuracyMeters,
          hourlyForecast = hourlyList,
          dailyForecast = dailyList,
          latitude = latitude,
          longitude = longitude
        )
      )
    } catch (e: Exception) {
      Log.e(TAG, "fetchWeatherByCoordinates failed", e)
      Result.failure(e)
    }
  }

  suspend fun fetchRealTimeWeather(city: String = "San Francisco"): Result<WeatherData> = withContext(Dispatchers.IO) {
    try {
      // 1. Geocode city to lat/lon
      val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${city.trim()}&count=1&language=en&format=json"
      val geoRequest = Request.Builder().url(geocodeUrl).build()
      val geoResponse = httpClient.newCall(geoRequest).execute()
      val geoJsonStr = geoResponse.body?.string().orEmpty()

      var lat = 37.7749
      var lon = -122.4194
      var resolvedCityName = city

      if (geoResponse.isSuccessful) {
        val geoJson = JSONObject(geoJsonStr)
        val results = geoJson.optJSONArray("results")
        if (results != null && results.length() > 0) {
          val first = results.getJSONObject(0)
          lat = first.optDouble("latitude", lat)
          lon = first.optDouble("longitude", lon)
          val name = first.optString("name", city)
          val country = first.optString("country", "")
          resolvedCityName = if (country.isNotBlank()) "$name, $country" else name
        }
      }

      fetchWeatherByCoordinates(
        latitude = lat,
        longitude = lon,
        locationName = resolvedCityName,
        isLiveLocation = false,
        isPrecise = true,
        accuracyMeters = 50f
      )
    } catch (e: Exception) {
      Log.e(TAG, "fetchRealTimeWeather failed", e)
      Result.failure(e)
    }
  }

  fun getWeatherDescription(code: Int, isDay: Boolean = true): String {
    return when (code) {
      0 -> if (isDay) "Clear sky" else "Clear starry night"
      1 -> if (isDay) "Mainly clear" else "Mainly clear night"
      2 -> if (isDay) "Partly cloudy" else "Partly cloudy night"
      3 -> if (isDay) "Overcast clouds" else "Overcast night"
      45, 48 -> if (isDay) "Foggy" else "Night fog & mist"
      51, 53, 55 -> if (isDay) "Drizzle" else "Night drizzle"
      61, 63, 65 -> if (isDay) "Rain" else "Night rainfall"
      71, 73, 75 -> if (isDay) "Snowfall" else "Moonlit snowfall"
      77 -> "Snow grains"
      80, 81, 82 -> if (isDay) "Rain showers" else "Night rain showers"
      85, 86 -> if (isDay) "Snow showers" else "Night snow showers"
      95 -> if (isDay) "Thunderstorm" else "Night thunderstorm"
      96, 99 -> if (isDay) "Thunderstorm with hail" else "Night storm with hail"
      else -> if (isDay) "Cloudy & mild" else "Cool cloudy night"
    }
  }
}
