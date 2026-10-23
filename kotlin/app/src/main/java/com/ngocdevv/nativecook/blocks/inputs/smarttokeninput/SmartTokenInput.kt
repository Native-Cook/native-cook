package com.ngocdevv.nativecook.blocks.inputs.smarttokeninput

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val WIDTH = 350f

// The box the component is laid out in: the card at its top, the menus under it.
private const val HEIGHT = 200f

// Height of the card with one line of text.
private const val REST = 44f
private const val RADIUS = 14f
private const val LINE = 20f
private const val TEXT_TOP = 12f

// Left edge of the sentence inside the card: the card's padding, the checkbox and the gap after it.
private const val TEXT_LEFT = 12f + 18f + 10f

// Room between two words, and between the last one and the caret after a space.
private const val GAP = 3.6f
private const val FONT = 13.5f

private const val MENU_WIDTH = 172f
private const val MENU_GAP = 8f

// The menu is centred this far into the word it hangs under.
private const val MENU_LEAD = 12f
private const val MENU_HEADER = 28f
private const val MENU_ROW = 34f

// The canvases overflow the card and the menus by this much on every side to fit their shadows.
private val BLEED = 44.dp

private val Ink = Color(0xFF1D1D1F)
private val IconColor = Color(0xFF3A3F47)
private val Chip = Color(0xFFF1F3F5)
private const val COMMAND = "/file"

// autoPlay (seconds). Keys land `KEY` apart on average, stretched by `RHYTHM` in turn.
private const val TYPE_AT = 0.8f
private const val KEY = 0.058f
private val RHYTHM = floatArrayOf(1.0f, 0.72f, 1.3f, 0.85f, 1.12f, 0.64f, 1.42f, 0.95f)

// From the last key of the date word to the highlight moving down, then to the tap on that row.
private const val ROW_AFTER = 0.75f
private const val PICK_AFTER = 0.65f

// From the tap to the next key.
private const val MORE_AFTER = 0.55f

// From the last key of the slash command to the tap on the upload row.
private const val FILE_AFTER = 0.8f

// The chip loads, closes its ring and turns into a paperclip before the field clears.
private const val CLEAR_AFTER = 2.2f
private const val LOOP = 9.6f

// After the field is left empty, the script waits this long before it starts again.
private const val RESUME_AFTER = 3.0f

private const val BLINK = 0.53f
private const val WIPE = 0.18f
private const val CHIP_LOAD = 0.8f
private const val CHIP_CLOSE = 0.3f
private const val CHIP_HOLD = 0.5f
private const val SPIN = 0.8f
private const val DOT_DELAY = 0.12f
private const val MENU_OUT = 0.14f
private const val PRESSED = 0.96f

// Depth of the bow in the card's bottom edge, how long it takes to form, and the spring that lets it go.
private const val BOW = 5f
private const val BOW_IN = 0.12f
private val BowOut = physical(stiffness = 300f, damping = 9f)
private val Grow = physical(stiffness = 320f, damping = 22f)
private val MenuIn = physical(stiffness = 420f, damping = 26f)
private val RowMove = physical(stiffness = 480f, damping = 34f)
private val Dot = physical(stiffness = 520f, damping = 15f)
private val ChipIn = physical(stiffness = 420f, damping = 20f)
private val ClipIn = physical(stiffness = 480f, damping = 18f)

/** A spring of mass 1 given by its stiffness and damping, the numbers every stack of this block shares. */
private fun physical(stiffness: Float, damping: Float) =
    spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness)), stiffness = stiffness)

private fun ms(seconds: Float) = (seconds * 1000f).roundToInt()

private val DayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
private val MonthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private val Punctuation = setOf(
    CharCategory.CONNECTOR_PUNCTUATION, CharCategory.DASH_PUNCTUATION, CharCategory.START_PUNCTUATION,
    CharCategory.END_PUNCTUATION, CharCategory.INITIAL_QUOTE_PUNCTUATION, CharCategory.FINAL_QUOTE_PUNCTUATION,
    CharCategory.OTHER_PUNCTUATION,
)

