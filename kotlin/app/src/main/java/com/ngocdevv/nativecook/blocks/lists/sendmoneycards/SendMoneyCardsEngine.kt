package com.ngocdevv.nativecook.blocks.lists.sendmoneycards

import android.os.SystemClock
import kotlin.math.max

internal class SendMoneyCardsEngine(private val autoPlay: Boolean) {
    enum class Haptic { None, Light, Medium, Soft }

    private sealed interface State {
        data object Rest : State
        data class Hold(val pose: SendMoneyCardsSpec.Pose) : State
        data class Pressing(val start: Long, val from: SendMoneyCardsSpec.Pose, val drag: Float) : State
        data class Releasing(val start: Long, val lift: Float, val drag: Float, val arm: Float) : State
        data class Sending(val start: Long, val from: SendMoneyCardsSpec.Pose) : State
        data object Open : State
        data class Closing(val start: Long) : State
    }

    var reduced = false
        set(value) {
            if (field == value) return
            field = value
            resumeAt = Long.MAX_VALUE
            restartAt = Long.MAX_VALUE
            if (!interrupted) {
                state = State.Rest
                openFlag = false
                autoStart = SystemClock.uptimeMillis()
            }
        }
    var width = SendMoneyCardsSpec.ScreenWidth
    var height = SendMoneyCardsSpec.ScreenHeight
    var offsetY = 0f
    var pressedTarget: String? = null
    private var state: State = State.Rest
    private var autoStart = SystemClock.uptimeMillis()
    private var interrupted = false
    private var resumeAt = Long.MAX_VALUE
    private var restartAt = Long.MAX_VALUE
    private var transitionEndsAt = Long.MAX_VALUE
    private var lastPose = SendMoneyCardsSpec.Pose()
    private var openFlag = false
    private var hintPosition = 0f
    private var hintVelocity = 0f
    private var hintTime = SystemClock.uptimeMillis()

    val isOpen: Boolean
        get() = if (interrupted) openFlag else {
            autoPlay && !reduced && scriptTime(SystemClock.uptimeMillis()) in
                SendMoneyCardsSpec.ScriptOpen..SendMoneyCardsSpec.ScriptClose
        }

    val needsFrames: Boolean
        get() {
            if (autoPlay && !reduced && !interrupted) return true
            return when (state) {
                is State.Pressing, is State.Releasing, is State.Sending, is State.Closing -> true
                State.Rest, is State.Hold, State.Open -> resumeAt != Long.MAX_VALUE
            }
        }

    fun update(now: Long) {
        when (val current = state) {
            is State.Sending -> if (now >= transitionEndsAt) {
                state = State.Open
                transitionEndsAt = Long.MAX_VALUE
            }
            is State.Releasing -> if (now >= transitionEndsAt) {
                state = State.Rest
                transitionEndsAt = Long.MAX_VALUE
            }
            is State.Closing -> if (now >= transitionEndsAt) {
                state = State.Rest
                transitionEndsAt = Long.MAX_VALUE
            }
            else -> Unit
        }
        if (!reduced && now >= resumeAt) {
            resumeAt = Long.MAX_VALUE
            if (state == State.Rest) {
                restart(now)
            } else if (isOpen) {
                close(now, haptic = false)
                restartAt = now + SendMoneyCardsSpec.CloseDuration.toLong() + 900L
            } else {
                release(now)
                restartAt = now + SendMoneyCardsSpec.CloseDuration.toLong() + 900L
            }
        }
        if (!reduced && now >= restartAt) {
            restart(now)
            restartAt = Long.MAX_VALUE
        }
    }

    fun pose(now: Long): SendMoneyCardsSpec.Pose {
        val offscreen = height + 40f
        var pose = if (autoPlay && !reduced && !interrupted) {
            SendMoneyCardsSpec.scriptPose(scriptTime(now), offscreen, reduced)
        } else {
            manualPose(now, offscreen)
        }
        if (interrupted && !reduced) {
            pose = pose.copy(hintLift = updateHintSpring(pose.lift, now))
        }
        lastPose = pose
        return pose
    }

    fun interrupt(now: Long) {
        if (interrupted) return
        val current = pose(now)
        interrupted = true
        openFlag = current.sheet > 0.5f || current.fade > 0.5f
        hintPosition = current.hintLift
        hintVelocity = 0f
        hintTime = now
        state = State.Hold(current)
    }

    fun beginCard(now: Long): Haptic {
        interrupt(now)
        state = State.Pressing(now, pose(now), 0f)
        transitionEndsAt = Long.MAX_VALUE
        return Haptic.Light
    }

    fun dragCard(now: Long, translationY: Float) {
        val current = state
        if (current is State.Pressing) {
            state = current.copy(drag = if (reduced) 0f else SendMoneyCardsSpec.rubber(translationY))
            pose(now)
        }
    }

