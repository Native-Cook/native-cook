package com.ngocdevv.nativecook.blocks.inputs.ghostcompletefield

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import kotlin.math.max
import kotlin.math.min

/*
 Ghost Complete Field: a reply in Postbox, a mail app that finishes your sentences.

 The loop types without focusing anything (no keyboard). In Cc it types "theo@", two domain chips
 pop beside the caret and the tapped one stretches into the field. In the body it types "Hi Mara,
 tha": the rest of the phrase appears behind the caret in grey, word by word out of a blur, and a
 tap on the keycap chip fills it with ink from left to right while the caret jumps to its end. A
 few more letters, a second suggestion, and this time a swipe to the right accepts it. Then the
 reply clears.

 A real tap focuses the real fields and the same things happen from what is typed.
*/

private val PAGE = Color(0xFFFBFBFC)
private val HAIRLINE = Color(0xFFE6E7EB)
private val INK = GhostColors.ink
private val MUTED = Color(0xFF8A90A0)
private val ACCENT = GhostColors.accent
private const val FONT_SIZE = 17f
private const val BODY_SIZE = 18f
private const val LINE = 26f
private const val ROW_LINE = 22f
private const val LABEL_W = 36f
private const val CHIPS_LEFT = 12f
private const val DOT_SIZE = 44f
private const val QUOTE_INTRO = "Notes from today's walkthrough are attached. Three things to settle before Friday:"

private class Geo(val W: Float, val H: Float, top: Float, bottom: Float) {
    val compact = H < 740f
    val margin = max(20f, (W - 440f) / 2f)
    val width = W - margin * 2f
    val navTop = top + if (compact) 2f else 6f
    val rowH = if (compact) 42f else 46f
    val rowsTop = navTop + 44f + if (compact) 2f else 8f
    val ccTop = rowsTop + rowH
    val ccX = margin + LABEL_W
    val ccWidth = width - LABEL_W
    val bodyTop = rowsTop + rowH * 3f + if (compact) 12f else 16f
    val bodyH = LINE * if (compact) 4f else 6f
    val toolbarTop = H - bottom - 50f
    val quoteTop = bodyTop + bodyH + if (compact) 8f else 14f
    val quoteH = max(0f, toolbarTop - 12f - quoteTop)
}

private fun reducedMotion(context: Context): Boolean {
    val resolver = context.contentResolver
    return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
        Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
}