// Icons, as outlines in a 16 dp box (the chevron in 10 dp, the paperclip in 14 dp).
private val CalendarIcon = outline(
    "M5.25 2.75 L10.75 2.75 C12.13 2.75 13.25 3.87 13.25 5.25 L13.25 10.75 C13.25 12.13 12.13 13.25 10.75 13.25 " +
        "L5.25 13.25 C3.87 13.25 2.75 12.13 2.75 10.75 L2.75 5.25 C2.75 3.87 3.87 2.75 5.25 2.75 Z M2.75 6.5 L13.25 6.5",
)
private val ChevronIcon = outline("M2.5 3.75 L5 6.25 L7.5 3.75")
private val UploadIcon = outline("M8 10.5 L8 3 M4.75 6.25 L8 3 L11.25 6.25 M3.25 13 L12.75 13")
private val ClipIcon = outline(
    "M11.03 7.49 L7.71 10.82 C6.46 12.07 4.43 12.07 3.18 10.82 C1.93 9.57 1.93 7.54 3.18 6.29 L6.93 2.55 " +
        "C7.75 1.73 9.08 1.73 9.9 2.55 C10.72 3.37 10.72 4.69 9.9 5.52 L6.29 9.12 C5.88 9.53 5.22 9.53 4.81 9.12 " +
        "C4.4 8.71 4.4 8.05 4.81 7.64 L7.78 4.67",
)

private fun outline(data: String) = PathParser().parsePathString(data).toPath()

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun tokenText(
    size: Float,
    lineHeight: Float,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    features: String? = null,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFeatureSettings = features,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val SentenceStyle = tokenText(FONT, LINE, Ink)
private val PlaceholderStyle = tokenText(FONT, LINE, Color(0xFFA3A8B0))
private val GhostStyle = tokenText(FONT, LINE, Color(0xFFB4B9C0))
private val ListStyle = tokenText(12.5f, 16f, Ink, FontWeight.Medium)
private val ChipStyle = tokenText(12f, 16f, Color(0xFF555A62), FontWeight.Medium)
private val TitleStyle = tokenText(11f, 14f, Color(0xFF9AA0A8), FontWeight.Medium)
private val RowStyle = tokenText(FONT, LINE, Ink, features = "tnum")

// The real field shows nothing of its own: no text, no caret, no selection handle.
private val FieldStyle = SentenceStyle.copy(color = Color.Transparent)
private val HiddenSelection = TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent)

private enum class TokenMenu { None, Schedule, Attach }

private data class TokenFile(
    val id: Int,
    /** Character offset of the text the chip sits at. */
    val at: Int,
    val name: String,
)

/** Which word of the text was confirmed as a date, and what it said then. */
private class TokenPick(val index: Int, val word: String)

/** A word of the sentence, or a file chip between two of them. */
private class TokenPiece(
    val id: String,
    val text: String,
    /** Position among the words; -1 for a chip. */
    val index: Int,
    /** Character offset of the word's first letter. */
    val start: Int,
    val file: TokenFile?,
)

private fun tokenPieces(value: String, files: List<TokenFile>): List<TokenPiece> {
    val pieces = mutableListOf<TokenPiece>()
    val word = StringBuilder()
    var start = 0
    var words = 0

    fun close() {
        if (word.isEmpty()) return
        pieces += TokenPiece("w$words", word.toString(), words, start, null)
        words += 1
        word.clear()
    }

    fun chips(offset: Int) {
        for (file in files) {
            if (file.at != offset) continue
            close()
            pieces += TokenPiece("f${file.id}", file.name, -1, offset, file)
        }
    }

    value.forEachIndexed { offset, character ->
        chips(offset)
        if (character.isWhitespace()) {
            close()
        } else {
            if (word.isEmpty()) start = offset
            word.append(character)
        }
    }
    chips(value.length)
    close()
    return pieces
}

/** Days from [today] to the day a word names, or null when it is not a date word. */
private fun dayOffset(word: String, today: LocalDate): Int? {
    val key = word.lowercase().trim { it.category in Punctuation }
    if (key == "today") return 0
    if (key == "tomorrow") return 1
    val target = DayNames.indexOfFirst { key == it.lowercase() || key == it.take(3).lowercase() }
    if (target < 0) return null
    // Counted from Sunday, like the names.
    val now = today.dayOfWeek.value % 7
    val ahead = (target - now + 7) % 7
    return if (ahead == 0) 7 else ahead
}

