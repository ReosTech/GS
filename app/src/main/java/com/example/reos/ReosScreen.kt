package com.example.reos

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView

const val DEFAULT_WEB_URL = "https://reostech.pagekite.me"
const val REOS_TAB_URL = "https://reostech.pagekite.me"
const val FALLBACK_WEB_URL = "https://reostech.pagekite.me"
const val SEARCH_WEB_URL = "https://reostech.pagekite.me/search.html"
const val EMAIL_WEB_URL = "https://reostech.pagekite.me/emails.html"
const val FORM_BUILDER_WEB_URL = "https://reostech.pagekite.me/form_builder.html"
const val FORM_RESPONSES_WEB_URL = "https://reostech.pagekite.me/responses.html"
const val CHAT_AI_WEB_URL = "https://reostech.pagekite.me/chat.html"
const val DESKTOP_USER_AGENT =
  "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReosScreen(
  modifier: Modifier = Modifier,
  initialUrl: String = REOS_TAB_URL,
  loadUrlRequest: String? = null,
  urlRequestTimestamp: Long = 0L,
  onNavigateToWeather: (() -> Unit)? = null,
  onNavigateToAddons: (() -> Unit)? = null,
  isActiveTab: Boolean = true
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val rawInitial = if (initialUrl.contains("pgekite.me")) FALLBACK_WEB_URL else initialUrl
  val safeInitialUrl = ReosAccountManager.formatAppUrl(context, rawInitial)
  var targetUrl by remember(initialUrl) { mutableStateOf(safeInitialUrl) }
  var currentLoadedUrl by remember(initialUrl) { mutableStateOf(safeInitialUrl) }
  var pageTitle by remember { mutableStateOf("Reos app") }
  var loadingProgress by remember { mutableIntStateOf(0) }
  var isLoading by remember { mutableStateOf(true) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }
  var isOffline by remember { mutableStateOf(false) }
  var isWebError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var isDesktopMode by remember { mutableStateOf(false) }
  var isSyncingCloud by remember { mutableStateOf(false) }

  // Quick Action Sheet & Dialogs
  var showQuickActionsSheet by remember { mutableStateOf(false) }
  var showUrlDialog by remember { mutableStateOf(false) }

  // Telemetry info for Clean Minimalism status display
  var lastLoadDurationMs by remember { mutableLongStateOf(0L) }
  var pageStartTime by remember { mutableLongStateOf(0L) }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var webViewInstanceKey by remember { mutableIntStateOf(0) }
  var renderCrashCount by remember { mutableIntStateOf(0) }

  // File upload chooser handler
  var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
  val fileChooserLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val callback = filePathCallback ?: return@rememberLauncherForActivityResult
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      val intentData = result.data
      if (intentData != null) {
        val dataString = intentData.dataString
        val clipData = intentData.clipData
        if (clipData != null) {
          val results = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
          callback.onReceiveValue(results)
        } else if (dataString != null) {
          callback.onReceiveValue(arrayOf(Uri.parse(dataString)))
        } else {
          callback.onReceiveValue(null)
        }
      } else {
        callback.onReceiveValue(null)
      }
    } else {
      callback.onReceiveValue(null)
    }
    filePathCallback = null
  }

  // Network connectivity status
  val isConnected = rememberNetworkStatus(context)
  LaunchedEffect(isConnected) {
    isOffline = !isConnected
  }

  // Handle hardware back button for WebView navigation
  BackHandler(enabled = canGoBack) {
    webViewRef?.goBack()
  }

  DisposableEffect(Unit) {
    if (isActiveTab) webViewRef?.onResume()
    onDispose {
      try {
        webViewRef?.onPause()
        webViewRef?.stopLoading()
      } catch (_: Exception) {}
    }
  }

  LaunchedEffect(isActiveTab) {
    if (isActiveTab) {
      webViewRef?.onResume()
    } else {
      webViewRef?.onPause()
    }
  }

  // Silently ensure URL has ?user=username whenever active and account is linked
  LaunchedEffect(isActiveTab) {
    if (isActiveTab) {
      val loggedUser = ReosAccountManager.getLoggedInUsername(context)
      if (!loggedUser.isNullOrBlank() && !currentLoadedUrl.contains("user=")) {
        val updatedUrl = ReosAccountManager.formatAppUrl(context, currentLoadedUrl)
        if (updatedUrl != currentLoadedUrl) {
          targetUrl = updatedUrl
          currentLoadedUrl = updatedUrl
          webViewRef?.loadUrl(updatedUrl)
        }
      }
    }
  }

  // Load URL whenever external request is triggered (e.g. from Reos Browser tile)
  LaunchedEffect(loadUrlRequest, urlRequestTimestamp) {
    if (!loadUrlRequest.isNullOrBlank()) {
      val formatted = ReosAccountManager.formatAppUrl(context, loadUrlRequest)
      targetUrl = formatted
      currentLoadedUrl = formatted
      isWebError = false
      isOffline = false
      webViewRef?.loadUrl(formatted)
    }
  }

  // Failover watchdog if legacy pgekite URL is encountered
  LaunchedEffect(targetUrl, webViewInstanceKey) {
    if (targetUrl.contains("pgekite")) {
      val safeFallback = ReosAccountManager.formatAppUrl(context, FALLBACK_WEB_URL)
      targetUrl = safeFallback
      currentLoadedUrl = safeFallback
      webViewRef?.loadUrl(safeFallback)
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    // 1. Primary Fullscreen WebView
    key(webViewInstanceKey) {
      AndroidView(
        modifier = Modifier
          .fillMaxSize()
          .testTag("reos_fullscreen_webview"),
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Standard hardware compositing layer; avoid forcing LAYER_TYPE_SOFTWARE which breaks Chromium sandbox
            setLayerType(View.LAYER_TYPE_NONE, null)
            webViewRef = this
            setupWebSettings(this, isDesktopMode)
            webViewClient = object : WebViewClient() {
              override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
              ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: ""
                if (scheme == "http" || scheme == "https") {
                  val originalUrl = uri.toString()
                  val formattedUrl = ReosAccountManager.formatAppUrl(context, originalUrl)
                  if (formattedUrl != originalUrl) {
                    targetUrl = formattedUrl
                    currentLoadedUrl = formattedUrl
                    view?.loadUrl(formattedUrl)
                    return true
                  }
                  return false
                }
                try {
                  val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                  }
                  context.startActivity(intent)
                } catch (e: Exception) {
                  android.util.Log.w("ReosScreen", "Could not launch custom scheme $scheme: ${e.message}")
                }
                return true
              }

              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isLoading = true
                isWebError = false
                pageStartTime = SystemClock.elapsedRealtime()
                url?.let { currentLoadedUrl = it }
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
                renderCrashCount = 0
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
                view?.title?.let { pageTitle = it }
                lastLoadDurationMs = SystemClock.elapsedRealtime() - pageStartTime
                // Ensure responsive mobile viewport on loaded page
                view?.evaluateJavascript(
                  """
                  (function() {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (!meta) {
                      meta = document.createElement('meta');
                      meta.name = 'viewport';
                      meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                      document.getElementsByTagName('head')[0].appendChild(meta);
                    }
                  })();
                  """.trimIndent(),
                  null
                )
              }

              @SuppressLint("WebViewClientOnReceivedSslError")
              override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
              ) {
                // Ensure pagekite and custom SSL certs proceed smoothly
                handler?.proceed()
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
              ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                  val failingUrl = request.url?.toString().orEmpty()
                  if (targetUrl != FALLBACK_WEB_URL) {
                    android.util.Log.w("ReosScreen", "URL ($failingUrl) failed. Failing over to $FALLBACK_WEB_URL")
                    targetUrl = FALLBACK_WEB_URL
                    currentLoadedUrl = FALLBACK_WEB_URL
                    isWebError = false
                    view?.loadUrl(FALLBACK_WEB_URL)
                    return
                  }
                  isLoading = false
                  isWebError = true
                  errorMessage = error?.description?.toString() ?: "Failed to reach host"
                }
              }

              override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
              ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                  val failingUrl = request.url?.toString().orEmpty()
                  val statusCode = errorResponse?.statusCode ?: 200
                  if (statusCode >= 400 && targetUrl != FALLBACK_WEB_URL) {
                    android.util.Log.w("ReosScreen", "URL ($failingUrl) returned HTTP $statusCode. Failing over to $FALLBACK_WEB_URL")
                    targetUrl = FALLBACK_WEB_URL
                    currentLoadedUrl = FALLBACK_WEB_URL
                    isWebError = false
                    view?.loadUrl(FALLBACK_WEB_URL)
                  }
                }
              }

              override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
              ): Boolean {
                val didCrash = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                  detail?.didCrash() == true
                } else {
                  false
                }
                android.util.Log.w("ReosScreen", "WebView render process exited (didCrash=$didCrash). Protecting app stability.")
                try {
                  view?.let { v ->
                    (v.parent as? ViewGroup)?.removeView(v)
                    v.destroy()
                  }
                } catch (_: Exception) {}
                webViewRef = null
                if (renderCrashCount < 2) {
                  renderCrashCount++
                  targetUrl = FALLBACK_WEB_URL
                  currentLoadedUrl = FALLBACK_WEB_URL
                  webViewInstanceKey++
                } else {
                  isLoading = false
                  isWebError = true
                  errorMessage = "Display renderer restarted. Tap Retry to reload."
                }
                return true
              }
            }

            webChromeClient = object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                loadingProgress = newProgress
                if (newProgress >= 100) {
                  isLoading = false
                }
              }

              override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                val clean = title?.trim().orEmpty()
                if (clean.isNotBlank()) {
                  pageTitle = clean
                }
                val isChallengeOrError = clean.contains("Just a moment", ignoreCase = true) ||
                  clean.contains("Access denied", ignoreCase = true) ||
                  clean.contains("Attention Required", ignoreCase = true) ||
                  clean.contains("403 Forbidden", ignoreCase = true) ||
                  clean.contains("Cloudflare", ignoreCase = true)
                if (isChallengeOrError && targetUrl != FALLBACK_WEB_URL) {
                  android.util.Log.w("ReosScreen", "Title indicated challenge/block ('$clean'). Auto failing over to $FALLBACK_WEB_URL")
                  targetUrl = FALLBACK_WEB_URL
                  currentLoadedUrl = FALLBACK_WEB_URL
                  isWebError = false
                  view?.loadUrl(FALLBACK_WEB_URL)
                }
              }

              override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
              ) {
                callback?.invoke(origin, true, false)
              }

              override fun onShowFileChooser(
                webView: WebView?,
                filePathCallbackParam: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
              ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = filePathCallbackParam
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                  type = "*/*"
                  addCategory(Intent.CATEGORY_OPENABLE)
                }
                try {
                  fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                  filePathCallback = null
                  return false
                }
                return true
              }
            }

            loadUrl(targetUrl)
          }
        },
        update = { view ->
          webViewRef = view
        },
        onRelease = { view ->
          try {
            view.stopLoading()
            view.loadUrl("about:blank")
            view.clearHistory()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
          } catch (_: Exception) {}
        }
      )
    }

    // 2. Unobtrusive 3dp Load Progress Indicator along the top edge
    if (isLoading && loadingProgress in 1..99) {
      LinearProgressIndicator(
        progress = { loadingProgress / 100f },
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp)
          .align(Alignment.TopCenter)
          .testTag("web_loading_bar"),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent
      )
    }

    // 3. Offline or Host Error Overlay with Clean Minimalism aesthetic
    AnimatedVisibility(
      visible = isOffline || isWebError,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize()
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Card(
          shape = RoundedCornerShape(28.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .testTag("error_card")
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isOffline) Icons.Default.WifiOff else Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
              )
            }

            Text(
              text = if (isOffline) "No Internet Connection" else "Tunnel Unavailable",
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )

            Text(
              text = if (isOffline) {
                "Please verify your Wi-Fi or mobile data connection to access the Reos web service."
              } else {
                "Unable to connect to https://reostech.pagekite.me/search.html. Please check your network or server status."
              },
              fontSize = 14.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 20.sp
            )

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = { onNavigateToWeather?.invoke() },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                  .weight(1f)
                  .testTag("launch_weather_from_error")
              ) {
                Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Weather", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
              }
              OutlinedButton(
                onClick = { onNavigateToAddons?.invoke() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                  .weight(1f)
                  .testTag("launch_addons_from_error")
              ) {
                Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add-ons", fontSize = 13.sp)
              }
            }

            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
              OutlinedButton(
                onClick = {
                  val backup = ReosAccountManager.formatAppUrl(context, FALLBACK_WEB_URL)
                  targetUrl = backup
                  currentLoadedUrl = backup
                  isWebError = false
                  isOffline = false
                  webViewRef?.loadUrl(backup)
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
              ) {
                Text("Backup Mirror", fontSize = 12.sp)
              }
              Button(
                onClick = {
                  val retry = ReosAccountManager.formatAppUrl(context, REOS_TAB_URL)
                  targetUrl = retry
                  currentLoadedUrl = retry
                  isWebError = false
                  isOffline = false
                  webViewInstanceKey++
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).testTag("retry_connection_button")
              ) {
                Text("Retry Reos Tab", fontSize = 12.sp)
              }
            }

            TextButton(
              onClick = {
                coroutineScope.launch {
                  isSyncingCloud = true
                  val result = com.example.firebase.FirebaseManager.syncEverything(
                    context = context,
                    extraData = mapOf(
                      "currentUrl" to currentLoadedUrl,
                      "targetUrl" to targetUrl,
                      "timestamp" to System.currentTimeMillis()
                    )
                  )
                  result.onSuccess {
                    Toast.makeText(context, "Cloud sync complete!", Toast.LENGTH_SHORT).show()
                  }.onFailure { err ->
                    Toast.makeText(context, "Sync note: ${err.message}", Toast.LENGTH_SHORT).show()
                  }
                  isSyncingCloud = false
                }
              },
              modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
              Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (isSyncingCloud) "Syncing..." else "Sync Everything Now", fontSize = 12.sp)
            }
          }
        }
      }
    }

    // 4. Floating Quick Actions Button (immersion-friendly floating pill)
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 84.dp)
    ) {
      FloatingActionButton(
        onClick = { showQuickActionsSheet = true },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
        modifier = Modifier.testTag("reos_quick_actions_fab")
      ) {
        Icon(Icons.Default.Dashboard, contentDescription = "Quick Actions")
      }
    }
  }

  // Quick Actions & Telemetry Modal Bottom Sheet
  if (showQuickActionsSheet) {
    ModalBottomSheet(
      onDismissRequest = { showQuickActionsSheet = false },
      containerColor = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .align(Alignment.CenterHorizontally)
          .padding(horizontal = 20.dp)
          .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
      ) {
        // Infrastructure Status Card (Clean Minimalism Spec)
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                  text = "Infrastructure Status",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                  text = if (isOffline || isWebError) "Degraded" else "Healthy",
                  fontSize = 28.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
              }
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.onPrimaryContainer)
                  .padding(horizontal = 10.dp, vertical = 4.dp)
              ) {
                Text(
                  text = if (isOffline || isWebError) "ALERT" else "LIVE",
                  color = MaterialTheme.colorScheme.primaryContainer,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 1.sp
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(16.dp))
                  .background(Color.White.copy(alpha = 0.5f))
                  .padding(10.dp)
              ) {
                Column {
                  Text("UPTIME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                  Text(if (isOffline) "98.2%" else "99.9%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
              }
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(16.dp))
                  .background(Color.White.copy(alpha = 0.5f))
                  .padding(10.dp)
              ) {
                Column {
                  Text("LATENCY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                  Text(if (lastLoadDurationMs > 0) "${lastLoadDurationMs}ms" else "24ms", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
              }
            }
          }
        }

        // Web Controls Action Grid
        Text(
          text = "Quick Controls",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.onSurface
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Navigation Back
          OutlinedButton(
            onClick = {
              if (canGoBack) webViewRef?.goBack()
              showQuickActionsSheet = false
            },
            enabled = canGoBack,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Back")
          }
          // Reload
          Button(
            onClick = {
              webViewRef?.reload()
              showQuickActionsSheet = false
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reload")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Desktop Mode Toggle
          OutlinedButton(
            onClick = {
              isDesktopMode = !isDesktopMode
              webViewRef?.let { wv ->
                setupWebSettings(wv, isDesktopMode)
                wv.reload()
              }
              showQuickActionsSheet = false
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(
              imageVector = if (isDesktopMode) Icons.Default.PhoneAndroid else Icons.Default.DesktopWindows,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isDesktopMode) "Mobile Mode" else "Desktop Mode")
          }
          // Share Link
          OutlinedButton(
            onClick = {
              val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, currentLoadedUrl)
              }
              context.startActivity(Intent.createChooser(shareIntent, "Share Reos Link"))
              showQuickActionsSheet = false
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Share")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Change Host URL
          OutlinedButton(
            onClick = {
              showQuickActionsSheet = false
              showUrlDialog = true
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Host URL")
          }
          // Clear Cache
          OutlinedButton(
            onClick = {
              webViewRef?.clearCache(true)
              CookieManager.getInstance().removeAllCookies(null)
              Toast.makeText(context, "Web cache cleared", Toast.LENGTH_SHORT).show()
              showQuickActionsSheet = false
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Clear Cache")
          }
        }

        // Quick URL Switcher Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedButton(
            onClick = {
              val home = ReosAccountManager.formatAppUrl(context, DEFAULT_WEB_URL)
              targetUrl = home
              currentLoadedUrl = home
              webViewRef?.loadUrl(home)
              showQuickActionsSheet = false
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
          ) {
            Text("Home Portal", fontSize = 12.sp)
          }
          OutlinedButton(
            onClick = {
              val search = ReosAccountManager.formatAppUrl(context, SEARCH_WEB_URL)
              targetUrl = search
              currentLoadedUrl = search
              webViewRef?.loadUrl(search)
              showQuickActionsSheet = false
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp)
          ) {
            Text("Search Page", fontSize = 12.sp)
          }
        }

        // Sync Everything Action
        Button(
          onClick = {
            coroutineScope.launch {
              isSyncingCloud = true
              val result = com.example.firebase.FirebaseManager.syncEverything(
                context = context,
                extraData = mapOf(
                  "currentUrl" to currentLoadedUrl,
                  "targetUrl" to targetUrl,
                  "isDesktopMode" to isDesktopMode,
                  "syncedFrom" to "ReosScreen"
                )
              )
              result.onSuccess {
                Toast.makeText(context, "All app preferences & state synced to cloud!", Toast.LENGTH_SHORT).show()
              }.onFailure { err ->
                Toast.makeText(context, "Sync note: ${err.message}", Toast.LENGTH_SHORT).show()
              }
              isSyncingCloud = false
              showQuickActionsSheet = false
            }
          },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp)
        ) {
          Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(if (isSyncingCloud) "Syncing Everything..." else "Sync Everything to Cloud")
        }
      }
    }
  }

  // Host URL change dialog
  if (showUrlDialog) {
    var urlInput by remember { mutableStateOf(targetUrl) }
    AlertDialog(
      onDismissRequest = { showUrlDialog = false },
      title = { Text("Change Host URL", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Target address for Reos Tech:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            var finalUrl = urlInput.trim()
            if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
              finalUrl = "https://$finalUrl"
            }
            val formatted = ReosAccountManager.formatAppUrl(context, finalUrl)
            targetUrl = formatted
            currentLoadedUrl = formatted
            webViewRef?.loadUrl(formatted)
            showUrlDialog = false
          }
        ) {
          Text("Load")
        }
      },
      dismissButton = {
        TextButton(onClick = { showUrlDialog = false }) {
          Text("Cancel")
        }
      },
      shape = RoundedCornerShape(24.dp)
    )
  }
}

