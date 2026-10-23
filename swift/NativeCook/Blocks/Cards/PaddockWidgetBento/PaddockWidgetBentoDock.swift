import SwiftUI

private typealias Theme = PaddockWidgetBentoTheme

/// The pin's floating dock as Liquid Glass (LiquidDock.tsx): the feed refracts through the capsule, and a lighter glass
/// lens sits on the active tab. Tapping a tab springs the lens across, stretching it on the way like a drop of glass.
/// iOS 26 uses `.glassEffect()`; older systems get a dark material under a translucent fill.
struct PaddockWidgetBentoDock: View {
    static let tabs: [(tab: PaddockWidgetBentoDestination, title: String, symbol: String)] = [
        (.home, "Home", "house.fill"), (.calendar, "Calendar", "calendar"), (.results, "Results", "trophy"),
        (.circuits, "Circuits", "globe"), (.customize, "Customize", "plus.rectangle.on.rectangle"),
    ]

    let scale: CGFloat
    let tab: PaddockWidgetBentoDestination
    let quiet: Bool
    let onSelect: (PaddockWidgetBentoDestination) -> Void

    @Environment(\.displayScale) private var displayScale
    @State private var stretch = 1.0

    private var index: Int { max(0, Self.tabs.firstIndex(where: { $0.tab == tab }) ?? 0) }

    var body: some View {
        let s = scale
        let d = Theme.Layout.dock
        let pitch = (d.width - 2 * Theme.Space.dockPad) / Double(Self.tabs.count)
        let hairline = 1 / displayScale
        let capsule = RoundedRectangle(cornerRadius: Theme.Radius.dock * s, style: .continuous)
        ZStack(alignment: .topLeading) {
            glass(capsule, s: s)
            lens(s: s, pitch: pitch)
            HStack(spacing: 0) {
                ForEach(Self.tabs, id: \.tab) { item in
                    let selected = item.tab == tab
                    Button {
                        guard !selected else { return }
                        UISelectionFeedbackGenerator().selectionChanged()
                        onSelect(item.tab)
                    } label: {
                        Image(systemName: item.symbol)
                            .font(.system(size: Theme.TypeSize.dockIcon * s, weight: selected ? .semibold : .medium))
                            .foregroundStyle(selected ? Theme.Colors.dockIconActive : Theme.Colors.dockIcon)
                            .frame(width: pitch * s, height: d.height * s)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(PaddockWidgetBentoDockPressStyle(quiet: quiet))
                    .accessibilityLabel(item.title)
                    .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                }
            }
            .padding(.horizontal, Theme.Space.dockPad * s)
        }
        .frame(width: d.width * s, height: d.height * s, alignment: .topLeading)
        .clipShape(capsule)
        .overlay { capsule.strokeBorder(Theme.Colors.dockStroke, lineWidth: hairline).allowsHitTesting(false) }
        .onChange(of: index) { _, _ in lensMoved() }
    }

    @ViewBuilder private func glass(_ shape: RoundedRectangle, s: CGFloat) -> some View {
        if #available(iOS 26.0, *) {
            Color.clear.glassEffect(.clear.tint(Theme.Colors.dockGlassTint), in: shape)
        } else {
            ZStack {
                Rectangle().fill(.ultraThinMaterial).environment(\.colorScheme, .dark)
                Theme.Colors.dockFallback
            }
        }
    }

    private func lens(s: CGFloat, pitch: Double) -> some View {
        let d = Theme.Layout.dock
        let shape = RoundedRectangle(cornerRadius: Theme.Radius.lens * s, style: .continuous)
        let baseLeft = Theme.Space.dockPad + (pitch - d.lensWidth) / 2
        return LensShape(shape: shape, s: s, hairline: 1 / displayScale)
            .frame(width: d.lensWidth * s, height: d.lensHeight * s)
            .scaleEffect(x: stretch, y: 1 / stretch.squareRoot())
            .offset(x: (baseLeft + Double(index) * pitch) * s, y: Theme.Space.dockLensInset * s)
            .animation(quiet ? .timingCurve(0.23, 1, 0.32, 1, duration: 0.16) : .spring(duration: 0.42, bounce: 0.28), value: index)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    /// The lens springs across and stretches a little on the way: 1 → 1.16 in 110 ms (ease-out), then a spring back.
    private func lensMoved() {
        guard !quiet else { return }
        withAnimation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.11)) { stretch = 1.16 }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(110))
            withAnimation(.spring(duration: 0.32, bounce: 0.4)) { stretch = 1 }
        }
    }
}

private struct LensShape: View {
    let shape: RoundedRectangle
    let s: CGFloat
    let hairline: CGFloat

    var body: some View {
        ZStack {
            if #available(iOS 26.0, *) {
                Color.clear.glassEffect(.regular, in: shape)
            }
            shape.fill(PaddockWidgetBentoTheme.Colors.dockLens)
            shape.strokeBorder(PaddockWidgetBentoTheme.Colors.dockLensStroke, lineWidth: hairline)
        }
    }
}

private struct PaddockWidgetBentoDockPressStyle: ButtonStyle {
    let quiet: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !quiet ? 0.92 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.11), value: configuration.isPressed)
    }
}
