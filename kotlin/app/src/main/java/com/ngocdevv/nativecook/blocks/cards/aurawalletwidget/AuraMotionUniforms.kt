package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import kotlin.math.max
import kotlin.math.min

// Deterministic translation of the score into shader uniforms, with no Android
// or state reads. Caller-owned output storage avoids allocating arrays per frame.
internal fun auraMotionUniforms(
    width: Double,
    phase: AuraPhase,
    chosen: Int,
    formed: Double,
    leaving: Int,
    unformed: Double,
    pullX: Double,
    pullY: Double,
    clock: Double,
    fromX: Double,
    fromY: Double,
    wide: Double,
    nudge: Double,
    opaque: Double,
    settings: Double,
    swap: Double,
    receive: Double,
    send: Double,
    weight: Double,
    entered: Double,
    output: AuraUniforms,
) {
    val unit = width / AuraPalette.design
    val sending = phase == AuraPhase.Sending && chosen >= 0
    val pulled = chosen >= 0 && (pullX != 0.0 || pullY != 0.0)
    val moving = sending || pulled
    val weightedHalo = if (chosen >= 0) auraDropHalo(weight) else AuraFlow.Drop.halo
    // An idle score is harmless; only sending reads its values below.
    auraSendPose(clock, fromY) { neck, lipKick, y, tall, dropWide, presence, taken,
        rippleRadius, rippleStrength, rim, cushion, waveRadius, waveStrength, bud, _ ->
        for (index in 0..4) {
            auraLipPose(index, chosen, formed, leaving, unformed) { dx, dy, scale, _, halo ->
                val absent = index == chosen && moving
                val growing = if (sending && index == chosen) bud else 0.0
                val arrival = if (entered >= 0) auraEnterScale(index, entered) else scale
                val faceScale = if (absent) growing else arrival
                val at = index * 4
                output.lipPoses[at] = ((auraSlotX(index) + if (absent) 0.0 else dx) * unit).toFloat()
                output.lipPoses[at + 1] = ((AuraPalette.Lip.cy + (if (absent) 0.0 else dy) + auraNudgeDip(index, nudge)) * unit).toFloat()
                val radius = if (faceScale > 0) AuraPalette.Lip.avatar / 2 * faceScale + AuraPalette.Lip.ring else 0.0
                val ringHalo = if (absent) 0.0 else if (index == chosen) halo / AuraFlow.Drop.halo * weightedHalo else halo
                output.lipPoses[at + 2] = (radius * unit).toFloat()
                output.lipPoses[at + 3] = (ringHalo * unit).toFloat()
            }
        }
        auraLipPose(chosen, chosen, formed, leaving, unformed) { dx, dy, _, _, halo ->
            val dropX = if (sending) fromX else auraSlotX(chosen) + dx + pullX
            val dropY = if (sending) y else AuraPalette.Lip.cy + dy + pullY + auraNudgeDip(chosen, nudge)
            val rootY = AuraPalette.Hero.y + AuraPalette.Hero.h
            val rootX = auraSlotX(chosen) + (dropX - auraSlotX(chosen)) * 0.2
            val radius = AuraPalette.Lip.avatar / 2 + AuraPalette.Lip.ring + halo / AuraFlow.Drop.halo * weightedHalo
            val bodyWide = if (sending) dropWide else 1.0
            val bodyTall = if (sending) tall else 1.0
            val contact = if (sending) auraSendMoments(fromY) { _, _, impact, _, _, _ ->
                ((clock - impact + AuraFlow.Send.squash) / AuraFlow.Send.squash).coerceIn(0.0, 1.0)
            } else 0.0
            val castDown = 3 * (1 - contact * 0.45)
            val castReach = 5 * (1 - contact * 0.45)
            val depth = if (sending) fromY - AuraPalette.Lip.cy - AuraFlow.Drop.sag else pullY
            val joined = (depth / AuraFlow.Neck.blend).coerceIn(0.0, 1.0)
            val rootWidth = auraNeckRoot(depth)
            output.opaque[0] = opaque.toFloat()
            output.neckJoin[0] = (joined * joined * (3 - 2 * joined)).toFloat()
            auraFour(output.pressValues, settings, swap, receive, send)
            auraActionFrames(wide) { settingsX, swapX, receiveX, sendX, settingsWidth, swapWidth, receiveWidth, sendWidth, kiss ->
                auraRect(output.actionRects, 0, settingsX, settingsWidth, unit)
                auraRect(output.actionRects, 4, swapX, swapWidth, unit)
                auraRect(output.actionRects, 8, receiveX, receiveWidth, unit)
                auraRect(output.actionRects, 12, sendX, sendWidth, unit)
                auraFour(output.landing, fromX * unit, AURA_LAND_Y * unit, if (sending) rim else 1.0, kiss * unit)
            }
            auraFour(output.liquidState,
                if (chosen >= 0 || leaving >= 0 || nudge >= 0 || entered >= 0) 1.0 else 0.0,
                if (chosen >= 0) chosen.toDouble() else -1.0, if (sending) 1.0 else 0.0, unit)
            auraFour(output.dropPose, dropX * unit, dropY * unit, radius * unit,
                (AuraPalette.Lip.avatar / 2 + AuraPalette.Lip.ring) * unit)
            auraFour(output.dropShape, bodyWide, bodyTall, if (sending) presence else 1.0, if (sending) neck else 1.0)
            auraFour(output.neckPose, rootX * unit, rootY * unit, auraNeckWaist(depth) * unit, AuraFlow.Neck.blend * unit)
            auraFour(output.sendFx, if (sending) lipKick * unit else 0.0, if (sending) cushion else 1.0,
                if (sending) rippleRadius * unit else 0.0, if (sending) rippleStrength else 0.0)
            auraFour(output.liquidOptics, rootWidth * unit, AuraFlow.Drop.halo * unit,
                if (moving) 1.0 else 0.0, min(halo / AuraFlow.Drop.halo, 1.0))
            auraFour(output.liquidBounds,
                (min(rootX - rootWidth, dropX - radius * bodyWide) - AuraFlow.Neck.blend * 2) * unit,
                (min(rootY, dropY - radius * bodyTall) - AuraFlow.Neck.blend * 2) * unit,
                (max(rootX + rootWidth, dropX + radius * bodyWide) + AuraFlow.Neck.blend * 2) * unit,
                (dropY + radius * bodyTall + AuraFlow.Neck.blend * 2) * unit)
            output.dropCast[0] = (castDown * unit).toFloat()
            output.dropCast[1] = (castReach * unit).toFloat()
            output.dropCast[2] = (0.24 * min(halo / AuraFlow.Drop.halo, 1.0) * if (sending) 1 - taken else 1.0).toFloat()
            auraFour(output.castBounds,
                (dropX - (radius + castReach * 2) * bodyWide) * unit,
                (dropY + castDown - (radius + castReach * 2) * bodyTall) * unit,
                (dropX + (radius + castReach * 2) * bodyWide) * unit,
                (dropY + castDown + (radius + castReach * 2) * bodyTall) * unit)
            output.poolWave[0] = (if (sending) waveRadius * unit else 0.0).toFloat()
            output.poolWave[1] = (if (sending) waveStrength else 0.0).toFloat()
        }
    }
}

private fun auraFour(output: FloatArray, x: Double, y: Double, z: Double, w: Double) {
    output[0] = x.toFloat()
    output[1] = y.toFloat()
    output[2] = z.toFloat()
    output[3] = w.toFloat()
}

private fun auraRect(output: FloatArray, at: Int, x: Double, width: Double, unit: Double) {
    output[at] = (x * unit).toFloat()
    output[at + 1] = (AuraPalette.Actions.y * unit).toFloat()
    output[at + 2] = (width * unit).toFloat()
    output[at + 3] = (AuraPalette.Actions.h * unit).toFloat()
}