/**
 The screen's state and behaviour. A class, not local functions: nothing here can read a value
 captured by an earlier composition.
*/
private class ReplyController(
    private val font: GhostFont,
    bodyFont: GhostFont,
    width: Float,
    ccWidth: Float,
    val auto: Boolean,
    val still: Boolean,
    val reduced: Boolean,
) {
    val bodyAuto = makeGhostLayout(bodyFont, BODY_FULL, BODY_GHOSTS, width, LINE)
    val ccAuto = makeGhostLayout(font, CC_FULL, CC_GHOSTS, ccWidth, ROW_LINE, false)
    private val autoStretch = font.width(CC_DOMAIN) + 8f

    val bodyPose = GhostPose()
    val ccPose = GhostPose()
    val chipsPose = ChipsPose()
    val dot = DotPose()
    val subject = TextFieldState("Re: Tuesday notes")
    private val subjectStart = "Re: Tuesday notes"
    private var subjectText = subjectStart

    /** 0 = the loop (or rest), 1 = a person drives, 2 = clearing before the loop restarts. */
    var mode by mutableIntStateOf(0)
        private set
    var chips by mutableStateOf(CC_CHIPS)
        private set

    private val sendValue = mutableFloatStateOf(0f)
    val send: Float get() = sendValue.floatValue
    private val chipsXValue = mutableFloatStateOf(0f)
    val chipsX: Float get() = chipsXValue.floatValue
    private val stretchValue = mutableFloatStateOf(80f)
    val stretch: Float get() = stretchValue.floatValue

    private var now = 0f
    private var clock = 0f
    private var lastTouch = 0f
    private var returnAt = 0f
    private var focusCount = 0
    private var picking = false
    private var chipCount = CC_CHIPS.size
    private val uShow = Tween()
    private val uPick = Tween(-1f)
    private val uTake = Tween()
    private val uStretch = Tween(80f)
    private val uSend = Tween()
    private val later = ArrayList<Pair<Float, () -> Unit>>()

    val body = GhostController(
        font = bodyFont,
        width = width,
        lineHeight = LINE,
        suggest = ::suggestPhrase,
        multiline = true,
        still = reduced,
        onChange = { text -> uSend.to(if (text.trim().isNotEmpty()) 1f else 0f, 220f) },
    )
    val cc = GhostController(
        font = font,
        width = ccWidth,
        lineHeight = ROW_LINE,
        keycap = false,
        still = reduced,
        onChange = { text -> onCcChange(text) },
    )

    fun attach(view: View) {
        body.view = view
        cc.view = view
    }

    fun bodyLayout(): GhostLayout = if (mode == 0) bodyAuto else body.layout

    fun ccLayout(): GhostLayout = if (mode == 0) ccAuto else cc.layout

    private fun onCcChange(text: String) {
        if (picking) return
        val next = suggestDomains(text)
        if (next.isNotEmpty() && chipCount == 0) {
            uShow.set(0f)
            uShow.to(2f, 2000f)
        }
        if (next.isEmpty()) uShow.set(0f)
        chipCount = next.size
        if (chips != next) chips = next
    }

    private fun resetForm() {
        body.adopt("", false)
        cc.adopt("", false)
        picking = false
        chipCount = CC_CHIPS.size
        chips = CC_CHIPS
        subjectText = subjectStart
        subject.setTextAndPlaceCursorAtEnd(subjectStart)
    }

    // ---- The frame ---------------------------------------------------------------------------
    fun step(dt: Float) {
        now += dt
        body.step(dt)
        cc.step(dt)
        uShow.step(dt)
        uPick.step(dt)
        uTake.step(dt)
        uStretch.step(dt)
        uSend.step(dt)
        if (later.isNotEmpty()) {
            val due = later.filter { it.first <= now }
            later.removeAll(due)
            due.forEach { it.second() }
        }

        when (mode) {
            0 -> if (auto) clock = (clock + dt) % LOOP
            1 -> if (focusCount == 0 && now - lastTouch > IDLE) {
                mode = 2
                returnAt = now
                val d = if (reduced) 0f else RETURN_LEN * 1000f * 0.6f
                body.ink.to(0f, d)
                cc.ink.to(0f, d)
                uSend.to(0f, d)
                uShow.set(0f)
            }
            else -> if (now - returnAt > RETURN_LEN) {
                uPick.set(-1f)
                uTake.set(0f)
                clock = 0f
                mode = 0
                resetForm()
            }
        }
        publish()
    }

    private fun publish() {
        if (mode == 0) {
            val t = if (still) T_FILLED else clock
            autoBody(t, bodyPose)
            if (still) bodyPose.caret = 0f
            autoCc(t, ccPose)
            if (auto) {
                autoChips(clock, chipsPose)
                autoDot(clock, dot)
            } else {
                chipsPose.show = 0f
                chipsPose.pick = -1
                chipsPose.take = 0f
                dot.alpha = 0f
            }
            stretchValue.floatValue = autoStretch
            sendValue.floatValue = autoSend(t)
        } else {
            body.publish(bodyPose)
            cc.publish(ccPose)
            chipsPose.show = uShow.v
            chipsPose.pick = uPick.v.toInt()
            chipsPose.take = uTake.v
            dot.alpha = 0f
            stretchValue.floatValue = uStretch.v
            sendValue.floatValue = uSend.v
        }
        // Where the chips start: just past the typed part of the address.
        val L = ccLayout()
        val run = ccPose.run
        val stop = if (run >= 0 && run < L.runs.size) L.runs[run].start else ccPose.n
        chipsXValue.floatValue = L.cx[min(L.cx.size - 1, max(0, stop))] + CHIPS_LEFT
    }

    fun touch() {
        lastTouch = now
    }

    /** Hands the loop's current text to the person, so taking over never jumps. */
    fun enterUser() {
        touch()
        val m = mode
        if (m == 1) return
        if (m == 0) {
            val t = if (still) T_FILLED else if (auto) clock else 0f
            val address = CC_FULL.substring(0, ccCount(t))
            val message = BODY_FULL.substring(0, bodyCount(t))
            cc.adopt(address, false)
            body.adopt(message)
            val next = suggestDomains(address)
            chipCount = next.size
            chips = next
            uShow.set(if (next.isNotEmpty()) 2f else 0f)
            uPick.set(-1f)
            uTake.set(0f)
            uSend.set(if (message.isNotEmpty()) 1f else 0f)
        } else {
            // Caught while clearing: what was written comes back.
            body.ink.to(1f, 150f)
            cc.ink.to(1f, 150f)
        }
        mode = 1
    }

    fun onFocus() {
        enterUser()
        focusCount += 1
    }

    fun onBlur() {
        focusCount = max(0, focusCount - 1)
        touch()
    }

    fun onSubject(text: String) {
        if (text == subjectText) return
        subjectText = text
        touch()
    }

    /** A domain chip was tapped: it stretches into the field and its text takes ink there. */
    fun pick(index: Int) {
        enterUser()
        val address = cc.typed
        val at = address.indexOf('@')
        val label = suggestDomains(address).getOrNull(index)
        if (at < 0 || label == null || picking) return
        val rest = label.substring(address.length - at - 1)
        if (rest.isEmpty()) return
        picking = true
        uStretch.set(font.width(rest) + 8f)
        cc.offer(rest, true)
        cc.accept(0f, true)
        uPick.set(index.toFloat())
        uTake.set(0f)
        uTake.to(1f, if (reduced) 0f else PICK_MS)
        later.add(
            now + ((if (reduced) 0f else PICK_MS) + 60f) / 1000f to {
                picking = false
                chipCount = 0
                chips = emptyList()
                uShow.set(0f)
                uPick.set(-1f)
                uTake.set(0f)
            },
        )
    }

    fun onSend(focusManager: FocusManager, view: View) {
        enterUser()
        focusManager.clearFocus()
        lightTap(view)
        // A sent reply clears at once: once the blur has passed, let the idle return run.
        later.add(now + 0.12f to { lastTouch = now - IDLE - 0.01f })
    }
}

