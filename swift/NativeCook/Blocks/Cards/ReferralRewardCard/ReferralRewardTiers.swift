import SwiftUI

struct ReferralRewardTiers: View {
  let model: ReferralRewardModel
  var body: some View {
    let _ = model.marksClock.frame
    Canvas { [frame = model.marksClock.frame] context, _ in
      let _ = frame
      context.drawLayer { marks in
        for i in stride(from: 2, through: 0, by: -1) {
          let x = 18 + Double(i) * 28
          let y = 18.0
          let fill = max(model.level.value - Double(i), 0)
          let disc = min(fill * 1.4, 1)
          let scale = fill <= 0 ? 1 : 0.84 + 0.16 * min(fill, 1.2)
          var c = marks
          c.translateBy(x: x, y: y)
          c.scaleBy(x: scale, y: scale)
          c.blendMode = .destinationOut
          c.fill(ReferralRewardRail.circle(0, 0, 16.5), with: .color(.white.opacity(disc)))
          c.blendMode = .normal
          c.fill(ReferralRewardRail.circle(0, 0, 15), with: .color(.white.opacity(disc)))
          c.stroke(
            ReferralRewardRail.circle(0, 0, 14.25),
            with: .color(.white.opacity(0.26 * (1 - min(fill * 2, 1)))), lineWidth: 1.5)
          c.translateBy(x: -8.4, y: -8.4 - (i == 1 ? 0 : 1))
          c.scaleBy(x: 0.7, y: 0.7)
          c.stroke(
            ReferralRewardIcons.rewards[i],
            with: .color(
              fill > 0.5
                ? Color(red: 12 / 255, green: 12 / 255, blue: 13 / 255) : .white.opacity(0.72)),
            style: .init(lineWidth: 1.5 / 0.7, lineCap: .round, lineJoin: .round))
        }
      }
      context.translateBy(x: 114, y: 18)
      context.scaleBy(x: 1 + 0.12 * model.bump.value, y: 1 + 0.12 * model.bump.value)
      context.fill(ReferralRewardRail.circle(0, 0, 15), with: .color(.white))
      context.translateBy(x: -15.6, y: -15.6)
      context.scaleBy(x: 1.3, y: 1.3)
      context.fill(
        ReferralRewardIcons.spark,
        with: .color(Color(red: 12 / 255, green: 12 / 255, blue: 13 / 255)))
    }
    .accessibilityRepresentation {
      Image(systemName: "gift").accessibilityLabel(model.rewardsLabel)
    }
    .accessibilitySortPriority(70)
  }
}
