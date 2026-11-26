package com.launcheryou

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SettingsStore(this)
        setContent { LauncherYouTheme(store.settings) { SettingsScreen(store) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore) {
    val s = store.settings
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val canDynamic = Build.VERSION.SDK_INT >= 31
    val dynamicActive = s.dynamicColor && canDynamic
    fun open(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = cs.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text("SetelanYou") },
                scrollBehavior = bar,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = cs.surface,
                    scrolledContainerColor = cs.surfaceContainer
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Preview(s)

            Section("Warna") {
                SwitchRow(
                    "Warna dinamis",
                    if (canDynamic) "Ambil warna dari wallpaper (Material You)" else "Perlu Android 12 atau lebih baru",
                    s.dynamicColor && canDynamic, canDynamic
                ) { v -> store.update { st -> st.copy(dynamicColor = v) } }

                Column(Modifier.alpha(if (dynamicActive) .4f else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Warna kustom", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(255, 150, 32, 335, 285, 182).forEach { hue ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.hsl(hue.toFloat(), .55f, .5f))
                                    .then(if (s.seedHue == hue) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                                    .clickable(enabled = !dynamicActive) { store.update { st -> st.copy(seedHue = hue) } }
                            )
                        }
                    }
                    Slider(
                        value = s.seedHue.toFloat(),
                        onValueChange = { v -> store.update { st -> st.copy(seedHue = v.toInt()) } },
                        valueRange = 0f..360f,
                        enabled = !dynamicActive
                    )
                }

                Text("Mode", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                ChipRow(listOf("Ikuti sistem" to 0, "Terang" to 1, "Gelap" to 2), s.themeMode) { v ->
                    store.update { st -> st.copy(themeMode = v) }
                }
            }

            Section("Ikon") {
                Text("Bentuk", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                ChipRow(listOf("Lingkaran" to 0, "Squircle" to 1, "Kotak bulat" to 2), s.iconShape) { v ->
                    store.update { st -> st.copy(iconShape = v) }
                }
                SwitchRow(
                    "Ikon bertema",
                    if (Build.VERSION.SDK_INT >= 33) "Warnai ikon monokrom sesuai tema" else "Perlu Android 13 atau lebih baru",
                    s.themedIcons
                ) { v -> store.update { st -> st.copy(themedIcons = v) } }
                SwitchRow("Tampilkan label", "Nama aplikasi di bawah ikon", s.showLabels) { v ->
                    store.update { st -> st.copy(showLabels = v) }
                }
                Text("Ukuran ikon", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Slider(
                    value = s.iconScale,
                    onValueChange = { v -> store.update { st -> st.copy(iconScale = v) } },
                    valueRange = 0.8f..1.2f
                )
            }

            Section("Beranda") {
                Text("Kolom ikon", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                ChipRow(listOf("4" to 4, "5" to 5, "6" to 6), s.columns) { v ->
                    store.update { st -> st.copy(columns = v) }
                }
                SwitchRow("Control Center", "Geser ke bawah dari sisi kanan atas layar", s.controlCenter) { v ->
                    store.update { st -> st.copy(controlCenter = v) }
                }
                FilledTonalButton(onClick = { store.update { st -> st.copy(pinned = emptyList()) } }) {
                    Text("Kosongkan beranda")
                }
            }

            Section("Izin & launcher") {
                Button(onClick = { open(Intent(Settings.ACTION_HOME_SETTINGS)) }) { Text("Jadikan LauncherYou launcher bawaan") }
                FilledTonalButton(onClick = {
                    open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }) { Text("Izinkan ubah kecerahan & rotasi") }
                FilledTonalButton(onClick = { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) {
                    Text("Izinkan Jangan Ganggu")
                }
            }
        }
    }
}

@Composable
private fun Preview(s: UiSettings) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(32.dp), color = cs.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("09:41", fontSize = 56.sp, fontWeight = FontWeight.Light, color = cs.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    Triple(Icons.Rounded.Call, cs.primary, cs.onPrimary),
                    Triple(Icons.Rounded.Email, cs.secondaryContainer, cs.onSecondaryContainer),
                    Triple(Icons.Rounded.Place, cs.tertiaryContainer, cs.onTertiaryContainer),
                    Triple(Icons.Rounded.Settings, cs.surface, cs.onSurface)
                ).forEach { (icon, bg, fg) ->
                    Box(
                        Modifier.size(52.dp * s.iconScale).clip(iconShape(s.iconShape)).background(bg),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, tint = fg) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else .5f), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}
