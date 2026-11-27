package com.launcheryou

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/** Pilih paket ikon: bawaan, terpasang di perangkat, atau impor dari file ZIP mana saja. */
@Composable
fun IconPackSection(store: SettingsStore) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val s = store.settings
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var installed by remember { mutableStateOf<List<IconPackInfo>>(emptyList()) }
    var imported by remember { mutableStateOf<List<IconPackInfo>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        val r = withContext(Dispatchers.Default) {
            Triple(IconPacks.installed(ctx), IconPacks.imported(ctx), loadApps(ctx.applicationContext))
        }
        installed = r.first
        imported = r.second
        if (apps.isEmpty()) apps = r.third
    }
    LaunchedEffect(apps, s.iconPack) {
        withContext(Dispatchers.Default) { IconPacks.prepare(ctx.applicationContext, apps, s.iconPack) }
        IconPacks.version++
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            thread {
                val id = IconPacks.importZip(ctx, uri)
                Handler(Looper.getMainLooper()).post {
                    if (id != null) {
                        IconPacks.invalidate(id)
                        store.update { st -> st.copy(iconPack = id) }
                        refresh++
                        Toast.makeText(ctx, "Paket ikon diimpor", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Gagal: ZIP harus berisi appfilter.xml dan ikon PNG", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Pratinjau: beberapa aplikasi nyata dengan paket yang dipilih
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        apps.take(8).forEach { AppIcon(it, s, 52.dp) }
    }

    Text("Bawaan", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    IconPacks.builtins.forEach { p ->
        PackRow(p.label, s.iconPack == p.id) { store.update { st -> st.copy(iconPack = p.id) } }
    }

    Text("Terpasang di perangkat", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    if (installed.isEmpty()) {
        Text(
            "Belum ada paket ikon terpasang. Pasang dari Play Store, lalu kembali ke sini.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
    installed.forEach { p ->
        PackRow(p.label, s.iconPack == p.id) { store.update { st -> st.copy(iconPack = p.id) } }
    }

    if (imported.isNotEmpty()) {
        Text("Dari file", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        imported.forEach { p ->
            PackRow(p.label, s.iconPack == p.id, onDelete = {
                IconPacks.delete(ctx, p.id)
                if (s.iconPack == p.id) store.update { st -> st.copy(iconPack = "system") }
                refresh++
            }) { store.update { st -> st.copy(iconPack = p.id) } }
        }
    }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { picker.launch("*/*") }) { Text("Impor dari file ZIP") }
        FilledTonalButton(onClick = {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack&c=apps"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=icon%20pack&c=apps"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { ctx.startActivity(market) }.isFailure) runCatching { ctx.startActivity(web) }
        }) { Text("Cari di Play Store") }
        FilledTonalButton(onClick = { refresh++ }) { Text("Muat ulang") }
    }
    Text(
        "Paket ikon mengikuti format ADW/Nova/Apex/GO. Ikon dari paket dipakai apa adanya (bentuk ikon ditentukan paketnya). " +
            "ZIP harus berisi appfilter.xml dan ikon PNG.",
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
    )
}

@Composable
private fun PackRow(label: String, selected: Boolean, onDelete: (() -> Unit)? = null, onSelect: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f).padding(vertical = 10.dp), color = cs.onSurface)
        if (onDelete != null) TextButton(onClick = onDelete) { Text("Hapus") }
    }
}
