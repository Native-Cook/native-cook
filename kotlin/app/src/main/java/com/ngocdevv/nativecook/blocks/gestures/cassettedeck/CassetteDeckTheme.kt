package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

internal data class CassetteDeckRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val radius: Double = 0.0,
)

internal data class CassetteDeckPoint(val x: Double, val y: Double)

/** Every colour, copy string, and stage-space measurement from the React Native reference. */
internal object CassetteDeckTheme {
    const val STAGE_WIDTH = 402.0
    const val STAGE_HEIGHT = 778.0
    const val EDGE = 16.0
    const val BRAND_BASELINE = 46.0
    const val MODEL_BASELINE = 66.0
    const val HINT_BASELINE = 748.0
    const val FINGER_SIZE = 44.0

    val plate = 0xFFCFD2D5.toInt()
    val plateLight = 0xFFE6E8EA.toInt()
    val plateDeep = 0xFFA9ADB2.toInt()
    val engrave = 0xFF4A4E54.toInt()
    val engraveMuted = 0xFF7B8086.toInt()
    val wellColor = 0xFF121314.toInt()
    val shell = 0xFF2A2B2E.toInt()
    val shellLight = 0xFF3A3B3F.toInt()
    val label = 0xFFF1E8D4.toInt()
    val labelLine = 0x59A0785A
    val labelBand = 0xFFD9573B.toInt()
    val pen = 0xFF1F2A63.toInt()
    val tape = 0xFF3B2418.toInt()
    val tapeLight = 0xFF6B4430.toInt()
    val hub = 0xFFF4F2EE.toInt()
    val counter = 0xFF0E0F10.toInt()
    val digit = 0xFFF2F0EA.toInt()
    val led = 0xFFFF8A2A.toInt()
    val ledOff = 0xFF3A2A20.toInt()
    val meterOn = 0xFF7BE38E.toInt()
    val meterHot = 0xFFFFB34A.toInt()
    val meterPeak = 0xFFFF5A3C.toInt()
    val meterOff = 0xFF23262A.toInt()
    val keyTop = 0xFFE9EBED.toInt()
    val keyFront = 0xFF9EA3A9.toInt()
    val keyDeep = 0xFF6E737A.toInt()
    val playMark = 0xFFE2462F.toInt()
    val finger = 0x291E2024
    val fingerEdge = 0x661E2024

    val power = CassetteDeckPoint(370.0, 40.0)
    val well = CassetteDeckRect(16.0, 98.0, 370.0, 300.0, 18.0)
    val cassette = CassetteDeckRect(36.0, 132.0, 330.0, 226.0, 12.0)
    val labelRect = CassetteDeckRect(52.0, 146.0, 298.0, 128.0, 6.0)
    val window = CassetteDeckRect(116.0, 210.0, 170.0, 52.0, 26.0)
    val reels = arrayOf(CassetteDeckPoint(148.0, 236.0), CassetteDeckPoint(254.0, 236.0))
    const val HUB_RADIUS = 14.0
    const val PACK_MIN = 17.0
    const val PACK_MAX = 44.0
    const val READOUT_Y = 436.0
    const val READOUT_HEIGHT = 64.0
    const val COUNTER_X = 16.0
    const val METERS_X = 178.0
    const val METERS_WIDTH = 208.0
    const val KEYS_Y = 552.0
    const val KEYS_HEIGHT = 140.0
    const val KEY_GAP = 8.0
    const val KEY_TOP = 22.0
    const val KEY_WIDTH = (STAGE_WIDTH - 2.0 * EDGE - 4.0 * KEY_GAP) / 5.0

    const val brand = "VELOR"
    const val model = "TC-7  STEREO CASSETTE DECK"
    const val side = "A"
    const val title = "Glass Hours"
    const val subtitle = "for Jun, summer mix"
    const val counterLabel = "TAPE COUNTER"
    const val memo = "MEMO"
    val hints = arrayOf("Press play", "Playing side A", "Winding", "Rewinding to 000", "Paused")
    val keyNames = arrayOf("Rewind", "Play", "Fast forward", "Stop", "Pause")

    fun keyX(index: Int): Double = EDGE + index * (KEY_WIDTH + KEY_GAP)
}
