package com.ngocdevv.nativecook.blocks.cards.snowglobegift

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

@Composable
fun SnowGlobeGift(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = LocalHapticFeedback.current
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
    val driver = remember(autoPlay,reduced) { SnowGlobeGiftDriver(autoPlay,reduced) }
    val art = remember { SnowGlobeGiftArt() }
    val regular = remember { FontFamily(Font("snowglobegift/snowglobegift_Inter-Regular.ttf",context.assets)) }
    val medium = remember { FontFamily(Font("snowglobegift/snowglobegift_Inter-Medium.ttf",context.assets)) }
    val semibold = remember { FontFamily(Font("snowglobegift/snowglobegift_Inter-SemiBold.ttf",context.assets)) }
    val display = remember { FontFamily(Font("snowglobegift/snowglobegift_InstrumentSerif-Regular.ttf",context.assets)) }
    val top = WindowInsets.statusBars.getTop(density)/density.density
    val bottom = WindowInsets.navigationBars.getBottom(density)/density.density
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val bars = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val old = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = true
        onDispose { if (old != null) bars.isAppearanceLightStatusBars = old }
    }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                if (!driver.needsFrames()) { delay(16); previous = 0L; continue }
                withFrameNanos { nanos ->
                    val dt = min(0.5,if (previous == 0L) 0.016 else (nanos-previous)/1e9)
                    previous = nanos; driver.tick(dt)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF8F1EF))) {
        val l = remember(maxWidth,maxHeight,top,bottom) { SnowGlobeGiftLayout(maxWidth.value,maxHeight.value,top,bottom) }
        Canvas(Modifier.fillMaxSize()) {
            driver.revision
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save(); c.scale(density.density,density.density)
                art.draw(c,l,driver.frame,density.density)
                c.restore()
            }
        }
        Box(Modifier.offset((l.heroX-l.heroS*1.1f).dp,(l.heroY-l.heroS*1.05f).dp)
            .size((l.heroS*2.2f).dp,(l.heroS*2.45f).dp)
            .semantics {
                role = Role.Button; contentDescription = "A snow globe with a gift inside. Shake it to stir the snow"
                onClick { driver.accessibilityShake(); true }
            }
            .pointerInput(driver,l) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    driver.takeOver()
                    val start = down.position
                    val tracker = VelocityTracker(); tracker.addPosition(down.uptimeMillis,start)
                    var dragged = false; var cancelled = true
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = (change.position.x-start.x)/density.density
                        val dy = (change.position.y-start.y)/density.density
                        tracker.addPosition(change.uptimeMillis,change.position)
                        if (!dragged && hypot(dx,dy) >= 2) { dragged = true; driver.dragStart(dx.toDouble()) }
                        if (change.pressed && dragged) driver.drag(dx.toDouble(),l.heroS)
                        change.consume()
                        if (!change.pressed) { cancelled = false; break }
                    } while (event.changes.any { it.pressed })
                    val speed = tracker.calculateVelocity().x/density.density
                    if (driver.end(speed.toDouble(),l.heroS,dragged,cancelled)) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            })
        Column(Modifier.offset(y=(l.amountY-40).dp).fillMaxWidth().height(80.dp)
            .graphicsLayer {
                driver.revision
                val a = driver.frame.amount.toFloat()
                alpha = a; translationY = (1-a)*18*density.density; scaleX = 0.9f+0.1f*a; scaleY = scaleX
            }
            .semantics { if (!driver.opened) hideFromAccessibility() },
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text("$50",fontFamily=display,fontSize=56.sp,lineHeight=60.sp,color=Color(0xFFB98A3E))
            Text("Kinto gift balance",fontFamily=semibold,fontSize=11.sp,letterSpacing=1.sp,color=Color(0xFFB98A3E))
        }
        SnowGlobeGiftPress(Modifier.offset(8.dp,l.top.dp).size(44.dp),reduced,"Back",{}) {
            Canvas(Modifier.size(12.dp,20.dp)) {
                val path = Path().apply { moveTo(10.dp.toPx(),2.dp.toPx()); lineTo(2.dp.toPx(),10.dp.toPx()); lineTo(10.dp.toPx(),18.dp.toPx()) }
                drawPath(path,Color(0xFF2A1D1C),style=Stroke(2.2.dp.toPx(),cap=StrokeCap.Round))
            }
        }
        Text("FROM MIRA",Modifier.offset(y=l.fromTop.dp).fillMaxWidth(),fontFamily=medium,fontSize=12.sp,lineHeight=18.sp,letterSpacing=1.4.sp,textAlign=TextAlign.Center,color=Color(0xFF8C7B78))
        Text("A gift for you",Modifier.offset(y=l.titleTop.dp).fillMaxWidth().semantics { heading() },fontFamily=display,fontSize=36.sp,lineHeight=42.sp,textAlign=TextAlign.Center,color=Color(0xFF2A1D1C))
        Text("“Happy birthday! The coffee is on me.”",Modifier.offset(40.dp,l.noteTop.dp).width((l.w-80).dp),fontFamily=regular,fontSize=15.sp,lineHeight=22.sp,textAlign=TextAlign.Center,color=Color(0xFF8C7B78))
        val label = if (driver.opened) { if (driver.added) "Added to Wallet" else "Add $50 to Wallet" } else "Open Gift"
        SnowGlobeGiftPress(Modifier.offset(16.dp,l.ctaTop.dp).size((l.w-32).dp,56.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF2A1D1C)),reduced,label,{
            driver.cta(); haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }) {
            Text(label,fontFamily=semibold,fontSize=17.sp,color=Color(0xFFFCF8F6))
        }
        Canvas(Modifier.fillMaxSize()) {
            driver.revision
            val f = driver.frame
            if (f.finger > 0) {
                val x = if (f.fingerAt == 0.0) l.heroX+f.fingerX.toFloat()*l.heroS else l.w/2
                val y = if (f.fingerAt == 0.0) l.heroY+0.1f*l.heroS else l.ctaTop+28
                val r = (22*(1-0.12*f.fingerPress)).toFloat().dp.toPx()
                val center = Offset(x.dp.toPx(),y.dp.toPx())
                drawCircle(Color(0xFF2A1D1C).copy(alpha=(0.1*f.finger).toFloat()),r,center)
                drawCircle(Color(0xFF2A1D1C).copy(alpha=(0.32*f.finger).toFloat()),r,center,style=Stroke(1.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun SnowGlobeGiftPress(modifier: Modifier,reduced: Boolean,label: String,action: () -> Unit,content: @Composable BoxScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed) 1f else 0f,tween(150,easing=CubicBezierEasing(0.23f,1f,0.32f,1f)),label="SnowGlobeGiftPress")
    Box(Modifier.graphicsLayer { scaleX = if (reduced) 1f else 1-0.04f*p; scaleY = scaleX; alpha = if (reduced) 1-0.28f*p else 1f }
        .then(modifier).semantics { contentDescription = label; role = Role.Button }
        .clickable(interactionSource=interaction,indication=null,onClick=action),contentAlignment=Alignment.Center,content=content)
}
