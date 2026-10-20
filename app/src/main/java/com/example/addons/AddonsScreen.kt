package com.example.addons

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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


  // Addon Extension Toggles
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

  // Permission Request Launchers
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
              text = "Manage push alerts, location telemetry, and extensions securely.",
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
                  "stormAlerts" to stormAlertsEnabled,
                  "offlineCache" to offlineCacheEnabled,
                  "highContrast" to highContrastEnabled,
                  "defaultUrl" to "https://reostech.pagekite.me",
                  "reosTabUrl" to "https://reostech.pgekite.me",
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
