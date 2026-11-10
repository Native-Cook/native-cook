package com.ngocdevv.nativecook.blocks.pickers.windchimetones

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Paint
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.min

private val WindChimeTonesCard=Color(0xff3a3a3b)
private val WindChimeTonesLabels=listOf("AirPlay","EQ","Add to pair")
private val WindChimeTonesTextStyle=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false))

@Composable
fun WindChimeTones(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val auto=autoPlay && !reduced
    val driver=remember(auto) { WindChimeTonesDriver(auto) }
    val art=remember { WindChimeTonesArt() }
    val audio=remember(context) { WindChimeTonesAudio(context) }
    val fonts=remember(context) {
        listOf("Regular","Medium","SemiBold").map { FontFamily(Font("windchimetones/windchimetones_Inter-$it.ttf",context.assets)) }
    }
    var tick by remember { mutableIntStateOf(0) }
    var running by remember(driver) { mutableStateOf(false) }
    var playing by remember(driver) { mutableStateOf(false) }
    var on by remember(driver) { mutableStateOf(true) }
    fun tap() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun powerBuzz() { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun togglePlay() {
        driver.togglePlay(audio::set,::tap); running=true; playing=driver.frame.playing; tick++
    }
    fun togglePower() {
        driver.togglePower(audio::set,::powerBuzz,::tap)
        running=true; on=driver.isOn; playing=driver.frame.playing; tick++
    }
    DisposableEffect(audio) { onDispose { audio.close() } }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=false
        onDispose { if(previous!=null) controller.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(driver,lifecycle,running) {
        if(!auto && !running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            driver.audibleAt?.let { audio.set(true,it) }
            var previous=0L
            try {
                while(auto || driver.running) {
                    withFrameNanos { now ->
                        val elapsed=min(.5,if(previous==0L) .016 else (now-previous)/1e9)
                        previous=now; driver.step(elapsed,auto)
                        if(playing!=driver.frame.playing) playing=driver.frame.playing
                        tick++
                        if(!driver.running && !auto) running=false
                    }
                }
            } finally { audio.set(false,0.0) }
        }
    }
    val top=WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom=WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xff2a2a2b)).clipToBounds()) {
        val l=remember(maxWidth,maxHeight,top,bottom) { WindChimeTonesLayout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom) }
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw=tick
            drawIntoCanvas { art.draw(it.nativeCanvas,l,driver.frame,density.density,reduced) }
        }
        WindChimeTonesPress(Modifier.offset((45*l.sx-22).dp,(l.y(103.0)-22).dp).size(44.dp),"Back",reduced) { WindChimeTonesIcon(WindChimeTonesIcons.back) }
        WindChimeTonesPress(Modifier.offset((356*l.sx-22).dp,(l.y(104.0)-22).dp).size(44.dp),"More",reduced) { WindChimeTonesIcon(WindChimeTonesIcons.more) }
        Box(Modifier.offset((317*l.sx-32).dp,(l.y(292.0)-32).dp).size(64.dp).graphicsLayer {
            @Suppress("UNUSED_VARIABLE") val redraw=tick
            ride(driver.frame.ride(l.powerBody,reduced),density.density)
        }) {
            WindChimeTonesPress(Modifier.fillMaxSize().shadow(12.dp,CircleShape).background(if(on) Color.White else Color(0xff5a5a5c),CircleShape),"Power",reduced,Role.Switch,on,::togglePower) {
                WindChimeTonesIcon(WindChimeTonesIcons.power,if(on) 0xfff0453a.toInt() else 0xffffffff.toInt())
            }
        }
        Text("Halden Audio",fontFamily=fonts[2],fontSize=(14.5/density.fontScale).sp,color=Color.White,style=WindChimeTonesTextStyle,
            modifier=Modifier.offset(y=(l.y(90.0)-10).dp).fillMaxWidth().wrapContentWidth().semantics { heading() })
        Text("Speaker",fontFamily=fonts[2],fontSize=(14.5/density.fontScale).sp,color=Color.White,style=WindChimeTonesTextStyle,
            modifier=Modifier.offset(y=(l.y(117.0)-10).dp).fillMaxWidth().wrapContentWidth())
        repeat(3) { i ->
            Box(Modifier.offset(l.toolX(i).dp,l.y(510.0).dp).size((94*l.sx).dp,(90*l.k).dp).graphicsLayer {
                @Suppress("UNUSED_VARIABLE") val redraw=tick
                ride(driver.frame.ride(l.toolBodies[i],reduced),density.density)
            }.background(WindChimeTonesCard,RoundedCornerShape(16.dp))) {
                WindChimeTonesPress(Modifier.fillMaxSize(),WindChimeTonesLabels[i],reduced) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        WindChimeTonesIcon(listOf(WindChimeTonesIcons.airPlay,WindChimeTonesIcons.eq,WindChimeTonesIcons.pair)[i])
                        Text(WindChimeTonesLabels[i],fontFamily=fonts[1],fontSize=(11.5/density.fontScale).sp,color=Color.White,style=WindChimeTonesTextStyle)
                    }
                }
            }
        }
        Box(Modifier.offset((37*l.sx).dp,l.y(625.0).dp).size((327*l.sx).dp,(195*l.k).dp).graphicsLayer {
            @Suppress("UNUSED_VARIABLE") val redraw=tick
            ride(driver.frame.ride(l.playerBody,reduced),density.density)
        }.background(WindChimeTonesCard,RoundedCornerShape(20.dp))) {
            WindChimeTonesIcon(WindChimeTonesIcons.speaker,modifier=Modifier.offset(((83-37)*l.sx-15).dp,(l.y(676.0)-l.y(625.0)-15).dp))
            Text("Now playing",fontFamily=fonts[2],fontSize=(14.5/density.fontScale).sp,color=Color.White,style=WindChimeTonesTextStyle,
                modifier=Modifier.offset(((128-37)*l.sx).dp,(l.y(664.0)-l.y(625.0)-10).dp))
            Text("Harbour Chimes – Tones at Dusk",fontFamily=fonts[0],fontSize=(13/density.fontScale).sp,color=Color.White.copy(alpha=.32f),style=WindChimeTonesTextStyle,
                maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.offset(((128-37)*l.sx).dp,(l.y(694.0)-l.y(625.0)-9).dp).width((200*l.sx).dp))
            Canvas(Modifier.offset(((63-37)*l.sx).dp,(l.y(724.0)-l.y(625.0)).dp).size(((338-63)*l.sx).dp,2.dp).clip(CircleShape)) {
                @Suppress("UNUSED_VARIABLE") val redraw=tick
                drawRect(Color.White.copy(alpha=.18f))
                drawRect(Color(0xffe6e6e8),size=size.copy(width=size.width*driver.frame.progress.toFloat()))
            }
            repeat(3) { i ->
                val icon=if(i==0) WindChimeTonesIcons.previous else if(i==2) WindChimeTonesIcons.next else if(playing) WindChimeTonesIcons.pause else WindChimeTonesIcons.play
                val label=if(i==0) "Previous" else if(i==2) "Next" else if(playing) "Pause" else "Play"
                WindChimeTonesPress(Modifier.offset(((doubleArrayOf(108.0,203.0,290.0)[i]-37)*l.sx-24).dp,(l.y(776.0)-l.y(625.0)-24).dp).size(48.dp),label,reduced,onClick={ if(i==1) togglePlay() }) { WindChimeTonesIcon(icon) }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val redraw=tick
            val f=driver.frame; val radius=22*(1-.12*f.press).toFloat()*density.density
            val center=Offset((203*l.sx).toFloat()*density.density,l.y(776.0).toFloat()*density.density)
            drawCircle(Color.White.copy(alpha=(.1*f.finger).toFloat()),radius,center)
            drawCircle(Color.White.copy(alpha=(.6*f.finger).toFloat()),radius-.75f*density.density,center,style=Stroke(1.5f*density.density))
        }
    }
}

