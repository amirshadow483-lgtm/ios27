package com.glasshome.launcher.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasshome.launcher.system.defaultHomePackage
import com.glasshome.launcher.system.openHomeSettings

/**
 * Persian recovery page. Reachable from: Settings (top banner), Control Center (Home tile),
 * long-press on empty Home area, and shown automatically first after a crash.
 */
@Composable
fun RecoveryScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val homes = remember(vm.tick) {
        try { pm.queryIntentActivities(homeIntent, 0).map { it.loadLabel(pm).toString() + "  (" + it.activityInfo.packageName + ")" } }
        catch (e: Throwable) { emptyList() }
    }
    val current = remember(vm.tick) { defaultHomePackage(ctx) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FullScreenPanel(dim = 0.6f) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("بازگشت به لانچر اصلی (MIUI / POCO)", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                if (vm.crashRecovery) {
                    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), strength = 0.5f) {
                        Column(Modifier.padding(14.dp)) {
                            Text("برنامه در اجرای قبلی با خطا بسته شد.", color = Danger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("می‌توانید به لانچر اصلی برگردید یا با جلوه‌های سبک‌تر ادامه دهید.", color = textPrimary(), fontSize = 14.sp)
                        }
                    }
                }

                GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), strength = 0.4f) {
                    Text("این گزینه فقط «برنامه صفحه اصلی (Home)» را عوض می‌کند. سیستم‌عامل، رام MIUI و اطلاعات شخصی شما " +
                        "هیچ تغییری نمی‌کنند و لانچر اصلی هرگز حذف نمی‌شود. اگر گوشی اصلاً روشن نمی‌شود (boot نمی‌شود) یا رام آسیب دیده، " +
                        "این برنامه نمی‌تواند کمکی کند؛ در آن حالت باید از روش‌های رسمی ریکاوری شیائومی استفاده کنید.",
                        color = textPrimary(), fontSize = 14.sp, modifier = Modifier.padding(14.dp))
                }

                GlassSurface(Modifier.fillMaxWidth().clickable { openHomeSettings(ctx) }, RoundedCornerShape(20.dp), strength = 0.6f) {
                    Text("باز کردن تنظیمات برنامه Home پیش‌فرض ‹", color = Accent, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(18.dp))
                }

                Card("مراحل (روش ۱ – ساده)") {
                    Step("۱. دکمه بالا را بزنید. اگر صفحه باز نشد: تنظیمات ← برنامه‌ها ← مدیریت برنامه‌ها ← برنامه‌های پیش‌فرض ← لانچر (Launcher / Home screen).")
                    Step("۲. گزینه «System launcher» یا «POCO Launcher» را انتخاب کنید.")
                    Step("۳. دکمه Home را بزنید؛ لانچر اصلی باید باز شود. (نام منوها در نسخه‌های MIUI ممکن است کمی فرق کند.)")
                }
                Card("اگر دوباره Glass Home باز شد (روش ۲)") {
                    Step("تنظیمات ← برنامه‌ها ← مدیریت برنامه‌ها ← Glass Home ← تنظیم به‌عنوان پیش‌فرض (Set as default) ← پاک کردن پیش‌فرض‌ها (Clear defaults). سپس Home را بزنید و لانچر اصلی را با گزینه «همیشه» انتخاب کنید.")
                }
                Card("اگر برنامه مدام کرش می‌کند و به تنظیمات نمی‌رسید (روش ۳)") {
                    Step("• از نوار اعلان (کشیدن از لبه بالا) آیکن چرخ‌دنده تنظیمات سیستم را باز کنید و روش ۱ یا ۲ را انجام دهید.")
                    Step("• یا گوشی را در Safe Mode روشن کنید (برنامه‌های شخص ثالث غیرفعال می‌شوند و لانچر سیستم اجرا می‌شود). روش دقیق در MIUI ممکن است فرق کند؛ معمولاً: خاموش کردن، روشن کردن و نگه داشتن کلید کاهش صدا هنگام نمایش لوگو. سپس Glass Home را از Settings ← Apps حذف کنید.")
                    Step("• حذف Glass Home فقط خود این برنامه را پاک می‌کند، نه برنامه‌ها یا اطلاعات دیگر را.")
                }
                Card("روش ۴ – با کامپیوتر (ADB، فقط در صورت نیاز)") {
                    Mono("adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME")
                    Mono("adb uninstall com.glasshome.launcher")
                    Step("دستور اول لانچر پیش‌فرض فعلی را نشان می‌دهد؛ دستور دوم فقط Glass Home را حذف می‌کند.")
                }

                Card("لانچرهای نصب‌شده روی این گوشی") {
                    Step("پیش‌فرض فعلی: ${current ?: "تعیین نشده"}")
                    homes.forEach { Mono(it) }
                    Step("معمولاً لانچر اصلی com.miui.home یا com.mi.android.globallauncher است.")
                }

                if (vm.crashRecovery) {
                    GlassSurface(Modifier.fillMaxWidth().clickable { vm.dismissCrash() }, RoundedCornerShape(20.dp), strength = 0.5f) {
                        Text("ادامه با Glass Home", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    GlassSurface(Modifier.fillMaxWidth().clickable { vm.close() }, RoundedCornerShape(20.dp), strength = 0.4f) {
                        Text("بستن", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), strength = 0.35f) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = textPrimary(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            content()
        }
    }
}
@Composable private fun Step(t: String) = Text(t, color = textPrimary(), fontSize = 14.sp, lineHeight = 22.sp)
@Composable private fun Mono(t: String) = Text(t, color = textSecondary(), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
    modifier = Modifier.fillMaxWidth())
