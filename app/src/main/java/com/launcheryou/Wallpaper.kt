package com.launcheryou

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class WallpaperPreset(val name: String, val colors: List<Color>)

object Wallpapers {
    val presets = listOf(
        WallpaperPreset("Senja", listOf(Color(0xFF3B1E6E), Color(0xFFE8704A), Color(0xFFFFC27A))),
        WallpaperPreset("Samudra", listOf(Color(0xFF021B3A), Color(0xFF1E7FA8), Color(0xFF7FE0D6))),
        WallpaperPreset("Hutan", listOf(Color(0xFF0B2B1E), Color(0xFF2F8F5B), Color(0xFFB7E36A))),
        WallpaperPreset("Mawar", listOf(Color(0xFF3A0F2E), Color(0xFFD1467E), Color(0xFFFFB3C9))),
        WallpaperPreset("Malam", listOf(Color(0xFF05060F), Color(0xFF2B2F77), Color(0xFF8B7CFF))),
        WallpaperPreset("Fajar", listOf(Color(0xFF16254D), Color(0xFF7C6BD6), Color(0xFFFFD2A8)))
    )

    /** Gradien lembut + bunga transparan, dibuat langsung di perangkat (tanpa file gambar). */
    fun render(c: List<Color>, w: Int = 1080, h: Int = 2340): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()
        p.shader = LinearGradient(0f, 0f, wf, hf, c[0].toArgb(), c[1].toArgb(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, wf, hf, p)
        p.shader = RadialGradient(
            wf * 0.85f, hf * 0.22f, wf * 0.95f,
            intArrayOf(c[2].copy(alpha = .60f).toArgb(), 0x00000000), null, Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, wf, hf, p)
        p.shader = RadialGradient(
            wf * 0.05f, hf * 0.88f, wf * 1.0f,
            intArrayOf(c[2].copy(alpha = .35f).toArgb(), 0x00000000), null, Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, wf, hf, p)

        p.shader = null
        p.style = Paint.Style.FILL
        p.color = Color.White.copy(alpha = .10f).toArgb()
        val fp = Path()
        val cx = wf * 0.78f
        val cy = hf * 0.24f
        val r = wf * 0.42f
        for (i in 0..180) {
            val a = (2.0 * PI * i / 180).toFloat()
            val rad = r * (0.92f + 0.08f * cos(8 * a))
            val x = cx + rad * cos(a)
            val y = cy + rad * sin(a)
            if (i == 0) fp.moveTo(x, y) else fp.lineTo(x, y)
        }
        fp.close()
        cv.drawPath(fp, p)
        return bmp
    }

    fun decode(ctx: Context, uri: Uri, maxDim: Int = 2400): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    /** target: 0 = beranda, 1 = layar kunci, 2 = keduanya. */
    fun apply(ctx: Context, bmp: Bitmap, target: Int, done: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        thread {
            val flags = when (target) {
                0 -> WallpaperManager.FLAG_SYSTEM
                1 -> WallpaperManager.FLAG_LOCK
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            val ok = runCatching { WallpaperManager.getInstance(app).setBitmap(bmp, null, true, flags) }.isSuccess
            Handler(Looper.getMainLooper()).post { done(ok) }
        }
    }

    /** Rona dominan (0..359) dari sebuah gambar, untuk dijadikan warna tema. */
    fun dominantHue(bmp: Bitmap): Int {
        val sm = Bitmap.createScaledBitmap(bmp, 32, 32, true)
        var x = 0.0
        var y = 0.0
        val hsv = FloatArray(3)
        for (i in 0 until 32) for (j in 0 until 32) {
            android.graphics.Color.colorToHSV(sm.getPixel(i, j), hsv)
            val wgt = (hsv[1] * hsv[2]).toDouble()
            val a = Math.toRadians(hsv[0].toDouble())
            x += cos(a) * wgt
            y += sin(a) * wgt
        }
        val deg = Math.toDegrees(atan2(y, x))
        return ((deg + 360.0) % 360.0).toInt()
    }
}

@Composable
fun WallpaperPicker(store: SettingsStore) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf<Bitmap?>(null) }
    var target by remember { mutableIntStateOf(2) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            thread {
                selected = Wallpapers.decode(ctx, uri)
                busy = false
            }
        }
    }

    Text("Wallpaper siap pakai", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Wallpapers.presets.forEachIndexed { i, p ->
            val thumb = remember(i) { Wallpapers.render(p.colors, 180, 390).asImageBitmap() }
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        busy = true
                        thread {
                            selected = Wallpapers.render(p.colors)
                            busy = false
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    thumb, p.name,
                    Modifier.size(width = 72.dp, height = 156.dp).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
                Text(p.name, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
    FilledTonalButton(onClick = { picker.launch("image/*") }) { Text("Pilih dari galeri") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

    selected?.let { bmp ->
        val img = remember(bmp) { bmp.asImageBitmap() }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(
                img, null,
                Modifier.size(width = 110.dp, height = 238.dp).clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChipRow(listOf("Beranda" to 0, "Kunci" to 1, "Keduanya" to 2), target) { v -> target = v }
                Button(onClick = {
                    Wallpapers.apply(ctx, bmp, target) { ok ->
                        Toast.makeText(ctx, if (ok) "Wallpaper diterapkan" else "Gagal menerapkan wallpaper", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Terapkan") }
                FilledTonalButton(onClick = {
                    val hue = Wallpapers.dominantHue(bmp)
                    store.update { st -> st.copy(dynamicColor = false, seedHue = hue) }
                }) { Text("Ambil warna") }
            }
        }
    }
    Text(
        "Dengan warna dinamis aktif (Android 12+), warna tema otomatis mengikuti wallpaper baru.",
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
    )
}
