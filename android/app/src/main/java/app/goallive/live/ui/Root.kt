package app.goallive.live.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
    Triple("home", "بازی‌ها", { Ic.ball }),
    Triple("leagues", "لیگ‌ها", { Ic.trophy }),
    Triple("following", "دنبال‌شده‌ها", { Ic.star }),
    Triple("more", "بیشتر", { Ic.grid }),
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
    LaunchedEffect(light) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (light) SystemBarStyle.light(0, 0) else SystemBarStyle.dark(0),
            navigationBarStyle = if (light) SystemBarStyle.light(0xFFE9EEE9.toInt(), 0) else SystemBarStyle.dark(0xFF0B0F14.toInt()),
        )
    }

    GoalLiveTheme(accent) {
        CompositionLocalProvider(LocalNav provides nav) {
            val entry by nc.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            Box(Modifier.fillMaxSize()) {
                Stadium(accent)
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    // the in-app streaming browser has its own header and uses the whole screen
                    val immersive = route?.startsWith("web/") == true
                    if (!immersive) TopBar(canBack = route != null && TABS.none { it.first == route }, nav)
                    SharedTransitionLayout(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalShared provides this) {
                    NavHost(
                        nc, "home", Modifier.fillMaxSize(),
                        // the site's view transition: fade in while rising a little
                        enterTransition = { fadeIn(tween(300)) + slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 40 } },
                        exitTransition = { fadeOut(tween(150)) },
                        popEnterTransition = { fadeIn(tween(250)) },
                        popExitTransition = { fadeOut(tween(150)) },
                    ) {
                        screen("home") { HomeScreen() }
                        screen("leagues") { LeaguesScreen() }
                        screen("following") { FollowingScreen() }
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
                        screen("web/{key}") { WebScreen(it.arguments?.getString("key") ?: "namava") }
                        screen("calendar") { CalendarScreen() }
                        screen("assistant") { AssistantScreen() }
                        screen("player/{id}", listOf(navArgument("id") { type = NavType.IntType })) { PlayerScreen(it.arguments?.getInt("id") ?: 0) }
                    }
                    }
                    }
                    // after NavHost has set its graph: open the match a notification pointed at
                    LaunchedEffect(openMatch.intValue) {
                        val id = openMatch.intValue
                        if (id > 0) { nav.match(id); openMatch.intValue = 0 }
                    }
                }
                // the streaming browser and the assistant's chat use the bottom of the screen themselves
                if (route?.startsWith("web/") != true && route != "assistant") BottomNav(route, nav, Modifier.align(Alignment.BottomCenter))
            }
            AskNotifications()
            // first launch: ask for the favourite team, like the site's onboarding sheet
            var onboard by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { if (!Store.onboarded) { delay(1800); onboard = true } }
            if (onboard) TeamPicker { onboard = false }
            Intro()
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
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canBack) IconBtn(Ic.back, "بازگشت", size = 22.dp) { nav.back() }
        Row(Modifier.clickable { nav.tab("home") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(34.dp).glow(accent.copy(alpha = 0.35f), 8.dp, RoundedCornerShape(11.dp))
                .clip(RoundedCornerShape(11.dp)))
            T("گل‌لایو", 20, FontWeight.Black)
            Box(Modifier.border(1.dp, C.live, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text("LIVE", color = C.live, style = lat(11.sp, FontWeight.Bold).copy(letterSpacing = 2.sp))
            }
        }
        Spacer(Modifier.weight(1f))
        IconBtn(Ic.search, "جستجو") { nav.tab("search") }
        IconBtn(Ic.sliders, "تنظیمات") { nav.tab("settings") }
    }
}

/** ".nav": the floating pill tab bar. */
@Composable
private fun BottomNav(route: String?, nav: Nav, modifier: Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().height(64.dp).clip(shape).background(C.cardSolid.copy(alpha = 0.97f))
                .border(1.dp, C.line2, shape).padding(6.dp),
        ) {
            TABS.forEach { (r, label, icon) ->
                val on = r == route
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { role = Role.Tab; selected = on; contentDescription = label }.then(if (on) Modifier.glow(accent.copy(alpha = 0.35f), 8.dp, RoundedCornerShape(18.dp)) else Modifier)
                        .clip(RoundedCornerShape(18.dp)).background(if (on) accent else Color.Transparent).clickable { if (!on) nav.tab(r) else nav.root(r) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    val c = if (on) LocalAccentInk.current else C.mute
                    Icon(icon(), null, Modifier.size(21.dp), tint = c)
                    Spacer(Modifier.height(1.dp))
                    T(label, 12, FontWeight.Bold, c, maxLines = 1)
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
                    Text("گل", color = Color.White, style = vz(72.sp, FontWeight.Black).copy(
                        shadow = androidx.compose.ui.graphics.Shadow(Lime, Offset(-5f, 0f), 0f)))
                    Spacer(Modifier.width(4.dp))
                    Text("لایو", color = Color.White, style = vz(72.sp, FontWeight.Black).copy(
                        shadow = androidx.compose.ui.graphics.Shadow(C.live, Offset(5f, 0f), 0f)))
                }
                Text("LIVE FOOTBALL · نتایج زنده", color = Lime, style = lat(14.sp, FontWeight.ExtraBold).copy(letterSpacing = 4.sp),
                    modifier = Modifier.alpha(tag.value).padding(top = 12.dp))
            }
        }
    }
}

/** Ask once for the notification permission (Android 13+), right after the first launch. */
@Composable
private fun AskNotifications() {
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
