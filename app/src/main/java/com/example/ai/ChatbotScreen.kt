package com.example.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class ChatMessage(
  val id: String = java.util.UUID.randomUUID().toString(),
  val sender: String, // "user" or "model"
  val text: String,
  val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ChatbotScreen(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val listState = rememberLazyListState()

  val availableModels = listOf(
    "gemini-3.8-flash" to "Gemini 3.8 Flash (Active)",
    "gemini-3.1-pro-preview" to "Gemini 3.1 Pro (Deep STEM)",
    "local-neural-engine" to "Neural Engine (Instant Offline)"
  )
  var selectedModel by remember { mutableStateOf("gemini-3.8-flash") }
  var useSearchGrounding by remember { mutableStateOf(true) }
  var useMapsGrounding by remember { mutableStateOf(true) }
  var inputText by remember { mutableStateOf("") }
  var isGenerating by remember { mutableStateOf(false) }

  val messages = remember {
    mutableStateListOf(
      ChatMessage(
        sender = "model",
        text = "Hello! I am your Advanced AI assistant powered by Gemini 3.8 Flash with live Google Search and Google Maps Grounding. Ask me anything about current events, local places, code, or science!"
      )
    )
  }

  val promptSuggestions = listOf(
    "Latest tech news today",
    "Best coffee shops near Tokyo Tower",
    "Explain quantum computing simply",
    "Write Kotlin Compose counter code"
  )

  fun sendMessage(promptText: String) {
    if (promptText.isBlank() || isGenerating) return
    val userMsg = ChatMessage(sender = "user", text = promptText.trim())
    messages.add(userMsg)
    inputText = ""
    isGenerating = true

    coroutineScope.launch {
      listState.animateScrollToItem(messages.size - 1)
      if (selectedModel == "local-neural-engine") {
        val reply = LocalAiEngine.generateResponse(
          prompt = userMsg.text,
          model = selectedModel,
          useSearchGrounding = useSearchGrounding,
          useMapsGrounding = useMapsGrounding
        )
        messages.add(ChatMessage(sender = "model", text = reply))
        isGenerating = false
        listState.animateScrollToItem(messages.size - 1)
        return@launch
      }

      val history = messages.dropLast(1).map { Pair(it.sender, it.text) }
      val result = AiService.generateContent(
        context = context,
        prompt = userMsg.text,
        model = selectedModel,
        systemInstruction = "You are an intelligent, helpful, and concise AI assistant. Provide verified, factual, and up-to-date information.",
        history = history,
        useSearchGrounding = useSearchGrounding,
        useMapsGrounding = useMapsGrounding
      )

      result.onSuccess { reply ->
        messages.add(ChatMessage(sender = "model", text = reply))
        isGenerating = false
        listState.animateScrollToItem(messages.size - 1)
      }.onFailure {
        val fallbackReply = LocalAiEngine.generateResponse(
          prompt = userMsg.text,
          model = selectedModel,
          useSearchGrounding = useSearchGrounding,
          useMapsGrounding = useMapsGrounding
        )
        messages.add(ChatMessage(sender = "model", text = fallbackReply))
        isGenerating = false
        listState.animateScrollToItem(messages.size - 1)
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // Model & Grounding Selection Bar
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 6.dp)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Model:",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        availableModels.forEach { (modelId, displayName) ->
          val isSelected = selectedModel == modelId
          FilterChip(
            selected = isSelected,
            onClick = { selectedModel = modelId },
            label = { Text(displayName, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(14.dp)
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
          onClick = {
            messages.clear()
            messages.add(
              ChatMessage(
                sender = "model",
                text = "Chat cleared. What would you like to explore next?"
              )
            )
          },
          modifier = Modifier.size(32.dp).testTag("clear_chat_button")
        ) {
          Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat", modifier = Modifier.size(18.dp))
        }
      }

      // Grounding Tools Row (Google Search & Google Maps Grounding)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 4.dp)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Tools:",
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.outline
        )
        FilterChip(
          selected = useSearchGrounding,
          onClick = { useSearchGrounding = !useSearchGrounding },
          label = { Text("🔍 Google Search Grounding", fontSize = 11.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
          ),
          shape = RoundedCornerShape(12.dp)
        )
        FilterChip(
          selected = useMapsGrounding,
          onClick = { useMapsGrounding = !useMapsGrounding },
          label = { Text("🗺️ Google Maps Grounding", fontSize = 11.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
          ),
          shape = RoundedCornerShape(12.dp)
        )
      }
    }

    // Message List
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      contentPadding = PaddingValues(vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      items(messages, key = { it.id }) { msg ->
        val isUser = msg.sender == "user"
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
          if (!isUser) {
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(6.dp),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
          }
          Card(
            shape = RoundedCornerShape(
              topStart = 20.dp,
              topEnd = 20.dp,
              bottomStart = if (isUser) 20.dp else 4.dp,
              bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(
              containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = if (isUser) Modifier.widthIn(max = 280.dp) else Modifier.fillMaxWidth(0.96f)
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              if (isUser) {
                SelectionContainer {
                  Text(
                    text = msg.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                }
              } else {
                AiMarkdownView(
                  content = msg.text,
                  textColor = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.fillMaxWidth()
                )
              }
              if (!isUser) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End
                ) {
                  IconButton(
                    onClick = {
                      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      val clip = ClipData.newPlainText("AI Response", msg.text)
                      clipboard.setPrimaryClip(clip)
                      Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(
                      Icons.Default.ContentCopy,
                      contentDescription = "Copy",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      if (isGenerating) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(6.dp),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Text("Thinking with $selectedModel...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }

    // Suggestions chips (when messages are few)
    if (messages.size <= 2 && !isGenerating) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        promptSuggestions.forEach { suggestion ->
          SuggestionChip(
            onClick = { sendMessage(suggestion) },
            label = { Text(suggestion, fontSize = 11.sp) },
            shape = RoundedCornerShape(16.dp)
          )
        }
      }
    }

    // Input Bar
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 3.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = inputText,
          onValueChange = { inputText = it },
          modifier = Modifier
            .weight(1f)
            .testTag("chat_input_field"),
          placeholder = { Text("Ask Gemini...", fontSize = 14.sp) },
          maxLines = 4,
          shape = RoundedCornerShape(24.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
          )
        )
        FilledIconButton(
          onClick = { sendMessage(inputText) },
          enabled = inputText.isNotBlank() && !isGenerating,
          shape = CircleShape,
          modifier = Modifier
            .size(48.dp)
            .testTag("chat_send_button")
        ) {
          Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
        }
      }
    }
  }
}
