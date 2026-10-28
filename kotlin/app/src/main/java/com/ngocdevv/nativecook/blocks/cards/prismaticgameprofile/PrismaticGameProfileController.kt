package com.ngocdevv.nativecook.blocks.cards.prismaticgameprofile

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

/** One interruption owner. Draw/layer reads observe its channels, not screen recomposition. */
internal class PrismaticGameProfileController(private val scope: CoroutineScope, autoPlay: Boolean, private val previewTime: Double?, initialReduced: Boolean, private val feedback: () -> Unit) {
    enum class Surface { None, Player, Statistics, Settings, Friend, Game, Home, Store }
    var surface by mutableStateOf(Surface.None)
        private set
    var reduced by mutableStateOf(initialReduced)
        private set
    var foreground by mutableStateOf(true)
        private set
    var selected by mutableIntStateOf(1)
        private set
    var demoPlayer by mutableStateOf(false)
        private set
    var contentReady by mutableStateOf(false)
        private set
    var source by mutableStateOf(PrismaticGameProfileMotion.source(1f))
    var identity by mutableStateOf(PrismaticGameProfileMotion.Identity())
    var origin by mutableStateOf(PrismaticGameProfileMotion.badgeOrigin(1f,1,source))
        private set
    private var demoEnabled by mutableStateOf(autoPlay || previewTime!=null)
    private var demoSeconds by mutableDoubleStateOf(previewTime?:0.0)
    private var rail by mutableFloatStateOf(1f)
    private var progress by mutableFloatStateOf(0f)
    private var content by mutableFloatStateOf(0f)
    private var tiltX by mutableFloatStateOf(0f)
    private var tiltY by mutableFloatStateOf(0f)
    private var catchStrength by mutableFloatStateOf(0f)
    private var material by mutableIntStateOf(-1)
    private var materialPhase by mutableFloatStateOf(0f)
    private var materialPower by mutableFloatStateOf(0f)
    private var playerIndex=1
    private var detailTarget=0f
    private var gestureReturn=0f
    private var grabProgress=0f
    private var grabRail=1f
    private var grabTiltX=0f
    private var grabTiltY=0f
    private var gesture=false
    private var catchEligible=false
    private var generation=0
    private var detailJob: Job?=null
    private var railJob: Job?=null
    private var contentJob: Job?=null
    private var catchJob: Job?=null
    private var tiltJob: Job?=null
    private val contentEase=CubicBezierEasing(.23f,1f,.32f,1f)
    fun frame(): PrismaticGameProfileMotion.Frame {
        if(demoEnabled) return PrismaticGameProfileMotion.demo(previewTime?:demoSeconds,reduced)
        return PrismaticGameProfileMotion.Frame(rail,0.0,if(reduced)-1 else material,materialPhase,if(reduced)0f else materialPower,if(surface==Surface.Player)progress else 0f,content,if(reduced)0f else catchStrength,if(reduced)0f else tiltX,if(reduced)0f else tiltY,reduced,playerIndex)
    }
    fun cardOrigin(): PrismaticGameProfileMotion.Bounds = if(demoEnabled)PrismaticGameProfileMotion.badgeOrigin(1f,1,source) else origin
    fun statsProgress(): Float = if(surface==Surface.Statistics)progress else 0f
    suspend fun runDemo() {
        snapshotFlow { demoEnabled && foreground && !reduced && previewTime==null }.collectLatest { running ->
            if(running) {
                val carried=demoSeconds;val epoch=withFrameNanos { it }
                while(scope.isActive) {
                    withFrameNanos { now ->
                        demoSeconds=(carried+(now-epoch)/1_000_000_000.0)%PrismaticGameProfileMotion.LOOP
                        val f=frame();val index=f.index
                        if(selected!=index)selected=index
                        val showing=f.progress>0
                        if(demoPlayer!=showing)demoPlayer=showing
                        val ready=f.content>=.999f
                        if(contentReady!=ready)contentReady=ready
                    }
                }
            }
        }
    }
    fun syncPreview() {
        if(previewTime!=null){val f=frame();selected=f.index;demoPlayer=f.progress>0;contentReady=f.content>=.999f}
    }
    private fun cancelAnimations(includeCatch: Boolean=true) {
        generation++;detailJob?.cancel();railJob?.cancel();contentJob?.cancel();tiltJob?.cancel()
        if(includeCatch){catchJob?.cancel();catchStrength=0f}
    }
    private fun interrupt(preserveCatch: Boolean=false) {
        val f=frame();val wasDemo=demoEnabled
        cancelAnimations(!preserveCatch)
        rail=f.rail;progress=f.progress;content=f.content;tiltX=f.tiltX;tiltY=f.tiltY
        if(wasDemo) {
            playerIndex=f.index;origin=PrismaticGameProfileMotion.badgeOrigin(f.rail,f.index,source)
            if(f.progress>0){surface=Surface.Player;detailTarget=if(f.progress>=.5f)1f else 0f}
            if(preserveCatch&&f.catchStrength>0){
                catchStrength=f.catchStrength;catchEligible=false
                catchJob=scope.launch { animate(f.catchStrength,0f,animationSpec=tween(max(1,((6.15-f.time)*1000).roundToInt()),easing=contentEase)){value,_->catchStrength=value} }
            }
        }
        demoEnabled=false;demoPlayer=false;material=-1;materialPower=0f
    }
    private fun animateContent(target: Float,duration: Int) {
        contentJob?.cancel();val token=generation
        contentJob=scope.launch {
            animate(content,target,animationSpec=tween(duration, easing=contentEase)){value,_->if(token==generation)content=value}
            if(token==generation)contentReady=target>=.999f
        }
    }
    private fun pulseCatch() {
        if(!catchEligible||reduced||!foreground||detailTarget!=1f)return
        catchEligible=false;catchJob?.cancel()
        catchJob=scope.launch {
            animate(catchStrength,1f,animationSpec=tween(90,easing=contentEase)){value,_->catchStrength=value}
            animate(1f,0f,animationSpec=tween(260,easing=contentEase)){value,_->catchStrength=value}
        }
    }
    private fun settle(target: Float,velocity: Float=0f) {
        detailJob?.cancel();detailTarget=target;gesture=false;val token=generation
        if(target==0f){catchEligible=false;catchJob?.cancel();catchStrength=0f}
        detailJob=scope.launch {
            if(target==0f){contentJob?.cancel();contentReady=false;animate(content,0f,animationSpec=tween(if(reduced)70 else 70,easing=contentEase)){value,_->if(token==generation)content=value}}
            if(reduced)progress=target else animate(progress,target,velocity, spring(dampingRatio=.8f,stiffness=700f,visibilityThreshold=.0005f)){value,_->if(token==generation)progress=value.coerceIn(0f,1f)}
            if(token!=generation||!foreground)return@launch
            progress=target
            if(target==1f){pulseCatch();animateContent(1f,90)} else {surface=Surface.None;content=0f;contentReady=false}
        }
    }
    fun open(next: Surface) {
        interrupt();gesture=false;surface=next
        if(next==Surface.Player||next==Surface.Statistics){
            playerIndex=selected;origin=PrismaticGameProfileMotion.badgeOrigin(rail,selected,source)
            progress=0f;content=0f;contentReady=false;catchEligible=next==Surface.Player&&!reduced;tiltX=0f;tiltY=0f
            settle(1f)
        }
    }
    fun close() {
        if(demoEnabled)interrupt(true)
        if(surface==Surface.Player||surface==Surface.Statistics) {cancelAnimations();settle(0f)} else {cancelAnimations();surface=Surface.None;contentReady=false}
    }
    fun select(index: Int) {
        interrupt();surface=Surface.None;catchEligible=false;gesture=false;settleRail(index.coerceIn(0,2),0f,true)
    }
    fun beginRail() {interrupt();gesture=true;grabRail=rail;surface=Surface.None}
    fun moveRail(dx: Float) {
        if(reduced)return
        rail=(grabRail-dx/146).coerceIn(0f,2f);selected=rail.roundToInt().coerceIn(0,2)
        val offset=rail-selected;val engage=PrismaticGameProfileMotion.smooth(abs(rail-grabRail)/.12f)
        material=selected;materialPhase=(.5f+offset*.72f+((rail-grabRail)*.15f).coerceIn(-.15f,.15f)).coerceIn(.12f,.88f)
        materialPower=.34f*engage*(1-PrismaticGameProfileMotion.smooth(abs(offset)/.5f))
    }
    fun releaseRail(vx: Float,cancelled: Boolean) {
        gesture=false;val velocity=-vx/146
        val target=if(cancelled)grabRail.roundToInt()else(rail+velocity*.12f).roundToInt()
        settleRail(target.coerceIn(0,2),if(cancelled)0f else velocity,!cancelled)
    }
    private fun settleRail(index: Int,velocity: Float,user: Boolean) {
        railJob?.cancel();val token=generation
        railJob=scope.launch {
            if(reduced)rail=index.toFloat() else animate(rail,index.toFloat(),velocity,spring(dampingRatio=.8f,stiffness=480f,visibilityThreshold=.001f)){value,_->if(token==generation){rail=value.coerceIn(0f,2f);selected=rail.roundToInt().coerceIn(0,2)}}
            if(token!=generation)return@launch
            rail=index.toFloat();selected=index
            if(user&&!reduced){feedback();material=index;animate(0f,1f,animationSpec=tween(if(index==0)450 else if(index==1)420 else 500,easing=androidx.compose.animation.core.LinearEasing)){value,_->if(token==generation){materialPhase=value;materialPower=PrismaticGameProfileMotion.smooth(value/.18f)*(1-PrismaticGameProfileMotion.smooth((value-.57f)/.43f))}}}
            if(token==generation){material=-1;materialPower=0f}
        }
    }
    fun beginPull(fromRail: Boolean) {
        val f=frame();val wasDemo=demoEnabled;val previousTarget=if(demoEnabled)if(f.progress>=.5f)1f else 0f else detailTarget
        val preserve=!fromRail&&((surface==Surface.Player&&!demoEnabled)||(wasDemo&&f.catchStrength>0))
        interrupt(preserve);gesture=true;gestureReturn=if(fromRail)0f else previousTarget
        grabProgress=if(fromRail)0f else f.progress
        if(fromRail){playerIndex=selected;origin=PrismaticGameProfileMotion.badgeOrigin(rail,selected,source);catchEligible=!reduced;tiltX=0f;tiltY=0f}
        if(wasDemo&&!fromRail)catchEligible=f.progress<.999f&&f.time<5.8&&!reduced
        progress=grabProgress;surface=Surface.Player
        if(progress<.999f){content=0f;contentReady=false}
    }
    fun movePull(dy: Float) {
        if(reduced)return
        val previous=progress;progress=(grabProgress-dy/120).coerceIn(0f,1f)
        if(progress<.999f){contentJob?.cancel();content=0f;contentReady=false}else if(previous<.999f)animateContent(1f,90)
    }
    fun releasePull(vy: Float,cancelled: Boolean) {
        val velocity=-vy/120;val target=if(cancelled)gestureReturn else if(progress+velocity*.18f>.5f)1f else 0f
        if(!cancelled&&target==1f&&gestureReturn==0f&&!reduced)feedback()
        settle(target,if(cancelled)0f else velocity)
    }
    fun beginTilt() {interrupt(true);surface=Surface.Player;gesture=true;grabTiltX=tiltX;grabTiltY=tiltY;detailTarget=1f}
    fun moveTilt(dx: Float,dy: Float) {tiltX=(grabTiltX-dy/90*6).coerceIn(-6f,6f);tiltY=(grabTiltY+dx/90*6).coerceIn(-6f,6f)}
    fun releaseTilt(cancelled: Boolean) {
        gesture=false;val x=if(cancelled)grabTiltX else 0f;val y=if(cancelled)grabTiltY else 0f;val fromX=tiltX;val fromY=tiltY;val token=generation
        tiltJob=scope.launch {animate(0f,1f,animationSpec=spring(dampingRatio=.8f,stiffness=480f)){v,_->if(token==generation){tiltX=fromX+(x-fromX)*v;tiltY=fromY+(y-fromY)*v}}}
    }
    fun updateReduced(value: Boolean) {
        if(reduced==value)return
        cancelAnimations();catchEligible=false;reduced=value;gesture=false;tiltX=0f;tiltY=0f;material=-1;materialPower=0f
        rail=rail.roundToInt().coerceIn(0,2).toFloat()
        if(surface==Surface.Player||surface==Surface.Statistics)settle(detailTarget)
        if(value)demoSeconds=0.0
        if(demoEnabled){
            val shown=frame()
            selected=shown.index;demoPlayer=shown.progress>0;contentReady=shown.content>=.999f
        } else if(value){demoPlayer=false;contentReady=false}
    }
    fun updateForeground(value: Boolean) {
        if(foreground==value)return
        foreground=value
        if(!value){cancelAnimations();catchEligible=false;material=-1;materialPower=0f;if(gesture){progress=gestureReturn;content=if(progress>=1)1f else 0f;contentReady=progress>=1;gesture=false;if(progress<=0)surface=Surface.None}}
        else if(surface==Surface.Player||surface==Surface.Statistics)settle(detailTarget)
    }
    fun carouselAccessible() = surface==Surface.None&&!demoPlayer
    fun dispose() {cancelAnimations();foreground=false}
}
