package com.ngocdevv.nativecook.blocks.lists.sendmoneycards

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

internal object SendMoneyCardsSpec {
    data class Box(val x: Float, val y: Float, val width: Float, val height: Float, val radius: Float)
    data class Pose(
        val lift: Float = 0f,
        val arm: Float = 0f,
        val sheet: Float = 0f,
        val fade: Float = 0f,
        val hintLift: Float = 0f,
        val closing: Boolean = false,
    )
    data class Person(val id: String, val name: String, val drawable: Int)
    data class Payment(val merchant: String, val date: String, val amount: String, val incoming: Boolean)

    const val Network = "NUVA"
    const val Balance = "$ 15,000.75"
    const val Last4 = "1234"
    const val Valid = "12/35"
    const val Holder = "RAYAN ALMASRI"

    val Favourites = listOf(
        Person("farid", "Farid", com.ngocdevv.nativecook.R.drawable.sendmoneycards_farid),
        Person("shadi", "Shadi", com.ngocdevv.nativecook.R.drawable.sendmoneycards_shadi),
        Person("cyrus", "Cyrus", com.ngocdevv.nativecook.R.drawable.sendmoneycards_cyrus),
    )
    val Contacts = listOf(
        Person("annette", "Noor Rahman", com.ngocdevv.nativecook.R.drawable.sendmoneycards_emoji_annette),
        Person("cameron", "Isabella Montgomery", com.ngocdevv.nativecook.R.drawable.sendmoneycards_emoji_cameron),
        Person("jane", "Imani Brooks", com.ngocdevv.nativecook.R.drawable.sendmoneycards_emoji_jane),
        Person("wade", "Yara Haddad", com.ngocdevv.nativecook.R.drawable.sendmoneycards_emoji_wade),
        Person("devon", "Ravi Menon", com.ngocdevv.nativecook.R.drawable.sendmoneycards_emoji_devon),
    )
    val Payments = listOf(
        Payment("Orchard", "May 24 , 2024", "- $103.56", false),
        Payment("Maple", "May 24 , 2024", "+ $1,000.97", true),
        Payment("Orchard", "May 24 , 2024", "- $103.56", false),
        Payment("Orchard", "May 24 , 2024", "+ $103.56", true),
    )

    const val Screen = 0xFFD5DAE6
    const val Ink = 0xFF121214
    const val InkSoft = 0xFF3C3C43
    const val Muted = 0xFF6E6E76
    const val Chip = 0xFFC3C6D0
    const val Row = 0xFFC2C5D2
    const val RowIcon = 0xFFB4B5C1
    const val Blue = 0xFF0016E9
    const val BlueLift = 0xFF003BEF
    const val BlueText = 0xFF0A2CF0
    const val CardChip = 0xFFB6CFE5
    const val White = 0xFFFFFFFF
    const val Black = 0xFF000000
    const val PillMuted = 0xFFB6B6BC
    const val SheetMuted = 0xFF8E8E93
    const val AddNew = 0xFFE9E9EE

    const val PillWidth = 305f
    const val PillHeight = 45f
    const val PillTop = 8f
    const val PillRadius = 22.5f
    const val PillPadX = 19.3f
    const val PillPadRight = 16f
    const val PillAvatar = 28f
    const val PillAvatarStep = 18f
    const val IslandWidth = 126f
    const val IslandHeight = 37f
    const val IslandTop = 11f
    const val TabWidth = 184f
    const val TabDepth = 35f
    const val TabOverlap = 22f
    const val TabRadius = 36f
    const val TabArrowY = 61f
    const val TabBottom = 87f
    const val TabArrowFollow = 0.33f
    const val HeaderButtonY = 97.7f
    const val HeaderButton = 48f
    const val HeaderInset = 48f
    const val HeaderGridGlyph = 21f
    const val HeaderPlusGlyph = 20f

    const val CardInset = 50f
    const val CardTop = 163f
    const val CardHeight = 275f
    const val CardRadius = 24f
    const val CardPadX = 20f
    const val CardPadRight = 17.5f
    const val CardValidRight = 18f
    const val CardBrandY = 45f
    const val CardChipY = 89f
    const val CardChipWidth = 26f
    const val CardChipHeight = 18f
    const val CardChipRadius = 3f
    const val CardNumberY = 120f
    const val CardLast4X = 112.3f
    const val CardDotsX = 19.6f
    const val CardDotsCenterY = 120.9f
    const val CardDotSize = 2.7f
    const val CardDotPitch = 7f
    const val CardGroupPitch = 31.1f
    const val CardValidY = 93f
    const val CardNameY = 158f
    const val CardActionY = 210f
    const val CardAction = 46f
    const val CardActionGlyph = 22f
    const val CardLabelGap = 4.4f
    const val HintY = 109f

