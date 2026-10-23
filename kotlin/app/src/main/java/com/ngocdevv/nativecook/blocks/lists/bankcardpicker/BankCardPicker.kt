package com.ngocdevv.nativecook.blocks.lists.bankcardpicker

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix as AndroidMatrix
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val LOOP_MS = 12_000L

data class BankCardPickerSelection(
    val designId: String,
    val designLabel: String,
    val planId: String,
    val planLabel: String,
) {
    val summary: String
        get() = "Bloom Card preview\nDesign: $designLabel\nPlan: $planLabel"
}

private val BankCardPickerRegular = FontFamily(Font(R.font.bankcardpicker_inter_regular, FontWeight.Normal))
private val BankCardPickerMedium = FontFamily(Font(R.font.bankcardpicker_inter_medium, FontWeight.Medium))
private val BankCardPickerSemibold = FontFamily(Font(R.font.bankcardpicker_inter_semibold, FontWeight.SemiBold))

@Composable
fun BankCardPicker(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    freezeAt: Long? = null,
    onBack: (() -> Unit)? = null,
    onShare: ((BankCardPickerSelection) -> Unit)? = null,
    onGetCard: ((BankCardPickerSelection) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val swap = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val pill = remember { Animatable(0f) }
    var design by remember { mutableIntStateOf(0) }
    var plan by remember { mutableIntStateOf(0) }
    var slotA by remember { mutableIntStateOf(0) }
    var slotB by remember { mutableIntStateOf(0) }
    var scripted by remember(autoPlay, reduced, freezeAt) { mutableStateOf(autoPlay && !reduced && freezeAt == null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var clockMs by remember { mutableLongStateOf(freezeAt ?: 0L) }
    var contactActive by remember { mutableStateOf(false) }
    var resumeGeneration by remember { mutableIntStateOf(0) }
    var takeoverSnapshot by remember { mutableStateOf<BankCardPickerSnapshot?>(null) }
    var modalOpen by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<BankCardPickerSelection?>(null) }

    fun manualSnapshot() = BankCardPickerSnapshot(
        design = design,
        plan = plan,
        slotA = slotA,
        slotB = slotB,
        swap = swap.value,
        ring = ring.value,
        pill = pill.value,
    )

    suspend fun captureScript() {
        if (!scripted) return
        val snapshot = bankCardPickerSnapshotAt(clockMs.toFloat(), reduced = false)
        takeoverSnapshot = snapshot
        scripted = false
        design = snapshot.design
        plan = snapshot.plan
        slotA = snapshot.slotA
        slotB = snapshot.slotB
        swap.snapTo(snapshot.swap)
        ring.snapTo(snapshot.ring)
        pill.snapTo(snapshot.pill)
        takeoverSnapshot = null
    }

    fun selectDesign(index: Int, byFinger: Boolean) {
        if (index == design) return
        scope.launch {
            captureScript()
            val toB = swap.value < 0.5f
            if (toB) slotB = index else slotA = index
            design = index
            val target = if (toB) 1f else 0f
            launch {
                swap.animateTo(
                    target,
                    if (reduced) tween(200) else tween(420, easing = BankCardPickerEaseInOut),
                )
            }
            launch {
                ring.animateTo(
                    index.toFloat(),
                    if (reduced) tween(160) else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                )
            }
            if (byFinger) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun selectPlan(index: Int, byFinger: Boolean) {
        if (index == plan) return
        scope.launch {
            captureScript()
            plan = index
            pill.animateTo(
                index.toFloat(),
                if (reduced) tween(160) else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            )
            if (byFinger) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    LaunchedEffect(scripted) {
        if (!scripted) return@LaunchedEffect
        while (scripted) {
            withFrameNanos { frame ->
                if (startedAt == 0L) startedAt = frame
                clockMs = ((frame - startedAt) / 1_000_000L) % LOOP_MS
            }
        }
    }

    LaunchedEffect(resumeGeneration) {
        if (!autoPlay || reduced || freezeAt != null || scripted || contactActive || modalOpen) return@LaunchedEffect
        delay(3_500)
        if (contactActive) return@LaunchedEffect
        design = 0
        plan = 0
        slotA = 0
        slotB = 0
        swap.snapTo(0f)
        ring.snapTo(0f)
        pill.snapTo(0f)
        takeoverSnapshot = null
        clockMs = 0
        startedAt = 0
        scripted = true
    }

    val snapshot = when {
        freezeAt != null -> bankCardPickerSnapshotAt(freezeAt.toFloat(), reduced = false)
        scripted -> bankCardPickerSnapshotAt(clockMs.toFloat(), reduced = false)
        else -> takeoverSnapshot ?: manualSnapshot()
    }
    fun selection() = BankCardPickerSelection(
        designId = BankCardPickerData.Designs[snapshot.design].id,
        designLabel = BankCardPickerData.Designs[snapshot.design].label,
        planId = BankCardPickerData.Plans[snapshot.plan].id,
        planLabel = BankCardPickerData.Plans[snapshot.plan].label,
    )
    fun beginModal() {
        modalOpen = true
        resumeGeneration += 1
        scope.launch { captureScript() }
    }
    fun endModal() {
        modalOpen = false
        resumeGeneration += 1
    }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        endModal()
    }
    fun share() {
        val selected = selection()
        if (onShare != null) {
            onShare(selected)
        } else {
            beginModal()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, selected.summary)
            }
            shareLauncher.launch(Intent.createChooser(intent, "Share selected card"))
        }
    }
    fun confirm() {
        val selected = selection()
        if (onGetCard != null) {
            onGetCard(selected)
        } else {
            beginModal()
            confirmation = selected
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(BankCardPickerColors.Page)
            .pointerInput(freezeAt) {
                if (freezeAt != null) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } else {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        contactActive = true
                        resumeGeneration += 1
                        scope.launch { captureScript() }
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                        }
                        contactActive = false
                        resumeGeneration += 1
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value
        val dy = topInset - 62f
        val segmentWidth = width - 50f
        val halfSegment = (segmentWidth - 4f) / 2f

        BankCardPickerHero(
            width = width,
            dy = dy,
            snapshot = snapshot,
            onBack = onBack,
            onShare = ::share,
            onDesign = ::selectDesign,
        )
        BankCardPickerPlans(
            width = segmentWidth,
            halfWidth = halfSegment,
            dy = dy,
            snapshot = snapshot,
            onPlan = ::selectPlan,
        )
        BankCardPickerInformation(width, dy, snapshot.plan)
        BankCardPickerBottomBar(width, height, dy)
        BankCardPickerCta(width, dy, ::confirm)
    }

    confirmation?.let { selected ->
        AlertDialog(
            onDismissRequest = {
                confirmation = null
                endModal()
            },
            title = { Text("Card selected") },
            text = { Text("${selected.designLabel}\n${selected.planLabel}") },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    endModal()
                }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun BankCardPickerHero(
    width: Float,
    dy: Float,
    snapshot: BankCardPickerSnapshot,
    onBack: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDesign: (Int, Boolean) -> Unit,
) {
    Box(
        Modifier
            .size(width.dp, (538f + dy).dp)
            .clip(RoundedCornerShape(bottomStart = 33.dp, bottomEnd = 33.dp)),
    ) {
        BankCardPickerHeroField()
        Box(
            Modifier
                .offset(x = (197.5f - 130f).dp, y = (204f - 83.5f + dy).dp)
                .size(260.dp, 167.dp),
        ) {
            for (depth in 14 downTo 1) BankCardPickerPlate(depth)
            BankCardPickerCardSlot(BankCardPickerData.Designs[snapshot.slotA], 1f - snapshot.swap)
            BankCardPickerCardSlot(BankCardPickerData.Designs[snapshot.slotB], snapshot.swap)
        }
        BankCardPickerIconButton(
            label = "Back",
            x = 11.3f - (44f - 11.3f) / 2f,
            y = 67f + dy - 22f,
            action = onBack,
            disabledHint = "Use the system back gesture to leave this preview",
        ) { BankCardPickerBackGlyph() }
        BankCardPickerIconButton("Share selected card", width - 18.3f - 19f - (44f - 19f) / 2f, 67f + dy - 22f, onShare) { BankCardPickerShareGlyph() }
        Text(
            "Bloom Card",
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(y = (370f + dy - 18f).dp).height(36.dp),
            style = TextStyle(fontFamily = BankCardPickerSemibold, fontSize = 30.sp, letterSpacing = (-0.5).sp, platformStyle = BankTextPlatform),
        )
        val item = BankCardPickerData.Designs[snapshot.design]
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(200, delayMillis = 120)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxWidth().offset(y = (407f + dy - 12f).dp),
            label = "design caption",
        ) { shown ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.7.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(),) {
                Spacer(Modifier.weight(1f))
                Text("Design by ${shown.designer}", color = Color.White, style = bankText(17f, BankCardPickerRegular))
                Box(Modifier.height(21.dp).clip(RoundedCornerShape(11.dp)).background(BankCardPickerColors.Lime).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text(shown.price, color = Color.Black, style = bankText(13f, BankCardPickerMedium))
                }
                Spacer(Modifier.weight(1f))
            }
        }
        BankCardPickerDesigns(width, dy, snapshot, onDesign)
    }
}

@Composable
private fun BankCardPickerDesigns(
    width: Float,
    dy: Float,
    snapshot: BankCardPickerSnapshot,
    onDesign: (Int, Boolean) -> Unit,
) {
    val scroll = rememberScrollState()
    Box(
        Modifier
            .offset(y = (471.7f + dy - 32.5f - 6f).dp)
            .size(width.dp, 77.dp)
            .horizontalScroll(scroll),
    ) {
        Box(
            Modifier
                .offset(x = (33.3f + snapshot.ring * 81.4f).dp, y = 6.dp)
                .size(65.dp)
                .border(3.dp, BankCardPickerColors.Lime, RoundedCornerShape(16.dp)),
        )
        Row(Modifier.padding(start = 33.3.dp, end = 24.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.4.dp)) {
            BankCardPickerData.Designs.forEachIndexed { index, item ->
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                val on = max(0f, 1f - abs(snapshot.ring - index) * 2f)
                val selected = max(0f, 1f - abs(snapshot.ring - index))
                Box(
                    Modifier
                        .size(65.dp)
                        .graphicsLayer {
                            val scale = (1f - (10.8f / 65f) * selected) * if (pressed) 0.97f else 1f
                            scaleX = scale
                            scaleY = scale
                            clip = true
                            shape = RoundedCornerShape(16.dp)
                        }
                        .clickable(interactionSource = source, indication = null) { onDesign(index, true) }
                        .semantics {
                            role = Role.Button
                            contentDescription = "${item.label}, design by ${item.designer}, ${item.price}"
                            this.selected = index == snapshot.design
                        },
                ) {
                    Image(painterResource(item.thumbnail), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().border(2.dp, Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp)))
                    BankCardPickerCheckGlyph(Modifier.align(Alignment.Center).graphicsLayer { alpha = on; scaleX = 0.9f + 0.1f * on; scaleY = scaleX })
                }
            }
        }
    }
}

@Composable
private fun BankCardPickerPlans(
    width: Float,
    halfWidth: Float,
    dy: Float,
    snapshot: BankCardPickerSnapshot,
    onPlan: (Int, Boolean) -> Unit,
) {
    Box(
        Modifier
            .offset(x = 25.dp, y = (556f + dy).dp)
            .size(width.dp, 51.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
    ) {
        Box(
            Modifier
                .offset(x = (2f + snapshot.pill * halfWidth).dp, y = 2.dp)
                .size(halfWidth.dp, 47.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BankCardPickerColors.Dark),
        )
        Row(Modifier.padding(start = 2.dp)) {
            BankCardPickerData.Plans.forEachIndexed { index, item ->
                Row(
                    Modifier
                        .size(halfWidth.dp, 51.dp)
                        .clickable { onPlan(index, true) }
                        .semantics { role = Role.Tab; selected = index == snapshot.plan },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.label, color = if (index == snapshot.plan) Color.White else BankCardPickerColors.Grey, style = bankText(15f, BankCardPickerSemibold))
                    if (index == 1) {
                        Spacer(Modifier.width(8.dp))
                        BankCardPickerFlagGlyph()
                    }
                }
            }
        }
    }
}

@Composable
private fun BankCardPickerInformation(width: Float, dy: Float, selectedPlan: Int) {
    val plan = BankCardPickerData.Plans[selectedPlan]
    Box(
        Modifier
            .offset(x = 8.dp, y = (621f + dy).dp)
            .size((width - 16f).dp, 280.dp)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color.White),
    ) {
        BankCardPickerInfoRow("Card\nmaintenance", plan.fee, plan.feeUnit, 25f)
        Box(Modifier.offset(x = 17.dp, y = 88.dp).size((width - 50f).dp, 1.dp).background(BankCardPickerColors.Hairline))
        BankCardPickerInfoRow("Cashback\ncategories", plan.categories, null, 106f)
        Box(Modifier.offset(x = 17.dp, y = 169.dp).size((width - 50f).dp, 1.dp).background(BankCardPickerColors.Hairline))
        BankCardPickerInfoRow("Transfers to any bank", "Free", null, 189f)
    }
}

@Composable
private fun BankCardPickerInfoRow(label: String, value: String, unit: String?, y: Float) {
    Row(Modifier.fillMaxWidth().offset(y = y.dp).padding(horizontal = 17.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Black, style = bankText(17f, BankCardPickerRegular, 22f))
        AnimatedContent(value to unit, transitionSpec = { fadeIn(tween(200, delayMillis = 120)) togetherWith fadeOut(tween(120)) }, label = "plan value") { shown ->
            Column(horizontalAlignment = Alignment.End) {
                Text(shown.first, color = Color.Black, style = bankText(22f, BankCardPickerSemibold, 28f))
                shown.second?.let { Text(it, color = Color.Black, style = bankText(15f, BankCardPickerMedium, 20f)) }
            }
        }
    }
}

@Composable
private fun BankCardPickerBottomBar(width: Float, height: Float, dy: Float) {
    Box(
        Modifier
            .offset(y = (752f + dy).dp)
            .size(width.dp, max(0f, height - 752f - dy).dp)
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(Color(0x8CF8F8F8)),
    )
}

@Composable
private fun BankCardPickerCta(width: Float, dy: Float, onGetCard: (() -> Unit)?) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressAmount by animateFloatAsState(
        if (pressed) 1f else 0f,
        tween(if (pressed) 120 else 150, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "cta press",
    )
    val view = LocalView.current
    Box(
        Modifier
            .offset(x = 17.dp, y = (772.5f + dy).dp)
            .size((width - 34f).dp, 55.dp)
            .graphicsLayer { val s = 1f - 0.03f * pressAmount; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(20.dp))
            .background(BankCardPickerColors.Dark)
            .clickable(interactionSource = source, indication = null) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onGetCard?.invoke()
            }
            .semantics { role = Role.Button; contentDescription = "Get the card" },
        contentAlignment = Alignment.Center,
    ) {
        Text("Get the card", color = Color.White, style = bankText(18f, BankCardPickerMedium))
    }
}

@Composable
private fun BankCardPickerHeroField() {
    val bitmap = remember {
        Bitmap.createBitmap(
            BankCardPickerHeroRows.flatten().map { it.toArgb() }.toIntArray(),
            8,
            14,
            Bitmap.Config.ARGB_8888,
        ).asImageBitmap()
    }
    Box(Modifier.fillMaxSize().clip(RectangleShape)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .blur(14.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        ) {
            val bleed = 48.dp.toPx().roundToInt()
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(-bleed, -bleed),
                dstSize = IntSize(
                    width = size.width.roundToInt() + bleed * 2,
                    height = size.height.roundToInt() + bleed * 2,
                ),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

@Composable
private fun BankCardPickerPlate(depth: Int) {
    val amount = (depth - 1f) / 13f
    val red = (124f + (27f - 124f) * amount) / 255f
    val green = (193f + (94f - 193f) * amount) / 255f
    val blue = (142f + (95f - 142f) * amount) / 255f
    Box(
        Modifier
            .fillMaxSize()
            .bankCardTransform(depth.toFloat())
            .clip(RoundedCornerShape(14.dp))
            .background(Color(red, green, blue, 0.94f))
            .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent, Color(0x8C0A3C30)))),
    )
}

@Composable
private fun BankCardPickerCardSlot(design: BankCardPickerDesign, frontness: Float) {
    BankCardPickerCardArt(
        design,
        Modifier
            .fillMaxSize()
            .zIndex(if (frontness > 0.5f) 2f else 1f)
            .graphicsLayer {
                alpha = ((frontness - 0.25f) / 0.5f).coerceIn(0f, 1f)
                shadowElevation = 14.dp.toPx()
                shape = RoundedCornerShape(14.dp)
                clip = false
            }
            .bankCardTransform(1f - frontness),
    )
}

private fun Modifier.bankCardTransform(depth: Float): Modifier = this
    .graphicsLayer {
        translationX = (-16.5f * depth).dp.toPx()
        translationY = (15f * depth).dp.toPx()
        scaleX = 0.952f
    }
    .drawWithContent {
        val canvas = drawContext.canvas.nativeCanvas
        val checkpoint = canvas.save()
        val skew = tan(10f * PI.toFloat() / 180f)
        val matrix = AndroidMatrix().apply {
            setSkew(0f, skew, size.width / 2f, size.height / 2f)
        }
        canvas.concat(matrix)
        drawContent()
        canvas.restoreToCount(checkpoint)
    }

@Composable
private fun BankCardPickerCardArt(design: BankCardPickerDesign, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF2F8F40))) {
        if (design.art != null) {
            Image(painterResource(design.art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            BankCardPickerBlossoms()
        }
        BankCardPickerCardShade()
        BankCardPickerFurniture()
    }
}

@Composable
private fun BankCardPickerCardShade() {
    Canvas(Modifier.fillMaxSize()) {
        val scaleX = size.width / 260f
        val scaleY = size.height / 167f
        withTransform({ scale(scaleX, scaleY, pivot = Offset.Zero) }) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0x2914141E)),
                    start = Offset(220f, 0f),
                    end = Offset(13f, 167f),
                ),
                size = Size(260f, 167f),
            )
        }
    }
}

