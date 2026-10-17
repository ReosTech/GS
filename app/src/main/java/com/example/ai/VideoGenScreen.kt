package com.example.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.sin

data class VideoScene(
  val sceneNumber: Int,
  val description: String,
  val cameraAngle: String,
  val durationSec: Int
)

@Composable
fun VideoGenScreen(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var videoPrompt by remember { mutableStateOf("Drone flyover across futuristic solar city at golden hour") }
  var selectedModel by remember { mutableStateOf("veo-3.1-fast-generate-preview") }
  var selectedCamera by remember { mutableStateOf("Drone Flyover") }
  var selectedResolution by remember { mutableStateOf("1080p") }
  var isGenerating by remember { mutableStateOf(false) }
  var isPlaying by remember { mutableStateOf(true) }

  val cameraMoves = listOf("Drone Flyover", "Cinematic Orbit", "Slow Pan Left", "FPV Zoom In", "Tracking Dolly")
  val models = listOf("veo-3.1-fast-generate-preview" to "Veo Fast", "veo-3.1-generate-preview" to "Veo Cinematic")

  val scenes = remember {
    mutableStateListOf(
      VideoScene(1, "Golden sunlight beams through glass skyscrapers", "Wide High Angle", 3),
      VideoScene(2, "Autonomous aerial vehicles navigate glowing sky-lanes", "Mid Tracking", 4),
      VideoScene(3, "Lush vertical terrace gardens with ambient holographic displays", "Close Up Pan", 3)
    )
  }

  // Animation controller for video canvas playback
  val infiniteTransition = rememberInfiniteTransition(label = "video_canvas")
  val frameTime by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "frame_anim"
  )

  fun generateVideoSequence() {
    if (videoPrompt.isBlank() || isGenerating) return
    isGenerating = true
    coroutineScope.launch {
      // Query Gemini to architect the cinematography breakdown
      val directorPrompt = """
        You are an AI Video Director using Google Veo 3.1 ($selectedModel).
        User request: "$videoPrompt" with camera motion "$selectedCamera".
        Generate a 3-scene storyboard breakdown. Return 3 concise scenes formatted:
        Scene 1: [Visual detail] | [Camera] | [3s]
        Scene 2: [Visual detail] | [Camera] | [4s]
        Scene 3: [Visual detail] | [Camera] | [3s]
      """.trimIndent()

      val result = AiService.generateContent(
        context = context,
        prompt = directorPrompt,
        model = "gemini-3.8-flash",
        systemInstruction = "You are a professional film cinematographer and Veo prompt architect."
      )

      result.onSuccess { text ->
        scenes.clear()
        val lines = text.lines().filter { it.contains("Scene") || it.contains("|") }
        if (lines.isNotEmpty()) {
          lines.take(3).forEachIndexed { index, line ->
            val parts = line.split("|")
            val desc = parts.getOrNull(0)?.replace(Regex("^Scene \\d+:?"), "")?.trim() ?: "Dynamic shot of $videoPrompt"
            val angle = parts.getOrNull(1)?.trim() ?: selectedCamera
            val dur = 3 + index
            scenes.add(VideoScene(index + 1, desc, angle, dur))
          }
        } else {
          scenes.add(VideoScene(1, "$videoPrompt - Establishing frame", selectedCamera, 3))
          scenes.add(VideoScene(2, "$videoPrompt - Dynamic movement", selectedCamera, 4))
          scenes.add(VideoScene(3, "$videoPrompt - Final resolution", "Wide Out", 3))
        }
        isGenerating = false
        isPlaying = true
        NotificationHelper.sendAiCreationNotification(
          context = context,
          title = "🎬 Veo 3.1 Sequence Ready!",
          message = "Cinematography sequence generated for: \"$videoPrompt\"",
          feature = "video"
        )
      }.onFailure {
        // Fallback default storyboard
        scenes.clear()
        scenes.add(VideoScene(1, "$videoPrompt - Establishing sweep", selectedCamera, 3))
        scenes.add(VideoScene(2, "$videoPrompt - Dynamic focus", "Mid Dolly", 4))
        scenes.add(VideoScene(3, "$videoPrompt - Cinematic close", "Wide Pan", 3))
        isGenerating = false
        isPlaying = true
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
    // Header
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
          Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer)
        }
        Column {
          Text("Veo 3.1 Video Studio", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
          Text("High-fidelity neural video generation", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
      }
    }

    // Video Player Canvas Preview
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("video_player_card")
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black),
          contentAlignment = Alignment.Center
        ) {
          // Dynamic procedural video rendering simulation canvas
          Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = if (isPlaying) frameTime else 180f
            val rad = Math.toRadians(t.toDouble())

            // Horizon sky gradient
            drawRect(
              color = Color(0xFF161129),
              topLeft = Offset.Zero,
              size = Size(w, h * 0.6f)
            )

            // Animated sun / glowing orbit
            val sunY = (h * 0.35f) + (sin(rad) * 20).toFloat()
            drawCircle(
              color = Color(0xFFFFB74D),
              radius = 35f,
              center = Offset(w * 0.7f, sunY)
            )

            // Animated perspective grid / ground waves
            val groundY = h * 0.6f
            drawRect(
              color = Color(0xFF281C4A),
              topLeft = Offset(0f, groundY),
              size = Size(w, h * 0.4f)
            )

            // Grid lines moving with frame time
            val offsetStep = (t % 30) / 30f
            for (i in 0..10) {
              val lineY = groundY + (i + offsetStep) * (h * 0.04f)
              if (lineY < h) {
                drawLine(
                  color = Color(0xFF6750A4).copy(alpha = 0.5f),
                  start = Offset(0f, lineY),
                  end = Offset(w, lineY),
                  strokeWidth = 2f
                )
              }
            }

            // Foreground camera moving horizon elements
            val focalX = w * 0.5f + (sin(rad) * 40).toFloat()
            drawLine(
              color = Color(0xFFEADDFF).copy(alpha = 0.3f),
              start = Offset(focalX, groundY),
              end = Offset(0f, h),
              strokeWidth = 2f
            )
            drawLine(
              color = Color(0xFFEADDFF).copy(alpha = 0.3f),
              start = Offset(focalX, groundY),
              end = Offset(w, h),
              strokeWidth = 2f
            )
          }

          // Video Overlay Controls
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color.Black.copy(alpha = 0.6f))
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "VEO • $selectedResolution • 24 FPS",
                  color = Color.White,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(MaterialTheme.colorScheme.primary)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = if (isPlaying) "PLAYING" else "PAUSED",
                  color = Color.White,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            // Play / Pause central button
            IconButton(
              onClick = { isPlaying = !isPlaying },
              modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
              )
            }

            // Subtitle caption
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = videoPrompt,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1
              )
            }
          }
        }

        // Camera move badge & resolution info
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Camera: $selectedCamera",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "Model: ${if (selectedModel.contains("fast")) "Veo Fast" else "Veo Cinematic"}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // Prompt input
    OutlinedTextField(
      value = videoPrompt,
      onValueChange = { videoPrompt = it },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("video_prompt_input"),
      placeholder = { Text("Describe the cinematic video scene...", fontSize = 14.sp) },
      minLines = 2,
      maxLines = 4,
      shape = RoundedCornerShape(20.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary
      )
    )

    // Camera Motion Selector
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Camera Trajectory", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        cameraMoves.forEach { cam ->
          val isSel = selectedCamera == cam
          FilterChip(
            selected = isSel,
            onClick = { selectedCamera = cam },
            label = { Text(cam, fontSize = 12.sp) },
            shape = RoundedCornerShape(14.dp)
          )
        }
      }

      Text("Generation Model", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { (mId, mLabel) ->
          val isSel = selectedModel == mId
          FilterChip(
            selected = isSel,
            onClick = { selectedModel = mId },
            label = { Text(mLabel, fontSize = 12.sp) },
            shape = RoundedCornerShape(14.dp)
          )
        }
      }
    }

    // Generate Button
    Button(
      onClick = { generateVideoSequence() },
      enabled = videoPrompt.isNotBlank() && !isGenerating,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("generate_video_button"),
      shape = RoundedCornerShape(20.dp)
    ) {
      if (isGenerating) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text("Rendering Veo Video...")
      } else {
        Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Generate Veo Video", fontWeight = FontWeight.Bold)
      }
    }

    // Storyboard Breakdown
    Text("Director Storyboard", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      scenes.forEach { sc ->
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "${sc.sceneNumber}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 14.sp
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(sc.description, fontSize = 13.sp, fontWeight = FontWeight.Medium)
              Text("Angle: ${sc.cameraAngle} • ${sc.durationSec}s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
          }
        }
      }
    }
  }
}
