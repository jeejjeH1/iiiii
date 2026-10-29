package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.goallive.live.data.Clubs
import app.goallive.live.data.Store
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import app.goallive.live.R
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/** Room left at the bottom of every list for the floating nav bar (and the system navigation bar under it). */
val BottomSpace: androidx.compose.ui.unit.Dp
    @Composable get() = 96.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

private val TABS = listOf(
    Triple("home", L("بازی‌ها"), { Ic.ball }),
    Triple("leagues", L("لیگ‌ها"), { Ic.trophy }),
    Triple("studio", L("تاکتیک"), { StudioIcon }),
    Triple("following", L("دنبال‌شده‌ها"), { Ic.star }),
    Triple("more", L("بیشتر"), { Ic.grid }),
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Root(openMatch: MutableIntState) {
    val nc = rememberNavController()
    val nav = remember(nc) { Nav(nc) }
    NavHook.nav = nav
    Look.systemDark = isSystemInDarkTheme()
    val my = Store.myTeam
    val accent = accentForLook(if (my == null) Lime else accentFor(Clubs.get(my.id), my.color))
    // status bar icons: light on the night look, dark on the day look
    val activity = LocalContext.current as? ComponentActivity
    val light = Look.light
    val pitchLook = Look.pitch
    val vipLook = Look.vip
    // the VIP look's moving parts all read one clock
    if (vipLook) VipClockDriver()
    LaunchedEffect(light, pitchLook, vipLook) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (light) SystemBarStyle.light(0, 0) else SystemBarStyle.dark(0),
            navigationBarStyle = if (light) SystemBarStyle.light(0xFFE9EEE9.toInt(), 0) else SystemBarStyle.dark(when { vipLook -> 0xFF05060D.toInt(); pitchLook -> 0xFF06190D.toInt(); else -> 0xFF0B0F14.toInt() }),
        )
    }

    GoalLiveTheme(accent) {
        CompositionLocalProvider(LocalNav provides nav) {
            val entry by nc.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            LaunchedEffect(route) { app.goallive.live.data.Telemetry.screen(route) }
            Box(Modifier.fillMaxSize()) {
                Stadium(accent)
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    // the tactics studio is a workbench: its own slim header, and the whole screen for the pitch
                    if (route != "studio") TopBar(canBack = route != null && TABS.none { it.first == route }, nav)
                    SharedTransitionLayout(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalShared provides this) {
                    NavHost(
                        nc, "home", Modifier.fillMaxSize(),
                        // the site's view transition: fade in while rising a little
                        // the VIP look moves like a film: the new page comes forward out of depth, the old one recedes
                        enterTransition = { if (Look.vip) fadeIn(tween(420)) + androidx.compose.animation.scaleIn(tween(520, easing = FastOutSlowInEasing), initialScale = 0.9f)
                            else fadeIn(tween(300)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 40 } },
                        exitTransition = { if (Look.vip) fadeOut(tween(260)) + androidx.compose.animation.scaleOut(tween(380), targetScale = 1.06f) else fadeOut(tween(150)) },
                        popEnterTransition = { if (Look.vip) fadeIn(tween(380)) + androidx.compose.animation.scaleIn(tween(460, easing = FastOutSlowInEasing), initialScale = 1.06f) else fadeIn(tween(250)) },
                        popExitTransition = { if (Look.vip) fadeOut(tween(240)) + androidx.compose.animation.scaleOut(tween(360), targetScale = 0.9f) else fadeOut(tween(150)) },
                    ) {
                        screen("home") { HomeScreen() }
                        screen("leagues") { LeaguesScreen() }
                        screen("following") { FollowingScreen() }
                        screen("studio") { StudioScreen() }
                        screen("settings") { SettingsScreen() }
                        screen("search") { SearchScreen() }
                        screen("more") { MoreScreen() }
                        screen("match/{id}", listOf(navArgument("id") { type = NavType.IntType })) { MatchScreen(it.arguments?.getInt("id") ?: 0) }
                        screen("team/{id}", listOf(navArgument("id") { type = NavType.IntType })) { TeamScreen(it.arguments?.getInt("id") ?: 0) }
                        screen("league/{id}", listOf(navArgument("id") { type = NavType.IntType })) { LeagueScreen(it.arguments?.getInt("id") ?: 0) }
                        screen("compare/{a}/{b}", listOf(navArgument("a") { type = NavType.IntType }, navArgument("b") { type = NavType.IntType })) {
                            CompareScreen(it.arguments?.getInt("a") ?: 0, it.arguments?.getInt("b") ?: 0)
                        }
                        screen("quiz") { QuizScreen() }
                        screen("fantasy") { FantasyScreen() }
                        screen("calendar") { CalendarScreen() }
                        screen("assistant") { AssistantScreen() }
                        screen("predictions") { PredictionsScreen() }
                        screen("pleague") { PredictLeagueScreen() }
                        screen("fleague/{id}/{comp}", listOf(navArgument("id") { type = NavType.IntType }, navArgument("comp") { type = NavType.IntType })) {
                            FantasyLeagueTable(it.arguments?.getInt("id") ?: 0, it.arguments?.getInt("comp") ?: 0)
                        }
                        screen("pleague/{id}", listOf(navArgument("id") { type = NavType.IntType })) { PredictLeagueTable(it.arguments?.getInt("id") ?: 0) }
                        screen("rankings") { RankingsScreen() }
                        screen("board/{m}/{s}", listOf(navArgument("m") { type = NavType.IntType }, navArgument("s") { type = NavType.StringType })) {
                            BoardScreen(it.arguments?.getInt("m") ?: 0, it.arguments?.getString("s") ?: "h")
                        }
                        screen("player/{id}", listOf(navArgument("id") { type = NavType.IntType })) { PlayerScreen(it.arguments?.getInt("id") ?: 0) }
                    }
                    }
                    }
                    // after NavHost has set its graph: open the match a notification pointed at
                    LaunchedEffect(app.goallive.live.data.Account.pendingCode) {
                        // an invite link: a fantasy league's code starts with F
                        val c = app.goallive.live.data.Account.pendingCode
                        if (c != null) { if (c.startsWith("F")) nav.fantasy() else nav.predictLeague() }
                    }
                    LaunchedEffect(openMatch.intValue) {
                        val id = openMatch.intValue
                        if (id > 0) { nav.match(id); openMatch.intValue = 0 }
                    }
                }
                // the streaming browser and the assistant's chat use the bottom of the screen themselves
                if (route != "assistant" && route != "studio") BottomNav(route, nav, Modifier.align(Alignment.BottomCenter))
            }
            // first launch: the welcome steps (team, leagues, alerts) instead of separate pop-ups
            var welcome by remember { mutableStateOf(!Store.onboarded) }
            // before anything else: logging in with a phone number, once (it can be put off)
            var login by remember { mutableStateOf(!app.goallive.live.data.Account.signedIn && !Store.prefs().getBoolean("login.asked", false)) }
            if (login) LoginGate { login = false }
            else if (welcome) Welcome { welcome = false } else {
                AskNotifications()
                // after an update: what's new; after some days of use: a rating on Cafe Bazaar
                WhatsNew(nav)
                RateAsk()
            }
            Intro()
            StoryStudio.story?.let { st -> StorySheet(st) { StoryStudio.story = null } }
        }
    }
}