@Composable
private fun BankCardPickerBlossoms() {
    Canvas(Modifier.fillMaxSize()) {
        val scaleX = size.width / 260f
        val scaleY = size.height / 167f
        val flowers = listOf(
            BankFlower(207f, 59f, 46f, Color(0xFFEAB6C4), Color(0xFFD1323C)),
            BankFlower(87f, 46f, 25f, Color(0xFFEAB6C4), Color(0xFFD1323C)),
            BankFlower(23f, 135f, 44f, Color(0xFFEAB6C4), Color(0xFFD1323C)),
            BankFlower(128f, 123f, 25f, Color(0xFFEAB6C4), Color(0xFFD1323C)),
            BankFlower(157f, 71f, 17f, Color(0xFFF1ECD6), Color(0xFFE2A8B2)),
        )
        withTransform({ scale(scaleX, scaleY, pivot = Offset.Zero) }) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2F8F40), Color(0xFF4AAF55)),
                    start = Offset(0f, 167f),
                    end = Offset(260f, 0f),
                ),
                size = Size(260f, 167f),
            )
            flowers.forEach { flower ->
                repeat(5) { index ->
                    val angle = index / 5f * PI.toFloat() * 2f - PI.toFloat() / 2f
                    val center = Offset(
                        flower.x + cos(angle) * flower.radius * 0.5f,
                        flower.y + sin(angle) * flower.radius * 0.5f,
                    )
                    drawCircle(flower.petal, flower.radius * 0.52f, center)
                }
                drawCircle(flower.heart, flower.radius * 0.2f, Offset(flower.x, flower.y))
            }
        }
    }
}

