package com.ngocdevv.nativecook.blocks.animations.gearpuckspeedline

import kotlin.math.*

/** Direct analytic port of RN's pure score; tables only cache phase integration and color crossings. */
internal class GearPuckSpeedLineScore {
    private data class Crossing(val t:Double,val from:Int,val to:Int,val x:Double)
    private fun seg(t:Double,a:Double,b:Double)=((t-a)/(b-a)).coerceIn(0.0,1.0)
    private fun easeOut(k:Double)=1-(1-k).pow(3)
    private fun easeInOut(k:Double)=if(k<.5)4*k*k*k else 1-(-2*k+2).pow(3)/2
    private fun climb(x:Double):Double {
        if(x<=0)return 0.0;if(x>=1)return 1.0
        var lo=0.0;var hi=1.0
        repeat(22){val u=(lo+hi)/2;val bx=3*(1-u)*(1-u)*u*.42+3*(1-u)*u*u*.22+u*u*u;if(bx<x)lo=u else hi=u}
        val u=(lo+hi)/2;return 3*(1-u)*(1-u)*u*.04+3*(1-u)*u*u+u*u*u
    }
    fun speedAt(t:Double):Double=when {
        t<1.68->0.0;t<3.88->132*climb(seg(t,1.68,3.88));t<4.78->132.0
        t<6.38->132+65*climb(seg(t,4.78,6.38));t<6.85->197.0
        t<8.15->197*(1-easeInOut(seg(t,6.85,8.15)));else->0.0
    }
    private fun band(speed:Double)=if(speed<25)0 else if(speed<120)1 else if(speed<165)2 else 3
    private fun crest(speed:Double)=56+270*min(abs(speed),200.0)/200
    private val phases:DoubleArray by lazy {
        val out=DoubleArray(2001);var acc=0.0
        for(i in 1..2000){acc+=(6+1.2*speedAt((i-.5)*.005))*.005;out[i]=acc}
        val scale=(acc/24).roundToInt()*24/acc
        out.map{it*scale}.toDoubleArray()
    }
    private val crossings:List<Crossing> by lazy {
        val out=mutableListOf<Crossing>();var previous=band(speedAt(0.0))
        for(i in 1..10000){val t=i*.001;val b=band(speedAt(t));if(b!=previous){out+=Crossing(t,previous,b,crest(speedAt(t)));previous=b}}
        out
    }
    private fun roll(t:Double,a:Double,b:Double,from:Double,to:Double):Double {
        if(t<b){val k=seg(t,a,b);return from+(to-from)*(.88*k*k*(3-2*k)+.12*k*k*k)}
        return spring(t-b,to,to,.36*(to-from)/(b-a),24.0,420.0)
    }
    private fun press(t:Double,a:Double,land:Double):Double {
        if(t<a)return 0.0;if(t<land)return easeOut(seg(t,a,a+.12))
        return max(spring(t-land,1.0,0.0,0.0,19.0,195.0),-.08)
    }
    private fun flash(age:Double)=if(age<0)0.0 else if(age<.03)age/.03 else exp(-(age-.03)/.09)
    fun frame(seconds:Double):GearPuckSpeedLineFrame {
        val t=((seconds%10)+10)%10;val f=GearPuckSpeedLineFrame()
        f.time=t;f.speed=speedAt(t);f.rate=(speedAt(t+.01)-speedAt(max(t-.01,0.0)))/.02
        f.lean=((f.speed-speedAt(max(t-.25,0.0)))/.25/70).coerceIn(-1.0,1.0)
        f.g=when{t>=.52&&t<4.25->roll(t,.52,1.36,0.0,2.0);t>=4.25&&t<8.15->roll(t,4.25,4.68,2.0,3.0);t>=8.15->roll(t,8.15,8.85,3.0,0.0);else->0.0}
        f.press=if(t<4.17)press(t,.4,1.36) else if(t<8.05)press(t,4.17,4.68) else press(t,8.05,8.85)
        f.flash=maxOf(flash(t-1.36),flash(t-4.68),.7*flash(t-8.85))
        val infl=when{t>=1.5&&t<4.27->spring(t-1.5,0.0,1.0,0.0,19.0,195.0);t>=4.27&&t<4.52->1-seg(t,4.27,4.52).pow(3);t>=4.7&&t<6.85->spring(t-4.7,0.0,1.0,0.0,19.0,195.0);t>=6.85&&t<8.15->smooth(0.0,30.0,f.speed);else->0.0}
        f.amplitude=infl*(96+.12*abs(f.speed));f.crest=crest(f.speed)
        for(p in arrayOf(doubleArrayOf(1.36,201.0,crest(0.0)),doubleArrayOf(4.68,267.0,crest(132.0)))){
            val duration=max(abs(p[2]-p[1])/900,.06);val k=(t-p[0])/duration
            if(k>=0&&k<1.25){f.packetX=p[1]+(p[2]-p[1])*min(k,1.0);f.packetGain=if(k<1)1.0 else 1-(k-1)/.25}
        }
        val home=6.85+(8.15-6.85)*.6
        f.numK=if(t>=1.6&&t<home)spring(t-1.6,0.0,1.0,0.0,22.1,157.9) else if(t>=home)spring(t-home,1.0,0.0,0.0,22.1,157.9) else 0.0
        f.drop=if(t>=4.25&&t<4.74)easeOut(seg(t,4.25,4.4)) else if(t>=4.74&&t<6.85)max(spring(t-4.74,1.0,0.0,0.0,22.1,157.9),0.0) else 0.0
        val hold=smooth(6.38,6.46,t)*(1-smooth(6.77,6.85,t));f.jitter=hold*sin(2*PI*24*t)
        val phase=t*200;val i=min(phase.toInt(),1999);f.phase=mix(phases[i],phases[i+1],phase-i)
        f.curtain=f.speed*(.55+.45*min(infl,1.0));f.whip=10*smooth(180.0,190.0,f.speed);f.sway=3*sin(2*PI*4*t/10)
        var last=-1;crossings.forEachIndexed{i,c->if(c.t<=t)last=i}
        fun crossing(i:Int):Crossing{val c=crossings[(i%crossings.size+crossings.size)%crossings.size];return if(i<0)c.copy(t=c.t-10) else c}
        val l=crossing(last);val p=crossing(last-1)
        f.color0=p.from.toDouble();f.color1=p.to.toDouble();f.color2=l.to.toDouble();f.frontPX=p.x;f.frontPR=700*(t-p.t);f.frontLX=l.x;f.frontLR=700*(t-l.t)
        return f
    }
}

