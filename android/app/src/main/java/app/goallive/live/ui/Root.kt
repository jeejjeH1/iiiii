package app.goallive.live.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
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

private val TABS = listOf(
    Triple("home", "بازی‌ها", Icons.Rounded.SportsSoccer),
    Triple("leagues", "لیگ‌ها", Icons.Rounded.EmojiEvents),
    Triple("following", "دنبال‌شده", Icons.Rounded.Star),
    Triple("settings", "تنظیمات", Icons.Rounded.Settings),
)

@Composable
fun Root(openMatch: MutableIntState) {
    val nc = rememberNavController()
    val nav = remember(nc) { Nav(nc) }
    val my = Store.myTeam
    val accent = accentFrom(my?.let { t -> Clubs.get(t.id)?.let { Color(it.accent ?: it.c1) } ?: parseColor(t.color) })

    GoalLiveTheme(accent) {
        CompositionLocalProvider(LocalNav provides nav) {
            val entry by nc.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            Scaffold(
                containerColor = C.bg,
                bottomBar = { if (TABS.any { it.first == route }) BottomBar(route, nav) },
            ) { pad ->
                NavHost(
                    nc, "home", Modifier.padding(pad),
                    // RTL: new pages come in from the left
                    enterTransition = { slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 5 } + fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { slideOutHorizontally(tween(240)) { -it / 5 } + fadeOut(tween(200)) },
                ) {
                    composable("home") { HomeScreen() }
                    composable("leagues") { LeaguesScreen() }
                    composable("following") { FollowingScreen() }
                    composable("settings") { SettingsScreen() }
                    composable("search") { SearchScreen() }
                    composable("match/{id}", listOf(navArgument("id") { type = NavType.IntType })) { MatchScreen(it.arguments?.getInt("id") ?: 0) }
                    composable("team/{id}", listOf(navArgument("id") { type = NavType.IntType })) { TeamScreen(it.arguments?.getInt("id") ?: 0) }
                    composable("league/{id}", listOf(navArgument("id") { type = NavType.IntType })) { LeagueScreen(it.arguments?.getInt("id") ?: 0) }
                    composable("player/{id}", listOf(navArgument("id") { type = NavType.IntType })) { PlayerScreen(it.arguments?.getInt("id") ?: 0) }
                }
                // after NavHost has set its graph: open the match a notification pointed at
                LaunchedEffect(openMatch.intValue) {
                    val id = openMatch.intValue
                    if (id > 0) { nav.match(id); openMatch.intValue = 0 }
                }
            }
            AskNotifications()
            Intro()
        }
    }
}

@Composable
private fun BottomBar(route: String?, nav: Nav) {
    NavigationBar(containerColor = C.card, tonalElevation = 0.dp) {
        TABS.forEach { (r, label, icon) ->
            NavigationBarItem(
                selected = r == route,
                onClick = { if (r != route) nav.tab(r) },
                icon = { Icon(icon, null) },
                label = { Text(label, fontWeight = if (r == route) FontWeight.Bold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary, indicatorColor = LocalAccent.current,
                    selectedTextColor = C.text, unselectedIconColor = C.mute, unselectedTextColor = C.mute,
                ),
            )
        }
    }
}

/** A short kick-off animation when the app opens: the ball drops in, spins and the logo appears. */
@Composable
private fun Intro() {
    var show by rememberSaveable { mutableStateOf(true) }
    val scale = remember { Animatable(0.2f) }
    val spin = remember { Animatable(-180f) }
    LaunchedEffect(Unit) {
        if (!show) return@LaunchedEffect
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        delay(250)
        show = false
    }
    LaunchedEffect(Unit) { spin.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) }
    AnimatedVisibility(show, enter = fadeIn(), exit = fadeOut(tween(300)) + scaleOut(targetScale = 1.15f)) {
        Box(
            Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF14321F), C.bg))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.SportsSoccer, null, tint = LocalAccent.current,
                    modifier = Modifier.size(96.dp).scale(scale.value).rotate(spin.value))
                Spacer(Modifier.height(16.dp))
                Text("گل‌لایو", fontSize = 40.sp, fontWeight = FontWeight.Black, color = C.text, style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.scale(0.8f + 0.2f * scale.value))
                Text("نتایج زنده فوتبال", color = C.mute, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
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
        delay(1500)
        p.edit().putBoolean("askedNotify", true).apply()
        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
