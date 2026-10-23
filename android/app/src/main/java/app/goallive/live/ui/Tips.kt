package app.goallive.live.ui

import app.goallive.live.L

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.goallive.live.data.Store

/** A short "how does this work?" guide: numbered steps and one big button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToSheet(emoji: String, title: String, steps: List<String>, button: String = L("فهمیدم، بزن بریم"), onClose: () -> Unit, onGo: () -> Unit = onClose) {
    val accent = LocalAccent.current
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.cardSolid,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { T(emoji, 30) }
            T(title, 20, FontWeight.Black, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp))
            steps.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                        T(fa(i + 1), 13, FontWeight.Black, LocalAccentInk.current)
                    }
                    T(s, 14, color = C.ink, modifier = Modifier.weight(1f).padding(top = 3.dp))
                }
            }
            Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(accent).clickable(onClick = onGo),
                contentAlignment = Alignment.Center) { T(button, 15, FontWeight.Black, LocalAccentInk.current) }
        }
    }
}

/** Shows a guide by itself the first time a feature is opened; [open] also lets a "?" button bring it back. */
@Composable
fun rememberTip(key: String): MutableState<Boolean> = remember(key) { mutableStateOf(!Store.tipSeen(key)) }

/** The small "?" button that reopens a guide. */
@Composable
fun HelpButton(onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(C.card2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Ic.help, L("راهنما"), Modifier.size(20.dp), tint = C.mute)
    }
}

val FANTASY_STEPS = listOf(
    L("یک لیگ انتخاب کنید و با ۱۰۰ میلیون بودجه ۱۱ بازیکن بخرید: ۱ دروازه‌بان، ۳ تا ۵ مدافع، ۲ تا ۵ هافبک و ۱ تا ۳ مهاجم."),
    L("از هر باشگاه حداکثر ۳ بازیکن. قیمت‌ها از ارزش بازار واقعی بازیکن‌ها ساخته شده‌اند."),
    L("یک کاپیتان انتخاب کنید؛ امتیاز او دو برابر حساب می‌شود."),
    L("با شروع هر هفته تیم شما قفل می‌شود و امتیازها از آمار واقعی بازی‌ها (گل، پاس گل، کلین‌شیت، نمره) حساب می‌شود."),
)

val COACH_STEPS = listOf(
    L("قبل از بازی، یکی از دو تیم و سیستم بازی (مثلاً ۴-۳-۳) را انتخاب کنید و ۱۱ بازیکن بچینید."),
    L("با سوت شروع ترکیبتان قفل می‌شود؛ در طول بازی تا ۵ تعویض دارید که در همان دقیقه ثبت می‌شود."),
    L("نمره شما از نمره واقعی بازیکن‌هایی که انتخاب کردید ساخته می‌شود و با تصمیم‌های سرمربی واقعی مقایسه می‌شود."),
)

val VOICE_STEPS = listOf(
    L("گل‌لایو بازی را مثل یک گزارشگر به فارسی تعریف می‌کند: گل‌ها، کارت‌ها، موقعیت‌ها و نتیجه."),
    L("گوشی را قفل کنید یا سراغ برنامه دیگری بروید؛ گزارش ادامه دارد و از اعلان می‌شود قطعش کرد."),
    L("اگر گوشی صدای فارسی ندارد، خودکار با صدای گوینده آنلاین پخش می‌شود. در تنظیمات می‌توانید گوینده مرد یا زن را انتخاب کنید."),
)
