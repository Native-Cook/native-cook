import SwiftUI
import UIKit

private typealias Colors = PulseHeartCardTheme.Colors
private typealias Layout = PulseHeartCardTheme.Layout

/// Press feedback: 0.97 in 120 ms on touch-down, back in 100 ms, ease-out (PRESS in motion.ts).
struct PulseHeartCardPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? PulseHeartCardMotion.Press.scale : 1)
            .animation(
                .timingCurve(0.23, 1, 0.32, 1, duration: configuration.isPressed ? PulseHeartCardMotion.Press.inSeconds : PulseHeartCardMotion.Press.outSeconds),
                value: configuration.isPressed
            )
    }
}

/// The selection lens as a small piece of glass: the bar and the page under it refract through a tinted
/// capsule (Liquid Glass on iOS 26, a material elsewhere), and a white rim with a sheen along its top
/// edge catches the light. Its tint keeps the pin's pill.
private struct PulseHeartCardGlassLens: View {
    let radius: Double

    var body: some View {
        let capsule = RoundedRectangle(cornerRadius: radius, style: .continuous)
        ZStack {
            if #available(iOS 26.0, *) {
                Color.clear.glassEffect(.clear.tint(PulseHeartCardTheme.Lens.glassTint), in: capsule)
            } else {
                capsule.fill(.ultraThinMaterial)
                capsule.fill(PulseHeartCardTheme.Lens.fallback)
            }
            capsule.fill(LinearGradient(
                stops: [
                    .init(color: PulseHeartCardTheme.Lens.sheen, location: 0),
                    .init(color: PulseHeartCardTheme.Lens.sheen.opacity(0), location: PulseHeartCardTheme.Lens.sheenTo),
                ],
                startPoint: .top, endPoint: .bottom
            ))
            capsule.strokeBorder(PulseHeartCardTheme.Lens.rim, lineWidth: PulseHeartCardTheme.Lens.rimWidth)
        }
        .clipShape(capsule)
    }
}

/// The pin's floating tab bar as iOS 26 Liquid Glass: the page refracts under the capsule and the
/// selection lens slides to a tapped tab on a spring, stretching as it travels. Tabs are peers, so
/// nothing else moves; the screens behind Longevity and Browse are not part of the block.
struct PulseHeartCardTabBar: View {
    let scale: Double
    /// Reduced motion or a screen reader: the lens jumps.
    let still: Bool

    private enum Tab: CaseIterable {
        case insights, longevity, browse
        var title: String {
            switch self {
            case .insights: "Insights"
            case .longevity: "Longevity"
            case .browse: "Browse"
            }
        }
    }

    @State private var active = 0
    @State private var stretch = 1.0
    @State private var stretchTask: Task<Void, Never>?

    var body: some View {
        let s = scale
        let padding = PulseHeartCardTheme.Space.tabPadding * s
        let item = Layout.tabItem * s
        let itemHeight = Layout.tabItemHeight * s
        let barRadius = Layout.tabRadius * s
        ZStack(alignment: .topLeading) {
            glass(radius: barRadius)

            // The lens sits in the padding box (inside the 1 pt border).
            PulseHeartCardGlassLens(radius: itemHeight / 2)
                .frame(width: item, height: itemHeight)
                .scaleEffect(x: stretch, y: 1)
                .offset(x: 1 + padding + Double(active) * item, y: 1 + padding)
                .allowsHitTesting(false)

            // Items are centred in the content box, which is 2 pt shorter than they are.
            HStack(spacing: 0) {
                ForEach(Array(Tab.allCases.enumerated()), id: \.offset) { index, tab in
                    tabItem(tab, index: index)
                }
            }
            .offset(x: 1 + padding, y: padding)

            Capsule().strokeBorder(Colors.tabBorder, lineWidth: 1).allowsHitTesting(false)
        }
        .frame(width: Layout.tabWidth * s, height: Layout.tabHeight * s, alignment: .topLeading)
        .clipShape(Capsule())
        .shadow(color: Colors.shadow, radius: 6 * s, x: 0, y: 5 * s)
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isTabBar)
    }

    @ViewBuilder
    private func glass(radius: Double) -> some View {
        if #available(iOS 26.0, *) {
            Color.clear.glassEffect(.regular.tint(Colors.tabGlassTint), in: Capsule())
        } else {
            ZStack {
                Capsule().fill(.ultraThinMaterial)
                Capsule().fill(Colors.tabBar)
            }
        }
    }

    private func tabItem(_ tab: Tab, index: Int) -> some View {
        let s = scale
        let selected = index == active
        let tint = selected ? Colors.activeBlue : Colors.ink
        let item = Layout.tabItem * s
        let icon = Layout.tabIcon * s
        return Button {
            select(index)
        } label: {
            ZStack(alignment: .topLeading) {
                Group {
                    switch tab {
                    case .insights: PulseHeartCardHeartIcon(size: icon, color: tint)
                    case .longevity: PulseHeartCardApertureIcon(size: icon, color: tint)
                    case .browse: PulseHeartCardBrowseIcon(size: icon, color: tint)
                    }
                }
                .offset(x: (item - icon) / 2, y: (Layout.tabIconCentre - Layout.tabIcon / 2) * s)
                Text(tab.title)
                    .font(.system(size: Layout.tabLabelSize * s, weight: .semibold))
                    .foregroundStyle(tint)
                    .lineLimit(1)
                    .frame(width: item, height: Layout.tabLabelLine * s)
                    .offset(y: Layout.tabLabelTop * s)
            }
            .frame(width: item, height: Layout.tabItemHeight * s, alignment: .topLeading)
            .contentShape(Rectangle().inset(by: -4))
        }
        .buttonStyle(PulseHeartCardPressStyle())
        .accessibilityLabel(tab.title)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }

    private func select(_ index: Int) {
        guard index != active else { return }
        if still {
            var transaction = Transaction()
            transaction.disablesAnimations = true
            withTransaction(transaction) { active = index }
        } else {
            withAnimation(PulseHeartCardTheme.TabMotion.lens) { active = index }
            stretchTask?.cancel()
            withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: PulseHeartCardTheme.TabMotion.stretchSeconds)) {
                stretch = PulseHeartCardTheme.TabMotion.stretch
            }
            stretchTask = Task { @MainActor in
                try? await Task.sleep(for: .seconds(PulseHeartCardTheme.TabMotion.stretchSeconds))
                guard !Task.isCancelled else { return }
                withAnimation(PulseHeartCardTheme.TabMotion.settle) { stretch = 1 }
            }
        }
        UISelectionFeedbackGenerator().selectionChanged()
    }
}
