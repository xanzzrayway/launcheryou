package com.launcheryou

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private fun pressMedia(am: AudioManager, code: Int) {
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
}

@Composable
fun ControlCenter(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val cs = MaterialTheme.colorScheme
    var tick by remember { mutableIntStateOf(0) }

    val am = remember { app.getSystemService(AudioManager::class.java) }
    val nm = remember { app.getSystemService(NotificationManager::class.java) }
    val cam = remember { app.getSystemService(CameraManager::class.java) }

    // Status jaringan hanya dibaca; Android modern melarang aplikasi biasa menyalakan/mematikannya langsung.
    val wifi = remember(tick) { runCatching { app.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false) }
    val bt = remember(tick) { runCatching { app.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false) }
    val airplane = remember(tick) { Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }
    val cellular = remember(tick) {
        runCatching {
            val c = app.getSystemService(ConnectivityManager::class.java)
            c.getNetworkCapabilities(c.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }.getOrDefault(false)
    }
    val playing = remember(tick) { am.isMusicActive }

    var dnd by remember { mutableStateOf(nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) }
    var autoRotate by remember { mutableStateOf(Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1) }
    var bright by remember {
        mutableFloatStateOf(Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    }
    var volume by remember {
        mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
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
                .padding(16.dp)
                .pointerInput(Unit) { detectTapGestures { } },   // tap di sela panel tidak menutup
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Baris 1: konektivitas + media
            Row(Modifier.fillMaxWidth().height(172.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            RoundToggle(Icons.Rounded.AirplanemodeActive, airplane, "Mode pesawat") {
                                go(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                            }
                            RoundToggle(Icons.Rounded.SignalCellularAlt, cellular && !airplane, "Data seluler") {
                                go(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            RoundToggle(Icons.Rounded.Wifi, wifi, "Wi-Fi") {
                                go(
                                    if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                                    else Intent(Settings.ACTION_WIFI_SETTINGS)
                                )
                            }
                            RoundToggle(Icons.Rounded.Bluetooth, bt, "Bluetooth") {
                                go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }
                        }
                    }
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.MusicNote, null, tint = cs.primary)
                            Text("  Media", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                        }
                        Text(
                            if (playing) "Sedang diputar" else "Tidak ada yang diputar",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            RoundToggle(Icons.Rounded.SkipPrevious, false, "Sebelumnya", 40.dp) {
                                pressMedia(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS); tick++
                            }
                            RoundToggle(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, playing, if (playing) "Jeda" else "Putar", 44.dp) {
                                pressMedia(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                tick++
                            }
                            RoundToggle(Icons.Rounded.SkipNext, false, "Berikutnya", 40.dp) {
                                pressMedia(am, KeyEvent.KEYCODE_MEDIA_NEXT); tick++
                            }
                        }
                    }
                }
            }

            // Baris 2: rotasi + jangan ganggu, slider kecerahan & volume
            Row(Modifier.fillMaxWidth().height(172.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Panel(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            RoundToggle(Icons.Rounded.ScreenRotation, !autoRotate, "Kunci rotasi") {
                                if (Settings.System.canWrite(ctx)) {
                                    Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (autoRotate) 0 else 1)
                                    autoRotate = !autoRotate
                                } else needWrite()
                            }
                        }
                    }
                    Panel(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            RoundToggle(Icons.Rounded.DoNotDisturbOn, dnd, "Jangan ganggu") {
                                if (nm.isNotificationPolicyAccessGranted) {
                                    nm.setInterruptionFilter(
                                        if (dnd) NotificationManager.INTERRUPTION_FILTER_ALL
                                        else NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                    )
                                    dnd = !dnd
                                } else go(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            }
                        }
                    }
                }
                VerticalSlider(bright, Icons.Rounded.WbSunny, "Kecerahan", Modifier.width(78.dp).fillMaxHeight()) { v ->
                    bright = v
                    if (Settings.System.canWrite(ctx)) {
                        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(1, 255))
                    } else needWrite()
                }
                VerticalSlider(volume, Icons.Rounded.VolumeUp, "Volume", Modifier.width(78.dp).fillMaxHeight()) { v ->
                    volume = v
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
                }
            }

            // Baris 3: pintasan
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RoundToggle(Icons.Rounded.FlashlightOn, torch, "Senter", 52.dp) {
                            camId?.let { id -> runCatching { cam.setTorchMode(id, !torch) } }
                        }
                    }
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RoundToggle(Icons.Rounded.Timer, false, "Timer", 52.dp) { go(Intent(AlarmClock.ACTION_SHOW_TIMERS)) }
                    }
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RoundToggle(Icons.Rounded.Calculate, false, "Kalkulator", 52.dp) {
                            go(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
                        }
                    }
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RoundToggle(Icons.Rounded.CameraAlt, false, "Kamera", 52.dp) {
                            go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f)
    ) { Box(Modifier.fillMaxSize()) { content() } }
}

@Composable
private fun RoundToggle(icon: ImageVector, active: Boolean, label: String, size: Dp = 56.dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) cs.primary else cs.surfaceContainerHighest)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (active) cs.onPrimary else cs.onSurface)
    }
}

@Composable
private fun VerticalSlider(
    value: Float, icon: ImageVector, label: String, modifier: Modifier, onValue: (Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var h by remember { mutableFloatStateOf(1f) }
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHighest)
            .onSizeChanged { h = it.height.toFloat() }
            .pointerInput(Unit) { detectTapGestures { o -> onValue((1f - o.y / h).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> onValue((1f - change.position.y / h).coerceIn(0f, 1f)) } }
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0f, 1f))
                .background(cs.primary)
        )
        Icon(
            icon, label,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            tint = if (value > .12f) cs.onPrimary else cs.onSurface
        )
    }
}
