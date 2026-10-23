import SwiftUI

struct VitalsChipMorphChips: View {
    let blend: VitalsChipMorphMotion.Blend
    let quiet: Bool
    let k: Double
    let onSelect: (Int) -> Void
    private typealias T = VitalsChipMorphTheme
    private typealias M = VitalsChipMorphMotion
    @Environment(\.displayScale) private var displayScale
    private var glassAvailable: Bool {
        if #available(iOS 26, *) { return true }
        return false
    }

    var body: some View {
        let a = T.chips[T.SCREEN_CHIP[blend.lo]], b = T.chips[T.SCREEN_CHIP[blend.hi]]
        let lead = quiet ? (blend.u < 0.5 ? 0.0 : 1.0) : M.easeInOut(M.seg(blend.u, 0, 0.7))
        let trail = quiet ? lead : M.easeInOut(M.seg(blend.u, 0.15, 0.85))
        let left = M.lerp(a.x, b.x, b.x > a.x ? trail : lead)
        let right = M.lerp(a.x + a.w, b.x + b.w, b.x > a.x ? lead : trail)
        let e = quiet ? (blend.u < 0.5 ? 0.0 : 1.0) : M.easeInOut(M.seg(blend.u, 0, 0.85))
        let row = M.lerp(T.layout.rowOffset[blend.lo], T.layout.rowOffset[blend.hi], e)
        let travel = !glassAvailable || blend.lo == blend.hi || quiet ? 0 : M.bump(blend.u, 0, 1)
        let lensVisible = !quiet && blend.lo != blend.hi
            && blend.u > T.lens.onFrom && blend.u < T.lens.offAt
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: T.radius.chip * k, style: .continuous)
                .fill(T.colors.chipActive)
                .frame(width: (right - left) * k, height: T.layout.chipH * k)
                .offset(x: left * k)
                .opacity(1 - 0.8 * travel)
            chipLens(
                width: (right - left) * k,
                left: left * k,
                scale: 1 + (T.lens.liftScale - 1) * travel,
                visible: lensVisible
            )
            ForEach(T.chips.indices, id: \.self) { index in
                chip(index)
            }
        }
        .frame(width: 2400 * k, height: T.layout.chipH * k, alignment: .topLeading)
        .offset(x: row * k, y: T.layout.chipTop * k)
    }

    @ViewBuilder
    private func chipLens(width: Double, left: Double, scale: Double, visible: Bool) -> some View {
        let shape = RoundedRectangle(cornerRadius: T.radius.chip * k, style: .continuous)
        if #available(iOS 26, *) {
            Color.clear
                .frame(width: width, height: T.layout.chipH * k)
                .glassEffect(.clear.tint(T.lens.tint), in: shape)
                .scaleEffect(scale)
                .offset(x: left)
                .opacity(visible ? 1 : 0)
                .animation(.easeInOut(duration: T.lens.fade), value: visible)
        }
    }

    private func chip(_ index: Int) -> some View {
        let spec = T.chips[index]
        let screen = T.SCREEN_CHIP.firstIndex(of: index) ?? -1
        let w = M.weight(screen, blend.lo, blend.hi, blend.u)
        let autoPress = blend.pressChip == index ? blend.press : 0
        let toward = blend.target == blend.hi ? blend.u : 1 - blend.u
        let pop = quiet || screen < 0 || blend.lo == blend.hi || blend.target != screen
            ? 1 : 1 + 0.12 * M.bump(toward, 0.5, 0.95)
        return Button { onSelect(index) } label: {
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: T.radius.chip * k, style: .continuous)
                    .fill(T.colors.chip)
                    .overlay {
                        RoundedRectangle(cornerRadius: T.radius.chip * k, style: .continuous)
                            .strokeBorder(T.colors.chipBorder, lineWidth: 2 / displayScale)
                    }
                    .opacity(1 - w)
                if index == 0 {
                    icon(spec.key).frame(width: spec.w * k, height: T.layout.chipH * k)
                } else {
                    icon(spec.key)
                        .scaleEffect(pop)
                        .position(x: T.space.chipIconCentre * k, y: T.layout.chipH / 2 * k)
                    VitalsChipMorphText(value: spec.title, spec: T.type.chipTitle,
                                        x: T.space.chipLabel, capTop: T.space.chipTitleCap, k: k, tabularNumbers: false)
                    VitalsChipMorphText(value: spec.value, spec: T.type.chipValue,
                                        x: T.space.chipLabel, capTop: T.space.chipValueCap, k: k, tabularNumbers: false)
                }
            }
            .frame(width: spec.w * k, height: T.layout.chipH * k, alignment: .topLeading)
            .contentShape(.rect.inset(by: -4))
        }
        .buttonStyle(PressStyle())
        .scaleEffect(1 - 0.03 * autoPress)
        .offset(x: spec.x * k)
        .accessibilityLabel(spec.value.isEmpty ? "Summary" : "\(spec.title), \(spec.value)")
        .accessibilityHint(screen >= 0 ? "" : "Shows \(T.SCREEN_NAMES[T.CHIP_SCREEN[index]])")
    }

    @ViewBuilder private func icon(_ key: String) -> some View {
        switch key {
        case "summary": VitalsChipMorphIcons(kind: .summary, s: 3 * k)
        case "sleep": VitalsChipMorphIcons(kind: .sleep, s: 3 * k)
        case "readiness": VitalsChipMorphIcons(kind: .readiness, s: 3 * k)
        case "fitness": VitalsChipMorphIcons(kind: .fitness, s: 3 * k)
        case "heart": VitalsChipMorphIcons(kind: .heart, s: 3 * k, size: 31 * 3 * k)
        default: VitalsChipMorphIcons(kind: .vitals, s: 3 * k)
        }
    }

    struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
                .animation(.timingCurve(0, 0, 0.58, 1, duration: configuration.isPressed ? 0.09 : 0.16),
                           value: configuration.isPressed)
        }
    }
}
