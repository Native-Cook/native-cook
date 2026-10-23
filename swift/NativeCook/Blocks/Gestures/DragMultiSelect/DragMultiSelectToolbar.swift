import SwiftUI

/// The add button and the selection toolbar are one object: entering selection, the blue circle stretches
/// into the dark toolbar (a container morph: position, size, radius and colour on one spring), the plus
/// fades out and the tools fade in one after another. Leaving runs it backwards. At rest the button breathes.
struct DragMultiSelectToolbar: View {
    let engine: DragMultiSelectEngine
    let width: Double
    let bottomInset: Double
    @State private var fabPressed = false

    private typealias Layout = DragMultiSelectLayout
    private typealias Palette = DragMultiSelectColor
    private typealias Component = DragMultiSelectTokens.Component

    private func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

    /// Reanimated's RGB interpolation linearises at gamma 2.2 and rounds back to an 8-bit channel.
    /// Matching that conversion keeps the blue-to-charcoal morph from looking too light mid-flight.
    private func gammaChannel(_ start: Double, _ end: Double, _ progress: Double) -> Double {
        let linear = lerp(pow(start, 2.2), pow(end, 2.2), progress)
        return (pow(linear, 1 / 2.2) * 255).rounded() / 255
    }

    var body: some View {
        let state = engine.state
        let reduced = engine.reduceMotion
        let selecting = state.isSelecting
        let shown = engine.shown.value
        let p = min(max(shown, 0), Component.shellOvershootLimit)
        let q = reduced ? (p > Component.reducedMorphThreshold ? 1.0 : 0.0) : p
        let b = reduced ? 0 : state.breath * (1 - min(p, 1))
        let pressScale = fabPressed ? 1 - Component.pressScale : 1.0
        // Where the shell sits as the add button, and as the toolbar.
        let fabLeft = width - Layout.gutter - Layout.fab
        let fabBottom = bottomInset + Layout.fabBottom
        let barLeft = (width - Layout.toolbarWidth) / 2
        let barBottom = bottomInset + Layout.toolbarBottom
        let shellWidth = lerp(Layout.fab, Layout.toolbarWidth, q)
        let shellHeight = lerp(Layout.fab, Layout.toolbarHeight, q)
        let radius = lerp(Layout.fab / 2, Layout.toolbarRadius, min(q, 1))
        let tint = min(min(q, 1) / Component.colorMorphEnd, 1)
        let fill = Color(
            .sRGB,
            red: gammaChannel(Palette.blueRGB.r, Palette.toolbarRGB.r, tint),
            green: gammaChannel(Palette.blueRGB.g, Palette.toolbarRGB.g, tint),
            blue: gammaChannel(Palette.blueRGB.b, Palette.toolbarRGB.b, tint)
        )
        let shape = RoundedRectangle(cornerRadius: radius, style: .circular)

        ZStack(alignment: .bottomTrailing) {
            plus(shown: shown)
            tools(shown: shown, kick: engine.trashKick.value, selecting: selecting, count: state.selectedCount)
        }
        .frame(width: shellWidth, height: shellHeight, alignment: .bottomTrailing)
        .clipShape(shape)
        .background {
            shape
                .fill(fill)
                .shadow(
                    color: .black.opacity(Component.shadowOpacity + Component.shadowBreathOpacity * b),
                    radius: Component.shadowRadius + Component.shadowBreathRadius * b,
                    x: 0,
                    y: Component.shadowY
                )
        }
        .scaleEffect((1 + Component.breathScale * b) * pressScale)
        .animation(
            .timingCurve(
                DragMultiSelectTokens.Ease.out.0,
                DragMultiSelectTokens.Ease.out.1,
                DragMultiSelectTokens.Ease.out.2,
                DragMultiSelectTokens.Ease.out.3,
                duration: (fabPressed ? Component.fabPressIn : Component.fabPressOut) / 1000
            ),
            value: fabPressed
        )
        .modifier(DragMultiSelectTouchReport(engine: engine))
        .accessibilityElement(children: selecting ? .contain : .ignore)
        .accessibilityLabel(selecting ? "Selection toolbar" : "New to-do")
        .accessibilityAddTraits(selecting ? [] : .isButton)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        .padding(.leading, lerp(fabLeft, barLeft, q))
        .padding(.bottom, lerp(fabBottom, barBottom, q))
    }