private val PlainStyle = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
fun GhostCompleteField(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val reduced = remember { reducedMotion(context) }
    // a light page: dark status bar icons, as the RN block's <StatusBar style="dark" />
    BlockSystemBars(darkIcons = true)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    val font = remember(density.density) { GhostFont(FONT_SIZE, density.density) }
    val bodyFont = remember(density.density) { GhostFont(BODY_SIZE, density.density) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PAGE)
            .clipToBounds(),
    ) {
        val g = remember(maxWidth, maxHeight, top, bottom) { Geo(maxWidth.value, maxHeight.value, top, bottom) }
        val c = remember(font, bodyFont, g.width, g.ccWidth, autoPlay, reduced) {
            ReplyController(font, bodyFont, g.width, g.ccWidth, autoPlay && !reduced, autoPlay && reduced, reduced)
        }
        LaunchedEffect(c, view) { c.attach(view) }
        LaunchedEffect(c) {
            var last = withFrameNanos { it }
            while (true) {
                val nanos = withFrameNanos { it }
                c.step(((nanos - last) / 1e9f).coerceIn(0f, 0.064f))
                last = nanos
            }
        }
        LaunchedEffect(c) {
            snapshotFlow { c.subject.text.toString() }.collect { c.onSubject(it) }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(focusManager, g) {
                    detectTapGestures { tap ->
                        // A tap on a field belongs to that field; anywhere else closes the keyboard.
                        val y = tap.y / density.density
                        val inFields = y >= g.ccTop && y < g.bodyTop + g.bodyH
                        if (!inFields) focusManager.clearFocus()
                    }
                },
        ) {
            NavBar(g, c, focusManager, view)
            ToRow(g)
            Row(
                Modifier
                    .offset(g.margin.dp, g.ccTop.dp)
                    .size(g.width.dp, g.rowH.dp)
                    .hairlineBottom(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("Cc:", style = PlainStyle.copy(fontSize = 15.sp, color = MUTED))
            }
            Box(Modifier.offset(g.ccX.dp, (g.ccTop + (g.rowH - ROW_LINE) / 2f).dp)) {
                GhostField(
                    pose = c.ccPose,
                    layout = { c.ccLayout() },
                    controller = c.cc,
                    font = font,
                    width = g.ccWidth.dp,
                    height = ROW_LINE.dp,
                    lineHeight = ROW_LINE,
                    placeholder = "Add people",
                    keycap = false,
                    ghostColor = Color(217, 71, 43, 0),
                    maxLength = 36,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    onTouch = { c.enterUser() },
                    onFocus = { c.onFocus() },
                    onBlur = { c.onBlur() },
                )
                DomainChips(c)
            }

            SubjectRow(g, c)

            Box(Modifier.offset(g.margin.dp, g.bodyTop.dp)) {
                GhostField(
                    pose = c.bodyPose,
                    layout = { c.bodyLayout() },
                    controller = c.body,
                    font = bodyFont,
                    width = g.width.dp,
                    height = g.bodyH.dp,
                    lineHeight = LINE,
                    placeholder = "Write your reply",
                    multiline = true,
                    maxLength = 220,
                    onTouch = { c.enterUser() },
                    onFocus = { c.onFocus() },
                    onBlur = { c.onBlur() },
                )
            }

            Quote(g)
            Toolbar(g)
        }

        GhostFinger(c, g)
    }
}