/** A destination whose animation scope is handed down, so its crests can take part in shared element transitions. */
private fun NavGraphBuilder.screen(route: String, arguments: List<NamedNavArgument> = emptyList(), content: @Composable (NavBackStackEntry) -> Unit) =
    composable(route, arguments) { entry -> CompositionLocalProvider(LocalAnimScope provides this) { content(entry) } }

/** ".top": back (on inner pages), the brand with its LIVE tag, search and settings. */
@Composable
private fun TopBar(canBack: Boolean, nav: Nav) {
    val accent = LocalAccent.current
    // a quiet bar: the mark and the name on the main tabs (just the mark inside a page), a breathing live dot, two plain icons
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    // a match page whose scoreboard has scrolled away puts a small one in the middle of the bar
    val mm = TopTitle.match
    val keep = remember { arrayOfNulls<MatchData>(1) }
    if (mm != null) keep[0] = mm
    Box(Modifier.fillMaxWidth().height(54.dp)) {
    AnimatedVisibility(canBack && mm != null, Modifier.align(Alignment.Center),
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 }, exit = fadeOut(tween(160)) + androidx.compose.animation.slideOutVertically(tween(200)) { it / 2 }) {
        keep[0]?.let { MiniScore(it) }
    }
    Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canBack) IconBtn(Ic.back, L("بازگشت"), size = 22.dp) { nav.back() }
        Row(Modifier.clip(RoundedCornerShape(14.dp)).clickable { nav.tab("home") }.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(if (canBack) 26.dp else 28.dp).clip(RoundedCornerShape(10.dp)))
            if (!canBack) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T(L("گل‌لایو"), 18, FontWeight.Black)
                if (Look.vip) VipChip() else Box(Modifier.size(7.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(C.live))
            }
        }
        Spacer(Modifier.weight(1f))
        IconBtn(Ic.search, L("جستجو"), tint = C.mute, size = 21.dp) { nav.tab("search") }
        IconBtn(Ic.sliders, L("تنظیمات"), tint = C.mute, size = 21.dp) { nav.tab("settings") }
    }
    }
    // a hairline under the bar, brightest in the middle
    Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, C.line2, accent.copy(alpha = 0.25f), C.line2, Color.Transparent))))
}

