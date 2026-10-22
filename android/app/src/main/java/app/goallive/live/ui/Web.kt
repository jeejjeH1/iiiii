package app.goallive.live.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Official live-streaming services. Their own sites open inside the app; the user watches with their own account. */
class Station(val key: String, val name: String, val sub: String, val url: String, val host: String)

val STATIONS = listOf(
    Station("namava", "نماوا", "پخش زنده با اشتراک نماوا", "https://www.namava.ir/", "namava.ir"),
    Station("aparat", "آپارات اسپورت", "پخش زنده لیگ برتر ایران و رقابت‌های اروپایی", "https://sport.aparat.com/", "aparat.com"),
    Station("varzesh", "تلوبیون · شبکه ورزش", "پخش زنده شبکه ورزش صداوسیما", "https://telewebion.com/live/varzesh", "telewebion.com"),
    Station("tv3", "تلوبیون · شبکه ۳", "پخش زنده شبکه سه", "https://telewebion.com/live/tv3", "telewebion.com"),
)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * In-app browser for a streaming service: logins are kept (cookies), protected video is allowed,
 * and the player's full-screen button turns the phone sideways like a real video app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(key: String) {
    val st = STATIONS.firstOrNull { it.key == key } ?: STATIONS.first()
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val activity = remember { ctx.activity() }
    var web by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var title by remember { mutableStateOf(st.name) }
    var full by remember { mutableStateOf(false) }

    fun setFull(on: Boolean) {
        full = on
        val a = activity ?: return
        a.requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val c = WindowCompat.getInsetsController(a.window, a.window.decorView)
        if (on) { c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; c.hide(WindowInsetsCompat.Type.systemBars()) }
        else c.show(WindowInsetsCompat.Type.systemBars())
    }

    BackHandler {
        val w = web
        when {
            full -> (w?.webChromeClient as? WebChromeClient)?.onHideCustomView()
            w != null && w.canGoBack() -> w.goBack()
            else -> nav.back()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (full) setFull(false)
            CookieManager.getInstance().flush()
            web?.destroy()
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg).then(if (full) Modifier else Modifier.navigationBarsPadding())) {
        if (!full) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Column {
                        T(st.name, 15, FontWeight.Black, maxLines = 1)
                        T(title, 11, color = C.mute, maxLines = 1)
                    }
                }
                IconBtn(Ic.live, "بارگذاری دوباره", size = 18.dp) { web?.reload() }
                IconBtn(Ic.share, "باز کردن در مرورگر", size = 18.dp) {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web?.url ?: st.url))) }
                }
                IconBtn(Ic.x, "بستن", size = 18.dp) { nav.back() }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(C.line)) {
                if (progress in 0.01f..0.99f) Box(Modifier.fillMaxWidth(progress).height(2.dp).background(LocalAccent.current))
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { c ->
                val root = FrameLayout(c)
                val w = WebView(c)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
                w.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(false)
                }
                w.setBackgroundColor(android.graphics.Color.BLACK)
                w.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                        val u = req.url
                        // web pages stay inside; app links (intent:, market:, tel:) go to the phone
                        if (u.scheme == "http" || u.scheme == "https") return false
                        runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        return true
                    }
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 0.05f }
                    override fun onPageFinished(view: WebView, url: String?) { progress = 1f; title = view.title?.takeIf { it.isNotBlank() } ?: st.name }
                }
                w.webChromeClient = object : WebChromeClient() {
                    var custom: View? = null
                    var callback: CustomViewCallback? = null
                    override fun onProgressChanged(view: WebView, p: Int) { progress = p / 100f }
                    override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                        custom?.let { root.removeView(it) }
                        custom = view; callback = cb
                        view.setBackgroundColor(android.graphics.Color.BLACK)
                        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        setFull(true)
                    }
                    override fun onHideCustomView() {
                        custom?.let { root.removeView(it) }
                        custom = null
                        callback?.onCustomViewHidden(); callback = null
                        setFull(false)
                    }
                    // DRM-protected streams ask for this before playing
                    override fun onPermissionRequest(request: PermissionRequest) {
                        val ok = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                        if (ok.isNotEmpty()) request.grant(ok.toTypedArray()) else request.deny()
                    }
                }
                root.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                w.loadUrl(st.url)
                web = w
                root
            },
        )
    }
}