private fun Modifier.hairlineBottom() = drawBehind {
    val y = size.height - 0.25f * density
    drawLine(HAIRLINE, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f * density)
}

@Composable
private fun NavBar(g: Geo, c: ReplyController, focusManager: FocusManager, view: View) {
    Row(
        Modifier
            .offset(g.margin.dp, g.navTop.dp)
            .size(g.width.dp, 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("Cancel", Modifier.width(70.dp), style = PlainStyle.copy(fontSize = FONT_SIZE.sp, color = ACCENT))
        BasicText(
            "Reply",
            style = PlainStyle.copy(
                fontSize = FONT_SIZE.sp,
                fontWeight = FontWeight.SemiBold,
                color = INK,
                letterSpacing = (-0.2).sp,
            ),
        )
        Box(
            Modifier
                .padding(start = 38.dp)
                .size(32.dp)
                .dropShadow(CircleShape, Shadow(radius = 2.dp, color = Color(21, 26, 36).copy(alpha = 0.14f), offset = DpOffset(0.dp, 1.dp)))
                .drawBehind { drawCircle(lerp(Color(0xFFD5D8E0), ACCENT, c.send)) }
                .semantics {
                    role = Role.Button
                    contentDescription = "Send"
                }
                .pointerInput(c) { detectTapGestures { c.onSend(focusManager, view) } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ToRow(g: Geo) {
    Row(
        Modifier
            .offset(g.margin.dp, g.rowsTop.dp)
            .size(g.width.dp, g.rowH.dp)
            .hairlineBottom(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("To:", Modifier.padding(end = 8.dp), style = PlainStyle.copy(fontSize = 15.sp, color = MUTED))
        Row(
            Modifier
                .height(28.dp)
                .background(Color(0xFFEEF0F4), RoundedCornerShape(14.dp))
                .padding(start = 4.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp).background(Color(0xFF3E4A63), CircleShape), contentAlignment = Alignment.Center) {
                BasicText(
                    "MO",
                    style = PlainStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF4F5F8)),
                )
            }
            BasicText(
                "Mara Okafor",
                style = PlainStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = INK),
            )
        }
    }
}

@Composable
private fun SubjectRow(g: Geo, c: ReplyController) {
    val focused = remember { booleanArrayOf(false) }
    Row(
        Modifier
            .offset(g.margin.dp, (g.rowsTop + g.rowH * 2f).dp)
            .size(g.width.dp, g.rowH.dp)
            .hairlineBottom(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("Subject:", Modifier.padding(end = 8.dp), style = PlainStyle.copy(fontSize = 15.sp, color = MUTED))
        BasicTextField(
            state = c.subject,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus ->
                    if (focus.isFocused != focused[0]) {
                        focused[0] = focus.isFocused
                        if (focus.isFocused) c.onFocus() else c.onBlur()
                    }
                },
            textStyle = PlainStyle.copy(
                fontSize = FONT_SIZE.sp,
                fontWeight = FontWeight.SemiBold,
                color = INK,
                letterSpacing = (-0.2).sp,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(ACCENT),
        )
    }
}

/**
 The suggested mail domains. They pop in beside the caret; the picked one slides to the caret and
 its pill stretches to the width the domain takes in the field, then lets go of it, while the
 field inks the same letters in underneath.
*/
@Composable
private fun DomainChips(c: ReplyController) {
    Row(
        Modifier
            .offset(y = ((ROW_LINE - 26f) / 2f).dp)
            .height(26.dp)
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .graphicsLayer {
                translationX = if (c.chipsPose.show > 0f) c.chipsX * density else -4000f * density
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        c.chips.forEachIndexed { index, label ->
            key(label) {
                DomainChip(index, label, c)
            }
        }
    }
}

@Composable
private fun DomainChip(index: Int, label: String, c: ReplyController) {
    // Where the chip stands in its row and how wide it is, dp; read by the layer lambdas.
    val box = remember { floatArrayOf(index * 96f, 88f) }
    val density = LocalDensity.current.density
    val pose = c.chipsPose
    val pill = remember { RoundedCornerShape(13.dp) }
    Box(
        Modifier
            .height(26.dp)
            .onPlaced { box[0] = it.positionInParent().x / density }
            .onSizeChanged { box[1] = it.width / density }
            .graphicsLayer {
                val pop = easeOutBack(clamp01((pose.show - index * CHIP_GAP) / (CHIP_MS / 1000f)), 1.5f)
                val picked = pose.pick == index
                val other = pose.pick >= 0 && !picked
                val leave = if (other) 1f - clamp01(pose.take * 3f) else 1f - seg(pose.take, 0.6f, 0.92f)
                alpha = if (pose.show <= 0f) 0f else clamp01(pop * 2f) * leave
                translationX = when {
                    picked -> -(box[0] + CHIPS_LEFT - 2f) * easeInOutCubic(seg(pose.take, 0f, 0.55f)) * this.density
                    other -> 10f * pose.take * this.density
                    else -> 0f
                }
                val s = 0.8f + 0.2f * pop
                scaleX = s
                scaleY = s
            }
            .semantics {
                role = Role.Button
                contentDescription = "Use $label"
            }
            .pointerInput(c, index) { detectTapGestures { c.pick(index) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    if (pose.pick == index) {
                        val s = easeOutBack(seg(pose.take, 0.05f, 0.6f), 1.4f)
                        scaleX = 1f + (c.stretch / max(1f, box[1]) - 1f) * s
                        scaleY = 1f - 0.14f * s
                    } else {
                        scaleX = 1f
                        scaleY = 1f
                    }
                }
                .background(Color(0xFFFCE9E3), pill)
                .border(0.5.dp, Color(0xFFF3C9BC), pill),
        )
        BasicText(
            text = label,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .graphicsLayer {
                    alpha = if (pose.pick == index) 1f - seg(pose.take, 0.08f, 0.38f) else 1f
                },
            style = PlainStyle.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = Color(0xFFB43A22)),
            maxLines = 1,
        )
    }
}

// ---- The message being answered -----------------------------------------------------------------------------

@Composable
private fun Quote(g: Geo) {
    val line = PlainStyle.copy(fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFF5B6170))
    Box(
        Modifier
            .offset(g.margin.dp, g.quoteTop.dp)
            .size(g.width.dp, g.quoteH.dp)
            .clipToBounds(),
    ) {
        Column(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).width(g.width.dp)) {
            BasicText(
                "On Tue 12 May at 16:42, Mara Okafor wrote:",
                Modifier.padding(bottom = 8.dp),
                style = PlainStyle.copy(fontSize = 13.sp, lineHeight = 18.sp, color = MUTED),
                maxLines = 1,
            )
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Color(0xFFE1E4EA), RoundedCornerShape(1.5.dp)))
                Column(
                    Modifier.weight(1f).padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    FileCard()
                    BasicText("Hi Ines,", style = line)
                    BasicText(QUOTE_INTRO, style = line)
                    BasicText("1. Move the launch review to Thursday", style = line)
                    BasicText("2. Pick one of the two onboarding flows", style = line)
                    BasicText("3. Decide who owns the pricing page", style = line)
                    BasicText("Shout if I missed anything.", style = line)
                    BasicText("Mara", style = line)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(44.dp)
                .background(Brush.verticalGradient(listOf(PAGE.copy(alpha = 0f), PAGE))),
        )
    }
}

