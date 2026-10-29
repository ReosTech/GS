package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.addons.AddonsScreen
import com.example.ai.NotificationHelper
import com.example.ai.WeatherScreen
import com.example.reos.ReosAppsScreen
import com.example.reos.ReosScreen
import com.example.ui.theme.ReosTechTheme

enum class MainAppTab(val title: String) {
  REOS("Reos"),
  REOS_APPS("Reos Apps"),
  ADDONS("Add-ons")
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    com.example.firebase.configureAppCheck(this, intent)
    enableEdgeToEdge()

    // Maximize display refresh rate for 90Hz / 120Hz buttery smooth pacing
    try {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val activeDisplay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
          display
        } else {
          wm.defaultDisplay
        }
        val modes = activeDisplay?.supportedModes
        val highestMode = modes?.maxByOrNull { it.refreshRate }
        if (highestMode != null && highestMode.refreshRate > 60f) {
          val params = window.attributes
          params.preferredDisplayModeId = highestMode.modeId
          window.attributes = params
        }
      }
    } catch (_: Exception) {}

    // Create notification channels for weather briefings and alerts
    NotificationHelper.createNotificationChannels(this)
    // Initialize Firebase services with google-services.json
    try {
      if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
        com.google.firebase.FirebaseApp.initializeApp(this)
      }
    } catch (e: Exception) {
      android.util.Log.w("MainActivity", "Firebase initialization note: ${e.message}")
    }
    val destination = intent?.getStringExtra("destination_tab")
    val initialTab = when (destination) {
      "weather", "apps" -> MainAppTab.REOS_APPS
      "addons" -> MainAppTab.ADDONS
      else -> MainAppTab.REOS
    }
    val initialSubApp = if (destination == "weather") "weather" else null
    setContent {
      ReosTechTheme {
        MainAppContainer(initialTab = initialTab, initialSubApp = initialSubApp)
      }
    }
  }
}

@Composable
fun MainAppContainer(
  initialTab: MainAppTab = MainAppTab.REOS,
  initialSubApp: String? = null
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  var currentTab by remember { mutableStateOf(initialTab) }
  var activeSubApp by remember { mutableStateOf(initialSubApp) }
  var reosBrowserUrlRequest by remember { mutableStateOf<String?>(null) }
  var reosBrowserUrlTimestamp by remember { mutableLongStateOf(0L) }

  // Back button handling:
  // If in weather sub-app, pressing back returns to Reos Apps hub
  // If in secondary tab, pressing back returns to Reos
  BackHandler(enabled = currentTab != MainAppTab.REOS || activeSubApp != null) {
    if (activeSubApp != null) {
      activeSubApp = null
    } else {
      currentTab = MainAppTab.REOS
    }
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // 1. Full-screen Content layer: Persist tabs in memory for instant 0ms, stutter-free 120Hz switching
    Box(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
      // Tab 0: Reos Browser & Web Portal
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            val isActive = currentTab == MainAppTab.REOS
            alpha = if (isActive) 1f else 0f
            translationX = if (isActive) 0f else 25000f
          }
      ) {
        ReosScreen(
          initialUrl = com.example.reos.REOS_TAB_URL,
          loadUrlRequest = reosBrowserUrlRequest,
          urlRequestTimestamp = reosBrowserUrlTimestamp,
          onNavigateToWeather = {
            currentTab = MainAppTab.REOS_APPS
            activeSubApp = "weather"
          },
          onNavigateToAddons = {
            currentTab = MainAppTab.ADDONS
            activeSubApp = null
          },
          isActiveTab = (currentTab == MainAppTab.REOS)
        )
      }

      // Tab 1: Reos Apps Hub & Weather Screen
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            val isActive = currentTab == MainAppTab.REOS_APPS
            alpha = if (isActive) 1f else 0f
            translationX = if (isActive) 0f else 25000f
          }
      ) {
        if (activeSubApp == "weather") {
          WeatherScreen(
            onBack = { activeSubApp = null }
          )
        } else {
          ReosAppsScreen(
            onNavigateToWeather = { activeSubApp = "weather" },
            onNavigateToReos = { targetUrl ->
              val formatted = com.example.reos.ReosAccountManager.formatAppUrl(context, targetUrl)
              reosBrowserUrlRequest = formatted
              reosBrowserUrlTimestamp = System.currentTimeMillis()
              currentTab = MainAppTab.REOS
              activeSubApp = null
            },
            onNavigateToAddons = {
              currentTab = MainAppTab.ADDONS
              activeSubApp = null
            }
          )
        }
      }

      // Tab 2: Add-ons & Cloud Preferences
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            val isActive = currentTab == MainAppTab.ADDONS
            alpha = if (isActive) 1f else 0f
            translationX = if (isActive) 0f else 25000f
          }
      ) {
        AddonsScreen(
          onNavigateToWeather = {
            currentTab = MainAppTab.REOS_APPS
            activeSubApp = "weather"
          },
          onNavigateToUrl = { targetUrl ->
            val formatted = com.example.reos.ReosAccountManager.formatAppUrl(context, targetUrl)
            reosBrowserUrlRequest = formatted
            reosBrowserUrlTimestamp = System.currentTimeMillis()
            currentTab = MainAppTab.REOS
            activeSubApp = null
          }
        )
      }
    }

    // 2. Ultra-Modern Flat Bottom Navigation Bar pinned strictly at the bottom
    FlatModernNavigationBar(
      currentTab = currentTab,
      onTabSelected = { selectedTab ->
        if (selectedTab == MainAppTab.REOS_APPS && currentTab == MainAppTab.REOS_APPS && activeSubApp != null) {
          activeSubApp = null
        } else {
          currentTab = selectedTab
        }
      },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
    )
  }
}

@Composable
fun FlatModernNavigationBar(
  currentTab: MainAppTab,
  onTabSelected: (MainAppTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val tabs = listOf(
    Triple(MainAppTab.REOS, "Reos", Icons.Default.Language),
    Triple(MainAppTab.REOS_APPS, "Apps", Icons.Default.Apps),
    Triple(MainAppTab.ADDONS, "Add-ons", Icons.Default.Extension)
  )

  // Floating dock pinned with responsive max-width on tablets and phones
  Box(
    modifier = modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      modifier = Modifier
        .widthIn(max = 420.dp)
        .fillMaxWidth()
        .height(58.dp)
        .testTag("flat_modern_navigation"),
      shape = RoundedCornerShape(29.dp),
      color = Color(0xF20F172A),
      border = BorderStroke(1.dp, Color(0x3394A3B8)),
      tonalElevation = 2.dp,
      shadowElevation = 8.dp
    ) {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        tabs.forEach { (tab, title, icon) ->
          val isSelected = currentTab == tab
          val pillBgColor by animateColorAsState(
            targetValue = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
            animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
            label = "flat_tab_bg"
          )
          val contentColor by animateColorAsState(
            targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
            animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
            label = "flat_tab_content"
          )
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = pillBgColor,
            modifier = Modifier
              .weight(1f)
              .height(44.dp)
              .clip(RoundedCornerShape(20.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White.copy(alpha = 0.25f))
              ) { onTabSelected(tab) }
              .testTag("nav_tab_${tab.name.lowercase()}")
          ) {
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(19.dp)
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }
}
