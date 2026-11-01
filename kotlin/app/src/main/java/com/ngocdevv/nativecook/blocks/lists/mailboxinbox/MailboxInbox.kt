package com.ngocdevv.nativecook.blocks.lists.mailboxinbox

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlin.math.*

private data class MailboxInboxMail(val from: String, val text: String, val time: String, val color: Color)
private val MailboxInboxLetters = listOf(
    MailboxInboxMail("Grandma Rose", "Your parcel of cookies is on its way!", "9:41", Color(0xFFFF8A65)),
    MailboxInboxMail("Kinto Bank", "Your October statement is ready", "8:20", Color(0xFF5A6BD0)),
    MailboxInboxMail("Pip & Co. Studio", "Invitation: spring open house", "Yesterday", Color(0xFF2F9E62)),
    MailboxInboxMail("City Library", "The book you reserved is waiting", "Mon", Color(0xFFE0A21F)),
)

@Composable
fun MailboxInbox(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
    val fonts = remember(context) {
        FontFamily(
            Font("mailboxinbox/mailboxinbox_Inter-Regular.ttf",context.assets,FontWeight.Normal),
            Font("mailboxinbox/mailboxinbox_Inter-SemiBold.ttf",context.assets,FontWeight.SemiBold),
            Font("mailboxinbox/mailboxinbox_Inter-Bold.ttf",context.assets,FontWeight.Bold),
        )
    }
    val art = remember { MailboxInboxArt() }
    val driver = remember(autoPlay,reduced) { MailboxInboxDriver() }
    val frame = remember(driver) { mutableStateOf(driver.frame) }
    var wake by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(driver,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                if (!driver.active(autoPlay,reduced)) {
                    val before = wake
                    snapshotFlow { wake }.first { it != before }
                    previous = 0L
                }
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else ((nanos-previous)/1e9).coerceIn(0.0,0.5)
                    previous = nanos
                    frame.value = driver.step(dt,autoPlay,reduced)
                }
            }
        }
    }
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF4F1EA))) {
        val width = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val cv = canvas.nativeCanvas
                cv.save()
                cv.scale(density.density,density.density)
                art.box(cv,width.value/2.0,top.value+150.0,frame.value.open,reduced)
                cv.restore()
            }
        }
        Row(Modifier.offset(20.dp,top+8.dp).width(width-40.dp).height(44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Postbox",fontFamily=fonts,fontWeight=FontWeight.Bold,fontSize=30.sp,letterSpacing=(-0.5).sp,color=Color(0xFF2B2F3A))
            Box(Modifier.size(44.dp).background(Color.White,CircleShape).clickable(onClickLabel="Write a letter") {}.semantics { contentDescription="Write a letter"; role=Role.Button },contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(21.dp)) {
                    val s=size.width/512
                    val path=Path().apply {
                        moveTo(288*s,80*s); lineTo(128*s,80*s); quadraticTo(80*s,80*s,80*s,128*s)
                        lineTo(80*s,384*s); quadraticTo(80*s,432*s,128*s,432*s)
                        lineTo(384*s,432*s); quadraticTo(432*s,432*s,432*s,384*s); lineTo(432*s,224*s)
                    }
                    drawPath(path,Color(0xFF2B2F3A),style=Stroke(32*s,cap=StrokeCap.Round))
                    drawLine(Color(0xFF2B2F3A),Offset(224*s,288*s),Offset(432*s,80*s),32*s,StrokeCap.Round)
                }
            }
        }
        Box(Modifier.offset(width/2-88.dp,top+88.4.dp).size(176.dp,193.6.dp)
            .semantics { contentDescription="Letterbox, 4 new letters. Tap to open or close"; role=Role.Button }
            .clickable { driver.toggle(); wake++; haptics.performHapticFeedback(HapticFeedbackType.Confirm) })
        Box(Modifier.offset(width/2+48.4.dp,top+75.2.dp).size(24.dp)
            .graphicsLayer { alpha=(1-MailboxInboxMotion.clamp(frame.value.open*3)).toFloat() }
            .background(Color(0xFF2B2F3A),CircleShape),contentAlignment=Alignment.Center) {
            Text("4",fontFamily=fonts,fontWeight=FontWeight.Bold,fontSize=13.sp,color=Color.White)
        }
        Text("Today’s post",Modifier.offset(20.dp,top+262.dp),fontFamily=fonts,fontWeight=FontWeight.SemiBold,fontSize=15.sp,color=Color(0xFF8A8578))
        for (i in 0..3) MailboxInboxLetter(i,width.value-32,top.value,fonts,frame) { driver.file(i); wake++ }
    }
}

