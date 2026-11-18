package de.datacreate.radiouhr

import android.content.Context
import android.content.SharedPreferences
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/** Podcasts: Suche (Apple-Podcast-Verzeichnis), Abos, RSS-Feeds, Hörfortschritt. */
object Podcast {
    class Pod(val feed: String, val title: String, val author: String, val art: String)
    class Ep(val id: String, val title: String, val date: Long, val url: String, val durationSec: Long, val art: String)

    // ------------------------------------------------------------ Abos

    fun subs(p: SharedPreferences): MutableList<Pod> = try {
        val a = JSONArray(p.getString("podSubs", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Pod(o.optString("feed"), o.optString("title"), o.optString("author"), o.optString("art")) } }
            .toMutableList()
    } catch (_: Exception) { mutableListOf() }

    fun saveSubs(p: SharedPreferences, list: List<Pod>) {
        val a = JSONArray()
        for (x in list) a.put(JSONObject().put("feed", x.feed).put("title", x.title).put("author", x.author).put("art", x.art))
        p.edit().putString("podSubs", a.toString()).apply()
    }

    fun isSub(p: SharedPreferences, feed: String) = subs(p).any { it.feed == feed }

    fun toggleSub(p: SharedPreferences, pod: Pod): Boolean {
        val l = subs(p)
        val i = l.indexOfFirst { it.feed == pod.feed }
        return if (i >= 0) {
            l.removeAt(i); saveSubs(p, l); false
        } else {
            l.add(pod); saveSubs(p, l); true
        }
    }

    // ------------------------------------------------------------ Fortschritt

    private fun key(ep: Ep) = Integer.toHexString(ep.url.hashCode())
    fun pos(p: SharedPreferences, ep: Ep): Long = p.getLong("pp_" + key(ep), 0L)
    fun setPos(p: SharedPreferences, ep: Ep, ms: Long) { p.edit().putLong("pp_" + key(ep), ms).apply() }
    fun done(p: SharedPreferences, ep: Ep): Boolean = p.getBoolean("pd_" + key(ep), false)
    fun setDone(p: SharedPreferences, ep: Ep, v: Boolean) { p.edit().putBoolean("pd_" + key(ep), v).putLong("pp_" + key(ep), 0L).apply() }

    fun saveLast(p: SharedPreferences, pod: Pod, ep: Ep) {
        p.edit().putString("pcLast", JSONObject()
            .put("feed", pod.feed).put("ptitle", pod.title).put("author", pod.author).put("part", pod.art)
            .put("id", ep.id).put("title", ep.title).put("date", ep.date).put("url", ep.url).put("dur", ep.durationSec).put("art", ep.art)
            .toString()).apply()
    }

    fun last(p: SharedPreferences): Pair<Pod, Ep>? = try {
        val o = JSONObject(p.getString("pcLast", null) ?: return null)
        Pod(o.optString("feed"), o.optString("ptitle"), o.optString("author"), o.optString("part")) to
            Ep(o.optString("id"), o.optString("title"), o.optLong("date"), o.optString("url"), o.optLong("dur"), o.optString("art"))
    } catch (_: Exception) { null }

    // ------------------------------------------------------------ Suche