/**
 * ".nav": the floating tab bar, a strip of pitch: dark glass with faint pitch markings, the selected tab in a lit pill that
 * slides across, and a ball sitting on the bar above it that rolls (and hops) to the next tab; a light tap of the phone on
 * each change. The list fades out above it instead of showing through.
 */
@Composable
private fun BottomNav(route: String?, nav: Nav, modifier: Modifier) {
    if (Look.vip) { VipDock(route, nav, modifier); return }
    val accent = LocalAccent.current
    val ink = LocalAccentInk.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val shape = RoundedCornerShape(24.dp)
    val sel = TABS.indexOfFirst { it.first == route }
    var last by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(sel) { if (sel >= 0) last = sel }
    val idx = if (sel >= 0) sel else last
    val pos by androidx.compose.animation.core.animateFloatAsState(idx.toFloat(), androidx.compose.animation.core.spring(0.78f, 260f), label = "tab")
    val shown by androidx.compose.animation.core.animateFloatAsState(if (sel >= 0) 1f else 0f, tween(250), label = "pill")
    val lines = when { Look.pitch -> Color.White.copy(alpha = 0.2f); Look.light -> Color.Black.copy(alpha = 0.06f); else -> Color.White.copy(alpha = 0.055f) }
    Box(
        modifier.fillMaxWidth().drawBehind {
            // the list fades into the page above the bar
            drawRect(Brush.verticalGradient(listOf(C.bg.copy(alpha = 0f), C.bg.copy(alpha = 0.9f), C.bg), -28.dp.toPx(), size.height * 0.6f), Offset(0f, -28.dp.toPx()),
                androidx.compose.ui.geometry.Size(size.width, size.height + 28.dp.toPx()))
        }.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.widthIn(max = 440.dp).fillMaxWidth().height(64.dp)) {
            val tw = (maxWidth - 12.dp) / TABS.size
            val twPx = with(androidx.compose.ui.platform.LocalDensity.current) { tw.toPx() }
            // the bar
            Box(
                Modifier.matchParentSize().shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)
                    .background(if (Look.vip) Brush.verticalGradient(listOf(Color(0xF0121633), Color(0xF5070818)))
                        else if (Look.pitch) Brush.verticalGradient(listOf(Color(0xFF16602F), Color(0xFF0C4020)))
                        else Brush.verticalGradient(listOf(C.cardSolid, androidx.compose.ui.graphics.lerp(C.cardSolid, C.bg, 0.55f))))
                    .drawBehind {
                        // the pitch look's bar is a strip of real grass, mown in bands
                        if (Look.pitch) { val b = size.width / 10; for (k in 0 until 10 step 2) drawRect(Color.White.copy(alpha = 0.05f), Offset(k * b, 0f), androidx.compose.ui.geometry.Size(b, size.height)) }
                        // the VIP bar: a cone of light falls from its top edge onto the chosen tab
                        if (Look.vip) { val w1 = (size.width - 12.dp.toPx()) / TABS.size; val cx = 6.dp.toPx() + w1 * (pos + 0.5f)
                            vipSpot(if (rtl) size.width - cx else cx, w1) }
                        else pitchLines(lines)
                    }
                    .then(if (Look.vip) Modifier.vipEdge(shape) else Modifier)
                    .border(1.dp, Brush.verticalGradient(listOf(if (Look.light) C.line2 else Color.White.copy(alpha = 0.16f), C.line)), shape)
            )
            // the pill under the selected tab
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = 6.dp + tw * pos).width(tw).fillMaxHeight().padding(vertical = 6.dp)
                    .graphicsLayer { alpha = shown }
                    .glow(accent.copy(alpha = 0.45f), 10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                    .background(if (Look.vip) Brush.linearGradient(listOf(Color(0xFF45E3FF), Color(0xFF8B5CF6))) else Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(accent, Color.White, 0.18f), accent)))
                    .then(if (Look.vip) Modifier.vipSheen() else Modifier)
            )
            // the ball on the bar, rolling with the pill and hopping between tabs
            val hop = kotlin.math.abs(kotlin.math.sin(Math.PI * pos)).toFloat()
            // (inside a page, with no tab chosen, the ball leaves with the pill)
            Canvas(Modifier.align(Alignment.TopStart).offset(x = 6.dp + tw * (pos + 0.5f) - 10.dp, y = (-13).dp - 9.dp * hop).size(20.dp).graphicsLayer { alpha = if (Look.vip) 0f else shown }) {
                val r = size.minDimension / 2
                // its shadow on the bar's edge, smaller as it rises
                drawOval(Color.Black.copy(alpha = 0.35f * (1 - hop * 0.6f)), Offset(r * (0.25f + 0.25f * hop), size.height + (9.dp.toPx() * hop) - r * 0.25f),
                    androidx.compose.ui.geometry.Size(r * (1.5f - hop * 0.5f), r * 0.45f))
                football(center, r, (if (rtl) -1f else 1f) * pos * twPx / r * 57.2958f)
            }
            Row(Modifier.matchParentSize().padding(6.dp)) {
                TABS.forEachIndexed { i, (r, label, icon) ->
                    val on = i == sel
                    // the ink follows the pill as it slides over the tab
                    val c = androidx.compose.ui.graphics.lerp(C.mute, ink, ((1f - kotlin.math.abs(pos - i)) * shown).coerceIn(0f, 1f))
                    val bounce = remember { Animatable(1f) }
                    LaunchedEffect(on) { if (on) { bounce.snapTo(0.72f); bounce.animateTo(1f, androidx.compose.animation.core.spring(0.38f, 520f)) } }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { role = Role.Tab; selected = on; contentDescription = label }
                            .clip(RoundedCornerShape(18.dp)).clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                if (!on) nav.tab(r) else nav.root(r)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon(), null, Modifier.size(21.dp).graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }, tint = c)
                        Spacer(Modifier.height(1.dp))
                        T(label, 12, FontWeight.Bold, c, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * The VIP look's navigation: a small floating dock in the middle of the bottom edge. The tabs are bare icons; the chosen one
 * opens into a glowing capsule with its name, and the dock reshapes itself around it.
 */
@Composable
private fun VipDock(route: String?, nav: Nav, modifier: Modifier) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = RoundedCornerShape(30.dp)
    Box(modifier.fillMaxWidth().drawBehind {
            drawRect(Brush.verticalGradient(listOf(C.bg.copy(alpha = 0f), C.bg.copy(alpha = 0.85f)), -24.dp.toPx(), size.height), Offset(0f, -24.dp.toPx()),
                androidx.compose.ui.geometry.Size(size.width, size.height + 24.dp.toPx()))
        }.navigationBarsPadding().padding(bottom = 14.dp, top = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.shadow(24.dp, shape, ambientColor = Color(0xFF45E3FF), spotColor = Color(0xFF8B5CF6)).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xF2141938), Color(0xF7070818)))).vipEdge(shape)
                .padding(6.dp).animateContentSize(androidx.compose.animation.core.spring(0.75f, 380f)),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { (r, label, icon) ->
                val on = r == route
                Row(
                    Modifier.height(50.dp).semantics(mergeDescendants = true) { role = Role.Tab; selected = on; contentDescription = label }
                        .clip(RoundedCornerShape(25.dp))
                        .then(if (on) Modifier.background(Brush.linearGradient(listOf(Color(0xFF45E3FF), Color(0xFF8B5CF6)))).vipSheen() else Modifier)
                        .clickable { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); if (!on) nav.tab(r) else nav.root(r) }
                        .padding(horizontal = if (on) 16.dp else 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon(), null, Modifier.size(22.dp), tint = if (on) Color(0xFF06101E) else C.mute)
                    AnimatedVisibility(on, enter = fadeIn() + androidx.compose.animation.expandHorizontally(), exit = fadeOut() + androidx.compose.animation.shrinkHorizontally()) {
                        Row { Spacer(Modifier.width(8.dp)); T(label, 13, FontWeight.Black, Color(0xFF06101E), maxLines = 1) }
                    }
                }
            }
        }
    }
}

