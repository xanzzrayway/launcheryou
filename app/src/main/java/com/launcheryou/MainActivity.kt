package com.launcheryou

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var store: SettingsStore
    private var apps by mutableStateOf<List<AppInfo>>(emptyList())
    private var homePress by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = SettingsStore(this)
        setContent {
            LauncherYouTheme(store.settings) { Home(store, apps, homePress) }
        }
    }

    override fun onResume() {
        super.onResume()
        val appCtx = applicationContext
        thread { apps = loadApps(appCtx) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homePress++   // tombol Home menutup laci & Control Center
    }
}

@Composable
fun Home(store: SettingsStore, apps: List<AppInfo>, homePress: Int) {
    val s = store.settings
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var drawer by remember { mutableStateOf(false) }
    var cc by remember { mutableStateOf(false) }

    LaunchedEffect(homePress) { drawer = false; cc = false }
    BackHandler(enabled = drawer || cc) { drawer = false; cc = false }

    val pinned = remember(apps, s.pinned) { s.pinned.mapNotNull { k -> apps.firstOrNull { it.key == k } } }
    val dock = remember(apps) { dockApps(ctx, apps) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var t = 0f
                detectVerticalDragGestures(onDragStart = { t = 0f }) { _, d ->
                    t += d
                    if (t < -120f && !drawer && !cc) { drawer = true; t = 0f }
                }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ClockWidget()
            Spacer(Modifier.height(24.dp))

            if (pinned.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainer.copy(alpha = .85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Beranda masih kosong. Buka laci aplikasi, tekan lama sebuah aplikasi, lalu pilih Pasang di beranda.",
                        Modifier.padding(20.dp), color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            pinned.chunked(s.columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        AppCell(
                            app, s, Modifier.weight(1f),
                            onClick = { launchApp(ctx, app) },
                            menu = { d -> AppMenu(app, s, store, d) }
                        )
                    }
                    repeat(s.columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.weight(1f))

            if (dock.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(36.dp),
                    color = cs.surfaceContainer.copy(alpha = .9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        dock.forEach { app ->
                            AppCell(app, s, Modifier.weight(1f), showLabel = false, onClick = { launchApp(ctx, app) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = { drawer = true },
                shape = CircleShape,
                color = cs.surfaceContainerHighest.copy(alpha = .92f),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("Cari aplikasi", color = cs.onSurfaceVariant)
                }
            }
        }

        // Geser ke bawah dari sisi kanan atas membuka Control Center (seperti iOS).
        if (s.controlCenter) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.5f)
                    .height(80.dp)
                    .pointerInput(Unit) {
                        var t = 0f
                        detectVerticalDragGestures(onDragStart = { t = 0f }) { _, d ->
                            t += d
                            if (t > 90f) { cc = true; t = -100000f }
                        }
                    }
            )
        }

        AnimatedVisibility(
            visible = drawer,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) { Drawer(apps, s, store) { drawer = false } }

        AnimatedVisibility(
            visible = cc,
            enter = slideInVertically { -it / 4 } + fadeIn(),
            exit = slideOutVertically { -it / 4 } + fadeOut()
        ) { ControlCenter { cc = false } }
    }
}

@Composable
fun ClockWidget() {
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (now.second * 1000L + now.nano / 1_000_000L))
        }
    }
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val date = remember(now) { now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("id", "ID"))) }
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainer.copy(alpha = .9f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text(time, fontSize = 72.sp, fontWeight = FontWeight.Light, color = cs.onSurface, letterSpacing = (-2).sp)
            Text(date, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun AppIcon(app: AppInfo, s: UiSettings, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val mono = if (s.themedIcons) app.mono else null
    val bg = app.bg
    val fg = app.fg
    Box(
        Modifier
            .size(size)
            .clip(iconShape(s.iconShape))
            .background(if (mono != null) cs.primaryContainer else cs.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Ikon bertema: lapisan monokrom (Android 13+) diwarnai dari palet Material You.
            mono != null -> Image(mono, null, Modifier.requiredSize(size * 1.5f), colorFilter = ColorFilter.tint(cs.onPrimaryContainer))
            // Ikon adaptif: gambar ulang dua lapisan agar mengikuti bentuk pilihan pengguna.
            bg != null && fg != null -> {
                Image(bg, null, Modifier.requiredSize(size * 1.5f))
                Image(fg, null, Modifier.requiredSize(size * 1.5f))
            }
            else -> Image(app.plain, null, Modifier.fillMaxSize().padding(size * 0.1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: AppInfo,
    s: UiSettings,
    modifier: Modifier = Modifier,
    showLabel: Boolean = s.showLabels,
    labelColor: Color = Color.White,
    onClick: () -> Unit,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = { if (menu != null) open = true })
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(app, s, 56.dp * s.iconScale)
            if (showLabel) {
                Text(
                    app.label,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = labelColor,
                    style = MaterialTheme.typography.labelMedium.copy(
                        shadow = if (labelColor == Color.White) Shadow(Color.Black.copy(alpha = .55f), blurRadius = 6f) else null
                    )
                )
            }
        }
        if (menu != null) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
        }
    }
}

@Composable
fun AppMenu(app: AppInfo, s: UiSettings, store: SettingsStore, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val pinned = app.key in s.pinned
    fun go(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    DropdownMenuItem(
        text = { Text(if (pinned) "Lepas dari beranda" else "Pasang di beranda") },
        onClick = {
            store.update { st -> st.copy(pinned = if (pinned) st.pinned - app.key else st.pinned + app.key) }
            dismiss()
        }
    )
    DropdownMenuItem(
        text = { Text("Info aplikasi") },
        onClick = { go(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))); dismiss() }
    )
    DropdownMenuItem(
        text = { Text("Copot pemasangan") },
        onClick = { go(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))); dismiss() }
    )
}

@Composable
fun Drawer(apps: List<AppInfo>, s: UiSettings, store: SettingsStore, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    val list = remember(apps, q) { if (q.isBlank()) apps else apps.filter { it.label.contains(q.trim(), ignoreCase = true) } }

    Surface(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = cs.surfaceContainerLow.copy(alpha = .97f)
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(Unit) {
                        var t = 0f
                        detectVerticalDragGestures(onDragStart = { t = 0f }) { _, d ->
                            t += d
                            if (t > 60f) { t = -100000f; onClose() }
                        }
                    }
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(cs.onSurfaceVariant.copy(alpha = .5f)))
            }
            TextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari di ponsel") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    list.firstOrNull()?.let { launchApp(ctx, it); onClose() }
                }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = cs.surfaceContainerHighest,
                    unfocusedContainerColor = cs.surfaceContainerHighest
                )
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(s.columns),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
            ) {
                items(list, key = { it.key }) { app ->
                    AppCell(
                        app, s, labelColor = cs.onSurface,
                        onClick = { launchApp(ctx, app); onClose() },
                        menu = { d -> AppMenu(app, s, store, d) }
                    )
                }
            }
        }
    }
}