    fun search(term: String): List<Pod> {
        val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&country=DE&limit=40&term=" + URLEncoder.encode(term, "UTF-8")
        val a = JSONObject(Net.get(url)).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Pod>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val feed = o.optString("feedUrl")
            if (feed.isEmpty()) continue
            out.add(Pod(feed, o.optString("collectionName"), o.optString("artistName"),
                o.optString("artworkUrl600").ifEmpty { o.optString("artworkUrl100") }))
        }
        return out
    }

    // ------------------------------------------------------------ Feed

    private fun cache(ctx: Context, feed: String) = File(ctx.cacheDir, "pod_" + Integer.toHexString(feed.hashCode()))

    /** Feed laden (Zwischenspeicher bis 1 Stunde, mit force immer neu). */
    fun feed(ctx: Context, feed: String, force: Boolean = false): Pair<Pod, List<Ep>>? {
        val f = cache(ctx, feed)
        val text = if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < 3600_000L) f.readText() else {
            try {
                val t = Net.get(feed)
                f.writeText(t)
                t
            } catch (e: Exception) {
                if (f.exists()) f.readText() else throw e
            }
        }
        return parse(feed, text)
    }

    fun cachedFeed(ctx: Context, feed: String): Pair<Pod, List<Ep>>? {
        val f = cache(ctx, feed)
        return if (f.exists()) try { parse(feed, f.readText()) } catch (_: Exception) { null } else null
    }

    private val DATE_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss zzz", "dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z"
    )

    private fun date(s: String): Long {
        val t = s.trim()
        for (fmt in DATE_FORMATS) {
            try {
                return SimpleDateFormat(fmt, Locale.US).parse(t)!!.time
            } catch (_: Exception) { }
        }
        return 0L
    }

    private fun dur(s: String): Long {
        val t = s.trim()
        if (t.isEmpty()) return 0
        val parts = t.split(':').mapNotNull { it.trim().toDoubleOrNull() }
        return when (parts.size) {
            1 -> parts[0].toLong()
            2 -> (parts[0] * 60 + parts[1]).toLong()
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toLong()
            else -> 0
        }
    }

    fun parse(feedUrl: String, text: String): Pair<Pod, List<Ep>>? {
        val xp = Xml.newPullParser()
        xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        xp.setInput(StringReader(text))
        var title = ""
        var author = ""
        var art = ""
        val eps = ArrayList<Ep>()
        var inItem = false
        var inImage = false
        var iTitle = ""; var iGuid = ""; var iDate = 0L; var iUrl = ""; var iDur = 0L; var iArt = ""
        var ev = xp.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (xp.name) {
                    "item" -> {
                        inItem = true
                        iTitle = ""; iGuid = ""; iDate = 0L; iUrl = ""; iDur = 0L; iArt = ""
                    }
                    "image" -> if (!inItem) inImage = true
                    "title" -> {
                        val t = safeText(xp)
                        if (inItem) iTitle = t else if (!inImage && title.isEmpty()) title = t
                    }
                    "itunes:author" -> { val t = safeText(xp); if (!inItem && author.isEmpty()) author = t }
                    "url" -> { val t = safeText(xp); if (inImage && art.isEmpty()) art = t }
                    "itunes:image" -> {
                        val h = xp.getAttributeValue(null, "href") ?: ""
                        if (inItem) iArt = h else if (h.isNotEmpty()) art = h
                    }
                    "guid" -> if (inItem) iGuid = safeText(xp)
                    "pubDate" -> if (inItem) iDate = date(safeText(xp))
                    "itunes:duration" -> if (inItem) iDur = dur(safeText(xp))
                    "enclosure" -> if (inItem && iUrl.isEmpty()) iUrl = xp.getAttributeValue(null, "url") ?: ""
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (xp.name) {
                    "image" -> inImage = false
                    "item" -> {
                        inItem = false
                        if (iUrl.isNotEmpty() && eps.size < 200) eps.add(Ep(iGuid.ifEmpty { iUrl }, iTitle.ifEmpty { "Folge" }, iDate, iUrl, iDur, iArt))
                    }
                }
            }
            ev = try { xp.next() } catch (_: Exception) { break }
        }
        if (eps.isEmpty() && title.isEmpty()) return null
        return Pod(feedUrl, title.ifEmpty { "Podcast" }, author, art) to eps.sortedByDescending { it.date }
    }

    private fun safeText(xp: XmlPullParser): String = try { xp.nextText().trim() } catch (_: Exception) { "" }

    fun fmtTime(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60) else String.format("%d:%02d", s / 60, s % 60)
    }
}
