import SwiftUI

/// Scratch storage is retained; sampling a ribbon never creates temporary arrays.
@MainActor final class ReferralRewardRibbon {
  private var sampleX = [Double](repeating: 0, count: 41)
  private var sampleY = [Double](repeating: 0, count: 41)
  private var radii = [Double](repeating: 0, count: 41)
  private var normalX = [Double](repeating: 0, count: 41)
  private var normalY = [Double](repeating: 0, count: 41)
  enum Route {
    case referral(ReferralRewardPath)
    case reward(from: ReferralRewardPoint, to: ReferralRewardPoint)

    func point(at progress: Double) -> ReferralRewardPoint {
      switch self {
      case .referral(let path):
        return path.at(progress)
      case .reward(let from, let to):
        let weight = ReferralRewardMath.inOut(progress)
        let remaining = 1 - weight
        return .init(
          x: remaining * remaining * from.x + 2 * remaining * weight * from.x + weight * weight
            * to.x,
          y: remaining * remaining * from.y + 2 * remaining * weight * (to.y + 18) + weight * weight
            * to.y)
      }
    }
  }

  // A value route avoids allocating a method-reference closure (or a box for
  // captured endpoints) each time SwiftUI asks for the flight's display list.
  func path(_ progress: Double, initialRadius: Double, route: Route) -> Path {
    var outline = Path()
    var sampleCount = 0
    for sampleIndex in 0...40 {
      let sampleProgress = progress - Double(sampleIndex) * 0.005
      if sampleProgress < 0 || sampleProgress >= 1 { continue }
      let point = route.point(at: sampleProgress)
      let reach = min(40, max(progress / 0.005, 1))
      let taper = pow(max(1 - Double(sampleIndex) / reach, 0), 1.3)
      let born = min(progress / 0.06, 1)
      sampleX[sampleCount] = point.x
      sampleY[sampleCount] = point.y
      radii[sampleCount] =
        (initialRadius * (1 - born) + 3.2 * born) * taper
        * (sampleProgress > 0.9 ? (1 - sampleProgress) / 0.1 : 1)
      sampleCount += 1
    }
    if sampleCount < 2 {
      if sampleCount == 1 && initialRadius > 0 && radii[0] > 0 {
        return ReferralRewardRail.circle(sampleX[0], sampleY[0], radii[0])
      }
      return outline
    }
    for i in 0..<sampleCount {
      let a = max(i - 1, 0)
      let b = min(i + 1, sampleCount - 1)
      let dx = sampleX[a] - sampleX[b]
      let dy = sampleY[a] - sampleY[b]
      let len = max(hypot(dx, dy), Double.leastNonzeroMagnitude)
      normalX[i] = -dy / len
      normalY[i] = dx / len
    }
    outline.move(
      to: CGPoint(x: sampleX[0] + normalX[0] * radii[0], y: sampleY[0] + normalY[0] * radii[0]))
    for i in 1..<sampleCount {
      outline.addLine(
        to: CGPoint(x: sampleX[i] + normalX[i] * radii[i], y: sampleY[i] + normalY[i] * radii[i]))
    }
    for i in stride(from: sampleCount - 1, through: 0, by: -1) {
      outline.addLine(
        to: CGPoint(x: sampleX[i] - normalX[i] * radii[i], y: sampleY[i] - normalY[i] * radii[i]))
    }
    for j in 1..<6 {
      let phi = Double(j) * Double.pi / 6
      let c = cos(phi)
      let s = sin(phi)
      outline.addLine(
        to: CGPoint(
          x: sampleX[0] + radii[0] * (-normalX[0] * c + normalY[0] * s),
          y: sampleY[0] + radii[0] * (-normalY[0] * c - normalX[0] * s)))
    }
    outline.closeSubpath()
    return outline
  }
}

struct ReferralRewardFlight: View {
  let model: ReferralRewardModel
  @State private var ribbon = ReferralRewardRibbon()
  var body: some View {
    let _ = model.flightClock.frame
    Canvas { [frame = model.flightClock.frame] context, _ in
      let _ = frame
      if let route = model.path, model.comet.value < 1.2 {
        let progress = model.comet.value
        let shape = model.fromCapsule ? model.capsuleShape.value : model.shape.value
        if progress < 0 {
          if shape >= 1 {
            let origin = route.samples[0]
            draw(ReferralRewardRail.circle(origin.x, origin.y, 6), context: &context)
          }
        } else {
          draw(ribbon.path(progress, initialRadius: 6, route: .referral(route)), context: &context)
        }
      }
      let progress = model.flight.value
      let layout = model.layout
      let from = ReferralRewardPoint(x: layout.width / 2, y: layout.lensY - 75)
      let to = ReferralRewardPoint(
        x: layout.markX + 15 + Double(model.rewardIndex) * 28, y: layout.markY)
      let landed = ReferralRewardMath.clamp((progress - 1) / 0.2)
      if landed > 0 && landed < 1 {
        context.stroke(
          ReferralRewardRail.circle(to.x, to.y, 15 + 13 * ReferralRewardMath.out(landed)),
          with: .color(.white.opacity(0.55 * pow(1 - landed, 1.4))), lineWidth: 1.25)
      }
      if progress > 0 && progress < 1.2 {
        let outline = ribbon.path(progress, initialRadius: 0, route: .reward(from: from, to: to))
        draw(outline, context: &context)
      }
    }.accessibilityHidden(true)
  }
  private func draw(_ path: Path, context: inout GraphicsContext) {
    guard !path.isEmpty else { return }
    context.drawLayer { glow in
      glow.addFilter(.blur(radius: 5))
      glow.fill(path, with: .color(model.tint))
    }
    context.fill(path, with: .color(.white))
  }
}
