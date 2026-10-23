import SwiftUI
import UIKit

struct VitalsChipMorphTabBar: View {
    let k: Double
    private typealias T = VitalsChipMorphTheme
    private let titles = ["Insights", "Longevity", "Browse"]

    var body: some View {
        let t = T.layout.tabBar
        ZStack(alignment: .topLeading) {
            glassBackground(width: t.w * k, height: t.h * k)
            RoundedRectangle(cornerRadius: T.radius.tabPill * k, style: .continuous)
                .fill(T.colors.tabActive)
                .frame(width: t.pill.w * k, height: t.pill.h * k)
                .offset(x: (t.pill.x - t.x) * k, y: (t.pill.y - t.y) * k)
            ForEach(0..<3, id: \.self) { i in
                ZStack(alignment: .topLeading) {
                    icon(i)
                        .position(x: t.pill.w / 2 * k, y: (t.iconCy - t.y) * k)
                    VitalsChipMorphText(value: titles[i], spec: T.type.tab, x: 0,
                                        capTop: t.labelCapTop - t.y, k: k,
                                        color: i == 0 ? T.colors.tabBlue : T.colors.ink,
                                        width: t.pill.w, alignment: .center)
                }
                .frame(width: t.pill.w * k, height: t.h * k, alignment: .topLeading)
                .offset(x: (t.centres[i] - t.x - t.pill.w / 2) * k)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(titles[i])
                .accessibilityAddTraits(i == 0 ? [.isButton, .isSelected] : .isButton)
            }
        }
        .frame(width: t.w * k, height: t.h * k, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: T.radius.tabBar * k, style: .continuous))
        .overlay { RoundedRectangle(cornerRadius: T.radius.tabBar * k, style: .continuous).strokeBorder(T.colors.tabRim, lineWidth: 1) }
        .shadow(color: Color(.sRGB, red: 40 / 255, green: 40 / 255, blue: 80 / 255, opacity: 0.1),
                radius: 9 * 3 * k, x: 0, y: 6 * 3 * k)
        .offset(x: t.x * k, y: t.y * k)
    }

    @ViewBuilder
    private func glassBackground(width: Double, height: Double) -> some View {
        let shape = RoundedRectangle(cornerRadius: T.radius.tabBar * k, style: .continuous)
        if #available(iOS 26, *) {
            Color.clear
                .frame(width: width, height: height)
                .glassEffect(.regular.tint(T.colors.tabGlassTint), in: shape)
        } else {
            ZStack {
                Glass()
                T.colors.tabGlass
            }
            .frame(width: width, height: height)
            .clipShape(shape)
        }
    }

    @ViewBuilder private func icon(_ index: Int) -> some View {
        switch index {
        case 0: VitalsChipMorphIcons(kind: .heart, s: 3 * k, size: 27 * 3 * k, color: T.colors.tabBlue)
        case 1: VitalsChipMorphIcons(kind: .longevity, s: 3 * k)
        default: VitalsChipMorphIcons(kind: .browse, s: 3 * k)
        }
    }

    struct Glass: UIViewRepresentable {
        func makeUIView(context: Context) -> UIVisualEffectView {
            let view = UIVisualEffectView()
            let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) {
                view.effect = UIBlurEffect(style: .light)
            }
            animator.fractionComplete = 0.28
            context.coordinator.animator = animator
            return view
        }
        func updateUIView(_ view: UIVisualEffectView, context: Context) {}
        func makeCoordinator() -> Coordinator { Coordinator() }
        final class Coordinator {
            var animator: UIViewPropertyAnimator?
            deinit { animator?.stopAnimation(true) }
        }
    }
}