    const val ListTitleY = 494f
    const val ListTop = 573f
    const val ListRowHeight = 60f
    const val ListPitch = 78f
    const val ListInset = 49f
    const val ListIcon = 48f
    const val ListIconPad = 5f
    const val ListNameGap = 9f
    const val ListPadRight = 10f
    const val ListGlyph = 23f
    const val ListArrowButton = 46f
    const val ListArrowX = 330.5f
    const val ListArrowY = 521f
    const val ListArrowGlyph = 24f

    const val SheetLeft = 30f
    const val SheetRight = 26f
    const val SheetTop = 79f
    const val SheetBottom = 16f
    const val SheetRadius = 44f
    const val SheetTitleX = 60.4f
    const val SheetTitleY = 119f
    const val SheetCloseX = 70.3f
    const val SheetCloseY = 133f
    const val SheetCloseGlyph = 18f
    const val SheetAvatarsY = 255.7f
    const val SheetAvatar = 39f
    const val SheetAvatarX = 79.5f
    const val SheetAvatarStep = 76f
    const val SheetAddNewX = 307.5f
    const val SheetContactsTitleY = 340f
    const val SheetContactsTop = 446.5f
    const val SheetContactPitch = 59.6f
    const val SheetContactX = 79.2f
    const val SheetNameGap = 15.1f
    const val SheetButtonX = 62f
    const val SheetButtonWidth = 283f
    const val SheetButtonTop = 753f
    const val SheetButtonHeight = 52f

    const val FontPill = 15.5f
    const val FontHint = 15.3f
    const val FontBrand = 21f
    const val FontBalance = 34.8f
    const val FontNumber = 16f
    const val FontValid = 15f
    const val FontTiny = 8f
    const val FontAction = 11.7f
    const val FontSectionTitle = 24f
    const val FontSectionLead = 25f
    const val FontRowName = 14f
    const val FontRowMeta = 12f
    const val FontRowAmount = 11f
    const val FontSheetTitle = 41.2f
    const val FontSheetTitleLine = 46.7f
    const val FontContactsTitle = 22f
    const val FontContactsLine = 27f
    const val FontContact = 14f
    const val FontLabel = 12f

    const val SheetGrowOriginY = 30f
    const val SheetOpenFadeFrom = 60f
    const val SheetOpenFadeTo = 150f
    const val SheetOpenScaleFrom = 0.5f
    const val SheetOpenScaleStart = 40f
    const val SheetOpenScaleEnd = 230f
    const val SheetCloseFadeFrom = 150f
    const val SheetCloseFadeTo = 250f
    const val SheetCloseOriginY = -212f
    val SheetCloseTimes = floatArrayOf(0f, 53f, 187f, 290f)
    val SheetCloseScales = floatArrayOf(1f, 0.83f, 0.55f, 0.45f)

    const val PressLift = 63f
    const val OpenDrag = 36f
    const val FlickVelocity = 650f
    const val PressDuration = 450f
    const val ArmDuration = 280f
    const val FallDuration = 285f
    const val OpenDuration = 250f
    const val OpenDelay = 0f
    const val CloseDuration = 290f
    const val ReturnDelay = 0f
    const val TabOut = 250f
    const val TabFade = 100f
    const val ReleaseDuration = 360f
    const val FadeDuration = 200f
    val ReturnTimes = floatArrayOf(0f, 57f, 187f, 220f, 253f, 287f, 320f)
    val ReturnLifts = floatArrayOf(800f, 620f, 264f, 196f, 97f, 37f, 14f)
    const val ReturnDuration = 320f
    const val SettleMass = 1f
    const val SettleStiffness = 120f
    const val SettleDamping = 15f
    const val SettleVelocity = -750f
    const val HintLag = 25f
    const val HintFollowStiffness = 900f
    const val HintFollowDamping = 60f
    const val LoopMs = 10_000f
    const val ResumeAfterMs = 3_500f
    const val ScriptPress = 1_500f
    const val ScriptOpen = 3_300f
    const val ScriptClose = 7_300f
    val OpenTimes = floatArrayOf(0f, 17f, 50f, 83f, 117f, 150f, 183f, 250f)
    val OpenWidths = floatArrayOf(120f, 190f, 215f, 240f, 262f, 285f, 330f, 346f)
    val OpenTops = floatArrayOf(18f, 18f, 22f, 26f, 30f, 40f, 60f, 79f)
    val OpenBottoms = floatArrayOf(50f, 134f, 232f, 333f, 397f, 569f, 639f, 858f)
    val CloseTimes = floatArrayOf(0f, 20f, 53f, 120f, 187f, 220f, 253f, 290f)
    val CloseWidths = floatArrayOf(346f, 330f, 300f, 280f, 258f, 230f, 200f, 160f)
    val CloseTops = floatArrayOf(79f, 66f, 58f, 48f, 35f, 25f, 18f, 18f)
    val CloseBottoms = floatArrayOf(858f, 720f, 660f, 470f, 276f, 161f, 121f, 68f)
    const val PinHeight = 874f
    const val GooBlur = 14f
    const val GooAlphaMultiplier = 24f
    const val GooAlphaBias = -11.5f
    const val PressScale = 0.97f
    const val PressRetention = 16f
    const val SnapBackMass = 1f
    const val SnapBackStiffness = 220f
    const val SnapBackDamping = 18f