@Composable
private fun FileCard() {
    val shape = remember { RoundedCornerShape(14.dp) }
    Row(
        Modifier
            .padding(bottom = 4.dp)
            .fillMaxWidth()
            .height(56.dp)
            .dropShadow(shape, Shadow(radius = 2.dp, color = Color(21, 26, 36).copy(alpha = 0.05f), offset = DpOffset(0.dp, 1.dp)))
            .dropShadow(shape, Shadow(radius = 20.dp, color = Color(21, 26, 36).copy(alpha = 0.05f), offset = DpOffset(0.dp, 8.dp)))
            .background(Color.White, shape)
            .border(0.5.dp, HAIRLINE, shape)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp, 36.dp).background(Color(0xFFFCE9E3), RoundedCornerShape(6.dp))) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .background(Color(0xFFF3C9BC), RoundedCornerShape(topEnd = 6.dp, bottomStart = 4.dp)),
            )
            Column(
                Modifier.padding(start = 6.dp, top = 13.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                listOf(14, 18, 10).forEach { w ->
                    Box(
                        Modifier
                            .size(w.dp, 2.dp)
                            .background(Color(0xFFD9472B).copy(alpha = 0.55f), RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
        Column {
            BasicText(
                "Tuesday notes.pdf",
                style = PlainStyle.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = INK,
                    letterSpacing = (-0.1).sp,
                ),
            )
            Spacer(Modifier.height(1.dp))
            BasicText(
                "3 pages · 240 KB",
                style = PlainStyle.copy(fontSize = 13.sp, color = MUTED, fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Composable
private fun Toolbar(g: Geo) {
    Row(
        Modifier
            .offset(g.margin.dp, g.toolbarTop.dp)
            .size(g.width.dp, 50.dp)
            .drawBehind {
                drawLine(HAIRLINE, Offset.Zero, Offset(size.width, 0f), strokeWidth = 0.5f * density)
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = MUTED, modifier = Modifier.size(20.dp))
            Icon(Icons.Outlined.Image, contentDescription = null, tint = MUTED, modifier = Modifier.size(20.dp))
            Icon(Icons.Outlined.TextFormat, contentDescription = null, tint = MUTED, modifier = Modifier.size(20.dp))
        }
        BasicText("Swipe right or tap Tab to accept", style = PlainStyle.copy(fontSize = 13.sp, color = MUTED))
    }
}

/** A soft touch dot: it taps the Cc field, a chip, the body and the keycap, then swipes right. */
@Composable
private fun GhostFinger(c: ReplyController, g: Geo) {
    val targets = remember(c, g) {
        val r0 = c.bodyAuto.runs[0]
        val r1 = c.bodyAuto.runs[1]
        listOf(
            Offset(g.ccX + 48f, g.ccTop + g.rowH / 2f),
            Offset(g.ccX + c.ccAuto.cx[CC_TYPED.length] + CHIPS_LEFT + 44f, g.ccTop + g.rowH / 2f),
            Offset(g.margin + 72f, g.bodyTop + LINE / 2f),
            Offset(g.margin + r0.chipX + KEYCAP_W / 2f, g.bodyTop + r0.chipY + LINE / 2f),
            // The swipe runs along the suggestion's last line, wherever the wrap put it.
            Offset(g.margin + g.width * 0.2f, g.bodyTop + c.bodyAuto.cy[r1.end] + LINE / 2f),
        )
    }
    val limit = g.W - 30f
    val pose = c.dot
    Box(
        Modifier
            .size(DOT_SIZE.dp)
            .graphicsLayer {
                val at = targets.getOrElse(pose.target) { targets[0] }
                val x = min(limit, at.x + pose.travel * DEMO_SWIPE)
                alpha = pose.alpha
                translationX = (x - DOT_SIZE / 2f) * density
                translationY = (at.y - DOT_SIZE / 2f + if (pose.target == 4) 0f else (1f - pose.alpha) * 8f) * density
                scaleX = pose.scale
                scaleY = pose.scale
            }
            .dropShadow(CircleShape, Shadow(radius = 12.dp, color = Color(21, 26, 36).copy(alpha = 0.14f), offset = DpOffset(0.dp, 4.dp)))
            .background(Color(21, 26, 36).copy(alpha = 0.14f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
    )
}