@Composable
private fun MailboxInboxLetter(i: Int, width: Float, top: Float, fonts: FontFamily, frame: State<MailboxInboxMotion.Frame>, onFile: () -> Unit) {
    val m=MailboxInboxLetters[i]
    val density=LocalDensity.current.density
    var dx by remember { mutableFloatStateOf(0f) }
    Box(Modifier.offset(16.dp,(top+296+i*88).dp).size(width.dp,78.dp)
        .graphicsLayer {
            val f=frame.value
            val k=MailboxInboxMotion.letter(f,i)
            val fromY=150-8.8-296-i*88-39
            val shift=(0 until i).sumOf { MailboxInboxMotion.smoother(f.filed[it]*1.4-0.3)*88 }
            translationX=((-88*0.68*(1-k)).toFloat()+dx)*density
            translationY=(fromY*(1-k)-sin(PI*k)*60-shift).toFloat()*density
            rotationZ=((1-k)*(if(i%2==1)24 else -18)).toFloat()
            scaleX=(0.22+0.78*k).toFloat(); scaleY=scaleX
            alpha=if(k<0.02)0f else 1f
        }
        .pointerInput(i) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position
                val velocity=VelocityTracker()
                velocity.addPosition(down.uptimeMillis,start)
                var moved=false
                var x=0f
                do {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    velocity.addPosition(change.uptimeMillis,change.position)
                    x=(change.position.x-start.x)/density
                    if (x<12 || x>999) moved=true
                    if(moved) { dx=max(0f,x); change.consume() }
                } while(change.pressed)
                if(moved && (x>90 || velocity.calculateVelocity().x/density>700)) onFile()
                dx=0f
            }
        }
        .semantics(mergeDescendants=true) { contentDescription="${m.from}: ${m.text}. ${m.time}"; role=Role.Button; stateDescription="Swipe right to file it" }
    ) {
        Box(Modifier.fillMaxSize().shadow(10.dp,RoundedCornerShape(18.dp),ambientColor=Color(0x0F2B2F3A),spotColor=Color(0x0F2B2F3A)).background(Color.White,RoundedCornerShape(18.dp)))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha=(1-MailboxInboxMotion.clamp((MailboxInboxMotion.letter(frame.value,i)-0.45)/0.3)).toFloat() }
            .clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFF8EC)).border(1.dp,Color(0xFFEADFC8),RoundedCornerShape(18.dp))) {
            Box(Modifier.offset((width/2-60).dp,(-60).dp).size(120.dp).graphicsLayer { rotationZ=45f }.background(Color(0xFFF3E7CF)))
            Box(Modifier.offset((width-34).dp,10.dp).size(22.dp,26.dp).background(m.color,RoundedCornerShape(3.dp)))
        }
        Row(Modifier.fillMaxSize().graphicsLayer { alpha=MailboxInboxMotion.clamp((MailboxInboxMotion.letter(frame.value,i)-0.55)/0.3).toFloat() }.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).background(m.color,CircleShape),contentAlignment=Alignment.Center) {
                Text(m.from.take(1),fontFamily=fonts,fontWeight=FontWeight.Bold,fontSize=18.sp,color=Color.White)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top) {
                    Text(m.from,fontFamily=fonts,fontWeight=FontWeight.SemiBold,fontSize=16.sp,color=Color(0xFF2B2F3A))
                    Text(m.time,fontFamily=fonts,fontSize=13.sp,color=Color(0xFF9A958A))
                }
                Text(m.text,fontFamily=fonts,fontSize=14.sp,color=Color(0xFF6B6760),maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}
