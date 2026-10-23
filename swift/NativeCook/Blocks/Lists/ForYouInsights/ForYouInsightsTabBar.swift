import SwiftUI

/// Liquid Glass tab bar (iOS 26, a material before); the selection lens slides on a spring and stretches while it travels.
struct ForYouInsightsTabBar: View {
    typealias T = ForYouInsightsTheme
    typealias L = ForYouInsightsTheme.Layout
    typealias M = ForYouInsightsMotion
    var k: Double
    var section: ForYouInsightsSection
    var lens: Double
    var onSelect: (ForYouInsightsSection) -> Void

    private static let tabs: [(ForYouInsightsSection, String, String)] = [
        (.insights, "Insights", "heart.fill"),
        (.longevity, "Longevity", "camera.aperture"),
        (.browse, "Browse", "square.grid.2x2.fill"),
    ]

    var body: some View {
        let width = L.tabBarWidth * k, height = L.tabBarHeight * k
        let pitch = L.tabPitch * k
        let pillW = L.tabPillWidth * k, pillH = L.tabPillHeight * k
        let travel = M.clamp01(abs(lens - lens.rounded()) * 2)
        let shape = RoundedRectangle(cornerRadius: T.Radius.tabBar * k, style: .continuous)
        ZStack(alignment: .topLeading) {
            glass(shape, width: width, height: height)
            RoundedRectangle(cornerRadius: T.Radius.tabPill * k, style: .continuous)
                .fill(T.Colors.pillFill)
                .overlay { RoundedRectangle(cornerRadius: T.Radius.tabPill * k, style: .continuous).strokeBorder(T.Colors.pillEdge, lineWidth: 0.5) }
                .frame(width: pillW, height: pillH)
                .scaleEffect(x: 1 + 0.16 * travel, y: 1 - 0.06 * travel)
                .offset(x: width / 2 - pillW / 2 + (lens - 1) * pitch, y: (height - pillH) / 2)
                .allowsHitTesting(false)
            ForEach(0..<3, id: \.self) { i in
                let tab = Self.tabs[i]
                let selected = tab.0 == section
                let tint = selected ? T.Colors.blue : T.Colors.ink
                Button { onSelect(tab.0) } label: {
                    ZStack(alignment: .top) {
                        Image(systemName: tab.2)
                            .font(.system(size: L.tabIcon * k * 0.9))
                            .foregroundStyle(tint)
                            .frame(width: L.tabIcon * k * 1.2, height: L.tabIcon * k)
                            .offset(y: (L.tabIconCenter - L.tabIcon / 2) * k)
                        Text(tab.1)
                            .font(.system(size: T.Typo.tab.size * k, weight: T.Typo.tab.weight.font))
                            .foregroundStyle(tint)
                            .frame(height: 14 * k)
                            .offset(y: (L.tabLabelCenter - 7) * k)
                    }
                    .frame(width: pillW, height: height, alignment: .top)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(tab.1)
                .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                .offset(x: width / 2 + Double(i - 1) * pitch - pillW / 2)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .shadow(color: T.Colors.shadow.opacity(0.12), radius: 18 * k, y: 8 * k)
    }

    @ViewBuilder private func glass(_ shape: RoundedRectangle, width: Double, height: Double) -> some View {
        if #available(iOS 26, *) {
            Color.clear
                .frame(width: width, height: height)
                .glassEffect(.regular.tint(T.Colors.tabGlassTint), in: shape)
        } else {
            ZStack {
                Rectangle().fill(.ultraThinMaterial)
                T.Colors.tabFallback
            }
            .frame(width: width, height: height)
            .clipShape(shape)
            .overlay { shape.strokeBorder(T.Colors.tabEdge, lineWidth: 0.5) }
        }
    }
}
