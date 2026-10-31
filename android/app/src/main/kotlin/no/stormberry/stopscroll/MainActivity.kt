package no.stormberry.stopscroll

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/*
 * The app's one screen: a WebView showing the bundled TypeScript page (the Labs pattern of
 * EclipseTracker and GDPRLetterBuilder), with CheckLink's WebMessage bridge. The page asks; this
 * activity checks every request (Bridge.parse) and does it: change a mode, record consent, open
 * Android's Accessibility settings (only after consent), open App info, turn the service off, open
 * one link of the fixed list. It never shows another app's screen and never reads one.
 */
class MainActivity : ComponentActivity() {
    private lateinit var container: FrameLayout
    private var webView: WebView? = null
    private lateinit var assetLoader: WebViewAssetLoader
    private var replyProxy: JavaScriptReplyProxy? = null
    private var sentToSettings = false
    private var returnedFromSettings = false
    private val onSettings: () -> Unit = { pushState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        val page = ContextCompat.getColor(this, R.color.page_background)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(page), navigationBarStyle = SystemBarStyle.dark(page))
        super.onCreate(savedInstanceState)
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(NavigationPolicy.ASSET_HOST)
            .setHttpAllowed(false)
            .addPathHandler("/", WebAssetHandler(assets))
            .build()
        WebView.setWebContentsDebuggingEnabled(false)
        CookieManager.getInstance().setAcceptCookie(false)
        container = FrameLayout(this).apply { setBackgroundColor(page) }
        setContentView(container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        createWebView()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val view = webView
                    if (view != null && view.canGoBack()) view.goBack() else finish()
                }
            },
        )
        SettingsStore.addListener(onSettings)
    }

    override fun onResume() {
        super.onResume()
        if (sentToSettings) {
            sentToSettings = false
            returnedFromSettings = true
        }
        webView?.onResume()
        pushState()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        SettingsStore.removeListener(onSettings)
        webView?.let { destroyWebView(it) }
        webView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        val view = WebView(this)
        view.setBackgroundColor(ContextCompat.getColor(this, R.color.page_background))
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        view.webViewClient = Client()
        view.webChromeClient = Chrome()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(view, BRIDGE_NAME, setOf(NavigationPolicy.ASSET_ORIGIN)) { _, message, origin, isMainFrame, proxy ->
                onBridgeMessage(message, origin, isMainFrame, proxy)
            }
        } else {
            Toast.makeText(this, R.string.webview_too_old, Toast.LENGTH_LONG).show()
        }
        container.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView = view
        replyProxy = null
        view.loadUrl(START_URL)
    }

    private fun destroyWebView(view: WebView) {
        container.removeView(view)
        view.stopLoading()
        view.destroy()
    }

    // onRenderProcessGone is implemented below (CheckLink's suppression: lint does not see it).
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
            if (!NavigationPolicy.isAssetRequest(request.url.toString())) return WebAssetHandler.forbidden()
            return assetLoader.shouldInterceptRequest(request.url) ?: WebAssetHandler.notFound()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return when (NavigationPolicy.decide(url)) {
                NavigationPolicy.Decision.IN_APP -> false
                NavigationPolicy.Decision.LINK -> {
                    if (request.isForMainFrame && request.hasGesture()) LinkList.idFor(url)?.let { openLink(it) }
                    true
                }
                NavigationPolicy.Decision.BLOCK -> true
            }
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            if (view != null && view === webView) {
                webView = null
                destroyWebView(view)
                createWebView()
            }
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) = request.deny()

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: android.webkit.GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }
    }

    private fun onBridgeMessage(message: WebMessageCompat, origin: Uri, isMainFrame: Boolean, proxy: JavaScriptReplyProxy) {
        if (!isMainFrame || !NavigationPolicy.isAssetOrigin(origin.toString())) return
        if (message.type != WebMessageCompat.TYPE_STRING) return
        replyProxy = proxy
        val rules = Rules.load(this)
        val agreed = Consent.agreedApps(SettingsStore.get(this).consent, rules.packages)
        val cmd = Bridge.parse(message.data ?: return, rules.packages, agreed) ?: return
        when (cmd) {
            is Command.GetState -> send(Bridge.state(state(), cmd.id))
            is Command.SetMode -> {
                SettingsStore.update(this) { it.withMode(cmd.app, cmd.mode) }
                send(Bridge.result(cmd.id, true))
            }
            is Command.RecordConsent -> {
                SettingsStore.update(this) { it.copy(consent = Consent.recordFor(Today.iso(), rules.packages)) }
                send(Bridge.result(cmd.id, true))
            }
            is Command.OpenAccessibilitySettings -> {
                if (agreed.isEmpty()) {
                    send(Bridge.result(cmd.id, false, "no-consent"))
                } else {
                    val ok = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    if (ok) sentToSettings = true
                    send(Bridge.result(cmd.id, ok))
                }
            }
            is Command.OpenAppInfo ->
                send(Bridge.result(cmd.id, start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))))
            is Command.TurnOff -> {
                val service = FeedService.running
                service?.turnOff()
                send(Bridge.result(cmd.id, service != null))
            }
            is Command.OpenLink -> send(Bridge.result(cmd.id, openLink(cmd.link)))
        }
    }

    private fun openLink(id: String): Boolean {
        val rules = Rules.load(this)
        val url = if (id == LinkList.EMAIL_ID) {
            LinkList.email(appVersion(), appVersionCode(), packageName, Build.VERSION.RELEASE, rules.apps.map { it.name to installedVersion(it.packageName) })
        } else {
            LinkList.LINKS[id] ?: return false
        }
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        if (url.startsWith("http")) intent.addCategory(Intent.CATEGORY_BROWSABLE)
        return start(intent)
    }

    private fun start(intent: Intent): Boolean =
        try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
            false
        }

    private fun pushState() {
        if (replyProxy != null) send(Bridge.state(state(), null))
    }

    private fun send(text: String) {
        val proxy = replyProxy ?: return
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) proxy.postMessage(text)
        } catch (_: Exception) {
        }
    }

    private fun state(): AppState {
        val rules = Rules.load(this)
        val s = SettingsStore.get(this)
        val agreed = Consent.agreedApps(s.consent, rules.packages)
        val enabled = serviceEnabled()
        if (enabled) returnedFromSettings = false
        return AppState(
            consent = s.consent,
            anyAgreed = agreed.isNotEmpty(),
            serviceEnabled = enabled,
            serviceRunning = FeedService.running != null,
            sdk = Build.VERSION.SDK_INT,
            installedFromFile = installedFromFile(),
            returnedFromSettings = returnedFromSettings,
            appVersion = appVersion(),
            rulesVersion = rules.rulesVersion,
            disclosureVersion = Consent.DISCLOSURE_VERSION,
            apps = rules.apps.map { a ->
                val v = installedVersion(a.packageName)
                AppRow(a.id, a.packageName, a.name, a.feedName, s.mode(a.packageName), a.packageName in agreed, v != null, v, s.stats(a.packageName))
            },
        )
    }

    private fun serviceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val si = it.resolveInfo?.serviceInfo
            si?.packageName == packageName && si.name == FeedService::class.java.name
        }
    }

    /** True when Android marked this install as a downloaded or local file (restricted settings apply, spec 10.2). */
    private fun installedFromFile(): Boolean =
        try {
            val source = packageManager.getInstallSourceInfo(packageName).packageSource
            source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE || source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        } catch (_: Exception) {
            false
        }

    private fun installedVersion(pkg: String): String? =
        try {
            packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName ?: "?"
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    private fun appVersion(): String =
        try {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }

    private fun appVersionCode(): Long =
        try {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).longVersionCode
        } catch (_: Exception) {
            -1L
        }

    companion object {
        private const val START_URL = "https://${NavigationPolicy.ASSET_HOST}/index.html"
        private const val BRIDGE_NAME = "StopScrollBridge"
    }
}
