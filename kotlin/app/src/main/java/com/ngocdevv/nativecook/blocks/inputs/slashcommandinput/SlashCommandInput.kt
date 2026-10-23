package com.ngocdevv.nativecook.blocks.inputs.slashcommandinput

import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val WIDTH = 350f

// The composer, and the whole box with the menu open under it.
private const val COMPOSER_HEIGHT = 116f
private const val HEIGHT = 392f
private const val RADIUS = 28f
private const val INSET = 22f
private const val FONT_SIZE = 18f

// Centre line of the text row.
private const val TEXT_Y = 35f
private const val TOKEN_HEIGHT = 27f

// A token's box is this much wider than its letters on each side.
private const val TOKEN_PAD = 5f

private const val MAX_CHIPS = 3
private const val CHIP_INSET = 18f
private const val CHIP_Y = 66f
private const val CHIP_HEIGHT = 34f
private const val CHIP_GAP = 8f

private const val MAX_ROWS = 12
private const val MENU_Y = 56f
private const val MENU_WIDTH = 196f
private const val MENU_PAD = 8f
private const val ROW_HEIGHT = 30f
private const val ROW_INSET = 18f
private const val HEADER_HEIGHT = 26f

// The hairline between the groups, with the space above and below it.
private const val DIVIDER_HEIGHT = 13f

// Rows fade in this long, each this much after the one before, when the menu opens.
private const val ROW_FADE = 0.14f
private const val ROW_STAGGER = 0.03f

// How long a chosen context's name takes to reach the chip row.
private const val FLY_TIME = 0.42f
private const val BLINK = 1.1f

// autoPlay period, seconds.
private const val LOOP = 9.6f

// After a touch, or once the field is left empty, the loop waits this long before it starts over.
private const val RESUME = 3f