/** The site's opening: floodlights flicker on, the ball rolls in, "گل لایو" pops up with a broadcast tag. */
@Composable
private fun Intro() {
    var show by rememberSaveable { mutableStateOf(true) }
    val ball = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!show) return@LaunchedEffect
        ball.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        word.animateTo(1f, tween(450))
        tag.animateTo(1f, tween(300))
        delay(250)
        show = false
    }
    AnimatedVisibility(show, enter = fadeIn(), exit = fadeOut(tween(450)) + scaleOut(targetScale = 1.08f)) {
        Box(Modifier.fillMaxSize().background(C.bg).clickable { show = false }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().alpha(0.6f + 0.4f * ball.value)) {
                // two floodlight beams
                for (side in listOf(-1f, 1f)) {
                    val cx = if (side < 0) -size.width * 0.1f else size.width * 1.1f
                    drawCircle(Brush.radialGradient(listOf(Color(0x33DCF5FF), Color.Transparent), Offset(cx, 0f), size.height * 0.8f), size.height * 0.8f, Offset(cx, 0f))
                }
                drawLine(Color.White.copy(alpha = 0.15f), Offset(size.width * (0.5f - 0.5f * ball.value), size.height / 2), Offset(size.width * (0.5f + 0.5f * ball.value), size.height / 2), 2f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp)
                    .offset(x = (-300 * (1 - ball.value)).dp).rotate(-720f * (1 - ball.value)).clip(androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.height(40.dp))
                Row(Modifier.scale(0.6f + 0.4f * word.value).alpha(word.value)) {
                    Text(L("گل"), color = Color.White, style = vz(72.sp, FontWeight.Black).copy(
                        shadow = androidx.compose.ui.graphics.Shadow(Lime, Offset(-5f, 0f), 0f)))
                    Spacer(Modifier.width(4.dp))
                    Text(L("لایو"), color = Color.White, style = vz(72.sp, FontWeight.Black).copy(
                        shadow = androidx.compose.ui.graphics.Shadow(C.live, Offset(5f, 0f), 0f)))
                }
                Text(L("LIVE FOOTBALL · نتایج زنده"), color = Lime, style = lat(14.sp, FontWeight.ExtraBold).copy(letterSpacing = 4.sp),
                    modifier = Modifier.alpha(tag.value).padding(top = 12.dp))
            }
        }
    }
}

