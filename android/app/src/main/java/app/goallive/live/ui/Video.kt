package app.goallive.live.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.goallive.live.data.Api
import app.goallive.live.data.faTeamName
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.a
import app.goallive.live.data.s
import coil.compose.AsyncImage
import java.net.URLEncoder

class Video(val title: String, val poster: String?, val channel: String, val duration: Int, val embed: String)

/** Highlights from Aparat (searched by our server) played inside the app. */
@Composable
fun Highlights(m: MatchData) {
    val ctx = LocalContext.current
    val hName = m.home.s("name") ?: ""
    val aName = m.away.s("name") ?: ""
    val fh = faTeamName(m.home.i("id") ?: 0, hName)
    val fa2 = faTeamName(m.away.i("id") ?: 0, aName)
    val q = if (fh != null || fa2 != null) "خلاصه بازی ${fh ?: hName} ${fa2 ?: aName}" else "$hName $aName highlights"
    val must = "${fh ?: hName}|${fa2 ?: aName}"
    val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
    val l = rememberLoader(m.d.o("general").i("matchId")) {
        val d = Api.obj("/api/hl?q=${enc(q)}&must=${enc(must)}&after=${m.time}", 300_000)
        val list = d.a("videos").objs().mapNotNull { v ->
            Video(v.s("title") ?: "", v.s("poster"), v.s("channel") ?: "", v.i("duration") ?: 0, v.s("embed") ?: return@mapNotNull null)
        }.toMutableList()
        // FotMob's official clip, when it is a YouTube link
        val fm = m.facts.o("highlights")
        val yt = fm.s("url")?.let { Regex("(?:v=|youtu\\.be/|embed/)([\\w-]{11})").find(it)?.groupValues?.get(1) }
        if (yt != null) list.add(Video("${fm.s("source") ?: "YouTube"} · ویدیوی رسمی", fm.s("image") ?: "https://i.ytimg.com/vi/$yt/hqdefault.jpg",
            "YouTube (ممکن است فیلترشکن لازم باشد)", 0, "https://www.youtube-nocookie.com/embed/$yt?autoplay=1"))
        list
    }
    var playing by remember { mutableStateOf<Video?>(null) }
    LoadBox(l, 3) { vids ->
        if (vids.isEmpty()) {
            Card {
                EmptyView("ویدیوی خلاصه هنوز پیدا نشد؛ معمولاً یکی دو ساعت بعد از بازی منتشر می‌شود.")
                Row(Modifier.fillMaxWidth().clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.aparat.com/search/${enc(q)}")))
                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = LocalAccent.current)
                    Spacer(Modifier.width(10.dp))
                    Text("جستجو در آپارات", fontWeight = FontWeight.Bold)
                }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vids.forEachIndexed { idx, v -> VideoCard(v, big = idx == 0) { playing = v } }
        }
    }
    playing?.let { v -> Player(v) { playing = null } }
}

@Composable
private fun VideoCard(v: Video, big: Boolean, onPlay: () -> Unit) {
    Card(Modifier.clickable(onClick = onPlay)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (big) 16f / 9 else 21f / 9).background(C.card2)) {
            AsyncImage(v.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
            Box(Modifier.align(Alignment.Center).size(if (big) 64.dp else 48.dp).clip(CircleShape).background(LocalAccent.current), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(if (big) 38.dp else 28.dp))
            }
            if (v.duration > 0) Text("%d:%02d".format(v.duration / 60, v.duration % 60), color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp))
        }
        Column(Modifier.padding(12.dp)) {
            Text(v.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(v.channel, color = C.mute, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Player(v: Video, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val root = FrameLayout(ctx)
                    val web = WebView(ctx)
                    web.settings.javaScriptEnabled = true
                    web.settings.domStorageEnabled = true
                    web.settings.mediaPlaybackRequiresUserGesture = false
                    web.webViewClient = WebViewClient()
                    web.setBackgroundColor(android.graphics.Color.BLACK)
                    // the player's own full-screen button hands us a view to show on top
                    web.webChromeClient = object : WebChromeClient() {
                        var custom: View? = null
                        override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                            custom = view
                            root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                        override fun onHideCustomView() {
                            custom?.let { root.removeView(it) }
                            custom = null
                        }
                    }
                    val html = """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body{margin:0;height:100%;background:#000;display:flex;align-items:center}
                        .w{position:relative;width:100%;padding-top:56.25%}iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style></head>
                        <body><div class="w"><iframe src="${v.embed}" allow="autoplay; fullscreen" allowfullscreen></iframe></div></body></html>"""
                    web.loadDataWithBaseURL("https://www.aparat.com/", html, "text/html", "utf-8", null)
                    root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    root
                },
                onRelease = { root -> (root.getChildAt(0) as? WebView)?.destroy() },
            )
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "بستن", tint = Color.White) }
                    Text(v.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
