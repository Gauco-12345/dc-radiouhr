package de.datacreate.radiouhr

import android.graphics.Color
import android.graphics.Typeface

enum class Presets { ROW, GRID, KEYS, NONE }
enum class ClockStyle { TEXT, NIXIE, SEGMENT, FLIP, ANALOG_BRAUN, ANALOG_WOOD, CUCKOO }
enum class BtnStyle { FLAT, IVORY, KEYPAD, LCD, NEON, GLASS, ROUND, WOOD, NONE }
enum class KnobStyle { MODERN, BAKELITE, LED, NEON, THIN, BRAUN, WOOD, GLASS }
enum class BgStyle { PLAIN, WOOD, DARKWOOD, METAL, SYNTH, WAVES }
enum class PanelStyle { NONE, GRILLE, BEZEL, GLASS }
enum class StationStyle { TEXT, DIAL, LCD }

fun col(s: String): Int = Color.parseColor(s)

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
    val extraButtons: Boolean = true,
    // ---- Gestaltung über die Farben hinaus
    val clock: ClockStyle = ClockStyle.TEXT,
    val btn: BtnStyle = BtnStyle.FLAT,
    val knob: KnobStyle = KnobStyle.MODERN,
    val bg: BgStyle = BgStyle.PLAIN,
    val clockPanel: PanelStyle = PanelStyle.NONE,
    val radioPanel: PanelStyle = PanelStyle.NONE,
    val station: StationStyle = StationStyle.TEXT,
    val iconTint: Int = Color.WHITE,
    val lcdScreen: Int = col("#101010"),
    val textGlow: Boolean = false
)

