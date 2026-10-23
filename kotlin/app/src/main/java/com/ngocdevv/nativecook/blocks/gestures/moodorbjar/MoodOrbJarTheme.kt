package com.ngocdevv.nativecook.blocks.gestures.moodorbjar

import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.R

internal object MoodOrbJarTheme {
    const val STAGE_WIDTH = MoodOrbJarTokens.STAGE_WIDTH
    const val REPLICA = MoodOrbJarTokens.REPLICA
}

private fun moodOrbJarColor(value: String): Color {
    if (value.startsWith("#")) {
        return Color(parseColor(value))
    }
    val values = value.substringAfter('(').substringBefore(')').split(',')
    return Color(
        red = values[0].trim().toInt(),
        green = values[1].trim().toInt(),
        blue = values[2].trim().toInt(),
        alpha = (values[3].trim().toFloat() * 255).toInt(),
    )
}

internal object MoodOrbJarColors {
    val bg = moodOrbJarColor(MoodOrbJarTokens.Colors.bg)
    val bgBottom = moodOrbJarColor(MoodOrbJarTokens.Colors.bgBottom)
    val card = moodOrbJarColor(MoodOrbJarTokens.Colors.card)
    val label = moodOrbJarColor(MoodOrbJarTokens.Colors.label)
    val secondary = moodOrbJarColor(MoodOrbJarTokens.Colors.secondary)
    val cardShadow = moodOrbJarColor(MoodOrbJarTokens.Colors.cardShadow)
    val ink = moodOrbJarColor(MoodOrbJarTokens.Colors.ink)
    val mute = moodOrbJarColor(MoodOrbJarTokens.Colors.mute)
    val faint = moodOrbJarColor(MoodOrbJarTokens.Colors.faint)
    val danger = moodOrbJarColor(MoodOrbJarTokens.Colors.danger)
    val dangerInk = moodOrbJarColor(MoodOrbJarTokens.Colors.dangerInk)
    val dotsBg = moodOrbJarColor(MoodOrbJarTokens.Colors.dotsBg)
    val sheetLine = moodOrbJarColor(MoodOrbJarTokens.Colors.sheetLine)
    val dotsInk = moodOrbJarColor(MoodOrbJarTokens.Colors.dotsInk)
    val aim = moodOrbJarColor(MoodOrbJarTokens.Colors.aim)
}

internal data class MoodOrbJarMood(
    val name: String,
    val base: Color,
    val light: Color,
    val deep: Color,
    val tags: String,
    val art: Int,
)

private val moodOrbJarArt = intArrayOf(
    R.drawable.moodorbjar_orb_awful_r,
    R.drawable.moodorbjar_orb_bad_r,
    R.drawable.moodorbjar_orb_neutral_r,
    R.drawable.moodorbjar_orb_good_r,
    R.drawable.moodorbjar_orb_great_r,
)

internal val MoodOrbJarMoods = MoodOrbJarTokens.MOODS.mapIndexed { index, mood ->
    MoodOrbJarMood(
        name = mood.name,
        base = moodOrbJarColor(mood.base),
        light = moodOrbJarColor(mood.light),
        deep = moodOrbJarColor(mood.deep),
        tags = mood.tags,
        art = moodOrbJarArt[index],
    )
}

internal object MoodOrbJarSpace {
    const val XS = MoodOrbJarTokens.Space.xs
    const val SM = MoodOrbJarTokens.Space.sm
    const val CARD_PAD = MoodOrbJarTokens.Space.cardPad
    const val LG = MoodOrbJarTokens.Space.lg
    const val CARD_GAP = MoodOrbJarTokens.Space.cardGap
    const val AVATAR_TOP = MoodOrbJarTokens.Space.avatarTop
}

internal object MoodOrbJarLayout {
    const val SIDE = MoodOrbJarTokens.Layout.side
    const val HEADER = MoodOrbJarTokens.Layout.header
    const val ICON = MoodOrbJarTokens.Layout.icon
    const val LID_WIDTH = MoodOrbJarTokens.Layout.lidWidth
    const val LID_HEIGHT = MoodOrbJarTokens.Layout.lidHeight
    const val LID_OVERLAP = MoodOrbJarTokens.Layout.lidOverlap
    const val JAR_TOP = MoodOrbJarTokens.Layout.jarTop
    const val JAR_IMAGE_W = MoodOrbJarTokens.Layout.jarImageW
    const val JAR_IMAGE_H = MoodOrbJarTokens.Layout.jarImageH
    const val JAR_IMAGE_MOUTH_Y = MoodOrbJarTokens.Layout.jarImageMouthY
    const val JAR_WIDTH = MoodOrbJarTokens.Layout.jarWidth
    const val JAR_HEIGHT = MoodOrbJarTokens.Layout.jarHeight
    const val BASE = MoodOrbJarTokens.Layout.base
    const val WALL = MoodOrbJarTokens.Layout.wall
    const val SHOULDER = MoodOrbJarTokens.Layout.shoulder
    const val CORNER = MoodOrbJarTokens.Layout.corner
    const val MOUTH_HALF = MoodOrbJarTokens.Layout.mouthHalf
    const val LABEL_Y = MoodOrbJarTokens.Layout.labelY
    const val PICKER_TOP = MoodOrbJarTokens.Layout.pickerTop
    const val PICKER_HEIGHT = MoodOrbJarTokens.Layout.pickerHeight
    const val PICKER_ORB = MoodOrbJarTokens.Layout.pickerOrb
    const val PICKER_ORB_Y = MoodOrbJarTokens.Layout.pickerOrbY
    const val SLOT_GAP = MoodOrbJarTokens.Layout.slotGap
    const val SLOT_DX = MoodOrbJarTokens.Layout.slotDx
    const val SLOT_LABEL_H = MoodOrbJarTokens.Layout.slotLabelH
    const val LABEL_SIZE = MoodOrbJarTokens.Layout.labelSize
    const val CARD_RADIUS = MoodOrbJarTokens.Layout.cardRadius
    const val ENTRIES_TOP = MoodOrbJarTokens.Layout.entriesTop
    const val ENTRY_HEIGHT = MoodOrbJarTokens.Layout.entryHeight
    const val ENTRY_GAP = MoodOrbJarTokens.Layout.entryGap
    const val ENTRY_ORB = MoodOrbJarTokens.Layout.entryOrb
    const val ORB = MoodOrbJarTokens.Layout.orb
}

internal val MoodOrbJarPileMoods = MoodOrbJarTokens.PILE_MOODS

internal object MoodOrbJarCopy {
    const val MONTH = MoodOrbJarTokens.Copy.month
    const val BACK = MoodOrbJarTokens.Copy.back
    const val CALENDAR = MoodOrbJarTokens.Copy.calendar
    const val MORE = MoodOrbJarTokens.Copy.more
    const val UNDO = MoodOrbJarTokens.Copy.undo
    const val NOW = MoodOrbJarTokens.Copy.now

    fun add(mood: String) = MoodOrbJarTokens.Copy.add(mood)

    internal data class Sample(
        val mood: Int,
        val tags: String,
        val time: String,
    )

    val entries = MoodOrbJarTokens.Copy.entries.map { entry ->
        Sample(entry.mood, entry.tags, entry.time)
    }
}