// Graphics layers apply the same centred T · R · S transform as the RN transform array.
private fun GraphicsLayerScope.ride(pose: WindChimeTonesMotion.Pose,density: Float) {
    translationX=pose.x.toFloat()*density
    translationY=pose.y.toFloat()*density
    rotationZ=Math.toDegrees(pose.rotation).toFloat()
    scaleX=pose.scaleX.toFloat()
    scaleY=pose.scaleY.toFloat()
}

@Composable
private fun WindChimeTonesIcon(icon: WindChimeTonesIcons.Icon,tint: Int?=null,modifier: Modifier=Modifier) {
    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier.size(icon.w.dp,icon.h.dp)) {
        drawIntoCanvas {
            val c=it.nativeCanvas
            c.save(); c.scale(density,density)
            WindChimeTonesIcons.draw(c,icon,paint,tint); c.restore()
        }
    }
}

@Composable
private fun WindChimeTonesPress(modifier: Modifier,label: String,reduced: Boolean,role: Role=Role.Button,checked: Boolean?=null,onClick: ()->Unit={},content: @Composable ()->Unit) {
    val interaction=remember { MutableInteractionSource() }
    val down by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(down && !reduced) .96f else 1f,tween(150,easing=CubicBezierEasing(.23f,1f,.32f,1f)),label="Press scale")
    val alpha by animateFloatAsState(if(down && reduced) .72f else 1f,tween(150,easing=CubicBezierEasing(.23f,1f,.32f,1f)),label="Press opacity")
    Box(modifier.clickable(interactionSource=interaction,indication=null,role=role,onClick=onClick)
        .semantics(mergeDescendants=true) { contentDescription=label; if(checked!=null) toggleableState=if(checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }
        .graphicsLayer { scaleX=scale; scaleY=scale; this.alpha=alpha },contentAlignment=Alignment.Center) { content() }
}
