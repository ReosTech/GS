package com.example.reos

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ReosAppItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val category: String,
  val icon: ImageVector,
  val badgeText: String? = null,
  val isFeatured: Boolean = false,
  val gradientColors: List<Color>,
  val testTag: String
)

@Composable
fun ReosAppsScreen(
  onNavigateToWeather: () -> Unit,
  onNavigateToReos: () -> Unit,
  onNavigateToAddons: () -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("All") }
  var quickNotesModalOpen by remember { mutableStateOf(false) }
  var quickNoteText by remember { mutableStateOf("") }
  var savedNoteStatus by remember { mutableStateOf<String?>(null) }

  val categories = listOf("All", "Weather", "Productivity", "System")

  val allApps = remember {
    listOf(
      ReosAppItem(
        id = "weather",
        title = "Weather",
        subtitle = "Interactive 3D atmosphere, dynamic temperature bar, hourly & 7-day forecast",
        category = "Weather",
        icon = Icons.Default.Cloud,
        badgeText = "3D Interactive",
        isFeatured = true,
        gradientColors = listOf(Color(0xFF1E3A8A), Color(0xFF0284C7)),
        testTag = "reos_apps_tile_weather"
      ),
      ReosAppItem(
        id = "reos_browser",
        title = "Reos Browser",
        subtitle = "Direct access to search.html portal, desktop mode & session persistence",
        category = "Productivity",
        icon = Icons.Default.Language,
        badgeText = "search.html",
        isFeatured = false,
        gradientColors = listOf(Color(0xFF0F172A), Color(0xFF334155)),
        testTag = "reos_apps_tile_reos"
      ),
      ReosAppItem(
        id = "addons",
        title = "Add-ons & Cloud",
        subtitle = "Cloud Firestore sync, Google Sign-In, offline caching & preference toggles",
        category = "System",
        icon = Icons.Default.Extension,
        badgeText = "Preferences",
        isFeatured = false,
        gradientColors = listOf(Color(0xFF065F46), Color(0xFF059669)),
        testTag = "reos_apps_tile_addons"
      ),
      ReosAppItem(
        id = "widgets_alerts",
        title = "Widgets & Alerts",
        subtitle = "Android home screen widgets, live status bar alerts & background push briefings",
        category = "System",
        icon = Icons.Default.Widgets,
        badgeText = "Desktop & Push",
        isFeatured = false,
        gradientColors = listOf(Color(0xFF92400E), Color(0xFFD97706)),
        testTag = "reos_apps_tile_widgets"
      ),
      ReosAppItem(
        id = "quick_notes",
        title = "Quick Scratchpad",
        subtitle = "Fast offline clipboard, instant memo notes & temporary clipboard storage",
        category = "Productivity",
        icon = Icons.Default.EditNote,
        badgeText = "Instant Memo",
        isFeatured = false,
        gradientColors = listOf(Color(0xFF831843), Color(0xFFDB2777)),
        testTag = "reos_apps_tile_notes"
      )
    )
  }

  val filteredApps = remember(searchQuery, selectedCategory) {
    allApps.filter { app ->
      val matchesSearch = searchQuery.isBlank() ||
        app.title.contains(searchQuery, ignoreCase = true) ||
        app.subtitle.contains(searchQuery, ignoreCase = true)
      val matchesCategory = selectedCategory == "All" || app.category == selectedCategory
      matchesSearch && matchesCategory
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 160.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
      contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header Section
      item(span = { GridItemSpan(maxLineSpan) }) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Title & Hub branding
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Reos Apps",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
              )
              Text(
                text = "Ecosystem Applications & Intelligent Utilities",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
              )
            }

            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF22C55E), CircleShape)
                )
                Text(
                  text = "Active Hub",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
              }
            }
          }

          // Search bar
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("reos_apps_search_input"),
            placeholder = { Text("Search Reos apps & tools...", fontSize = 13.sp) },
            leadingIcon = {
              Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
          )

          // Filter Category Chips
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            categories.forEach { cat ->
              val isSelected = selectedCategory == cat
              FilterChip(
                selected = isSelected,
                onClick = { selectedCategory = cat },
                label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = BorderStroke(
                  1.dp,
                  if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
              )
            }
          }
        }
      }

      // App Tiles
      items(
        items = filteredApps,
        key = { it.id },
        span = { item ->
          // Featured apps like Weather span full width for rich tile display
          if (item.isFeatured) GridItemSpan(maxLineSpan) else GridItemSpan(1)
        }
      ) { appItem ->
        if (appItem.isFeatured) {
          // --- FEATURED WEATHER TILE ---
          FeaturedWeatherTile(
            item = appItem,
            onClick = onNavigateToWeather
          )
        } else {
          // --- STANDARD APP TILE ---
          StandardAppTile(
            item = appItem,
            onClick = {
              when (appItem.id) {
                "reos_browser" -> onNavigateToReos()
                "addons" -> onNavigateToAddons()
                "widgets_alerts" -> onNavigateToAddons()
                "quick_notes" -> quickNotesModalOpen = true
                else -> onNavigateToWeather()
              }
            }
          )
        }
      }
    }

    // Quick Scratchpad Dialog
    if (quickNotesModalOpen) {
      AlertDialog(
        onDismissRequest = { quickNotesModalOpen = false },
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Reos Quick Scratchpad", fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              "Instant local scratchpad for temporary ideas, clipboard snippets, or meteorological notes.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.outline
            )
            OutlinedTextField(
              value = quickNoteText,
              onValueChange = { quickNoteText = it },
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .testTag("quick_note_input"),
              placeholder = { Text("Type quick note here...") },
              shape = RoundedCornerShape(14.dp)
            )
            savedNoteStatus?.let { status ->
              Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              savedNoteStatus = "Note saved to active session!"
            }
          ) {
            Text("Save")
          }
        },
        dismissButton = {
          TextButton(onClick = { quickNotesModalOpen = false }) {
            Text("Close")
          }
        }
      )
    }
  }
}

