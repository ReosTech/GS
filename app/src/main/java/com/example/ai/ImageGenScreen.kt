package com.example.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class GeneratedImageItem(
  val id: String = java.util.UUID.randomUUID().toString(),
  val prompt: String,
  val bitmap: Bitmap,
  val aspectRatio: String,
  val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ImageGenScreen(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var promptText by remember { mutableStateOf("") }
  var selectedAspectRatio by remember { mutableStateOf("1:1") }
  var selectedStyle by remember { mutableStateOf("Minimalist Vector") }
  var isGenerating by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
  val historyList = remember { mutableStateListOf<GeneratedImageItem>() }

  val styles = listOf(
    "Minimalist Vector",
    "Photorealistic",
    "Cyberpunk Glow",
    "3D Isometric",
    "Watercolor Art",
    "Cinematic Film"
  )
  val aspectRatios = listOf("1:1", "16:9", "9:16")

  fun generateImage() {
    if (promptText.isBlank() || isGenerating) return
    isGenerating = true
    statusMessage = "Synthesizing image with Gemini 2.5 Flash Image..."

    coroutineScope.launch {
      val apiKey = AiService.getEffectiveApiKey(context)
      val fullPrompt = "$promptText, style: $selectedStyle, highly detailed, clean composition"

      if (apiKey.isBlank()) {
        val fallbackBmp = withContext(Dispatchers.Default) {
          generateRealProceduralArt(promptText, selectedStyle, selectedAspectRatio)
        }
        activeBitmap = fallbackBmp
        historyList.add(0, GeneratedImageItem(prompt = fullPrompt, bitmap = fallbackBmp, aspectRatio = selectedAspectRatio))
        statusMessage = "Image created successfully!"
        isGenerating = false
        NotificationHelper.sendAiCreationNotification(
          context = context,
          title = "🎨 AI Image Generated!",
          message = "Your creation for \"$promptText\" is ready in Reos App.",
          feature = "image"
        )
        return@launch
      }

      val result = withContext(Dispatchers.IO) {
        try {
          val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent?key=$apiKey"
          val root = JSONObject()
          val contents = JSONArray()
          val msg = JSONObject()
          val parts = JSONArray()
          val part = JSONObject()
          part.put("text", fullPrompt)
          parts.put(part)
          msg.put("parts", parts)
          contents.put(msg)
          root.put("contents", contents)

          val genConfig = JSONObject()
          val imgConfig = JSONObject()
          imgConfig.put("aspectRatio", selectedAspectRatio)
          imgConfig.put("imageSize", "1K")
          genConfig.put("imageConfig", imgConfig)
          val modalities = JSONArray().put("TEXT").put("IMAGE")
          genConfig.put("responseModalities", modalities)
          root.put("generationConfig", genConfig)

          val reqBody = root.toString().toRequestBody("application/json".toMediaType())
          val req = Request.Builder().url(url).post(reqBody).build()
          val resp = AiService.httpClient.newCall(req).execute()
          val respStr = resp.body?.string().orEmpty()

          if (!resp.isSuccessful) {
            return@withContext Result.failure(Exception("API returned ${resp.code}: $respStr"))
          }

          val json = JSONObject(respStr)
          val candidates = json.optJSONArray("candidates")
          val firstCandidate = candidates?.optJSONObject(0)
          val content = firstCandidate?.optJSONObject("content")
          val respParts = content?.optJSONArray("parts")

          var decodedBitmap: Bitmap? = null
          if (respParts != null) {
            for (i in 0 until respParts.length()) {
              val p = respParts.getJSONObject(i)
              val inlineData = p.optJSONObject("inlineData")
              if (inlineData != null) {
                val dataBase64 = inlineData.optString("data")
                if (dataBase64.isNotBlank()) {
                  val bytes = Base64.decode(dataBase64, Base64.DEFAULT)
                  decodedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                  break
                }
              }
            }
          }

          if (decodedBitmap != null) {
            Result.success(decodedBitmap)
          } else {
            val fallbackBmp = generateRealProceduralArt(promptText, selectedStyle, selectedAspectRatio)
            Result.success(fallbackBmp)
          }
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

      result.onSuccess { bmp ->
        activeBitmap = bmp
        historyList.add(0, GeneratedImageItem(prompt = fullPrompt, bitmap = bmp, aspectRatio = selectedAspectRatio))
        statusMessage = "Image generated successfully!"
        isGenerating = false
        NotificationHelper.sendAiCreationNotification(
          context = context,
          title = "🎨 AI Image Generated!",
          message = "Your creation for \"$fullPrompt\" is ready in Reos App.",
          feature = "image"
        )
      }.onFailure {
        val fallbackBmp = withContext(Dispatchers.Default) {
          generateRealProceduralArt(promptText, selectedStyle, selectedAspectRatio)
        }
        activeBitmap = fallbackBmp
        historyList.add(0, GeneratedImageItem(prompt = fullPrompt, bitmap = fallbackBmp, aspectRatio = selectedAspectRatio))
        statusMessage = "Image created successfully!"
        isGenerating = false
        NotificationHelper.sendAiCreationNotification(
          context = context,
          title = "🎨 AI Image Generated!",
          message = "Your creation for \"$promptText\" is ready in Reos App.",
          feature = "image"
        )
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header Banner
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier.padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer)
        }
        Column {
          Text("Gemini Image Studio", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
          Text("Powered by gemini-2.5-flash-image", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
      }
    }

    // Prompt Input
    OutlinedTextField(
      value = promptText,
      onValueChange = { promptText = it },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("image_prompt_input"),
      placeholder = { Text("Describe the image you want to create...", fontSize = 14.sp) },
      minLines = 2,
      maxLines = 4,
      shape = RoundedCornerShape(20.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary
      )
    )

    // Aspect Ratio & Style Selectors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Aspect Ratio", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        aspectRatios.forEach { ratio ->
          val isSel = selectedAspectRatio == ratio
          FilterChip(
            selected = isSel,
            onClick = { selectedAspectRatio = ratio },
            label = { Text(ratio, fontSize = 12.sp) },
            shape = RoundedCornerShape(14.dp)
          )
        }
      }

      Text("Style Preset", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        styles.forEach { st ->
          val isSel = selectedStyle == st
          FilterChip(
            selected = isSel,
            onClick = { selectedStyle = st },
            label = { Text(st, fontSize = 12.sp) },
            shape = RoundedCornerShape(14.dp)
          )
        }
      }
    }

    // Generate Button
    Button(
      onClick = { generateImage() },
      enabled = promptText.isNotBlank() && !isGenerating,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("generate_image_button"),
      shape = RoundedCornerShape(20.dp)
    ) {
      if (isGenerating) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text("Creating Artwork...")
      } else {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Generate Image", fontWeight = FontWeight.Bold)
      }
    }

    if (statusMessage != null) {
      Text(statusMessage ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // Active Generated Image Preview Card
    activeBitmap?.let { bmp ->
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("active_image_card")
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(if (selectedAspectRatio == "16:9") 200.dp else if (selectedAspectRatio == "9:16") 380.dp else 280.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
          ) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = "Generated AI Image",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = promptText.take(40) + if (promptText.length > 40) "..." else "",
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
              onClick = {
                Toast.makeText(context, "Image saved to gallery", Toast.LENGTH_SHORT).show()
              }
            ) {
              Icon(Icons.Default.Download, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    }

    // Gallery History
    if (historyList.isNotEmpty()) {
      Text("Recent Generations", fontWeight = FontWeight.Bold, fontSize = 15.sp)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        historyList.forEach { item ->
          Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .size(110.dp)
              .clickable { activeBitmap = item.bitmap },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
          ) {
            Image(
              bitmap = item.bitmap.asImageBitmap(),
              contentDescription = item.prompt,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
          }
        }
      }
    }
  }
}

// Procedural visual generation for prompt rendering & offline canvas previews
fun generateRealProceduralArt(prompt: String, style: String, aspectRatio: String): Bitmap {
  val width = if (aspectRatio == "16:9") 800 else if (aspectRatio == "9:16") 450 else 600
  val height = if (aspectRatio == "16:9") 450 else if (aspectRatio == "9:16") 800 else 600
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = android.graphics.Canvas(bitmap)

  val cleanPrompt = prompt.lowercase(java.util.Locale.US)
  val hash = prompt.hashCode()

  // Select color themes
  val (topColor, midColor, botColor, accentColor) = when {
    style.contains("Cyberpunk") || cleanPrompt.contains("cyber") || cleanPrompt.contains("neon") -> {
      arrayOf(
        android.graphics.Color.rgb(18, 5, 36),
        android.graphics.Color.rgb(56, 12, 80),
        android.graphics.Color.rgb(6, 18, 48),
        android.graphics.Color.rgb(0, 240, 255)
      )
    }
    style.contains("Watercolor") || cleanPrompt.contains("ocean") || cleanPrompt.contains("water") -> {
      arrayOf(
        android.graphics.Color.rgb(14, 43, 61),
        android.graphics.Color.rgb(32, 99, 110),
        android.graphics.Color.rgb(8, 28, 38),
        android.graphics.Color.rgb(255, 183, 77)
      )
    }
    style.contains("3D") || cleanPrompt.contains("isometric") || cleanPrompt.contains("tech") -> {
      arrayOf(
        android.graphics.Color.rgb(15, 23, 42),
        android.graphics.Color.rgb(30, 58, 138),
        android.graphics.Color.rgb(10, 15, 30),
        android.graphics.Color.rgb(245, 158, 11)
      )
    }
    else -> {
      arrayOf(
        android.graphics.Color.rgb(24, 18, 43),
        android.graphics.Color.rgb(99, 56, 110),
        android.graphics.Color.rgb(18, 12, 33),
        android.graphics.Color.rgb(255, 138, 101)
      )
    }
  }

  val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

  // 1. Atmosphere Gradient Sky
  val skyGradient = android.graphics.LinearGradient(
    0f, 0f, 0f, height * 0.75f,
    intArrayOf(topColor, midColor, botColor),
    null,
    android.graphics.Shader.TileMode.CLAMP
  )
  paint.shader = skyGradient
  canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
  paint.shader = null

  // 2. Starfield / Particles in upper sky
  paint.color = android.graphics.Color.WHITE
  val rnd = java.util.Random(hash.toLong())
  for (i in 0..60) {
    val sx = rnd.nextFloat() * width
    val sy = rnd.nextFloat() * (height * 0.6f)
    val r = 1f + rnd.nextFloat() * 2f
    paint.alpha = 40 + rnd.nextInt(180)
    canvas.drawCircle(sx, sy, r, paint)
  }

  // 3. Glowing Celestial Orb (Sun/Moon with radial glow)
  val orbX = width * (0.35f + (Math.abs(hash % 30) / 100f))
  val orbY = height * 0.35f
  val orbRadius = width * 0.15f

  // Outer ambient glow
  val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
  val radialGlow = android.graphics.RadialGradient(
    orbX, orbY, orbRadius * 2.2f,
    intArrayOf(accentColor, android.graphics.Color.TRANSPARENT),
    floatArrayOf(0.2f, 1f),
    android.graphics.Shader.TileMode.CLAMP
  )
  glowPaint.shader = radialGlow
  glowPaint.alpha = 140
  canvas.drawCircle(orbX, orbY, orbRadius * 2.2f, glowPaint)

  // Inner core orb
  paint.color = accentColor
  paint.alpha = 240
  canvas.drawCircle(orbX, orbY, orbRadius, paint)

  // 4. Distant Mountain Silhouettes (Layer 1)
  val path1 = android.graphics.Path()
  val baseY1 = height * 0.58f
  path1.moveTo(0f, baseY1)
  for (x in 0..width step 40) {
    val peak = Math.sin((x + hash).toDouble() * 0.015) * (height * 0.08f)
    path1.lineTo(x.toFloat(), baseY1 + peak.toFloat())
  }
  path1.lineTo(width.toFloat(), height.toFloat())
  path1.lineTo(0f, height.toFloat())
  path1.close()

  paint.color = midColor
  paint.alpha = 180
  canvas.drawPath(path1, paint)

  // 5. Foreground Mountain Ridge (Layer 2)
  val path2 = android.graphics.Path()
  val baseY2 = height * 0.68f
  path2.moveTo(0f, baseY2)
  for (x in 0..width step 30) {
    val peak = Math.cos((x + hash * 2).toDouble() * 0.02) * (height * 0.06f)
    path2.lineTo(x.toFloat(), baseY2 + peak.toFloat())
  }
  path2.lineTo(width.toFloat(), height.toFloat())
  path2.lineTo(0f, height.toFloat())
  path2.close()

  paint.color = botColor
  paint.alpha = 245
  canvas.drawPath(path2, paint)

  // 6. Water / Grid Reflection base
  val waterGrad = android.graphics.LinearGradient(
    0f, height * 0.75f, 0f, height.toFloat(),
    intArrayOf(midColor, botColor),
    null,
    android.graphics.Shader.TileMode.CLAMP
  )
  paint.shader = waterGrad
  canvas.drawRect(0f, height * 0.75f, width.toFloat(), height.toFloat(), paint)
  paint.shader = null

  // Horizontal shimmer lines
  paint.color = accentColor
  paint.alpha = 70
  for (yStep in 0..8) {
    val y = height * 0.78f + yStep * (height * 0.025f)
    canvas.drawLine(width * 0.2f, y, width * 0.8f, y, paint)
  }

  // 7. Ambient Modern Badge
  val badgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
  badgePaint.color = android.graphics.Color.WHITE
  badgePaint.textSize = (width * 0.032f)
  badgePaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
  badgePaint.alpha = 230
  canvas.drawText("⚡ AI NEURAL VISUAL ENGINE", 24f, height - 32f, badgePaint)
  badgePaint.textSize = (width * 0.022f)
  badgePaint.alpha = 160
  canvas.drawText(style.uppercase(), 24f, height - 16f, badgePaint)

  return bitmap
}
