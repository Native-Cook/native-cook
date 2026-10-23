import SwiftUI

struct PullLeverRefreshCaption: View {
    let stage: PullLeverRefreshStage
    let seconds: Int
    let safeTop: CGFloat

    var body: some View {
        VStack(spacing: 0) {
            Text("ESPRESSO")
                .font(.system(size: 12, weight: .semibold))
                .tracking(1.8)
                .foregroundStyle(PullLeverRefreshTokens.eyebrow)
                .lineLimit(1)
            Text(stage.title)
                .id("title-\(stage.title)")
                .font(.system(size: 30, weight: .bold))
                .tracking(-0.6)
                .foregroundStyle(PullLeverRefreshTokens.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.82)
                .padding(.top, 8)
                .contentTransition(.numericText())
                .animation(.easeOut(duration: 0.36), value: stage)
            Text(stage.subtitle(seconds: seconds))
                .id("subtitle-\(stage.subtitle(seconds: seconds))")
                .font(.system(size: 15, weight: .regular))
                .tracking(-0.1)
                .foregroundStyle(PullLeverRefreshTokens.muted)
                .lineLimit(1)
                .padding(.top, 4)
                .contentTransition(.numericText())
                .animation(.easeOut(duration: 0.16), value: stage)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 24)
        .padding(.top, safeTop + 24)
        .frame(maxHeight: .infinity, alignment: .top)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