/**
 * Featured Weather Tile
 * Highlighted prominently as requested by the user, opening the Weather tab on click.
 */
@Composable
fun FeaturedWeatherTile(
  item: ReosAppItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .clickable(onClick = onClick)
      .testTag(item.testTag),
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.linearGradient(
            colors = listOf(
              Color(0xFF0F172A),
              Color(0xFF1E293B),
              Color(0xFF172554)
            )
          )
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Top Row: Badges & Launch Icon
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Weather 3D App Icon Box
            Box(
              modifier = Modifier
                .size(48.dp)
                .background(
                  Brush.radialGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                  ),
                  RoundedCornerShape(16.dp)
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = "Weather Icon",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
              )
            }

            Column {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.ExtraBold,
                  color = Color.White
                )
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = Color(0x3338BDF8)
                ) {
                  Text(
                    text = item.badgeText ?: "3D App",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF7DD3FC),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
              Text(
                text = "Atmospheric Engine & Forecast",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }

          // Launch Action Pill
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF2563EB),
            border = BorderStroke(1.dp, Color(0x4D60A5FA))
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = "Open App",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
              )
            }
          }
        }

        // Subtitle / Description
        Text(
          text = item.subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFFCBD5E1),
          lineHeight = 20.sp
        )

        // Feature Highlights Strip
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          WeatherFeaturePill(icon = Icons.Default.ViewInAr, label = "3D Clouds & Sun")
          WeatherFeaturePill(icon = Icons.Default.Thermostat, label = "Dynamic Temp Bar")
          WeatherFeaturePill(icon = Icons.Default.CalendarMonth, label = "7-Day Forecast")
        }
      }
    }
  }
}

@Composable
fun WeatherFeaturePill(
  icon: ImageVector,
  label: String
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0x1AFFFFFF),
    border = BorderStroke(1.dp, Color(0x26FFFFFF))
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Icon(icon, contentDescription = null, tint = Color(0xFF93C5FD), modifier = Modifier.size(13.dp))
      Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE2E8F0))
    }
  }
}

/**
 * Standard App Tile in the Reos Apps Grid
 */
@Composable
fun StandardAppTile(
  item: ReosAppItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 170.dp)
      .clip(RoundedCornerShape(20.dp))
      .clickable(onClick = onClick)
      .testTag(item.testTag),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Icon and category badge
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .background(
                Brush.linearGradient(item.gradientColors),
                RoundedCornerShape(14.dp)
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = item.icon,
              contentDescription = item.title,
              tint = Color.White,
              modifier = Modifier.size(22.dp)
            )
          }

          item.badgeText?.let { badge ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
              Text(
                text = badge,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }

        // Title
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        // Subtitle
        Text(
          text = item.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
          maxLines = 3,
          lineHeight = 16.sp
        )
      }

      // Action hint
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "Open",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp)
        )
      }
    }
  }
}
