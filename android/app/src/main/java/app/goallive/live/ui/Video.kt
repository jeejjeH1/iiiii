package app.goallive.live.ui

import app.goallive.live.L

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.a
import app.goallive.live.data.b
import app.goallive.live.data.faTeamName
import app.goallive.live.data.i
import app.goallive.live.data.o
import app.goallive.live.data.objs
import app.goallive.live.data.s
import coil.compose.AsyncImage
import java.net.URLEncoder

class Video(val title: String, val poster: String?, val channel: String, val duration: Int, val embed: String)

/** Highlights from Aparat (searched by our server) played inside the app, laid out like the site's ".hl-tile"s. */
@Composable
fun Highlights(m: MatchData, compact: Boolean = false) {
    val ctx = LocalContext.current
    val fh = faTeamName(m.homeId, m.homeName)
    val fa2 = faTeamName(m.awayId, m.awayName)
    val q = if (fh != null || fa2 != null) L("خلاصه بازی {0} {1}", fh ?: m.homeName, fa2 ?: m.awayName) else "${m.homeName} ${m.awayName} highlights"
    val must = "${fh ?: m.homeName}|${fa2 ?: m.awayName}"
    val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
    val l = rememberLoader(m.matchId, "hl") {
        val list = runCatching {
            Api.obj("/api/hl?q=${enc(q)}&must=${enc(must)}&after=${m.time}", 300_000).a("videos").objs().mapNotNull { v ->
                Video(v.s("title") ?: "", v.s("poster"), v.s("channel") ?: "", v.i("duration") ?: 0, v.s("embed") ?: return@mapNotNull null)
            }
        }.getOrDefault(emptyList()).toMutableList()
        // FotMob's official clip, when it is a YouTube link
        val fm = m.facts.o("highlights")
        val yt = fm.s("url")?.let { Regex("(?:v=|youtu\\.be/|embed/)([\\w-]{11})").find(it)?.groupValues?.get(1) }
        if (yt != null) list.add(Video(L("{0} · ویدیوی رسمی", fm.s("source") ?: "YouTube"), fm.s("image") ?: "https://i.ytimg.com/vi/$yt/hqdefault.jpg",
            L("YouTube (ممکن است فیلترشکن لازم باشد)"), 0, "https://www.youtube-nocookie.com/embed/$yt?autoplay=1"))
        list
    }
    var playing by remember { mutableStateOf<Video?>(null) }
    LoadBox(l, if (compact) 1 else 3) { vids ->
        if (vids.isEmpty()) {
            val body: @Composable () -> Unit = {
                // in the summary just one row; the full card explains when videos usually appear
                if (!compact) T(L("ویدیوی خلاصه هنوز پیدا نشد؛ معمولاً یکی دو ساعت بعد از بازی منتشر می‌شود."), 14, color = C.mute,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                ListRow(first = compact, onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.aparat.com/search/${enc(q)}"))) } }) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFED145B)), contentAlignment = Alignment.Center) { T(L("آ"), 15, FontWeight.Black, Color.White) }
                    Column(Modifier.weight(1f)) {
                        T(L("جستجو در آپارات"), 14, FontWeight.Bold)
                        if (compact) T(L("ویدیوی خلاصه هنوز نیامده"), 12, color = C.mute)
                    }
                    Icon(Ic.chev, null, Modifier.size(18.dp), tint = C.mute)
                }
            }
            if (compact) Column { body() } else Card { body() }
        } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(vids.first(), m, big = true, width = null) { playing = vids.first() }
            if (vids.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(vids.drop(1)) { v -> Tile(v, m, big = false, width = 190.dp) { playing = v } }
            }
        }
    }
    playing?.let { v -> Player(v, m) { playing = null } }
}

@Composable
private fun Tile(v: Video, m: MatchData, big: Boolean, width: Dp?, onPlay: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).aspectRatio(16f / 9).clip(RoundedCornerShape(18.dp)).background(Color.Black)
            .clickable(onClick = onPlay),
    ) {
        AsyncImage(v.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().drawBehind {
            cssLinear(242f, 0f to m.homeColor.copy(alpha = 0.35f), 0.5f to Color.Transparent, 1f to m.awayColor.copy(alpha = 0.35f))
            drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
        })
        if (v.duration > 0) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Ltr("%d:%02d".format(v.duration / 60, v.duration % 60), style = lat(12.sp, FontWeight.ExtraBold), color = Color.White)
        }
        val play = if (big) 62.dp else 40.dp
        Box(
            Modifier.align(if (big) Alignment.Center else Alignment.TopStart).padding(if (big) 0.dp else 12.dp).then(if (big) Modifier.padding(bottom = 30.dp) else Modifier)
                .size(play + if (big) 16.dp else 10.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(play).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(Ic.play, null, Modifier.size(if (big) 26.dp else 18.dp), tint = LocalAccentInk.current)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            T(v.title, if (big) 13 else 11, FontWeight.ExtraBold, Color.White, maxLines = 2)
            T(v.channel, 11, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Player(v: Video, m: MatchData, onClose: () -> Unit) {
    val ch = Clubs.get(m.homeId); val ca = Clubs.get(m.awayId)
    val c1 = ch?.let { Color(it.c1) } ?: m.homeColor
    val c2 = ca?.let { Color(it.c1) } ?: m.awayColor
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(C.cardSolid).statusBarsPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().drawBehind { cssLinear(242f, 0f to c1, 0.5f to darken(c1, 0.3f), 1f to c2) }.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBtn(Ic.x, L("بستن"), Color.White, Color.Black.copy(alpha = 0.25f)) { onClose() }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        Crest(m.homeId, 24.dp); Ltr(m.homeName, style = lat(15.sp, FontWeight.ExtraBold), color = Color.White)
                    }
                    ScoreText(m.home.i("score"), m.away.i("score"), 26, Color.White, sepColor = Color.White.copy(alpha = 0.5f))
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        Ltr(m.awayName, style = lat(15.sp, FontWeight.ExtraBold), color = Color.White); Crest(m.awayId, 24.dp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(Color.Black)) {
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
                        val html = """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body{margin:0;height:100%;background:#000}
                            iframe{position:absolute;inset:0;width:100%;height:100%;border:0}</style></head>
                            <body><iframe src="${v.embed}" allow="autoplay; fullscreen" allowfullscreen></iframe></body></html>"""
                        web.loadDataWithBaseURL("https://www.aparat.com/", html, "text/html", "utf-8", null)
                        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        root
                    },
                    onRelease = { root -> (root.getChildAt(0) as? WebView)?.destroy() },
                )
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                T(v.title, 14, FontWeight.ExtraBold)
                T(v.channel, 12, color = C.mute)
            }
            val goals = m.events.filter { it.s("type") == "Goal" }
            if (goals.isNotEmpty()) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                goals.forEach { g ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card2).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Ltr(minuteText(g) + "'", Modifier.width(34.dp), style = vz(13.sp, FontWeight.Black), color = LocalAccent.current)
                        Ltr(g.s("nameStr") ?: g.o("player").s("name") ?: "", Modifier.weight(1f))
                        T(if (g.b("isHome")) m.homeName else m.awayName, 12, color = C.mute, maxLines = 1)
                    }
                }
            }
            T(L("ویدیو از آپارات پخش می‌شود"), 11, color = C.faint, modifier = Modifier.fillMaxWidth().padding(16.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
