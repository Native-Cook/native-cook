package com.ngocdevv.nativecook.blocks.buttons.tapearncoin

import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.BASE
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.LOOP
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.BURST_LIFE
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.RESUME_AFTER
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.Scene
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.Burst
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.clamp
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.seg
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.out
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.inside
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.inOut
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.mix
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.spring
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.ring
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.hash
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.sceneAt
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.column
import com.ngocdevv.nativecook.blocks.buttons.tapearncoin.TapEarnCoinMotion.coin
import kotlin.math.*
import kotlin.random.Random

/** Full-screen reward page. Native drawing avoids recomposing text for every odometer frame. */
@Composable
fun TapEarnCoin(autoPlay: Boolean = false) {
    val density = LocalDensity.current
    val top = WindowInsets.systemBars.getTop(density) / density.density
    val bottom = WindowInsets.systemBars.getBottom(density) / density.density
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { TapEarnCoinView(it).apply { configure(autoPlay, top, bottom) } },
        update = { it.configure(autoPlay, top, bottom) },
    )
}

private class TapEarnCoinView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val start = SystemClock.uptimeMillis()
    private var pausedMillis = 0L
    private var hiddenAt = 0L
    private fun now() = ((if (hiddenAt > 0) hiddenAt else SystemClock.uptimeMillis()) - start - pausedMillis) / 1000.0
    private var autoPlay = false
    private var safeTop = 0.0
    private var safeBottom = 0.0
    private var configured = false
    private var reduced = false
    private var manual = false
    private var autoStart = 0.0
    private var base = BASE
    private var targetAmount = BASE
    private var lastTouch = -100.0
    private var resumeAt = -1.0
    private var open = false
    private var openingUntil = -100.0
    private var taps = 3
    private var refillAt = -100.0
    private var refillCoinsAt = -100.0
    private var pressStart = -100.0
    private var chargeTick = -100.0
    private var pressing = false
    private var charge = 0.0
    private var lastTap = -100.0
    private var combo = 0
    private val labelStiffness = TapEarnCoinMotion.durationStiffness(1.0,0.0,0.42,0.5)
    private var popAt = -100.0
    private var shakeAt = -100.0
    private var ctaDown = false
    private var blueDown = false
    private var scene = Scene()
    private var gleam = -1.0
    private var fade = 1.0
    private var sheetFade = 1.0
    private var l = TapEarnCoinLayout(402.0, 874.0, 0.0, 0.0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val regular = Typeface.createFromAsset(context.assets,"tapearncoin/tapearncoin_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(context.assets,"tapearncoin/tapearncoin_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(context.assets,"tapearncoin/tapearncoin_Inter-SemiBold.ttf")
    private val bold = Typeface.createFromAsset(context.assets,"tapearncoin/tapearncoin_Inter-Bold.ttf")
    private val atlas = context.assets.open("tapearncoin/tapearncoin_coin-atlas.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
    private var gold: RuntimeShader? = null
    private val sound = TapEarnCoinAudio(context)
    private data class Timed(val at: Double,val action: () -> Unit)
    private val timers = mutableListOf<Timed>()
    private data class ManualBurst(val start: Double,val seed: Double,val pay: Double,val power: Double)
    private val bursts = mutableListOf<ManualBurst>()
    // Each animation owns one scalar; all durations are the reference's milliseconds / 1000.
    private data class Track(val start: Double,val delay: Double,val duration: Double,val from: Double,val to: Double,val curve: Int,val damping: Double,val velocity: Double,val stiffness: Double)
    private val tracks = mutableMapOf<String,Track>()
    private val values = mutableMapOf("press" to 0.0,"hop" to 0.0,"morph" to 0.0,"lift" to 0.0,"travel" to 0.0,"dim" to 0.0,"sheet" to 0.0,"swing" to 0.0,"amount" to BASE,"later" to 0.0,"leanX" to 0.0,"leanZ" to 0.0,"gleam" to -1.0,"fade" to 1.0,"sheetFade" to 1.0,"charge" to 0.0)
    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        if(Build.VERSION.SDK_INT>=33) gold=RuntimeShader(TapEarnCoinShader)
    }
    fun configure(auto: Boolean,top: Float,bottom: Float) {
        safeTop=top.toDouble();safeBottom=bottom.toDouble()
        val nextReduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
        val preferenceChanged=configured&&nextReduced!=reduced
        if(preferenceChanged) {
            if(!manual)copyScene()
            tracks.clear();timers.clear();bursts.clear();pressing=false;charge=0.0;set("charge",0.0)
            set("press",0.0);set("hop",0.0);set("leanX",0.0);set("leanZ",0.0);set("swing",0.0)
            val settled=if(open)1.0 else 0.0
            for(key in listOf("morph","lift","travel","dim","sheet"))set(key,settled)
            set("fade",1.0);set("sheetFade",1.0)
            resumeAt=-1.0;lastTouch=now()
        }
        if(!configured || auto!=autoPlay || preferenceChanged) {
            reduced=nextReduced;autoPlay=auto;manual=!auto||reduced;autoStart=now();configured=true
        }
        invalidate()
    }
    private fun buzz(heavy: Boolean=false) {performHapticFeedback(if(heavy)HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)}
    private fun after(seconds: Double,action: ()->Unit) {timers.add(Timed(now()+seconds,action));postInvalidateOnAnimation()}
    private fun v(key: String)=values[key]?:0.0
    private fun set(key: String,value: Double) {tracks.remove(key);values[key]=value}
    private fun anim(key: String,to: Double,duration: Double,delay: Double=0.0,curve: Int=4,damping: Double=0.8,velocity: Double=0.0) {
        val old=tracks[key]
        val inherited=if(old?.curve==3)TapEarnCoinMotion.durationSpring(old.from,old.to,old.velocity,max(0.0,now()-old.start-old.delay),old.damping,old.stiffness).velocity else 0.0
        var speed=inherited+velocity
        if((to>v(key)&&speed<0)||(to<v(key)&&speed>0))speed=0.0
        val stiffness=if(curve==3)TapEarnCoinMotion.durationStiffness(v(key)-to,speed,duration,damping) else 0.0
        tracks[key]=Track(now(),delay,duration,v(key),to,curve,damping,speed,stiffness)
        postInvalidateOnAnimation()
    }
    // Reanimated timing curves: (0.23,1,0.32,1), (0.77,0,0.175,1).
    private fun bezier(x: Double,x1: Double,y1: Double,x2: Double,y2: Double): Double {
        fun cubic(t: Double,a: Double,b: Double)=3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var a=0.0;var b=1.0
        repeat(16){val m=(a+b)/2;if(cubic(m,x1,x2)<x)a=m else b=m}
        return cubic((a+b)/2,y1,y2)
    }
    private fun advance(t: Double) {
        val done=mutableListOf<String>()
        for((key,a) in tracks) {
            val elapsed=t-a.start-a.delay
            if(elapsed<0) continue
            val u=clamp(elapsed/a.duration)
            if(a.curve==3) {
                val sample=TapEarnCoinMotion.durationSpring(a.from,a.to,a.velocity,elapsed,a.damping,a.stiffness)
                values[key]=sample.position
                if(sample.finished)done.add(key)
            } else {
                val progress=when(a.curve){2->bezier(u,0.77,0.0,0.175,1.0);0->u;4->if(u<0.5)2*u*u else 1-2*(1-u)*(1-u);else->bezier(u,0.23,1.0,0.32,1.0)}
                values[key]=mix(a.from,a.to,progress)
                if(u>=1)done.add(key)
            }
        }
        done.forEach {tracks.remove(it)}
        val due=timers.filter {it.at<=t}.toList();timers.removeAll(due.toSet());due.forEach {it.action()}
    }
    private fun copyScene() {
        val s=scene
        values.putAll(mapOf("press" to s.press,"hop" to s.hop,"morph" to s.morph,"lift" to s.lift,"travel" to s.travel,"dim" to s.dim,"sheet" to s.sheet,"swing" to s.swing,"amount" to s.amount,"later" to s.later,"leanX" to s.leanX,"leanZ" to s.leanZ,"gleam" to gleam))
    }
    private fun takeOver() {
        val t=now();lastTouch=t;resumeAt=-1.0
        if(manual)return
        val loop=(t-autoStart)%LOOP
        copyScene();manual=true
        bursts.clear();TapEarnCoinMotion.bursts(loop).forEach {bursts.add(ManualBurst(t-it.age,it.seed,it.pay,it.power))}
        targetAmount=round(sceneAt(min(loop+0.75,LOOP),base).amount)
        taps=if(scene.label>=4)0 else scene.label
        if(scene.label>=4)startRefill()
        open=loop>3.0&&loop<7.2
        anim("press",0.0,0.42,curve=3,damping=0.5);anim("leanX",0.0,0.42,curve=3,damping=0.5);anim("leanZ",0.0,0.42,curve=3,damping=0.5)
        anim("later",0.0,0.15);anim("amount",targetAmount,0.42,curve=1)
        val dest=if(open)1.0 else 0.0
        anim("lift",dest,if(open)0.4 else 0.45,curve=2);anim("morph",dest,0.52,curve=2)
        anim("travel",dest,0.56,curve=2);anim("dim",dest,if(open)0.36 else 0.30)
        anim("sheet",dest,if(open)0.52 else 0.42,curve=3,damping=if(open)0.85 else 1.0)
        anim("hop",0.0,0.4,curve=3,damping=0.7)
    }
    private fun openSheet() {
        open=true
        // Start the idle interval after the entire 2.5 s flight has landed.
        openingUntil=now()+(if(reduced)0.36 else 2.5)
        lastTouch=openingUntil
        set("sheetFade",1.0)
        announceForAccessibility("Out of taps. Want a reminder when you can tap again?")
        if(reduced) {
            anim("fade",0.0,0.16);set("sheetFade",0.0);anim("sheetFade",1.0,0.26);anim("dim",1.0,0.26)
            after(0.16){set("morph",1.0);set("lift",1.0);set("travel",1.0);set("sheet",1.0);anim("fade",1.0,0.2)}
            return
        }
        sound.play("morph")
        anim("hop",0.55,0.15,curve=1);after(0.15){anim("hop",0.0,0.23,curve=2)}
        anim("lift",1.0,0.46,0.16,1);anim("morph",1.0,1.1,0.3,2)
        after(1.4){sound.play("ding");anim("swing",0.32,0.11);after(0.11){anim("swing",0.0,1.2,curve=3,damping=0.16)}}
        anim("dim",1.0,0.48,1.6,1);anim("travel",1.0,0.74,1.76,2);anim("sheet",1.0,0.64,1.76,3,0.82)
        after(2.5){buzz(true);lastTouch=now()}
    }
    private fun closeSheet(refill: Boolean=true,velocity: Double=0.0) {
        if(!open)return
        open=false;openingUntil=-100.0
        if(reduced) {
            anim("fade",0.0,0.16);anim("dim",0.0,0.22);anim("sheetFade",0.0,0.2)
            after(0.16){set("morph",0.0);set("lift",0.0);set("travel",0.0);set("sheet",0.0);anim("fade",1.0,0.2)}
        } else {
            anim("sheet",0.0,0.46,curve=3,damping=1.0,velocity=velocity);anim("dim",0.0,0.42,curve=2)
            anim("travel",0.0,0.64,curve=2);anim("morph",0.0,0.9,0.46,2);anim("lift",0.0,0.45,1.24,2)
            after(0.46){sound.play("morph")}
            after(1.69){anim("hop",0.45,0.08);after(0.08){anim("hop",0.0,0.64,curve=3,damping=0.32)};buzz()}
        }
        if(refill&&taps<=0)after(if(reduced)0.2 else 0.7){startRefill()}
    }
    private fun startRefill() {refillAt=now();refillCoinsAt=refillAt+2.55}
    private fun tap(held: Double=0.0) {
        if(open)return
        if(taps<=0){shakeAt=now();popAt=now();buzz(true);return}
        val t=now();combo=if(t-lastTap<0.45)min(combo+1,4) else 0;lastTap=t
        val power=max(min(combo,2),if(held>0.5)2 else if(held>0.15)1 else 0)
        val pay=3+Random.nextInt(7)+combo+round(held*12).toInt()
        targetAmount+=pay
        if(!reduced)bursts.add(ManualBurst(t,1+Random.nextDouble()*90,pay.toDouble(),power.toDouble()))
        anim("amount",targetAmount,if(reduced)0.001 else 0.52,if(reduced)0.0 else 0.12,1)
        if(!reduced){set("gleam",-1.0);anim("gleam",1.0,0.76,curve=1)}
        if(!reduced)sound.play("coin",1+0.07*max(combo.toDouble(),power*1.5))
        taps--;popAt=t;announceForAccessibility("${targetAmount.toInt()} cents earned. $taps taps left.")
        if(taps==0)after(if(reduced)0.25 else 0.38){openSheet()}
    }
    private fun remind() {
        if(!reduced)sound.play("ding")
        buzz(true)
        if(!reduced){anim("swing",-0.4,0.1);after(0.1){anim("swing",0.0,1.3,curve=3,damping=0.12)}}
        after(0.75){closeSheet()}
    }
    private fun update(t: Double): List<Burst> {
        advance(t)
        if(!manual) {
            val loop=(t-autoStart)%LOOP;scene=sceneAt(loop,base);gleam=TapEarnCoinMotion.gleam(loop);fade=1.0;sheetFade=1.0
            return TapEarnCoinMotion.bursts(loop)
        }
        if(pressing) {
            charge=clamp((t-pressStart-0.3)/1.2);set("charge",charge)
            if(!reduced&&charge>0&&charge<1&&t-chargeTick>0.2){chargeTick=t;buzz()}
        }
        if(!pressing)charge=v("charge")
        if(refillAt>0&&t-refillAt>=3) {taps=3;refillAt=-100.0;popAt=t;buzz()}
        if(autoPlay&&!reduced&&!pressing&&t>=openingUntil&&t-lastTouch>RESUME_AFTER&&resumeAt<0) {
            resumeAt=t+1.1;timers.clear();if(open)closeSheet(false);refillAt=-100.0;taps=3;popAt=t
        }
        if(resumeAt>=0&&t>=resumeAt) {base=targetAmount;set("amount",targetAmount);autoStart=t;manual=false;resumeAt=-1.0;return update(t)}
        scene=Scene(v("press"),v("hop"),v("morph"),v("lift"),v("travel"),v("dim"),v("sheet"),v("swing"),v("amount"),v("later"),if(refillAt>0)4 else taps,v("leanX"),v("leanZ"))
        gleam=v("gleam");fade=v("fade");sheetFade=v("sheetFade")
        bursts.removeAll {t-it.start>BURST_LIFE}
        return if(reduced)emptyList() else bursts.map {Burst(t-it.start,it.seed,it.pay,it.power)}
    }
    private fun color(c: Int,alpha: Double=1.0) {
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=c;paint.alpha=(Color.alpha(c)*clamp(alpha)).toInt()
    }
    private fun capFor(size: Double)=when(size){28.0->1.25;16.0,17.0->1.3;14.0->1.4;22.0->1.2;27.0->1.0;else->Double.POSITIVE_INFINITY}
    private fun fontFactor(cap: Double)=if(cap==0.0)1.0 else min(resources.configuration.fontScale.toDouble(),cap)
    private fun prepareText(size: Double,font: Typeface,tracking: Double?=null,cap: Double=capFor(size)) {
        paint.typeface=font;paint.textSize=(size*fontFactor(cap)).toFloat()
        val points=tracking?:when(size){28.0-> -0.6;16.0-> -0.2;27.0-> -0.3;14.0-> -0.1;17.0-> -0.2;23.0-> -0.5;else->0.0}
        paint.letterSpacing=(points/size).toFloat()
    }
    private fun text(c: Canvas,value: String,x: Double,y: Double,size: Double,ink: Int,font: Typeface=regular,center: Boolean=false,alpha: Double=1.0,tracking: Double?=null,cap: Double=capFor(size)) {
        color(ink,alpha);prepareText(size,font,tracking,cap);paint.textAlign=if(center)Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(value,x.toFloat(),y.toFloat(),paint)
    }
    private fun textBaseline(top: Double,line: Double,size: Double,font: Typeface,cap: Double=capFor(size),scaleLine: Boolean=true): Double {
        prepareText(size,font,cap=cap);val m=paint.fontMetrics
        return top+((if(scaleLine)line*fontFactor(cap) else line)-(m.descent-m.ascent))/2-m.ascent
    }
    private fun wrapped(value: String,size: Double,font: Typeface,width: Double,cap: Double=capFor(size),tracking: Double?=null): List<String> {
        prepareText(size,font,tracking,cap)
        val lines=mutableListOf<String>()
        for(paragraph in value.split('\n')) {
            var line=""
            for(word in paragraph.split(' ')) {
                val candidate=if(line.isEmpty())word else "$line $word"
                if(line.isNotEmpty()&&paint.measureText(candidate)>width) {lines.add(line);line=word} else line=candidate
            }
            lines.add(line)
        }
        return lines
    }
    private fun lines(c: Canvas,copy: List<String>,x: Double,top: Double,line: Double,size: Double,ink: Int,font: Typeface,center: Boolean=false,alpha: Double=1.0,cap: Double=capFor(size),tracking: Double?=null): Double {
        val step=line*fontFactor(cap)
        copy.forEachIndexed {index,value->text(c,value,x,textBaseline(top+index*step,line,size,font,cap),size,ink,font,center,alpha,tracking,cap)}
        return copy.size*step
    }
    private data class SheetCopy(val title: List<String>,val body: List<String>,val row: List<String>,val titleY: Double,val bodyY: Double,val rowY: Double,val rowH: Double,val blueY: Double)
    private fun sheetCopy(): SheetCopy {
        val title=wrapped("Want a reminder when\nyou can tap again?",23.0,bold,l.w-44)
        val body=wrapped("Taps refill every hour.",15.0,regular,l.w-44)
        val row=wrapped("[Optional] Tap-to-earn reminders",14.0,regular,l.w-74,Double.POSITIVE_INFINITY,0.0)
        val titleY=64*l.s+l.bellSize+12
        val bodyY=titleY+title.size*32*fontFactor(Double.POSITIVE_INFINITY)+8
        val rowY=bodyY+body.size*21*fontFactor(Double.POSITIVE_INFINITY)+20
        val rowH=max(44.0,row.size*20*fontFactor(Double.POSITIVE_INFINITY))
        return SheetCopy(title,body,row,titleY,bodyY,rowY,rowH,rowY+rowH+20)
    }
    private fun box(c: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,ink: Int,alpha: Double=1.0) {
        color(ink,alpha);c.drawRoundRect(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat(),r.toFloat(),r.toFloat(),paint)
    }
    private fun label(c: Canvas,t: Double) {
        val refill=scene.label>=4
        val count=if(manual&&refillAt>0)max(1,3-floor(t-refillAt).toInt()) else 1
        val prefix=if(refill)"Refill in " else "Taps left: "
        val suffix=if(refill)"00:0$count" else scene.label.toString()
        prepareText(14.0,medium,-0.1);val a=paint.measureText(prefix).toDouble();prepareText(14.0,semibold,-0.1);val b=paint.measureText(suffix)
        val loop=(t-autoStart)%LOOP
        val popAge=if(manual)t-popAt else {
            val latest=listOf(1.01,1.76,2.51,9.3).lastOrNull {it<=loop}
            if(latest==null)-1.0 else loop-latest
        }
        val pulse=when {
            popAge<0->0.0
            popAge<0.09->{val u=popAge/0.09;if(u<0.5)2*u*u else 1-2*(1-u)*(1-u)}
            else->TapEarnCoinMotion.durationSpring(1.0,0.0,0.0,popAge-0.09,0.5,labelStiffness).position
        }
        val scale=if(reduced)1.0 else 1+0.08*pulse
        c.save();c.scale(scale.toFloat(),scale.toFloat(),(l.w/2).toFloat(),(l.labelTop+10).toFloat())
        val shake=if(manual&&!reduced)max(0.0,1-(t-shakeAt)/0.47)*5*sin(t*70) else 0.0
        val left=(l.w-a-b)/2+shake;val baseline=textBaseline(l.labelTop,20.0,14.0,medium)
        text(c,prefix,left,baseline,14.0,TapEarnCoinColor.INK,medium)
        text(c,suffix,left+a,baseline,14.0,TapEarnCoinColor.LABEL,semibold)
        c.restore()
    }
    private fun drawPage(c: Canvas,t: Double,live: List<Burst>) {
        paint.shader=LinearGradient(0f,0f,0f,l.h.toFloat(),intArrayOf(0xfffff3df.toInt(),0xfffff9f0.toInt(),Color.WHITE),floatArrayOf(0f,0.5f,0.72f),Shader.TileMode.CLAMP)
        paint.alpha=255;c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),paint);paint.shader=null
        val head=1-0.7*scene.lift*(1-scene.travel)
        val title=wrapped("Keep tapping the button\nto earn 1,000¢",28.0,bold,l.w-48)
        val titleH=lines(c,title,l.w/2,l.titleTop,37.0,28.0,TapEarnCoinColor.TITLE,bold,true,head)
        val subtitle=wrapped("Reach 1,000¢ and it’s paid out as cash.",16.0,regular,l.w-48)
        lines(c,subtitle,l.w/2,l.titleTop+titleH+(l.subtitleTop-l.titleTop-74),22.0,16.0,TapEarnCoinColor.INK,regular,true,head)
        for(i in 0..2) {
            val x=l.digitLeft+i*(l.digitW+l.digitGap)
            val bump=1+0.07*TapEarnCoinMotion.bump(live,i)
            c.save();c.scale(bump.toFloat(),bump.toFloat(),(x+l.digitW/2).toFloat(),(l.digitsTop+l.digitH/2).toFloat())
            box(c,x,l.digitsTop,l.digitW,l.digitH,10*l.w/402,TapEarnCoinColor.BOX)
            c.clipRect(x.toFloat(),l.digitsTop.toFloat(),(x+l.digitW).toFloat(),(l.digitsTop+l.digitH).toFloat())
            val col=column(scene.amount,2-i)%10
            val whole=floor(col).toInt();val fraction=col-whole
            for(j in 0..1) {
                val top=l.digitsTop+(j-fraction)*l.digitH
                text(c,((whole+j)%10).toString(),x+l.digitW/2,textBaseline(top,l.digitH,27.0,semibold,scaleLine=false),27.0,TapEarnCoinColor.DIGIT,semibold,true)
            }
            c.restore()
        }
        text(c,"¢",l.digitLeft+3*l.digitW+3*l.digitGap+2,textBaseline(l.digitsTop,l.digitH,22.0,semibold,scaleLine=false),22.0,TapEarnCoinColor.UNIT,semibold)
        label(c,t)
        val cs=if(ctaDown)0.96f else 1f
        c.save();c.scale(cs,cs,(l.w/2).toFloat(),(l.ctaTop+28).toFloat())
        box(c,l.side,l.ctaTop,l.w-2*l.side,l.ctaH,14.0,if(ctaDown)TapEarnCoinColor.CTA_DOWN else TapEarnCoinColor.CTA)
        text(c,"Share to earn more",l.w/2,textBaseline(l.ctaTop,56.0,17.0,semibold,scaleLine=false),17.0,Color.WHITE,semibold,true);c.restore()
        color(0xff14100a.toInt(),0.62*scene.dim);c.drawRect(0f,0f,l.w.toFloat(),l.h.toFloat(),paint)
        val sheetY=l.sheetTop+(1-scene.sheet)*(l.sheetH+24)
        if(scene.sheet>0.001 || sheetY<l.h) {
            c.save();c.translate(0f,sheetY.toFloat())
            val copy=sheetCopy()
            val laterCopy=wrapped("Maybe later",15.0,medium,l.w-44)
            box(c,0.0,0.0,l.w,max(l.sheetH+80,copy.blueY+76+laterCopy.size*19*fontFactor(Double.POSITIVE_INFINITY)+40),18.0,Color.WHITE,sheetFade)
            lines(c,copy.title,22.0,copy.titleY,32.0,23.0,TapEarnCoinColor.SHEET_TITLE,bold,alpha=sheetFade)
            lines(c,copy.body,22.0,copy.bodyY,21.0,15.0,TapEarnCoinColor.BODY,regular,alpha=sheetFade)
            val rowTop=copy.rowY+(copy.rowH-copy.row.size*20*fontFactor(Double.POSITIVE_INFINITY))/2
            lines(c,copy.row,22.0,rowTop,20.0,14.0,TapEarnCoinColor.ROW,regular,alpha=sheetFade,cap=Double.POSITIVE_INFINITY,tracking=0.0)
            text(c,"›",l.w-30,textBaseline(copy.rowY-2,copy.rowH,22.0,regular,cap=Double.POSITIVE_INFINITY,scaleLine=false),22.0,0xffb0b8c1.toInt(),alpha=sheetFade,cap=Double.POSITIVE_INFINITY)
            val blueY=copy.blueY
            c.save();val bs=if(blueDown)0.96f else 1f;c.scale(bs,bs,(l.w/2).toFloat(),(blueY+28).toFloat())
            box(c,l.side,blueY,l.w-2*l.side,56.0,14.0,if(blueDown)TapEarnCoinColor.BLUE_DOWN else TapEarnCoinColor.BLUE,sheetFade)
            text(c,"Agree and remind me",l.w/2,textBaseline(blueY,56.0,17.0,semibold,scaleLine=false),17.0,Color.WHITE,semibold,true,sheetFade);c.restore()
            c.save();val ls=(1-0.04*scene.later).toFloat();c.scale(ls,ls,(l.w/2).toFloat(),(blueY+56+20+9).toFloat())
            lines(c,laterCopy,l.w/2,blueY+56+20,19.0,15.0,TapEarnCoinColor.ROW,medium,true,sheetFade*(1-0.35*scene.later));c.restore()
            c.restore()
        }
    }
    private data class ObjectPose(val x: Double,val y: Double,val unit: Double)
    private fun objectPose(t: Double): ObjectPose {
        val tr=scene.travel;val lf=scene.lift;val by=l.bellY+(1-scene.sheet)*l.sheetH
        val shake=if(manual&&!reduced)max(0.0,1-(t-shakeAt)/0.47) else 0.0
        return ObjectPose(mix(l.coinX,l.bellX,tr)+shake*7*sin(t*70),mix(l.coinY-64*lf,by,tr)-sin(PI*tr)*l.coinR*0.45,mix(l.coinR*(1+0.1*lf),l.bellSize/1.3,tr))
    }
    private fun drawGold(c: Canvas,t: Double,live: List<Burst>,o: ObjectPose) {
        val s=gold
        if(Build.VERSION.SDK_INT>=33&&s!=null) {
            s.setFloatUniform("uCentre",(o.x*d).toFloat(),(o.y*d).toFloat());s.setFloatUniform("uUnit",(o.unit*d).toFloat());s.setFloatUniform("uPixelRatio",1f)
            s.setFloatUniform("uTilt",mix(0.56,0.12,scene.morph).toFloat());s.setFloatUniform("uPress",(if(reduced)0.0 else scene.press*(1+0.3*charge)).toFloat())
            s.setFloatUniform("uHop",(if(reduced)0.0 else scene.hop+charge*0.07*sin(t*57)).toFloat());s.setFloatUniform("uMorph",scene.morph.toFloat());s.setFloatUniform("uSwing",scene.swing.toFloat())
            s.setFloatUniform("uShadow",(mix(1.0,0.4,scene.travel)*(1-0.85*sin(PI*scene.travel))*(1-0.55*scene.lift*(1-scene.travel))).toFloat())
            s.setFloatUniform("uGleam",gleam.toFloat());s.setFloatUniform("uAlpha",fade.toFloat());s.setFloatUniform("uLean",scene.leanX.toFloat(),scene.leanZ.toFloat());s.setFloatUniform("uGlow",(if(reduced)0.0 else max(TapEarnCoinMotion.glow(live),charge*0.9)).toFloat())
            shaderPaint.shader=s;c.drawRect(((o.x-1.65*o.unit)*d).toFloat(),((o.y-1.65*o.unit)*d).toFloat(),((o.x+1.65*o.unit)*d).toFloat(),((o.y+1.65*o.unit)*d).toFloat(),shaderPaint)
        } else {
            c.save();c.scale(d,d);c.translate(o.x.toFloat(),o.y.toFloat());c.rotate((scene.swing*180/PI).toFloat());c.scale(o.unit.toFloat(),o.unit.toFloat())
            color(0xffdeb549.toInt(),fade)
            if(scene.morph<0.5){c.drawOval(-1f,-0.25f,1f,0.42f,paint);color(0xffffd469.toInt(),fade);c.drawOval(-0.62f,(-0.5+0.12*scene.press).toFloat(),0.62f,0.03f,paint);text(c,"¢",0.0,-0.04,0.43,0xffa57616.toInt(),bold,true,fade,cap=0.0)}
            else {val path=Path().apply{moveTo(-0.6f,0.3f);cubicTo(-0.27f,-0.05f,-0.45f,-0.65f,0f,-0.65f);cubicTo(0.45f,-0.65f,0.27f,-0.05f,0.6f,0.3f);close()};c.drawPath(path,paint);c.drawOval(-0.68f,0.24f,0.68f,0.4f,paint);c.drawCircle(0f,-0.72f,0.08f,paint);c.drawCircle(0f,0.43f,0.12f,paint);color(0xffed2922.toInt(),fade);c.drawCircle(0.37f,-0.6f,0.155f,paint)}
            c.restore()
        }
    }
    private fun atlasCoin(c: Canvas,x: Double,y: Double,r: Double,roll: Double,spin: Double,alpha: Double) {
        if(alpha<=0.01||r<0.5)return
        val tau=2*PI;val f=(floor((((roll%tau)+tau)%tau)/tau*24+0.5).toInt())%24
        val size=r/40*96
        c.save();c.translate(x.toFloat(),y.toFloat());c.rotate((spin*180/PI).toFloat())
        color(Color.WHITE,alpha);c.drawBitmap(atlas,Rect(f*96,0,(f+1)*96,96),RectF((-size/2).toFloat(),(-size/2).toFloat(),(size/2).toFloat(),(size/2).toFloat()),paint);c.restore()
    }
    private fun simpleCoin(c: Canvas,x: Double,y: Double,r: Double,tilt: Double,spin: Double,alpha: Double,lit: Double) {
        if(alpha<=0.01||r<0.5)return
        val face=max(0.12,abs(tilt));val ry=r*face;val thick=r*0.2*sqrt(1-face*face)
        c.save();c.translate(x.toFloat(),y.toFloat());c.rotate((spin*180/PI).toFloat())
        color(0xff6c6c66.toInt(),alpha);c.drawOval((-r).toFloat(),(-ry+thick).toFloat(),r.toFloat(),(ry+thick).toFloat(),paint)
        val hi=round(200+52*lit).toInt();val lo=round(118+40*lit).toInt()
        paint.shader=LinearGradient((-r*0.8).toFloat(),(-ry).toFloat(),(r*0.7).toFloat(),ry.toFloat(),Color.rgb(hi,hi,hi-6),Color.rgb(lo,lo+2,lo-4),Shader.TileMode.CLAMP);paint.alpha=(255*clamp(alpha)).toInt()
        c.drawOval((-r).toFloat(),(-ry).toFloat(),r.toFloat(),ry.toFloat(),paint)
        color(0xff787870.toInt(),alpha*0.7);paint.style=Paint.Style.STROKE;paint.strokeWidth=max(0.6,r*0.09).toFloat();c.drawOval((-r*0.7).toFloat(),(-ry*0.7).toFloat(),(r*0.7).toFloat(),(ry*0.7).toFloat(),paint)
        color(0xfffffffa.toInt(),alpha*0.85*lit*lit);c.drawOval((-r*0.62).toFloat(),(-ry*0.78).toFloat(),(r*0.08).toFloat(),(-ry*0.28).toFloat(),paint);c.restore()
    }
    private fun drawCoins(c: Canvas,t: Double,live: List<Burst>,o: ObjectPose) {
        val keep=clamp(1-1.2*scene.dim);val pullRaw=seg(scene.morph,0.0,0.55);val pull=inside(pullRaw)
        for(b in live)for(i in 0 until 9+3*b.power.roundToInt()) {
            val index=(i+floor(b.seed).toInt())%3
            val coin=coin(b.age,b.seed,i,b.power,l.coinX,l.coinY-0.32*l.coinR,l.coinR,l.digitX(index),l.digitsTop+l.digitH/2,l.ctaTop)
            atlasCoin(c,mix(coin.x,o.x,pull),mix(coin.y,o.y,pull),coin.r*(1-0.85*pull),coin.roll,coin.spin,coin.alpha*keep*(1-seg(pullRaw,0.75,1.0)))
        }
        val refillAge=if(manual)t-refillCoinsAt else (t-autoStart)%LOOP-8.85
        if(!reduced&&refillAge>=0&&refillAge<0.75)for(i in 0..2) {
            val a=refillAge-i*0.07;if(a<0)continue
            val fall=inside(seg(a,0.0,0.38));val settle=seg(a,0.38,0.6)
            simpleCoin(c,l.w/2+34+(i-1)*16,l.labelTop+9-150+150*fall-6*ring(max(0.0,a-0.38),3.0,10.0),7*(1-out(settle)),0.55+0.45*cos(a*18+i),a*2,1-settle,0.5+0.5*sin(a*21+i))
        }
        for(b in live) {
            if(b.age<0||b.age>1)continue
            val rise=out(seg(b.age,0.0,0.9));val alpha=clamp(b.age/0.06)*(1-seg(b.age,0.75,1.0))*keep
            val scale=(0.7+0.3*spring(b.age,0.34,0.55))*(1+0.12*b.power)
            val x=l.coinX+(hash(b.seed*3.3)-0.5)*0.9*l.coinR;val y=l.coinY-1.02*l.coinR-rise*l.coinR*0.32
            val caption="+${b.pay.toInt()}¢";prepareText(14.0,semibold,0.0,0.0);val pw=paint.measureText(caption)+22
            c.save();c.translate(x.toFloat(),y.toFloat());c.scale(scale.toFloat(),scale.toFloat())
            box(c,-pw/2.0,-15.0,pw.toDouble(),30.0,15.0,0xff343840.toInt(),0.88*alpha)
            text(c,caption,0.0,4.6,14.0,Color.WHITE,semibold,true,alpha,tracking=0.0,cap=0.0);c.restore()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if(width==0||height==0)return
        val t=now();l=TapEarnCoinLayout(width/d.toDouble(),height/d.toDouble(),safeTop,safeBottom)
        val live=update(t)
        canvas.save();canvas.scale(d,d);drawPage(canvas,t,live);canvas.restore()
        val o=objectPose(t);drawGold(canvas,t,live,o)
        canvas.save();canvas.scale(d,d);drawCoins(canvas,t,live,o);canvas.restore()
        // Idle manual views sleep; frame-driven work resumes only while an effect or timer is live.
        if(!manual||pressing||tracks.isNotEmpty()||timers.isNotEmpty()||live.isNotEmpty()||refillAt>0||resumeAt>0||
            (autoPlay&&!reduced&&t-lastTouch<=RESUME_AFTER)||t-popAt<1.0||t-shakeAt<0.5)postInvalidateOnAnimation()
    }
    private var touchTarget=0
    private var downX=0.0
    private var downY=0.0
    private var downSheet=1.0
    private var velocity: VelocityTracker?=null
    private fun sheetY()=l.sheetTop+(1-scene.sheet)*(l.sheetH+24)
    private fun blueY()=sheetCopy().blueY
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x=event.x/d.toDouble();val y=event.y/d.toDouble();val t=now()
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX=x;downY=y;touchTarget=0
                if(scene.sheet>0.02&&y>=sheetY()) {
                    val local=y-sheetY();val copy=sheetCopy();touchTarget=when {
                        local>=blueY()&&local<=blueY()+56 -> 3
                        local>=blueY()+64 -> 4
                        local>=copy.rowY&&local<copy.rowY+copy.rowH -> 5
                        else -> 2
                    }
                    takeOver();downSheet=scene.sheet
                    if(touchTarget==3)blueDown=true
                    if(touchTarget==4)anim("later",1.0,0.08)
                } else if(x>=l.coinX-l.coinR&&x<=l.coinX+l.coinR&&y>=l.coinY-0.85*l.coinR&&y<=l.coinY+0.75*l.coinR) {
                    takeOver()
                    if(v("sheet")<=0.02&&v("morph")<=0.02&&v("lift")<=0.02) {
                        touchTarget=1;pressing=true;pressStart=t;chargeTick=t
                        if(!reduced) {
                            anim("press",1.0,0.09,curve=1)
                            anim("leanX",((x-l.coinX)/(2*l.coinR*0.32)).coerceIn(-1.0,1.0)*0.075,0.09,curve=1)
                            val local=y-(l.coinY-l.coinR*0.85);val capY=l.coinR*0.55
                            anim("leanZ",((local-capY)/(2*l.coinR*0.2)).coerceIn(-1.0,1.0)*0.06,0.09,curve=1)
                        }
                        buzz()
                    }
                } else if(y>=l.ctaTop&&y<=l.ctaTop+56&&x>=l.side&&x<=l.w-l.side&&scene.dim<0.1) {
                    takeOver();touchTarget=6;ctaDown=true;buzz()
                }
                if(touchTarget==0)return false
                parent?.requestDisallowInterceptTouchEvent(true)
                velocity?.recycle();velocity=VelocityTracker.obtain();velocity?.addMovement(event)
                invalidate();return true
            }
            MotionEvent.ACTION_MOVE -> {
                if(touchTarget==0)return false
                velocity?.addMovement(event);lastTouch=t
                val dy=y-downY
                if(touchTarget in 2..5 && abs(dy)>8) {
                    touchTarget=2;blueDown=false;set("later",0.0)
                    if(!reduced) {
                    val v=if(dy>=0)max(0.0,downSheet-dy/l.sheetH) else downSheet+(-dy/(1+-dy/60))/l.sheetH
                    set("sheet",v)
                    val u=1-min(v,1.0);set("travel",1-inOut(seg(u,0.25,0.9)));set("morph",1-inOut(seg(u,0.3,0.95)));set("dim",1-inOut(seg(u,0.1,1.0)))
                    }
                }
                invalidate();return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                if(touchTarget==0)return false
                val success=event.actionMasked==MotionEvent.ACTION_UP
                velocity?.addMovement(event);velocity?.computeCurrentVelocity(1000);val vy=(velocity?.yVelocity?:0f)/d
                velocity?.recycle();velocity=null
                lastTouch=t;resumeAt=-1.0
                when(touchTarget) {
                    1 -> {
                        pressing=false;anim("press",0.0,0.48,curve=3,damping=0.42);anim("leanX",0.0,0.42,curve=3,damping=0.45);anim("leanZ",0.0,0.42,curve=3,damping=0.45)
                        val held=charge;anim("charge",0.0,0.22,curve=1)
                        if(success&&hypot(x-downX,y-downY)<=80&&t-pressStart<60)tap(held)
                    }
                    2 -> {
                        if(success&&(y-downY>120||vy>800))closeSheet(velocity=-vy/l.sheetH)
                        else {anim("sheet",1.0,0.42,curve=3,damping=0.8,velocity=-vy/l.sheetH);anim("travel",1.0,0.42,curve=3,damping=0.85);anim("morph",1.0,0.42,curve=3,damping=0.85);anim("dim",1.0,0.26)}
                    }
                    3 -> {blueDown=false;if(success)remind()}
                    4 -> {anim("later",0.0,0.2);if(success)closeSheet()}
                    6 -> ctaDown=false
                }
                touchTarget=0;parent?.requestDisallowInterceptTouchEvent(false)
                if(success)performClick()
                invalidate();return true
            }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean {super.performClick();return true}
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className="android.widget.Button"
        info.contentDescription=if(open)"Want a reminder when you can tap again? Taps refill every hour." else "Tap to earn coins. ${scene.amount.roundToInt()} cents earned. ${if(scene.label>=4)"Out of taps." else "${scene.label} taps left."}"
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,if(open)"Agree and remind me" else "Tap to earn coins"))
        if(open)info.addAction(AccessibilityNodeInfo.AccessibilityAction(0x01020001,"Maybe later"))
        else info.addAction(AccessibilityNodeInfo.AccessibilityAction(0x01020002,"Share to earn more"))
    }
    override fun performAccessibilityAction(action: Int,arguments: Bundle?): Boolean {
        when(action) {
            AccessibilityNodeInfo.ACTION_CLICK -> {takeOver();if(open)remind() else tap();invalidate();return true}
            0x01020001 -> {takeOver();closeSheet();invalidate();return true}
            0x01020002 -> {takeOver();buzz();invalidate();return true}
        }
        return super.performAccessibilityAction(action,arguments)
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE && hiddenAt == 0L) hiddenAt = SystemClock.uptimeMillis()
        else if (visibility == VISIBLE && hiddenAt > 0L) {
            pausedMillis += SystemClock.uptimeMillis() - hiddenAt
            hiddenAt = 0L
            if(configured)configure(autoPlay,safeTop.toFloat(),safeBottom.toFloat())
            postInvalidateOnAnimation()
        }
    }
    override fun onDetachedFromWindow() {velocity?.recycle();velocity=null;sound.release();super.onDetachedFromWindow()}
}

/** Prefetched one-shot cues. Silent/vibrate modes are respected; the scripted loop never calls play. */
private class TapEarnCoinAudio(private val context: Context) {
    private val pool=SoundPool.Builder().setMaxStreams(6).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids=mutableMapOf<String,Int>()
    private val loaded=mutableSetOf<Int>()
    private val streams=mutableMapOf<String,Int>()
    init {
        pool.setOnLoadCompleteListener {_,id,status->if(status==0)loaded.add(id)}
        for(cue in listOf("coin","morph","ding"))context.assets.openFd("tapearncoin/tapearncoin_tec-$cue.wav").use {ids[cue]=pool.load(it,1)}
    }
    fun play(cue: String,rate: Double=1.0) {
        val manager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if(manager.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
        val id=ids[cue]?:return
        if(id !in loaded)return
        val volume=when(cue){"morph"->0.5f;"coin"->0.75f;else->0.8f}
        streams.remove(cue)?.let {pool.stop(it)}
        streams[cue]=pool.play(id,volume,volume,1,0,rate.toFloat())
    }
    fun release() {pool.release()}
}
