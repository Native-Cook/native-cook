package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max

/** Single local UI-thread clock; only native Skia drawing reads each frame snapshot. */
@Composable
internal fun TrophySavingsMilestoneHero(
    state: TrophySavingsMilestoneRibbonState, autoPlay: Boolean, previewTime: Double?,
    previewReducedMotion: Boolean, overlay: Boolean,
) {
    val reduced=rememberLiveReducedMotion() || previewReducedMotion
    val owner=LocalLifecycleOwner.current
    val view=LocalView.current
    val context=LocalContext.current
    val density=LocalDensity.current.density
    val image=remember(context) { runCatching {
        context.assets.open("trophysavingsmilestone/trophy_paper_applause_roll_right.png").use {
            BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inScaled=false })
        }
    }.getOrNull() }
    val ribbon=remember(image) { TrophySavingsMilestoneRibbon(image) }
    val lighting=remember { TrophySavingsMilestoneLighting() }
    val interaction=remember { MutableInteractionSource() }
    val feedback=remember { Animatable(1f) }
    SideEffect { state.autoPlay=autoPlay; state.previewTime=previewTime; state.reduced=reduced }
    LaunchedEffect(autoPlay,previewTime,reduced) {
        state.autoPlay=autoPlay; state.previewTime=previewTime; state.reduced=reduced; state.reset()
    }
    LaunchedEffect(overlay) { state.updateOverlay(overlay) }
    DisposableEffect(owner,state) {
        val observer=LifecycleEventObserver { _,_ ->
            state.active=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); state.reset()
        }
        owner.lifecycle.addObserver(observer)
        state.active=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { owner.lifecycle.removeObserver(observer); state.active=false; state.reset() }
    }
    LaunchedEffect(state.generation,state.ticking) {
        while(state.ticking) withFrameNanos {
            val token=state.generation
            val impact=state.tick()
            // Android's platform View API has no prepare call; respect platform haptic settings.
            if(impact && token==state.generation && state.impactAllowed && view.isAttachedToWindow && view.isHapticFeedbackEnabled)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    LaunchedEffect(state.generation,state.resumeDeadline) {
        val deadline=state.resumeDeadline ?: return@LaunchedEffect
        val token=state.generation
        val wait=ceil(max(0.0,deadline-TrophySavingsMilestoneRibbonState.now())*1000).toLong()
        if(wait>0) delay(wait)
        state.resumeGallery(token,deadline)
    }
    LaunchedEffect(state.reducedFeedback,reduced,state.active,state.overlay) {
        feedback.snapTo(1f)
        if(reduced && state.reducedFeedback>0 && state.canActivate) {
            val ease=CubicBezierEasing(0f,0f,0.58f,1f)
            feedback.animateTo(0.88f,tween(120,easing=ease)); feedback.animateTo(1f,tween(100,easing=ease))
        }
    }
    Box(Modifier.size(393.dp,350.dp)) {
        Canvas(Modifier.size(393.dp,350.dp)) {
            val frame=state.presented.value
            ribbon.update(frame.progress,frame.pose)
            drawIntoCanvas { holder ->
                val canvas=holder.nativeCanvas
                canvas.save(); canvas.scale(density,density)
                lighting.drawPool(canvas,frame.scene)
                TrophySavingsMilestonePaper.drawMoving(canvas,frame.scene,false)
                ribbon.draw(canvas,false)
                TrophySavingsMilestoneArtwork.draw(canvas,feedback.value)
                lighting.drawCup(canvas,frame.scene); lighting.drawShimmer(canvas,frame.scene)
                ribbon.draw(canvas,true)
                TrophySavingsMilestonePaper.drawMoving(canvas,frame.scene,true)
                canvas.restore()
            }
        }
        val drag=if(previewTime!=null) Modifier else Modifier.pointerInput(state,density,reduced) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                val velocity=VelocityTracker(); velocity.addPosition(down.uptimeMillis,down.position)
                var recognized=false; var released=false; var cancelledTap=false
                try {
                    while(true) {
                        val event=awaitPointerEvent(PointerEventPass.Initial)
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        if(!state.canActivate) break
                        if(event.changes.count { it.pressed }>1) { event.changes.forEach { it.consume() }; break }
                        velocity.addPosition(change.uptimeMillis,change.position)
                        val displacement=change.position-down.position
                        if(!change.pressed) {
                            if(recognized) { change.consume(); state.finishContact(false); released=true }
                            if(cancelledTap) change.consume()
                            break
                        }
                        if(displacement.getDistance()>=6*density) {
                            if(reduced) cancelledTap=true else recognized=true
                        }
                        if(recognized) {
                            state.drag((displacement.x/density).toDouble(),(velocity.calculateVelocity().x/density).toDouble())
                            change.consume()
                        } else if(cancelledTap) change.consume()
                    }
                } finally { if(recognized && !released) state.finishContact(true) }
            }
        }
        Box(Modifier.offset(x=19.5.dp,y=73.dp).size(353.dp,260.dp).then(drag)
            .semantics { contentDescription="Savings trophy" }
            .clickable(interactionSource=interaction,indication=null,role=Role.Button,
                onClickLabel=if(reduced) "Replay brief celebration feedback." else "Replay the celebration. Drag horizontally to feed the paper streamer.",
                onClick=state::demonstrate))
    }
}