private fun dayLabel(date: LocalDate): String {
    val day = DayNames[date.dayOfWeek.value % 7].take(3)
    val month = MonthNames[date.monthValue - 1]
    return "$day, $month ${date.dayOfMonth}"
}

private sealed interface TokenAct {
    class Type(val text: String) : TokenAct
    class Row(val index: Int) : TokenAct
    data object Date : TokenAct
    data object File : TokenAct
    data object Clear : TokenAct
}

private class TokenStep(val at: Float, val act: TokenAct)

/** The autoPlay timeline: every key with the text it leaves behind, then the taps. */
private fun tokenSteps(script: String, more: String): List<TokenStep> {
    val steps = mutableListOf<TokenStep>()
    var at = TYPE_AT
    val typed = StringBuilder()

    fun type(text: String) {
        for (character in text) {
            typed.append(character)
            steps += TokenStep(at, TokenAct.Type(typed.toString()))
            at += KEY * RHYTHM[(typed.length - 1) % RHYTHM.size]
        }
    }

    type(script)
    at += ROW_AFTER
    steps += TokenStep(at, TokenAct.Row(1))
    at += PICK_AFTER
    steps += TokenStep(at, TokenAct.Date)
    at += MORE_AFTER
    type(more)
    at += FILE_AFTER
    steps += TokenStep(at, TokenAct.File)
    at += CLEAR_AFTER
    steps += TokenStep(at, TokenAct.Clear)
    return steps
}

/** The card's outline: a rounded rectangle whose bottom edge can bow. */
private fun Path.bowed(w: Float, h: Float, r: Float, bow: Float): Path {
    rewind()
    moveTo(r, 0f)
    arcTo(Rect(w - 2f * r, 0f, w, 2f * r), -90f, 90f, false)
    arcTo(Rect(w - 2f * r, h - 2f * r, w, h), 0f, 90f, false)
    // A curve through the bowed middle instead of a straight bottom edge.
    quadraticTo(w / 2f, h + bow * 2f, r, h)
    arcTo(Rect(0f, h - 2f * r, 2f * r, h), 90f, 90f, false)
    arcTo(Rect(0f, 0f, 2f * r, 2f * r), 180f, 90f, false)
    close()
    return this
}

/** A filled path with a soft edge, for the card's shadow. Drawn in the current (dp) units. */
private class SoftPath(color: Color, sigma: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }

    fun draw(canvas: android.graphics.Canvas, path: Path) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        canvas.drawPath(path.asAndroidPath(), paint)
    }
}

private class TokenPaths {
    val shadow = SoftPath(rgba(0, 0, 0, 0.08f), sigma = 10f)

    /** The card in dp, for the canvas behind it, and in pixels, to clip what is on it. */
    val outline = Path()
    val clip = Path()
}

/**
 * A task composer that reads what is typed. A date word ("Wed", "tomorrow") turns blue and opens a
 * schedule menu under it; "/" opens a file menu whose row drops a file chip into the sentence. The
 * card grows a line at a time with a wobble of its bottom edge.
 *
 * @param value Controlled text. Leave it null and the component keeps its own.
 * @param autoPlay Types the script, picks a date and a file, clears the field and starts over.
 * @param script What autoPlay types up to the date word.
 * @param scriptMore What autoPlay types after it picked the date, ending in the slash command.
 * @param fileName Name of the file the upload row attaches.
 * @param today The day date words are counted from.
 * @param onValueChange Called with the new text whenever it changes.
 * @param onSchedule Called with the chosen day when a row of the schedule menu is tapped.
 * @param onAttach Called with the file's name when the upload row is tapped.
 * @param onSend Called with the text when the keyboard's done key is pressed; the field clears itself afterwards.
 */
