package com.ngocdevv.nativecook.blocks.pickers.wavefieldpad

import android.animation.ValueAnimator
import android.content.pm.ApplicationInfo
import android.os.Build
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.HapticFeedbackConstants
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WFP_W = 402.0
private const val PAD_X = 24.0
private const val PAD_Y = 356.0
private const val PAD_W = 354.0
private const val PUCK_R = 44.0

/** Wave Field Pad, with a shared autoplay/live frame and native accessibility actions. */
@Composable
fun WaveFieldPadDemo(autoPlay: Boolean = true, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val close = onClose ?: { backDispatcher?.onBackPressed() }
    fun haptic(event: String) { val kind = when(event) { "sel" -> HapticFeedbackConstants.CLOCK_TICK; "rigid" -> HapticFeedbackConstants.LONG_PRESS; "medium" -> HapticFeedbackConstants.CONTEXT_CLICK; else -> HapticFeedbackConstants.VIRTUAL_KEY }; view.performHapticFeedback(kind) }
    val art = remember(context) { WaveFieldPadArt.load(context) }
    val shaders = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { WaveFieldPadShaders() }.getOrNull() else null }
    var reduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var talkBack by remember { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(context, accessibility) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = !ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { talkBack = it }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { context.contentResolver.unregisterContentObserver(observer); accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    var pinnedTime by remember(activity) {
        mutableStateOf(if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            activity?.intent?.getStringExtra("waveFieldPadTime")?.toDoubleOrNull()?.takeIf { it.isFinite() }
        } else null)
    }
    var time by remember { mutableDoubleStateOf(pinnedTime ?: 0.0) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var frame by remember { mutableStateOf(WaveFieldPadMotion.frameAt(time)) }
    var live by remember { mutableStateOf<WaveFieldPadLive?>(null) }
    var tab by remember { mutableStateOf(0) }
    var lastTap by remember { mutableDoubleStateOf(-9.0) }
    var beatOffset by remember { mutableDoubleStateOf(0.0) }
    fun capture(): WaveFieldPadLive {
        pinnedTime = null
        live?.let { return it }
        return WaveFieldPadLive(frame, WaveFieldPadMotion.frameAt(time-1.0/60), elapsed, ::haptic).also { it.policy(reduced); live=it }
    }
    LaunchedEffect(reduced, talkBack, autoPlay) {
        if (reduced || talkBack || !autoPlay) capture().policy(reduced) else live?.policy(false)
    }
    LaunchedEffect(autoPlay, reduced, talkBack, pinnedTime) {
        if (pinnedTime != null) return@LaunchedEffect
        var prior=withFrameNanos { it }
        while (true) withFrameNanos { now ->
            val dt=((now-prior)/1e9).coerceIn(0.0,.05);prior=now;elapsed+=dt
            val l=live
            if(l==null) { time=(time+dt)%WaveFieldPadMotion.LOOP; frame=WaveFieldPadMotion.frameAt(time).let { it.copy(beat=it.beat+beatOffset) } }
            else { time=(time+dt)%WaveFieldPadMotion.LOOP; l.step(dt,autoPlay&&!reduced&&!talkBack); frame=l.frame(time); if(l.readyForAuto){beatOffset=frame.beat%2;time=0.0;live=null;frame=WaveFieldPadMotion.frameAt(0.0).copy(beat=beatOffset)} }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF151216))) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val safeTop = insets.calculateTopPadding()
        val availableBottom = maxHeight - insets.calculateBottomPadding() - 12.dp
        val unit = minOf(maxWidth / 402, availableBottom / 780, (availableBottom - safeTop) / 704).coerceAtLeast(.01.dp)
        val top = (safeTop - unit * 76).coerceAtLeast(0.dp)
        Box(Modifier.offset(x = (maxWidth - unit * 402) / 2, y = top).size(unit * 402, (maxHeight - top).coerceAtLeast(1.dp))) {
        Canvas(Modifier.fillMaxSize().pointerInput(unit, top) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val scale=size.width.toDouble().coerceAtLeast(1.0)/WFP_W
                val sx=down.position.x/scale; val sy=down.position.y/scale
                if(sx !in PAD_X..(PAD_X+PAD_W)||sy !in PAD_Y..(PAD_Y+PAD_W)) return@awaitEachGesture
                val l=capture();l.down((sx-PAD_X-PUCK_R)/266,1-(sy-PAD_Y-PUCK_R)/266)
                val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                var vx=0.0;var vy=0.0;var moved=false;var finished=false
                try {
                    while(true) {
                        val event=awaitPointerEvent();val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        val x=change.position.x/scale;val y=change.position.y/scale;val t=change.uptimeMillis/1000.0
                        if(change.pressed){tracker.addPosition(change.uptimeMillis,change.position);moved=moved||hypot(x-sx,y-sy)>6;l.move((x-PAD_X-PUCK_R)/266,1-(y-PAD_Y-PUCK_R)/266);change.consume()}
                        else { tracker.addPosition(change.uptimeMillis,change.position);val velocity=tracker.calculateVelocity();vx=velocity.x/scale/266;vy=-velocity.y/scale/266;l.release(vx,vy);if(!moved&&t-lastTap<.28) l.reset();if(!moved)lastTap=t;finished=true;break }
                    }
                } finally { if(!finished && live===l) l.release(0.0,0.0) }
            }
        }) {
            val scale=size.width.toDouble()/WFP_W;val height=size.height/scale
            drawIntoCanvas { c ->
                c.nativeCanvas.save();c.nativeCanvas.scale(scale.toFloat(),scale.toFloat())
                art.draw(c.nativeCanvas,height,frame, time,reduced,tab,shaders,(1/scale).toFloat())
                c.nativeCanvas.restore()
            }
        }
        Box(Modifier.offset(x=unit*24,y=unit*356).size(unit*354).semantics {
            contentDescription="Wave field pad"
            stateDescription="Mix ${(frame.puck.u.coerceIn(0.0,1.0)*100).roundToInt()} percent, intensity ${(frame.puck.v.coerceIn(0.0,1.0)*100).roundToInt()} percent, ${frame.hz.roundToInt()} hertz"
            progressBarRangeInfo=ProgressBarRangeInfo(frame.puck.u.coerceIn(0.0,1.0).toFloat(),0f..1f,100)
            setProgress { target -> capture().setMix(target.toDouble());frame=live!!.frame(time);true }
            customActions=listOf(
                CustomAccessibilityAction("Mix more waves") { capture().adjust(.1,0.0);frame=live!!.frame(time);true },
                CustomAccessibilityAction("Mix more sounds") { capture().adjust(-.1,0.0);frame=live!!.frame(time);true },
                CustomAccessibilityAction("Increase intensity") { capture().adjust(0.0,.1);frame=live!!.frame(time);true },
                CustomAccessibilityAction("Decrease intensity") { capture().adjust(0.0,-.1);frame=live!!.frame(time);true },
                CustomAccessibilityAction("Recenter mix") { capture().reset();frame=live!!.frame(time);true },
            )
        })
        Box(Modifier.offset(x=unit*338,y=unit*76).size(unit*44).semantics { contentDescription="Close";role=Role.Button }.clickable { haptic("light"); close() })
        listOf(12 to 134,156 to 112,278 to 112).forEachIndexed { index, item ->
            Box(Modifier.offset(x=unit*item.first,y=unit*736).size(width=unit*item.second,height=unit*44).semantics { contentDescription=listOf("Tune mix","Sounds","Timers")[index];role=Role.Tab;selected=tab==index }.clickable { tab=index; haptic("sel") })
        }
        }
    }
}

