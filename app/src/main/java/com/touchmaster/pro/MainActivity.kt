package com.touchmaster.pro

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.touchmaster.pro.data.AppItem

class MainActivity : ComponentActivity() {
    private val apps by lazy {
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(packageManager).toString(), it.activityInfo.applicationInfo) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { TouchMasterApp() } }
    private fun isAccessibilityEnabled() = (getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == packageName }

    @Composable private fun TouchMasterApp() {
        var onboarding by remember { mutableStateOf(!getPreferences(0).getBoolean("onboarded", false)) }
        var enabled by remember { mutableStateOf(isAccessibilityEnabled()) }
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8B5CF6), secondary = Color(0xFF00C8FF), background = Color(0xFF08080D), surface = Color(0xFF151520))) {
            if (onboarding) Onboarding { getPreferences(0).edit().putBoolean("onboarded", true).apply(); onboarding = false }
            else MainScreen(enabled) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); enabled = isAccessibilityEnabled() }
        }
    }
    @Composable private fun Onboarding(onDone: () -> Unit) {
        var page by remember { mutableIntStateOf(0) }
        Surface(Modifier.fillMaxSize(), color = Color(0xFF08080D)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("TouchMaster Pro", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF00C8FF))
                Text(when (page) { 0 -> "Kullanıcı başlatmadan hiçbir dokunuş göndermez. Ekran içeriğini okumaz, internet kullanmaz ve gizli çalışmaz."; 1 -> "Ayarlar > Erişilebilirlik > TouchMaster Pro yolundan hizmeti açın. Android 13+ kısıtlaması varsa uygulama ayrıntılarından kısıtlanmış ayarlara izin verin."; else -> "Hazırsınız. Yalnızca kendi sorumluluğunuzda ve uygulama kurallarına uygun kullanın." }, color = Color.White)
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (page < 2) page++ else onDone() }, modifier = Modifier.fillMaxWidth()) { Text(if (page < 2) "Devam" else "Başla") }
            }
        }
    }
    @Composable private fun MainScreen(enabled: Boolean, openSettings: () -> Unit) {
        var tab by remember { mutableIntStateOf(0) }
        Scaffold(bottomBar = { NavigationBar { listOf("Ana Sayfa", "Uygulamalarım", "Araçlar", "Ayarlar").forEachIndexed { i, label -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(label) }) } } }) { pad ->
            Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("TouchMaster Pro", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF00C8FF))
                when (tab) {
                    0 -> { Card { Column(Modifier.padding(16.dp)) { Text("Erişilebilirlik", color = Color.White); Text(if (enabled) "Açık" else "Kapalı", color = if (enabled) Color.Green else Color.Red); if (!enabled) Button(onClick = openSettings) { Text("Etkinleştir") } } }; Text("Çekirdek motor hazır. Overlay paneli servis açıldıktan sonra gösterilir.", color = Color.LightGray) }
                    1 -> { Text("Uygulamalarım", style = MaterialTheme.typography.titleLarge); LazyColumn { items(apps) { app -> Card(onClick = { packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(app.label, Modifier.padding(16.dp), color = Color.White) } } } }
                    2 -> Text("Araçlar\n\nAuto Clicker • Nokta Editörü • Makrolar", color = Color.White)
                    else -> Text("Ayarlar\n\nKesinti politikası: Yeniden dene\nGörünüm ve güvenlik ayarları", color = Color.White)
                }
            }
        }
    }
}