@Composable
private fun BankCardPickerFurniture() {
    Canvas(Modifier.fillMaxSize()) {
        val scaleX = size.width / 260f
        val scaleY = size.height / 167f
        withTransform({ scale(scaleX, scaleY, pivot = Offset.Zero) }) {
            val white = Color.White.copy(alpha = 0.88f)
            drawCircle(white, 12.5f, Offset(25f, 25f), style = Stroke(2.4f))
            val leaf = Path().apply {
                moveTo(19.5f, 30.5f)
                cubicTo(19f, 22f, 24f, 18.5f, 31f, 19f)
                cubicTo(31.5f, 26f, 28f, 31f, 19.5f, 30.5f)
                close()
            }
            drawPath(leaf, white)
            drawRoundRect(Color(0xFFECEBE6), Offset(26f, 63f), Size(32f, 25f), CornerRadius(5f))
            val chip = Color(0xFFB9B6AD)
            listOf(71f, 80f).forEach { y ->
                drawLine(chip, Offset(26f, y), Offset(35f, y), 0.9f)
                drawLine(chip, Offset(58f, y), Offset(49f, y), 0.9f)
            }
            listOf(35f, 49f).forEach { x ->
                drawLine(chip, Offset(x, 63f), Offset(x, 88f), 0.9f)
            }
            drawLine(chip, Offset(35f, 75.5f), Offset(49f, 75.5f), 0.9f)
            val bars = Path().apply {
                moveTo(202f, 153f); lineTo(207f, 139f); lineTo(217f, 139f); lineTo(212f, 153f); close()
                moveTo(216f, 153f); lineTo(224f, 132f); lineTo(234f, 132f); lineTo(226f, 153f); close()
                moveTo(230f, 153f); lineTo(239f, 128f); lineTo(249f, 128f); lineTo(240f, 153f); close()
            }
            drawPath(bars, Color.White)
        }
    }
}

