package com.example.ai

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AiSubTool(val label: String, val icon: ImageVector) {
  WEATHER("Weather", Icons.Default.Cloud),
  CHAT("Chatbot", Icons.AutoMirrored.Filled.Chat),
  IMAGE("Image Gen", Icons.Default.Brush),
  VIDEO("Video Gen", Icons.Default.Videocam),
  MUSIC("Music Gen", Icons.Default.MusicNote),
  MAPS("AI Maps", Icons.Default.Map)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedAiScreen(
  modifier: Modifier = Modifier,
  initialFeature: String? = null
) {
  val context = LocalContext.current
  val initialTool = remember(initialFeature) {
    when (initialFeature?.lowercase()) {
      "weather" -> AiSubTool.WEATHER
      "maps", "map" -> AiSubTool.MAPS
      "image" -> AiSubTool.IMAGE
      "video" -> AiSubTool.VIDEO
      "music" -> AiSubTool.MUSIC
      "chat" -> AiSubTool.CHAT
      else -> AiSubTool.WEATHER
    }
  }
  var currentSubTool by remember { mutableStateOf(initialTool) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var showNotificationsDialog by remember { mutableStateOf(false) }
  var customApiKeyInput by remember { mutableStateOf(AiService.getEffectiveApiKey(context)) }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      NotificationHelper.sendAiNotification(
        context = context,
        title = "🔔 Reos AI Notifications Enabled!",
        message = "You will now receive real-time weather alerts and AI creation updates.",
        subText = "Push Notifications"
      )
      Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
    } else {
      Toast.makeText(context, "Notifications are disabled in system settings.", Toast.LENGTH_SHORT).show()
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // Top Bar for Advanced AI Hub
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column {
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
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
              )
            }
            Column {
              Text(
                text = "Advanced AI",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Multimodal Intelligence Suite",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Push Notification Hub button
            IconButton(
              onClick = { showNotificationsDialog = true },
              modifier = Modifier.testTag("ai_notifications_button")
            ) {
              Icon(
                Icons.Default.NotificationsActive,
                contentDescription = "AI Notifications",
                tint = MaterialTheme.colorScheme.tertiary
              )
            }
            // API Key Settings button
            IconButton(
              onClick = {
                customApiKeyInput = AiService.getEffectiveApiKey(context)
                showSettingsDialog = true
              },
              modifier = Modifier.testTag("ai_settings_button")
            ) {
              Icon(Icons.Default.Key, contentDescription = "API Key Settings", tint = MaterialTheme.colorScheme.primary)
            }
          }
        }

        // Sub-tool Navigation Chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          AiSubTool.entries.forEach { tool ->
            val isSelected = currentSubTool == tool
            FilterChip(
              selected = isSelected,
              onClick = { currentSubTool = tool },
              label = {
                Text(
                  tool.label,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
              },
              leadingIcon = {
                Icon(
                  tool.icon,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
              ),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.testTag("ai_chip_${tool.name.lowercase()}")
            )
          }
        }
      }
    }

    // Active Screen View
    Box(modifier = Modifier.weight(1f)) {
      when (currentSubTool) {
        AiSubTool.WEATHER -> WeatherScreen()
        AiSubTool.CHAT -> ChatbotScreen()
        AiSubTool.IMAGE -> ImageGenScreen()
        AiSubTool.VIDEO -> VideoGenScreen()
        AiSubTool.MUSIC -> MusicGenScreen()
        AiSubTool.MAPS -> MapsScreen()
      }
    }
  }

  // AI Push Notifications Setup Dialog
  if (showNotificationsDialog) {
    AlertDialog(
      onDismissRequest = { showNotificationsDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
          Text("AI Push Notifications", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Reos App sends push notifications for live local weather changes, meteorologist briefings, and completion alerts for AI image, video, and music creations.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          val hasPermission = NotificationHelper.hasNotificationPermission(context)
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (hasPermission) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
              else MaterialTheme.colorScheme.surfaceVariant
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(
                imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
              )
              Text(
                text = if (hasPermission) "Notifications are active & enabled" else "Permission required to receive status bar alerts",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          Button(
            onClick = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                NotificationHelper.sendAiNotification(
                  context = context,
                  title = "🚀 Reos AI Alert: Live Intelligence Ready",
                  message = "Real-time weather radar and geospatial maps are now active with live location.",
                  subText = "Test Notification"
                )
                Toast.makeText(context, "Test push notification sent!", Toast.LENGTH_SHORT).show()
              }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (hasPermission) "Send Test AI Notification" else "Enable Push Notifications")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showNotificationsDialog = false }) {
          Text("Done")
        }
      },
      shape = RoundedCornerShape(24.dp)
    )
  }

  // API Key Configuration Dialog
  if (showSettingsDialog) {
    AlertDialog(
      onDismissRequest = { showSettingsDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Gemini API Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "AI Studio automatically manages API keys through the Secrets panel. If you prefer to test with your own Google AI key, you can enter it below:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedTextField(
            value = customApiKeyInput,
            onValueChange = { customApiKeyInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Gemini API Key") },
            placeholder = { Text("AIzaSy...") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            AiService.saveCustomApiKey(context, customApiKeyInput.trim())
            showSettingsDialog = false
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSettingsDialog = false }) {
          Text("Cancel")
        }
      },
      shape = RoundedCornerShape(24.dp)
    )
  }
}
