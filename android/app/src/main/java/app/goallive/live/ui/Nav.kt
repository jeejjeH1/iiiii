package app.goallive.live.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/** Screen-to-screen navigation, available everywhere through [LocalNav]. */
class Nav(private val c: NavHostController?) {
    fun match(id: Int) { if (id > 0) c?.navigate("match/$id") }
    fun team(id: Int) { if (id > 0) c?.navigate("team/$id") }
    fun league(id: Int) { if (id > 0) c?.navigate("league/$id") }
    fun player(id: Int) { if (id > 0) c?.navigate("player/$id") }
    fun search() { c?.navigate("search") }
    fun compare(a: Int, b: Int = 0) { if (a > 0) c?.navigate("compare/$a/$b") }
    fun quiz() { c?.navigate("quiz") }
    fun calendar() { c?.navigate("calendar") }
    fun back() { c?.popBackStack() }
    fun tab(route: String) {
        val nc = c ?: return
        nc.navigate(route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

val LocalNav = staticCompositionLocalOf { Nav(null) }

/** The app's navigator, for code outside composition (the screenshot run drives the app through it). */
object NavHook { var nav: Nav? = null }
