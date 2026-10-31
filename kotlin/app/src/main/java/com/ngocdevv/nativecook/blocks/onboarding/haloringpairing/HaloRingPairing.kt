package com.ngocdevv.nativecook.blocks.onboarding.haloringpairing

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.*
import kotlinx.coroutines.delay

@Composable
fun HaloRingPairing(autoPlay: Boolean = false) {
    val c = HaloRingPairingTheme
    val reduced = rememberLiveReducedMotion()
    val playing = autoPlay && !reduced
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _,_ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BlockSystemBars(darkIcons = false)
    val regular = remember { c.font(context,"Regular") }
    val medium = remember { c.font(context,"Medium") }
    val semibold = remember { c.font(context,"SemiBold") }
    val clock = remember(playing,reduced) { mutableDoubleStateOf(0.0) }
    val driver = remember(playing,reduced) { HaloRingPairingDriver(playing,reduced) }
    var awake by remember(playing,reduced) { mutableStateOf(true) }
    var revision by remember(driver) { mutableIntStateOf(0) }
    val running = active && (!reduced || awake)
    LaunchedEffect(running,driver) {
        if (!running) return@LaunchedEffect
        val start = clock.doubleValue
        var first = -1L
        while (true) {
            withFrameNanos { now ->
                if (first < 0) first = now
                clock.doubleValue = start+(now-first)/1e9
            }
            if (driver.idleAt(clock.doubleValue)) { awake = false; break }
        }
    }
    val frame = remember(driver) { { driver.frame(clock.doubleValue) } }
    val discrete by remember(driver) { derivedStateOf { revision; frame().let { it.step to it.size } } }
    val foundHapticAt = driver.foundHapticAt
    LaunchedEffect(driver,foundHapticAt) {
        if (foundHapticAt != null) {
            delay(((foundHapticAt-clock.doubleValue).coerceAtLeast(0.0)*1000).toLong())
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
    val wake = { awake = true; revision++ }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) HaloRingPairingRuntimeShader() else null
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(c.page)) {
        val l = HaloRingPairingLayout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),
            WindowInsets.safeDrawing.getTop(density)/density.density.toDouble(),
            WindowInsets.safeDrawing.getBottom(density)/density.density.toDouble())
        Box(Modifier.fillMaxSize().pointerInput(driver,l) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false,pass = PointerEventPass.Initial)
                val inStage = (down.position.y/density.density).toDouble() in (l.stageY-150)..(l.stageY+150)
                if (inStage) driver.begin(clock.doubleValue) else {
                    driver.takeOver(clock.doubleValue); driver.holding = true
                }
                wake()
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis,down.position)
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis,change.position)
                        if (inStage) {
                            val dx = (change.position.x-down.position.x)/density.density
                            val dy = (change.position.y-down.position.y)/density.density
                            driver.move(clock.doubleValue,dx.toDouble(),dy.toDouble())
                            if (hypot(dx,dy) >= 3) change.consume()
                        }
                        wake()
                    } while (change.pressed)
                } finally {
                    driver.end(clock.doubleValue,tracker.calculateVelocity().x/density.density.toDouble()); wake()
                }
            }
        }) {
            // The stage exposes the same adjustable ring to TalkBack as the reference.
            Box(Modifier.offset(y=(l.stageY-150).dp).fillMaxWidth().height(300.dp).semantics {
                contentDescription = if (discrete.first == 0) "Looking for your ring" else "Halo Ring, titanium. Drag to turn it"
                customActions = listOf(
                    CustomAccessibilityAction("Turn clockwise") { driver.begin(clock.doubleValue); driver.move(clock.doubleValue,40.0,0.0); driver.end(clock.doubleValue,0.0); wake(); true },
                    CustomAccessibilityAction("Turn counterclockwise") { driver.begin(clock.doubleValue); driver.move(clock.doubleValue,-40.0,0.0); driver.end(clock.doubleValue,0.0); wake(); true },
                )
            })
            HaloRingPairingWords("Looking for your ring","Hold it close to your phone",semibold,regular,
                Modifier.offset(x=24.dp,y=l.titleTop.dp).width((l.w-48).dp).graphicsLayer {
                    revision; val f = frame(); alpha = ((1-HaloRingPairingMotion.smooth(0.2,0.7,f.condense))*(1-f.paired)).toFloat()
                }.clearAndSetSemantics { })
            HaloRingPairingWords("Found Halo Ring","Titanium · Size ${discrete.second}",semibold,regular,
                Modifier.offset(x=24.dp,y=l.titleTop.dp).width((l.w-48).dp).graphicsLayer {
                    revision; val f = frame(); alpha = f.found.toFloat(); translationY = (8*(1-f.found)).dp.toPx()
                }.semantics { if (discrete.first != 1) hideFromAccessibility() })
            Row(Modifier.offset(y=l.sizesTop.dp).fillMaxWidth().graphicsLayer {
                revision; val f = frame(); alpha = f.found.toFloat(); translationY = (8*(1-f.found)).dp.toPx()
            }.semantics { contentDescription = "Ring size"; if (discrete.first != 1) hideFromAccessibility() },
                horizontalArrangement = Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally)) {
                for (size in HaloRingPairingMotion.SIZES) {
                    val selectedSize = size == discrete.second
                    Box(Modifier.size(46.dp,36.dp)
                        .background(if (selectedSize) c.green.copy(alpha=0.12f) else Color.Transparent,CircleShape)
                        .border(1.dp,if (selectedSize) c.green else c.line,CircleShape)
                        .clickable(enabled=discrete.first == 1,role=Role.Button) {
                            if (driver.choose(size,clock.doubleValue)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            wake()
                        }.semantics { contentDescription = "Size $size"; selected = selectedSize },contentAlignment=Alignment.Center) {
                        HaloRingPairingText("$size",15,medium,if (selectedSize) c.ink else c.mute,
                            Modifier.clearAndSetSemantics { },tabular=true)
                    }
                }
            }
            Box(Modifier.offset(x=20.dp,y=l.buttonTop.dp).size((l.w-40).dp,56.dp).graphicsLayer {
                revision; val f = frame(); alpha = f.found.toFloat(); scaleX = (1-0.04*f.pressPair).toFloat(); scaleY = scaleX
            }.background(c.green,CircleShape).clickable(enabled=discrete.first == 1,role=Role.Button) {
                if (driver.pair(clock.doubleValue)) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                wake()
            }.semantics { if (discrete.first != 1) hideFromAccessibility() },contentAlignment=Alignment.Center) {
                HaloRingPairingText("Pair ring",17,semibold,c.greenInk)
            }
            HaloRingPairingDashboard(l,medium,semibold,discrete.first == 2,
                Modifier.offset(x=20.dp,y=(l.top+10).dp).width((l.w-40).dp).graphicsLayer {
                    revision; val f = frame(); alpha = f.paired.toFloat(); translationY = (12*(1-f.paired)).dp.toPx()
                }.semantics { if (discrete.first != 2) hideFromAccessibility() }) {
                driver.unpair(clock.doubleValue); wake()
            }
            Box(Modifier.offset(x=(l.chipX-22).dp,y=(l.chipY-22).dp).size(44.dp).graphicsLayer {
                revision; alpha = HaloRingPairingMotion.smooth(0.6,1.0,frame().fly).toFloat()
            }.background(c.card,CircleShape).border(1.dp,c.line,CircleShape))
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                revision
                val f = frame()
                if (Build.VERSION.SDK_INT >= 33 && shader != null) shader.draw(this,l,f,reduced)
                else HaloRingPairingFallback(l,f,reduced)
            }
            Box(Modifier.size(44.dp).graphicsLayer {
                revision; val f = frame()
                val x = when (f.fingerAt) { 0 -> l.w*0.3+f.fingerDx; 1 -> l.w/2+28; else -> l.w/2 }
                val y = when (f.fingerAt) { 0 -> l.stageY+40; 1 -> l.sizesTop+18; else -> l.buttonTop+28 }
                translationX = (x-22).dp.toPx(); translationY = (y-22).dp.toPx(); alpha = (0.85*f.finger).toFloat()
            }.background(Color.White.copy(alpha=0.35f),CircleShape).border(1.5.dp,Color.White.copy(alpha=0.85f),CircleShape))
        }
    }
}

