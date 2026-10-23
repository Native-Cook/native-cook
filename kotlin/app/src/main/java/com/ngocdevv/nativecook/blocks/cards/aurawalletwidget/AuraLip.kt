package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import kotlin.math.max

// The hit area stays at its slot while the portrait follows exactly the same
// pose as its shader hole. Slop is screen points; pull travel is design points.
@Composable
internal fun AuraLip(machine: AuraMachine, portraits: AuraPortraits, unit: Double, density: Float) {
    val hint = stringResource(R.string.aurawalletwidget_friend_hint)
    val drive = machine.drive
    for (index in 0..4) {
        val side = AuraPalette.Lip.avatar * unit
        val touchSide = max(side, AuraPalette.Touch.minimum)
        Box(
            Modifier.offset((auraSlotX(index) * unit - touchSide / 2).dp,
                (AuraPalette.Lip.cy * unit - touchSide / 2).dp)
                .size(touchSide.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = AuraPeople.names[index]
                    selected = drive.chosen == index && drive.phase != AuraPhase.Rest
                    onClick(label = hint) {
                        machine.pick(index)
                        true
                    }
                }
                .pointerInput(machine, index, unit, density) {
                    val velocity = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        machine.touch()
                        val accepted = !machine.isLocked()
                        if (accepted) machine.press(index, true, friend = true)
                        val origin = down.position
                        var pullOrigin = origin
                        var pulling = false
                        var completed = false
                        velocity.resetTracking()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (event.changes.count { it.pressed } > 1 || change.isConsumed) break
                                velocity.addPosition(change.uptimeMillis, change.position)
                                val travel = change.position - origin
                                if (!pulling && accepted && travel.getDistance() > AuraFlow.Views.pullSlop * density) {
                                    pulling = true
                                    // Pan translation starts at recognition, after slop, on both axes.
                                    pullOrigin = change.position
                                    machine.beginPull(index)
                                }
                                if (pulling) {
                                    val pullTravel = change.position - pullOrigin
                                    machine.movePull(
                                        index,
                                        pullTravel.x / density / unit,
                                        pullTravel.y / density / unit,
                                    )
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    val speed = velocity.calculateVelocity()
                                    if (pulling) machine.endPull(index, speed.x / density / unit, speed.y / density / unit, false)
                                    else if (accepted) machine.pick(index)
                                    change.consume()
                                    completed = true
                                    break
                                }
                            }
                        } finally {
                            if (!completed && pulling) {
                                val speed = velocity.calculateVelocity()
                                machine.endPull(index, speed.x / density / unit, speed.y / density / unit, true)
                            }
                            machine.press(index, false, friend = true)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(side.dp).graphicsLayer {
                auraLipPose(
                    index,
                    drive.chosen,
                    drive.formed.value,
                    drive.leaving,
                    drive.unformed.value,
                ) { dx, dy, faceScale, faceOpacity, _ ->
                    var x = dx + drive.offsets[index * 4].value
                    var y = dy + drive.offsets[index * 4 + 1].value + auraNudgeDip(index, drive.nudge.value)
                    var horizontal = faceScale + drive.offsets[index * 4 + 2].value
                    var vertical = horizontal
                    var opacity = faceOpacity + drive.offsets[index * 4 + 3].value
                    if (index == drive.chosen) {
                        x += drive.pullX.value
                        y += drive.pullY.value
                        if (drive.phase == AuraPhase.Sending) {
                            val elapsed = drive.clock.value
                            val growing = auraSendMoments(drive.fromY) { _, _, _, _, bud, _ -> elapsed > bud }
                            auraSendPose(elapsed, drive.fromY) { _, _, dropY, tall, wide, presence, _, _, _, _, _, _, _, bud, budOpacity ->
                                x = if (growing) 0.0 else drive.fromX - auraSlotX(index)
                                y = if (growing) 0.0 else dropY - AuraPalette.Lip.cy
                                horizontal = if (growing) bud else wide
                                vertical = if (growing) bud else tall
                                opacity = if (growing) budOpacity else presence
                                if (machine.reduced) {
                                    opacity = if (growing) budOpacity
                                    else max(0.0, 1 - elapsed / AuraFlow.Views.departureFade)
                                }
                            }
                        }
                    }
                    val compression = if (drive.phase == AuraPhase.Sending && index == drive.chosen) 1.0
                    else 1 + (AuraPalette.Motion.pressScale - 1) * drive.friendPress[index].value
                    if (!machine.reduced && drive.entered.value >= 0) {
                        val growth = auraEnterScale(index, drive.entered.value)
                        horizontal *= growth
                        vertical *= growth
                        opacity *= auraEnterOpacity(index, drive.entered.value)
                    }
                    alpha = opacity.coerceIn(0.0, 1.0).toFloat()
                    translationX = if (machine.reduced) 0f else (x * unit * density).toFloat()
                    translationY = if (machine.reduced) 0f else (y * unit * density).toFloat()
                    scaleX = if (machine.reduced) 1f else (horizontal * compression).toFloat()
                    scaleY = if (machine.reduced) 1f else (vertical * compression).toFloat()
                }
            }) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.save()
                    canvas.nativeCanvas.translate(size.width / 2, size.height / 2)
                    portraits.draw(canvas.nativeCanvas, index, size.width / 2, zoom = true)
                    canvas.nativeCanvas.restore()
                }
            }
        }
    }
}
