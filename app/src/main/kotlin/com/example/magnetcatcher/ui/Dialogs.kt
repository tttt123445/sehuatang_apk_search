package com.example.magnetcatcher.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import coil3.compose.AsyncImage
import com.example.magnetcatcher.parser.UrlTools

@Composable
fun PreviewDialog(preview: PreviewUiState, viewModel: MainViewModel) {
    val candidates = remember(preview.item.threadId, preview.index) {
        preview.item.images.getOrNull(preview.index)
            ?.let { UrlTools.imageUrlCandidates(it) }
            ?: emptyList()
    }
    var candidateIndex by remember(preview.item.threadId, preview.index, preview.useProxy) { mutableStateOf(0) }
    var loadFailed by remember(preview.item.threadId, preview.index, preview.useProxy) { mutableStateOf(false) }
    val imageUrl = candidates.getOrNull(candidateIndex)
    Dialog(
        onDismissRequest = viewModel::dismissPreview,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = Color(0xFF050707), modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 14.dp, 14.dp, 10.dp),
                ) {
                    Text(preview.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.openOriginal(preview.item) }) { Text("原帖", color = Color(0xFFDFF8EA)) }
                    if (loadFailed) {
                        TextButton(
                            onClick = {
                                candidateIndex = 0
                                loadFailed = false
                            }
                        ) { Text("重试", color = Color(0xFFDFF8EA)) }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { viewModel.dismissPreview() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF111616))
                        .pointerInput(preview.item.threadId, preview.index) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    totalDrag += dragAmount
                                    change.consume()
                                },
                                onDragEnd = {
                                    when {
                                        totalDrag > 90f -> viewModel.previousPreview()
                                        totalDrag < -90f -> viewModel.nextPreview()
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        preview.preparingProxy -> CircularProgressIndicator(color = Color.White)
                        loadFailed || imageUrl == null -> Text("图片加载失败", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
                        preview.error.isNotEmpty() -> Text(preview.error, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
                        else -> AsyncImage(
                            model = rememberThreadImageRequest(imageUrl, preview.item.url),
                            imageLoader = viewModel.imageLoader(preview.useProxy),
                            contentDescription = preview.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                            onError = {
                                if (candidateIndex < candidates.lastIndex) {
                                    candidateIndex += 1
                                } else {
                                    loadFailed = true
                                }
                            },
                        )
                    }
                }
                if (preview.item.images.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 10.dp, 16.dp, 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::previousPreview,
                            enabled = preview.index > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text("上一张") }
                        Text(preview.position, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp), fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = viewModel::nextPreview,
                            enabled = preview.index + 1 < preview.item.images.size,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text("下一张") }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ThreadWebViewDialog(webPage: WebPageUiState, viewModel: MainViewModel) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val pageKey = "${webPage.url}|${webPage.useProxy}|${webPage.proxyPort}|${webPage.cookieHeader.hashCode()}"
    BackHandler {
        val webView = webViewRef
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            viewModel.dismissWebPage()
        }
    }

    Dialog(
        onDismissRequest = viewModel::dismissWebPage,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 14.dp, 14.dp, 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(webPage.title, color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(webPage.url, color = MutedColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    TextButton(onClick = viewModel::openWebPageExternally) {
                        Text("浏览器", color = PrimaryDark)
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFF1F3F2))
                            .clickable { viewModel.dismissWebPage() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", color = TextColor, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                    }
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val uri = request.url
                                    if (uri.scheme == "http" || uri.scheme == "https") {
                                        return false
                                    }
                                    return true
                                }

                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                                    if (webPage.useProxy) {
                                        handler.proceed()
                                    } else {
                                        handler.cancel()
                                    }
                                }
                            }
                        }
                    },
                    update = { view ->
                        webViewRef = view
                        if (view.tag != pageKey) {
                            view.tag = pageKey
                            view.loadThreadPage(webPage)
                        }
                    },
                    onRelease = { view ->
                        view.stopLoading()
                        view.clearThreadWebViewProxy()
                        view.webViewClient = WebViewClient()
                        view.destroy()
                    },
                )
            }
        }
    }
}

private fun WebView.loadThreadPage(webPage: WebPageUiState) {
    applyThreadCookies(webPage.url, webPage.cookieHeader)
    val loadPage = Runnable { loadUrl(webPage.url) }
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
        loadPage.run()
        return
    }
    val proxyOverride = threadWebProxyOverride(webPage)
    val executor = ContextCompat.getMainExecutor(context)
    runCatching {
        val controller = ProxyController.getInstance()
        if (proxyOverride.enabled) {
            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule(proxyOverride.proxyRule!!)
                .addDirect()
                .build()
            controller.setProxyOverride(proxyConfig, executor, loadPage)
        } else {
            controller.clearProxyOverride(executor, loadPage)
        }
    }.onFailure {
        loadPage.run()
    }
}

private fun WebView.clearThreadWebViewProxy() {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
    val executor = ContextCompat.getMainExecutor(context)
    runCatching {
        ProxyController.getInstance().clearProxyOverride(executor) {}
    }
}

private fun applyThreadCookies(url: String, cookieHeader: String) {
    val cookies = splitCookieHeader(cookieHeader)
    if (cookies.isEmpty()) return
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    for (target in threadCookieTargets(url)) {
        for (cookie in cookies) {
            cookieManager.setCookie(target, cookie)
        }
    }
    cookieManager.flush()
}
