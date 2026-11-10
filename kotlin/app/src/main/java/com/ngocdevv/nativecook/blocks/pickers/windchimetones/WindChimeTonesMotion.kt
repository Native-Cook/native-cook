package com.ngocdevv.nativecook.blocks.pickers.windchimetones

import kotlin.math.*

internal object WindChimeTonesMotion {
    const val LOOP=10.0
    const val PIECE=8.0
    const val SPEED=420.0
    const val PLAY=.7
    const val START=.8
    const val PAUSE=8.6
    val notes=arrayOf(
        doubleArrayOf(.180,.58,587.0),doubleArrayOf(.730,.36,880.0),doubleArrayOf(1.090,.43,740.0),
        doubleArrayOf(1.820,.65,659.0),doubleArrayOf(2.160,.32,988.0),doubleArrayOf(2.910,.40,1175.0),
        doubleArrayOf(3.370,.53,880.0),doubleArrayOf(3.660,.30,740.0),doubleArrayOf(4.420,.72,587.0),
        doubleArrayOf(5.070,.39,988.0),doubleArrayOf(5.480,.46,659.0),doubleArrayOf(6.230,.61,880.0),
        doubleArrayOf(6.890,.34,1175.0),doubleArrayOf(7.570,.49,740.0))
    fun seg(t: Double,a: Double,b: Double)=((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun easeOut(x: Double)=1-(1-x).pow(3)
    fun timing(x: Double)=if(x<.5) 2*x*x else 1-(-2*x+2).pow(2)/2
    data class Body(val d: Double,val ux: Double,val uy: Double,val half: Double,val mass: Double)
    data class Pose(val x: Double=0.0,val y: Double=0.0,val rotation: Double=0.0,val scaleX: Double=1.0,val scaleY: Double=1.0)
    data class Frame(var playing: Boolean=false,var music: Double=-1.0,var cut: Double=PIECE,
        var laps: Int=1,var finger: Double=0.0,var press: Double=0.0,var power: Double=1.0,
        var glow: Double=0.0,var time: Double=0.0) {
        val kick get()=if(music>=0) pushAt(music,cut,laps) else 0.0
        val progress get()=if(music<0) .52 else min(1.0,.52+((music%PIECE+PIECE)%PIECE)/PIECE*.3)
        fun ride(body: Body,reduced: Boolean): Pose {
            if(reduced || music<0 || power<.001) return Pose()
            val k=kickAt(music,body.d,cut,laps,body.mass)
            val near=kickAt(music,body.d-body.half*abs(body.ux),cut,laps,body.mass).x
            val far=kickAt(music,body.d+body.half*abs(body.ux),cut,laps,body.mass).x
            val rock=body.uy*(far-near)*sign(body.ux)/(2*body.half)
            val stretch=1+(k.v/3000*power).coerceIn(-.035,.035)
            return Pose(k.x*body.ux*power,k.x*body.uy*power,rock*power,1/sqrt(stretch),stretch)
        }
    }
    fun frameAt(t: Double): Frame {
        fun tap(at: Double)=easeOut(seg(t,at-.18,at-.03))*(1-easeOut(seg(t,at+.08,at+.24)))
        val finger=max(tap(PLAY),tap(PAUSE))
        return Frame(t>=PLAY && t<PAUSE,if(t>=START) t-START else -1.0,PAUSE-START,
            finger=finger,press=finger,time=t)
    }
    data class Kick(val x: Double,val v: Double)
    fun kickAt(t: Double,dist: Double,cut: Double,laps: Int,mass: Double=1.0): Kick {
        val m=sqrt(mass); val w=17/m; val a=5/m
        var x=0.0; var v=0.0
        notes.filter { it[0]<=cut }.forEach { n -> repeat(laps) { lap ->
            val tau=t-(n[0]-lap*PIECE)-dist/SPEED
            if(tau>0 && tau<1.2*m) {
                val amp=n[1]*11/m; val e=exp(-a*tau)
                x+=amp*e*sin(w*tau)
                v+=amp*e*(w*cos(w*tau)-a*sin(w*tau))
            }
        } }
        return Kick(x,v)
    }
    fun pushAt(t: Double,cut: Double,laps: Int): Double {
        var p=0.0
        notes.filter { it[0]<=cut }.forEach { n -> repeat(laps) { lap ->
            val tau=t-(n[0]-lap*PIECE)
            if(tau>0 && tau<1.5) p+=n[1]*exp(-6*tau)*cos(28*tau)
        } }
        return p
    }
    fun ringsAt(t: Double,cut: Double,laps: Int): FloatArray {
        val out=FloatArray(16) { if(it%2==0) -1f else 0f }; var count=0
        if(t>=0) repeat(laps) { lap -> notes.reversed().filter { it[0]<=cut }.forEach { n ->
            val age=t-(n[0]-lap*PIECE)
            if(age>=0 && age<2.2 && count<16) { out[count++]=age.toFloat(); out[count++]=n[1].toFloat() }
        } }
        return out
    }
}

internal class WindChimeTonesLayout(val w: Double,val h: Double,safeTop: Double,safeBottom: Double) {
    val top=max(safeTop,44.0)
    private val bottom=max(safeBottom,20.0)
    val sx=w/402
    val k=(h-top-bottom)/(874-62-34)
    fun y(y: Double)=if(y<62) y*top/62 else top+(y-62)*k
    val cx=200*sx; val cy=y(290.0); val radius=101*sx
    fun distance(x: Double,y: Double)=hypot(x-cx,y-cy)
    fun toolX(i: Int)=doubleArrayOf(37.0,152.0,270.0)[i]*sx
    private fun body(x: Double,y: Double,half: Double,mass: Double): WindChimeTonesMotion.Body {
        val d=max(1.0,distance(x,y))
        return WindChimeTonesMotion.Body(d,(x-cx)/d,(y-cy)/d,half,mass)
    }
    val toolBodies=List(3) { body(toolX(it)+47*sx,y(510.0)+45*k,47*sx,1.0) }
    val playerBody=body((37+327/2.0)*sx,y(625.0)+40,327*sx/2,2.4)
    val powerBody=body(317*sx,y(292.0),32.0,.6)
}

internal class WindChimeTonesDriver(auto: Boolean) {
    var frame=WindChimeTonesMotion.Frame()
    var clock=0.0; private var autoStart=0.0
    private var manual=!auto
    var lastTouch=-100.0
    var running=false
    var isOn=true
    private var pausedAt=-1.0; private var pauseClock=0.0; private var playStart=0.0
    private var powerFrom=1.0; private var powerTo=1.0; private var powerAt=-100.0
    private var fingerFrom=0.0; private var fingerAt=-100.0
    fun step(elapsed: Double,auto: Boolean): Boolean {
        val m=WindChimeTonesMotion
        clock+=elapsed
        val dt=min(.05,elapsed)
        if(!manual && auto) {
            val glow=frame.glow
            frame=m.frameAt((clock-autoStart)%m.LOOP); frame.glow=glow
        } else if(frame.playing) {
            val t=clock-playStart
            frame.music=t%m.PIECE; frame.cut=m.PIECE; frame.laps=if(t>=m.PIECE) 2 else 1
        } else if(pausedAt>=0) {
            frame.music=pausedAt%m.PIECE+clock-pauseClock; frame.cut=pausedAt%m.PIECE
            frame.laps=if(pausedAt>=m.PIECE) 2 else 1
        } else frame.music=-1.0
        if(manual) {
            frame.power=powerFrom+(powerTo-powerFrom)*m.timing(m.seg(clock,powerAt,powerAt+.4))
            frame.finger=fingerFrom*(1-m.timing(m.seg(clock,fingerAt,fingerAt+.15)))
        }
        frame.time=clock%600
        frame.glow+=(min(1.0,abs(frame.kick))-frame.glow)*min(1.0,dt*6)
        val settled=frame.music<0 || frame.music-frame.cut>2.4
        if(manual && auto && !frame.playing && clock-lastTouch>=2 && settled) {
            pausedAt=-1.0; frame.power=1.0; powerFrom=1.0; powerTo=1.0; autoStart=clock; manual=false
        }
        running=auto || frame.playing || !settled || clock<powerAt+.4 || clock<fingerAt+.15 || frame.glow>=.000001
        return running
    }
    val audibleAt: Double? get()=if(manual && frame.playing) (clock-playStart)%WindChimeTonesMotion.PIECE else null
    private fun takeOver() {
        lastTouch=clock
        if(!manual) {
            manual=true; fingerFrom=frame.finger; fingerAt=clock; frame.press=0.0
            pausedAt=if(frame.music>=0) min(frame.music,frame.cut) else -1.0
            pauseClock=clock; frame.playing=false
        }
        running=true
    }
    private fun setPlay(on: Boolean,audio: (Boolean,Double)->Unit,buzz: ()->Unit) {
        if(on) {
            val from=if(pausedAt>=0) pausedAt else 0.0
            playStart=clock-from; pausedAt=-1.0; frame.playing=true; audio(true,from%8)
        } else if(frame.playing) {
            pausedAt=clock-playStart; pauseClock=clock; frame.playing=false; audio(false,0.0)
        }
        buzz()
    }
    fun togglePlay(audio: (Boolean,Double)->Unit,buzz: ()->Unit) {
        takeOver(); if(frame.power<.5) return
        setPlay(!frame.playing,audio,buzz)
    }
    fun togglePower(audio: (Boolean,Double)->Unit,buzz: ()->Unit,tap: ()->Unit) {
        buzz(); isOn=!isOn; takeOver()
        powerFrom=frame.power; powerTo=if(isOn) 1.0 else 0.0; powerAt=clock
        if(!isOn) setPlay(false,audio,tap)
    }
}