private class WaveFieldPadArt(private val regular: Typeface, private val medium: Typeface, private val tabular: Typeface) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true; isLinearText = true }
    private val pad = RectF(PAD_X.toFloat(), PAD_Y.toFloat(), (PAD_X + PAD_W).toFloat(), (PAD_Y + PAD_W).toFloat())
    private val path = Path()
    private val fallback = WaveFieldPadFallback()
    private var shaderFault = false

    fun draw(canvas: AndroidCanvas, h: Double, frame: WaveFieldPadMotion.Frame, clock: Double, reduced: Boolean, tab: Int, shaders: WaveFieldPadShaders?, pixel: Float) {
        val f=if(reduced) frame.copy(rip0=frame.rip0.copy(amplitude=0.0),rip1=frame.rip1.copy(amplitude=0.0),ring=0.0,scale=1.0,jitter=0.0,scatter=WaveFieldPadMotion.P(0.0,0.0)) else frame
        val u=f.puck.u;val v=f.puck.v
        val px=PAD_X+PUCK_R+u*(PAD_W-2*PUCK_R);val py=PAD_Y+PUCK_R+(1-v)*(PAD_W-2*PUCK_R)
        var useShader=shaders!=null && Build.VERSION.SDK_INT>=33 && !shaderFault
        if(useShader) try { shaders!!.drawPage(canvas,h.toFloat(),f) } catch(error:RuntimeException) { android.util.Log.e("WaveFieldPad","Page shader failed; using Canvas fallback",error);shaderFault=true;useShader=false }
        if(!useShader) {
            fill.shader=LinearGradient(0f,0f,0f,h.toFloat(),intArrayOf(0xFF1A1F1B.toInt(),0xFF151216.toInt(),0xFF131115.toInt()),null,Shader.TileMode.CLAMP);fill.alpha=255;canvas.drawRect(0f,0f,402f,h.toFloat(),fill);fill.shader=null
            val values=WaveFieldPadRenderModel.values(f,pixel.toDouble(),reduced)
            fill.shader=RadialGradient(201f,180f,240f,intArrayOf(0x8022302A.toInt(),0x0022302A),null,Shader.TileMode.CLAMP);fill.alpha=255;canvas.drawCircle(201f,180f,240f,fill);fill.shader=null
            fill.shader=RadialGradient(values.ambX.toFloat(),values.ambY.toFloat(),150f,intArrayOf(0x552F1F0C,0x002F1F0C),null,Shader.TileMode.CLAMP);fill.alpha=255;canvas.drawCircle(values.ambX.toFloat(),values.ambY.toFloat(),150f,fill);fill.shader=null
        }
        drawWave(canvas,f)
        if(useShader) try { shaders!!.drawPad(canvas,f,reduced,pixel) } catch(error:RuntimeException) { android.util.Log.e("WaveFieldPad","Pad shader failed; using Canvas fallback",error);shaderFault=true;useShader=false }
        if(!useShader) fallback.draw(canvas,f,reduced)
        drawPuck(canvas,px,py,f);drawHeader(canvas,f);drawLabels(canvas,px,py);drawTabs(canvas,tab)
    }

    private fun drawWave(canvas:AndroidCanvas,f:WaveFieldPadMotion.Frame) {
        val strands=intArrayOf(-2,2,-1,1,0);val alphas=doubleArrayOf(.14,.22,.32,.48,1.0)
        for(index in strands.indices) {
            path.reset();var started=false
            for(i in 0..96) { val x=i/96.0;if(abs(x-.5)<.105){started=false;continue};val sx=30+342*x;val sy=WaveFieldPadRenderModel.waveY(f,strands[index],x)
                if(!started){path.moveTo(sx.toFloat(),sy.toFloat());started=true}else path.lineTo(sx.toFloat(),sy.toFloat()) }
            line.color=AndroidColor.argb((alphas[index]*255).roundToInt(),63,214,168);line.strokeWidth=if(strands[index]==0)2.5f else 1.5f;canvas.drawPath(path,line)
        }
        val loops=(1+3*f.puck.u.coerceIn(0.0,1.0)).roundToInt()
        for(i in 1 until loops){val x=i.toDouble()/loops;if(abs(x-.5)<=.135)continue;val alpha=(f.flash*f.lock).coerceIn(0.0,1.0);if(alpha<=0)continue
            val cx=(30+342*x).toFloat();fill.shader=RadialGradient(cx,180f,9f,intArrayOf(AndroidColor.argb((alpha*115).roundToInt(),255,201,120),0x00FFC978),null,Shader.TileMode.CLAMP);fill.alpha=255;canvas.drawCircle(cx,180f,9f,fill);fill.shader=null;fill.color=AndroidColor.argb((alpha*255).roundToInt(),255,231,189);canvas.drawCircle(cx,180f,3.5f,fill)
        }
    }

    private fun drawHeader(canvas:AndroidCanvas,f:WaveFieldPadMotion.Frame) {
        text.typeface=tabular;text.textSize=30f;val hzWidth=19f; text.typeface=medium;val suffixWidth=text.measureText("Hz");val left=201f-(hzWidth+suffixWidth)/2
        val roll=WaveFieldPadRenderModel.hzRoll(f.hz);canvas.save();canvas.clipRect(left,152f,left+hzWidth,188f)
        for(i in 1..4) label(canvas,i.toString(),left+hzWidth/2,(170-(text.ascent()+text.descent())/2+(i-roll)*36).toFloat(),30f,0xFFF2F4F1.toInt(),tabular,Paint.Align.CENTER)
        canvas.restore();text.typeface=medium;text.textSize=30f;label(canvas,"Hz",left+hzWidth,170-(text.ascent()+text.descent())/2,30f,0xFFF2F4F1.toInt(),medium,Paint.Align.LEFT)
        val band=WaveFieldPadRenderModel.smooth(1.42,1.58,f.hz);canvas.save();canvas.clipRect(0f,194f,402f,214f)
        text.typeface=regular;text.textSize=15f;val baseline=204-(text.ascent()+text.descent())/2
        label(canvas,"Deep Delta",201f,(baseline-band*20).toFloat(),15f,0xFFE3E7E2.toInt(),regular,Paint.Align.CENTER);label(canvas,"Delta",201f,(baseline+(1-band)*20).toFloat(),15f,0xFFE3E7E2.toInt(),regular,Paint.Align.CENTER);canvas.restore()
        text.typeface=medium;text.textSize=22f;label(canvas,"Shape tonight's mix",201f,268-(text.ascent()+text.descent())/2,22f,0xFFF2F4F1.toInt(),medium,Paint.Align.CENTER)
        text.typeface=regular;text.textSize=13f;label(canvas,"Slide the ember to balance sounds and waves.",201f,298-(text.ascent()+text.descent())/2,13f,0xFF7D857D.toInt(),regular,Paint.Align.CENTER)
        line.color=0xFFE9ECE8.toInt();line.strokeWidth=2f;canvas.drawLine(355f,93f,365f,103f,line);canvas.drawLine(365f,93f,355f,103f,line)
    }

    private fun drawGrid(canvas: AndroidCanvas, px: Double, py: Double) {
        line.strokeWidth = 1.5f
        for (i in 0..14) {
            val x = PAD_X + i * PAD_W / 14; val y = PAD_Y + i * PAD_W / 14
            val nearX = (1 - min(1.0, abs(x - px) / 110.0)); val nearY = (1 - min(1.0, abs(y - py) / 110.0))
            line.color = AndroidColor.argb((24 + 65 * nearX).roundToInt(), 255, 190, 107); canvas.drawLine(x.toFloat(), PAD_Y.toFloat(), x.toFloat(), (PAD_Y + PAD_W).toFloat(), line)
            line.color = AndroidColor.argb((24 + 65 * nearY).roundToInt(), 255, 190, 107); canvas.drawLine(PAD_X.toFloat(), y.toFloat(), (PAD_X + PAD_W).toFloat(), y.toFloat(), line)
        }
    }

    private fun drawArcs(canvas: AndroidCanvas, px: Double, py: Double) {
        line.color = 0x48FFE2B0; line.strokeWidth = 2f
        for (cx in listOf(PAD_X, PAD_X + PAD_W)) {
            val cy = PAD_Y + PAD_W / 2
            val radius = max(0.0, hypot(px - cx, py - cy) - PUCK_R)
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), line)
            line.color = 0x30FFE2B0; canvas.drawCircle(cx.toFloat(), cy.toFloat(), 56f, line)
        }
    }

    private fun drawPuck(canvas:AndroidCanvas,x:Double,y:Double,f:WaveFieldPadMotion.Frame) {
        val xx=x.toFloat();val yy=y.toFloat();val r=(44*f.scale).toFloat();val values=WaveFieldPadRenderModel.values(f,1.0,false)
        val alpha=(f.halo/.55).coerceIn(0.0,1.0);fill.shader=RadialGradient(xx,yy,100f,intArrayOf(AndroidColor.argb((140*alpha).roundToInt(),240,128,30),AndroidColor.argb((140*alpha).roundToInt(),240,128,30),AndroidColor.argb((46*alpha).roundToInt(),240,128,30),0x00F0801E),floatArrayOf(0f,.44f,.72f,1f),Shader.TileMode.CLAMP);fill.alpha=255;canvas.drawCircle(xx,yy,100f,fill);fill.shader=null
        fill.shader=RadialGradient(xx,yy+3,r+14,intArrayOf(0x59000000,0x55000000,0x00000000),floatArrayOf(0f,.7f,1f),Shader.TileMode.CLAMP);fill.alpha=255;canvas.drawCircle(xx,yy+3,r+14,fill);fill.shader=null
        val dx=values.dentX-values.puckX;val dy=values.dentY-values.puckY;val len=hypot(dx,dy);val shift=if(len>.001)min(len,4.0)/len else 0.0
        fill.shader=RadialGradient((x-.18*r+dx*shift).toFloat(),(y-.2*r+dy*shift).toFloat(),r*1.2f,intArrayOf(0xFFF4D28C.toInt(),0xFFF4D28C.toInt(),0xFFF79A2E.toInt(),0xFFEE7A1A.toInt(),0xFFC85A14.toInt()),floatArrayOf(0f,.16f,.5f,.84f,1f),Shader.TileMode.CLAMP);fill.alpha=255;canvas.drawCircle(xx,yy,r,fill);fill.shader=null
        line.color=0x99FFC07A.toInt();line.strokeWidth=1.5f;canvas.drawCircle(xx,yy,r,line)
        line.shader=LinearGradient(xx-r,yy-r,xx+.4f*r,yy+.4f*r,0x99FFE2B0.toInt(),0x00FFE2B0,Shader.TileMode.CLAMP);line.alpha=255;canvas.drawCircle(xx,yy,r,line);line.shader=null
        canvas.save();canvas.translate(xx-.4f*r,yy-.52f*r);canvas.rotate(-38f);fill.shader=LinearGradient(0f,-4f,0f,4f,0x8CFFFFFF.toInt(),0x14FFFFFF,Shader.TileMode.CLAMP);fill.alpha=255;canvas.drawOval(-10f,-4f,10f,4f,fill);fill.shader=null;canvas.restore()
        line.color=0xFFFFF3DC.toInt();line.strokeWidth=2f;val d=(8*f.scale).toFloat();canvas.drawLine(xx-d,yy,xx+d,yy,line);canvas.drawLine(xx,yy-d,xx,yy+d,line)
    }

    private fun drawLabels(canvas: AndroidCanvas, px: Double, py: Double) {
        fun draw(value:String,x:Float,y:Float,rotation:Float) {
            val visibility=WaveFieldPadRenderModel.smooth(38.0,66.0,hypot(px-x,py-y))
            if(visibility<=0) return
            canvas.save();canvas.rotate(rotation,x,y);text.setShadowLayer(3f,0f,1f,0xCC000000.toInt())
            label(canvas,value,x,y+5,13f,AndroidColor.argb((visibility*255).roundToInt(),227,231,226),medium,Paint.Align.CENTER)
            text.clearShadowLayer();canvas.restore()
        }
        draw("Intense",201f,378f,0f);draw("Easy",201f,688f,0f);draw("Sounds",46f,533f,-90f);draw("Waves",356f,533f,90f)
    }

    private fun drawTabs(canvas:AndroidCanvas,selectedTab:Int) {
        val y=736f;val xs=floatArrayOf(12f,156f,278f);val widths=floatArrayOf(134f,112f,112f);val names=arrayOf("Tune mix","Sounds","Timers")
        for(i in xs.indices){val color=if(i==selectedTab)0xFF17161A.toInt() else 0xFFE6E6E4.toInt();fill.color=if(i==selectedTab)0xFFF6F6F4.toInt() else 0xFF1F1C23.toInt();canvas.drawRoundRect(RectF(xs[i],y,xs[i]+widths[i],y+44),22f,22f,fill)
            text.typeface=medium;text.textSize=14f;val tw=text.measureText(names[i]);val left=xs[i]+(widths[i]-18-7-tw)/2;drawIcon(canvas,i,left,y+13,color);label(canvas,names[i],left+25,y+22-(text.ascent()+text.descent())/2,14f,color,medium,Paint.Align.LEFT)
        }
    }
    private fun drawIcon(canvas:AndroidCanvas,index:Int,x:Float,y:Float,color:Int){
        canvas.save();canvas.translate(x,y);canvas.scale(.75f,.75f);line.color=color;line.strokeWidth=2f;path.reset()
        when(index){
            0->{path.moveTo(3.5f,8.5f);path.lineTo(3.5f,6.5f);path.quadTo(3.5f,3.5f,6.5f,3.5f);path.lineTo(8.5f,3.5f);path.moveTo(15.5f,3.5f);path.lineTo(17.5f,3.5f);path.quadTo(20.5f,3.5f,20.5f,6.5f);path.lineTo(20.5f,8.5f);path.moveTo(20.5f,15.5f);path.lineTo(20.5f,17.5f);path.quadTo(20.5f,20.5f,17.5f,20.5f);path.lineTo(15.5f,20.5f);path.moveTo(8.5f,20.5f);path.lineTo(6.5f,20.5f);path.quadTo(3.5f,20.5f,3.5f,17.5f);path.lineTo(3.5f,15.5f);canvas.drawPath(path,line);canvas.drawCircle(12f,12f,2.75f,line)}
            1->{path.moveTo(4f,9.75f);path.lineTo(7.25f,9.75f);path.lineTo(12f,5.5f);path.lineTo(12f,18.5f);path.lineTo(7.25f,14.25f);path.lineTo(4f,14.25f);path.close();canvas.drawPath(path,line);canvas.drawArc(11.4f,8f,19.6f,16f,-43f,86f,false,line);canvas.drawArc(10.4f,4f,26.4f,20f,-43f,86f,false,line)}
            else->{canvas.drawArc(3f,3f,21f,21f,-72f,-288f,false,line);canvas.drawLine(12f,7.8f,12f,12.3f,line);canvas.drawLine(12f,12.3f,15f,14.2f,line);path.moveTo(19.3f,2.6f);path.quadTo(19.6f,4.5f,21.5f,4.8f);path.quadTo(19.6f,5.1f,19.3f,7f);path.quadTo(19f,5.1f,17.1f,4.8f);path.quadTo(19f,4.5f,19.3f,2.6f);path.close();canvas.drawPath(path,line)}
        };canvas.restore()
    }

    private fun label(canvas: AndroidCanvas, value: String, x: Float, y: Float, size: Float, color: Int, face: Typeface, align: Paint.Align) { text.typeface = face; text.textSize = size; text.color = color; text.textAlign = align; canvas.drawText(value, x, y, text) }

    companion object {
        fun load(context: Context): WaveFieldPadArt {
            val assets = context.assets
            fun font(name: String) = Typeface.createFromAsset(assets, "wavefieldpad/wavefieldpad_$name.ttf")
            return WaveFieldPadArt(font("Outfit_Regular"), font("Outfit_Medium"), font("OutfitTabular_Medium"))
        }
    }
}

private fun rubber(raw: Double): Double { val range = WaveFieldPadMotion.RANGE; val cap = WaveFieldPadMotion.MAX_LIVE_OVERSHOOT_PT; return when { raw > 1 -> 1 + cap * kotlin.math.tanh((raw - 1) * range / cap) / range; raw < 0 -> -cap * kotlin.math.tanh(-raw * range / cap) / range; else -> raw } }

@Preview
@Composable
private fun WaveFieldPadDemoPreview() { WaveFieldPadDemo() }