/** Ask once for the notification permission (Android 13+), right after the first launch. */
@Composable
private fun AskNotifications() {
    AskBackground()
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val p = Store.prefs()
        if (p.getBoolean("askedNotify", false)) return@LaunchedEffect
        delay(2500)
        p.edit().putBoolean("askedNotify", true).apply()
        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * Alerts with the app closed: Android stops the score checks of a closed app unless it may run in the background.
 * Asked (with one line of why) a little after start, at most three times, a week apart, and only while it isn't allowed yet.
 */
@Composable
private fun AskBackground() {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val p = Store.prefs()
        val n = p.getInt("askedBattery", 0); val last = p.getLong("askedBatteryAt", 0)
        if (batteryFree(ctx) || n >= 3 || System.currentTimeMillis() - last < 7 * 86_400_000L) return@LaunchedEffect
        delay(if (p.getBoolean("askedNotify", false)) 4000 else 9000)
        p.edit().putInt("askedBattery", n + 1).putLong("askedBatteryAt", System.currentTimeMillis()).apply()
        show = true
    }
    if (show) androidx.compose.material3.AlertDialog(
        onDismissRequest = { show = false }, containerColor = C.bg,
        title = { T(L("🔔 اعلان گل حتی وقتی برنامه بسته است"), 16, FontWeight.Black) },
        text = { T(L("اندروید برنامه‌های بسته را از کار می‌اندازد و اعلان گل نمی‌رسد. در پنجره‌ی بعد «اجازه» را بزنید تا گل‌لایو در پس‌زمینه نتایج را چک کند (مصرف باتری بسیار کم است)."), 14, color = C.mute) },
        confirmButton = { androidx.compose.material3.TextButton({ show = false; askBatteryFree(ctx) }) { T(L("اجازه بده"), 14, FontWeight.ExtraBold, LocalAccent.current) } },
        dismissButton = { androidx.compose.material3.TextButton({ show = false }) { T(L("بعداً"), 14, color = C.mute) } },
    )
}
