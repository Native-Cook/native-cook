package com.ngocdevv.nativecook.blocks.animations.pixelwingflight

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.provider.Settings
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

private const val LOOP = 8.0
private fun pixelFlightSmooth(v:Double):Double {val x=v.coerceIn(0.0,1.0);return x*x*(3-2*x)}
private fun pixelFlightFlap(t:Double):Double {
    if(t<.8||t>=7.2)return 0.0
    val u=t-.8;val x=if(u<.4)u/.4 else (6.4-u)/.4
    val integral=x*x*x-.5*x*x*x*x
    return (if(u<.4).2*integral else if(u>6)3-.2*integral else .5*(u-.2))%1
}

@Composable
fun PixelWingFlight(autoPlay:Boolean=false, previewTime:Double?=null) {
    val context=LocalContext.current;val haptics=LocalHapticFeedback.current
    val density=LocalDensity.current;val topPx=WindowInsets.statusBars.getTop(density);val bottomPx=WindowInsets.navigationBars.getBottom(density)
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val cloud=remember{context.assets.open("pixelwingflight/pixelwingflight_cloud.png").use{BitmapFactory.decodeStream(it)}}
    val grain=remember{context.assets.open("pixelwingflight/pixelwingflight_grain.png").use{BitmapFactory.decodeStream(it)}}
    val poses=remember{pixelWingFlightPoses.map{code->android.graphics.Path().apply{code.forEachIndexed{index,c->val bits=c.digitToInt(16);for(bit in 0..3)if(bits and (1 shl(3-bit))!=0){val x=(((index%12)*4+bit)*10+140)*393f/720;val y=((index/12)*10+250)*393f/720;addRect(x-1.25f,y-1.25f,x+1.25f,y+1.25f,android.graphics.Path.Direction.CW)}}}}}
    val bounds=remember{poses.map{android.graphics.RectF().also{r->it.computeBounds(r,true)}}}
    val paint=remember{android.graphics.Paint(3)}
    var clock by remember{mutableDoubleStateOf(0.0)};var touched by remember{mutableDoubleStateOf(-100.0)};var manual by remember{mutableDoubleStateOf(0.0)};var dragStart by remember{mutableDoubleStateOf(0.0)};var paused by remember{mutableStateOf(false)};var held by remember{mutableDoubleStateOf(0.0)};var isTouching by remember{mutableStateOf(false)};var pauseClock by remember{mutableDoubleStateOf(0.0)};var pauseOffset by remember{mutableDoubleStateOf(0.0)};var coast by remember{mutableDoubleStateOf(0.0)};var velocity by remember{mutableDoubleStateOf(0.0)};var sampleAt by remember{mutableDoubleStateOf(0.0)};var sampleManual by remember{mutableDoubleStateOf(0.0)};var heldReturn by remember{mutableDoubleStateOf(0.0)};var manualWingOffset by remember{mutableDoubleStateOf(0.0)};var heldWingOffset by remember{mutableDoubleStateOf(0.0)}
    var awake by remember{mutableStateOf(false)}
    LaunchedEffect(autoPlay,reduced,previewTime,awake,paused){if(reduced||previewTime!=null||paused||(!autoPlay&&!awake))return@LaunchedEffect;var first=0L;val baseClock=clock+pauseOffset;while(autoPlay||awake)withFrameNanos{n->if(first==0L)first=n;clock=baseClock+(n-first)/1e9-pauseOffset;if(!autoPlay&&!isTouching&&touched>=0&&clock-touched>=2.6)awake=false}}
    fun time():Double{previewTime?.let{return it%LOOP};if(reduced)return 0.0;if(isTouching)return manual;if(paused)return held;val dt=clock-touched;if(dt<2.6)return (manual+coast*(1-exp(-dt/.1))+LOOP)%LOOP;return if(autoPlay)(if(touched<0)clock else dt-2.6)%LOOP else 0.0}
    fun recovery():Double{if(previewTime!=null||reduced||isTouching)return 0.0;if(paused)return heldReturn;val dt=clock-touched;return if(dt<2.6)pixelFlightSmooth((dt-2)/.6) else 0.0}
    fun wingOffset():Double{if(previewTime!=null||reduced)return 0.0;if(paused)return heldWingOffset;return if(isTouching||clock-touched<2.6)manualWingOffset else 0.0}
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val overlayScale=maxWidth.value/393f
    val overlayHeight=maxHeight.value/overlayScale
    val overlayBottom=max(bottomPx/density.density/overlayScale,20f)
    Canvas(Modifier.fillMaxSize().background(Color(0xFF3636B9)).semantics {
        contentDescription="Flight canvas. Drag horizontally to steer the wings."
        customActions=listOf(CustomAccessibilityAction(if(paused) "Resume flight" else "Pause flight") { if(paused){val duration=clock-pauseClock;pauseOffset+=duration;clock=pauseClock;paused=false}else{held=time();heldReturn=recovery();heldWingOffset=wingOffset();pauseClock=clock;paused=true};true })
    }.pointerInput(Unit){detectTapGestures{p->val s=size.width/393f;val h=size.height/s;val b=max(bottomPx/s,20f);if(abs(p.x/s-345)<26&&abs(p.y/s-(h-b-31))<26){if(paused){val duration=clock-pauseClock;pauseOffset+=duration;clock=pauseClock;paused=false}else{held=time();heldReturn=recovery();heldWingOffset=wingOffset();pauseClock=clock;paused=true};haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)}}}.pointerInput(Unit){detectDragGestures(onDragStart={awake=true;manualWingOffset=3+2*pixelFlightFlap((time()+wingOffset()+LOOP)%LOOP)-time();manual=time();dragStart=manual;isTouching=true;touched=clock;paused=false;coast=0.0;velocity=0.0;sampleAt=clock;sampleManual=manual},onDragEnd={coast=(velocity*.12).coerceIn(-.36,.36);isTouching=false;touched=clock},onDragCancel={isTouching=false;touched=clock},onDrag={change,delta->change.consume();val raw=dragStart+(change.position.x-change.previousPosition.x)/size.width*2;val dt=clock-sampleAt;if(dt>.002)velocity=.35*velocity+.65*((raw-sampleManual)/dt).coerceIn(-3.0,3.0);manual=(raw+LOOP)%LOOP;dragStart=raw;sampleManual=raw;sampleAt=clock;touched=clock})}) {
        val s=size.width/393;val h=size.height/s;val dy=(h-1280*393f/720)/2;val top=topPx/s;val bottom=max(bottomPx/s,20f);val t=time();val returning=recovery();val position=pixelFlightFlap((t+wingOffset()+LOOP)%LOOP)*poses.size;val pose=position.toInt()%poses.size;val next=(pose+1)%poses.size;val mix=pixelFlightSmooth(position-pose)
        scale(s,s,Offset.Zero){
            drawRect(Brush.linearGradient(listOf(Color(0xFF3836B8),Color(0xFF333BC7),Color(0xFF3033AD)),Offset.Zero,Offset(393f,h)))
            val canvas=drawContext.canvas.nativeCanvas
            paint.color=android.graphics.Color.WHITE;paint.alpha=178;canvas.drawBitmap(grain,null,android.graphics.RectF(0f,0f,393f,h),paint)
            paint.color=0xFFF7F7F7.toInt();canvas.saveLayer(0f,0f,393f,h,null);canvas.translate(0f,dy);paint.xfermode=android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
            paint.alpha=(255*(1-mix)*(1-returning)).toInt();canvas.drawPath(poses[pose],paint)
            paint.alpha=(255*mix*(1-returning)).toInt();canvas.drawPath(poses[next],paint)
            paint.alpha=(255*returning).toInt();canvas.drawPath(poses[0],paint);canvas.restore();paint.xfermode=null;paint.alpha=255
            val y1=(((bounds[pose].top*(1-mix)+bounds[next].top*mix)*(1-returning)+bounds[0].top*returning)+dy-9).toFloat()
            val y2=(((bounds[pose].bottom*(1-mix)+bounds[next].bottom*mix)*(1-returning)+bounds[0].bottom*returning)+dy+9).toFloat()
            drawLine(Color.White.copy(alpha=.48f),Offset(86f,0f),Offset(86f,h),.65f);drawLine(Color.White.copy(alpha=.48f),Offset(306f,0f),Offset(306f,h),.65f);drawLine(Color.White.copy(alpha=.48f),Offset(0f,y1),Offset(393f,y1),.65f);drawLine(Color.White.copy(alpha=.48f),Offset(0f,y2),Offset(393f,y2),.65f)
            val colors=listOf(Color.White,Color(0xFF53D56D),Color(0xFFF6A144),Color(0xFF66D6F0));for(i in 0..13)drawCircle(colors[i%4].copy(alpha=.7f),if(i%3==0)1f else .5f,Offset(((i*97+29)%393).toFloat(),((i*173+137)%720)+dy))
            for(i in 0..5){val w=170f+(i%3)*38;val start=i*.173;var u=(t/LOOP*(if(i%2==0)2 else 1)+start)%1;val delta=start-u-round(start-u);u=(u+delta*returning+1)%1;val x=(443-(493+w)*u).toFloat();val y=(dy+120+i*92+22*((1-returning)*sin(2*PI*t/LOOP+i)+returning*sin(i.toDouble()))).toFloat();paint.color=android.graphics.Color.WHITE;paint.alpha=if(i%2==0)112 else 61;canvas.drawBitmap(cloud,null,android.graphics.RectF(x,y,x+w,y+w*.375f),paint)}
            fun text(v:String,x:Float,y:Float,size:Float,alpha:Int=255,bold:Boolean=false,center:Boolean=false,italic:Boolean=false){paint.color=android.graphics.Color.WHITE;paint.alpha=alpha;paint.textSize=size;paint.typeface=Typeface.create("sans-serif",if(italic)Typeface.ITALIC else if(bold)Typeface.BOLD else Typeface.NORMAL);paint.textAlign=if(center)android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT;canvas.drawText(v,x,y-(paint.ascent()+paint.descent())/2,paint)}
            canvas.save();canvas.translate(317f,(y1+y2)/2+25);canvas.rotate(-90f);text("Grow wings and fly",0f,0f,11f,italic=true,center=true);canvas.restore()
            text("AER / FIELD NOTES",24f,top+28,10f,204,true);paint.textAlign=android.graphics.Paint.Align.RIGHT;text("01 / FLIGHT",305f,top+28,10f,179,true)
            text("A little more sky.",24f,h-bottom-112,25f,bold=true);text("A quiet moment, above it all.",24f,h-bottom-82,12f,173);text("DRAG TO FLY",24f,h-bottom-29,9f,166,true)
            drawCircle(Color.White.copy(alpha=.45f),24f,Offset(345f,h-bottom-31),style=androidx.compose.ui.graphics.drawscope.Stroke(1f))
            val iconY=h-bottom-31
            if(paused){val triangle=Path().apply{moveTo(341f,iconY-6);lineTo(350f,iconY);lineTo(341f,iconY+6);close()};drawPath(triangle,Color.White)}else{drawRect(Color.White,Offset(340.5f,iconY-6),androidx.compose.ui.geometry.Size(3f,12f));drawRect(Color.White,Offset(346.5f,iconY-6),androidx.compose.ui.geometry.Size(3f,12f))}
        }
    }
    Box(Modifier.offset(x=(321f*overlayScale).dp,y=((overlayHeight-overlayBottom-55f)*overlayScale).dp)
        .size((48f*overlayScale).dp)
        .semantics { contentDescription=if(paused) "Resume flight" else "Pause flight" }
        .clickable(interactionSource=remember { MutableInteractionSource() },indication=null,role=Role.Button) {
            if(paused){val duration=clock-pauseClock;pauseOffset+=duration;clock=pauseClock;paused=false}else{held=time();heldReturn=recovery();heldWingOffset=wingOffset();pauseClock=clock;paused=true}
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        })
    }
}