    fun islandOffset(safeTop: Float) = if (safeTop > 0f) safeTop - ScreenSafeTop else -40f

    fun scriptPose(time: Float, offscreen: Float, reduced: Boolean): Pose {
        if (reduced) {
            val hint = segment(time, ScriptPress, FadeDuration) * (1f - segment(time, ScriptClose, FadeDuration))
            val fade = segment(time, ScriptOpen, FadeDuration) * (1f - segment(time, ScriptClose, FadeDuration))
            return Pose(arm = hint, fade = fade)
        }
        var lift = 0f
        var arm = 0f
        var sheet = 0f
        var closing = false
        if (time >= ScriptPress) {
            lift = PressLift * easeOutCubic(segment(time, ScriptPress, PressDuration))
            arm = easeOutCubic(segment(time, ScriptPress, ArmDuration))
        }
        if (time >= ScriptOpen) {
            lift = mix(PressLift, offscreen, segment(time, ScriptOpen, FallDuration))
            sheet = segment(time, ScriptOpen + OpenDelay, OpenDuration)
        }
        if (time >= ScriptClose) {
            closing = true
            sheet = 1f - segment(time, ScriptClose, CloseDuration)
            val returning = time - ScriptClose - ReturnDelay
            lift = when {
                returning < 0f -> offscreen
                returning < ReturnDuration -> table(returning, ReturnTimes, ReturnLifts)
                else -> springSettle(returning - ReturnDuration)
            }
            arm = 1f - easeInOutCubic(segment(time, ScriptClose + TabOut, TabFade))
        }
        val hintLift = if (time >= ScriptClose) scriptLift(max(0f, time - HintLag), offscreen) else lift
        return Pose(lift = lift, arm = arm, sheet = sheet, hintLift = hintLift, closing = closing)
    }

    private fun scriptLift(time: Float, offscreen: Float): Float {
        var lift = if (time >= ScriptPress) {
            PressLift * easeOutCubic(segment(time, ScriptPress, PressDuration))
        } else 0f
        if (time >= ScriptOpen) {
            lift = mix(PressLift, offscreen, segment(time, ScriptOpen, FallDuration))
        }
        if (time >= ScriptClose) {
            val returning = time - ScriptClose - ReturnDelay
            lift = when {
                returning < 0f -> offscreen
                returning < ReturnDuration -> table(returning, ReturnTimes, ReturnLifts)
                else -> springSettle(returning - ReturnDuration)
            }
        }
        return lift
    }

    fun sheetBox(progress: Float, width: Float, height: Float, offsetY: Float, closing: Boolean): Box {
        val time = if (closing) (1f - progress) * CloseDuration else progress * OpenDuration
        val boxWidth = table(time, if (closing) CloseTimes else OpenTimes, if (closing) CloseWidths else OpenWidths)
        val top = table(time, if (closing) CloseTimes else OpenTimes, if (closing) CloseTops else OpenTops) + offsetY
        val pinBottom = table(time, if (closing) CloseTimes else OpenTimes, if (closing) CloseBottoms else OpenBottoms)
        val bottom = pinBottom + offsetY + (height - offsetY - PinHeight) * (pinBottom / OpenBottoms.last())
        val boxHeight = max(1f, bottom - top)
        val round = min(boxWidth, boxHeight) / 2f
        val square = if (closing) 1f - smooth(time / 45f) else smooth((time - 183f) / 67f)
        val radius = mix(round, SheetRadius, square)
        val fullWidth = width - SheetLeft - SheetRight
        val x = (width - boxWidth) / 2f + (SheetLeft - (width - fullWidth) / 2f) * min(1f, boxWidth / fullWidth)
        return Box(x, top, boxWidth, boxHeight, radius)
    }

