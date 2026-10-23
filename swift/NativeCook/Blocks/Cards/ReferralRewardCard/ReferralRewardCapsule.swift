import SwiftUI

struct ReferralRewardCapsule: View {
  let model: ReferralRewardModel
  var body: some View {
    let _ = model.railClock.frame
    let l = model.layout
    let peek = model.peek.value
    let shift = model.reduced ? 0 : model.capsuleShift
    let copyWidth = model.capsuleCopyWidth
    let statusHeight = model.capsuleStatusHeight
    let height = model.capsuleCopyHeight
    let nameP = ReferralRewardMath.clamp((peek - 0.35) / 0.4)
    let statusP = ReferralRewardMath.clamp((peek - 0.42) / 0.4)
    ZStack(alignment: .topLeading) {
      VStack(alignment: .leading, spacing: 4) {
        Text(ReferralRewardFriend.all[model.capsuleIndex].name)
          .font(.system(size: 17 * l.fontScale, weight: .semibold)).lineLimit(1)
          .frame(height: 22 * l.fontScale).opacity(nameP)
          .blur(radius: model.reduced ? 0 : 4 * (1 - nameP)).offset(
            x: model.reduced ? 0 : 10 * (1 - nameP))
        ReferralRewardText(
          text: model.capsuleStatus, size: 13 * l.fontScale, weight: .regular,
          line: 20 * l.fontScale
        )
        .lineLimit(2).foregroundStyle(.white.opacity(0.72)).frame(height: statusHeight)
        .opacity(statusP).blur(radius: model.reduced ? 0 : 4 * (1 - statusP)).offset(
          x: model.reduced ? 0 : 10 * (1 - statusP))
      }
      .frame(width: copyWidth, height: height, alignment: .leading)
      .frame(width: max(copyWidth - 2 * shift, 0), height: height, alignment: .leading).clipped()
      .position(x: 114 + shift + max(copyWidth - 2 * shift, 0) / 2, y: 112)
      .accessibilityRepresentation {
        if model.peekOpen { Text(model.capsuleDescription) }
      }
      .accessibilityHidden(!model.peekOpen)
      .accessibilitySortPriority(50)
      if !model.capsuleJoined { action }
    }.foregroundStyle(.white).frame(width: l.width, height: 212, alignment: .topLeading)
      .allowsHitTesting(false)
  }
  private var action: some View {
    let l = model.layout
    let launching = !model.reduced && model.capsuleLaunching >= 0
    let at = launching ? model.capsuleLaunching : model.peek.value
    let arrived = ReferralRewardMath.clamp((at - 0.5) / 0.4)
    let shape = launching ? model.capsuleShape.value : 0
    let p = launching ? arrived + (1 - arrived) * shape : arrived
    let fade = launching ? model.capsuleLabel.value : 0
    let shift = model.reduced ? 0 : (l.width / 2 - 66) * (1 - at)
    return ZStack {
      RoundedRectangle(cornerRadius: max(22 + (6 - 22) * shape, 0), style: .circular).fill(.white)
        .frame(width: l.actionWidth + (12 - l.actionWidth) * shape, height: 44 + (12 - 44) * shape)
        .opacity(shape >= 1 ? 0 : 1)
      Text(ReferralRewardL10n.t("invite"))
        .font(.system(size: 14 * l.fontScale, weight: .semibold))
        .foregroundStyle(Color(red: 12 / 255, green: 12 / 255, blue: 13 / 255))
        .opacity(1 - fade).blur(radius: model.reduced ? 0 : 4 * fade)
    }.frame(width: l.actionWidth, height: 44)
      .opacity(p).blur(radius: model.reduced ? 0 : 4 * (1 - p))
      .scaleEffect((model.reduced ? 1 : 0.92 + 0.08 * p) * (1 - 0.04 * model.actionPress.value))
      .position(x: l.actionX - shift, y: 112)
      .accessibilityRepresentation {
        if model.peekOpen {
          Button(model.capsuleActionLabel) {
            model.touch()
            model.invite(selected: model.capsuleIndex)
          }
        }
      }
      .accessibilityHidden(!model.peekOpen)
      .accessibilitySortPriority(49)
  }
}
