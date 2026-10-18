package com.example.addons

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai.AiMarkdownView
import com.example.ai.NotificationHelper
import com.example.firebase.FirebaseManager
import com.example.firebase.GoogleSignInHelper
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

data class DispatchedMessage(
  val id: String = java.util.UUID.randomUUID().toString(),
  val recipient: String,
  val text: String,
  val timestamp: Long = System.currentTimeMillis(),
  val viaSmsManager: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonsScreen(
  modifier: Modifier = Modifier,
  onNavigateToWeather: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val coroutineScope = rememberCoroutineScope()

  // SharedPreferences for persistent addon toggles
  val prefs = remember { context.getSharedPreferences("reos_addons_prefs", Context.MODE_PRIVATE) }

  // Firebase Auth & Cloud Firestore State
  var currentUser by remember { mutableStateOf<FirebaseUser?>(FirebaseManager.getCurrentUser()) }
  var isSigningInGoogle by remember { mutableStateOf(false) }
  var isSyncingFirestore by remember { mutableStateOf(false) }

  // Requested Add-ons: More Connected & Experience+
  var moreConnectedEnabled by remember {
    mutableStateOf(prefs.getBoolean("addon_more_connected", true))
  }
  var experiencePlusEnabled by remember {
    mutableStateOf(prefs.getBoolean("addon_experience_plus", true))
  }

  // Requested Add-on: Email Access
  var emailAccessEnabled by remember {
    mutableStateOf(prefs.getBoolean("addon_email_access", false))
  }
  var verifiedAccountEmail by remember {
    mutableStateOf(prefs.getString("addon_verified_email", null))
  }
  var verifiedAccountUser by remember {
    mutableStateOf(prefs.getString("addon_verified_user", null))
  }
  var showEmailSetupDialog by remember { mutableStateOf(false) }
  var isVerifyingAccount by remember { mutableStateOf(false) }
  var isFetchingEmails by remember { mutableStateOf(false) }
  var emailAuthError by remember { mutableStateOf<String?>(null) }
  val fetchedEmails = remember { mutableStateListOf<ReosEmailItem>() }
  var accountUsernameInput by remember { mutableStateOf("") }
  var accountPasswordInput by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }

  // Addon Extension Toggles
  var weatherSmsBroadcastEnabled by remember {
    mutableStateOf(prefs.getBoolean("addon_weather_sms", false))
  }
  var stormAlertsEnabled by remember {
    mutableStateOf(prefs.getBoolean("addon_storm_alerts", true))
  }
  var offlineCacheEnabled by remember {
    mutableStateOf(prefs.getBoolean("addon_offline_cache", true))
  }
  var highContrastEnabled by remember {
    mutableStateOf(prefs.getBoolean("addon_high_contrast", false))
  }

  // Real-time Permission States
  var hasSmsPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    )
  }
  var hasNotificationPermission by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      } else true
    )
  }
  var hasLocationPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    )
  }
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }

  // SMS Dispatcher Form State
  var recipientNumber by remember { mutableStateOf("") }
  var messageBody by remember { mutableStateOf("Reos Weather Alert: Clear skies today at 22°C with light wind. Stay hydrated!") }
  var showMarkdownPreview by remember { mutableStateOf(false) }
  val dispatchedMessages = remember { mutableStateListOf<DispatchedMessage>() }

  // Permission Request Launchers
  val smsPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    hasSmsPermission = perms[Manifest.permission.SEND_SMS] == true
    val granted = perms[Manifest.permission.SEND_SMS] == true
    Toast.makeText(
      context,
      if (granted) "Messages & SMS permission granted!" else "SMS permission not granted. You can still use the system Messages app.",
      Toast.LENGTH_SHORT
    ).show()
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasNotificationPermission = granted
    if (granted) {
      NotificationHelper.sendAiNotification(
        context = context,
        title = "🔔 Reos Notifications Active",
        message = "You will receive real-time weather and addon alerts.",
        subText = "Add-ons"
      )
      Toast.makeText(context, "Notification permission granted!", Toast.LENGTH_SHORT).show()
    } else {
      Toast.makeText(context, "Notifications are disabled.", Toast.LENGTH_SHORT).show()
    }
  }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { perms ->
    hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    Toast.makeText(
      context,
      if (hasLocationPermission) "Location permission granted for Weather & Add-ons!" else "Location permission not granted.",
      Toast.LENGTH_SHORT
    ).show()
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasCameraPermission = granted
    Toast.makeText(
      context,
      if (granted) "Camera permission enabled for scanning & pairing!" else "Camera permission not granted.",
      Toast.LENGTH_SHORT
    ).show()
  }

  // Function to dispatch SMS
  fun dispatchSms(useDirectSms: Boolean) {
    val phone = recipientNumber.trim()
    val text = messageBody.trim()

    if (phone.isBlank()) {
      Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
      return
    }
    if (text.isBlank()) {
      Toast.makeText(context, "Message cannot be empty", Toast.LENGTH_SHORT).show()
      return
    }

    keyboardController?.hide()

    if (useDirectSms && hasSmsPermission) {
      try {
        val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          context.getSystemService(SmsManager::class.java)
        } else {
          @Suppress("DEPRECATION")
          SmsManager.getDefault()
        }
        smsManager.sendTextMessage(phone, null, text, null, null)
        dispatchedMessages.add(0, DispatchedMessage(recipient = phone, text = text, viaSmsManager = true))
        Toast.makeText(context, "SMS dispatched directly to $phone!", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        // Fallback to launching the default SMS/Messages app intent
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
          data = Uri.parse("smsto:$phone")
          putExtra("sms_body", text)
        }
        try {
          context.startActivity(smsIntent)
          dispatchedMessages.add(0, DispatchedMessage(recipient = phone, text = text, viaSmsManager = false))
        } catch (_: Exception) {
          Toast.makeText(context, "Failed to send SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    } else {
      // Launch default messaging intent
      val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:$phone")
        putExtra("sms_body", text)
      }
      try {
        context.startActivity(smsIntent)
        dispatchedMessages.add(0, DispatchedMessage(recipient = phone, text = text, viaSmsManager = false))
        Toast.makeText(context, "Opened Messages app for $phone", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        Toast.makeText(context, "Could not open Messages app: ${e.message}", Toast.LENGTH_SHORT).show()
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // Top App Bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Apps,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(22.dp)
            )
          }
          Column {
            Text(
              text = "Add-ons & Permissions",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Device capabilities, messages & extensions",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Shortcut to System Settings
        IconButton(
          onClick = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
              data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
          },
          modifier = Modifier.testTag("open_system_settings_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "System App Settings",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // Scrollable Addon Content
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Overview Banner
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(18.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp)
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Modular Permissions Hub",
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
              text = "Manage Messages/SMS, push alerts, location telemetry, and extensions securely.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
              lineHeight = 16.sp
            )
          }
        }
      }

      // 2. Section Header: Device Permissions
      Text(
        text = "DEVICE PERMISSIONS",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary
      )

      // --- PERMISSION CARD: MESSAGES & SMS ---
      PermissionCard(
        title = "Messages & SMS",
        description = "Send and share real-time weather briefings, storm alerts, and Reos links directly via SMS text messages.",
        icon = Icons.Default.Email,
        isGranted = hasSmsPermission,
        onRequestPermission = {
          smsPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.SEND_SMS,
              Manifest.permission.READ_SMS,
              Manifest.permission.RECEIVE_SMS
            )
          )
        },
        testTag = "perm_card_sms"
      )

      // --- INTERACTIVE SMS MESSENGER CARD ---
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sms_messenger_card")
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
            Text(
              text = "Send Weather & Alert SMS",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          // Recipient Phone Field
          OutlinedTextField(
            value = recipientNumber,
            onValueChange = { recipientNumber = it },
            label = { Text("Recipient Phone Number") },
            placeholder = { Text("+1 (555) 019-2834") },
            leadingIcon = {
              Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("sms_recipient_input")
          )

          // Message Body Field
          OutlinedTextField(
            value = messageBody,
            onValueChange = { messageBody = it },
            label = { Text("Message Body") },
            leadingIcon = {
              Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("sms_body_input")
          )

          // Quick Template Chips
          Text(
            text = "Quick Presets:",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilterChip(
              selected = messageBody.contains("Weather Alert"),
              onClick = {
                messageBody = "Reos Weather: Current conditions 21°C, clear skies. High UV at noon, carry water!"
              },
              label = { Text("Weather", fontSize = 11.sp) },
              shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
              selected = messageBody.contains("Reos Weather Brief"),
              onClick = {
                messageBody = """
                  ### 🌤️ Reos Weather Brief
                  - [x] Check local conditions
                  - [ ] Pack sunscreen & water bottle
                  
                  | Factor | Value | Status |
                  |---|---|---|
                  | Temperature | 22°C | Optimal |
                  | Wind Gusts | 14 km/h | Mild |
                  | Precipitation | 5% | Clear |
                  
                  Visit [Reos Portal](https://t.ly/Zalxp) for live radar telemetry.
                """.trimIndent()
                showMarkdownPreview = true
              },
              label = { Text("Markdown Brief", fontSize = 11.sp) },
              shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
              selected = showMarkdownPreview,
              onClick = { showMarkdownPreview = !showMarkdownPreview },
              label = { Text(if (showMarkdownPreview) "Hide Preview" else "Preview", fontSize = 11.sp) },
              shape = RoundedCornerShape(12.dp)
            )
          }

          // Live Markdown Rendered Preview
          AnimatedVisibility(visible = showMarkdownPreview) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "MARKDOWN PREVIEW",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  letterSpacing = 0.8.sp,
                  modifier = Modifier.padding(bottom = 6.dp)
                )
                AiMarkdownView(
                  content = messageBody,
                  textColor = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }
          }

          // Action Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Direct SMS Dispatch (if permission granted)
            Button(
              onClick = { dispatchSms(useDirectSms = true) },
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("send_direct_sms_btn")
            ) {
              Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (hasSmsPermission) "Send SMS" else "Request & Send", fontSize = 13.sp)
            }
            // Open System Messages App Intent
            OutlinedButton(
              onClick = { dispatchSms(useDirectSms = false) },
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("open_messages_app_btn")
            ) {
              Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Messages App", fontSize = 13.sp)
            }
          }

          // Dispatched Log
          if (dispatchedMessages.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
              text = "Recent Dispatches (${dispatchedMessages.size})",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            dispatchedMessages.take(3).forEach { msg ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("To: ${msg.recipient}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(msg.text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                  }
                  Text(
                    text = if (msg.viaSmsManager) "Direct SMS" else "Messages App",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }
          }
        }
      }

      // --- PERMISSION CARD: NOTIFICATIONS ---
      PermissionCard(
        title = "Push Notifications",
        description = "Receive instant alerts for rapid weather shifts, hourly forecasts, and extension updates.",
        icon = Icons.Default.Notifications,
        isGranted = hasNotificationPermission,
        onRequestPermission = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            Toast.makeText(context, "Notifications enabled by default on this Android version.", Toast.LENGTH_SHORT).show()
          }
        },
        actionButton = {
          TextButton(
            onClick = {
              NotificationHelper.sendAiNotification(
                context = context,
                title = "⚡ Weather Alert Briefing",
                message = "Current temp 21°C with clear skies. Recommended: light layers and hydration.",
                subText = "Weather Add-on"
              )
              Toast.makeText(context, "Test notification dispatched!", Toast.LENGTH_SHORT).show()
            }
          ) {
            Text("Send Test Alert", fontSize = 12.sp)
          }
        },
        testTag = "perm_card_notifications"
      )

      // --- PERMISSION CARD: LOCATION TELEMETRY ---
      PermissionCard(
        title = "Location Telemetry",
        description = "Provides precise GPS coordinates for hyper-local meteorological forecasts and regional climate updates.",
        icon = Icons.Default.LocationOn,
        isGranted = hasLocationPermission,
        onRequestPermission = {
          locationPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION
            )
          )
        },
        actionButton = onNavigateToWeather?.let { nav ->
          {
            TextButton(onClick = nav) {
              Text("View in Weather", fontSize = 12.sp)
            }
          }
        },
        testTag = "perm_card_location"
      )

      // --- PERMISSION CARD: CAMERA & SCANNER ---
      PermissionCard(
        title = "Camera & QR Scanner",
        description = "Permits camera access for scanning Reos link codes, device synchronization, and visual pairing.",
        icon = Icons.Default.AccountBox,
        isGranted = hasCameraPermission,
        onRequestPermission = {
          cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        testTag = "perm_card_camera"
      )

      // 3. Section Header: Addon Extensions Catalog
      Text(
        text = "ACTIVE ADDON MODULES",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
      )

      // Firebase Cloud Sync Card (Google Sign-In & Firestore Persistence)
      Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("firebase_cloud_sync_card")
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(
                    if (currentUser != null) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                  ),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (currentUser != null) Icons.Default.CloudSync else Icons.Default.CloudQueue,
                  contentDescription = null,
                  tint = if (currentUser != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(24.dp)
                )
              }
              Column {
                Text(
                  text = "Firebase Cloud Sync",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (currentUser != null) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                  modifier = Modifier.padding(top = 2.dp)
                ) {
                  Text(
                    text = if (currentUser != null) "✓ Connected (${currentUser?.displayName ?: "User"})" else "Not Signed In",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentUser != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }

            if (currentUser != null) {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                  onClick = {
                    coroutineScope.launch {
                      isSyncingFirestore = true
                      val settingsMap = mapOf(
                        "moreConnected" to moreConnectedEnabled,
                        "experiencePlus" to experiencePlusEnabled,
                        "emailAccess" to emailAccessEnabled,
                        "weatherSms" to weatherSmsBroadcastEnabled,
                        "stormAlerts" to stormAlertsEnabled,
                        "offlineCache" to offlineCacheEnabled,
                        "highContrast" to highContrastEnabled
                      )
                      val syncResult = FirebaseManager.syncUserPreferences(context, settingsMap)
                      syncResult.onSuccess {
                        Toast.makeText(context, "Preferences synced to Firestore!", Toast.LENGTH_SHORT).show()
                      }.onFailure { err ->
                        Toast.makeText(context, "Sync note: ${err.message}", Toast.LENGTH_LONG).show()
                      }
                      isSyncingFirestore = false
                    }
                  },
                  shape = RoundedCornerShape(12.dp),
                  enabled = !isSyncingFirestore,
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                  modifier = Modifier.height(34.dp)
                ) {
                  if (isSyncingFirestore) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("Syncing...", fontSize = 11.sp)
                  } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sync", fontSize = 11.sp)
                  }
                }
                TextButton(
                  onClick = {
                    GoogleSignInHelper.signOut()
                    currentUser = null
                    Toast.makeText(context, "Signed out of Firebase", Toast.LENGTH_SHORT).show()
                  },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                  modifier = Modifier.height(34.dp)
                ) {
                  Text("Sign Out", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
              }
            } else {
              Button(
                onClick = {
                  coroutineScope.launch {
                    isSigningInGoogle = true
                    val result = GoogleSignInHelper.signInWithGoogle(context)
                    result.onSuccess { user ->
                      currentUser = user
                      Toast.makeText(context, "Welcome, ${user.displayName ?: "User"}!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                      if (err !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                        Toast.makeText(context, "Sign-in note: ${err.message}", Toast.LENGTH_SHORT).show()
                      }
                    }
                    isSigningInGoogle = false
                  }
                },
                enabled = !isSigningInGoogle,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
              ) {
                if (isSigningInGoogle) {
                  CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                  Spacer(Modifier.width(6.dp))
                  Text("Connecting...", fontSize = 12.sp)
                } else {
                  Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Sign In with Google", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          Button(
            onClick = {
              coroutineScope.launch {
                isSyncingFirestore = true
                val settingsMap = mapOf(
                  "moreConnected" to moreConnectedEnabled,
                  "experiencePlus" to experiencePlusEnabled,
                  "emailAccess" to emailAccessEnabled,
                  "weatherSms" to weatherSmsBroadcastEnabled,
                  "stormAlerts" to stormAlertsEnabled,
                  "offlineCache" to offlineCacheEnabled,
                  "highContrast" to highContrastEnabled,
                  "defaultUrl" to "https://reostech.pagekite.me",
                  "reosTabUrl" to "https://t.ly/Zalxp",
                  "fallbackUrl" to "https://reostech.pagekite.me",
                  "userEmail" to (currentUser?.email ?: FirebaseManager.DEFAULT_USER_EMAIL)
                )
                val syncResult = FirebaseManager.syncEverything(context, settingsMap)
                syncResult.onSuccess {
                  Toast.makeText(context, "Everything synced to cloud & local storage!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                  Toast.makeText(context, "Sync note: ${err.message}", Toast.LENGTH_LONG).show()
                }
                isSyncingFirestore = false
              }
            },
            enabled = !isSyncingFirestore,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("sync_everything_button")
          ) {
            if (isSyncingFirestore) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
              Spacer(Modifier.width(8.dp))
              Text("Syncing Everything...", fontSize = 13.sp)
            } else {
              Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Sync Everything to Cloud", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
          }

          Text(
            text = "Durable cloud synchronization powered by Firebase Firestore and Google Sign-In. Automatically backs up addon preferences, telemetry, and message logs.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
          )
        }
      }

      // Addon Module: More Connected
      AddonExtensionToggle(
        title = "More Connected",
        description = "Connects better across all Reos services seamlessly with automated background data sync, cross-service event bridge, and instant reconnect on network recovery.",
        icon = Icons.Default.Share,
        isEnabled = moreConnectedEnabled,
        onToggle = { enabled ->
          moreConnectedEnabled = enabled
          prefs.edit().putBoolean("addon_more_connected", enabled).apply()
          Toast.makeText(
            context,
            if (enabled) "More Connected enabled: Cross-service bridge active!" else "More Connected disabled.",
            Toast.LENGTH_SHORT
          ).show()
        }
      )

      // Addon Module: Experience+
      AddonExtensionToggle(
        title = "Experience+",
        description = "Makes the app significantly smoother by enabling butter-smooth 120Hz frame pacing, memory buffer caching, and zero-stutter rendering.",
        icon = Icons.Default.Bolt,
        isEnabled = experiencePlusEnabled,
        onToggle = { enabled ->
          experiencePlusEnabled = enabled
          prefs.edit().putBoolean("addon_experience_plus", enabled).apply()
          Toast.makeText(
            context,
            if (enabled) "Experience+ enabled: Butter-smooth 120Hz pacing & cache optimization active!" else "Experience+ disabled.",
            Toast.LENGTH_SHORT
          ).show()
        }
      )

      // Addon Module: Email Access with Live Push Notifications
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("addon_email_access_card")
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(
                    if (emailAccessEnabled && verifiedAccountEmail != null) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                  ),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Email,
                  contentDescription = null,
                  tint = if (emailAccessEnabled && verifiedAccountEmail != null) MaterialTheme.colorScheme.onPrimaryContainer
                  else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp)
                )
              }
              Column {
                Text(
                  text = "Email Access",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                // Status Pill
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (emailAccessEnabled && verifiedAccountEmail != null) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFC62828).copy(alpha = 0.15f),
                  modifier = Modifier.padding(top = 2.dp)
                ) {
                  Text(
                    text = if (emailAccessEnabled && verifiedAccountEmail != null) "✓ Verified & Active" else "Not Configured",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (emailAccessEnabled && verifiedAccountEmail != null) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }

            if (verifiedAccountEmail != null) {
              Switch(
                checked = emailAccessEnabled,
                onCheckedChange = { enabled ->
                  emailAccessEnabled = enabled
                  prefs.edit().putBoolean("addon_email_access", enabled).apply()
                  if (enabled) {
                    coroutineScope.launch {
                      ReosAddonsService.fetchEmailsAndNotify(context, verifiedAccountEmail ?: "", triggerPush = true)
                    }
                  }
                }
              )
            } else {
              FilledTonalButton(
                onClick = { showEmailSetupDialog = true },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
              ) {
                Text("Setup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          Text(
            text = "Delivers instant push notifications whenever a new message arrives for your verified Reos account. Features on-device data optimization and rich formatting.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
          )

          if (verifiedAccountEmail != null) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text("Reos Account", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(verifiedAccountEmail ?: "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  OutlinedButton(
                    onClick = {
                      coroutineScope.launch {
                        isFetchingEmails = true
                        val res = ReosAddonsService.fetchEmailsAndNotify(context, verifiedAccountEmail ?: "", triggerPush = true)
                        res.onSuccess { emails ->
                          fetchedEmails.clear()
                          fetchedEmails.addAll(emails)
                          Toast.makeText(context, "Checked Reos emails! Push alert dispatched.", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                          Toast.makeText(context, "Email sync note: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                        isFetchingEmails = false
                      }
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                  ) {
                    if (isFetchingEmails) {
                      CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    } else {
                      Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Check Now", fontSize = 11.sp)
                    }
                  }

                  TextButton(
                    onClick = {
                      verifiedAccountEmail = null
                      verifiedAccountUser = null
                      emailAccessEnabled = false
                      prefs.edit()
                        .remove("addon_email_access")
                        .remove("addon_verified_email")
                        .remove("addon_verified_user")
                        .apply()
                      fetchedEmails.clear()
                      Toast.makeText(context, "Disconnected Reos account.", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                  ) {
                    Text("Disconnect", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                  }
                }
              }
            }

            // Emails list preview with Data Optimization & Markdown rendering
            if (fetchedEmails.isNotEmpty()) {
              Text(
                text = "Recent Incoming Messages (${fetchedEmails.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              fetchedEmails.take(4).forEach { mail ->
                var isExpanded by remember { mutableStateOf(false) }
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surface,
                  border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                ) {
                  Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Surface(
                          shape = CircleShape,
                          color = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(6.dp)
                        ) {}
                        Text(
                          text = mail.sender,
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.primary
                        )
                      }
                      Text(
                        text = mail.timestamp,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    Text(
                      text = mail.subject,
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    // Optimized Data & Markdown message body
                    Box(modifier = Modifier.fillMaxWidth()) {
                      val contentToDisplay = if (isExpanded || mail.formattedMessage.length <= 180) {
                        mail.formattedMessage
                      } else {
                        mail.formattedMessage.take(180) + "..."
                      }
                      AiMarkdownView(
                        content = contentToDisplay,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                      )
                    }
                    if (mail.formattedMessage.length > 180) {
                      Text(
                        text = if (isExpanded) "Show Less" else "Tap to view full optimized data",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Addon Toggle 1: Weather SMS Alerts
      AddonExtensionToggle(
        title = "Automated Weather SMS Alerts",
        description = "Automatically prepare daily morning weather summaries ready to dispatch to family or team members.",
        icon = Icons.Default.Email,
        isEnabled = weatherSmsBroadcastEnabled,
        onToggle = { enabled ->
          weatherSmsBroadcastEnabled = enabled
          prefs.edit().putBoolean("addon_weather_sms", enabled).apply()
        }
      )

      // Addon Toggle 2: Severe Storm Warning
      AddonExtensionToggle(
        title = "Severe Storm & Wind Watch",
        description = "Monitors Open-Meteo telemetry for wind gusts exceeding 35 km/h and triggers instant notification warnings.",
        icon = Icons.Default.Warning,
        isEnabled = stormAlertsEnabled,
        onToggle = { enabled ->
          stormAlertsEnabled = enabled
          prefs.edit().putBoolean("addon_storm_alerts", enabled).apply()
        }
      )

      // Addon Toggle 3: Reos Offline Cache Booster
      AddonExtensionToggle(
        title = "Offline Web Portal Cache",
        description = "Caches Reos dashboard pages and stylesheets locally for instantaneous offline availability.",
        icon = Icons.Default.Refresh,
        isEnabled = offlineCacheEnabled,
        onToggle = { enabled ->
          offlineCacheEnabled = enabled
          prefs.edit().putBoolean("addon_offline_cache", enabled).apply()
        }
      )

      // Addon Toggle 4: High Contrast Clean Theme
      AddonExtensionToggle(
        title = "OLED High-Contrast Theme",
        description = "Enforces deep black backgrounds for maximum battery efficiency on modern AMOLED screens.",
        icon = Icons.Default.Star,
        isEnabled = highContrastEnabled,
        onToggle = { enabled ->
          highContrastEnabled = enabled
          prefs.edit().putBoolean("addon_high_contrast", enabled).apply()
        }
      )

      // Bottom clearance for floating bubble dock
      Spacer(Modifier.height(88.dp))
    }
  }

  // Email Access Setup Dialog
  if (showEmailSetupDialog) {
    AlertDialog(
      onDismissRequest = { if (!isVerifyingAccount) showEmailSetupDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Connect Reos Account", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Credentials will be verified securely to activate live incoming email push notifications for the verified account owner.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
          )
          OutlinedTextField(
            value = accountUsernameInput,
            onValueChange = { accountUsernameInput = it },
            label = { Text("Reos Username or Email") },
            placeholder = { Text("e.g. admin or user@reostech.me") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          )
          OutlinedTextField(
            value = accountPasswordInput,
            onValueChange = { accountPasswordInput = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (passwordVisible) "Hide password" else "Show password"
                )
              }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          )
          if (emailAuthError != null) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = emailAuthError ?: "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(8.dp)
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (accountUsernameInput.isBlank() || accountPasswordInput.isBlank()) {
              emailAuthError = "Please enter both username and password."
              return@Button
            }
            coroutineScope.launch {
              isVerifyingAccount = true
              emailAuthError = null
              val result = ReosAddonsService.verifyUserCredentials(accountUsernameInput, accountPasswordInput)
              result.onSuccess { (user, email) ->
                isVerifyingAccount = false
                verifiedAccountUser = user
                verifiedAccountEmail = email
                emailAccessEnabled = true
                prefs.edit()
                  .putBoolean("addon_email_access", true)
                  .putString("addon_verified_user", user)
                  .putString("addon_verified_email", email)
                  .apply()
                showEmailSetupDialog = false
                Toast.makeText(context, "Account verified! Email Access is now active.", Toast.LENGTH_LONG).show()

                // Fetch initial emails and dispatch push notification
                val emailRes = ReosAddonsService.fetchEmailsAndNotify(context, email, triggerPush = true)
                emailRes.onSuccess { emails ->
                  fetchedEmails.clear()
                  fetchedEmails.addAll(emails)
                }
              }.onFailure { err ->
                isVerifyingAccount = false
                emailAuthError = err.message ?: "Authentication failed. User credentials could not be verified."
              }
            }
          },
          enabled = !isVerifyingAccount,
          shape = RoundedCornerShape(12.dp)
        ) {
          if (isVerifyingAccount) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(6.dp))
            Text("Verifying...")
          } else {
            Text("Verify & Connect")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showEmailSetupDialog = false },
          enabled = !isVerifyingAccount
        ) {
          Text("Cancel")
        }
      },
      shape = RoundedCornerShape(24.dp)
    )
  }
}

@Composable
fun PermissionCard(
  title: String,
  description: String,
  icon: ImageVector,
  isGranted: Boolean,
  onRequestPermission: () -> Unit,
  actionButton: (@Composable () -> Unit)? = null,
  testTag: String = ""
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag)
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(
                if (isGranted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer
              else MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Text(
              text = title,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
            // Status Pill
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isGranted) Color(0xFF2E7D32).copy(alpha = 0.15f) else Color(0xFFC62828).copy(alpha = 0.15f),
              modifier = Modifier.padding(top = 2.dp)
            ) {
              Text(
                text = if (isGranted) "✓ Granted" else "⚠ Requires Permission",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }

        if (!isGranted) {
          FilledTonalButton(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
          ) {
            Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        } else {
          actionButton?.invoke()
        }
      }

      Text(
        text = description,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 17.sp
      )
    }
  }
}

@Composable
fun AddonExtensionToggle(
  title: String,
  description: String,
  icon: ImageVector,
  isEnabled: Boolean,
  onToggle: (Boolean) -> Unit
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(24.dp)
        )
        Column {
          Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = description,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
          )
        }
      }
      Spacer(Modifier.width(8.dp))
      Switch(
        checked = isEnabled,
        onCheckedChange = onToggle
      )
    }
  }
}