@Composable
private fun BankCardPickerIconButton(
    label: String,
    x: Float,
    y: Float,
    action: (() -> Unit)?,
    disabledHint: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = if (action != null) {
        Modifier.clickable { action() }
    } else {
        Modifier.semantics {
            disabled()
            if (disabledHint != null) stateDescription = disabledHint
        }
    }
    Box(
        Modifier
            .offset(x.dp, y.dp)
            .size(44.dp)
            .then(interaction)
            .semantics { role = Role.Button; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun BankCardPickerBackGlyph() {
    Canvas(Modifier.size(11.3.dp, 19.7.dp)) {
        withTransform({ scale(size.width / 11.3f, size.height / 19.7f, pivot = Offset.Zero) }) {
            val path = Path().apply {
                moveTo(10.3f, 1f)
                lineTo(1.3f, 10f)
                lineTo(10.3f, 19f)
            }
            drawPath(path, Color.White, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun BankCardPickerShareGlyph() {
    Canvas(Modifier.size(19.dp, 15.dp)) {
        withTransform({ scale(size.width / 19f, size.height / 15f, pivot = Offset.Zero) }) {
            val path = Path().apply {
                moveTo(11f, 0f)
                lineTo(19f, 7.5f)
                lineTo(11f, 15f)
                lineTo(11f, 10.5f)
                cubicTo(6f, 10.5f, 2f, 12f, 0f, 15f)
                cubicTo(1f, 8f, 5f, 5f, 11f, 5f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}

@Composable
private fun BankCardPickerFlagGlyph() {
    Canvas(Modifier.size(14.3.dp, 13.dp)) {
        withTransform({ scale(size.width / 14f, size.height / 13f, pivot = Offset.Zero) }) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(14f, 0f)
                lineTo(10f, 6.5f)
                lineTo(14f, 13f)
                lineTo(0f, 13f)
                close()
            }
            drawPath(path, BankCardPickerColors.Lime)
        }
    }
}

@Composable
private fun BankCardPickerCheckGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier.size(30.dp, 24.dp)) {
        withTransform({ scale(size.width / 30f, size.height / 24f, pivot = Offset.Zero) }) {
            val path = Path().apply {
                moveTo(3f, 12.5f)
                lineTo(11f, 20f)
                lineTo(27f, 3.5f)
            }
            drawPath(
                path,
                BankCardPickerColors.Lime,
                style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private data class BankCardPickerSnapshot(val design:Int,val plan:Int,val slotA:Int,val slotB:Int,val swap:Float,val ring:Float,val pill:Float)
private enum class BankAction { Design, Plan }
private data class BankCue(val at:Float,val action:BankAction,val target:Int)
private val BankScript = listOf(
    BankCue(1_200f, BankAction.Design, 1),
    BankCue(3_000f, BankAction.Design, 2),
    BankCue(4_800f, BankAction.Design, 3),
    BankCue(6_800f, BankAction.Plan, 1),
    BankCue(8_600f, BankAction.Plan, 0),
    BankCue(10_200f, BankAction.Design, 0),
)

private fun bankCardPickerSnapshotAt(milliseconds: Float, reduced: Boolean): BankCardPickerSnapshot {
    val time = ((milliseconds % LOOP_MS) + LOOP_MS) % LOOP_MS
    var design = 0
    var plan = 0
    var slotA = 0
    var slotB = 0
    var swap = 0f
    var ring = 0f
    var pill = 0f
    BankScript.filter { time >= it.at }.forEach { cue ->
        val elapsed = time - cue.at
        when (cue.action) {
            BankAction.Design -> {
                val from = kotlin.math.round(swap)
                val target = if (from < 0.5f) 1f else 0f
                if (target > 0.5f) slotB = cue.target else slotA = cue.target
                val previous = design
                design = cue.target
                val duration = if (reduced) 200f else 420f
                swap = if (elapsed < duration) {
                    val progress = elapsed / duration
                    val eased = if (reduced) bankEaseOut(progress) else bankEaseInOut(progress)
                    from + (target - from) * eased
                } else {
                    target
                }
                val ringDuration = if (reduced) 160f else 300f
                ring = if (elapsed < ringDuration) {
                    previous + (cue.target - previous) * bankSpring(elapsed / ringDuration)
                } else {
                    cue.target.toFloat()
                }
            }
            BankAction.Plan -> {
                val previous = plan
                plan = cue.target
                val pillDuration = if (reduced) 160f else 300f
                pill = if (elapsed < pillDuration) {
                    previous + (cue.target - previous) * bankSpring(elapsed / pillDuration)
                } else {
                    cue.target.toFloat()
                }
            }
        }
    }
    return BankCardPickerSnapshot(design, plan, slotA, slotB, swap, ring, pill)
}

private val BankCardPickerEaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private fun bankEaseInOut(value: Float) = bankBezier(value, 0.77f, 0f, 0.175f, 1f)
private fun bankEaseOut(value: Float) = bankBezier(value, 0.23f, 1f, 0.32f, 1f)

private fun bankSpring(value: Float): Float {
    if (value <= 0f) return 0f
    if (value >= 1f) return 1f
    val frequency = 2f * PI.toFloat()
    return 1f - exp(-frequency * value) * (1f + frequency * value)
}

private fun bankBezier(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    fun sample(time: Float, first: Float, second: Float): Float {
        val inverse = 1f - time
        return 3f * inverse * inverse * time * first +
            3f * inverse * time * time * second +
            time * time * time
    }
    var low = 0f
    var high = 1f
    repeat(12) {
        val middle = (low + high) / 2
        if (sample(middle, x1, x2) < x) low = middle else high = middle
    }
    return sample((low + high) / 2, y1, y2)
}

private data class BankCardPickerDesign(val id:String,val art:Int?,val thumbnail:Int,val designer:String,val price:String,val label:String)
private data class BankCardPickerPlan(val id:String,val label:String,val fee:String,val feeUnit:String,val categories:String)
private object BankCardPickerData{
    val Designs=listOf(
        BankCardPickerDesign("blossom",null,R.drawable.bankcardpicker_thumb_blossom,"Olga Paperova","$4.99","Pink blossoms on green"),
        BankCardPickerDesign("meadow",R.drawable.bankcardpicker_card_meadow,R.drawable.bankcardpicker_thumb_meadow,"Kenta Hayashi","$4.99","A cat watching the sea"),
        BankCardPickerDesign("ridge",R.drawable.bankcardpicker_card_ridge,R.drawable.bankcardpicker_thumb_ridge,"Inês Duarte","$6.99","Mountains under a setting planet"),
        BankCardPickerDesign("valley",R.drawable.bankcardpicker_card_valley,R.drawable.bankcardpicker_thumb_valley,"Tomasz Wilk","$6.99","A green valley with a striped moon"),
        BankCardPickerDesign("silk",R.drawable.bankcardpicker_card_silk,R.drawable.bankcardpicker_thumb_silk,"Ana Lucía Ríos","Free","Silver silk"),
    )
    val Plans=listOf(BankCardPickerPlan("basic","Basic","$0","per month","3"),BankCardPickerPlan("prime","BloomPrime","$0","with Prime","5"))
}
private data class BankFlower(val x:Float,val y:Float,val radius:Float,val petal:Color,val heart:Color)
private object BankCardPickerColors{val Page=Color(0xFFF1F1F1);val Dark=Color(0xFF31373B);val Grey=Color(0xFF989898);val Hairline=Color(0xFFDADADA);val Lime=Color(0xFF64FF52)}
private val BankTextPlatform=PlatformTextStyle(includeFontPadding=false)
private fun bankText(size:Float,family:FontFamily,lineHeight:Float=size*1.2f)=TextStyle(fontFamily=family,fontSize=size.sp,lineHeight=lineHeight.sp,platformStyle=BankTextPlatform)

private val BankCardPickerHeroRows=listOf(
    listOf(0xFF0B1313,0xFF0B1213,0xFF0B1212,0xFF0A1112,0xFF0A1112,0xFF0A1011,0xFF0A1011,0xFF0A1011),
    listOf(0xFF0F1E1D,0xFF0F1D1C,0xFF0E1C1C,0xFF0E1B1B,0xFF0E1B1A,0xFF0D1A1A,0xFF0D1919,0xFF0D1919),
    listOf(0xFF162D2C,0xFF152C2B,0xFF152B2A,0xFF142A29,0xFF142827,0xFF132726,0xFF132625,0xFF132625),
    listOf(0xFF1F4241,0xFF1E4140,0xFF1E403F,0xFF1D3E3D,0xFF1C3D3C,0xFF1C3B3A,0xFF1B3A39,0xFF1B3A39),
    listOf(0xFF245F59,0xFF245D58,0xFF255C57,0xFF255A56,0xFF255854,0xFF265653,0xFF265552,0xFF265452),
    listOf(0xFF1F6B5D,0xFF226C60,0xFF256D63,0xFF296F66,0xFF2C7069,0xFF30726B,0xFF32736D,0xFF33736E),
    listOf(0xFF1E7666,0xFF24796B,0xFF2A7D70,0xFF318176,0xFF37857B,0xFF3E8980,0xFF428C83,0xFF448D85),
    listOf(0xFF1F7D6C,0xFF288373,0xFF31897A,0xFF3B9082,0xFF45978A,0xFF4E9E92,0xFF54A297,0xFF57A499),
    listOf(0xFF207C6B,0xFF2C8575,0xFF388F7F,0xFF46998A,0xFF53A395,0xFF60ADA0,0xFF68B4A7,0xFF6CB7AA),
    listOf(0xFF217470,0xFF30807B,0xFF3F8D87,0xFF509B94,0xFF60A9A0,0xFF70B6AC,0xFF7ABFB3,0xFF7FC3B7),
    listOf(0xFF256466,0xFF357473,0xFF468582,0xFF589792,0xFF6AA9A1,0xFF7BBAB0,0xFF86C5B9,0xFF8CCBBE),
    listOf(0xFF2B6063,0xFF3A7071,0xFF4A827F,0xFF5C948F,0xFF6DA69F,0xFF7EB8AD,0xFF89C3B7,0xFF8EC9BC),
    listOf(0xFF2B6063,0xFF3A7070,0xFF4A817F,0xFF5A938F,0xFF6BA59E,0xFF7BB6AD,0xFF85C1B6,0xFF8AC7BB),
    listOf(0xFF326368,0xFF407275,0xFF4E8283,0xFF5E9391,0xFF6EA4A0,0xFF7CB4AE,0xFF86BFB6,0xFF8BC4BB),
).map{row->row.map{Color(it)}}
