package de.datacreate.radiouhr

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

/** Welche Seiten (Kacheln) es gibt und welche eingeschaltet sind. */
object Pages {
    val ALL = listOf(
        "uhr" to "Uhr",
        "radio" to "Radio",
        "podcast" to "Podcast",
        "medien" to "Mediathek",
        "wetter" to "Wetter"
    )

    fun name(id: String) = ALL.firstOrNull { it.first == id }?.second ?: id

    fun active(p: SharedPreferences): List<String> =
        ALL.map { it.first }.filter { it == "uhr" || p.getBoolean("page_$it", true) }
}

/** Mediathek: Ordner auf dem Gerät (per Android-Ordnerauswahl freigegeben) durchsuchen. */
object Media {
    class Item(val uri: Uri, val docId: String, val name: String, val dir: Boolean, val mime: String, val size: Long) {
        val kind: String get() = when {
            dir -> "dir"
            mime.startsWith("audio/") || ext in AUDIO -> "audio"
            mime.startsWith("video/") || ext in VIDEO -> "video"
            mime.startsWith("image/") || ext in IMAGE -> "image"
            else -> "doc"
        }
        val ext: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val title: String get() = if (dir) name else name.substringBeforeLast('.')
    }

    private val AUDIO = setOf("mp3", "m4a", "m4b", "aac", "ogg", "opus", "flac", "wav", "wma")
    private val VIDEO = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
    private val IMAGE = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val COVERS = listOf("cover", "folder", "front", "album", "albumart")

    /** Lesbarer Name des gewählten Ordners, z. B. „Kinder“ oder „SD-Karte: Hörspiele“. */
    fun label(tree: String): String = try {
        val id = DocumentsContract.getTreeDocumentId(Uri.parse(tree))
        val vol = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        val where = if (vol == "primary") "Gerät" else "SD-Karte"
        if (path.isEmpty()) where else "$where: $path"
    } catch (_: Exception) { tree }

    fun rootId(tree: String): String = DocumentsContract.getTreeDocumentId(Uri.parse(tree))

    /** Inhalt eines Ordners: zuerst Unterordner, dann Dateien – jeweils natürlich sortiert (2 vor 10). */
    fun list(ctx: Context, tree: String, docId: String): List<Item> {
        val treeUri = Uri.parse(tree)
        val q = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val out = ArrayList<Item>()
        ctx.contentResolver.query(q, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        ), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val mime = c.getString(2) ?: ""
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                val dir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                out.add(Item(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), id, name, dir, mime, size))
            }
        }
        return out.sortedWith(compareBy<Item>({ !it.dir }, { natKey(it.name) }))
    }

    /** Titelbild eines Ordners: cover.jpg/folder.jpg …, sonst das erste Bild. */
    fun cover(items: List<Item>): Item? {
        val imgs = items.filter { it.kind == "image" }
        return imgs.firstOrNull { it.name.substringBeforeLast('.').lowercase(Locale.ROOT) in COVERS } ?: imgs.firstOrNull()
    }

    private fun natKey(s: String): String =
        Regex("\\d+").replace(s.lowercase(Locale.GERMANY)) { it.value.padStart(8, '0') }

    /** Spieldauer in ms (zwischengespeichert, 0 = unbekannt). Nicht im UI-Thread aufrufen. */
    fun duration(ctx: Context, it: Item): Long {
        val sp = ctx.getSharedPreferences("mediaDur", Context.MODE_PRIVATE)
        val k = Integer.toHexString((it.docId + "|" + it.size).hashCode())
        val c = sp.getLong(k, -1L)
        if (c >= 0) return c
        val r = android.media.MediaMetadataRetriever()
        val ms = try {
            r.setDataSource(ctx, it.uri)
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) { 0L } finally { try { r.release() } catch (_: Throwable) { } }
        sp.edit().putLong(k, ms).apply()
        return ms
    }

    fun sizeLabel(b: Long): String = when {
        b <= 0 -> ""
        b < 1048576 -> String.format(Locale.GERMANY, "%.0f KB", b / 1024.0)
        b < 1073741824 -> String.format(Locale.GERMANY, "%.0f MB", b / 1048576.0)
        else -> String.format(Locale.GERMANY, "%.1f GB", b / 1073741824.0)
    }
}
