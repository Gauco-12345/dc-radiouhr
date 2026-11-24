package de.datacreate.radiouhr

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/** Radio-Kategorie. tags = Suchbegriffe für die Top-Sender-Vorschläge (radio-browser.info). */
class RadioCat(val id: String, var name: String, val tags: String)

/**
 * Kategorien für Radiosender. „fav“ = Favoriten (die bisherige Senderliste, auf der Startseite sichtbar).
 * Ein Sender darf in mehreren Kategorien stehen.
 */
object RadioCats {
    const val FAV = "fav"

    private val DEFAULTS = listOf(
        RadioCat(FAV, "★ Favoriten", ""),
        RadioCat("news", "Nachrichten", "news,nachrichten,information"),
        RadioCat("pop", "Pop", "pop,top 40,charts"),
        RadioCat("rock", "Rock", "rock,classic rock"),
        RadioCat("kids", "Kinder", "kinder,kids,children"),
        RadioCat("schlager", "Schlager", "schlager,volksmusik"),
        RadioCat("oldies", "Oldies", "oldies,80s,70s"),
        RadioCat("klassik", "Klassik", "classical,klassik"),
        RadioCat("jazz", "Jazz", "jazz,blues"),
        RadioCat("dance", "Elektro & Dance", "electronic,dance,house"),
        RadioCat("kultur", "Kultur & Wort", "culture,kultur,talk")
    )

    fun cats(p: SharedPreferences): MutableList<RadioCat> {
        val raw = p.getString("radioCats", null) ?: return DEFAULTS.map { RadioCat(it.id, it.name, it.tags) }.toMutableList()
        return try {
            val a = JSONArray(raw)
            val out = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                RadioCat(o.getString("id"), o.getString("name"), o.optString("tags"))
            }.toMutableList()
            if (out.none { it.id == FAV }) out.add(0, RadioCat(FAV, "★ Favoriten", ""))
            out
        } catch (_: Exception) { DEFAULTS.toMutableList() }
    }

    fun saveCats(p: SharedPreferences, l: List<RadioCat>) {
        val a = JSONArray()
        for (c in l) a.put(JSONObject().put("id", c.id).put("name", c.name).put("tags", c.tags))
        p.edit().putString("radioCats", a.toString()).apply()
    }

    private fun all(p: SharedPreferences): JSONObject = try { JSONObject(p.getString("catStations", "{}")) } catch (_: Exception) { JSONObject() }

    /** Eigene Sender einer Kategorie (ohne Favoriten – die liegen in „stations“). */
    fun stations(p: SharedPreferences, cat: String): MutableList<Station> {
        val a = all(p).optJSONArray(cat) ?: return mutableListOf()
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Station(o.getString("name"), o.getString("url"), o.optString("icon"))
        }.toMutableList()
    }

    fun saveStations(p: SharedPreferences, cat: String, l: List<Station>) {
        val o = all(p)
        val a = JSONArray()
        for (s in l) a.put(JSONObject().put("name", s.name).put("url", s.url).put("icon", s.icon))
        o.put(cat, a)
        p.edit().putString("catStations", o.toString()).apply()
    }

    fun removeCat(p: SharedPreferences, cat: String) {
        val o = all(p)
        o.remove(cat)
        p.edit().putString("catStations", o.toString()).apply()
        saveCats(p, cats(p).filter { it.id != cat })
    }

    fun newId() = "c" + System.currentTimeMillis().toString(36)

    /** Top-Sender (deutsch, nach Beliebtheit) für eine Kategorie – einen Tag zwischengespeichert. */
    fun suggestions(ctx: Context, cat: RadioCat, fetch: (String) -> JSONArray?): List<Station> {
        if (cat.tags.isBlank()) return emptyList()
        val f = File(ctx.cacheDir, "rb_" + Integer.toHexString(cat.tags.hashCode()) + ".json")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 24 * 3600_000L) {
            parse(f.readText())?.let { return it }
        }
        val seen = HashSet<String>()
        val out = ArrayList<JSONObject>()
        for (t in cat.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            val a = fetch("/json/stations/search?tag=" + URLEncoder.encode(t, "UTF-8") +
                "&countrycode=DE&hidebroken=true&order=clickcount&reverse=true&limit=25") ?: continue
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val name = o.optString("name").trim()
                val key = name.lowercase().replace(Regex("[^a-z0-9äöü]"), "")
                if (name.isEmpty() || !seen.add(key)) continue
                out.add(o)
            }
        }
        out.sortByDescending { it.optInt("clickcount") }
        val arr = JSONArray()
        for (o in out.take(30)) arr.put(JSONObject()
            .put("name", o.optString("name").trim())
            .put("url", o.optString("url_resolved").ifEmpty { o.optString("url") })
            .put("icon", o.optString("favicon").ifEmpty { "-" }))
        if (arr.length() > 0) try { f.writeText(arr.toString()) } catch (_: Exception) { }
        return parse(arr.toString()) ?: emptyList()
    }

    private fun parse(t: String): List<Station>? = try {
        val a = JSONArray(t)
        (0 until a.length()).map { val o = a.getJSONObject(it); Station(o.getString("name"), o.getString("url"), o.optString("icon")) }
            .filter { it.url.startsWith("http") }
    } catch (_: Exception) { null }
}