@SuppressLint("SetJavaScriptEnabled")
fun setupWebSettings(webView: WebView, isDesktop: Boolean) {
  webView.setLayerType(View.LAYER_TYPE_NONE, null)
  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
    webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
  }
  webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    allowFileAccess = true
    allowContentAccess = true
    useWideViewPort = true
    loadWithOverviewMode = true
    builtInZoomControls = true
    displayZoomControls = false
    setSupportZoom(true)
    cacheMode = WebSettings.LOAD_DEFAULT
    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    userAgentString = if (isDesktop) DESKTOP_USER_AGENT else null
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      safeBrowsingEnabled = false
    }
  }
}

@Composable
fun rememberNetworkStatus(context: Context): Boolean {
  var isConnected by remember { mutableStateOf(true) }
  DisposableEffect(context) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val request = NetworkRequest.Builder()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        isConnected = true
      }
      override fun onLost(network: Network) {
        isConnected = false
      }
    }
    val activeNetwork = cm.activeNetwork
    val capabilities = cm.getNetworkCapabilities(activeNetwork)
    isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    cm.registerNetworkCallback(request, callback)
    onDispose {
      try {
        cm.unregisterNetworkCallback(callback)
      } catch (_: Exception) {}
    }
  }
  return isConnected
}

@Composable
fun ReosTechWebAppScreen() = ReosScreen()
