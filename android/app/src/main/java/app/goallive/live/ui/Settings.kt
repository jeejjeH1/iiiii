package app.goallive.live.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.goallive.live.data.Api
import app.goallive.live.data.Clubs
import app.goallive.live.data.MyTeam
import app.goallive.live.data.Store
import app.goallive.live.data.path
import app.goallive.live.data.s
import app.goallive.live.notify.LiveAlerts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf(false) }
    // re-read permission/battery state whenever the user comes back from system settings
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }
    val granted = remember(refresh) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val batteryFree = remember(refresh) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Column(Modifier.fillMaxSize()) {
        TopBar("تنظیمات", null)
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                SectionTitle("تیم من")
                Card {
                    val t = Store.myTeam
                    Row(Modifier.fillMaxWidth().clickable { picker = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (t != null) Crest(t.id, 34.dp) else Icon(Icons.Rounded.Favorite, null, tint = LocalAccent.current)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t?.let { Clubs.get(it.id)?.fa ?: it.name } ?: "انتخاب تیم من", fontWeight = FontWeight.Bold)
                            Text("رنگ اپ، کارت صفحه اول و ویجت با تیم شما هماهنگ می‌شود", color = C.mute, style = MaterialTheme.typography.labelSmall)
                        }
                        if (t != null) IconButton(onClick = { Store.pickMyTeam(null) }) { Icon(Icons.Rounded.Close, "حذف", tint = C.mute) }
                    }
                }
            }
            item {
                SectionTitle("اعلان‌ها")
                Card {
                    if (!granted) Row(Modifier.fillMaxWidth().clickable {
                        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = C.live)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("اجازه اعلان داده نشده", fontWeight = FontWeight.Bold, color = C.live)
                            Text("برای دریافت اعلان گل اینجا بزنید", color = C.mute, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Toggle("گل‌ها", "هر گل در بازی تیم‌ها و بازی‌های دنبال‌شده", Store.notifyGoals) { Store.setNotify(goals = it) }
                    Toggle("ترکیب تیم‌ها", "وقتی ترکیب رسمی اعلام می‌شود (حدود یک ساعت قبل از بازی)", Store.notifyLineups) { Store.setNotify(lineups = it) }
                    Toggle("شروع و پایان بازی", "سوت شروع و نتیجه نهایی", Store.notifyStartEnd) { Store.setNotify(startEnd = it) }
                    Row(Modifier.fillMaxWidth().clickable {
                        LiveAlerts.notify(ctx, 1, LiveAlerts.CH_GOALS, "⚽ گل! اعلان‌ها فعال است", "اعلان‌های گل این‌طور نمایش داده می‌شوند", 0)
                    }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, null, tint = LocalAccent.current)
                        Spacer(Modifier.width(12.dp))
                        Text("ارسال اعلان آزمایشی", fontWeight = FontWeight.Bold)
                    }
                }
                Text("اعلان‌ها برای تیم‌ها و بازی‌هایی می‌آید که ستاره‌دار کرده‌اید. اپ خودش هر دقیقه نتیجه بازی‌های زنده شما را بررسی می‌کند.",
                    color = C.mute, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
            }
            if (!batteryFree) item {
                Card {
                    Row(Modifier.fillMaxWidth().clickable {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                        }.onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
                    }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BatteryAlert, null, tint = C.gold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("اعلان سریع‌تر وقتی گوشی قفل است", fontWeight = FontWeight.Bold)
                            Text("بهینه‌سازی باتری را برای گل‌لایو خاموش کنید (اینجا بزنید)", color = C.mute, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                SectionTitle("ویجت صفحه اصلی")
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Widgets, null, tint = LocalAccent.current)
                        Spacer(Modifier.width(12.dp))
                        Text("روی جای خالی صفحه اصلی گوشی انگشت را نگه دارید، «ویجت‌ها» را بزنید و ویجت گل‌لایو را اضافه کنید. نتیجه زنده و بازی بعدی «تیم من» را نشان می‌دهد.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                SectionTitle("درباره")
                Card {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = C.mute)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
                            Text("گل‌لایو $v · اپ اندروید", fontWeight = FontWeight.Bold)
                            Text("داده‌ها از FotMob و ویدیوها از آپارات", color = C.mute, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    if (picker) TeamPicker(onDismiss = { picker = false }) { id, name ->
        picker = false
        scope.launch {
            // remember the club color for the widget and the app accent
            val color = if (Clubs.get(id) == null) runCatching {
                val d = Api.fm("teams", mapOf("id" to id, "ccode3" to "IRN"), 3_600_000)
                d.path("overview.teamColors").s("darkMode") ?: d.path("overview.teamColors").s("color")
            }.getOrNull() else null
            Store.pickMyTeam(MyTeam(id, name, color))
            LiveAlerts.start(ctx)
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, color = C.mute, style = MaterialTheme.typography.labelSmall)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = LocalAccent.current))
    }
}

@Composable
fun TeamPicker(onDismiss: () -> Unit, onPick: (Int, String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { res = emptyList(); return@LaunchedEffect }
        delay(300)
        res = runCatching { search(q.trim()).teams.map { (it.s("id")?.toIntOrNull() ?: 0) to (it.s("name") ?: "") } }.getOrDefault(emptyList()).take(8)
    }
    val popular = listOf(8633 to "Real Madrid", 8634 to "Barcelona", 10260 to "Manchester United", 8650 to "Liverpool", 101610 to "Persepolis", 101614 to "Esteghlal")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
        title = { Text("تیم من") },
        text = {
            Column {
                SearchField(q, "نام تیم…", autoFocus = true) { q = it }
                Spacer(Modifier.height(6.dp))
                (if (q.trim().length < 2) popular else res).forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(id, name) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Crest(id, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(Clubs.get(id)?.fa ?: name, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
    )
}
