package com.launcheryou

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

class IconPackInfo(val id: String, val label: String)

private class Filter {
    val comp = HashMap<String, String>()
    val pkg = HashMap<String, String>()
    val backs = ArrayList<String>()
    var mask: String? = null
    var upon: String? = null
    var scale = 1f
}

private class LoadedPack(
    val byComponent: Map<String, String>,
    val byPackage: Map<String, String>,
    val backs: List<String>,
    val mask: String?,
    val upon: String?,
    val scale: Float,
    val bitmap: (String) -> Bitmap?
)

/**
 * Paket ikon: bawaan (Sistem, Material You, Monokrom, Pastel), aplikasi paket ikon yang terpasang
 * (format ADW/Nova/Apex/GO, appfilter.xml), dan paket dari file ZIP.
 */
object IconPacks {
    val builtins = listOf(
        IconPackInfo("system", "Sistem"),
        IconPackInfo("materialyou", "Material You"),
        IconPackInfo("mono", "Monokrom"),
        IconPackInfo("pastel", "Pastel")
    )

    /** Naik setiap kali ikon paket selesai dimuat, supaya tampilan ikut diperbarui. */
    var version by mutableIntStateOf(0)

    private val actions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme"
    )
    private val packs = ConcurrentHashMap<String, LoadedPack>()
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    fun installed(ctx: Context): List<IconPackInfo> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, IconPackInfo>()
        for (a in actions) {
            runCatching {
                pm.queryIntentActivities(Intent(a), 0).forEach { ri ->
                    val p = ri.activityInfo.packageName
                    if (p != ctx.packageName && p !in seen) seen[p] = IconPackInfo("pkg:$p", ri.loadLabel(pm).toString())
                }
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    private fun root(ctx: Context) = File(ctx.filesDir, "iconpacks")

    fun imported(ctx: Context): List<IconPackInfo> =
        (root(ctx).listFiles() ?: emptyArray())
            .filter { it.isDirectory && File(it, "appfilter.xml").exists() }
            .map { IconPackInfo("zip:${it.name}", it.name) }
            .sortedBy { it.label.lowercase() }

    /** Ekstrak ZIP (appfilter.xml + PNG) ke penyimpanan aplikasi. Mengembalikan id paket, atau null jika tidak valid. */
    fun importZip(ctx: Context, uri: Uri): String? = runCatching {
        var name = "paket"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0).substringBeforeLast('.')
        }
        name = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "paket" }
        val dir = File(root(ctx), name)
        dir.deleteRecursively()
        dir.mkdirs()
        var count = 0
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { zin ->
                var e = zin.nextEntry
                while (e != null && count < 6000) {
                    val fn = e.name.substringAfterLast('/')
                    val ok = !e.isDirectory && fn.isNotEmpty() && fn.length < 120 && !fn.contains("\\") &&
                        (fn.endsWith(".png", true) || fn.endsWith(".webp", true) || fn.equals("appfilter.xml", true))
                    if (ok) {
                        File(dir, if (fn.equals("appfilter.xml", true)) "appfilter.xml" else fn).outputStream().use { zin.copyTo(it) }
                        count++
                    }
                    e = zin.nextEntry
                }
            }
        }
        if (File(dir, "appfilter.xml").exists()) "zip:$name" else { dir.deleteRecursively(); null }
    }.getOrNull()

    fun delete(ctx: Context, id: String) {
        if (id.startsWith("zip:")) File(root(ctx), id.removePrefix("zip:")).deleteRecursively()
        invalidate(id)
    }

    fun invalidate(id: String) {
        packs.remove(id)
        failed.removeIf { it.startsWith("$id|") }
        cache.keys.filter { it.startsWith("$id|") }.forEach { cache.remove(it) }
    }

    fun cached(id: String, appKey: String): ImageBitmap? = cache["$id|$appKey"]

    /** Muat paket dan siapkan ikon semua aplikasi (panggil dari thread latar). */
    fun prepare(ctx: Context, apps: List<AppInfo>, id: String) {
        if (!id.startsWith("pkg:") && !id.startsWith("zip:")) return
        val pack = packs[id] ?: (
            if (id.startsWith("pkg:")) loadPkg(ctx, id.removePrefix("pkg:")) else loadZip(ctx, id.removePrefix("zip:"))
        )?.also { packs[id] = it } ?: return
        for (app in apps) {
            val k = "$id|${app.key}"
            if (cache.containsKey(k) || failed.contains(k)) continue
            val img = runCatching { render(pack, app) }.getOrNull()
            if (img != null) cache[k] = img else failed.add(k)
        }
    }

    /** Filter warna untuk paket bawaan. */
    fun filterFor(id: String, tint: Color): ColorFilter? = when (id) {
        "mono" -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        "pastel" -> ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    .62f, 0f, 0f, 0f, 97f,
                    0f, .62f, 0f, 0f, 97f,
                    0f, 0f, .62f, 0f, 97f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        "materialyou" -> duotone(tint)
        else -> null
    }

    /** Ubah ikon apa pun jadi dua-nada dari warna tema (dipakai untuk ikon tanpa lapisan monokrom). */
    private fun duotone(t: Color): ColorFilter {
        val k0 = 0.35f
        val k1 = 0.65f
        fun row(c: Float) = floatArrayOf(c * k1 * 0.299f, c * k1 * 0.587f, c * k1 * 0.114f, 0f, c * k0 * 255f)
        return ColorFilter.colorMatrix(
            ColorMatrix(row(t.red) + row(t.green) + row(t.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
        )
    }

    private fun render(pack: LoadedPack, app: AppInfo): ImageBitmap? {
        val name = pack.byComponent[app.key] ?: pack.byPackage[app.pkg]
        if (name != null) {
            val b = pack.bitmap(name)
            if (b != null) return b.asImageBitmap()
        }
        // Ikon tanpa tema: gabungkan dengan iconback / iconmask / iconupon bila paket menyediakannya.
        if (pack.backs.isEmpty() && pack.mask == null && pack.upon == null) return null
        val size = 192
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val full = Rect(0, 0, size, size)
        val inset = (size * (1f - pack.scale.coerceIn(0.3f, 1f)) / 2f).toInt()
        c.drawBitmap(app.plain.asAndroidBitmap(), null, Rect(inset, inset, size - inset, size - inset), p)
        pack.mask?.let { n -> pack.bitmap(n) }?.let { m ->
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            c.drawBitmap(m, null, full, p)
            p.xfermode = null
        }
        if (pack.backs.isNotEmpty()) {
            val n = pack.backs[(app.pkg.hashCode() and 0x7fffffff) % pack.backs.size]
            pack.bitmap(n)?.let { b ->
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
                c.drawBitmap(b, null, full, p)
                p.xfermode = null
            }
        }
        pack.upon?.let { n -> pack.bitmap(n) }?.let { u -> c.drawBitmap(u, null, full, p) }
        return out.asImageBitmap()
    }

    private fun firstImg(p: XmlPullParser): String? {
        for (i in 0 until p.attributeCount) if (p.getAttributeName(i).startsWith("img")) return p.getAttributeValue(i)
        return null
    }

    private fun parse(p: XmlPullParser, f: Filter) {
        val rx = Regex("ComponentInfo\\{([^/}]+)/([^}]+)\\}")
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item" -> {
                        val c = p.getAttributeValue(null, "component")
                        val d = p.getAttributeValue(null, "drawable")
                        val m = if (c != null && d != null) rx.find(c) else null
                        if (m != null && d != null) {
                            val pk = m.groupValues[1]
                            var cl = m.groupValues[2]
                            if (cl.startsWith(".")) cl = pk + cl
                            f.comp["$pk/$cl"] = d
                            if (!f.pkg.containsKey(pk)) f.pkg[pk] = d
                        }
                    }
                    "iconback" -> for (i in 0 until p.attributeCount) {
                        if (p.getAttributeName(i).startsWith("img")) f.backs.add(p.getAttributeValue(i))
                    }
                    "iconmask" -> f.mask = firstImg(p)
                    "iconupon" -> f.upon = firstImg(p)
                    "scale" -> f.scale = p.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                }
            }
            ev = p.next()
        }
    }

    private fun loadPkg(ctx: Context, pkg: String): LoadedPack? = runCatching {
        val res = ctx.packageManager.getResourcesForApplication(pkg)
        val f = Filter()
        val xmlId = res.getIdentifier("appfilter", "xml", pkg)
        if (xmlId != 0) {
            parse(res.getXml(xmlId), f)
        } else {
            res.assets.open("appfilter.xml").use { s: InputStream ->
                val p = Xml.newPullParser()
                p.setInput(s, "UTF-8")
                parse(p, f)
            }
        }
        LoadedPack(f.comp, f.pkg, f.backs, f.mask, f.upon, f.scale) { name ->
            var id = res.getIdentifier(name, "drawable", pkg)
            if (id == 0) id = res.getIdentifier(name, "mipmap", pkg)
            if (id == 0) null
            else runCatching { ResourcesCompat.getDrawable(res, id, null)?.toBitmap(192, 192) }.getOrNull()
        }
    }.getOrNull()

    private fun loadZip(ctx: Context, name: String): LoadedPack? = runCatching {
        val dir = File(root(ctx), name)
        val f = Filter()
        File(dir, "appfilter.xml").inputStream().use { s ->
            val p = Xml.newPullParser()
            p.setInput(s, "UTF-8")
            parse(p, f)
        }
        LoadedPack(f.comp, f.pkg, f.backs, f.mask, f.upon, f.scale) { n ->
            val file = listOf("png", "webp").map { File(dir, "$n.$it") }.firstOrNull { it.exists() }
            if (file == null) null else BitmapFactory.decodeFile(file.path)
        }
    }.getOrNull()
}
