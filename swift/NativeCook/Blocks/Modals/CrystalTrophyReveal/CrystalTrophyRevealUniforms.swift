import Foundation

extension CrystalTrophyRevealLayout {
  func uniforms(_ s: CrystalTrophyRevealMotion, pixelRatio: Double) -> [SIMD4<Float>] {
    let f = self
    var result = [SIMD4<Float>](repeating: .zero, count: 132)
    func value(_ values: [Double]) -> SIMD4<Float> {
      var vector = SIMD4<Float>.zero
      for (index, element) in values.enumerated() { vector[index] = Float(element) }
      return vector
    }
    result[0] = value([f.width, f.height])  // uSize
    result[1] = value([f.originX, f.originY])  // uOrigin
    result[2] = value([f.unit])  // uUnit
    result[3] = value([
      s.reduced
        ? 0 : s.auto && !s.paused ? s.demoTime : s.clock.truncatingRemainder(dividingBy: 6000)
    ])  // uTime
    result[4] = value([s.lamp])  // uLamp
    result[5] = value([s.beam])  // uBeam
    result[6] = value([s.under])  // uUnder
    result[7] = value([s.seam])  // uSeam
    result[8] = value([s.rise])  // uRise
    result[9] = value([s.flash])  // uFlash
    result.replaceSubrange(10..<22, with: f.flecks(yaw: s.yaw))
    result[22] = value([s.open])  // uOpen
    result[23] = value([s.hem])  // uHem
    result[24] = value([s.shiver])  // uShiver
    result[25] = value([pixelRatio])  // uPixelRatio
    result[26] = value([11, 0.15])  // uCamera
    result[27] = value([cos(s.yaw), sin(s.yaw)])  // uTurn
    result.replaceSubrange(28..<59, with: CrystalTrophyRevealCut.planes.map { SIMD4<Float>($0) })
    result.replaceSubrange(59..<77, with: CrystalTrophyRevealCut.base.map { SIMD4<Float>($0) })
    result.replaceSubrange(77..<109, with: f.rim(yaw: s.yaw, rise: s.rise))
    result.replaceSubrange(109..<117, with: f.sparkles(yaw: s.yaw, rise: s.rise, flash: s.flash))
    result[117] = value([(1 - s.rise) * 2.96])  // uDrop
    result[118] = value([s.etch])  // uEtch
    result[119] = value([s.touch])  // uTouch
    result[120] = value([s.touchX, s.touchY])  // uFinger
    result[121] = value([s.cameraScale])  // uCameraScale
    result[122] = value([s.cameraY])  // uCameraY
    result[123] = value([f.share.minX, f.share.minY, f.share.width, f.share.height])  // uShare
    result[124] = value([f.close.midX, f.close.midY])  // uClose
    result[125] = value([s.uiProgress(3.4, 0.42)])  // uControlAlpha
    result[126] = value([s.sharePress])  // uSharePress
    result[127] = value([s.closePress])  // uClosePress
    result[128] = value([s.sheen])  // uSheen
    result[129] = value([s.shareX, s.shareY])  // uSharePoint
    result[130] = value([s.closeX, s.closeY])  // uClosePoint
    result[131] = value([s.reduced ? 1 : 0])  // uReduced
    return result
  }
}
