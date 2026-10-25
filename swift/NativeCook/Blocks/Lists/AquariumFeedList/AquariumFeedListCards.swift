import SwiftUI
import UIKit

/// One log entry: a pane of frosted glass in the water (fish swim behind it), an icon tile, the
/// title and its detail, the time and a tag. Only the fed card's glow and press change; every other
/// card compares equal and is skipped.
struct AquariumFeedListCard: View, Equatable {
    let index: Int
    /// Feeding glow (0–1) and press pulse (0–1).
    let on: Double
    let press: Double
    let onPress: (Int) -> Void

    static func == (a: Self, b: Self) -> Bool { a.index == b.index && a.on == b.on && a.press == b.press }

    private typealias S = AquariumFeedListScene

    private static func mix(_ a: (Double, Double, Double, Double), _ b: (Double, Double, Double, Double), _ t: Double) -> Color {
        Color(.sRGB,
              red: (a.0 + (b.0 - a.0) * t) / 255,
              green: (a.1 + (b.1 - a.1) * t) / 255,
              blue: (a.2 + (b.2 - a.2) * t) / 255,
              opacity: a.3 + (b.3 - a.3) * t)
    }

    var body: some View {
        let e = S.entries[index]
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        HStack(spacing: 14) {
            Image(systemName: e.symbol)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(S.color(e.tint))
                .frame(width: 44, height: 44)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(S.color(e.tint, 0x26 / 255)))
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(e.title)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(S.color(0xF0FBFC))
                        .lineLimit(1)
                        .layoutPriority(0)
                    Spacer(minLength: 0)
                    Text(e.when)
                        .font(.system(size: 12, weight: .semibold).monospacedDigit())
                        .foregroundStyle(Color(.sRGB, red: 222 / 255, green: 246 / 255, blue: 250 / 255, opacity: 0.55))
                        .lineLimit(1)
                        .fixedSize()
                        .layoutPriority(1)
                }
                Text(e.detail)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color(.sRGB, red: 222 / 255, green: 246 / 255, blue: 250 / 255, opacity: 0.78))
                    .lineLimit(1)
                Text(e.tag.uppercased())
                    .font(.system(size: 10, weight: .heavy))
                    .kerning(1.4)
                    .foregroundStyle(S.color(e.tint))
                    .padding(.top, 3)
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .frame(height: S.cardH)
        .frame(maxWidth: .infinity)
        .background {
            ZStack {
                // Frosted glass: the fish behind it blur and dim; a still blur, never animated.
                shape.fill(.ultraThinMaterial).environment(\.colorScheme, .dark)
                shape.fill(Self.mix((8, 46, 60, 0.5), (42, 76, 64, 0.56), on))
                // The glass's lit top edge and a darker bottom lip.
                Rectangle()
                    .fill(Color(.sRGB, red: 225 / 255, green: 250 / 255, blue: 255 / 255, opacity: 0.42))
                    .frame(height: 1)
                    .padding(.horizontal, 14)
                    .frame(maxHeight: .infinity, alignment: .top)
                Rectangle()
                    .fill(Color(.sRGB, red: 0, green: 20 / 255, blue: 30 / 255, opacity: 0.25))
                    .frame(height: 1)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
            .clipShape(shape)
        }
        .overlay(shape.strokeBorder(Self.mix((220, 248, 255, 0.16), (255, 214, 128, 0.7), on), lineWidth: 1))
        .background(
            shape
                .fill(Color(.sRGB, red: 1 / 255, green: 16 / 255, blue: 24 / 255, opacity: 1))
                .shadow(color: Color(.sRGB, red: 1 / 255, green: 16 / 255, blue: 24 / 255, opacity: 0.3), radius: 1, y: 1)
                .shadow(color: Color(.sRGB, red: 1 / 255, green: 16 / 255, blue: 24 / 255, opacity: 0.28), radius: 15, y: 14)
                .mask(AquariumFeedListOutside(radius: 22).fill(style: FillStyle(eoFill: true)))
        )
        .scaleEffect(1 - 0.025 * press)
        .contentShape(shape)
        .onTapGesture { onPress(index) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(e.title), \(e.detail), \(e.when)")
        .accessibilityAddTraits(.isButton)
    }
}

