package com.magpiepodcast.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.magpiepodcast.MagpieApp
import com.magpiepodcast.data.ApiConfig
import com.magpiepodcast.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false

    private val pollToken = object : Runnable {
        override fun run() {
            if (finished || isFinishing) return
            binding.webView.evaluateJavascript(
                "(function(){try{return localStorage.getItem('token');}catch(e){return null;}})();"
            ) { raw ->
                val token = unwrapJsString(raw)
                if (!token.isNullOrBlank() && token != "null") {
                    onTokenCaptured(token)
                }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (MagpieApp.instance.tokenStore.isLoggedIn()) {
            goMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.title = getString(com.magpiepodcast.R.string.login_title)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = CHROME_UA
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progress.visibility =
                    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                maybeCaptureTokenFromUrl(request.url)
                return false
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                maybeCaptureTokenFromUrl(Uri.parse(url))
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                url?.let { maybeCaptureTokenFromUrl(Uri.parse(it)) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { maybeCaptureTokenFromUrl(Uri.parse(it)) }
            }
        }

        binding.webView.loadUrl(ApiConfig.LOGIN_URL)
        handler.postDelayed(pollToken, 1500L)
    }

    private fun maybeCaptureTokenFromUrl(uri: Uri) {
        val token = uri.getQueryParameter("token")
        if (!token.isNullOrBlank() && uri.host?.contains("magpiepodcast") == true) {
            onTokenCaptured(token)
        }
    }

    private fun onTokenCaptured(token: String) {
        if (finished) return
        finished = true
        handler.removeCallbacks(pollToken)
        MagpieApp.instance.tokenStore.token = token
        Toast.makeText(this, "Login OK", Toast.LENGTH_SHORT).show()
        goMain()
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollToken)
        if (::binding.isInitialized) {
            binding.webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private fun unwrapJsString(raw: String?): String? {
            if (raw.isNullOrBlank() || raw == "null") return null
            var s = raw.trim()
            if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
                s = s.substring(1, s.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
            return s.takeIf { it.isNotBlank() && it != "null" }
        }
    }
}
