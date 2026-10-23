package com.ngocdevv.nativecook.blocks.charts.linetocandlesswitch

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.*

private object S {
    val page=Color.Black;val ink=Color(0xffe7e9ea);val mute=Color(0xff71767b);val surface=Color(0xff16181c);val pillOn=Color(0xff2f3336);val rule=Color(0xff2f3336);val menu=rgba(32,35,39,0.97f);val hover=Color.White.copy(alpha=0.12f);val green=Color(0xff00b96b);val red=Color(0xfff6465d)
    const val margin=16f;const val logo=40f;const val chartPad=14f;const val chartMin=200f;const val dateH=28f;const val pillW=46f;const val pillH=30f;const val viewW=58f;const val menuW=184f;const val menuRow=44f;const val menuGap=8f
    const val menuDamping=20.0;const val menuStiffness=380.0;const val menuMass=0.8;const val menuFrom=0.8;const val menuOut=0.14;const val slide=0.22;const val heldIn=0.15;const val heldOut=0.22;const val springEnergy=6e-9;const val headerH=133f
    fun out(t: Double)=1-(1-Motion.clamp(t)).pow(3)
    fun quad(t: Double): Double{val u=Motion.clamp(t);return if(u<0.5)2*u*u else 1-(-2*u+2).pow(2)/2}
}
private data class Spring(val damping: Double, val stiffness: Double, val mass: Double, val velocity: Double)
private data class Scalar(val from: Double, val to: Double = from, val at: Double = -100.0, val duration: Double = 0.0, val curve: Int = 0, val spring: Spring? = null) {
    fun sample(now: Double): Pair<Double, Double> {
        val t = max(0.0, now - at); val s = spring
        if (s != null) {
            val z = s.damping / (2 * s.mass); val w = sqrt(s.stiffness / s.mass - z * z); val a = from - to; val b = (s.velocity + z * a) / w
            val delta = exp(-z * t) * (a * cos(w * t) + b * sin(w * t)); val v = exp(-z * t) * (s.velocity * cos(w * t) + (-z * b - w * a) * sin(w * t)); val initial = s.mass * s.velocity * s.velocity + s.stiffness * a * a
            return if (initial == 0.0 || (s.mass * v * v + s.stiffness * delta * delta) / initial < S.springEnergy) Pair(to, 0.0) else (to + delta) to v
        }
        val u = if (duration == 0.0) 1.0 else Motion.clamp(t / duration); val eased = if (curve == 1) Motion.ease(u) else if (curve == 2) S.quad(u) else S.out(u)
        return (from + (to - from) * eased) to 0.0
    }
    fun end(): Double {
        if (spring == null) return at + duration
        var lo = 0.0; var hi = 1.0
        while (sample(at + hi).second != 0.0) hi *= 2
        repeat(48) { val mid = (lo + hi) / 2; if (sample(at + mid).second == 0.0) hi = mid else lo = mid }
        return at + hi
    }
    fun target(value: Double, now: Double, duration: Double, curve: Int = 1) = Scalar(sample(now).first, value, now, duration, curve)
    fun targetSpring(value: Double, now: Double, damping: Double, stiffness: Double, mass: Double): Scalar { val current = sample(now); return Scalar(current.first, value, now, spring = Spring(damping, stiffness, mass, current.second)) }
}
private data class Transition(val from: Frame,val to: Frame,val at: Double,val length: Double){fun time(now: Double)=if(length==0.0)1.0 else max(0.0,now-at);fun frame(now: Double)=frameAt(from,to,time(now),max(length,0.001));val end get()=at+length}

