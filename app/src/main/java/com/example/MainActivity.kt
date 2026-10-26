package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
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
    // 1. Full-screen Content layer
    Box(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
      when (currentTab) {
        MainAppTab.REOS -> {
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
            isActiveTab = true
          )
        }
        MainAppTab.REOS_APPS -> {
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
        MainAppTab.ADDONS -> {
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
    }
    // 2. Ultra-Modern Flat Navigation Bar
    FlatModernNavigationBar(
      currentTab = currentTab,
      onTabSelected = { selectedTab ->
        if (selectedTab == MainAppTab.REOS_APPS && currentTab == MainAppTab.REOS_APPS && activeSubApp != null) {
          // Tapping Reos Apps tab while inside a sub-app (like weather) navigates back to the Reos Apps hub
          activeSubApp = null
        } else {
          currentTab = selectedTab
        }
      },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .windowInsetsPadding(WindowInsets.navigationBars)
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
    Triple(MainAppTab.REOS_APPS, "Reos Apps", Icons.Default.Apps),
    Triple(MainAppTab.ADDONS, "Add-ons", Icons.Default.Extension)
  )

  // Floating Flat Modern Dock Container with subtle frosted glass aesthetic
  Surface(
    modifier = modifier
      .wrapContentWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .testTag("flat_modern_navigation"),
    shape = RoundedCornerShape(24.dp),
    color = Color(0xF20F172A), // Translucent frosted deep obsidian slate
    border = BorderStroke(1.dp, Color(0x3394A3B8)), // Crisp modern frosted hairline border
    tonalElevation = 0.dp,
    shadowElevation = 6.dp
  ) {
    Row(
      modifier = Modifier
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0x1AFFFFFF), // Subtle top frosted sheen
              Color(0x00FFFFFF)
            )
          )
        )
        .padding(horizontal = 6.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      tabs.forEach { (tab, title, icon) ->
        val isSelected = currentTab == tab
        val pillBgColor by animateColorAsState(
          targetValue = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
          label = "flat_tab_bg"
        )
        val contentColor by animateColorAsState(
          targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
          label = "flat_tab_content"
        )
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = pillBgColor,
          modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null
            ) { onTabSelected(tab) }
            .testTag("nav_tab_${tab.name.lowercase()}")
        ) {
          Row(
            modifier = Modifier
              .padding(horizontal = if (isSelected) 18.dp else 14.dp)
              .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = icon,
              contentDescription = title,
              tint = contentColor,
              modifier = Modifier.size(19.dp)
            )
            AnimatedVisibility(
              visible = isSelected,
              enter = expandHorizontally() + fadeIn(),
              exit = shrinkHorizontally() + fadeOut()
            ) {
              Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1
              )
            }
          }
        }
      }
    }
  }
}