@Composable
private fun HaloRingPairingText(text: String,size: Int,font: FontFamily,color: Color,modifier: Modifier = Modifier,
    lineHeight: Int? = null,center: Boolean = false,tabular: Boolean = false) {
    BasicText(text,modifier,style=TextStyle(fontFamily=font,fontSize=size.sp,color=color,
        lineHeight=(lineHeight ?: (size*1.2).roundToInt()).sp,
        textAlign=if (center) TextAlign.Center else TextAlign.Start,
        fontFeatureSettings=if (tabular) "tnum" else null,platformStyle=PlatformTextStyle(includeFontPadding=false)))
}

@Composable
private fun HaloRingPairingWords(title: String,sub: String,semibold: FontFamily,regular: FontFamily,modifier: Modifier) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        HaloRingPairingText(title,26,semibold,HaloRingPairingTheme.ink,Modifier.height(32.dp).semantics { heading() },32,true)
        HaloRingPairingText(sub,15,regular,HaloRingPairingTheme.mute,Modifier.height(20.dp),20,true)
    }
}

@Composable
private fun HaloRingPairingDashboard(l: HaloRingPairingLayout,medium: FontFamily,semibold: FontFamily,
    enabled: Boolean,modifier: Modifier,onChip: () -> Unit) {
    val c = HaloRingPairingTheme
    Column(modifier) {
        Row(Modifier.height(44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clickable(enabled=enabled,role=Role.Button,onClick=onChip)
                .semantics { contentDescription = "Halo Ring, connected. Show the ring" })
            Column {
                HaloRingPairingText("Halo",16,semibold,c.ink,Modifier.height(21.dp),21)
                HaloRingPairingText("Connected",13,medium,c.green,Modifier.height(17.dp),17)
            }
        }
        Spacer(Modifier.height(28.dp))
        HaloRingPairingText("Good morning",30,semibold,c.ink,Modifier.height(38.dp).semantics { heading() },38)
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            for (row in 0..1) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    for (index in row*2..row*2+1) {
                        val s = c.stats[index]
                        Column(Modifier.size(((l.w-40)*0.475).dp,108.dp).background(c.card,RoundedCornerShape(22.dp))
                            .padding(16.dp).clearAndSetSemantics { contentDescription = "${s.first} ${s.second} ${s.third}" },
                            verticalArrangement=Arrangement.SpaceBetween) {
                            HaloRingPairingText(s.first,14,medium,c.mute)
                            BasicText(buildAnnotatedString {
                                withStyle(SpanStyle(fontFamily=semibold,fontSize=28.sp,color=c.ink)) { append(s.second) }
                                if (s.third.isNotEmpty()) withStyle(SpanStyle(fontFamily=medium,fontSize=15.sp,color=c.mute)) { append(" ${s.third}") }
                            },style=TextStyle(fontFeatureSettings="tnum",platformStyle=PlatformTextStyle(includeFontPadding=false)))
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(33)
private class HaloRingPairingRuntimeShader {
    private val shader = RuntimeShader(HaloRingPairingShader)
    private val paint = Paint().apply { this.shader = this@HaloRingPairingRuntimeShader.shader }
    fun draw(scope: DrawScope,l: HaloRingPairingLayout,f: HaloRingPairingFrame,reduced: Boolean) = with(scope) {
        val arc = sin(PI*f.fly)
        val x = l.w/2+(l.chipX-l.w/2)*f.fly-30*arc
        val y = l.stageY+(l.chipY-l.stageY)*f.fly-20*arc
        val s = 148+(13-148)*f.fly
        val b = HaloRingPairingMotion.bump(f.bumpProgress*0.7)
        val scale = f.scale*(1+0.04*b)*(1-f.fly)+f.fly
        shader.setFloatUniform("uDensity",density)
        shader.setFloatUniform("uRing",x.toFloat(),y.toFloat(),s.toFloat(),density)
        shader.setFloatUniform("uState",f.condense.toFloat(),f.yaw.toFloat(),(1.05+(0.3-1.05)*f.fly).toFloat(),scale.toFloat())
        shader.setFloatUniform("uLive",f.phase.toFloat(),(f.search*if (reduced) 0.6 else 1.0).toFloat(),1f,0f)
        drawContext.canvas.nativeCanvas.drawRect(0f,0f,size.width,size.height,paint)
    }
}

// API 26–32: retain the layout, colour and interactions with flat band/ripple shapes.
private fun DrawScope.HaloRingPairingFallback(l: HaloRingPairingLayout,f: HaloRingPairingFrame,reduced: Boolean) {
    val arc = sin(PI*f.fly)
    val x = (l.w/2+(l.chipX-l.w/2)*f.fly-30*arc).toFloat()*density
    val y = (l.stageY+(l.chipY-l.stageY)*f.fly-20*arc).toFloat()*density
    val s = ((148+(13-148)*f.fly)*f.scale).toFloat()*density
    val flatten = abs(cos(1.05+(0.3-1.05)*f.fly)).toFloat().coerceAtLeast(0.15f)
    val green = HaloRingPairingTheme.green
    fun oval(radius: Float,color: Color,stroke: Float) {
        drawOval(color,Offset(x-radius,y-radius*flatten),Size(radius*2,radius*flatten*2),style=Stroke(stroke,cap=StrokeCap.Round))
    }
    if (f.search > 0) for (i in 0..2) {
        val u = ((f.phase*1.5/(2*PI)+i/3.0)%1).toFloat()
        oval(s*(0.8f+u*1.4f),green.copy(alpha=((1-u)*(1-u)*f.search*if (reduced) 0.6 else 1.0).toFloat().coerceIn(0f,1f)),1.5.dp.toPx())
    }
    oval(s*0.8f,green.copy(alpha=(1-f.condense).toFloat()),4.dp.toPx())
    if (f.condense > 0) {
        oval(s*0.8f,Color(0xFF939699).copy(alpha=f.condense.toFloat()),s*0.15f)
        oval(s*0.735f,Color(0xFF121618).copy(alpha=f.condense.toFloat()),s*0.045f)
        drawCircle(green.copy(alpha=f.condense.toFloat()),s*0.025f,Offset(x,y+s*0.73f*flatten))
    }
}