object Skins {
    val all = listOf(
        Skin(
            "standard", "Standard", "schlicht dunkel",
            col("#000000"), col("#000000"), col("#FFFFFF"), col("#AAAAAA"), col("#FF8A3D"), col("#FFD27A"), col("#8FB8D8"),
            col("#14FFFFFF"), col("#33FFFFFF"), col("#33FFFFFF"), 14f, col("#1C1C1C"), col("#33FFFFFF")
        ),
        Skin(
            "retro", "Retro", "Röhrenradio: Holz, Nixie-Röhren, Senderskala, Elfenbeintasten",
            col("#5A3519"), col("#2A160A"), col("#F3E3C3"), col("#D8B98A"), col("#FF8A2A"), col("#FFC870"), col("#E8C89A"),
            col("#2A1A0E"), col("#B8873B"), col("#4A2E18"), 5f, col("#2A1A0E"), col("#AAD8B98A"),
            textFamily = "serif",
            presets = Presets.KEYS,
            clock = ClockStyle.NIXIE, btn = BtnStyle.IVORY, knob = KnobStyle.BAKELITE, bg = BgStyle.WOOD,
            radioPanel = PanelStyle.GRILLE, station = StationStyle.DIAL, iconTint = col("#3B2A1A")
        ),
        Skin(
            "speicher", "Großer Senderspeicher", "Autoradio: Klappziffern, Gummitasten, Bernstein-Display",
            col("#2B3036"), col("#0E1013"), col("#F0F0F0"), col("#9AA3AD"), col("#FFB000"), col("#FFD27A"), col("#8FB8D8"),
            col("#1AFFFFFF"), col("#33FFFFFF"), col("#40FFB000"), 10f, col("#1C2026"), col("#33FFB000"),
            clockScale = 0.8f, presets = Presets.GRID, forecast = false, events = false,
            clock = ClockStyle.FLIP, btn = BtnStyle.KEYPAD, knob = KnobStyle.LED, bg = BgStyle.METAL,
            station = StationStyle.LCD, iconTint = col("#E6E6E6"), lcdScreen = col("#1A1206")
        ),
        Skin(
            "minimal", "Minimal", "nur Uhr, Temperatur, Play",
            col("#000000"), col("#000000"), col("#EEEEEE"), col("#777777"), col("#FFFFFF"), col("#EEEEEE"), col("#777777"),
            col("#00000000"), col("#33FFFFFF"), col("#22FFFFFF"), 28f, col("#000000"), col("#22FFFFFF"),
            presets = Presets.NONE, forecast = false, events = false, details = false, extraButtons = false,
            btn = BtnStyle.NONE, knob = KnobStyle.THIN, iconTint = col("#DDDDDD")
        ),
        Skin(
            "lcd", "Digitalwecker", "7-Segment-Anzeige, LCD-Tasten, LED-Lautstärke",
            col("#101410"), col("#030503"), col("#8CFF5A"), col("#4E9A34"), col("#8CFF5A"), col("#8CFF5A"), col("#5FBF40"),
            col("#0F240F"), col("#2E6B22"), col("#1E3E16"), 3f, col("#0A170A"), col("#332E6B22"),
            textFamily = "monospace",
            clock = ClockStyle.SEGMENT, btn = BtnStyle.LCD, knob = KnobStyle.LED,
            clockPanel = PanelStyle.BEZEL, station = StationStyle.LCD, iconTint = col("#8CFF5A"),
            lcdScreen = col("#061206")
        ),
        Skin(
            "hell", "Bauhaus hell", "Zeiger-Uhr im 60er-Design, runde Tasten",
            col("#EEEBE4"), col("#DAD5CA"), col("#1E1E1E"), col("#6B6458"), col("#F08A00"), col("#E8952A"), col("#3A6E98"),
            col("#FFFFFF"), col("#22000000"), col("#15000000"), 26f, col("#F4F3EF"), col("#33000000"),
            clockFamily = "sans-serif-light",
            clock = ClockStyle.ANALOG_BRAUN, btn = BtnStyle.ROUND, knob = KnobStyle.BRAUN, iconTint = col("#2A2A2A")
        ),
        Skin(
            "neon", "Neon", "leuchtende Konturen, Synthwave-Horizont",
            col("#1B0A33"), col("#06020E"), col("#39F3FF"), col("#C77DFF"), col("#FF2BD6"), col("#FFE45C"), col("#39F3FF"),
            col("#1AFF2BD6"), col("#88FF2BD6"), col("#44FF2BD6"), 12f, col("#140726"), col("#44FF2BD6"),
            btn = BtnStyle.NEON, knob = KnobStyle.NEON, bg = BgStyle.SYNTH, iconTint = col("#39F3FF"), textGlow = true
        ),
        Skin(
            "ozean", "Ozean", "Glas-Optik über Wellen",
            col("#0D3557"), col("#020D19"), col("#E6F4FF"), col("#8DB8D9"), col("#2EC4E6"), col("#FFD27A"), col("#7FD1E8"),
            col("#1AFFFFFF"), col("#44FFFFFF"), col("#33FFFFFF"), 22f, col("#0A1E33"), col("#332EC4E6"),
            btn = BtnStyle.GLASS, knob = KnobStyle.GLASS, bg = BgStyle.WAVES, radioPanel = PanelStyle.GLASS,
            iconTint = col("#E6F4FF")
        ),
        Skin(
            "schwarzwald", "Schwarzwald", "große Kuckucksuhr mit Pendel, Holztasten",
            col("#3B2414"), col("#160C05"), col("#F3E9D2"), col("#CDB68F"), col("#D3202F"), col("#F3C969"), col("#B9D3A8"),
            col("#1E3A27"), col("#4C6B4F"), col("#2D5238"), 6f, col("#3A2412"), col("#66CDB68F"),
            clockFamily = "serif", textFamily = "serif",
            clock = ClockStyle.CUCKOO, btn = BtnStyle.WOOD, knob = KnobStyle.WOOD, bg = BgStyle.DARKWOOD,
            iconTint = col("#F3E9D2")
        )
    )

    fun byId(id: String?): Skin = all.firstOrNull { it.id == id } ?: all[0]
}