@Composable
fun SmartTokenInput(
    value: String? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Add a task",
    script: String = "Send email to John on Wed",
    scriptMore: String = " with a redesign proposal /f",
    listLabel: String = "No list",
    scheduleTitle: String = "Schedule",
    attachTitle: String = "Attachment",
    attachLabel: String = "File upload",
    fileName: String = "Redesign.doc",
    today: LocalDate = LocalDate.now(),
    accent: Color = Color(0xFF2F9BE0),
    onValueChange: ((String) -> Unit)? = null,
    onSchedule: ((LocalDate) -> Unit)? = null,
    onAttach: ((String) -> Unit)? = null,
    onSend: ((String) -> Unit)? = null,
    onCalendar: (() -> Unit)? = null,
    onList: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val controlled = value != null

    var inner by remember { mutableStateOf("") }
    // What the keyboard is editing: the text plus the letters it is still composing.
    var field by remember { mutableStateOf(TextFieldValue()) }
    var focused by remember { mutableStateOf(false) }
    // File chips, each sitting at a character offset of the text.
    var files by remember { mutableStateOf(listOf<TokenFile>()) }
    var nextFile by remember { mutableIntStateOf(0) }
    // The date word that was confirmed.
    var pick by remember { mutableStateOf<TokenPick?>(null) }
    // Highlighted row of the schedule menu.
    var row by remember { mutableIntStateOf(0) }
    // The two days the schedule menu offers; kept while it fades out.
    var days by remember { mutableStateOf(listOf<LocalDate>()) }
    var playing by remember { mutableStateOf(autoPlay && !controlled) }
    var caretOn by remember { mutableStateOf(true) }
    // The sentence is faded out, just before the field empties.
    var wiped by remember { mutableStateOf(false) }
    // Lines the sentence takes.
    var lines by remember { mutableIntStateOf(1) }
    // Left edge of each piece of the sentence inside the component, in dp.
    val origins = remember { mutableStateMapOf<String, Float>() }
    var scheduleAt by remember { mutableStateOf(Offset(0f, REST + MENU_GAP)) }
    var attachAt by remember { mutableStateOf(Offset(0f, REST + MENU_GAP)) }

    val height = remember { Animatable(REST) }
    // How far the middle of the card's bottom edge hangs below its corners.
    val bow = remember { Animatable(0f) }
    val paths = remember { TokenPaths() }

    val text = value ?: inner
    val pieces = remember(text, files) { tokenPieces(text, files) }
    val cardHeight = REST + (lines - 1) * LINE
    val spaced = text.lastOrNull()?.isWhitespace() == true || pieces.lastOrNull()?.file != null

    // The word the caret is in.
    val open = pieces.lastOrNull()?.takeIf { it.file == null && text.lastOrNull()?.isWhitespace() == false }
    val menu = when {
        open == null -> TokenMenu.None
        open.text.startsWith("/") -> TokenMenu.Attach
        pick?.index != open.index && dayOffset(open.text, today) != null -> TokenMenu.Schedule
        else -> TokenMenu.None
    }

    // The rest of the slash command, shown in grey after the caret.
    val ghost = open?.text?.lowercase()
        ?.takeIf { it.startsWith("/") && COMMAND.startsWith(it) }
        ?.let { COMMAND.drop(it.length) }
        .orEmpty()

    fun edit(new: String) {
        if (new == text) return
        // A chip goes with the text in front of it.
        val kept = files.filter { it.at <= new.length }
        files = kept
        val picked = pick
        if (picked != null && tokenPieces(new, kept).firstOrNull { it.index == picked.index }?.text != picked.word) {
            pick = null
        }
        if (!controlled) inner = new
        onValueChange?.invoke(new)
    }

    fun choose(chosen: Int, byHand: Boolean) {
        if (menu != TokenMenu.Schedule || open == null || chosen !in days.indices) return
        row = chosen
        pick = TokenPick(open.index, open.text)
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onSchedule?.invoke(days[chosen])
    }

    /** Swaps the slash command for a file chip. */
    fun attach(byHand: Boolean) {
        if (menu != TokenMenu.Attach || open == null) return
        val kept = text.substring(0, open.start)
        files = files + TokenFile(nextFile, kept.length, fileName)
        nextFile += 1
        edit(kept)
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onAttach?.invoke(fileName)
    }

    fun reset() {
        files = emptyList()
        pick = null
        wiped = false
        edit("")
    }

    fun send() {
        focusManager.clearFocus()
        if (pieces.isEmpty()) return
        onSend?.invoke(text)
        wiped = true
    }

    // The script outlives many recompositions; it always acts on the newest text and callbacks.
    val perform by rememberUpdatedState<(TokenAct) -> Unit> { act ->
        when (act) {
            is TokenAct.Type -> edit(act.text)
            is TokenAct.Row -> row = act.index
            TokenAct.Date -> choose(row, byHand = false)
            TokenAct.File -> attach(byHand = false)
            TokenAct.Clear -> wiped = true
        }
    }
    val steps = remember(script, scriptMore) { tokenSteps(script, scriptMore) }
    val empty by rememberUpdatedState(text.isEmpty() && files.isEmpty())

    LaunchedEffect(menu) {
        val offset = open?.let { dayOffset(it.text, today) }
        if (menu == TokenMenu.Schedule && offset != null) {
            row = 0
            days = listOf(0, 7).map { today.plusDays((offset + it).toLong()) }
        }
    }

    // Hangs the open menu under the word the caret is in, kept inside the card's width.
    val lead = open?.let { origins[it.id] }
    LaunchedEffect(menu, lead, cardHeight) {
        if (lead == null) return@LaunchedEffect
        val x = (lead + MENU_LEAD - MENU_WIDTH / 2f).coerceIn(4f, WIDTH - MENU_WIDTH - 4f)
        val at = Offset(x, cardHeight + MENU_GAP)
        when (menu) {
            TokenMenu.Schedule -> scheduleAt = at
            TokenMenu.Attach -> attachAt = at
            TokenMenu.None -> Unit
        }
    }

    LaunchedEffect(still) {
        var from = lines
        snapshotFlow { lines }.collectLatest { now ->
            if (now == from) return@collectLatest
            val grew = now > from
            from = now
            val target = REST + (now - 1) * LINE
            if (still) {
                height.snapTo(target)
                return@collectLatest
            }
            coroutineScope {
                launch { height.animateTo(target, Grow) }
                // The bottom edge bows out as the card gains a line, then rings back to straight.
                bow.animateTo(if (grew) BOW else -BOW / 2f, tween(ms(BOW_IN), easing = EaseOut))
                bow.animateTo(0f, BowOut)
            }
        }
    }

    LaunchedEffect(playing, steps) {
        if (!playing) return@LaunchedEffect
        val period = max(LOOP, steps.last().at + TYPE_AT)
        // Every step is timed from the start of its loop, so the period does not drift.
        var origin = System.nanoTime()

        suspend fun until(at: Float) {
            val wait = origin + (at * 1e9).toLong() - System.nanoTime()
            if (wait > 0) delay(wait / 1_000_000)
        }

        while (true) {
            for (step in steps) {
                until(step.at)
                perform(step.act)
            }
            origin += (period * 1e9).toLong()
            until(0f)
        }
    }

    LaunchedEffect(focused) {
        // The script resumes once the field has been left empty for a while.
        if (!autoPlay || controlled || focused || playing) return@LaunchedEffect
        delay(ms(RESUME_AFTER).toLong())
        if (empty) playing = true
    }

    LaunchedEffect(text, files.size, focused, playing) {
        // The caret stays solid while keys arrive and blinks once they stop.
        caretOn = true
        if (!focused && !playing) return@LaunchedEffect
        while (true) {
            delay(ms(BLINK).toLong())
            caretOn = !caretOn
        }
    }

    val blink by animateFloatAsState(if (caretOn) 1f else 0f, tween(80, easing = EaseOut), label = "caret")
    val hint by animateFloatAsState(
        targetValue = if (pieces.isEmpty()) 1f else 0f,
        animationSpec = if (pieces.isEmpty()) tween(200, easing = EaseOut) else snap(),
        label = "placeholder",
    )
    // Fades the sentence out, then empties the field.
    val sentence by animateFloatAsState(
        targetValue = if (wiped) 0f else 1f,
        animationSpec = if (wiped) tween(ms(WIPE), easing = EaseOut) else snap(),
        label = "wipe",
        finishedListener = { if (it == 0f) reset() },
    )

    val caret = focused || (playing && pieces.isNotEmpty())
    val tintedStyle = remember(accent) { SentenceStyle.copy(color = accent) }
    val linePx = with(density) { LINE.dp.toPx() }
    // The field keeps the keyboard's composing state between keys, and follows the text when the script or a menu rewrites it.
    val shown = if (field.text == text) field else TextFieldValue(text, TextRange(text.length))

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        // The white card and its shadow, behind what is on it.
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, w, _ ->
                    paths.outline.bowed(w, height.value, RADIUS, bow.value)
                    translate(x, y + 8f) { drawIntoCanvas { paths.shadow.draw(it.nativeCanvas, paths.outline) } }
                    translate(x, y) { drawPath(paths.outline, Color.White) }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    paths.clip.bowed(size.width, height.value.dp.toPx(), RADIUS.dp.toPx(), bow.value.dp.toPx())
                    clipPath(paths.clip) { this@drawWithContent.drawContent() }
                }
                .padding(start = 12.dp, end = 6.dp),
        ) {
            Box(Modifier.padding(top = 13.dp).size(18.dp).background(Color(0xFFEDEFF1), RoundedCornerShape(5.dp)))

            Box(Modifier.weight(1f).padding(start = 10.dp, top = TEXT_TOP.dp, end = 6.dp)) {
                Box(
                    Modifier.height(LINE.dp).graphicsLayer { alpha = hint },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(placeholder, style = PlaceholderStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TokenCaret { if (caret && pieces.isEmpty()) blink else 0f }
                }

                TokenFlow(
                    tailLead = if (spaced) GAP else 0.5f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { lines = max(1, (it.height / linePx).roundToInt()) }
                        .graphicsLayer { alpha = sentence },
                ) {
                    for (piece in pieces) {
                        key(piece.id) {
                            val place = Modifier.onGloballyPositioned {
                                origins[piece.id] = TEXT_LEFT + it.positionInParent().x / density.density
                            }
                            val file = piece.file
                            if (file != null) {
                                TokenChip(file.name, still, place)
                            } else {
                                val live = piece.id == open?.id && menu != TokenMenu.None
                                val tinted = live || pick?.index == piece.index
                                BasicText(
                                    text = piece.text,
                                    modifier = place.height(LINE.dp).wrapContentHeight(),
                                    style = if (tinted) tintedStyle else SentenceStyle,
                                    softWrap = false,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    // Caret and ghost completion hang off the last piece without taking room in the line.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TokenCaret { if (caret) blink else 0f }
                        BasicText(ghost, style = GhostStyle, softWrap = false, maxLines = 1)
                    }
                }

                // The real field: it takes the keyboard, the sentence above is drawn from its text.
                CompositionLocalProvider(LocalTextSelectionColors provides HiddenSelection) {
                    BasicTextField(
                        value = shown,
                        onValueChange = {
                            // The drawn caret is always after the last letter, so the real one stays there too.
                            field = it.copy(selection = TextRange(it.text.length))
                            edit(it.text)
                        },
                        modifier = Modifier
                            .matchParentSize()
                            .onFocusChanged {
                                focused = it.isFocused
                                // A touch takes the field over from the script.
                                if (it.isFocused && playing) {
                                    playing = false
                                    reset()
                                }
                            }
                            .semantics { contentDescription = placeholder },
                        textStyle = FieldStyle,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { send() }),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.Transparent),
                    )
                }
            }

            TokenPressable(onClick = { onCalendar?.invoke() }, modifier = Modifier.padding(top = 8.dp), label = "Schedule") {
                Box(Modifier.size(28.dp).background(Chip, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                    TokenOutline(CalendarIcon, box = 16f, width = 1.5f)
                    // The dot that says a day is set.
                    TokenDot(set = pick != null, still = still, accent = accent, modifier = Modifier.offset(11.5.dp, (-11.5).dp))
                }
            }

            TokenPressable(onClick = { onList?.invoke() }, modifier = Modifier.padding(start = 5.dp, top = 8.dp)) {
                Row(
                    Modifier.height(28.dp).background(Chip, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(8.5.dp).drawBehind {
                            drawCircle(Color(0xFFB4B9C0), style = Stroke(1.5.dp.toPx()))
                        },
                    )
                    BasicText(
                        text = listLabel,
                        modifier = Modifier.padding(start = 6.dp, end = 5.dp),
                        style = ListStyle,
                        softWrap = false,
                        maxLines = 1,
                    )
                    TokenOutline(ChevronIcon, box = 10f, width = 1.6f)
                }
            }
        }

        TokenMenuView(
            title = scheduleTitle, rows = days.map(::dayLabel), icon = CalendarIcon, open = menu == TokenMenu.Schedule,
            highlighted = row, still = still, at = scheduleAt,
        ) { choose(it, byHand = true) }
        TokenMenuView(
            title = attachTitle, rows = listOf(attachLabel), icon = UploadIcon, open = menu == TokenMenu.Attach,
            highlighted = 0, still = still, at = attachAt,
        ) { attach(byHand = true) }
    }
}

/**
 * Lays words out like text: left to right, wrapping whole pieces, every line [LINE] tall. The last
 * child is the caret's tail: it is placed [tailLead] dp after the last piece and takes no room.
 */
@Composable
private fun TokenFlow(tailLead: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val words = placeables.dropLast(1)
        val tail = placeables.last()
        val line = LINE.dp.toPx()
        val gap = GAP.dp.toPx()
        val width = constraints.maxWidth
        var x = 0f
        var wrapped = 0
        val origins = words.map { word ->
            if (x > 0f && x + word.width > width) {
                x = 0f
                wrapped += 1
            }
            Offset(x, wrapped * line).also { x += word.width + gap }
        }
        layout(width, if (words.isEmpty()) 0 else ((wrapped + 1) * line).roundToInt()) {
            words.forEachIndexed { index, word ->
                val at = origins[index]
                word.place(at.x.roundToInt(), (at.y + (line - word.height) / 2f).roundToInt())
            }
            val last = words.lastOrNull() ?: return@layout
            val at = origins.last()
            tail.place(
                (at.x + last.width + tailLead.dp.toPx()).roundToInt(),
                (at.y + (line - tail.height) / 2f).roundToInt(),
            )
        }
    }
}

