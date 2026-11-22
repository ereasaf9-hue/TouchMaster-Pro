package com.touchmaster.pro

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touchmaster.pro.macro.MacroParser
import com.touchmaster.pro.macro.MacroLimits
import com.touchmaster.pro.macro.MacroStep
import com.touchmaster.pro.macro.MacroExecutor
import com.touchmaster.pro.macro.MacroRuntime
import kotlinx.coroutines.CancellationException
import com.touchmaster.pro.service.TouchMasterAccessibilityService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF08080D)
private val CardInk = Color(0xFF151520)
private val Violet = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF00C8FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { TouchMasterApp() } }
}

@Composable
private fun TouchMasterApp() {
    val ctx = LocalContext.current
    val connected by TouchMasterAccessibilityService.connected.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var x by remember { mutableStateOf("500") }; var y by remember { mutableStateOf("900") }
    var interval by remember { mutableStateOf("500") }; var count by remember { mutableStateOf("10") }
    var macro by remember { mutableStateOf("CLICK 500 900\nDELAY 300\nLOOP 3\nCLICK 500 900\nEND_LOOP") }
    var message by remember { mutableStateOf("") }
    var worker by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val tabLabels = listOf(stringResource(R.string.tab_home), stringResource(R.string.tab_macros), stringResource(R.string.tab_tools), stringResource(R.string.tab_settings))
    MaterialTheme(colorScheme = darkColorScheme(primary = Violet, secondary = Cyan, background = Ink, surface = CardInk)) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = CardInk) {
                tabLabels.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Text(listOf("⌂", "≋", "⊙", "⚙")[index]) }, label = { Text(label) })
                }
            }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Spacer(Modifier.height(8.dp)); Text("TouchMaster Pro", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(stringResource(R.string.app_tagline), color = Cyan) }
                if (!connected) item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF38251A))) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.accessibility_required), color = Color.White)
                            Spacer(Modifier.height(8.dp)); Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Erişilebilirlik ayarlarını aç") }
                        }
                    }
                }
                when (tab) {
                    0 -> {
                        item { InfoCard("Gizlilik ve kontrol", stringResource(R.string.privacy_notice)) }
                        item { InfoCard("Hızlı başlangıç", "Otomasyonu yalnızca Başlat düğmesine bastığınızda çalıştırın. Durdur düğmesi her an erişilebilir.") }
                        item { Button(onClick = { tab = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Auto Clicker") } }
                        item { Button(onClick = { tab = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Makro düzenleyici") } }
                    }
                    1 -> {
                        item { Text(stringResource(R.string.macro_editor), color = Color.White, style = MaterialTheme.typography.titleLarge) }
                        item { OutlinedTextField(value = macro, onValueChange = { macro = it }, modifier = Modifier.fillMaxWidth().height(240.dp), label = { Text("Komutlar") }, placeholder = { Text(stringResource(R.string.macro_hint)) }) }
                        item { Button(onClick = {
                            val dm = ctx.resources.displayMetrics
                            val parsed = MacroParser(MacroLimits(dm.widthPixels, dm.heightPixels)).parse(macro)
                            message = if (parsed.isSuccess) "Makro sözdizimi geçerli (${parsed.steps.size} adım)." else parsed.errors.joinToString("\n")
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_macro)) } }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(enabled = connected && !running, onClick = {
                                    val dm = ctx.resources.displayMetrics
                                    val parsed = MacroParser(MacroLimits(dm.widthPixels, dm.heightPixels)).parse(macro)
                                    if (!parsed.isSuccess) { message = parsed.errors.joinToString("\n"); return@Button }
                                    running = true
                                    worker = scope.launch {
                                        try {
                                            MacroExecutor(object : MacroRuntime {
                                                override suspend fun perform(step: MacroStep) {
                                                    val service = TouchMasterAccessibilityService.service() ?: error("Erişilebilirlik servisi bağlantısı kesildi.")
                                                    val ok = when (step) {
                                                        is MacroStep.Click -> service.tap(step.x.toFloat(), step.y.toFloat(), step.durationMs ?: 40L)
                                                        is MacroStep.Press -> service.tap(step.x.toFloat(), step.y.toFloat(), step.durationMs)
                                                        is MacroStep.Swipe -> service.swipe(step.x1.toFloat(), step.y1.toFloat(), step.x2.toFloat(), step.y2.toFloat(), step.durationMs)
                                                        else -> true
                                                    }
                                                    if (!ok) error("Gesture sistem tarafından iptal edildi (satır ${step.line}).")
                                                }
                                                override suspend fun delay(milliseconds: Long) { kotlinx.coroutines.delay(milliseconds) }
                                                override suspend fun checkpoint() { if (!running) throw CancellationException("Kullanıcı durdurdu") }
                                            }).execute(parsed.steps)
                                            message = "Makro tamamlandı."
                                        } catch (e: CancellationException) { message = "Makro durduruldu." }
                                        catch (e: Exception) { message = e.message ?: "Makro çalıştırılamadı." }
                                        finally { running = false }
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("Makroyu çalıştır") }
                                OutlinedButton(enabled = running, onClick = { running = false; worker?.cancel(); message = "Durduruldu." }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stop)) }
                            }
                        }
                        if (message.isNotBlank()) item { Text(message, color = Cyan) }
                        item { InfoCard("Komutlar", "CLICK x y [ms] · PRESS x y ms · SWIPE x1 y1 x2 y2 ms · DELAY ms · LOOP n / END_LOOP · LOOP_FOREVER") }
                    }
                    2 -> {
                        item { Text("Auto Clicker", color = Color.White, style = MaterialTheme.typography.titleLarge) }
                        item { NumberField(stringResource(R.string.x_coordinate), x) { x = it } }
                        item { NumberField(stringResource(R.string.y_coordinate), y) { y = it } }
                        item { NumberField(stringResource(R.string.interval_ms), interval) { interval = it } }
                        item { NumberField(stringResource(R.string.click_count), count) { count = it } }
                        item { InfoCard("Sistem sınırı", "Android gesture gecikmesi cihaza bağlıdır. Bu uygulama milisaniye hassasiyetini garanti etmez.") }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(enabled = !running && connected, onClick = {
                                    val service = TouchMasterAccessibilityService.service()
                                    val px = x.toFloatOrNull(); val py = y.toFloatOrNull(); val gap = (interval.toLongOrNull() ?: 500L).coerceAtLeast(100L); val n = (count.toIntOrNull() ?: 1).coerceIn(1, 10_000)
                                    if (service == null || px == null || py == null) { message = "Koordinatları kontrol edin ve erişilebilirlik servisini açın."; return@Button }
                                    running = true; message = "Çalışıyor — durdurmak için düğmeye basın."
                                    worker = scope.launch {
                                        try { repeat(n) { if (!running) return@launch; val ok = service.tap(px, py, 40); if (!ok) { message = "Gesture sistem tarafından iptal edildi."; running = false; return@launch }; delay((gap - 40).coerceAtLeast(0)) } }
                                        catch (e: Exception) { message = e.message ?: "Otomasyon durduruldu." }
                                        finally { running = false }
                                    }
                                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.start)) }
                                OutlinedButton(enabled = running, onClick = { running = false; worker?.cancel(); message = "Durduruldu." }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stop)) }
                            }
                        }
                        if (message.isNotBlank()) item { Text(message, color = Cyan) }
                    }
                    else -> {
                        item { Text(stringResource(R.string.settings_title), color = Color.White, style = MaterialTheme.typography.titleLarge) }
                        item { InfoCard("İzinler", "Dokunma otomasyonu için erişilebilirlik izni gerekir. İsteğe bağlı overlay bu başlangıç sürümünde etkin değildir.") }
                        item { InfoCard("Tanılama", stringResource(R.string.diagnostics_unavailable)) }
                        item { InfoCard("Gelişmiş", "Shizuku desteği kapalıdır ve bu sürümde sunulmamaktadır. Root erişimi kullanılmaz.") }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = CardInk), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) { Text(title, color = Cyan, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text(body, color = Color.White) }
    }
}

@Composable private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { onChange(it.filter(Char::isDigit).take(7)) }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
}
