package com.budgetpulse.app.ui.common

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/** A web page embedded in the screen (used for the Bank Jago savings-tips article). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedWebPage(url: String, modifier: Modifier = Modifier) {
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) { loading = true; failed = false }
                        override fun onPageFinished(view: WebView?, u: String?) { loading = false }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) { failed = true; loading = false }
                        }
                    }
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() }
        )
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (failed) {
            ErrorBox(
                message = "The article could not be loaded. Check your connection.",
                onRetry = { openUrl(context, url) },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
