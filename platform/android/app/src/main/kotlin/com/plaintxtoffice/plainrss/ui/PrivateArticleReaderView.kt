package com.plaintxtoffice.plainrss.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.plaintxtoffice.plainrss.domain.ReaderDocument
import com.plaintxtoffice.plainrss.domain.ReaderInput
import com.plaintxtoffice.plainrss.domain.ReaderSanitizer
import com.plaintxtoffice.plainrss.domain.ReaderUrl
import com.plaintxtoffice.plainrss.network.ReaderImageFetcher
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlinx.coroutines.runBlocking

/**
 * Displays sanitized article HTML inside a locked-down [WebView] with JavaScript, file access,
 * cookies, and direct network requests disabled. Remote images are loaded only through
 * [imageFetcher] when [ReaderInput.allowsImages] is true for the current article.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PrivateArticleReaderView(
  input: ReaderInput,
  imageFetcher: ReaderImageFetcher,
  onOpenExternalUrl: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentOpenExternal = rememberUpdatedState(onOpenExternalUrl)
  val document: ReaderDocument = remember(input) { ReaderSanitizer.document(input) }
  val currentDocument = rememberUpdatedState(document)

  AndroidView(
    modifier = modifier,
    factory = { context ->
      WebView(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        settings.apply {
          javaScriptEnabled = false
          allowFileAccess = false
          allowContentAccess = false
          blockNetworkLoads = true
          cacheMode = WebSettings.LOAD_NO_CACHE
          domStorageEnabled = false
          setSupportZoom(true)
          builtInZoomControls = false
          displayZoomControls = false
        }
        webViewClient = object : WebViewClient() {
          override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?,
          ): WebResourceResponse? {
            val uri = request?.url ?: return emptyBlockedResponse()
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return emptyBlockedResponse()
            if (scheme == "about" || scheme == "data") {
              return null
            }
            if (scheme == "rss-image") {
              val doc = currentDocument.value
              val remoteUrl = doc.images[uri.toString()] ?: return emptyBlockedResponse()
              return try {
                val pngBytes = runBlocking { imageFetcher.fetchPng(remoteUrl) }
                WebResourceResponse(
                  "image/png",
                  null,
                  ByteArrayInputStream(pngBytes),
                )
              } catch (_: Exception) {
                emptyBlockedResponse()
              }
            }
            return emptyBlockedResponse()
          }

          override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
          ): Boolean {
            val urlString = request?.url?.toString() ?: return true
            if (urlString == "about:blank") return false
            val doc = currentDocument.value
            val mapped = doc.links[urlString]
            val allowed = if (mapped != null) ReaderUrl.allowed(mapped) else null
            if (allowed != null) {
              currentOpenExternal.value(allowed)
            }
            return true
          }
        }
      }
    },
    update = { webView ->
      val lastHtml = webView.tag as? String
      if (lastHtml != document.html) {
        webView.tag = document.html
        webView.stopLoading()
        webView.loadDataWithBaseURL(
          null,
          document.html,
          "text/html",
          "UTF-8",
          null,
        )
      }
    },
  )

  DisposableEffect(Unit) {
    onDispose {
      // Nothing persistent to clean up; WebView uses no cache or DOM storage.
    }
  }
}

private fun emptyBlockedResponse(): WebResourceResponse =
  WebResourceResponse(
    "text/plain",
    "UTF-8",
    403,
    "Blocked",
    mapOf("Cache-Control" to "no-store"),
    ByteArrayInputStream(ByteArray(0)),
  )