private val Contexts = listOf("Design")
private val ContextCommands = listOf("design-file", "from-brief", "from-code", "past-output")
private val SkillCommands = listOf("contrast-check", "give-variants", "critique", "ally-check")

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun slashText(size: Float, lineHeight: Float, color: Color, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val FieldStyle = slashText(FONT_SIZE, 24f, Color(0xFFE6E6E6), FontWeight.Normal)
private val PlaceholderStyle = slashText(FONT_SIZE, 24f, Color(0xFF6F6F6F), FontWeight.Normal)
private val ChipStyle = slashText(15f, 20f, Color.White, FontWeight.Medium)
private val HeaderStyle = slashText(11f, 14f, Color(0xFF777777), FontWeight.Medium, 0.9f)
private val RowStyle = slashText(15f, 20f, Color(0xFFEDEDED), FontWeight.Normal)
private val FlyStyle = slashText(15f, 18f, Color.White, FontWeight.Medium)

private sealed interface ScriptAct {
    class Type(val letter: Char) : ScriptAct
    class Move(val by: Int) : ScriptAct
    data object Choose : ScriptAct
    data object Clear : ScriptAct
}

private class ScriptLine(val t: Float, val act: ScriptAct)

/** Seconds between letters as the loop types: uneven like a hand, the same every time. */
private val TypingRhythm = floatArrayOf(0.09f, 0.07f, 0.11f, 0.06f, 0.13f, 0.08f, 0.06f, 0.1f, 0.07f, 0.12f)

/**
 * The autoPlay loop: type up to the slash, walk the highlight down, filter the list and step back
 * up to a context and pick it, then type a second slash, filter to a skill and pick that, and clear.
 */
private val Script: List<ScriptLine> = buildList {
    fun typing(text: String, start: Float) {
        var t = start
        text.forEachIndexed { index, letter ->
            add(ScriptLine(t, ScriptAct.Type(letter)))
            t += TypingRhythm[index % TypingRhythm.size] + if (letter == ' ') 0.08f else 0f
        }
    }
    typing("I need help with /", 0.8f)
    add(ScriptLine(3.0f, ScriptAct.Move(1)))
    add(ScriptLine(3.25f, ScriptAct.Move(1)))
    typing("de", 3.7f)
    add(ScriptLine(4.25f, ScriptAct.Move(-1)))
    add(ScriptLine(4.7f, ScriptAct.Choose))
    typing("/cr", 5.5f)
    add(ScriptLine(6.6f, ScriptAct.Choose))
    add(ScriptLine(8.3f, ScriptAct.Clear))
}

/** What a frame of the loop asks the composable to do once the frame is over. */
private sealed interface Event {
    class Type(val letter: Char) : Event
    data object Choose : Event
    data object Reset : Event
}

/** A value that follows its target like a spring (mass 1, stiffness omega², damping 2·zeta·omega). */
private class Chase(var x: Float) {
    var v = 0f

    fun jump(to: Float) {
        x = to
        v = 0f
    }

    fun step(target: Float, omega: Float, zeta: Float, dt: Float) {
        v += (omega * omega * (target - x) - 2f * zeta * omega * v) * dt
        x += v * dt
    }
}

/** Moves [x] towards [target], covering the same share of the distance in the same time at any frame rate. */
private fun approach(x: Float, target: Float, rate: Float, dt: Float) = x + (target - x) * (1f - exp(-rate * dt))

/** How far a spring released [t] seconds ago has come from 0 to 1, overshoot included. */
private fun settle(t: Float, omega: Float, zeta: Float): Float {
    if (t <= 0f) return 0f
    val damped = omega * sqrt(1f - zeta * zeta)
    return 1f - exp(-zeta * omega * t) * (cos(damped * t) + zeta * omega / damped * sin(damped * t))
}

/** About how wide a chip is for its label: enough to aim the flying name at the next free place. */
private fun chipWidth(label: String) = 58f + 8.3f * label.length

private class Parsed(val prefix: String, val token: String?, val skills: List<Pair<String, String>>)

/** The token being typed (a word that starts with "/" at the end of the text) and the chosen skills, each with the text before it. */
private fun parse(text: String, skillCommands: List<String>): Parsed {
    var prefix = text
    var token: String? = null
    val skills = mutableListOf<Pair<String, String>>()
    var before = ""
    val words = text.split(" ")
    words.forEachIndexed { index, word ->
        if (word.startsWith("/")) {
            if (index == words.size - 1) {
                prefix = before
                token = word
            } else if (word.drop(1) in skillCommands) {
                skills.add(before to word)
            }
        }
        before += "$word "
    }
    return Parsed(prefix, token, skills)
}

/** What the frame loop needs from the latest composition. */
private class Inputs(val focused: Boolean, val busy: Boolean, val open: Boolean, val anchor: Float, val matches: List<Boolean>, val split: Int)

/** Per-frame state: the menu, its rows and everything that eases after them. */
private class Sim {
    var time = 0f
    var clock = 0f

    /** The next line of the autoPlay script. */
    var step = 0
    var looping = false

    /** Seconds the loop still waits after a touch. */
    var hold = 0f

    /** The text in the field is the loop's, and what that text is. */
    var scripted = false
    var text = ""
    var lastEdit = -10f

    var open = false
    var openAt = 0f

    /** Left edge of the menu; it stays where it was while the menu closes. */
    var menuX = 0f

    /** The highlighted command. */
    var highlight = 0
    var clearing = false
    val bornAt = FloatArray(MAX_CHIPS) { -10f }
    var flyAt = -10f
    var flyFrom = Offset.Zero
    var flyTo = Offset.Zero

    val menu = Chase(0f)
    val box = Chase(1f)
    var tint = 1f
    var fade = 1f
    val rows = FloatArray(MAX_ROWS) { 1f }
    val headers = floatArrayOf(1f, 1f)
    var divider = 1f
    val bar = Chase(0f)
    var barAlpha = 0f

    // Where the menu's parts are this frame, from its top edge.
    val rowY = FloatArray(MAX_ROWS)
    val headerY = FloatArray(2)
    var dividerY = 0f
    var height = 0f
    val events = mutableListOf<Event>()

    // Written last in a frame: reading it is what redraws the component.
    private val tick = mutableFloatStateOf(0f)

    /** Subscribes the caller to the next frame. */
    fun watch(): Sim {
        tick.floatValue
        return this
    }

    fun touch() {
        hold = RESUME
    }

    /** A chip left the row: the ones after it move up a place. */
    fun dropChip(at: Int) {
        for (i in at until MAX_CHIPS - 1) bornAt[i] = bornAt[i + 1]
        bornAt[MAX_CHIPS - 1] = -10f
    }

    fun move(by: Int, matches: List<Boolean>) {
        val shown = matches.indices.filter { matches[it] }
        val at = shown.indexOf(highlight)
        if (at < 0) return
        highlight = shown[(at + by).coerceIn(0, shown.size - 1)]
    }

    fun advance(dt: Float, engaged: Boolean, open: Boolean, anchor: Float, matches: List<Boolean>, split: Int, still: Boolean) {
        time += dt

        if (engaged) hold = RESUME
        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) {
                clock = 0f
                step = 0
                events.add(Event.Reset)
            }
        } else if (looping) {
            clock += dt
            if (clock >= LOOP) {
                clock -= LOOP
                step = 0
            }
            while (step < Script.size && clock >= Script[step].t) {
                when (val act = Script[step].act) {
                    is ScriptAct.Type -> events.add(Event.Type(act.letter))
                    is ScriptAct.Move -> move(act.by, matches)
                    ScriptAct.Choose -> events.add(Event.Choose)
                    ScriptAct.Clear -> clearing = true
                }
                step += 1
            }
        }

        val first = matches.take(split).contains(true)
        val second = matches.drop(split).contains(true)
        if (open && !this.open) {
            openAt = time
            box.jump(if (still) 1f else 0.5f)
            highlight = max(0, matches.indexOf(true))
            // A menu that opens shows its list as it is; only what is typed afterwards folds rows away.
            for (i in matches.indices) rows[i] = if (matches[i]) 1f else 0f
            headers[0] = if (first) 1f else 0f
            headers[1] = if (second) 1f else 0f
            divider = if (first && second) 1f else 0f
        }
        this.open = open
        if (open) menuX = anchor
        if (open && matches.getOrNull(highlight) != true) highlight = max(0, matches.indexOf(true))
        if (clearing && fade < 0.03f) {
            clearing = false
            events.add(Event.Reset)
        }

        if (still) {
            menu.x = approach(menu.x, if (open) 1f else 0f, 14f, dt)
        } else {
            menu.step(if (open) 1f else 0f, 20f, 0.65f, dt)
        }
        box.step(1f, 26f, 0.45f, dt)
        tint = approach(tint, 1f, 9f, dt)
        fade = approach(fade, if (clearing) 0f else 1f, 12f, dt)

        // While the menu is closed its rows stay as they were, so it closes in one piece.
        if (open) {
            for (i in matches.indices) rows[i] = approach(rows[i], if (matches[i]) 1f else 0f, 16f, dt)
            headers[0] = approach(headers[0], if (first) 1f else 0f, 16f, dt)
            headers[1] = approach(headers[1], if (second) 1f else 0f, 16f, dt)
            divider = approach(divider, if (first && second) 1f else 0f, 16f, dt)
        }
        var y = MENU_PAD
        for (i in matches.indices) {
            if (i == 0 || i == split) {
                if (i == split) {
                    dividerY = y
                    y += DIVIDER_HEIGHT * divider
                }
                headerY[if (i == 0) 0 else 1] = y
                y += HEADER_HEIGHT * headers[if (i == 0) 0 else 1]
            }
            rowY[i] = y
            y += ROW_HEIGHT * rows[i]
        }
        height = y + MENU_PAD

        val target = if (highlight in matches.indices) rowY[highlight] else 0f
        if (!open || still || barAlpha < 0.05f) {
            bar.jump(target)
        } else {
            bar.step(target, 24f, 0.8f, dt)
        }
        barAlpha = approach(barAlpha, if (open && (first || second)) 1f else 0f, 14f, dt)

        tick.floatValue = time
    }
}

