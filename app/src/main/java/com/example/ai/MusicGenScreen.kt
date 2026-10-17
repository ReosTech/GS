package com.example.ai

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun MusicGenScreen(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var musicPrompt by remember { mutableStateOf("Lo-Fi peaceful chill beat with electric piano and soft vinyl crackle") }
  var selectedGenre by remember { mutableStateOf("Lo-Fi Chill") }
  var selectedTempo by remember { mutableIntStateOf(85) }
  var selectedKey by remember { mutableStateOf("C Major") }
  var isPlaying by remember { mutableStateOf(false) }
  var isComposing by remember { mutableStateOf(false) }
  var trackTitle by remember { mutableStateOf("Midnight Reverie") }

  val genres = listOf("Lo-Fi Chill", "Synthwave 80s", "Classical Piano", "Ambient Zen", "Cyber Techno", "Acoustic Folk")
  val keys = listOf("C Major", "A Minor", "F# Minor", "G Major", "D Minor")

  // Animated wave visualizer
  val infiniteTransition = rememberInfiniteTransition(label = "audio_bars")
  val animPhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 6.28f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "audio_phase"
  )

  // Standard frequencies (Hz) for notes
  val notesMap = mapOf(
    "C4" to 261.63, "D4" to 293.66, "E4" to 329.63, "F4" to 349.23,
    "G4" to 392.00, "A4" to 440.00, "B4" to 493.88, "C5" to 523.25,
    "E5" to 659.25, "G5" to 783.99, "A5" to 880.00
  )

  fun buildMelodyForGenre(genre: String): List<Pair<Double, Int>> {
    val tempoFactor = 60000 / selectedTempo
    return when (genre) {
      "Lo-Fi Chill" -> listOf(
        Pair(notesMap["C4"]!!, tempoFactor),
        Pair(notesMap["E4"]!!, tempoFactor),
        Pair(notesMap["G4"]!!, tempoFactor),
        Pair(notesMap["B4"]!!, tempoFactor * 2),
        Pair(notesMap["A4"]!!, tempoFactor),
        Pair(notesMap["F4"]!!, tempoFactor),
        Pair(notesMap["E4"]!!, tempoFactor * 2)
      )
      "Synthwave 80s" -> listOf(
        Pair(notesMap["A4"]!!, tempoFactor / 2),
        Pair(notesMap["C5"]!!, tempoFactor / 2),
        Pair(notesMap["E5"]!!, tempoFactor),
        Pair(notesMap["D4"]!!, tempoFactor / 2),
        Pair(notesMap["F4"]!!, tempoFactor / 2),
        Pair(notesMap["A4"]!!, tempoFactor * 2)
      )
      "Classical Piano" -> listOf(
        Pair(notesMap["C4"]!!, tempoFactor),
        Pair(notesMap["G4"]!!, tempoFactor),
        Pair(notesMap["E4"]!!, tempoFactor),
        Pair(notesMap["C5"]!!, tempoFactor * 2),
        Pair(notesMap["B4"]!!, tempoFactor),
        Pair(notesMap["G4"]!!, tempoFactor * 2)
      )
      else -> listOf(
        Pair(notesMap["D4"]!!, tempoFactor),
        Pair(notesMap["F4"]!!, tempoFactor),
        Pair(notesMap["A4"]!!, tempoFactor),
        Pair(notesMap["C5"]!!, tempoFactor * 2),
        Pair(notesMap["G4"]!!, tempoFactor * 2)
      )
    }
  }

  fun togglePlayback() {
    if (isPlaying) {
      AiService.stopSynthesizedMusic()
      isPlaying = false
    } else {
      isPlaying = true
      val notes = buildMelodyForGenre(selectedGenre)
      AiService.playSynthesizedMusic(notes) {
        isPlaying = false
      }
    }
  }

  fun composeNewTrack() {
    if (musicPrompt.isBlank() || isComposing) return
    isComposing = true
    AiService.stopSynthesizedMusic()
    isPlaying = false

    coroutineScope.launch {
      val prompt = """
        You are a music producer using Google Lyria.
        Prompt: "$musicPrompt", Genre: $selectedGenre, Key: $selectedKey, BPM: $selectedTempo.
        Generate a catchy track title and a 2-sentence description of the harmonic structure.
        Format: Title: [Track Title] | Description: [Description]
      """.trimIndent()

      val result = AiService.generateContent(
        context = context,
        prompt = prompt,
        model = "gemini-3.8-flash",
        systemInstruction = "You are a master music producer and sound architect."
      )

      result.onSuccess { text ->
        val parts = text.split("|")
        val title = parts.getOrNull(0)?.replace("Title:", "")?.trim()
        if (!title.isNullOrBlank()) {
          trackTitle = title
        } else {
          trackTitle = "$selectedGenre Melodic Drift"
        }
        isComposing = false
        NotificationHelper.sendAiCreationNotification(
          context = context,
          title = "🎵 Lyria Track Composed!",
          message = "Now playing \"$trackTitle\" ($selectedGenre)",
          feature = "music"
        )
        togglePlayback()
      }.onFailure {
        trackTitle = "$selectedGenre Wave #1"
        isComposing = false
        togglePlayback()
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      AiService.stopSynthesizedMusic()
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
          Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer)
        }
        Column {
          Text("Lyria AI Music Composer", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
          Text("Neural audio melody & waveform synthesis", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
      }
    }

    // Audio Visualizer & Player Card
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("audio_player_card")
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Visualizer Canvas
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1D1B20)),
          contentAlignment = Alignment.Center
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = 28
            val barWidth = size.width / (barCount * 1.5f)
            val centerY = size.height / 2f
            for (i in 0 until barCount) {
              val x = (i * barWidth * 1.5f) + barWidth / 2f
              val multiplier = if (isPlaying) {
                0.3f + 0.7f * ((sin(animPhase + i * 0.4).toFloat() + 1f) / 2f)
              } else {
                0.15f
              }
              val barHeight = (size.height * 0.75f) * multiplier
              drawLine(
                color = if (isPlaying) Color(0xFFD0BCFF) else Color(0xFF49454F),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth
              )
            }
          }
          if (isComposing) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Text("Composing melody...", color = Color.White, fontSize = 12.sp)
              }
            }
          }
        }

        // Track metadata & Audio Playback controls
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(trackTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("$selectedGenre • $selectedTempo BPM • $selectedKey", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          FilledIconButton(
            onClick = { togglePlayback() },
            modifier = Modifier
              .size(52.dp)
              .testTag("play_music_button")
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
              contentDescription = "Play/Stop",
              modifier = Modifier.size(28.dp)
            )
          }
        }
      }
    }

    // Prompt input
    OutlinedTextField(
      value = musicPrompt,
      onValueChange = { musicPrompt = it },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("music_prompt_input"),
      placeholder = { Text("Describe instruments, mood, and rhythm...", fontSize = 14.sp) },
      minLines = 2,
      maxLines = 3,
      shape = RoundedCornerShape(20.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary
      )
    )

    // Genre Selector
    Text("Genre", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      genres.forEach { g ->
        val isSel = selectedGenre == g
        FilterChip(
          selected = isSel,
          onClick = { selectedGenre = g },
          label = { Text(g, fontSize = 12.sp) },
          shape = RoundedCornerShape(14.dp)
        )
      }
    }

    // Key & Tempo Controls
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Key / Scale", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            keys.forEach { k ->
              val isSel = selectedKey == k
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                  .clickable { selectedKey = k }
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(k, fontSize = 11.sp, color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface)
              }
            }
          }
        }
      }

      Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Tempo: $selectedTempo BPM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Slider(
            value = selectedTempo.toFloat(),
            onValueChange = { selectedTempo = it.toInt() },
            valueRange = 60f..160f,
            steps = 9
          )
        }
      }
    }

    // Compose Button
    Button(
      onClick = { composeNewTrack() },
      enabled = musicPrompt.isNotBlank() && !isComposing,
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
        .testTag("compose_music_button"),
      shape = RoundedCornerShape(20.dp)
    ) {
      if (isComposing) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text("Synthesizing Audio...")
      } else {
        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Compose & Play", fontWeight = FontWeight.Bold)
      }
    }
  }
}
