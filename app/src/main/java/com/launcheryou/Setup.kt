package com.launcheryou

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Setup awal dari nol: sambutan, warna, ikon, wallpaper, lalu jadikan launcher bawaan. */
@Composable
fun SetupWizard(store: SettingsStore) {
    val cs = MaterialTheme.colorScheme
    var step by remember { mutableIntStateOf(0) }
    val last = 4
    BackHandler(enabled = step > 0) { step-- }

    Surface(Modifier.fillMaxSize(), color = cs.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(last + 1) { i ->
                    val w by animateDpAsState(
                        if (i == step) 28.dp else 8.dp,
                        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                        label = "dot"
                    )
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(if (i <= step) cs.primary else cs.outline.copy(alpha = .3f))
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val fwd = targetState > initialState
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { if (fwd) it / 3 else -it / 3 } + fadeIn(tween(220)))
                        .togetherWith(
                            slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { if (fwd) -it / 3 else it / 3 } + fadeOut(tween(120))
                        )
                },
                label = "step"
            ) { st ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (st) {
                        0 -> StepWelcome()
                        1 -> StepColor(store)
                        2 -> StepIcons(store)
                        3 -> StepWallpaper(store)
                        else -> StepFinish()
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Kembali") } else Spacer(Modifier.width(1.dp))
                Button(onClick = {
                    if (step < last) step++ else store.update { st -> st.copy(setupDone = true) }
                }) { Text(if (step == 0) "Mulai" else if (step < last) "Lanjut" else "Selesai") }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, sub: String) {
    val cs = MaterialTheme.colorScheme
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
    Text(sub, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
}

@Composable
private fun StepWelcome() {
    val cs = MaterialTheme.colorScheme
    val rot by rememberInfiniteTransition(label = "welcome").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "rot"
    )
    Spacer(Modifier.height(24.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(220.dp).clip(FlowerShape(8, 0.09f, rot)).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(130.dp).clip(FlowerShape(8, 0.09f, -rot)).background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Home, null, Modifier.size(56.dp), tint = cs.onPrimary)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    StepHeader(
        "Selamat datang di LauncherYou",
        "Kita atur dari nol: warna, ikon, dan wallpaper, lalu jadikan launcher bawaan. Cuma beberapa langkah."
    )
}

@Composable
private fun StepColor(store: SettingsStore) {
    val s = store.settings
    val cs = MaterialTheme.colorScheme
    val canDynamic = Build.VERSION.SDK_INT >= 31
    StepHeader("Pilih warna", "Pilih tema siap pakai, atau biarkan warna mengikuti wallpaper (Android 12+).")
    ThemePresets(store)
    SwitchRow(
        "Warna dinamis",
        if (canDynamic) "Ambil warna dari wallpaper (Material You)" else "Perlu Android 12 atau lebih baru",
        s.dynamicColor && canDynamic, canDynamic
    ) { v -> store.update { st -> st.copy(dynamicColor = v) } }
    Text("Mode", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    ChipRow(listOf("Ikuti sistem" to 0, "Terang" to 1, "Gelap" to 2), s.themeMode) { v ->
        store.update { st -> st.copy(themeMode = v) }
    }
    SwitchRow("Hitam AMOLED", "Latar hitam pekat di mode gelap", s.amoled) { v ->
        store.update { st -> st.copy(amoled = v) }
    }
}

@Composable
private fun StepIcons(store: SettingsStore) {
    val s = store.settings
    val cs = MaterialTheme.colorScheme
    StepHeader("Gaya ikon", "Pilih bentuk ikon dan jumlah kolom di beranda.")
    Preview(s)
    Text("Bentuk", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    ChipRow(listOf("Lingkaran" to 0, "Squircle" to 1, "Kotak bulat" to 2, "Bunga" to 3), s.iconShape) { v ->
        store.update { st -> st.copy(iconShape = v) }
    }
    SwitchRow(
        "Ikon bertema",
        if (Build.VERSION.SDK_INT >= 33) "Warnai ikon monokrom sesuai tema" else "Perlu Android 13 atau lebih baru",
        s.themedIcons
    ) { v -> store.update { st -> st.copy(themedIcons = v) } }
    Text("Kolom ikon", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    ChipRow(listOf("4" to 4, "5" to 5, "6" to 6), s.columns) { v ->
        store.update { st -> st.copy(columns = v) }
    }
}

@Composable
private fun StepWallpaper(store: SettingsStore) {
    StepHeader("Wallpaper", "Pilih wallpaper atau lewati dulu, bisa diganti kapan saja di SetelanYou.")
    WallpaperPicker(store)
}

@Composable
private fun StepFinish() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    fun open(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(140.dp).clip(FlowerShape(10, 0.08f)).background(cs.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Check, null, Modifier.size(56.dp), tint = cs.onTertiaryContainer) }
    }
    StepHeader("Hampir selesai", "Jadikan LauncherYou launcher bawaan dan beri izin untuk Control Center.")
    Button(onClick = { open(Intent(Settings.ACTION_HOME_SETTINGS)) }) { Text("Jadikan launcher bawaan") }
    FilledTonalButton(onClick = {
        open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    }) { Text("Izinkan ubah kecerahan & rotasi") }
    FilledTonalButton(onClick = { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) {
        Text("Izinkan Jangan Ganggu")
    }
}

class ThemePreset(val name: String, val hue: Int, val mode: Int, val amoled: Boolean)

/** Tema siap pakai: kartu bunga berwarna, dipilih dengan satu ketukan. */
@Composable
fun ThemePresets(store: SettingsStore) {
    val s = store.settings
    val cs = MaterialTheme.colorScheme
    val presets = listOf(
        ThemePreset("Lavender", 255, 0, false),
        ThemePreset("Hutan", 150, 0, false),
        ThemePreset("Senja", 25, 0, false),
        ThemePreset("Mawar", 335, 0, false),
        ThemePreset("Samudra", 205, 0, false),
        ThemePreset("Midnight", 265, 2, true)
    )
    val rainbow = Brush.sweepGradient(
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsl(it, .65f, .55f) }
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (Build.VERSION.SDK_INT >= 31) {
            val sel = s.dynamicColor
            val sc by animateFloatAsState(if (sel) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "dyn")
            Column(
                Modifier.clickable { store.update { st -> st.copy(dynamicColor = true) } },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .graphicsLayer { scaleX = sc; scaleY = sc }
                        .clip(FlowerShape(10, 0.07f))
                        .background(rainbow)
                        .then(if (sel) Modifier.border(3.dp, cs.onSurface, FlowerShape(10, 0.07f)) else Modifier)
                )
                Text("Dinamis", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        presets.forEach { p ->
            val sel = !s.dynamicColor && s.seedHue == p.hue && s.amoled == p.amoled
            val sc by animateFloatAsState(if (sel) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "preset")
            Column(
                Modifier.clickable {
                    store.update { st ->
                        st.copy(dynamicColor = false, seedHue = p.hue, themeMode = p.mode, amoled = p.amoled)
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .graphicsLayer { scaleX = sc; scaleY = sc }
                        .clip(FlowerShape(10, 0.07f))
                        .background(Color.hsl(p.hue.toFloat(), .55f, if (p.amoled) .25f else .5f))
                        .then(if (sel) Modifier.border(3.dp, cs.onSurface, FlowerShape(10, 0.07f)) else Modifier)
                )
                Text(p.name, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}
