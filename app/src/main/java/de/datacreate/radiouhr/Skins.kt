package de.datacreate.radiouhr

import android.graphics.Color
import android.graphics.Typeface

enum class Presets { ROW, GRID, NONE }

data class Skin(
    val id: String,
    val name: String,
    val desc: String,
    val bgTop: Int,
    val bgBottom: Int,
    val fg: Int,
    val sub: Int,
    val accent: Int,
    val icon: Int,
    val wind: Int,
    val btnFill: Int,
    val btnStroke: Int,
    val btnPressed: Int,
    val corner: Float,
    val knobBody: Int,
    val knobTrack: Int,
    val clockFamily: String = "sans-serif-thin",
    val clockStyle: Int = Typeface.NORMAL,
    val textFamily: String = "sans-serif-light",
    val clockScale: Float = 1f,
    val presets: Presets = Presets.ROW,
    val forecast: Boolean = true,
    val events: Boolean = true,
    val details: Boolean = true,
    val extraButtons: Boolean = true
)

object Skins {
    private fun c(s: String) = Color.parseColor(s)

    val all = listOf(
        Skin(
            "standard", "Standard", "schlicht dunkel",
            c("#000000"), c("#000000"), c("#FFFFFF"), c("#AAAAAA"), c("#FF8A3D"), c("#FFD27A"), c("#8FB8D8"),
            c("#14FFFFFF"), c("#33FFFFFF"), c("#33FFFFFF"), 14f, c("#1C1C1C"), c("#33FFFFFF")
        ),
        Skin(
            "retro", "Retro", "Röhrenradio, Holz & Bernstein",
            c("#3A2314"), c("#140A04"), c("#FFB347"), c("#C99A6B"), c("#FF7A1A"), c("#FFC870"), c("#D9A066"),
            c("#2A1A0E"), c("#B8873B"), c("#4A2E18"), 4f, c("#2A1A0E"), c("#55B8873B"),
            clockFamily = "monospace", clockStyle = Typeface.BOLD, textFamily = "serif", clockScale = 0.82f
        ),
        Skin(
            "speicher", "Großer Senderspeicher", "große Stationstasten statt Vorhersage",
            c("#10161D"), c("#000000"), c("#FFFFFF"), c("#9AA7B4"), c("#3DA5FF"), c("#FFD27A"), c("#8FB8D8"),
            c("#1AFFFFFF"), c("#33FFFFFF"), c("#403DA5FF"), 16f, c("#1C232B"), c("#333DA5FF"),
            clockScale = 0.72f, presets = Presets.GRID, forecast = false, events = false
        ),
        Skin(
            "minimal", "Minimal", "nur Uhr, Temperatur, Play",
            c("#000000"), c("#000000"), c("#EEEEEE"), c("#777777"), c("#FFFFFF"), c("#EEEEEE"), c("#777777"),
            c("#00000000"), c("#33FFFFFF"), c("#22FFFFFF"), 28f, c("#000000"), c("#22FFFFFF"),
            presets = Presets.NONE, forecast = false, events = false, details = false, extraButtons = false
        ),
        Skin(
            "lcd", "Digitalwecker", "grüne LCD-Anzeige",
            c("#0B1A0B"), c("#020602"), c("#8CFF5A"), c("#4E9A34"), c("#8CFF5A"), c("#8CFF5A"), c("#5FBF40"),
            c("#0F240F"), c("#2E6B22"), c("#1E3E16"), 6f, c("#0A170A"), c("#332E6B22"),
            clockFamily = "monospace", textFamily = "monospace", clockScale = 0.85f
        ),
        Skin(
            "hell", "Hell", "heller Hintergrund für tagsüber",
            c("#F4F1EA"), c("#E2DBCD"), c("#1E1E1E"), c("#6B6458"), c("#E8572A"), c("#E8952A"), c("#3A6E98"),
            c("#FFFFFF"), c("#22000000"), c("#15000000"), 16f, c("#FFFFFF"), c("#22000000"),
            clockFamily = "sans-serif-light"
        ),
        Skin(
            "neon", "Neon", "Synthwave in Pink & Türkis",
            c("#1B0A33"), c("#06020E"), c("#39F3FF"), c("#C77DFF"), c("#FF2BD6"), c("#FFE45C"), c("#39F3FF"),
            c("#1AFF2BD6"), c("#88FF2BD6"), c("#44FF2BD6"), 12f, c("#140726"), c("#44FF2BD6")
        ),
        Skin(
            "ozean", "Ozean", "tiefes Blau",
            c("#0A2540"), c("#020D19"), c("#E6F4FF"), c("#8DB8D9"), c("#2EC4E6"), c("#FFD27A"), c("#7FD1E8"),
            c("#1A2EC4E6"), c("#552EC4E6"), c("#402EC4E6"), 20f, c("#0A1E33"), c("#332EC4E6")
        ),
        Skin(
            "schwarzwald", "Schwarzwald", "Tannengrün, Creme & Bollenhut-Rot",
            c("#14301F"), c("#050D08"), c("#F3E9D2"), c("#A8B89A"), c("#D3202F"), c("#F3C969"), c("#9CC3A0"),
            c("#1E3A27"), c("#4C6B4F"), c("#2D5238"), 10f, c("#132A1B"), c("#444C6B4F"),
            clockFamily = "serif", textFamily = "serif", clockScale = 0.9f
        )
    )

    fun byId(id: String?): Skin = all.firstOrNull { it.id == id } ?: all[0]
}