    private func plus(shown: Double) -> some View {
        Button {} label: {
            DragMultiSelectIcon(kind: .plus, size: 28, color: Palette.white)
                .frame(width: Layout.fab, height: Layout.fab)
                .contentShape(Rectangle())
                .rotationEffect(.degrees(Component.plusRotation * min(shown, 1)))
                .opacity(1 - min(shown * Component.plusFadeMultiplier, 1))
        }
        .buttonStyle(DragMultiSelectPressScaleStyle(isPressed: $fabPressed))
        .disabled(engine.state.isSelecting)
        .accessibilityLabel("New to-do")
    }

    private static let slotWidth = Component.toolbarSlot

    /// How far a tool has come in: they follow the morph one after another.
    private func progress(_ index: Int, shown: Double) -> Double {
        let start = Component.toolStart + Double(index) * DragMultiSelectTiming.toolStagger
        return min(max((shown - start) / Component.toolDuration, 0), 1)
    }

    /// Fixed at the toolbar's full width and pinned right, so the morphing shell clips the tools instead of squeezing them.
    private func tools(shown: Double, kick: Double, selecting: Bool, count: Int) -> some View {
        let none = count == 0
        return ZStack(alignment: .topLeading) {
            slot(0, shown: shown) {
                tool(.calendar, label: "When", disabled: none) { engine.leave() }
            }
            slot(1, shown: shown) {
                tool(.arrowRight, label: "Move", disabled: none) { engine.leave() }
            }
            slot(2, shown: shown, kick: kick) {
                tool(.trash, label: nil, a11y: "Delete \(count)", disabled: none) { engine.deleteSelected() }
            }
            slot(3, shown: shown) {
                tool(.moreHorizontal, label: nil, a11y: "More actions", disabled: none) { engine.leave() }
            }
        }
        .frame(width: Layout.toolbarWidth, height: Layout.toolbarHeight, alignment: .topLeading)
        .allowsHitTesting(selecting)
        .accessibilityHidden(!selecting)
    }

    /// Each tool is centred where the source puts it.
    private func slot<Content: View>(_ index: Int, shown: Double, kick: Double = 0, @ViewBuilder content: () -> Content) -> some View {
        let appeared = progress(index, shown: shown)
        return content()
            .frame(width: Self.slotWidth, height: Layout.toolbarHeight)
            .rotationEffect(.degrees(-Component.trashRotation * kick))
            .scaleEffect(1 + Component.trashScale * kick)
            .offset(y: (1 - appeared) * Component.toolTravel - Component.toolTravel * kick)
            .opacity(appeared)
            .offset(x: Layout.tools[index] - Self.slotWidth / 2)
    }

    private func tool(
        _ icon: DragMultiSelectIcon.Kind,
        label: String?,
        a11y: String? = nil,
        disabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: Component.toolGap) {
                DragMultiSelectIcon(kind: icon, size: Layout.toolIcon, color: Palette.toolbarInk)
                if let label {
                    Text(label)
                        .font(DragMultiSelectType.tool)
                        .foregroundStyle(Palette.toolbarInk)
                        .frame(height: DragMultiSelectType.toolLineHeight)
                }
            }
            .padding(.horizontal, Component.toolPadding)
            .frame(minWidth: Component.minimumHit, minHeight: Component.minimumHit)
        }
        .buttonStyle(DragMultiSelectDimStyle(pressedOpacity: Component.disabledOpacity, dimWhenDisabled: true))
        .disabled(disabled)
        .accessibilityLabel(a11y ?? label ?? "")
    }
}