    fun endCard(now: Long, translationY: Float, velocityY: Float): Haptic {
        val haptic = if (translationY < -SendMoneyCardsSpec.OpenDrag ||
            velocityY < -SendMoneyCardsSpec.FlickVelocity) {
            send(now)
            Haptic.Medium
        } else {
            release(now)
            Haptic.None
        }
        scheduleResume(now)
        return haptic
    }

    fun cancelCard(now: Long) {
        release(now)
        scheduleResume(now)
    }

    fun endSheet(now: Long, translationY: Float, velocityY: Float): Haptic {
        val haptic = if (translationY < -40f || velocityY < -SendMoneyCardsSpec.FlickVelocity) {
            close(now, haptic = true)
        } else Haptic.None
        scheduleResume(now)
        return haptic
    }

    fun closeButton(now: Long): Haptic {
        interrupt(now)
        val haptic = close(now, haptic = true)
        scheduleResume(now)
        return haptic
    }

    fun accessibilitySend(now: Long): Haptic {
        interrupt(now)
        send(now)
        scheduleResume(now)
        return Haptic.Medium
    }

    fun press(target: String?) {
        pressedTarget = target
    }

    fun endPassiveTouch(now: Long) {
        pressedTarget = null
        scheduleResume(now)
    }

    private fun send(now: Long) {
        val current = pose(now)
        state = State.Sending(now, current)
        openFlag = true
        transitionEndsAt = now + (if (reduced) SendMoneyCardsSpec.FadeDuration else max(SendMoneyCardsSpec.FallDuration, SendMoneyCardsSpec.OpenDuration)).toLong()
    }

    private fun release(now: Long) {
        val current = pose(now)
        val pieces = when (val pressing = state) {
            is State.Pressing -> {
                val elapsed = (now - pressing.start).toFloat()
                val lift = SendMoneyCardsSpec.mix(pressing.from.lift, SendMoneyCardsSpec.PressLift,
                    SendMoneyCardsSpec.easeOutCubic(SendMoneyCardsSpec.segment(
                        elapsed, 0f, SendMoneyCardsSpec.PressDuration,
                    )))
                val arm = SendMoneyCardsSpec.mix(pressing.from.arm, 1f,
                    SendMoneyCardsSpec.easeOutCubic(SendMoneyCardsSpec.segment(
                        elapsed, 0f, SendMoneyCardsSpec.ArmDuration,
                    )))
                Triple(lift, pressing.drag, arm)
            }
            else -> Triple(current.lift, 0f, current.arm)
        }
        state = State.Releasing(now, pieces.first, pieces.second, pieces.third)
        transitionEndsAt = now + (if (reduced) SendMoneyCardsSpec.FadeDuration else max(SendMoneyCardsSpec.ReleaseDuration, 700f)).toLong()
    }

    private fun close(now: Long, haptic: Boolean): Haptic {
        state = State.Closing(now)
        openFlag = false
        transitionEndsAt = now + if (reduced) SendMoneyCardsSpec.FadeDuration.toLong() else 900L
        return if (haptic) Haptic.Soft else Haptic.None
    }

    private fun restart(now: Long) {
        if (!autoPlay || reduced) return
        state = State.Rest
        openFlag = false
        interrupted = false
        autoStart = now
        hintPosition = 0f
        hintVelocity = 0f
        hintTime = now
    }

    private fun updateHintSpring(target: Float, now: Long): Float {
        val seconds = ((now - hintTime).coerceIn(0L, 50L)) / 1000f
        hintTime = now
        if (seconds <= 0f) return hintPosition
        val acceleration = SendMoneyCardsSpec.HintFollowStiffness * (target - hintPosition) -
            SendMoneyCardsSpec.HintFollowDamping * hintVelocity
        hintVelocity += acceleration * seconds
        hintPosition += hintVelocity * seconds
        return hintPosition
    }

    private fun scheduleResume(now: Long) {
        if (autoPlay && !reduced) resumeAt = now + SendMoneyCardsSpec.ResumeAfterMs.toLong()
    }

    private fun scriptTime(now: Long): Float {
        val elapsed = max(0L, now - autoStart).toFloat()
        return elapsed % SendMoneyCardsSpec.LoopMs
    }

