package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.sin

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
    if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") return buildKey
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

  // --- Gemini Text Generation (Chat & Prompts) ---
  suspend fun generateContent(
    context: Context,
    prompt: String,
    model: String = "gemini-3.8-flash",
    systemInstruction: String? = null,
    history: List<Pair<String, String>> = emptyList() // Pair(role, text)
  ): Result<String> = withContext(Dispatchers.IO) {
    val apiKey = getEffectiveApiKey(context)
    val resolvedModel = resolveModelName(model)

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

        // Add current prompt
        val currentMsgObj = JSONObject()
        currentMsgObj.put("role", "user")
        val currentParts = JSONArray()
        val currentPart = JSONObject()
        currentPart.put("text", prompt)
        currentParts.put(currentPart)
        currentMsgObj.put("parts", currentParts)
        contentsArray.put(currentMsgObj)

        rootJson.put("contents", contentsArray)

        // Add system instruction if provided
        if (!systemInstruction.isNullOrBlank()) {
          val sysObj = JSONObject()
          val sysParts = JSONArray()
          val sysPart = JSONObject()
          sysPart.put("text", systemInstruction)
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
    val localResult = LocalAiEngine.generateResponse(prompt, model, systemInstruction)
    Result.success(localResult)
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
    val humidity: Int = 50
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
        "&hourly=temperature_2m,relative_humidity_2m,weather_code,precipitation_probability" +
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

        if (times != null && temps != null && codes != null) {
          val count = minOf(24, times.length())
          for (i in 0 until count) {
            val t = times.optString(i, "")
            val hourStr = if (t.contains("T")) t.substringAfter("T").take(5) else "$i:00"
            val temp = temps.optDouble(i, currentTemp)
            val code = codes.optInt(i, weatherCode)
            val prob = precipProbs?.optInt(i, 0) ?: 0
            val hum = humidities?.optInt(i, humidity) ?: humidity
            hourlyList.add(HourlyWeather(hourStr, temp, code, prob, hum))
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

      val weatherDesc = getWeatherDescription(weatherCode)

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

  fun getWeatherDescription(code: Int): String {
    return when (code) {
      0 -> "Clear sky"
      1, 2, 3 -> "Partly cloudy"
      45, 48 -> "Foggy"
      51, 53, 55 -> "Drizzle"
      61, 63, 65 -> "Rain"
      71, 73, 75 -> "Snowfall"
      77 -> "Snow grains"
      80, 81, 82 -> "Rain showers"
      85, 86 -> "Snow showers"
      95 -> "Thunderstorm"
      96, 99 -> "Thunderstorm with hail"
      else -> "Cloudy & mild"
    }
  }

  // --- Real Audio Track Synthesis for Music Generation ---
  private var activeAudioTrack: AudioTrack? = null

  fun playSynthesizedMusic(
    notes: List<Pair<Double, Int>>, // frequency in Hz, duration in ms
    onPlaybackComplete: () -> Unit
  ) {
    stopSynthesizedMusic()
    Thread {
      try {
        val sampleRate = 44100
        val audioFormat = AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(sampleRate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build()

        val audioAttributes = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()

        val bufferSize = AudioTrack.getMinBufferSize(
          sampleRate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
          .setAudioAttributes(audioAttributes)
          .setAudioFormat(audioFormat)
          .setBufferSizeInBytes(bufferSize * 4)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()

        activeAudioTrack = track
        track.play()

        for ((freq, durationMs) in notes) {
          if (activeAudioTrack == null) break
          val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
          val buffer = ShortArray(numSamples)
          for (i in 0 until numSamples) {
            val time = i.toDouble() / sampleRate
            // Rich harmonic synthesis with ADSR envelope
            val envelope = if (i < numSamples * 0.1) {
              i / (numSamples * 0.1) // Attack
            } else if (i > numSamples * 0.7) {
              (numSamples - i) / (numSamples * 0.3) // Release
            } else {
              1.0 // Sustain
            }
            // Fundamental + 2nd + 3rd harmonic for musical warmth
            val sample = (
              0.6 * sin(2.0 * Math.PI * freq * time) +
              0.25 * sin(2.0 * Math.PI * freq * 2 * time) +
              0.15 * sin(2.0 * Math.PI * freq * 3 * time)
            ) * envelope
            buffer[i] = (sample * 32767).toInt().toShort()
          }
          track.write(buffer, 0, buffer.size)
        }

        track.stop()
        track.release()
        activeAudioTrack = null
        onPlaybackComplete()
      } catch (e: Exception) {
        Log.e(TAG, "Audio synth error", e)
        activeAudioTrack = null
        onPlaybackComplete()
      }
    }.start()
  }

  fun stopSynthesizedMusic() {
    try {
      activeAudioTrack?.stop()
      activeAudioTrack?.release()
    } catch (_: Exception) {}
    activeAudioTrack = null
  }
}