/**
 * A prompt field with a command menu. Typing "/" boxes the character as a token and opens the menu
 * right under it; more letters filter the list, the rows that don't match folding away while a
 * highlight keeps to the first one that does. Choosing a context flies its name down into the chip
 * row and takes the token out of the text; choosing a skill leaves it in the text as a tinted token.
 *
 * @param value The field's text. Leave it out and the component keeps the text itself.
 * @param autoPlay Types a prompt, picks a context and a skill from the menu, clears and starts over, in a loop.
 * @param contexts The chips in the bottom row before anything is chosen.
 * @param contextCommands Commands that become a chip when chosen.
 * @param skillCommands Commands that stay in the text as a token when chosen.
 * @param accent Colour of the chips' dots, the skill tokens and the caret.
 * @param onCommand Called with the command's name when a row is chosen.
 * @param onSend Called with the text and the chips when the keyboard's send key is pressed.
 */
@Composable
fun SlashCommandInput(
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Ask, or type / for commands",
    contexts: List<String> = Contexts,
    contextTitle: String = "CONTEXT",
    contextCommands: List<String> = ContextCommands,
    skillTitle: String = "SKILLS",
    skillCommands: List<String> = SkillCommands,
    accent: Color = Color(0xFF4F8BFF),
    onCommand: ((String) -> Unit)? = null,
    onSend: ((text: String, chips: List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    val sim = remember { Sim() }
    var own by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var chips by remember { mutableStateOf(contexts.take(MAX_CHIPS)) }
    var flyLabel by remember { mutableStateOf("") }
    val current = value ?: own

    val commands = (contextCommands + skillCommands).take(MAX_ROWS)
    val split = min(contextCommands.size, MAX_ROWS)
    val parsed = parse(current, skillCommands)
    val open = parsed.token != null
    val query = (parsed.token ?: "/").drop(1).lowercase()
    val matches = commands.map { query.isEmpty() || it.lowercase().contains(query) }

    /** Width of a piece of the field's text, in dp. A measured text drops the spaces at its end; no-break spaces are as wide and always count. */
    fun measure(piece: String): Float =
        measurer.measure(piece.replace(' ', ' '), FieldStyle, softWrap = false, maxLines = 1).size.width / density.density

    val prefixWidth = measure(parsed.prefix)
    // Left edge of the menu: it hangs from the token, but never leaves the composer.
    val anchor = min(INSET + prefixWidth - 6f, WIDTH - MENU_WIDTH - 8f)

    fun store(text: String) {
        if (value == null) own = text
        onValueChange?.invoke(text)
    }

    fun reset() {
        store("")
        chips = contexts.take(MAX_CHIPS)
        sim.text = ""
        sim.scripted = false
        sim.clearing = false
        sim.bornAt.fill(-10f)
    }

    fun choose(index: Int, byUser: Boolean) {
        if (parsed.token == null || index !in commands.indices) return
        val name = commands[index]
        if (index < contextCommands.size) {
            // The row holds what fits: the oldest chosen context makes room for a new one.
            val kept = chips.toMutableList()
            while (
                kept.size > contexts.size &&
                kept.fold(chipWidth(name)) { sum, label -> sum + chipWidth(label) + CHIP_GAP } > WIDTH - CHIP_INSET * 2f
            ) {
                kept.removeAt(contexts.size)
                sim.dropChip(contexts.size)
            }
            if (kept.size >= MAX_CHIPS) return
            store(parsed.prefix)
            sim.text = parsed.prefix
            // The name lands about where its chip will be; the chip pops in under it.
            val slot = kept.fold(CHIP_INSET) { sum, label -> sum + chipWidth(label) + CHIP_GAP }
            flyLabel = name
            sim.flyFrom = Offset(sim.menuX + ROW_INSET, MENU_Y + sim.rowY[index] + ROW_HEIGHT / 2f)
            sim.flyTo = Offset(slot + 34f, CHIP_Y + CHIP_HEIGHT / 2f)
            sim.flyAt = sim.time
            sim.bornAt[kept.size] = sim.time + if (reducedMotion) 0f else FLY_TIME * 0.75f
            chips = kept + name
        } else {
            val text = "${parsed.prefix}/$name "
            store(text)
            sim.text = text
            sim.tint = 0f
        }
        sim.lastEdit = sim.time
        if (byUser) {
            sim.scripted = false
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onCommand?.invoke(name)
        }
    }

    fun remove(index: Int) {
        if (index !in chips.indices) return
        chips = chips.filterIndexed { at, _ -> at != index }
        sim.dropChip(index)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    // The send key picks the highlighted row while the menu is open, and sends the prompt otherwise.
    fun submit() {
        if (sim.open) {
            choose(sim.highlight, byUser = true)
            return
        }
        val sent = current.trim()
        if (sent.isEmpty()) return
        onSend?.invoke(sent, chips)
        sim.clearing = true
        focusManager.clearFocus()
    }

    /** Applies what the last frame decided, with the text and the chips of the latest composition. */
    fun drain() {
        if (sim.events.isEmpty()) return
        val events = sim.events.toList()
        sim.events.clear()
        for (event in events) {
            when (event) {
                is Event.Type -> {
                    // The loop keeps its own copy of the text, so a letter is never typed onto a stale one.
                    sim.text += event.letter
                    store(sim.text)
                    sim.scripted = true
                    sim.lastEdit = sim.time
                }
                Event.Choose -> choose(sim.highlight, byUser = false)
                Event.Reset -> reset()
            }
        }
    }

    val inputs by rememberUpdatedState(
        Inputs(focused, current.isNotEmpty() || chips.size > contexts.size, open, anchor, matches, split),
    )
    val drainNow by rememberUpdatedState<() -> Unit> { drain() }

    LaunchedEffect(auto) { sim.looping = auto }

    LaunchedEffect(reducedMotion) {
        var last = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val dt = min((frame - last) / 1e9f, 0.064f)
            last = frame
            val now = inputs
            // What the loop typed and chose is its own: only a focused field or a hand's work holds it off.
            sim.advance(dt, now.focused || (!sim.scripted && now.busy), now.open, now.anchor, now.matches, now.split, reducedMotion)
            drainNow()
        }
    }

    Box(modifier.size(WIDTH.dp, HEIGHT.dp)) {
        // A faint blue light under the composer's right corner, as on the reference's backdrop.
        Canvas(Modifier.offset(100.dp, (-20).dp).size(300.dp)) {
            scale(1f, 0.55f) {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.15f), accent.copy(alpha = 0f)), radius = size.width / 2f))
            }
        }

        Box(
            Modifier
                .size(WIDTH.dp, COMPOSER_HEIGHT.dp)
                .background(Color(0xFF1B1B1B), RoundedCornerShape(RADIUS.dp))
                .border(1.dp, Color(0xFF2C2C2C), RoundedCornerShape(RADIUS.dp)),
        )

        // The boxes behind the tokens: the one being typed, and the skills already chosen.
        parsed.token?.let { token ->
            TokenBox(
                x = INSET + prefixWidth - TOKEN_PAD,
                width = measure(token) + TOKEN_PAD * 2f,
                fill = Color(0xFF262626),
                line = Color(0xFF3A3A3A),
            ) {
                val s = sim.watch()
                scaleX = s.box.x
                scaleY = s.box.x
                alpha = (s.box.x * 2f - 1f).coerceIn(0f, 1f) * s.fade
            }
        }
        for ((before, token) in parsed.skills) {
            TokenBox(
                x = INSET + measure(before) - TOKEN_PAD,
                width = measure(token) + TOKEN_PAD * 2f,
                fill = accent.copy(alpha = 0.16f),
                line = accent.copy(alpha = 0.5f),
            ) {
                val s = sim.watch()
                alpha = s.tint * s.fade
            }
        }

        BasicTextField(
            value = current,
            onValueChange = {
                if (it != current) {
                    store(it)
                    sim.text = it
                    sim.scripted = false
                    sim.lastEdit = sim.time
                }
            },
            modifier = Modifier
                .offset(INSET.dp, (TEXT_Y - 14f).dp)
                .size((WIDTH - INSET * 2f).dp, 28.dp)
                .graphicsLayer { alpha = sim.watch().fade }
                .onFocusChanged {
                    focused = it.isFocused
                    // A tap on the field takes it over from the loop: what the loop typed and chose is cleared.
                    if (it.isFocused && sim.scripted) reset()
                },
            textStyle = FieldStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            cursorBrush = SolidColor(accent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (current.isEmpty()) BasicText(placeholder, style = PlaceholderStyle, maxLines = 1)
                    inner()
                }
            },
        )

        // The loop's own caret, right after the last letter it typed.
        Box(
            Modifier
                .offset((INSET + measure(current) + 1f).dp, (TEXT_Y - 10.5f).dp)
                .size(2.dp, 21.dp)
                .graphicsLayer {
                    val s = sim.watch()
                    val sinceEdit = s.time - s.lastEdit
                    val blink = if (sinceEdit < 0.5f) 1f else (0.5f + 3f * sin(TAU * sinceEdit / BLINK)).coerceIn(0f, 1f)
                    alpha = if (s.scripted && !focused && current.isNotEmpty()) blink * s.fade else 0f
                }
                .background(accent, RoundedCornerShape(1.dp)),
        )

        Box(
            Modifier
                .offset(CHIP_INSET.dp, CHIP_Y.dp)
                .size((WIDTH - CHIP_INSET * 2f).dp, CHIP_HEIGHT.dp)
                .clipToBounds(),
        ) {
            // The chips keep their own width; what does not fit is cut off at the row's end.
            Row(
                Modifier.wrapContentWidth(Alignment.Start, unbounded = true).height(CHIP_HEIGHT.dp),
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chips.forEachIndexed { index, label ->
                    Chip(sim, index, label, fixed = index < contexts.size, still = reducedMotion, accent = accent) {
                        sim.touch()
                        remove(index)
                    }
                }
            }
        }

        Menu(
            sim = sim,
            still = reducedMotion,
            commands = commands,
            titles = listOf(contextTitle, skillTitle),
            matches = matches,
            enabled = open,
        ) { index ->
            sim.touch()
            sim.highlight = index
            choose(index, byUser = true)
        }

        // The chosen context's name, on its way from the menu to the chip row.
        BasicText(
            flyLabel,
            style = FlyStyle,
            maxLines = 1,
            modifier = Modifier
                .offset {
                    val s = sim.watch()
                    val p = ((s.time - s.flyAt) / FLY_TIME).coerceIn(0f, 1f)
                    // Sideways first, then down: the name swings out of the menu and drops into the row.
                    val across = 1f - (1f - p).pow(3)
                    val down = p * p * (3f - 2f * p)
                    IntOffset(
                        (s.flyFrom.x + (s.flyTo.x - s.flyFrom.x) * across).dp.roundToPx(),
                        (s.flyFrom.y + (s.flyTo.y - s.flyFrom.y) * down - 9f).dp.roundToPx(),
                    )
                }
                .graphicsLayer {
                    val s = sim.watch()
                    val p = (s.time - s.flyAt) / FLY_TIME
                    // Under reduced motion nothing flies: the chip simply appears in the row.
                    alpha = if (reducedMotion || p < 0f || p >= 1f) 0f else min(1f, (1f - p) / 0.25f)
                },
        )
    }
}