/// Everything around a card but the card itself, so its drop shadow never darkens its glass.
struct AquariumFeedListOutside: Shape {
    let radius: Double

    func path(in rect: CGRect) -> Path {
        var p = Path(rect.insetBy(dx: -80, dy: -80))
        p.addRoundedRect(in: rect, cornerSize: CGSize(width: radius, height: radius), style: .continuous)
        return p
    }
}

/// The log: twelve cards in a column.
struct AquariumFeedListCards: View {
    let top: Double
    let bottom: Double
    let focus: AquariumFeedListFocus
    let onPress: (Int) -> Void

    var body: some View {
        VStack(spacing: AquariumFeedListScene.cardGap) {
            ForEach(AquariumFeedListScene.entries.indices, id: \.self) { i in
                AquariumFeedListCard(
                    index: i,
                    on: focus.card == i ? focus.amt : 0,
                    press: focus.card == i ? focus.press : 0,
                    onPress: onPress
                )
                .equatable()
            }
        }
        .padding(.top, top)
        .padding(.bottom, bottom)
        .padding(.horizontal, 16)
    }

    static func height(top: Double, bottom: Double) -> Double {
        let n = Double(AquariumFeedListScene.entries.count)
        return top + bottom + n * AquariumFeedListScene.cardH + (n - 1) * AquariumFeedListScene.cardGap
    }
}

/// A UIScrollView holding the cards, so the loop can move the list to an exact offset every frame
/// (SwiftUI's ScrollView cannot on iOS 17). Its scroll and drag events go to the engine.
struct AquariumFeedListScroll: UIViewRepresentable {
    let engine: AquariumFeedListEngine
    let width: Double
    let top: Double
    let bottom: Double
    let focus: AquariumFeedListFocus

    func makeCoordinator() -> Coordinator { Coordinator(engine: engine) }

    func makeUIView(context: Context) -> UIScrollView {
        let sv = UIScrollView()
        sv.delegate = context.coordinator
        sv.showsVerticalScrollIndicator = false
        sv.showsHorizontalScrollIndicator = false
        sv.alwaysBounceVertical = true
        sv.contentInsetAdjustmentBehavior = .never
        sv.backgroundColor = .clear
        let host = UIHostingController(rootView: cards)
        host.view.backgroundColor = .clear
        host.safeAreaRegions = []
        sv.addSubview(host.view)
        context.coordinator.host = host
        context.coordinator.focus = focus
        context.coordinator.size = CGSize(width: top, height: bottom)
        engine.scrollView = sv
        layout(sv, host)
        return sv
    }

    func updateUIView(_ sv: UIScrollView, context: Context) {
        guard let host = context.coordinator.host else { return }
        let key = CGSize(width: top, height: bottom)
        if context.coordinator.focus != focus || context.coordinator.size != key {
            context.coordinator.focus = focus
            context.coordinator.size = key
            host.rootView = cards
        }
        layout(sv, host)
    }

    private var cards: AquariumFeedListCards {
        let engine = engine
        return AquariumFeedListCards(top: top, bottom: bottom, focus: focus) { i in engine.tapCard(i) }
    }

    private func layout(_ sv: UIScrollView, _ host: UIHostingController<AquariumFeedListCards>) {
        let h = AquariumFeedListCards.height(top: top, bottom: bottom)
        let frame = CGRect(x: 0, y: 0, width: width, height: h)
        if host.view.frame != frame { host.view.frame = frame }
        if sv.contentSize != frame.size { sv.contentSize = frame.size }
    }

    @MainActor
    final class Coordinator: NSObject, UIScrollViewDelegate {
        let engine: AquariumFeedListEngine
        var host: UIHostingController<AquariumFeedListCards>?
        var focus = AquariumFeedListFocus()
        var size = CGSize.zero

        init(engine: AquariumFeedListEngine) { self.engine = engine }

        func scrollViewDidScroll(_ scrollView: UIScrollView) { engine.scrolled(to: scrollView.contentOffset.y) }
        func scrollViewWillBeginDragging(_ scrollView: UIScrollView) { engine.beganDrag() }
        func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
            engine.endedDrag(decelerating: decelerate)
        }
        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) { engine.endedDeceleration() }
    }
}
