package com.ngocdevv.nativecook.blocks.charts.retirementgapdial

import android.graphics.Typeface
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

private const val LOOP = 8.0
// Round the actual binary Double, as Swift's printf does, rather than its shortest decimal string.
private fun retirementAmount(value: Double) = BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN).toPlainString()

@Composable
private fun RetirementGapDialStatusBar() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = true
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

private fun retirementClamp(v: Double) = v.coerceIn(0.0, 1.0)
private fun retirementSmooth(v: Double): Double { val x=retirementClamp(v);return x*x*(3-2*x) }
private fun retirementArrive(q:Double):Double {val x=retirementClamp(q);return retirementSmooth(x)+2.4*(x*x*x-x*x)}
private fun retirementCatch(dt:Double,velocity:Double=1.045*2.4/1.78):Double {val x=max(0.0,dt);return velocity/17*exp(-7.5*x)*sin(17*x)*(1-retirementSmooth((x-.5)/.3))}
private fun retirementRubber(v:Double):Double=if(v<0)-.08*(1-exp(v/.08)) else if(v>1)1+.08*(1-exp(-(v-1)/.08)) else v
private fun retirementPhase(time: Double): Double {
    val t=time%LOOP
    return when {
        t<1.1 -> 0.0
        t<1.32 -> -.045*retirementSmooth((t-1.1)/.22)
        t<3.1 -> -.045+1.045*retirementArrive((t-1.32)/1.78)
        t<3.9 -> 1+retirementCatch(t-3.1)
        t<5.2 -> 1.0
        t<6.8 -> 1-retirementSmooth((t-5.2)/1.6)
        else -> 0.0
    }
}
@Composable
fun RetirementGapDial(autoPlay: Boolean=false, previewTime: Double?=null) {
    RetirementGapDialStatusBar()
    val context=LocalContext.current;val haptics=LocalHapticFeedback.current
    val topPx=WindowInsets.statusBars.getTop(LocalDensity.current)
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    var clock by remember { mutableDoubleStateOf(0.0) };var touched by remember { mutableDoubleStateOf(-100.0) }
    var manual by remember { mutableDoubleStateOf(0.0) };var resolveAt by remember { mutableDoubleStateOf(-100.0) };var resolveFrom by remember { mutableDoubleStateOf(0.0) };var caught by remember { mutableStateOf(false) };var isTouching by remember { mutableStateOf(false) }
    var releaseFrom by remember {mutableDoubleStateOf(0.0)};var releaseVelocity by remember {mutableDoubleStateOf(0.0)};var sampleAt by remember {mutableDoubleStateOf(0.0)};var sampleValue by remember {mutableDoubleStateOf(0.0)};var dragAngle by remember {mutableDoubleStateOf(0.0)};var dragStart by remember {mutableDoubleStateOf(0.0)}
    val paint=remember { android.graphics.Paint(3) };val glow=remember { android.graphics.Paint(3).apply { style=android.graphics.Paint.Style.STROKE;strokeCap=android.graphics.Paint.Cap.ROUND;color=0xFFF5222F.toInt();maskFilter=android.graphics.BlurMaskFilter(23f,android.graphics.BlurMaskFilter.Blur.NORMAL) } }
    var awake by remember{mutableStateOf(false)}
    LaunchedEffect(autoPlay,reduced,previewTime,awake) { if(reduced||previewTime!=null||(!autoPlay&&!awake))return@LaunchedEffect;var first=0L;val baseClock=clock;while(autoPlay||awake)withFrameNanos { n->if(first==0L)first=n;clock=baseClock+(n-first)/1e9;if(!autoPlay&&!isTouching&&touched>=0&&clock-touched>=2.6)awake=false } }
    fun value():Double { previewTime?.let{return retirementPhase(it)};if(isTouching)return manual;if(reduced)return if(touched<0)0.0 else manual;val rd=clock-resolveAt;if(rd<.7)return resolveFrom+(1-resolveFrom)*retirementArrive(rd/.7);if(rd<1.5)return 1+retirementCatch(rd-.7,(1-resolveFrom)*2.4/.7);val dt=clock-touched;if(dt<.65){val d=releaseFrom-manual;return manual+(d+(releaseVelocity+16*d)*dt)*exp(-16*dt)*(1-retirementSmooth((dt-.45)/.2))};if(dt<2)return manual;if(dt<2.6)return manual*(1-retirementSmooth((dt-2)/.6));return if(autoPlay)retirementPhase(if(touched<0)clock else dt-2.6)else 0.0 }
    fun resolve(){awake=true;resolveFrom=value();resolveAt=clock;touched=clock;manual=1.0;releaseFrom=1.0;releaseVelocity=0.0;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val overlayScale=maxWidth.value/393f
    val overlayHeight=maxHeight.value/overlayScale
    Canvas(Modifier.fillMaxSize().background(Color(0xFFDEDEDE)).semantics {
        contentDescription = "Retirement goal dial. Drag the marker to close the gap."
        progressBarRangeInfo = ProgressBarRangeInfo(manual.toFloat().coerceIn(0f,1f),0f..1f)
        setProgress { target -> awake=true;manual=target.toDouble().coerceIn(0.0,1.0);releaseFrom=manual;releaseVelocity=0.0;touched=clock;resolveAt=-100.0;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);true }
        customActions = listOf(CustomAccessibilityAction("Close 20 percent of the gap") { awake=true;manual=(value()+.2).coerceIn(0.0,1.0);releaseFrom=manual;releaseVelocity=0.0;touched=clock;true },CustomAccessibilityAction("Reopen 20 percent of the gap") { awake=true;manual=(value()-.2).coerceIn(0.0,1.0);releaseFrom=manual;releaseVelocity=0.0;touched=clock;true })
    }.pointerInput(Unit){detectTapGestures {p->val s=size.width/393f;val cy=size.height/s-220;if(p.y/s in (cy+42)..(cy+86)&&abs(p.x/s-196.5)<55)resolve() }}.pointerInput(Unit){
        detectDragGestures(onDragStart={p->awake=true;manual=value();dragStart=manual;val s=size.width/393f;val cy=size.height/s-220;dragAngle=atan2((p.y/s-cy).toDouble(),p.x/s-196.5)*180/PI;isTouching=true;touched=clock;caught=false;resolveAt=-100.0;sampleAt=clock;sampleValue=manual;releaseVelocity=0.0},onDragEnd={releaseFrom=manual;manual=retirementClamp(manual+(releaseVelocity*.12).coerceIn(-.08,.08));isTouching=false;touched=clock},onDragCancel={releaseFrom=manual;manual=retirementClamp(manual);releaseVelocity=0.0;isTouching=false;touched=clock},onDrag={change,_->
            change.consume();val s=size.width/393f;val cy=size.height/s-220;val a=atan2((change.position.y/s-cy).toDouble(),change.position.x/s-196.5)*180/PI
            val next=retirementRubber(dragStart+(a-dragAngle)/19.5);val dt=clock-sampleAt;if(dt>.002)releaseVelocity=.35*releaseVelocity+.65*((next-sampleValue)/dt).coerceIn(-3.0,3.0);manual=next;sampleValue=next;sampleAt=clock;touched=clock
            if(manual>=.99&&!caught){caught=true;haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)}
        })
    }) {
        val s=size.width/393;val h=size.height/s;val cy=h-220;val top=topPx/s;val v=value();val amount=retirementClamp(v);val done=retirementSmooth((amount-.90)/.10);val goalCover=retirementSmooth((amount-.86)/.10);val impact=((v-1)/.05).coerceIn(0.0,1.0);val angle=(-109.5+19.5*v).toFloat();val a=angle*PI/180;val thumb=Offset((196.5+226*cos(a)).toFloat(),(cy+226*sin(a)).toFloat())
        scale(s,s,Offset.Zero) {
            fun ring(start:Float,end:Float,color:Color,width:Float=51f,r:Float=226f){drawArc(color,start,(end-start).coerceAtLeast(.001f),false,Offset(196.5f-r,cy-r),Size(r*2,r*2),style=Stroke(width,cap=StrokeCap.Round))}
            fun text(t:String,x:Float,y:Float,size:Float,color:Int=0xFF141414.toInt(),bold:Boolean=false,center:Boolean=false,alpha:Double=1.0){paint.style=android.graphics.Paint.Style.FILL;paint.color=color;paint.alpha=(255*alpha).toInt();paint.textSize=size;paint.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);paint.textAlign=if(center)android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT;drawContext.canvas.nativeCanvas.drawText(t,x,y-(paint.ascent()+paint.descent())/2,paint)}
            ring(-171f,-9f,Color(0xFFC7C7C7));glow.strokeWidth=69f;glow.alpha=(84*(1-amount)).toInt();drawContext.canvas.nativeCanvas.drawArc(196.5f-226,cy-226,196.5f+226,cy+226,min(angle,-90.01f),-90-min(angle,-90.01f),false,glow)
            drawLine(Color.Black.copy(alpha=(.055+.04*amount).toFloat()),Offset(196.5f,cy-275),Offset(196.5f,cy-77),1f);drawLine(Color.Black.copy(alpha=.1f),Offset((196.5+282*cos(a)).toFloat(),(cy+282*sin(a)).toFloat()),Offset((196.5+88*cos(a)).toFloat(),(cy+88*sin(a)).toFloat()),1.4f,pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f,6f)))
            if(goalCover<1)ring(min(angle,-90.01f),-90f,Color(0xFFF5222F).copy(alpha=(1-goalCover).toFloat()));drawCircle(Color(0xFFB91824).copy(alpha=(1-goalCover).toFloat()),17f,Offset(196.5f,cy-226));ring(-171f,angle,Color(0xFFF5F5F5));ring(-171f,angle-.5f,Color.White.copy(alpha=.7f),.7f,250.5f);drawCircle(Color(0xFFF5F5F5).copy(alpha=goalCover.toFloat()),17f,Offset(196.5f,cy-226));drawCircle(Color.White.copy(alpha=(done*impact*.6).toFloat()),(20+impact*5).toFloat(),Offset(196.5f,cy-226),style=Stroke(1.5f));drawOval(Color(0xFFDDDDDD),Offset(thumb.x-(17*(1+impact*.18)).toFloat(),thumb.y-(17*(1-impact*.14)).toFloat()),Size((34*(1+impact*.18)).toFloat(),(34*(1-impact*.14)).toFloat()));drawContext.canvas.nativeCanvas.save();drawContext.canvas.nativeCanvas.rotate(angle+109.5f,thumb.x,thumb.y);text("↗",thumb.x,thumb.y,25f,0xFF292929.toInt(),true,true);drawContext.canvas.nativeCanvas.restore()
            drawLine(Color(0xFF404040),Offset(31f,top+29),Offset(22f,top+38),2f,cap=StrokeCap.Round);drawLine(Color(0xFF404040),Offset(22f,top+38),Offset(31f,top+47),2f,cap=StrokeCap.Round);text("Retirement",24f,top+116,29f,bold=true)
            text(if(amount<.005)"Current ₹ 89 L"else "Current ₹ ${retirementAmount(89+1.17*amount)} L",24f,top+157,17f,0xFF4F4F4F.toInt())
            text("Back on track",196.5f,(cy-34+4*(1-done)).toFloat(),18f,bold=true,center=true,alpha=done)
            if(done<1){paint.textSize=18f;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);val left="Off-Track by";val right="₹ ${retirementAmount(1.17*(1-amount))} L";val leftWidth=paint.measureText(left);val total=leftWidth+6+paint.measureText(right);text(left,(393-total)/2,(cy-34-4*done).toFloat(),18f,bold=true,alpha=1-done);text(right,(393-total)/2+leftWidth+6,(cy-34-4*done).toFloat(),18f,0xFFF5222F.toInt(),true,alpha=1-done)}
            text("Target ₹ 90.17 L",196.5f,cy,16f,0xFF525252.toInt(),center=true);drawRoundRect(Color(0xFFD0D0D0),Offset(147.5f,cy+42),Size(98f,44f),androidx.compose.ui.geometry.CornerRadius(22f,22f));text("Resolve",196.5f,cy+64,17f,0xFF268BF4.toInt(),true,true);text("Drag the marker to close the gap",196.5f,h-42,11f,0x73000000,center=true)
        }
    }
    Box(Modifier.offset(x=(147.5f*overlayScale).dp,y=((overlayHeight-178f)*overlayScale).dp)
        .size((98f*overlayScale).dp,(44f*overlayScale).dp)
        .semantics { contentDescription="Resolve retirement gap" }
        .clickable(interactionSource=remember { MutableInteractionSource() },indication=null,role=Role.Button) { resolve() })
    }
}
