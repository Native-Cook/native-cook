package com.ngocdevv.nativecook.blocks.charts.metricdropdownlinescrub

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.*

private object Motion {
    const val loop=10.0;const val firstDown=0.8;val firstMove=listOf(1.0,3.2);val firstPath=listOf(0.06,0.94);const val firstUp=3.5;const val rangeOpen=3.9;const val rangeHover=4.45;const val rangePick=4.65;const val metricOpen=5.4;const val metricHover=5.95;const val metricPick=6.15;const val secondDown=7.0;val secondMove=listOf(7.15,8.4);val secondPath=listOf(0.92,0.12);const val secondUp=8.65;const val reset=9.05;const val autoRange=1;const val autoMetric=1;const val userHold=3.0
    fun clamp(x: Double)=x.coerceIn(0.0,1.0)
    fun ease(t: Double): Double{val u=clamp(t);return if(u<0.5)4*u.pow(3)else 1-(-2*u+2).pow(3)/2}
    fun glide(t: Double): Double{val u=clamp(t);return u*u*(3-2*u)}
    fun fraction(t: Double,rest: Double): Double{if(t>=secondDown)return secondPath[0]+(secondPath[1]-secondPath[0])*glide((t-secondMove[0])/(secondMove[1]-secondMove[0]));if(t>=firstDown)return firstPath[0]+(firstPath[1]-firstPath[0])*glide((t-firstMove[0])/(firstMove[1]-firstMove[0]));return rest}
    fun on(t: Double)=(t>=firstDown&&t<firstUp)||(t>=secondDown&&t<secondUp)
    fun fade(t: Double,still: Boolean): Double{if(still)return if(on(t))1.0 else 0.0;if(t>=secondDown)return if(t<secondUp)S.quad((t-secondDown)/S.scrubFade)else 1-S.quad((t-secondUp)/S.scrubFade);return if(t<firstUp)S.quad((t-firstDown)/S.scrubFade)else 1-S.quad((t-firstUp)/S.scrubFade)}
    fun crossings(count: Int,times: List<Double>,path: List<Double>): List<Double>{if(count<=1)return emptyList();return (0 until count-1).mapNotNull{i->val target=(i+0.5)/(count-1);val p=(target-path[0])/(path[1]-path[0]);if(p<=0||p>=1)null else{var lo=0.0;var hi=1.0;repeat(48){val mid=(lo+hi)/2;if(glide(mid)<p)lo=mid else hi=mid};times[0]+hi*(times[1]-times[0])+1e-7}}}
}
private object S {
    val background=Color(0xff0e0e0f);val ink=Color.White;val mute=Color(0xff8a8a8e);val button=Color(0xff1e1e21);val card=Color(0xff17171a);val tooltip=Color(0xff2a2a2e);val edge=Color.White.copy(alpha=0.07f);val caption=Color(0xffb5b5ba);val menu=Color(0xff242428);val pressed=Color(0xff34343a);val separator=Color(0xff36363c);val tabIdle=Color(0xff6b6b70)
    const val margin=20f;const val cardInset=16f;const val headerButton=40f;const val pillHeight=40f;const val pillGap=10f;const val pillsGap=22f;const val cardGap=24f;const val cardRadius=24f;const val plotTop=62f;const val plotBottom=40f;const val plotLeft=16f;const val plotRight=60f;val grid=listOf(0.0,0.25,0.5,0.75,1.0);const val tooltipW=184f;const val tooltipH=88f;const val tooltipRise=46f;const val menuW=220f;const val menuItem=50f;const val menuGap=8f;const val tabBar=56f
    const val morph=0.5;const val resetMorph=0.8;const val scrubFade=0.16;const val menuIn=0.2;const val labelFade=0.22;const val jumpDip=0.35;const val jumpFade=0.22;const val followDamping=24.0;const val followStiffness=340.0;const val followMass=0.7;const val springEnergy=6e-9
    val css=CubicBezierEasing(0.25f,0.1f,0.25f,1f);val cssOut=CubicBezierEasing(0f,0f,0.58f,1f)
    fun quad(x: Double): Double{val u=Motion.clamp(x);return if(u<0.5)2*u*u else 1-(-2*u+2).pow(2)/2}
    fun out(x: Double)=1-(1-Motion.clamp(x)).pow(3)
    fun pillColor(x: Double)=rgba(floor(38+176*x+0.5).toInt(),floor(38+176*x+0.5).toInt(),floor(42+176*x+0.5).toInt(),1f)
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
        val u = if (duration == 0.0) 1.0 else Motion.clamp(t / duration); val eased = if (curve == 1) Motion.ease(u) else if (curve == 2) S.quad(u) else if(curve==4)S.css.transform(u.toFloat()).toDouble()else if(curve==5)S.cssOut.transform(u.toFloat()).toDouble()else S.out(u)
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
private class Drawing{val roller=Roller();var follow=Scalar(0.0);var lastX: Double?=null;var lastOn=false;fun update(x: Double,on: Boolean,still: Boolean,now: Double){if(still||!on||!lastOn||lastX==null)follow=Scalar(x)else if(x!=lastX)follow=follow.targetSpring(x,now,S.followDamping,S.followStiffness,S.followMass);lastOn=on;lastX=x}}
private data class Ghost(val index: Int,val at: Double)
private data class Geometry(val width: Float,val height: Float,val headerH: Float){val pillsY get()=16+headerH+S.pillsGap;val cardY get()=pillsY+S.pillHeight+S.cardGap;val cardW get()=width-2*S.cardInset;val cardH get()=max(0f,height-cardY-8-S.tabBar);val plot get()=Plot(S.plotLeft,S.plotTop,max(0f,cardW-S.plotLeft-S.plotRight),max(0f,cardH-S.plotTop-S.plotBottom))}
private data class Picture(val points: List<Double>,val pick: Int,val x: Float,val y: Float,val on: Boolean,val alpha: Float)

@Composable
fun MetricDropdownLineScrub(title: String="Deadlift",subtitle: String="You're viewing demo data. This section will fill up with your data as you log workouts.",sessions: List<String> = Sessions,metrics: List<Metric> = Metrics,ranges: List<Range> = Ranges,initialMetric: Int=0,initialRange: Int=3,todayLabel: String="Today",autoPlay: Boolean=false,onMetricChange: ((Metric,Int)->Unit)?=null,onRangeChange: ((Range,Int)->Unit)?=null,onScrub: ((String,Double)->Unit)?=null,onHeaderPress: (()->Unit)?=null,modifier: Modifier=Modifier){
    val reduced=rememberReducedMotion();val still by rememberUpdatedState(reduced);val logs by rememberUpdatedState(sessions);val measures by rememberUpdatedState(metrics);val spans by rememberUpdatedState(ranges);val im by rememberUpdatedState(initialMetric);val ir by rememberUpdatedState(initialRange)
    val mc by rememberUpdatedState(onMetricChange);val rc by rememberUpdatedState(onRangeChange);val sc by rememberUpdatedState(onScrub);val hc by rememberUpdatedState(onHeaderPress)
    val birth=remember{System.nanoTime()}
    fun clock()=(System.nanoTime()-birth)/1e9
    val time=remember{mutableDoubleStateOf(0.0)}
    var mi by remember{mutableIntStateOf(initialMetric)};var ri by remember{mutableIntStateOf(initialRange)};val opening=remember{Data.layoutPoints(metrics[initialMetric],Data.rangeStart(sessions,ranges[initialRange]),sessions.size)};var from by remember{mutableStateOf(opening)};var to by remember{mutableStateOf(opening)};var morph by remember{mutableStateOf(Scalar(1.0))};var jump by remember{mutableStateOf(Scalar(1.0))};var scrub by remember{mutableStateOf(Scalar(0.0))}
    var menus by remember{mutableStateOf(listOf(Scalar(0.0),Scalar(0.0)))};var pills by remember{mutableStateOf(listOf(Scalar(0.0),Scalar(0.0)))};var arrows by remember{mutableStateOf(listOf(Scalar(0.0),Scalar(0.0)))};var menuKind by remember{mutableStateOf<Int?>(null)};var hover by remember{mutableStateOf<Int?>(null)};var pressedRow by remember{mutableStateOf<Int?>(null)}
    var raw by remember{mutableDoubleStateOf(0.0)};var holding by remember{mutableStateOf(false)};var automatic by remember{mutableStateOf(false)};var origin by remember{mutableDoubleStateOf(0.0)};var restRaw by remember{mutableDoubleStateOf(0.0)};var hardUntil by remember{mutableDoubleStateOf(if(reduced)0.0 else S.labelFade)};var animation by remember{mutableIntStateOf(0)};var touches by remember{mutableIntStateOf(0)};var lastTouch by remember{mutableDoubleStateOf(-100.0)}
    var labelAt by remember{mutableStateOf(listOf(0.0,0.0))};var axisAt by remember{mutableDoubleStateOf(0.0)};var datesAt by remember{mutableDoubleStateOf(0.0)};var axisGhosts by remember{mutableStateOf(emptyList<Ghost>())};var dateGhosts by remember{mutableStateOf(emptyList<Ghost>())};val drawing=remember{Drawing()};val view=LocalView.current;val pxDensity=LocalDensity.current.density
    fun fraction(now: Double)=if(automatic)Motion.fraction(max(0.0,now-origin)%Motion.loop,restRaw)else raw
    fun isOn(now: Double)=if(automatic)Motion.on(max(0.0,now-origin)%Motion.loop)else holding
    fun alpha(now: Double)=if(automatic)Motion.fade(max(0.0,now-origin)%Motion.loop,still)else scrub.sample(now).first
    fun normalized(now: Double): List<Double>{val p=morph.sample(now).first;return from.indices.map{from[it]+(to[it]-from[it])*p}}
    fun active(now: Double,extra: Double=Roller.roll){time.doubleValue=now;hardUntil=(listOf(now+extra,morph.end(),jump.end(),scrub.end(),axisAt+if(still)0.0 else S.labelFade,datesAt+if(still)0.0 else S.labelFade)+menus.map{it.end()}+pills.map{it.end()}+arrows.map{it.end()}).max();animation++}
    fun stop(now: Double){if(automatic){raw=fraction(now);val a=alpha(now);automatic=false;scrub=Scalar(a)}}
    fun touched(){val now=clock();stop(now);if(!holding)scrub=scrub.target(0.0,now,if(still)0.0 else S.scrubFade,2);hover=null;lastTouch=now;touches++;active(now)}
    fun setMenu(kind: Int?,now: Double){menuKind=kind;pressedRow=null;menus=menus.mapIndexed{i,s->s.target(if(kind==i)1.0 else 0.0,now,S.menuIn,5)};pills=pills.mapIndexed{i,s->s.target(if(kind==i)1.0 else 0.0,now,S.menuIn,4)};arrows=arrows.mapIndexed{i,s->s.target(if(kind==i)1.0 else 0.0,now,if(still)0.0 else S.menuIn,4)}}
    fun show(metric: Int,range: Int,duration: Double,now: Double){val points=normalized(now);if(metric!=mi){axisGhosts=axisGhosts.filter{now-it.at<S.labelFade}+Ghost(mi,now);axisAt=now;labelAt=listOf(now,labelAt[1])};if(range!=ri){dateGhosts=dateGhosts.filter{now-it.at<S.labelFade}+Ghost(ri,now);datesAt=now;labelAt=listOf(labelAt[0],now)};val start=Data.rangeStart(logs,spans[range]);from=points;to=Data.layoutPoints(measures[metric],start,logs.size);mi=metric;ri=range;if(still){morph=Scalar(1.0);jump=Scalar(S.jumpDip).target(1.0,now,S.jumpFade,2)}else morph=Scalar(0.0).target(1.0,now,duration,1)}
    fun choose(kind: Int,index: Int,auto: Boolean,now: Double){setMenu(null,now);hover=null;if(!auto)view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);if(kind==0&&index!=mi){show(index,ri,S.morph,now);mc?.invoke(measures[index],index)};if(kind==1&&index!=ri){show(mi,index,S.morph,now);rc?.invoke(spans[index],index)};active(now)}
    fun rest(now: Double){setMenu(null,now);hover=null;scrub=scrub.target(0.0,now,if(still)0.0 else S.scrubFade,2);if(mi!=im||ri!=ir)show(im,ir,S.resetMorph,now);active(now)}
    val count by rememberUpdatedState(sessions.size-Data.rangeStart(sessions,ranges[ri]))
    fun selected(now: Double)=if(count<=1)0 else floor(Motion.clamp(fraction(now))*(count-1)+0.5).toInt()
    var previous by remember{mutableIntStateOf(0)}
    LaunchedEffect(animation){do{val now=max(0.0,(withFrameNanos{it}-birth)/1e9);time.doubleValue=now;val pick=selected(now);if(pick!=previous){previous=pick;val first=Data.rangeStart(logs,spans[ri]);val index=first+min(pick,count-1);if(!automatic&&holding)view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);sc?.invoke(logs[index],measures[mi].values[index])}}while(clock()<maxOf(hardUntil,drawing.follow.end(),drawing.roller.end))}
    LaunchedEffect(autoPlay,touches){if(!autoPlay)return@LaunchedEffect;if(touches>0){until(birth+((lastTouch+Motion.userHold)*1e9).toLong());if(holding)return@LaunchedEffect;rest(clock());until(birth+((lastTouch+Motion.userHold+S.resetMorph+0.2)*1e9).toLong())};automatic=true;restRaw=raw;var begin=System.nanoTime();origin=(begin-birth)/1e9
        val n1=logs.size-Data.rangeStart(logs,spans[ir]);val n2=logs.size-Data.rangeStart(logs,spans[Motion.autoRange]);val events=(listOf(Motion.firstDown,Motion.firstUp,Motion.firstUp+S.scrubFade,Motion.rangeOpen,Motion.rangeHover,Motion.rangePick,Motion.metricOpen,Motion.metricHover,Motion.metricPick,Motion.secondDown,Motion.secondUp,Motion.secondUp+S.scrubFade,Motion.reset)+Motion.crossings(n1,Motion.firstMove,Motion.firstPath)+Motion.crossings(n2,Motion.secondMove,Motion.secondPath)).sorted()
        while(true){for(at in events){until(begin+(at*1e9).toLong());val now=clock();if(at==Motion.rangeOpen)setMenu(1,now);if(at==Motion.rangeHover)hover=Motion.autoRange;if(at==Motion.rangePick)choose(1,Motion.autoRange,true,now);if(at==Motion.metricOpen)setMenu(0,now);if(at==Motion.metricHover)hover=Motion.autoMetric;if(at==Motion.metricPick)choose(0,Motion.autoMetric,true,now);if(at==Motion.reset)rest(now);active(now)};begin+=(Motion.loop*1e9).toLong();until(begin);origin=(begin-birth)/1e9;restRaw=Motion.secondPath[1];active(clock())}
    }
    DisposableEffect(view){val window=(view.context as? Activity)?.window;val controller=window?.let{WindowCompat.getInsetsController(it,view)};val before=controller?.isAppearanceLightStatusBars;controller?.isAppearanceLightStatusBars=false;onDispose{if(before!=null)controller.isAppearanceLightStatusBars=before}}
    BoxWithConstraints(modifier.fillMaxSize().background(S.background).padding(WindowInsets.safeDrawing.asPaddingValues())){
        val lines=wrapped(subtitle,13f,maxWidth.value-96,2);val geo=Geometry(maxWidth.value,maxHeight.value,max(58f,38+6+lines.size*18f));val plot=geo.plot
        fun picture(now: Double): Picture{val p=normalized(now);val pick=selected(now);val slot=if(count<=1)0 else floor(pick.toDouble()*(sessions.size-1)/(count-1)+0.5).toInt();val points=p.indices.step(2).flatMap{i->listOf((plot.left+p[i]*plot.width),(plot.top+(1-p[i+1])*plot.height))};return Picture(points,pick,points[slot*2].toFloat(),points[slot*2+1].toFloat(),isOn(now),alpha(now).toFloat())}
        val headerInput by rememberUpdatedState(geo)
        Canvas(Modifier.fillMaxSize()){surface(0.dp){_,_,_,_->val f=picture(time.doubleValue);drawScene(geo,f,time.doubleValue,reduced,title,lines,metrics,ranges,sessions,mi,ri,todayLabel,jump,menus,pills,arrows,menuKind,hover,pressedRow,labelAt,axisAt,datesAt,axisGhosts,dateGhosts,drawing)}}
        Box(Modifier.offset((geo.width-60).dp,34.dp).size(40.dp).clickable(remember{MutableInteractionSource()},indication=null,role=Role.Button){touched();hc?.invoke()}.semantics{contentDescription="More"})
        for(kind in 0..1){val w=(geo.width-40-S.pillGap)/2;Box(Modifier.offset((S.margin+kind*(w+S.pillGap)).dp,geo.pillsY.dp).size(w.dp,S.pillHeight.dp).clickable(remember{MutableInteractionSource()},indication=null,role=Role.Button){touched();view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);setMenu(if(menuKind==kind)null else kind,clock());active(clock())}.semantics{contentDescription=if(kind==0)metrics[mi].label else ranges[ri].label;stateDescription=if(menuKind==kind)"Expanded" else "Collapsed"})}
        Box(Modifier.offset(S.cardInset.dp,geo.cardY.dp).size(geo.cardW.dp,geo.cardH.dp).semantics{contentDescription="${metrics[mi].label}, ${ranges[ri].label}"}.pointerInput(Unit){awaitEachGesture{val first=awaitFirstDown(requireUnconsumed=false);val now=clock();stop(now);holding=true;scrub=scrub.target(1.0,now,if(still)0.0 else S.scrubFade,2);touched();val p=headerInput.plot;raw=if(p.width>0)(first.position.x/density-p.left)/p.width.toDouble()else 0.0;active(now);do{val change=awaitPointerEvent().changes.firstOrNull{it.id==first.id}?:break;if(!change.pressed)break;raw=if(p.width>0)(change.position.x/density-p.left)/p.width.toDouble()else 0.0;change.consume();active(clock())}while(true);holding=false;scrub=scrub.target(0.0,clock(),if(still)0.0 else S.scrubFade,2);touched();active(clock())}})
        val kind=menuKind
        if(kind!=null){Box(Modifier.fillMaxSize().clickable(remember{MutableInteractionSource()},indication=null){touched();view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);setMenu(null,clock());active(clock())}.semantics{contentDescription="Close menu"});val items=if(kind==0)metrics.map{it.label}else ranges.map{it.label};for((i,label)in items.withIndex()){val interaction=remember{MutableInteractionSource()};val rowPressed by interaction.collectIsPressedAsState();LaunchedEffect(rowPressed){if(rowPressed)pressedRow=i else if(pressedRow==i)pressedRow=null};Box(Modifier.offset((menuX(kind,geo)+6).dp,(geo.pillsY+S.pillHeight+S.menuGap+6+1/pxDensity+i*S.menuItem).dp).size((S.menuW-12).dp,S.menuItem.dp).clickable(interaction,indication=null,role=Role.Button){touched();choose(kind,i,false,clock())}.semantics{contentDescription=label;selected=if(kind==0)i==mi else i==ri})}}
    }
}
private suspend fun until(target: Long){while(true){val left=target-System.nanoTime();if(left<=0)return;delay((left+999_999L)/1_000_000L)}}
private fun paint(size: Float,weight: Int=400,tabular: Boolean=false)=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=size;if(tabular)fontFeatureSettings="tnum";typeface=if(Build.VERSION.SDK_INT>=28)Typeface.create(Typeface.DEFAULT,weight,false)else if(weight>=600)Typeface.DEFAULT_BOLD else Typeface.DEFAULT}
private fun measure(value: String,size: Float,weight: Int=400)=paint(size,weight).measureText(value)
private fun ellipsis(value: String,size: Float,max: Float,weight: Int=400): String{if(measure(value,size,weight)<=max)return value;var s=value;while(s.isNotEmpty()&&measure(s+"…",size,weight)>max)s=s.dropLast(1);return s+"…"}
private fun wrapped(value: String,size: Float,max: Float,lines: Int): List<String>{val out=mutableListOf<String>();var line="";for(word in value.split(' ')){val next=if(line.isEmpty())word else "$line $word";if(measure(next,size)>max&&line.isNotEmpty()&&out.size<lines-1){out.add(line);line=word}else line=next};if(line.isNotEmpty())out.add(ellipsis(line,size,max));return out}
private fun DrawScope.text(value: String,x: Float,top: Float,size: Float,height: Float,color: Color,weight: Int=400,anchor: Int=0,tabular: Boolean=false,tracking: Float=0f){val p=paint(size,weight,tabular).apply{this.color=color.toArgb()};val baseline=top+(height-p.fontMetrics.ascent-p.fontMetrics.descent)/2;val w=p.measureText(value)+value.length*tracking;var at=x-if(anchor==1)w/2 else if(anchor==2)w else 0f;drawIntoCanvas{c->if(tracking==0f)c.nativeCanvas.drawText(value,at,baseline,p)else for(ch in value){val g=ch.toString();c.nativeCanvas.drawText(g,at,baseline,p);at+=p.measureText(g)+tracking}}}
private fun DrawScope.icon(kind: Int,x: Float,y: Float,size: Float,color: Color,angle: Float=0f){rotate(angle,Offset(x,y)){val p=Path();when(kind){0->{p.moveTo(x-size/4,y-size/8);p.lineTo(x,y+size/8);p.lineTo(x+size/4,y-size/8)};1->{for(i in 0..2)p.addRect(Rect(x-size/3+i*size/4,y+size/3-(i+1)*size/5,x-size/3+i*size/4+size/7,y+size/3))};2->{p.addRect(Rect(x-size/3,y-size/3,x+size/3,y+size/3));p.moveTo(x-size/3,y-size/8);p.lineTo(x+size/3,y-size/8)};3->{p.moveTo(x-size/4,y);p.lineTo(x-size/12,y+size/4);p.lineTo(x+size/3,y-size/4)};4->{for(i in 0..3){val dx=(i%2-0.5f)*size/2;val dy=(i/2-0.5f)*size/2;p.addRect(Rect(x+dx-size/7,y+dy-size/7,x+dx+size/7,y+dy+size/7))}};5->{p.addRoundRect(RoundRect(Rect(x-size/3,y-size/4,x+size/3,y+size/4),CornerRadius(size/10)));p.moveTo(x-size/4,y+size/4);p.lineTo(x-size/4,y+size/2);p.lineTo(x,y+size/4)}};drawPath(p,color,style=if(kind==1)Fill else Stroke(1.5f,cap=StrokeCap.Round,join=StrokeJoin.Round))}}
private fun menuX(kind: Int,g: Geometry)=if(kind==0)S.margin else min(S.margin+(g.width-40-S.pillGap)/2+S.pillGap,g.width-S.margin-S.menuW)
private fun DrawScope.drawScene(g: Geometry,f: Picture,now: Double,still: Boolean,title: String,lines: List<String>,metrics: List<Metric>,ranges: List<Range>,sessions: List<String>,mi: Int,ri: Int,today: String,jump: Scalar,menus: List<Scalar>,pills: List<Scalar>,arrows: List<Scalar>,kind: Int?,hover: Int?,pressedRow: Int?,labelAt: List<Double>,axisAt: Double,datesAt: Double,axisGhosts: List<Ghost>,dateGhosts: List<Ghost>,d: Drawing){
    text(ellipsis(title,32f,g.width-96,700),S.margin,16f,32f,38f,S.ink,700,tracking=-0.6f);lines.forEachIndexed{i,line->text(line,S.margin,60+i*18f,13f,18f,S.mute)};drawCircle(S.button,20f,Offset(g.width-40,54f));icon(0,g.width-40,54f,20f,S.ink)
    val pillW=(g.width-40-S.pillGap)/2
    for(k in 0..1){val x=S.margin+k*(pillW+S.pillGap);val color=if(kind==k)Color(0xff141416)else S.ink;drawRoundRect(S.pillColor(pills[k].sample(now).first),Offset(x,g.pillsY),Size(pillW,40f),CornerRadius(20f));val label=if(k==0)metrics[mi].label else ranges[ri].label;text(ellipsis(label,15f,pillW-48,500),x+18,g.pillsY+10,15f,20f,color.copy(alpha=if(still)1f else S.quad((now-labelAt[k])/S.labelFade).toFloat()),500);icon(0,x+pillW-22,g.pillsY+20,16f,color,(arrows[k].sample(now).first*180).toFloat())}
    drawRoundRect(S.card,Offset(S.cardInset,g.cardY),Size(g.cardW,g.cardH),CornerRadius(S.cardRadius))
    translate(S.cardInset,g.cardY){clipPath(Path().apply{addRoundRect(RoundRect(Rect(0f,0f,g.cardW,g.cardH),CornerRadius(S.cardRadius)))}){drawChart(g.plot,S.grid,f.points,f.x,f.y,f.alpha,S.tooltipH-S.tooltipRise,jump.sample(now).first.toFloat())
        fun axes(index: Int,a: Double){val m=metrics[index];for(at in S.grid)text(Data.axisValue(m.min+(m.max-m.min)*at)+" "+m.unit,g.cardW-14,(g.plot.top+g.plot.height*(1-at)-7).toFloat(),11f,14f,S.mute.copy(alpha=a.toFloat()),anchor=2,tabular=true)}
        fun dates(index: Int,a: Double){val first=Data.rangeStart(sessions,ranges[index]);val n=sessions.size-first;val mid=first+floor((n-1)/2.0+0.5).toInt();val y=g.cardH-28;text(Data.shortDate(sessions[first]),g.plot.left,y,11f,14f,S.mute.copy(alpha=a.toFloat()));text(Data.shortDate(sessions[mid]),g.plot.left+g.plot.width/2,y,11f,14f,S.mute.copy(alpha=a.toFloat()),anchor=1);text(today,g.cardW-S.plotRight+8,y,11f,14f,S.mute.copy(alpha=a.toFloat()),anchor=2)}
        if(!still){for(ghost in axisGhosts)axes(ghost.index,1-S.quad((now-ghost.at)/S.labelFade));for(ghost in dateGhosts)dates(ghost.index,1-S.quad((now-ghost.at)/S.labelFade))};axes(mi,if(still)1.0 else S.quad((now-axisAt)/S.labelFade));dates(ri,if(still)1.0 else S.quad((now-datesAt)/S.labelFade))
    }}
    for(i in 0..3){val x=g.width*(i+0.5f)/4;val y=g.height-S.tabBar/2;icon(if(i==0)4 else if(i==1)2 else if(i==2)1 else 5,x,y,22f,if(i==2)S.ink else S.tabIdle);if(i==3)drawCircle(S.ink,3.5f,Offset(x+10.5f,g.height-S.tabBar+18.5f))}
    d.update(f.x.toDouble(),f.on,still,now);val left=min(g.width-S.cardInset-S.tooltipW,max(S.cardInset,S.cardInset-S.tooltipW/2+d.follow.sample(now).first.toFloat()));val top=g.cardY-S.tooltipRise
    drawIntoCanvas{it.saveLayer(Rect(left-24,top-24,left+S.tooltipW+24,top+S.tooltipH+24),androidx.compose.ui.graphics.Paint().apply{alpha=f.alpha})}
    scale(0.96f+0.04f*f.alpha,0.96f+0.04f*f.alpha,Offset(left+S.tooltipW/2,top+S.tooltipH/2)){val shadow=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=S.tooltip.toArgb();if(Build.VERSION.SDK_INT>=28)setShadowLayer(12f,0f,8f,android.graphics.Color.argb(115,0,0,0))};drawIntoCanvas{it.nativeCanvas.drawRoundRect(left,top,left+S.tooltipW,top+S.tooltipH,16f,16f,shadow)};drawRoundRect(S.edge,Offset(left+0.5f/density,top+0.5f/density),Size(S.tooltipW-1/density,S.tooltipH-1/density),CornerRadius(16f),style=Stroke(1/density));val m=metrics[mi];val first=Data.rangeStart(sessions,ranges[ri]);val idx=first+min(f.pick,sessions.size-first-1);val w=d.roller.draw(this,Data.format(m.values[idx],m.decimals),now,still,left+14,top+12);text(m.unit,left+14+w+4,top+20,14f,18f,S.caption,500);text(ellipsis(m.caption,12f,S.tooltipW-28),left+14,top+44,12f,16f,S.caption);text(ellipsis(Data.longDate(sessions[idx]),10f,S.tooltipW-28),left+14,top+62,10f,14f,S.mute)};drawIntoCanvas{it.restore()}
    for(k in 0..1){val value=menus[k].sample(now).first.toFloat();if(value<=0)continue;val items=if(k==0)metrics.map{it.label}else ranges.map{it.label};val current=if(k==0)mi else ri;val x=menuX(k,g);val y=g.pillsY+40+S.menuGap-if(still)0f else 6*(1-value);val h=items.size*S.menuItem+12+2/density
        drawIntoCanvas{it.saveLayer(Rect(x-32,y-32,x+S.menuW+32,y+h+32),androidx.compose.ui.graphics.Paint().apply{alpha=value})};scale(if(still)1f else 0.94f+0.06f*value,if(still)1f else 0.94f+0.06f*value,Offset(x+S.menuW/2,y)){val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=S.menu.toArgb();if(Build.VERSION.SDK_INT>=28)setShadowLayer(16f,0f,12f,android.graphics.Color.argb(128,0,0,0))};drawIntoCanvas{it.nativeCanvas.drawRoundRect(x,y,x+S.menuW,y+h,18f,18f,p)};drawRoundRect(S.edge,Offset(x,y),Size(S.menuW,h),CornerRadius(18f),style=Stroke(1/density));for((i,label)in items.withIndex()){val rx=x+6+1/density;val ry=y+6+1/density+i*S.menuItem;val rw=S.menuW-12-2/density;if(kind==k&&(hover==i||pressedRow==i))drawRoundRect(S.pressed,Offset(rx,ry),Size(rw,S.menuItem),CornerRadius(12f));icon(if(k==0)1 else 2,rx+20.5f,ry+25,17f,if(i==current)S.ink else S.caption);text(ellipsis(label,15f,rw-69,500),rx+41,ry+15,15f,20f,if(i==current)S.ink else S.caption,500);if(i==current)icon(3,rx+rw-20,ry+25,16f,S.ink);if(i<items.lastIndex)drawRect(S.separator,Offset(rx+41,ry+S.menuItem-1/density),Size(rw-53,1/density))}};drawIntoCanvas{it.restore()}
    }
}
