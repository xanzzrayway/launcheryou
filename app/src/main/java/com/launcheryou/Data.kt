package com.launcheryou

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Semua pengaturan tampilan yang dibagi antara LauncherYou dan SetelanYou. */
data class UiSettings(
    val dynamicColor: Boolean = true,
    val seedHue: Int = 255,
    val themeMode: Int = 0,        // 0 = ikuti sistem, 1 = terang, 2 = gelap
    val iconShape: Int = 0,        // 0 = lingkaran, 1 = squircle, 2 = kotak bulat
    val themedIcons: Boolean = true,
    val columns: Int = 4,
    val showLabels: Boolean = true,
    val iconScale: Float = 1f,
    val controlCenter: Boolean = true,
    val pinned: List<String> = emptyList(),
    val setupDone: Boolean = false,
    val amoled: Boolean = false,
    val scrim: Float = 0.15f,
    val clock24: Boolean = true,
    val showDock: Boolean = true
)

class SettingsStore(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("launcheryou", Context.MODE_PRIVATE)

    var settings by mutableStateOf(read())
        private set

    // Disimpan sebagai field agar tidak di-garbage-collect (SharedPreferences hanya menyimpan weak reference).
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> settings = read() }

    init { sp.registerOnSharedPreferenceChangeListener(listener) }

    private fun read() = UiSettings(
        dynamicColor = sp.getBoolean("dynamic", true),
        seedHue = sp.getInt("hue", 255),
        themeMode = sp.getInt("mode", 0),
        iconShape = sp.getInt("shape", 0),
        themedIcons = sp.getBoolean("themed", true),
        columns = sp.getInt("columns", 4),
        showLabels = sp.getBoolean("labels", true),
        iconScale = sp.getFloat("scale", 1f),
        controlCenter = sp.getBoolean("cc", true),
        setupDone = sp.getBoolean("setup", false),
        amoled = sp.getBoolean("amoled", false),
        scrim = sp.getFloat("scrim", 0.15f),
        clock24 = sp.getBoolean("clock24", true),
        showDock = sp.getBoolean("dock", true),
        pinned = (sp.getString("pinned", "") ?: "").split(",").filter { it.isNotBlank() }
    )

    fun update(block: (UiSettings) -> UiSettings) {
        val n = block(settings)
        sp.edit()
            .putBoolean("dynamic", n.dynamicColor)
            .putInt("hue", n.seedHue)
            .putInt("mode", n.themeMode)
            .putInt("shape", n.iconShape)
            .putBoolean("themed", n.themedIcons)
            .putInt("columns", n.columns)
            .putBoolean("labels", n.showLabels)
            .putFloat("scale", n.iconScale)
            .putBoolean("cc", n.controlCenter)
            .putBoolean("setup", n.setupDone)
            .putBoolean("amoled", n.amoled)
            .putFloat("scrim", n.scrim)
            .putBoolean("clock24", n.clock24)
            .putBoolean("dock", n.showDock)
            .putString("pinned", n.pinned.joinToString(","))
            .apply()
        settings = n
    }
}

class AppInfo(
    val label: String,
    val pkg: String,
    val cls: String,
    private val icon: Drawable
) {
    val key: String get() = "$pkg/$cls"

    private fun layer(d: Drawable?): ImageBitmap? = d?.toBitmap(216, 216)?.asImageBitmap()

    val plain: ImageBitmap by lazy { icon.toBitmap(192, 192).asImageBitmap() }
    val bg: ImageBitmap? by lazy { (icon as? AdaptiveIconDrawable)?.let { layer(it.background) } }
    val fg: ImageBitmap? by lazy { (icon as? AdaptiveIconDrawable)?.let { layer(it.foreground) } }
    val mono: ImageBitmap? by lazy {
        if (Build.VERSION.SDK_INT >= 33) (icon as? AdaptiveIconDrawable)?.let { layer(it.monochrome) } else null
    }
}

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name, it.loadIcon(pm)) }
        .sortedBy { it.label.lowercase() }
}

fun launchApp(ctx: Context, app: AppInfo) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.pkg, app.cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }
}

/** Dock: telepon, pesan, peramban, kamera bawaan perangkat. */
fun dockApps(ctx: Context, all: List<AppInfo>): List<AppInfo> {
    val pm = ctx.packageManager
    fun pick(i: Intent): AppInfo? {
        val ri = pm.resolveActivity(i, 0) ?: return null
        return all.firstOrNull { it.pkg == ri.activityInfo.packageName }
    }
    return listOfNotNull(
        pick(Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))),
        pick(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)),
        pick(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)),
        pick(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    ).distinctBy { it.pkg }
}