@Composable
private fun TokenCaret(alpha: () -> Float) {
    Box(
        Modifier
            .size(1.5.dp, 17.dp)
            .graphicsLayer { this.alpha = alpha() }
            .background(Ink, RoundedCornerShape(0.75.dp)),
    )
}

/** Strokes an icon whose outline is given in a [box] dp square. */
@Composable
private fun TokenOutline(outline: Path, box: Float, width: Float) {
    Canvas(Modifier.size(box.dp)) {
        scale(density, pivot = Offset.Zero) {
            drawPath(outline, IconColor, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The accent dot on the calendar button: pops in a moment after a day was set. */
@Composable
private fun TokenDot(set: Boolean, still: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(set) {
        if (set && !still) {
            delay(ms(DOT_DELAY).toLong())
            shown.animateTo(1f, Dot)
        } else {
            shown.animateTo(if (set) 1f else 0f, tween(150, easing = EaseOut))
        }
    }
    Box(
        modifier
            .size(8.5.dp)
            .graphicsLayer {
                val scale = if (still) 1f else shown.value
                scaleX = scale
                scaleY = scale
                alpha = shown.value.coerceIn(0f, 1f)
            }
            .drawBehind {
                drawCircle(accent, 3.5.dp.toPx())
                drawCircle(Color.White, 3.5.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            },
    )
}

/** A file in the sentence: a ring that spins while it loads, closes, and turns into a paperclip. */
@Composable
private fun TokenChip(name: String, still: Boolean, modifier: Modifier = Modifier) {
    val shown = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }
    // 0 while the ring is open, 1 once it has closed.
    val closed = remember { Animatable(0f) }
    // 0 while the ring shows, 1 once the paperclip has taken its place.
    val clipped = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { shown.animateTo(1f, if (still) tween(150, easing = EaseOut) else ChipIn) }
        val spinning = if (still) {
            null
        } else {
            launch { spin.animateTo(360f, infiniteRepeatable(tween(ms(SPIN), easing = LinearEasing))) }
        }
        delay(ms(CHIP_LOAD).toLong())
        launch { closed.animateTo(1f, tween(ms(CHIP_CLOSE), easing = EaseOut)) }
        delay(ms(CHIP_HOLD).toLong())
        clipped.animateTo(1f, if (still) tween(150, easing = EaseOut) else ClipIn)
        // The ring is gone: nothing is left to turn.
        spinning?.cancel()
    }

    Row(
        modifier
            .graphicsLayer {
                val scale = if (still) 1f else 0.8f + 0.2f * shown.value
                scaleX = scale
                scaleY = scale
                alpha = shown.value.coerceIn(0f, 1f)
            }
            .height(LINE.dp)
            .background(Chip, RoundedCornerShape(6.dp))
            .padding(start = 5.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            scale(density, pivot = Offset.Zero) {
                val middle = Offset(7f, 7f)
                val turn = clipped.value
                val ring = (1f - turn).coerceIn(0f, 1f)
                if (ring > 0f) {
                    rotate(spin.value, middle) {
                        scale(if (still) 1f else 1f - 0.6f * turn, middle) {
                            drawArc(
                                color = Color(0xFF8A9099),
                                startAngle = 0f,
                                sweepAngle = 360f * (0.72f + 0.28f * closed.value),
                                useCenter = false,
                                topLeft = Offset(1.75f, 1.75f),
                                size = Size(10.5f, 10.5f),
                                alpha = ring,
                                style = Stroke(1.5f, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
                scale(if (still) 1f else 0.4f + 0.6f * turn, middle) {
                    drawPath(
                        ClipIcon,
                        Color(0xFF6B717A),
                        alpha = turn.coerceIn(0f, 1f),
                        style = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        BasicText(name, style = ChipStyle, softWrap = false, maxLines = 1)
    }
}

/** A small menu hanging under the card: a grey heading and rows, one of them highlighted. [at] is its corner, in dp. */
@Composable
private fun TokenMenuView(
    title: String,
    rows: List<String>,
    icon: Path,
    open: Boolean,
    highlighted: Int,
    still: Boolean,
    at: Offset,
    onPick: (Int) -> Unit,
) {
    val shadow = remember { SoftRoundRect(rgba(0, 0, 0, 0.12f), sigma = 12f) }
    val shown by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (open && !still) MenuIn else tween(ms(MENU_OUT), easing = EaseOut),
        label = "menu",
    )
    val slide by animateFloatAsState(
        targetValue = highlighted * MENU_ROW,
        animationSpec = if (still) snap() else RowMove,
        label = "row",
    )

    Box(
        Modifier
            .offset(at.x.dp - BLEED, at.y.dp - BLEED)
            // The layer is as large as the shadow, because a fading layer cuts off what is drawn outside it.
            .graphicsLayer {
                val scale = if (still) 1f else 0.95f + 0.05f * shown
                scaleX = scale
                scaleY = scale
                alpha = shown.coerceIn(0f, 1f)
                // The top centre of the menu itself.
                transformOrigin = TransformOrigin(0.5f, BLEED.toPx() / size.height)
            }
            .then(if (open) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        Canvas(Modifier.matchParentSize()) {
            surface(BLEED) { x, y, w, h ->
                drawIntoCanvas { shadow.draw(it.nativeCanvas, x, y + 8f, w, h, 12f) }
                drawRoundRect(Color.White, Offset(x, y), Size(w, h), CornerRadius(12f))
            }
        }
        Column(Modifier.padding(BLEED).width(MENU_WIDTH.dp).padding(bottom = 4.dp)) {
            Box(Modifier.height(MENU_HEADER.dp), contentAlignment = Alignment.CenterStart) {
                BasicText(
                    text = title,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp),
                    style = TitleStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
            Box(Modifier.padding(horizontal = 4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MENU_ROW.dp)
                        .graphicsLayer { translationY = slide.dp.toPx() }
                        .background(Chip, RoundedCornerShape(8.dp)),
                )
                Column {
                    rows.forEachIndexed { index, label ->
                        // A closed menu is invisible and must not catch touches.
                        TokenPressable(onClick = { onPick(index) }, enabled = open) {
                            Row(
                                Modifier.fillMaxWidth().height(MENU_ROW.dp).padding(start = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TokenOutline(icon, box = 16f, width = 1.5f)
                                Spacer(Modifier.width(9.dp))
                                BasicText(label, style = RowStyle, overflow = TextOverflow.Ellipsis, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A tappable part of the component: scales down while pressed. */
@Composable
private fun TokenPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                // A disabled clickable still swallows the touch, and a closed menu can lie over the open one.
                if (enabled) {
                    Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
    ) { content() }
}