@Composable
fun LineToCandlesSwitch(
    name: String="Northwind Corp",ticker: String="NWND",currency: String="$",quote: String="USD",afterHours: String="After hours $198.52 (−0.18%)",ranges: List<Range> = Ranges,autoPlay: Boolean=false,noteAuthor: String="Market note",note: String="Northwind closed the week up almost ten percent after its quarterly results beat estimates.",
    onRangeChange: ((Range,Int)->Unit)?=null,onViewChange: ((String)->Unit)?=null,onBack: (()->Unit)?=null,modifier: Modifier=Modifier,
){
    val reduced=rememberReducedMotion();val still by rememberUpdatedState(reduced);val items by rememberUpdatedState(ranges);val tables=remember(ranges){ranges.mapIndexed{i,r->Motion.frames(r,i+1)}};val all by rememberUpdatedState(tables)
    val pickedRange by rememberUpdatedState(onRangeChange);val pickedView by rememberUpdatedState(onViewChange);val back by rememberUpdatedState(onBack)
    val birth=remember{System.nanoTime()};fun clock()=(System.nanoTime()-birth)/1e9
    val time=remember{mutableDoubleStateOf(0.0)};var rangeIndex by remember{mutableIntStateOf(1)};var viewIndex by remember{mutableIntStateOf(0)};var transition by remember{mutableStateOf(Transition(tables[1][0],tables[1][0],-100.0,0.0))}
    var menuState by remember{mutableIntStateOf(-1)};var menu by remember{mutableStateOf(Scalar(0.0))};var slot by remember{mutableStateOf(Scalar(1.0))};var check by remember{mutableStateOf(Scalar(0.0))};var cursor by remember{mutableDoubleStateOf(0.5)};var held by remember{mutableStateOf(Scalar(0.0))};var down by remember{mutableStateOf(false)}
    var automatic by remember{mutableStateOf(false)};var origin by remember{mutableDoubleStateOf(0.0)};var autoScrubbing by remember{mutableStateOf(false)};var busyUntil by remember{mutableDoubleStateOf(if(!reduced && tables[1][0].line.last()<100)RollingNumber.reveal else -100.0)};var touches by remember{mutableIntStateOf(0)};var lastTouch by remember{mutableDoubleStateOf(-100.0)};var controlRootY by remember{mutableFloatStateOf(0f)};var rootY by remember{mutableFloatStateOf(0f)}
    val reels=remember{RollingNumber()};val view=LocalView.current;val density=LocalDensity.current
    fun scrubState(now: Double): List<Double>{if(automatic){val f=Motion.scrubAt(max(0.0,now-origin)%LOOP);return listOf(if(still)Motion.restX else f[0],if(still)f[2]else f[1],f[2])};return listOf(cursor,held.sample(now).first,if(down)1.0 else 0.0)}
    fun touch(now: Double){lastTouch=now;touches++}
    fun active(now: Double){time.doubleValue=now;busyUntil=maxOf(transition.end,slot.end(),check.end(),menu.end(),held.end(),now+if(still)0.0 else RollingNumber.reveal)}
    fun takeover(now: Double){val f=scrubState(now);cursor=f[0];held=Scalar(f[1]).target(0.0,now,if(still)0.0 else S.heldOut,2);automatic=false;autoScrubbing=false;down=false}
    fun show(r: Int,v: Int,now: Double){val current=transition.frame(now);val next=all[r][v];val d=if(still)0.0 else Motion.duration(current,next);transition=Transition(current,next,now,d);rangeIndex=r;viewIndex=v;slot=slot.target(r.toDouble(),now,if(still)0.0 else S.slide);check=check.target(v.toDouble(),now,if(still)0.0 else S.slide)}
    fun setMenu(state: Int,now: Double){menuState=state;menu=if(still)Scalar(if(state>=0)1.0 else 0.0)else if(state>=0)menu.targetSpring(1.0,now,S.menuDamping,S.menuStiffness,S.menuMass)else menu.target(0.0,now,S.menuOut,2)}
    fun pickRange(r: Int){val now=clock();takeover(now);if(menuState>=0)setMenu(-1,now);show(r,viewIndex,now);touch(now);view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);pickedRange?.invoke(items[r],r);active(now)}
    fun pickView(v: Int){val now=clock();takeover(now);setMenu(-1,now);show(rangeIndex,v,now);touch(now);view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);pickedView?.invoke(Motion.views[v].label);active(now)}
    fun scrub(x: Double){val now=clock();val begin=automatic||!down;if(automatic){held=Scalar(scrubState(now)[1]);automatic=false;autoScrubbing=false};if(begin){if(menuState>=0)setMenu(-1,now);down=true;held=held.target(1.0,now,if(still)0.0 else S.heldIn,2);touch(now)};cursor=Motion.clamp(x);active(now)}
    fun lift(){val now=clock();down=false;held=held.target(0.0,now,if(still)0.0 else S.heldOut,2);touch(now);active(now)}
    DisposableEffect(view){val window=(view.context as? Activity)?.window;val controller=window?.let{WindowCompat.getInsetsController(it,view)};val before=controller?.isAppearanceLightStatusBars;controller?.isAppearanceLightStatusBars=false;onDispose{if(before!=null)controller.isAppearanceLightStatusBars=before}}
    LaunchedEffect(busyUntil,autoScrubbing){while(autoScrubbing||clock()<busyUntil)time.doubleValue=max(0.0,(withFrameNanos{it}-birth)/1e9)}
    LaunchedEffect(autoPlay,touches,reduced){
        if(!autoPlay)return@LaunchedEffect
        if(touches>0){until(birth+((lastTouch+Motion.userHold)*1e9).toLong());if(down)return@LaunchedEffect}
        var start=System.nanoTime();origin=(start-birth)/1e9;automatic=true;time.doubleValue=clock()
        val events=(Motion.steps.map{it.at to it}+listOf(Motion.pressAt to null,Motion.liftAt to null,Motion.liftAt+Motion.heldFor to null)).sortedBy{it.first}
        while(true){for((at,step)in events){until(start+(at*1e9).toLong());val now=clock();if(step!=null){if(rangeIndex!=step.range||viewIndex!=step.view)show(step.range,step.view,now);if(menuState!=step.menu)setMenu(step.menu,now);active(now)};if(at==Motion.pressAt)autoScrubbing=!still;if(at==Motion.liftAt+Motion.heldFor)autoScrubbing=false;time.doubleValue=now};start+=(LOOP*1e9).toLong();until(start);origin=(start-birth)/1e9;time.doubleValue=clock()}
    }
    val insets=WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(modifier.fillMaxSize().background(S.page).padding(insets).padding(bottom=max(0f,16-insets.calculateBottomPadding().value).dp).onGloballyPositioned{rootY=it.positionInRoot().y}){
        val width=maxWidth.value
        Column(Modifier.fillMaxSize()){
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal=S.margin.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).offset(x=(-10).dp).clickable(remember{MutableInteractionSource()},indication=null,role=Role.Button){back?.invoke()}.semantics{contentDescription="Back"},contentAlignment=Alignment.Center){Icon(Icons.AutoMirrored.Filled.ArrowBack,null,Modifier.size(24.dp),tint=S.ink)}}
            Canvas(Modifier.padding(top=4.dp,start=S.margin.dp,end=S.margin.dp).fillMaxWidth().height(S.headerH.dp).semantics{contentDescription="$name price"}){surface(0.dp){_,_,_,_->val f=scrubState(time.doubleValue);header(reels,transition.to.line,f[0],f[2]!=0.0,time.doubleValue,reduced,name,ticker,currency,quote,afterHours)}}
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.weight(1f).heightIn(min=(S.chartMin+S.dateH).dp).fillMaxWidth().semantics{contentDescription="$name chart, ${ranges[rangeIndex].label}, ${Motion.views[viewIndex].label}"}.pointerInput(Unit){awaitEachGesture{val first=awaitFirstDown(requireUnconsumed=false);scrub((first.position.x/size.width).toDouble());do{val change=awaitPointerEvent().changes.firstOrNull{it.id==first.id}?:break;if(!change.pressed)break;if(change.position!=change.previousPosition)scrub((change.position.x/size.width).toDouble());change.consume()}while(true);lift()}}){surface(0.dp){_,_,w,h->val f=scrubState(time.doubleValue);val p=transition;val plot=h-S.dateH;drawChart(p.from,p.to,p.time(time.doubleValue),max(p.length,0.001),w,plot,S.chartPad,f[0],f[1]);val r=ranges[rangeIndex];val date=Motion.dateLabel(r.start+f[0]*(r.end-r.start),r.hours);text(date,(w-measure(date,14f))/2,plot+5,14f,18f,S.mute.copy(alpha=f[1].toFloat()))}}
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(S.pillH.dp).padding(horizontal=S.margin.dp).onGloballyPositioned{controlRootY=it.positionInRoot().y},horizontalArrangement=Arrangement.SpaceBetween){
                Box(Modifier.width((S.pillW*ranges.size).dp).height(S.pillH.dp)){
                    Box(Modifier.size(S.pillW.dp,S.pillH.dp).graphicsLayer{translationX=(slot.sample(time.doubleValue).first*S.pillW*density.density).toFloat()}.background(S.pillOn,RoundedCornerShape(15.dp)))
                    Row{ranges.forEachIndexed{i,r->Box(Modifier.size(S.pillW.dp,S.pillH.dp).clickable(remember{MutableInteractionSource()},indication=null,role=Role.Tab){pickRange(i)}.semantics{selected=i==rangeIndex},contentAlignment=Alignment.Center){Label(r.label,14,18,if(i==rangeIndex)S.ink else S.mute,weight=FontWeight.SemiBold)}}}
                }
                Box(Modifier.size(S.viewW.dp,S.pillH.dp).background(S.surface,RoundedCornerShape(15.dp)).clickable(remember{MutableInteractionSource()},indication=null,role=Role.Button){val now=clock();takeover(now);setMenu(if(menuState>=0)-1 else 4,now);touch(now);active(now)}.semantics{contentDescription="Chart type, ${Motion.views[viewIndex].label}";stateDescription=if(menuState>=0)"Expanded" else "Collapsed"},contentAlignment=Alignment.Center){Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){Glyph(Motion.views[viewIndex].group,S.ink);Canvas(Modifier.size(13.dp)){surface(0.dp){_,_,_,_->val p=Path().apply{moveTo(3f,5f);lineTo(6.5f,2f);lineTo(10f,5f);moveTo(3f,8f);lineTo(6.5f,11f);lineTo(10f,8f)};drawPath(p,S.mute,style=Stroke(1.2f,cap=StrokeCap.Round,join=StrokeJoin.Round))}}}}
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().drawBehind{drawLine(S.rule,Offset.Zero,Offset(size.width,0f),1f)}.padding(top=14.dp,start=S.margin.dp,end=S.margin.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Box(Modifier.size(S.logo.dp).background(S.surface,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.AutoMirrored.Outlined.Article,null,Modifier.size(20.dp),tint=S.ink)}
                Column(Modifier.weight(1f)){Label(noteAuthor,15,20,S.ink,weight=FontWeight.Bold);Spacer(Modifier.height(2.dp));Label(note,15,20,S.ink,maxLines=2)}
            }
        }
        Box(Modifier.offset((width-S.margin-S.menuW).dp,((controlRootY-rootY)/density.density-S.menuGap-S.menuRow*4).dp).size(S.menuW.dp,(S.menuRow*4).dp).graphicsLayer{val value=menu.sample(time.doubleValue).first;alpha=Motion.clamp(value).toFloat();scaleX=(S.menuFrom+(1-S.menuFrom)*value).toFloat();scaleY=scaleX;transformOrigin=TransformOrigin(1f,1f)}){
            Canvas(Modifier.fillMaxSize().offset(y=8.dp).bleed(24.dp).then(if(Build.VERSION.SDK_INT>=31)Modifier.blur(sigmaToRadius(12f).dp,BlurredEdgeTreatment.Unbounded)else Modifier.graphicsLayer{alpha=0.35f})){surface(24.dp){x,y,w,h->drawRoundRect(Color.Black.copy(alpha=0.6f),Offset(x,y),Size(w,h),CornerRadius(14f))}}
            Column(Modifier.fillMaxSize().background(S.menu,RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))){Motion.views.forEachIndexed{i,v->
                val interaction=remember{MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
                Row(Modifier.fillMaxWidth().height(S.menuRow.dp).background(if(menuState==i||pressed)S.hover else Color.Transparent).drawBehind{if(i>0)drawLine(S.rule,Offset.Zero,Offset(size.width,0f),1f)}.then(if(menuState>=0)Modifier.clickable(interaction,indication=null,role=Role.Button){pickView(i)}else Modifier).padding(start=10.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.width(26.dp)){Icon(Icons.Default.Check,null,Modifier.size(17.dp).graphicsLayer{alpha=Motion.clamp(1-abs(check.sample(time.doubleValue).first-i)*1.5).toFloat()},tint=S.ink)}
                    Label(v.label,16,21,S.ink,Modifier.weight(1f));Glyph(v.group,S.ink)
                }
            }}
        }
    }
}
@Composable
private fun Glyph(group: Int,color: Color){Canvas(Modifier.size(16.dp)){surface(0.dp){_,_,_,_->if(group==0){val p=Path().apply{moveTo(1f,12f);lineTo(5f,6f);lineTo(9f,9f);lineTo(15f,2f)};drawPath(p,color,style=Stroke(1.5f,cap=StrokeCap.Round,join=StrokeJoin.Round))}else for(i in 0..1){val x=1.5f+i*8;val y=i*3f;drawRect(color,Offset(x+2,y),Size(1f,13f));drawRoundRect(color,Offset(x,y+2),Size(5f,8f),CornerRadius(1f))}}}}
@Composable
private fun Label(value: String,size: Int,height: Int,color: Color,modifier: Modifier=Modifier,weight: FontWeight=FontWeight.Normal,maxLines: Int=Int.MAX_VALUE){BasicText(value,modifier,style=TextStyle(color=color,fontSize=size.sp,lineHeight=height.sp,fontWeight=weight,platformStyle=PlatformTextStyle(includeFontPadding=false)),maxLines=maxLines,overflow=TextOverflow.Ellipsis)}
private suspend fun until(target: Long){while(true){val left=target-System.nanoTime();if(left<=0)return;delay((left+999_999L)/1_000_000L)}}
private fun paint(size: Float,weight: Int,tabular: Boolean=false)=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=size;if(tabular)fontFeatureSettings="tnum";typeface=if(Build.VERSION.SDK_INT>=28)Typeface.create(Typeface.DEFAULT,weight,false)else if(weight>=600)Typeface.DEFAULT_BOLD else Typeface.DEFAULT}
private fun measure(value: String,size: Float,weight: Int=400,tracking: Float=0f)=paint(size,weight).measureText(value)+value.length*tracking
private fun DrawScope.text(value: String,x: Float,top: Float,size: Float,height: Float,color: Color,weight: Int=400,tracking: Float=0f,tabular: Boolean=false){val p=paint(size,weight,tabular).apply{this.color=color.toArgb()};val baseline=top+(height-p.fontMetrics.ascent-p.fontMetrics.descent)/2;drawIntoCanvas{c->if(tracking==0f)c.nativeCanvas.drawText(value,x,baseline,p)else{var at=x;value.forEach{val glyph=it.toString();c.nativeCanvas.drawText(glyph,at,baseline,p);at+=p.measureText(glyph)+tracking}}}}
private fun DrawScope.header(reels: RollingNumber,line: List<Double>,x: Double,down: Boolean,now: Double,still: Boolean,name: String,ticker: String,currency: String,quote: String,afterHours: String){
    drawRoundRect(LineGreen,Offset.Zero,Size(S.logo,S.logo),CornerRadius(12f));val p=paint(20f,800).apply{color=Color.White.toArgb()};drawIntoCanvas{it.nativeCanvas.drawText(name.take(1),20-measure(name.take(1),20f,800)/2,20-(p.fontMetrics.ascent+p.fontMetrics.descent)/2,p)};text(name,52f,0f,17f,22f,S.ink,600);text("$ticker · $quote",52f,22f,14f,18f,S.mute)
    val value=if(down)Motion.lineAt(line,x)else line.last();val change=value-line[0];text(currency,0f,50f,34f,41f,S.ink,700,-0.5f);reels.update(value,2,3,34f,41f,700,S.ink,-0.5f,still,now);reels.draw(this,measure(currency,34f,700,-0.5f),50f,2,now)
    val label=(if(change<0)"▼ " else "▲ ")+currency+Motion.fixed(abs(change))+" ("+(if(change<0)"−" else "+")+Motion.fixed(abs(change/line[0]*100))+"%)";text(label,0f,93f,15f,20f,if(change<0)S.red else S.green,500,tabular=true);text(afterHours,0f,115f,13f,18f,S.mute)
}