    private fun manualPose(now: Long, offscreen: Float): SendMoneyCardsSpec.Pose {
        return when (val current = state) {
            State.Rest -> SendMoneyCardsSpec.Pose()
            is State.Hold -> current.pose
            is State.Pressing -> {
                val elapsed = (now - current.start).toFloat()
                if (reduced) {
                    val arm = SendMoneyCardsSpec.mix(current.from.arm, 1f,
                        SendMoneyCardsSpec.segment(elapsed, 0f, SendMoneyCardsSpec.FadeDuration))
                    SendMoneyCardsSpec.Pose(arm = arm)
                } else {
                    val lift = SendMoneyCardsSpec.mix(current.from.lift, SendMoneyCardsSpec.PressLift,
                        SendMoneyCardsSpec.easeOutCubic(
                            SendMoneyCardsSpec.segment(elapsed, 0f, SendMoneyCardsSpec.PressDuration),
                        )) + current.drag
                    val arm = SendMoneyCardsSpec.mix(current.from.arm, 1f,
                        SendMoneyCardsSpec.easeOutCubic(
                            SendMoneyCardsSpec.segment(elapsed, 0f, SendMoneyCardsSpec.ArmDuration),
                        ))
                    SendMoneyCardsSpec.Pose(lift = lift, arm = arm, hintLift = lift)
                }
            }
            is State.Releasing -> {
                val elapsed = (now - current.start).toFloat()
                val timedLift = SendMoneyCardsSpec.mix(current.lift, 0f,
                    SendMoneyCardsSpec.easeInOutCubic(SendMoneyCardsSpec.segment(
                        elapsed, 0f, SendMoneyCardsSpec.ReleaseDuration,
                    )))
                val dragLift = SendMoneyCardsSpec.springDisplacement(
                    milliseconds = elapsed,
                    initial = current.drag,
                    velocity = 0f,
                    mass = SendMoneyCardsSpec.SnapBackMass,
                    stiffness = SendMoneyCardsSpec.SnapBackStiffness,
                    damping = SendMoneyCardsSpec.SnapBackDamping,
                )
                val lift = if (reduced) 0f else timedLift + dragLift
                val duration = if (reduced) SendMoneyCardsSpec.FadeDuration else SendMoneyCardsSpec.ArmDuration
                val arm = SendMoneyCardsSpec.mix(current.arm, 0f,
                    SendMoneyCardsSpec.segment(elapsed, 0f, duration))
                SendMoneyCardsSpec.Pose(lift = lift, arm = arm, hintLift = lift)
            }
            is State.Sending -> {
                val elapsed = (now - current.start).toFloat()
                if (reduced) {
                    SendMoneyCardsSpec.Pose(fade = SendMoneyCardsSpec.mix(current.from.fade, 1f,
                        SendMoneyCardsSpec.segment(elapsed, 0f, SendMoneyCardsSpec.FadeDuration)))
                } else {
                    val lift = SendMoneyCardsSpec.mix(current.from.lift, offscreen,
                        SendMoneyCardsSpec.segment(elapsed, 0f, SendMoneyCardsSpec.FallDuration))
                    val sheet = SendMoneyCardsSpec.mix(current.from.sheet, 1f,
                        SendMoneyCardsSpec.segment(elapsed, SendMoneyCardsSpec.OpenDelay,
                            SendMoneyCardsSpec.OpenDuration))
                    SendMoneyCardsSpec.Pose(lift = lift, arm = 1f, sheet = sheet, hintLift = lift)
                }
            }
            State.Open -> if (reduced) SendMoneyCardsSpec.Pose(fade = 1f) else {
                SendMoneyCardsSpec.Pose(lift = offscreen, arm = 1f, sheet = 1f, hintLift = offscreen)
            }
            is State.Closing -> {
                val elapsed = (now - current.start).toFloat()
                if (reduced) {
                    SendMoneyCardsSpec.Pose(
                        fade = 1f - SendMoneyCardsSpec.segment(elapsed, 0f, SendMoneyCardsSpec.FadeDuration),
                        closing = true,
                    )
                } else {
                    val sheet = 1f - SendMoneyCardsSpec.segment(elapsed, 0f, SendMoneyCardsSpec.CloseDuration)
                    val lift = if (elapsed < SendMoneyCardsSpec.ReturnDuration) {
                        SendMoneyCardsSpec.table(elapsed, SendMoneyCardsSpec.ReturnTimes,
                            SendMoneyCardsSpec.ReturnLifts)
                    } else SendMoneyCardsSpec.springSettle(elapsed - SendMoneyCardsSpec.ReturnDuration)
                    val arm = 1f - SendMoneyCardsSpec.easeInOutCubic(
                        SendMoneyCardsSpec.segment(elapsed, SendMoneyCardsSpec.TabOut, SendMoneyCardsSpec.TabFade),
                    )
                    val hintTime = max(0f, elapsed - SendMoneyCardsSpec.HintLag)
                    val hintLift = if (hintTime < SendMoneyCardsSpec.ReturnDuration) {
                        SendMoneyCardsSpec.table(hintTime, SendMoneyCardsSpec.ReturnTimes,
                            SendMoneyCardsSpec.ReturnLifts)
                    } else SendMoneyCardsSpec.springSettle(hintTime - SendMoneyCardsSpec.ReturnDuration)
                    SendMoneyCardsSpec.Pose(lift, arm, sheet, 0f, hintLift, true)
                }
            }
        }
    }
}
