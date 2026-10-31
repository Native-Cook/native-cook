package com.ngocdevv.nativecook.blocks.gestures.jellyscaleexchange

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*

internal class JellyScaleExchangeDriver {
    var revision by mutableIntStateOf(0); private set
    var frame=JellyScaleExchangeMotion.Frame(); private set
    var buzz: (Boolean)->Unit={}
    private var clock=0.0
    private var autoStart=0.0
    private var automatic=false
    private var lastTouch=-100.0
    private var pouring=false
    private var swapping=false
    private var exchangeAt: Double?=null
    private var exchangeFrom=frame
    private var landedAt: Double?=null
    private var takeoverAt=-100.0
    private var takeoverFrom=frame
    private var pressPourAt=0.0
    private var pressSwapAt=0.0
    private var pourPressFrom=0.0
    private var swapPressFrom=0.0
    private val ease=CubicBezierEasing(0.77f,0f,0.175f,1f)
    private val easeOut=CubicBezierEasing(0.23f,1f,0.32f,1f)
    private fun defaultEase(u: Double) = if(u<0.5) 2*u*u else 1-(-2*u+2).pow(2)/2
    private fun tween(t: Double,a: Double,b: Double,d: Double,eased: Boolean=true): Double {
        val u=JellyScaleExchangeMotion.clamp01(t/d)
        return a+(b-a)*(if(eased) ease.transform(u.toFloat()).toDouble() else u)
    }
    fun reset(auto: Boolean,reduced: Boolean) {
        automatic=auto&&!reduced; autoStart=clock; revision++
    }
    fun needsFrames(auto: Boolean,reduced: Boolean) = (auto&&!reduced)||pouring||exchangeAt!=null||
        frame.wobbleU<1||frame.plus<1||clock-takeoverAt<0.15||clock-pressPourAt<0.15||clock-pressSwapAt<0.15
    fun takeOver(reduced: Boolean) {
        lastTouch=clock
        if(automatic) {
            automatic=false; takeoverFrom=frame; takeoverAt=clock
            pourPressFrom=frame.pressPour; swapPressFrom=frame.pressSwap; pressPourAt=clock; pressSwapAt=clock
            if(frame.melt>0.001&&frame.fly<0.999) exchange(reduced)
        }
        revision++
    }
    fun pressPour(down: Boolean,reduced: Boolean) {
        takeOver(reduced)
        if(down&&exchangeAt!=null) return
        pourPressFrom=frame.pressPour; pressPourAt=clock; pouring=down; revision++
    }
    fun pressSwap(down: Boolean,reduced: Boolean,activate: Boolean=false) {
        takeOver(reduced); swapPressFrom=frame.pressSwap; pressSwapAt=clock; swapping=down
        if(activate) exchange(reduced)
        revision++
    }
    fun accessibilityPour(reduced: Boolean) {
        takeOver(reduced)
        if(exchangeAt==null&&frame.pour<16) { frame=frame.copy(pour=min(16.0,ceil(frame.pour)+1)); buzz(false); revision++ }
    }
    fun exchange(reduced: Boolean) {
        if(exchangeAt!=null||frame.pour<0.5) return
        exchangeFrom=frame; exchangeAt=clock; pouring=false; landedAt=null; revision++
    }
    fun tick(dt: Double,auto: Boolean,reduced: Boolean) {
        clock+=dt
        if(automatic) { frame=JellyScaleExchangeMotion.frameAt((clock-autoStart)%JellyScaleExchangeMotion.LOOP); revision++; return }
        val pU=if(reduced) 1.0 else JellyScaleExchangeMotion.clamp01((clock-pressPourAt)/(if(pouring) 0.1 else 0.15))
        val sU=if(reduced) 1.0 else JellyScaleExchangeMotion.clamp01((clock-pressSwapAt)/(if(swapping) 0.1 else 0.15))
        frame=frame.copy(pressPour=pourPressFrom+((if(pouring) 1.0 else 0.0)-pourPressFrom)*easeOut.transform(pU.toFloat()),
            pressSwap=swapPressFrom+((if(swapping) 1.0 else 0.0)-swapPressFrom)*easeOut.transform(sU.toFloat()),
            finger=takeoverFrom.finger*(1-defaultEase(JellyScaleExchangeMotion.seg(clock,takeoverAt,takeoverAt+0.15))))
        if(pouring&&exchangeAt==null&&frame.pour<16) {
            val previous=floor(frame.pour)
            frame=frame.copy(pour=min(16.0,frame.pour+(if(reduced) 12 else 6)*dt))
            if(floor(frame.pour)!=previous) buzz(false)
        }
        exchangeAt?.let { start ->
            val t=clock-start; val m=if(reduced) 0.3 else 1.1; val c=if(reduced) 0.3 else 0.8; val f=if(reduced) 0.3 else 0.7
            frame=frame.copy(pour=exchangeFrom.pour+(ceil(exchangeFrom.pour)-exchangeFrom.pour)*defaultEase(if(reduced) 1.0 else JellyScaleExchangeMotion.clamp01(t/0.2)),
                melt=tween(t,exchangeFrom.melt,1.0,m),coin=tween(max(0.0,t-m),exchangeFrom.coin,1.0,c),
                fly=tween(max(0.0,t-m-c),exchangeFrom.fly,1.0,f),
                spin=if(reduced) exchangeFrom.spin else tween(max(0.0,t-m-c),exchangeFrom.spin,2*PI,0.9))
            if(t>=m+c+f) {
                frame=frame.copy(pour=0.0,melt=0.0,coin=0.0,spin=0.0,fly=0.0,wobbleU=if(reduced) 1.0 else 0.0,plus=0.0)
                exchangeAt=null; landedAt=clock; buzz(true)
            }
        }
        landedAt?.let { at -> frame=frame.copy(wobbleU=if(reduced) 1.0 else JellyScaleExchangeMotion.seg(clock,at,at+0.7),plus=JellyScaleExchangeMotion.seg(clock,at,at+0.9)) }
        if(pouring||exchangeAt!=null||frame.wobbleU<1||frame.plus<1) lastTouch=clock
        if(auto&&!reduced&&clock-lastTouch>=2) {
            if(frame.pour>0.001) exchange(reduced) else { autoStart=clock; automatic=true }
        }
        revision++
    }
}