    fun pillBox(progress: Float, width: Float, offsetY: Float): Box {
        val amount = smooth((progress - 0.2f) / 0.4f)
        val boxWidth = mix(PillWidth, IslandWidth, amount)
        val boxHeight = mix(PillHeight, IslandHeight, amount)
        return Box((width - boxWidth) / 2f, mix(PillTop, IslandTop, amount) + offsetY,
            boxWidth, boxHeight, boxHeight / 2f)
    }

    fun tabBox(arm: Float, progress: Float, pill: Box): Box {
        val depth = TabDepth * arm * (1f - min(1f, progress * 2.5f))
        val width = min(TabWidth, pill.width - 12f)
        val y = pill.y + pill.height - TabOverlap
        val height = TabOverlap + depth
        return Box(pill.x + (pill.width - width) / 2f, y, width, height, min(TabRadius, height / 2f))
    }

    data class ContentTransform(val scale: Float, val opacity: Float, val translateY: Float, val originY: Float)

    fun sheetContentTransform(progress: Float, closing: Boolean, offsetY: Float, height: Float): ContentTransform {
        val scale: Float
        val opacity: Float
        val originY: Float
        if (closing) {
            val time = (1f - progress) * CloseDuration
            scale = table(time, SheetCloseTimes, SheetCloseScales)
            opacity = 1f - clamp((time - SheetCloseFadeFrom) / (SheetCloseFadeTo - SheetCloseFadeFrom))
            originY = SheetCloseOriginY
        } else {
            val time = progress * OpenDuration
            val amount = clamp((time - SheetOpenScaleStart) / (SheetOpenScaleEnd - SheetOpenScaleStart))
            scale = SheetOpenScaleFrom + (1f - SheetOpenScaleFrom) * (1f - (1f - amount) * (1f - amount))
            opacity = clamp((time - SheetOpenFadeFrom) / (SheetOpenFadeTo - SheetOpenFadeFrom))
            originY = SheetGrowOriginY
        }
        val translateY = (offsetY + originY - height / 2f) * (1f - scale)
        return ContentTransform(scale, opacity, translateY, originY)
    }

    fun rubber(distance: Float, limit: Float = 120f): Float {
        return sign(distance) * limit * (1f - 1f / (abs(distance) / limit + 1f))
    }

    fun easeOutCubic(value: Float): Float {
        val inverse = 1f - value
        return 1f - inverse * inverse * inverse
    }

    fun easeInOutCubic(value: Float): Float {
        return if (value < 0.5f) 4f * value * value * value else 1f - (-2f * value + 2f).pow(3) / 2f
    }

    fun springSettle(milliseconds: Float): Float {
        val initial = ReturnLifts.last()
        val angular = sqrt(SettleStiffness / SettleMass)
        val dampingRatio = SettleDamping / (2f * sqrt(SettleStiffness * SettleMass))
        val damped = angular * sqrt(1f - dampingRatio * dampingRatio)
        val seconds = milliseconds / 1000f
        return exp(-dampingRatio * angular * seconds) *
            (initial * cos(damped * seconds) +
                ((SettleVelocity + dampingRatio * angular * initial) / damped) * sin(damped * seconds))
    }

    fun springDisplacement(
        milliseconds: Float,
        initial: Float,
        velocity: Float,
        mass: Float,
        stiffness: Float,
        damping: Float,
    ): Float {
        val angular = sqrt(stiffness / mass)
        val dampingRatio = damping / (2f * sqrt(stiffness * mass))
        val damped = angular * sqrt(max(0.0001f, 1f - dampingRatio * dampingRatio))
        val seconds = milliseconds / 1000f
        return exp(-dampingRatio * angular * seconds) *
            (initial * cos(damped * seconds) +
                ((velocity + dampingRatio * angular * initial) / damped) * sin(damped * seconds))
    }

    fun segment(time: Float, start: Float, duration: Float) = clamp((time - start) / duration)
    fun clamp(value: Float) = min(1f, max(0f, value))
    fun smooth(value: Float): Float {
        val amount = clamp(value)
        return amount * amount * (3f - 2f * amount)
    }
    fun mix(from: Float, to: Float, amount: Float) = from + (to - from) * amount

    fun table(time: Float, times: FloatArray, values: FloatArray): Float {
        if (time <= times[0]) return values[0]
        for (index in 1 until times.size) {
            if (time <= times[index]) {
                val amount = (time - times[index - 1]) / (times[index] - times[index - 1])
                return mix(values[index - 1], values[index], amount)
            }
        }
        return values.last()
    }

    const val ScreenWidth = 402f
    const val ScreenHeight = 874f
    const val ScreenSafeTop = 62f
}