/** The rounded box behind a token of the text; [layer] sets its scale and opacity each frame. */
@Composable
private fun TokenBox(x: Float, width: Float, fill: Color, line: Color, layer: GraphicsLayerScope.() -> Unit) {
    Box(
        Modifier
            .offset(x.dp, (TEXT_Y - TOKEN_HEIGHT / 2f).dp)
            .size(width.dp, TOKEN_HEIGHT.dp)
            .graphicsLayer(layer)
            .background(fill, RoundedCornerShape(6.dp))
            .border(1.dp, line, RoundedCornerShape(6.dp)),
    )
}

@Composable
private fun Chip(sim: Sim, index: Int, label: String, fixed: Boolean, still: Boolean, accent: Color, onRemove: () -> Unit) {
    Row(
        Modifier
            .graphicsLayer {
                val s = sim.watch()
                val pop = if (still) 1f else settle(s.time - s.bornAt[index], 18f, 0.5f)
                scaleX = 0.6f + 0.4f * pop
                scaleY = 0.6f + 0.4f * pop
                // One of the chips the row starts with stays when the rest is cleared.
                alpha = (if (s.time >= s.bornAt[index]) (pop * 2f).coerceIn(0f, 1f) else 0f) * (if (fixed) 1f else s.fade)
            }
            .height(CHIP_HEIGHT.dp)
            .background(Color(0xFF232A3A), CircleShape)
            .border(1.dp, Color(0xFF2F3A52), CircleShape)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onRemove)
            .semantics { contentDescription = "Remove $label" }
            .padding(start = 7.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val u = 1.dp.toPx()
            drawCircle(
                Brush.linearGradient(
                    listOf(Color(0xFF86B1FF), accent, Color(0xFF3566D6)),
                    start = Offset(4f * u, 0f),
                    end = Offset(16f * u, 20f * u),
                ),
            )
            val check = Path().apply {
                moveTo(6f * u, 10.4f * u)
                lineTo(8.9f * u, 13.2f * u)
                lineTo(14.2f * u, 7.2f * u)
            }
            drawPath(check, Color.White, style = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        BasicText(label, style = ChipStyle, maxLines = 1)
        Canvas(Modifier.padding(start = 3.dp).size(9.dp)) {
            val a = 1.5.dp.toPx()
            val b = 7.5.dp.toPx()
            val grey = Color(0xFF9AA3B8)
            drawLine(grey, Offset(a, a), Offset(b, b), 1.3.dp.toPx(), StrokeCap.Round)
            drawLine(grey, Offset(b, a), Offset(a, b), 1.3.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** The command menu: two titled groups, a hairline between them, and the highlight behind one row. */
@Composable
private fun Menu(
    sim: Sim,
    still: Boolean,
    commands: List<String>,
    titles: List<String>,
    matches: List<Boolean>,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.45f), sigma = 18f) }
    val outline = remember { Path() }
    // The box is as tall as the whole list; the menu drawn in it is as tall as the rows that show.
    val full = MENU_PAD * 2f + HEADER_HEIGHT * 2f + DIVIDER_HEIGHT + ROW_HEIGHT * commands.size

    Box(
        Modifier
            .offset { IntOffset(sim.watch().menuX.dp.roundToPx(), MENU_Y.dp.roundToPx()) }
            .size(MENU_WIDTH.dp, full.dp)
            .graphicsLayer {
                val s = sim.watch()
                // It grows out of the token above its top-left corner.
                val grown = if (still) 1f else 0.7f + 0.3f * s.menu.x
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = grown
                scaleY = grown
                alpha = min(1f, s.menu.x.coerceIn(0f, 1f) * 1.6f)
                // The shadow reaches past the menu, so the fade must not clip the layer to its bounds.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawWithContent {
                val s = sim.watch()
                val tall = max(MENU_PAD * 2f, s.height)
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 12f, MENU_WIDTH, tall, 16f) }
                }
                val radius = CornerRadius(16.dp.toPx())
                val body = Size(size.width, tall.dp.toPx())
                drawRoundRect(Color(0xFF1E1E1E), size = body, cornerRadius = radius)
                outline.rewind()
                outline.addRoundRect(RoundRect(Rect(Offset.Zero, body), radius))
                clipPath(outline) { this@drawWithContent.drawContent() }
                val hair = 1.dp.toPx()
                drawRoundRect(
                    Color(0xFF333333),
                    topLeft = Offset(hair / 2f, hair / 2f),
                    size = Size(body.width - hair, body.height - hair),
                    cornerRadius = CornerRadius(radius.x - hair / 2f),
                    style = Stroke(hair),
                )
            },
    ) {
        Box(
            Modifier
                .offset { IntOffset(6.dp.roundToPx(), sim.watch().bar.x.dp.roundToPx()) }
                .size((MENU_WIDTH - 12f).dp, ROW_HEIGHT.dp)
                .graphicsLayer { alpha = sim.watch().barAlpha }
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
        )

        titles.forEachIndexed { group, title ->
            Box(
                Modifier
                    .offset { IntOffset(ROW_INSET.dp.roundToPx(), sim.watch().headerY[group].dp.roundToPx()) }
                    .size((MENU_WIDTH - ROW_INSET * 2f).dp, HEADER_HEIGHT.dp)
                    .graphicsLayer {
                        val shown = sim.watch().headers[group]
                        alpha = shown * shown
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(title, style = HeaderStyle, maxLines = 1)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(0, (sim.watch().dividerY + DIVIDER_HEIGHT / 2f).dp.roundToPx()) }
                .size(MENU_WIDTH.dp, 1.dp)
                .graphicsLayer {
                    val shown = sim.watch().divider
                    alpha = shown * shown
                }
                .background(Color(0xFF333333)),
        )

        commands.forEachIndexed { index, name ->
            // Rows fade in one after another as the menu opens.
            val order = matches.take(index).count { it }
            Box(
                Modifier
                    .offset { IntOffset(0, sim.watch().rowY[index].dp.roundToPx()) }
                    .size(MENU_WIDTH.dp, ROW_HEIGHT.dp)
                    .graphicsLayer {
                        val s = sim.watch()
                        val shown = s.rows[index] * ((s.time - s.openAt - order * ROW_STAGGER) / ROW_FADE).coerceIn(0f, 1f)
                        alpha = shown * shown
                    }
                    // A row that folds away is cut off at its shrinking height, so it never runs into the next one.
                    .drawWithContent {
                        clipRect(bottom = size.height * sim.watch().rows[index]) { this@drawWithContent.drawContent() }
                    }
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = matches[index] && enabled,
                        role = Role.Button,
                    ) { onPick(index) }
                    .padding(horizontal = ROW_INSET.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicText(name, style = RowStyle, maxLines = 1)
            }
        }
    }
}
