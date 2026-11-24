package com.istream.movies

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var customViewContainer: FrameLayout
    private lateinit var loadingProgress: ProgressBar

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalSystemUiVisibility = 0
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    companion object {
        const val ISTREAM_URL = "https://istream-web.vercel.app/?app=apk"
        const val APP_USER_AGENT_SUFFIX = " iStreamApp/1.0 (Android; iStream Cinema)"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        customViewContainer = findViewById(R.id.custom_view_container)
        loadingProgress = findViewById(R.id.loading_progress)

        setupThemeColors()
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()

        webView.loadUrl(ISTREAM_URL)
    }

    private fun setupThemeColors() {
        window.statusBarColor = getColor(R.color.black)
        window.navigationBarColor = getColor(R.color.black)
        swipeRefresh.setColorSchemeResources(R.color.istream_red)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.dark_surface)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings

        // JavaScript & Dom Storage
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // Viewport & Scale
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        // Media & Hardware
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Set custom user-agent with iStreamApp tag for real-time APK detection
        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = defaultUserAgent + APP_USER_AGENT_SUFFIX

        // Enable cookies
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadingProgress.visibility = View.VISIBLE
                // Inject real-time APK detection bridge variable
                view?.evaluateJavascript("window.isIStreamApp = true;", null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingProgress.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                view?.evaluateJavascript("window.isIStreamApp = true;", null)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    loadingProgress.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadingProgress.progress = newProgress
                if (newProgress >= 100) {
                    loadingProgress.visibility = View.GONE
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback
                originalOrientation = requestedOrientation

                // Hide webview and show full screen video view
                webView.visibility = View.GONE
                customViewContainer.visibility = View.VISIBLE
                customViewContainer.addView(view, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))

                // Enter immersive landscape mode for cinema playback
                enterFullscreen()
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun onHideCustomView() {
                if (customView == null) return

                customViewContainer.removeView(customView)
                customViewContainer.visibility = View.GONE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null

                webView.visibility = View.VISIBLE
                exitFullscreen()
                requestedOrientation = originalOrientation
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
        // Only allow swipe refresh when webView is scrolled to the very top
        webView.viewTreeObserver.addOnScrollChangedListener {
            swipeRefresh.isEnabled = webView.scrollY == 0 && customView == null
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    // Exit full screen custom video
                    webView.webChromeClient?.onHideCustomView()
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun enterFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    private fun exitFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
