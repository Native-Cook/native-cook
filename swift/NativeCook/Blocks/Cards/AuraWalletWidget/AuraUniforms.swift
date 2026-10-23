// One pure argument builder joins the static material to the score's plain values.
// The caller owns storage; no arrays or dictionaries are created on a frame.
import Foundation

enum AuraUniforms {
  typealias Slot = AuraUniformSlot

  static func put(_ buffer: inout [Float], _ at: Int, _ x: Double) {
    buffer[at] = Float(x)
  }

  static func put(_ buffer: inout [Float], _ at: Int, _ x: Double, _ y: Double) {
    buffer[at] = Float(x)
    buffer[at + 1] = Float(y)
  }

  static func put(_ buffer: inout [Float], _ at: Int, _ x: Double, _ y: Double, _ z: Double) {
    buffer[at] = Float(x)
    buffer[at + 1] = Float(y)
    buffer[at + 2] = Float(z)
  }

  static func put(
    _ buffer: inout [Float], _ at: Int, _ x: Double, _ y: Double, _ z: Double, _ w: Double
  ) {
    buffer[at] = Float(x)
    buffer[at + 1] = Float(y)
    buffer[at + 2] = Float(z)
    buffer[at + 3] = Float(w)
  }

  static func color(_ buffer: inout [Float], _ at: Int, _ hex: UInt32) {
    put(
      &buffer, at, Double((hex >> 16) & 255) / 255,
      Double((hex >> 8) & 255) / 255, Double(hex & 255) / 255)
  }

  /// Static geometry is rebuilt only on layout or display-scale changes.
  static func shell(width: Double, pixelRatio: Double) -> [Float] {
    var buffer = [Float](repeating: 0, count: Slot.count)
    let u = width / AuraDesign.design
    put(&buffer, Slot.origin, 64, 64)
    put(&buffer, Slot.cardWidth, width)
    put(&buffer, Slot.pixelRatio, pixelRatio)
    color(&buffer, Slot.pageTone, 0x040116)
    color(&buffer, Slot.leftCore, 0xD9A8DC)
    color(&buffer, Slot.leftEdge, 0xA25CD8)
    color(&buffer, Slot.lowCore, 0x5A12B8)
    color(&buffer, Slot.lowEdge, 0x2D065F)
    color(&buffer, Slot.highTone, 0x1B075C)
    put(&buffer, Slot.shellRadius, 44 * u)
    put(&buffer, Slot.bleed, 64)
    put(&buffer, Slot.shellClear, 0.3)
    color(&buffer, Slot.shellTone, 0x17142A)
    color(&buffer, Slot.lightTone, 0xFFFFFF)
    put(&buffer, Slot.heroRect, 12 * u, 12 * u, 338 * u, 156 * u)
    put(&buffer, Slot.heroRadii, 32 * u, 40 * u)
    put(&buffer, Slot.lipShape, 164 * u, 21 * u)
    for i in 0..<5 { put(&buffer, Slot.lipXs + i, auraSlotX(Double(i)) * u) }
    color(&buffer, Slot.heroCore, 0xB98FF5)
    color(&buffer, Slot.heroMid, 0x8A4FD8)
    color(&buffer, Slot.heroLeft, 0x6928CA)
    color(&buffer, Slot.heroEdge, 0x5411C1)
    put(&buffer, Slot.paneRect, 12 * u, 201 * u, 338 * u, 80 * u)
    put(&buffer, Slot.paneRadius, 24 * u)
    put(&buffer, Slot.paneFill, 1, 1, 1, 0.045)
    put(&buffer, Slot.paneLine, 1, 1, 1, 0.16)
    color(&buffer, Slot.pillowTop, 0x3E3C4A)
    color(&buffer, Slot.pillowMid, 0x2B273E)
    color(&buffer, Slot.pillowLow, 0x211D30)
    color(&buffer, Slot.pillowShine, 0x7B7986)
    color(&buffer, Slot.accentTop, 0x8655CB)
    color(&buffer, Slot.accentMid, 0x6330B9)
    color(&buffer, Slot.accentLow, 0x5829A9)
    color(&buffer, Slot.accentShine, 0xEBD9FF)
    put(&buffer, Slot.shadowSizes, 3 * u, 7 * u, 3 * u, 3.5 * u)
    put(&buffer, Slot.surfaceSizes, 1.5 * u, 2 * u, 0.65 * u, u)
    motion(&buffer, width: width, drive: AuraDrive(), opaque: false)
    return buffer
  }

