package com.touchmaster.pro

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import com.touchmaster.pro.data.AppItem
import com.touchmaster.pro.data.ClickerSettings
import com.touchmaster.pro.data.PointEntry
import com.touchmaster.pro.service.TouchMasterAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF090A0F)
private val Panel = Color(0xFF13151D)
private val PanelSoft = Color(0xFF1A1D27)
private val Violet = Color(0xFF9B7BFF)
private val Blue = Color(0xFF66D9FF)
private val Muted = Color(0xFF9298A8)

class MainActivity : ComponentActivity() {
    private fun loadApps(): List<AppItem> {
        return packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(packageManager).toString(), it.activityInfo.applicationInfo) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TouchMasterTheme { TouchMasterApp() } }
    }

    private fun isAccessibilityEnabled() = (getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == packageName }

    private fun isNotificationGranted() = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    private fun isBatteryOptimizationDisabled() = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    private fun requestNotifications() { if (Build.VERSION.SDK_INT >= 33 && !isNotificationGranted()) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7001) }
    private fun requestBatteryOptimization() { if (!isBatteryOptimizationDisabled()) startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }

    private fun launchApp(app: AppItem) {
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "Önce Erişilebilirlik hizmetini etkinleştirin.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        val service = TouchMasterAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "Servis henüz hazır değil. Tekrar deneyin.", Toast.LENGTH_LONG).show()
            return
        }
        service.showPanel(listOf(PointEntry(1L, 0.5f, 0.5f, "1")), ClickerSettings())
        packageManager.getLaunchIntentForPackage(app.packageName)?.let(::startActivity)
    }

    @Composable
    private fun TouchMasterApp() {
        var tab by remember { mutableIntStateOf(0) }
        var enabled by remember { mutableStateOf(isAccessibilityEnabled()) }
        var notifications by remember { mutableStateOf(isNotificationGranted()) }
        var batteryOptimization by remember { mutableStateOf(isBatteryOptimizationDisabled()) }
        var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { enabled = isAccessibilityEnabled(); notifications = isNotificationGranted(); batteryOptimization = isBatteryOptimizationDisabled() } }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { loadApps() } }
        Scaffold(containerColor = Ink, bottomBar = { BottomBar(tab) { tab = it } }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(enabled) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); enabled = isAccessibilityEnabled() }
                    1 -> AppsScreen(apps, ::launchApp)
                    2 -> ToolsScreen()
                    else -> SettingsScreen(enabled, notifications, batteryOptimization, { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); enabled = isAccessibilityEnabled() }, { requestNotifications(); notifications = isNotificationGranted() }, { requestBatteryOptimization(); batteryOptimization = isBatteryOptimizationDisabled() })
                }
            }
        }
    }

    @Composable
    private fun HomeScreen(enabled: Boolean, openSettings: () -> Unit) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            Header("TouchMaster", "PRO CONTROL")
            Text("Kontrol merkezi", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Oyun ve uygulamalar için sade, görünür dokunma araçları.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            if (!enabled) Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF25213C), Panel)))) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(CircleShape).background(Violet.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Text("✦", color = Violet, fontSize = 24.sp) }; Spacer(Modifier.width(14.dp)); Column { Text("Kurulum gerekiyor", color = Color.White, fontWeight = FontWeight.Medium); Text("Erişilebilirlik hizmetini açın", color = Color(0xFFFFB86B), fontSize = 13.sp) } }
                        Button(onClick = openSettings, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet), modifier = Modifier.fillMaxWidth()) { Text("Hizmeti etkinleştir", color = Ink, fontWeight = FontWeight.Bold) }
                    }
                }
            } else Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp)) { Text("Hazır", color = Blue, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text("Uygulamalarım sekmesinden bir uygulama seçerek başlayın.", color = Color(0xFFBFC4D2), fontSize = 13.sp) } }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("01", "Aktif profil", Modifier.weight(1f))
                MetricCard("—", "Çalışma durumu", Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
            Text("Hızlı başlangıç", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            QuickCard("Auto Clicker", "Yeni bir nokta setiyle başla", "→")
        }
    }

    @Composable
    private fun AppsScreen(apps: List<AppItem>, launch: (AppItem) -> Unit) {
        var query by remember { mutableStateOf("") }
        val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            Header("Uygulamalarım", "LIBRARY")
            Text("Uygulama kütüphanesi", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Bir uygulama seçtiğinde kontrol paneli ekranda açılır.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Uygulama ara", color = Muted) }, leadingIcon = { Text("⌕", color = Blue, fontSize = 24.sp) }, shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Violet, unfocusedBorderColor = Color(0xFF2B2E3A), focusedContainerColor = Panel, unfocusedContainerColor = Panel), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("Cihazındaki uygulamalar  ·  ${filtered.size}", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.packageName }) { app -> AppCard(app) { launch(app) } }
            }
        }
    }

    @Composable
    private fun AppCard(app: AppItem, onClick: () -> Unit) {
        val context = LocalContext.current
        val icon by produceState<Bitmap?>(initialValue = null, app.packageName) { value = withContext(Dispatchers.IO) { app.icon?.loadIcon(context.packageManager)?.toBitmap(96) } }
        val bitmap = icon
        Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.height(154.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(17.dp)).background(PanelSoft), contentAlignment = Alignment.Center) { if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = app.label, contentScale = ContentScale.Fit, modifier = Modifier.size(44.dp)) else Text("•", color = Blue, fontSize = 30.sp) }
                Column { Text(app.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("Hazırla  ›", color = Blue, fontSize = 12.sp) }
            }
        }
    }

    @Composable private fun ToolsScreen() { SimpleSection("Araçlar", "TOOLS", listOf("Auto Clicker" to "Nokta bazlı otomasyon", "Multi Touch" to "Birden fazla hedef", "Swipe" to "Kaydırma hareketleri", "Makrolar" to "Adım adım komutlar")) }
    @Composable private fun SettingsScreen(enabled: Boolean, notifications: Boolean, batteryOptimization: Boolean, openSettings: () -> Unit, requestNotifications: () -> Unit, requestBatteryOptimization: () -> Unit) { Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) { Header("Ayarlar", "SETTINGS"); Text("İzinler", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold); Text("Zorunlu olan yalnızca Erişilebilirlik'tir. Diğerleri isteğe bağlıdır.", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(18.dp)); SettingRow("Erişilebilirlik", if (enabled) "Açık" else "Kapalı", if (!enabled) openSettings else null); if (Build.VERSION.SDK_INT >= 33) SettingRow("Bildirimler", if (notifications) "Açık" else "Önerilir", if (!notifications) requestNotifications else null); SettingRow("Pil optimizasyonu", if (batteryOptimization) "Devre dışı" else "Önerilir", if (!batteryOptimization) requestBatteryOptimization else null); Spacer(Modifier.height(18.dp)); Text("Görünüm", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); SettingRow("Panel görünümü", "Koyu cam", null); SettingRow("Güvenlik", "Hedef uygulamadan çıkınca durdur", null) } }
    @Composable private fun SimpleSection(title: String, eyebrow: String, rows: List<Pair<String, String>>) { Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) { Header(title, eyebrow); Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(18.dp)); rows.forEach { (a, b) -> SettingRow(a, b, null) } } }
    @Composable private fun SettingRow(title: String, subtitle: String, action: (() -> Unit)?) { Card(onClick = { action?.invoke() }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Medium); Text(subtitle, color = Muted, fontSize = 12.sp) }; if (action != null) Text("→", color = Blue, fontSize = 22.sp) } } }
    @Composable private fun MetricCard(value: String, label: String, modifier: Modifier) { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = modifier) { Column(Modifier.padding(16.dp)) { Text(value, color = Blue, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text(label, color = Muted, fontSize = 12.sp) } } }
    @Composable private fun QuickCard(title: String, subtitle: String, action: String) { Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Medium); Text(subtitle, color = Muted, fontSize = 12.sp) }; Text(action, color = Blue, fontSize = 24.sp) } } }
    @Composable private fun Header(title: String, eyebrow: String) { Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(eyebrow, color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }; Box(Modifier.size(34.dp).clip(CircleShape).background(PanelSoft), contentAlignment = Alignment.Center) { Text("TM", color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }
    @Composable private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) { NavigationBar(containerColor = Color(0xFF0D0F15), tonalElevation = 0.dp) { listOf("⌂" to "Ana", "▦" to "Uygulamalar", "◇" to "Araçlar", "⚙" to "Ayarlar").forEachIndexed { i, pair -> NavigationBarItem(selected = selected == i, onClick = { onSelect(i) }, icon = { Text(pair.first, fontSize = 20.sp) }, label = { Text(pair.second, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue, indicatorColor = Color(0xFF1C2330), unselectedIconColor = Muted, unselectedTextColor = Muted)) } } }
}

private fun Drawable.toBitmap(maxSize: Int): Bitmap { if (this is BitmapDrawable && bitmap != null) return Bitmap.createScaledBitmap(bitmap, maxSize, maxSize, true); val sourceWidth = intrinsicWidth.coerceAtLeast(1); val sourceHeight = intrinsicHeight.coerceAtLeast(1); val scale = minOf(maxSize.toFloat() / sourceWidth, maxSize.toFloat() / sourceHeight); val width = (sourceWidth * scale).toInt().coerceAtLeast(1); val height = (sourceHeight * scale).toInt().coerceAtLeast(1); val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); val canvas = Canvas(result); setBounds(0, 0, width, height); draw(canvas); return result }

@Composable private fun TouchMasterTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = darkColorScheme(primary = Violet, secondary = Blue, background = Ink, surface = Panel, onBackground = Color.White, onSurface = Color.White), content = content) }