internal class GearPuckSpeedLineFrame(val values: DoubleArray=DoubleArray(28)) {
    var g: Double
        get() = values[0]
        set(value) { values[0] = value }
    var press: Double
        get() = values[1]
        set(value) { values[1] = value }
    var flash: Double
        get() = values[2]
        set(value) { values[2] = value }
    var speed: Double
        get() = values[3]
        set(value) { values[3] = value }
    var rate: Double
        get() = values[4]
        set(value) { values[4] = value }
    var amplitude: Double
        get() = values[5]
        set(value) { values[5] = value }
    var crest: Double
        get() = values[6]
        set(value) { values[6] = value }
    var lean: Double
        get() = values[7]
        set(value) { values[7] = value }
    var jitter: Double
        get() = values[8]
        set(value) { values[8] = value }
    var phase: Double
        get() = values[9]
        set(value) { values[9] = value }
    var curtain: Double
        get() = values[10]
        set(value) { values[10] = value }
    var whip: Double
        get() = values[11]
        set(value) { values[11] = value }
    var sway: Double
        get() = values[12]
        set(value) { values[12] = value }
    var color0: Double
        get() = values[13]
        set(value) { values[13] = value }
    var color1: Double
        get() = values[14]
        set(value) { values[14] = value }
    var color2: Double
        get() = values[15]
        set(value) { values[15] = value }
    var frontPX: Double
        get() = values[16]
        set(value) { values[16] = value }
    var frontPR: Double
        get() = values[17]
        set(value) { values[17] = value }
    var frontLX: Double
        get() = values[18]
        set(value) { values[18] = value }
    var frontLR: Double
        get() = values[19]
        set(value) { values[19] = value }
    var packetX: Double
        get() = values[20]
        set(value) { values[20] = value }
    var packetGain: Double
        get() = values[21]
        set(value) { values[21] = value }
    var pluckX: Double
        get() = values[22]
        set(value) { values[22] = value }
    var pluck: Double
        get() = values[23]
        set(value) { values[23] = value }
    var pluckLag: Double
        get() = values[24]
        set(value) { values[24] = value }
    var numK: Double
        get() = values[25]
        set(value) { values[25] = value }
    var drop: Double
        get() = values[26]
        set(value) { values[26] = value }
    var time: Double
        get() = values[27]
        set(value) { values[27] = value }
    fun copy() = GearPuckSpeedLineFrame(values.copyOf())
    fun colorAt(x:Double): FloatArray {
        val a=BANDS[color0.roundToInt().coerceIn(0,3)];val b=BANDS[color1.roundToInt().coerceIn(0,3)];val c=BANDS[color2.roundToInt().coerceIn(0,3)]
        val p=(1-smooth(frontPR-8,frontPR+8,abs(x-frontPX))).toFloat()
        val q=(1-smooth(frontLR-8,frontLR+8,abs(x-frontLX))).toFloat()
        return FloatArray(3) {i -> val v=a[i]+(b[i]-a[i])*p;v+(c[i]-v)*q}
    }
    fun curve(x:Double): Double {
        val dx=x-crest;val sigma=68*(if((dx<0)==(lean>0))1+.3*abs(lean) else 1.0)
        return 330-(amplitude+jitter)*exp(-dx*dx/(2*sigma*sigma))+pluck*exp(-(x-pluckX).pow(2)/5000)
    }
    val drums:DoubleArray get() {
        val a=max(abs(speed),0.0);val fl=floor(a);val u=fl+smooth(.3,.7,a-fl)
        fun wrap(p:Double):Double {val m=p-10*floor(p/10);return floor(p/10)+(if(m>9)m-9 else 0.0)}
        val t=wrap(u);return doubleArrayOf(wrap(t),t,u)
    }
    val readout: Readout get() {
        val d=drums;val cells=1+d[1].coerceIn(0.0,1.0)+d[0].coerceIn(0.0,1.0);val w=cells*47.12
        val hx=crest.coerceIn(14+w/2,388-w/2);val parked=34.0/76
        val cx=mix(mix(22+w*parked/2,hx,numK),hx,drop)
        val base=mix(mix(368.0,326.0,numK),372.0,drop)
        val scale=mix(mix(parked,1.0,numK),.6,drop)
        return Readout(cx,base,scale,w)
    }
    data class Readout(val cx:Double,val base:Double,val scale:Double,val width:Double)
    companion object {
        val BANDS = arrayOf(floatArrayOf(.57647f,.90196f,.24706f),floatArrayOf(.18431f,.60784f,1f),floatArrayOf(1f,.64706f,.12157f),floatArrayOf(1f,.16471f,.23529f))
    }
}

