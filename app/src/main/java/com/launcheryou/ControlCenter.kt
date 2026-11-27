package com.launcheryou

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings as SettingsIcon
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun pressMedia(am: AudioManager, code: Int) {
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
}

private fun hasListener(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

/** Sesi media aktif (butuh akses notifikasi). Null kalau belum diizinkan atau tidak ada yang diputar. */
private fun activeController(ctx: Context): MediaController? {
    if (!hasListener(ctx)) return null
    val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return null
    val list = runCatching { msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java)) }.getOrNull().orEmpty()
    return list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
}

private fun readBattery(ctx: Context): Pair<Int, Boolean> {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    return (if (level >= 0 && scale > 0) level * 100 / scale else 0) to charging
}

private fun readBright(ctx: Context): Float =
    Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

private fun readVolume(am: AudioManager): Float {
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    return if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0f
}

private fun locationOn(ctx: Context): Boolean =
    runCatching { LocationManagerCompat.isLocationEnabled(ctx.getSystemService(LocationManager::class.java)) }.getOrDefault(false)

@Composable
fun ControlCenter(store: SettingsStore, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val cs = MaterialTheme.colorScheme
    val s = store.settings
    val isDark = s.themeMode == 2 || (s.themeMode == 0 && isSystemInDarkTheme())

    // Semua status dibaca ulang tiap ~0,8 dtk selama panel terbuka, jadi selalu sesuai kondisi asli perangkat.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            tick++
        }
    }

    val am = remember { app.getSystemService(AudioManager::class.java) }
    val nm = remember { app.getSystemService(NotificationManager::class.java) }
    val cam = remember { app.getSystemService(CameraManager::class.java) }

    val wifi = remember(tick) { runCatching { app.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false) }
    val bt = remember(tick) { runCatching { app.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false) }
    val airplane = remember(tick) { Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }
    val cellular = remember(tick) {
        runCatching {
            val c = app.getSystemService(ConnectivityManager::class.java)
            c.getNetworkCapabilities(c.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }.getOrDefault(false)
    }
    val location = remember(tick) { locationOn(app) }
    val autoRotate = remember(tick) { Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1 }
    val dnd = remember(tick) { nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }
    val ringer = remember(tick) { am.ringerMode }
    val batt = remember(tick) { readBattery(app) }
    val mc = remember(tick) { activeController(app) }
    val listener = remember(tick) { hasListener(app) }

    var bright by remember { mutableFloatStateOf(readBright(app)) }
    var volume by remember { mutableFloatStateOf(readVolume(am)) }
    var touched by remember { mutableLongStateOf(0L) }
    LaunchedEffect(tick) {
        if (System.currentTimeMillis() - touched > 1500L) {
            bright = readBright(app)
            volume = readVolume(am)
        }
    }
    var askedWrite by remember { mutableStateOf(false) }

    // Senter
    val camId = remember {
        runCatching {
            cam.cameraIdList.firstOrNull { cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }
    var torch by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == camId) torch = enabled }
        }
        cam.registerTorchCallback(cb, null)
        onDispose { cam.unregisterTorchCallback(cb) }
    }

    fun go(i: Intent, close: Boolean = true) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        if (close) onClose()
    }
    fun needWrite() {
        if (!askedWrite) {
            askedWrite = true
            go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }
    }
    // Aksi sistem asli lewat layanan aksesibilitas (kunci layar, notifikasi, Quick Settings sistem).
    fun a11y(action: Int) {
        val svc = LockService.instance
        if (svc == null) {
            Toast.makeText(ctx, "Aktifkan LauncherYou di Aksesibilitas dulu", Toast.LENGTH_LONG).show()
            go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            onClose()
            svc.performGlobalAction(action)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .5f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
            .pointerInput(Unit) {
                var t = 0f
                detectVerticalDragGestures(onDragStart = { t = 0f }) { _, d ->
                    t += d
                    if (t < -100f) { t = -100000f; onClose() }
                }
            }
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .pointerInput(Unit) { detectTapGestures { } },   // tap di sela panel tidak menutup
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: baterai + pintasan SetelanYou
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${batt.first}%", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(
                        if (batt.second) "Sedang mengisi" else "Baterai",
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .8f)
                    )
                }
                FlowerButton(Icons.Rounded.SettingsIcon, "SetelanYou", cs.tertiaryContainer, cs.onTertiaryContainer) {
                    go(Intent(ctx, SettingsActivity::class.java))
                }
            }

            // Konektivitas: status asli, ketuk membuka panel sistem
            CcCard {
                TileRow {
                    Tile(Icons.Rounded.Wifi, "Wi-Fi", wifi, Modifier.weight(1f)) {
                        go(
                            if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                            else Intent(Settings.ACTION_WIFI_SETTINGS),
                            false
                        )
                    }
                    Tile(Icons.Rounded.Bluetooth, "Bluetooth", bt, Modifier.weight(1f)) {
                        go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    Tile(Icons.Rounded.SignalCellularAlt, "Data", cellular && !airplane, Modifier.weight(1f)) {
                        go(
                            if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                            else Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
                            false
                        )
                    }
                    Tile(Icons.Rounded.AirplanemodeActive, "Pesawat", airplane, Modifier.weight(1f)) {
                        go(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                    }
                }
                Text(
                    "Android melarang aplikasi mengubah Wi-Fi/Bluetooth/Data langsung. Ketuk untuk panel sistem, atau pakai Panel QS di bawah.",
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
                )
            }

            // Media: judul lagu & kontrol asli (butuh akses notifikasi), tanpa itu tetap bisa play/pause
            CcCard {
                val md = mc?.metadata
                val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.asImageBitmap()
                val playing = if (mc != null) mc.playbackState?.state == PlaybackState.STATE_PLAYING else am.isMusicActive
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(FlowerShape(8, 0.07f)).background(cs.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.MusicNote, null, tint = cs.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title ?: if (playing) "Sedang diputar" else "Tidak ada yang diputar",
                            style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            artist ?: if (listener) "" else "Izinkan akses notifikasi untuk info lagu",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RoundToggle(Icons.Rounded.SkipPrevious, false, "Sebelumnya", 48.dp) {
                        if (mc != null) mc.transportControls.skipToPrevious() else pressMedia(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                        tick++
                    }
                    RoundToggle(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, playing, if (playing) "Jeda" else "Putar", 56.dp) {
                        if (mc != null) {
                            if (playing) mc.transportControls.pause() else mc.transportControls.play()
                        } else pressMedia(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                        tick++
                    }
                    RoundToggle(Icons.Rounded.SkipNext, false, "Berikutnya", 48.dp) {
                        if (mc != null) mc.transportControls.skipToNext() else pressMedia(am, KeyEvent.KEYCODE_MEDIA_NEXT)
                        tick++
                    }
                }
                if (!listener) {
                    FilledTonalButton(onClick = { go(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text("Izinkan info & kontrol media")
                    }
                }
            }

            // Slider kecerahan & volume
            CcCard {
                PillSlider(bright, Icons.Rounded.WbSunny, "Kecerahan") { v ->
                    touched = System.currentTimeMillis()
                    bright = v
                    if (Settings.System.canWrite(ctx)) {
                        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(1, 255))
                    } else needWrite()
                }
                PillSlider(volume, Icons.Rounded.VolumeUp, "Volume") { v ->
                    touched = System.currentTimeMillis()
                    volume = v
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
                }
            }

            // Pintasan
            CcCard {
                TileRow {
                    Tile(Icons.Rounded.FlashlightOn, "Senter", torch, Modifier.weight(1f)) {
                        camId?.let { id -> runCatching { cam.setTorchMode(id, !torch) } }
                    }
                    Tile(Icons.Rounded.ScreenRotation, "Auto-rotasi", autoRotate, Modifier.weight(1f)) {
                        if (Settings.System.canWrite(ctx)) {
                            Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (autoRotate) 0 else 1)
                            tick++
                        } else needWrite()
                    }
                    Tile(Icons.Rounded.DoNotDisturbOn, "Jgn ganggu", dnd, Modifier.weight(1f)) {
                        if (nm.isNotificationPolicyAccessGranted) {
                            nm.setInterruptionFilter(
                                if (dnd) NotificationManager.INTERRUPTION_FILTER_ALL
                                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            )
                            tick++
                        } else go(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                    Tile(
                        when (ringer) {
                            AudioManager.RINGER_MODE_NORMAL -> Icons.Rounded.VolumeUp
                            AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration
                            else -> Icons.Rounded.VolumeOff
                        },
                        when (ringer) {
                            AudioManager.RINGER_MODE_NORMAL -> "Berdering"
                            AudioManager.RINGER_MODE_VIBRATE -> "Getar"
                            else -> "Senyap"
                        },
                        ringer != AudioManager.RINGER_MODE_NORMAL, Modifier.weight(1f)
                    ) {
                        runCatching {
                            if (ringer == AudioManager.RINGER_MODE_NORMAL) {
                                am.setRingerMode(
                                    if (nm.isNotificationPolicyAccessGranted) AudioManager.RINGER_MODE_SILENT
                                    else AudioManager.RINGER_MODE_VIBRATE
                                )
                            } else am.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
                        }
                        tick++
                    }
                }
                TileRow {
                    Tile(Icons.Rounded.LocationOn, "Lokasi", location, Modifier.weight(1f)) {
                        go(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    Tile(Icons.Rounded.DarkMode, "Mode gelap", isDark, Modifier.weight(1f)) {
                        store.update { st -> st.copy(themeMode = if (isDark) 1 else 2) }
                    }
                    Tile(Icons.Rounded.Lock, "Kunci", false, Modifier.weight(1f)) {
                        if (Build.VERSION.SDK_INT >= 28) a11y(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                        else Toast.makeText(ctx, "Perlu Android 9 atau lebih baru", Toast.LENGTH_SHORT).show()
                    }
                    Tile(Icons.Rounded.Notifications, "Notifikasi", false, Modifier.weight(1f)) {
                        a11y(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    }
                }
                TileRow {
                    Tile(Icons.Rounded.Timer, "Timer", false, Modifier.weight(1f)) { go(Intent(AlarmClock.ACTION_SHOW_TIMERS)) }
                    Tile(Icons.Rounded.Calculate, "Kalkulator", false, Modifier.weight(1f)) {
                        go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
                    }
                    Tile(Icons.Rounded.CameraAlt, "Kamera", false, Modifier.weight(1f)) {
                        go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                    }
                    Tile(Icons.Rounded.Tune, "Panel QS", false, Modifier.weight(1f)) {
                        a11y(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    }
                }
            }
        }
    }
}

@Composable
private fun CcCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, content = content)
}

@Composable
private fun Tile(icon: ImageVector, label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RoundToggle(icon, active, label, 56.dp, onClick)
        Text(
            label,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoundToggle(icon: ImageVector, active: Boolean, label: String, size: Dp = 56.dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // Aktif = berubah jadi bunga (morf mulus), ditekan = mengempis.
    val depth by animateFloatAsState(
        if (active) (if (pressed) 0.03f else 0.08f) else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "flower"
    )
    val sc by animateFloatAsState(
        if (pressed) 0.88f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(FlowerShape(8, depth))
            .background(if (active) cs.primary else cs.surfaceContainerHighest)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (active) cs.onPrimary else cs.onSurface)
    }
}

@Composable
private fun PillSlider(value: Float, icon: ImageVector, label: String, onValue: (Float) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var w by remember { mutableFloatStateOf(1f) }
    val cb by rememberUpdatedState(onValue)
    val v by animateFloatAsState(value.coerceIn(0f, 1f), tween(90), label = "slider")
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerHighest)
            .onSizeChanged { w = it.width.toFloat() }
            .pointerInput(Unit) { detectTapGestures { o -> cb((o.x / w).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> cb((change.position.x / w).coerceIn(0f, 1f)) } }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(v)
                .background(cs.primary)
        )
        Icon(
            icon, label,
            Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
            tint = if (v > .12f) cs.onPrimary else cs.onSurface
        )
    }
}