  static func motion(_ buffer: inout [Float], width: Double, drive d: AuraDrive, opaque: Bool) {
    let u = width / AuraDesign.design
    let chosen = Double(d.chosen)
    let sending = d.phase == .sending && d.chosen >= 0
    let pulled = d.chosen >= 0 && (d.pullX != 0 || d.pullY != 0)
    let moving = sending || pulled
    let pose = auraSendPose(d.clock, d.fromY)
    let weightedHalo = d.chosen >= 0 ? auraDropHalo(d.weight) : AuraFlow.drop.halo
    for i in 0..<5 {
      let lip = auraLipPose(Double(i), chosen, d.formed, Double(d.leaving), d.unformed)
      let absent = i == d.chosen && moving
      let bud = sending && i == d.chosen ? pose.bud : 0
      let arrival = d.entered >= 0 ? auraEnterScale(Double(i), d.entered) : lip.scale
      let faceScale = absent ? bud : arrival
      let halo =
        absent ? 0 : i == d.chosen ? lip.halo / AuraFlow.drop.halo * weightedHalo : lip.halo
      put(
        &buffer, Slot.lipPoses + i * 4,
        (auraSlotX(Double(i)) + (absent ? 0 : lip.dx)) * u,
        (164 + (absent ? 0 : lip.dy) + auraNudgeDip(Double(i), d.nudge)) * u,
        (faceScale > 0 ? 18 * faceScale + 3 : 0) * u, halo * u)
    }
    let chosenPose = auraLipPose(chosen, chosen, d.formed, Double(d.leaving), d.unformed)
    let dropX = sending ? d.fromX : auraSlotX(chosen) + chosenPose.dx + d.pullX
    let dropY = sending ? pose.y : 164 + chosenPose.dy + d.pullY + auraNudgeDip(chosen, d.nudge)
    let rootY = 168.0
    let rootX = auraSlotX(chosen) + (dropX - auraSlotX(chosen)) * 0.2
    let radius = 21 + chosenPose.halo / AuraFlow.drop.halo * weightedHalo
    let dropWide = sending ? pose.wide : 1
    let dropTall = sending ? pose.tall : 1
    let contact =
      sending
      ? auraClamp(
        (d.clock - auraSendMoments(d.fromY).impact + AuraFlow.send.squash) / AuraFlow.send.squash)
      : 0
    let castDown = 3 * (1 - contact * 0.45)
    let castReach = 5 * (1 - contact * 0.45)
    let depth = sending ? d.fromY - 164 - AuraFlow.drop.sag : d.pullY
    let joined = auraClamp(depth / AuraFlow.neck.blend)
    let rootWidth = auraNeckRoot(depth)
    let actions = auraActionFrames(d.wide)
    for i in 0..<4 {
      put(
        &buffer, Slot.actionRects + i * 4, actions.xs[i] * u, 298 * u, actions.widths[i] * u, 52 * u
      )
      put(&buffer, Slot.pressValues + i, d.press[i])
    }
    put(&buffer, Slot.opaque, opaque ? 1 : 0)
    put(&buffer, Slot.neckJoin, joined * joined * (3 - 2 * joined))
    put(
      &buffer, Slot.liquidState,
      d.chosen >= 0 || d.leaving >= 0 || d.nudge >= 0 || d.entered >= 0 ? 1 : 0,
      chosen, sending ? 1 : 0, u)
    put(&buffer, Slot.dropPose, dropX * u, dropY * u, radius * u, 21 * u)
    put(
      &buffer, Slot.dropShape, dropWide, dropTall, sending ? pose.presence : 1,
      sending ? pose.neck : 1)
    put(
      &buffer, Slot.neckPose, rootX * u, rootY * u, auraNeckWaist(depth) * u,
      AuraFlow.neck.blend * u)
    put(
      &buffer, Slot.sendFx, sending ? pose.lipKick * u : 0, sending ? pose.cushion : 1,
      sending ? pose.rippleRadius * u : 0, sending ? pose.rippleStrength : 0)
    put(
      &buffer, Slot.liquidOptics, rootWidth * u, AuraFlow.drop.halo * u,
      moving ? 1 : 0, min(chosenPose.halo / AuraFlow.drop.halo, 1))
    put(
      &buffer, Slot.liquidBounds,
      (min(rootX - rootWidth, dropX - radius * dropWide) - AuraFlow.neck.blend * 2) * u,
      (min(rootY, dropY - radius * dropTall) - AuraFlow.neck.blend * 2) * u,
      (max(rootX + rootWidth, dropX + radius * dropWide) + AuraFlow.neck.blend * 2) * u,
      (dropY + radius * dropTall + AuraFlow.neck.blend * 2) * u)
    put(
      &buffer, Slot.dropCast, castDown * u, castReach * u,
      0.24 * min(chosenPose.halo / AuraFlow.drop.halo, 1) * (sending ? 1 - pose.taken : 1))
    put(
      &buffer, Slot.castBounds, (dropX - (radius + castReach * 2) * dropWide) * u,
      (dropY + castDown - (radius + castReach * 2) * dropTall) * u,
      (dropX + (radius + castReach * 2) * dropWide) * u,
      (dropY + castDown + (radius + castReach * 2) * dropTall) * u)
    put(&buffer, Slot.poolWave, sending ? pose.waveRadius * u : 0, sending ? pose.waveStrength : 0)
    put(&buffer, Slot.landing, d.fromX * u, auraLandY * u, sending ? pose.rim : 1, actions.kiss * u)
  }
}
