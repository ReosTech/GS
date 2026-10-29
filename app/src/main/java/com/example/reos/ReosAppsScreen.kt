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
import androidx.compose.ui.platform.LocalContext
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
  val gradientColors: List<Color>,
  val testTag: String
)

@Composable
fun ReosAppsScreen(
  onNavigateToWeather: () -> Unit,
  onNavigateToReos: (String) -> Unit = {},
  onNavigateToAddons: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("All") }
  var quickNotesModalOpen by remember { mutableStateOf(false) }
  var quickNoteText by remember { mutableStateOf(com.example.firebase.FirebaseManager.getScratchpad(context)) }
  var savedNoteStatus by remember { mutableStateOf<String?>(null) }

  // Reos Account State
  var loggedInUser by remember { mutableStateOf(ReosAccountManager.getLoggedInUsername(context)) }

  DisposableEffect(Unit) {
    loggedInUser = ReosAccountManager.getLoggedInUsername(context)
    val localNote = com.example.firebase.FirebaseManager.getScratchpad(context)
    if (localNote.isNotBlank()) {
      quickNoteText = localNote
    }
    com.example.firebase.FirebaseManager.fetchRemoteUserData(context) { remoteUser, remoteNote ->
      if (!remoteUser.isNullOrBlank()) loggedInUser = remoteUser
      if (!remoteNote.isNullOrBlank() && quickNoteText.isBlank()) quickNoteText = remoteNote
    }
    onDispose { }
  }

  val categories = listOf("All", "Productivity", "Forms & Mail", "System")

  val allApps = remember(loggedInUser) {
    listOf(
      ReosAppItem(
        id = "weather",
        title = "Weather",
        subtitle = "Interactive 3D atmosphere, dynamic temperature bar, hourly & 7-day forecast",
        category = "Productivity",
        icon = Icons.Default.Cloud,
        badgeText = "3D View",
        gradientColors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
        testTag = "reos_apps_tile_weather"
      ),
      ReosAppItem(
        id = "ai",
        title = "AI",
        subtitle = "Interactive Reos AI assistant & chat intelligence portal",
        category = "Productivity",
        icon = Icons.Default.Psychology,
        badgeText = "Assistant",
        gradientColors = listOf(Color(0xFF4F46E5), Color(0xFF818CF8)),
        testTag = "reos_apps_tile_ai"
      ),
      ReosAppItem(
        id = "email",
        title = "Email",
        subtitle = "Connected Reos webmail suite and inbox messaging",
        category = "Forms & Mail",
        icon = Icons.Default.Email,
        badgeText = if (loggedInUser != null) "User: $loggedInUser" else "Webmail",
        gradientColors = listOf(Color(0xFF0369A1), Color(0xFF0EA5E9)),
        testTag = "reos_apps_tile_email"
      ),
      ReosAppItem(
        id = "form_builder",
        title = "Form Builder",
        subtitle = "Design, customize, and publish dynamic web forms & surveys",
        category = "Forms & Mail",
        icon = Icons.Default.DynamicForm,
        badgeText = if (loggedInUser != null) "User: $loggedInUser" else "Builder",
        gradientColors = listOf(Color(0xFFC2410C), Color(0xFFF97316)),
        testTag = "reos_apps_tile_form_builder"
      ),
      ReosAppItem(
        id = "form_responses",
        title = "Form Responses",
        subtitle = "Review submissions, analyze responses, and track real-time feedback",
        category = "Forms & Mail",
        icon = Icons.Default.Poll,
        badgeText = if (loggedInUser != null) "User: $loggedInUser" else "Responses",
        gradientColors = listOf(Color(0xFF6B21A8), Color(0xFFA855F7)),
        testTag = "reos_apps_tile_form_responses"
      ),
      ReosAppItem(
        id = "reos_browser",
        title = "Reos Browser",
        subtitle = "Integrated web search portal, desktop mode & session persistence",
        category = "Productivity",
        icon = Icons.Default.Language,
        badgeText = "Browser",
        gradientColors = listOf(Color(0xFF0F172A), Color(0xFF334155)),
        testTag = "reos_apps_tile_reos"
      ),
      ReosAppItem(
        id = "addons",
        title = "Add-ons & Cloud",
        subtitle = "Reos Account login, Cloud Firestore sync, offline caching & preference toggles",
        category = "System",
        icon = Icons.Default.Extension,
        badgeText = if (loggedInUser != null) "Account: $loggedInUser" else "Preferences",
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
        gradientColors = listOf(Color(0xFF831843), Color(0xFFDB2777)),
        testTag = "reos_apps_tile_notes"
      )
    )
  }

  val filteredApps = remember(searchQuery, selectedCategory, allApps) {
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
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 160.dp),
      modifier = Modifier
        .fillMaxHeight()
        .fillMaxWidth()
        .widthIn(max = 760.dp)
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

            // Reos Account Status Indicator Badge (Consistent across tablet, phone, laptop, TV)
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = if (loggedInUser != null) Color(0x3322C55E) else MaterialTheme.colorScheme.primaryContainer,
              border = BorderStroke(1.dp, if (loggedInUser != null) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
              modifier = Modifier
                .height(36.dp)
                .clickable { onNavigateToAddons() }
                .testTag("reos_account_indicator_badge")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .background(if (loggedInUser != null) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                  text = if (loggedInUser != null) "👤 $loggedInUser" else "Reos Account",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (loggedInUser != null) Color(0xFF16A34A) else MaterialTheme.colorScheme.onPrimaryContainer
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

      // All App Tiles (Standardized Uniform Tile Grid including Weather)
      items(
        items = filteredApps,
        key = { it.id },
        contentType = { "app_tile" },
        span = { GridItemSpan(1) }
      ) { appItem ->
        StandardAppTile(
          item = appItem,
          onClick = {
            when (appItem.id) {
              "weather" -> onNavigateToWeather()
              "ai" -> onNavigateToReos(ReosAccountManager.formatAppUrl(context, CHAT_AI_WEB_URL))
              "email" -> onNavigateToReos(ReosAccountManager.formatAppUrl(context, EMAIL_WEB_URL))
              "form_builder" -> onNavigateToReos(ReosAccountManager.formatAppUrl(context, FORM_BUILDER_WEB_URL))
              "form_responses" -> onNavigateToReos(ReosAccountManager.formatAppUrl(context, FORM_RESPONSES_WEB_URL))
              "reos_browser" -> onNavigateToReos(ReosAccountManager.formatAppUrl(context, SEARCH_WEB_URL))
              "addons" -> onNavigateToAddons()
              "widgets_alerts" -> onNavigateToAddons()
              "quick_notes" -> quickNotesModalOpen = true
              else -> onNavigateToWeather()
            }
          }
        )
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
              com.example.firebase.FirebaseManager.saveScratchpad(context, quickNoteText)
              savedNoteStatus = "Note saved"
            }
          ) {
            Text("Save")
          }
        },
        dismissButton = {
          TextButton(onClick = {
            if (quickNoteText != com.example.firebase.FirebaseManager.getScratchpad(context)) {
              com.example.firebase.FirebaseManager.saveScratchpad(context, quickNoteText)
            }
            quickNotesModalOpen = false
          }) {
            Text("Close")
          }
        }
      )
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
  val gradientBrush = remember(item.gradientColors) { Brush.linearGradient(item.gradientColors) }

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
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp)
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
                gradientBrush,
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