internal fun mix(a:Double,b:Double,k:Double)=a+(b-a)*k
internal fun smooth(a:Double,b:Double,x:Double):Double {val k=((x-a)/(b-a)).coerceIn(0.0,1.0);return k*k*(3-2*k)}
private fun spring(t:Double,from:Double,to:Double,v:Double,d:Double,k:Double):Double {
    if(t<=0)return from
    val w=sqrt(k);val z=d/(2*w);val x=from-to
    if(z<1){val wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t))}
    return to+exp(-w*t)*(x+(v+w*x)*t)
}

internal class GearPuckSpeedLineEngine(private val score: GearPuckSpeedLineScore) {
    var automatic=true
    var shown=score.frame(0.0)
    var target=0
    var held=false
    var mode=0
    private var origin=0.0;private var prior=0.0;private var lastTouch=0.0
    private var gearV=0.0;private var infl=0.0;private var inflV=0.0;private var numV=0.0;private var dropV=0.0;private var speedV=0.0
    private var phaseOffset=0.0
    private var gearStart=0.0;private var gapTime=-99.0;private var flashTime=-99.0;private var pluckTime=-99.0;private var pluckFrom=0.0
    private fun step(x:Double,v:Double,to:Double,dt:Double,d:Double=24.0,k:Double=420.0):Pair<Double,Double> {
        var p=x;var velocity=v;val h=dt/4
        repeat(4){velocity+=(-k*(p-to)-d*velocity)*h;p+=velocity*h};return p to velocity
    }
    fun frame(now:Double,autoPlay:Boolean,reduced:Boolean,pin:Double?,manualPin:Boolean):GearPuckSpeedLineFrame {
        if(origin==0.0){origin=now;prior=now;automatic=autoPlay&&!reduced&&!manualPin}
        val dt=(now-prior).coerceIn(0.0,.05);prior=now
        if(automatic){shown=score.frame(pin?:now-origin);shown.phase+=phaseOffset;target=shown.g.roundToInt().coerceIn(0,4);return shown}
        val oldSpeed=shown.speed
        if(!held||mode!=1){val r=step(shown.g,gearV,target.toDouble(),dt);shown.g=r.first;gearV=r.second}
        if(reduced&&(!held||mode!=1)){shown.g=target.toDouble();gearV=0.0}
        val cap=doubleArrayOf(0.0,0.0,132.0,197.0,197.0)[target]
        if(!held||mode!=2){shown.speed=max(0.0,min(cap,shown.speed+speedV*dt-(if(target<2)60 else 12)*dt));speedV*=exp(-dt*2)}
        shown.rate=if(dt>0)(shown.speed-oldSpeed)/dt else 0.0
        shown.lean+=(shown.rate/70-shown.lean)*(1-exp(-dt/.15));shown.lean=shown.lean.coerceIn(-1.0,1.0)
        shown.press+=((if(held&&mode==1)1.0 else 0.0)-shown.press)*(1-exp(-dt*16))
        val gap=now-gapTime;val inflTarget=(if(target>=2)1.0 else smooth(0.0,30.0,shown.speed))*(if(gap<.25)0 else 1)
        var r=step(infl,inflV,if(reduced)0.0 else inflTarget,dt,19.0,195.0);infl=r.first;inflV=r.second
        shown.amplitude=max(0.0,infl)*(96+.12*shown.speed);shown.crest=56+270*min(shown.speed,200.0)/200;shown.jitter=0.0
        r=step(shown.numK,numV,if(reduced)0.0 else if(inflTarget>.5||shown.speed>50)1.0 else 0.0,dt,22.1,157.9);shown.numK=r.first;numV=r.second
        r=step(shown.drop,dropV,if(!reduced&&gap<.42&&shown.numK>.5)1.0 else 0.0,dt,22.1,157.9);shown.drop=r.first.coerceIn(0.0,1.0);dropV=r.second
        if(!reduced)shown.phase+=(6+1.2*shown.speed)*dt
        shown.curtain=shown.speed*(.55+.45*min(infl,1.0));shown.whip=10*smooth(180.0,190.0,shown.speed)
        shown.time=now-origin;shown.sway=if(reduced)0.0 else 3*sin(2*PI*4*shown.time/10)
        shown.frontPR+=700*dt;shown.frontLR+=700*dt
        val band=if(shown.speed<25)0 else if(shown.speed<120)1 else if(shown.speed<165)2 else 3
        if(shown.color2.toInt()!=band){shown.color0=shown.color1;shown.color1=shown.color2;shown.color2=band.toDouble();shown.frontPX=shown.frontLX;shown.frontPR=shown.frontLR;shown.frontLX=shown.crest;shown.frontLR=if(reduced)1000.0 else 0.0}
        if(!held||mode!=3)shown.pluck=if(reduced)0.0 else spring(now-pluckTime,pluckFrom,0.0,0.0,11.5,1020.0)
        shown.pluckLag=shown.pluck*.7;shown.packetGain=0.0;shown.flash=max(0.0,1-(now-flashTime)/.24)
        if(autoPlay&&!reduced&&!manualPin&&pin==null&&!held&&now-lastTouch>2){target=0;if(shown.speed<.3&&shown.amplitude<.1&&abs(shown.g)<.01&&abs(shown.numK)<.01){automatic=true;origin=now;phaseOffset=shown.phase;shown=score.frame(0.0);shown.phase=phaseOffset}}
        return shown
    }
    fun begin(x:Double,y:Double,now:Double,reduced:Boolean) {
        if(automatic){infl=shown.amplitude/(96+.12*shown.speed);automatic=false}
        held=true;lastTouch=now;gearStart=shown.g;val bx=69+66*shown.g
        mode=when{(x-bx).pow(2)+(y-664).pow(2)<56*56->1;y>612&&y<716->4;target>=2&&abs(x-shown.crest)<110&&y>shown.curve(shown.crest)-40&&y<400->2;abs(y-330)<90&&!reduced->3;else->0}
        if(mode==2)speedV=0.0
    }
    fun move(x:Double,y:Double,dx:Double,dy:Double) {
        when(mode){
            1->{var r=gearStart+dx/66;if(r<0)r/=1-r*3;if(r>4)r=4+(r-4)/(1+(r-4)*3);val well=r.coerceIn(0.0,4.0).roundToInt();shown.g=r+.22*(well-r)*(1-smooth(.18,.5,abs(r-well)))}
            2->shown.speed=((x-56)/270*200).coerceIn(0.0,doubleArrayOf(0.0,0.0,132.0,197.0,197.0)[target])
            3->{shown.pluckX=x;shown.pluck=(if(dy<0)-1 else 1)*40*abs(dy)/(abs(dy)+40)}
        }
    }
    fun end(x:Double,dx:Double,dy:Double,velocity:Double,now:Double,cancelled:Boolean) {
        var to=target
        if(mode==1){to=(shown.g+(if(cancelled)0.0 else velocity/66*.12)).roundToInt().coerceIn(0,4);gearV=if(cancelled)0.0 else velocity/66}
        if(!cancelled&&hypot(dx,dy)<4&&(mode==1||mode==4)){to=((x-69)/66).roundToInt().coerceIn(0,4);if(mode==1&&(target==2||target==3))to=if(target==2)3 else 2}
        select(to,now)
        if(cancelled)flashTime=-99.0
        if(mode==2)speedV=if(cancelled)0.0 else velocity*200*.69/270
        if(mode==3){pluckTime=now;pluckFrom=shown.pluck}
        held=false;mode=0;lastTouch=now
    }
    fun select(gear:Int,now:Double){
        if(automatic){infl=shown.amplitude/(96+.12*shown.speed);automatic=false}
        if((target==2&&gear>=3)||(target>=3&&gear==2))gapTime=now
        target=gear.coerceIn(0,4);flashTime=now;lastTouch=now
    }
}
