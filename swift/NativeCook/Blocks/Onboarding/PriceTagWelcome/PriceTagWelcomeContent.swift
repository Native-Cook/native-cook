import SwiftUI

struct PriceTagWelcomeContent: View {
    let size: CGSize
    let frame: PriceTagWelcomeMotion.Frame
    let reduced: Bool
    let onWake: () -> Void
    let onDown: (Double, Double, Double) -> Void
    let onBeginDrag: (Double) -> Void
    let onMoveDrag: (Double, Double) -> Void
    let onEndDrag: (Double, Double) -> Void
    let onTap: (Double, Double, Double) -> Void
    let onCancel: (Double) -> Void
    let onAccessibilityToggle: (Int) -> Void
    let onButton: () -> Void

    private var unit: Double { size.width / PriceTagWelcomeTheme.referenceWidth }
    private var fontScale: Double { size.width / PriceTagWelcomeTheme.referencePoints }
    /// Bit i: item i is on screen, so its frost canvases are mounted.
    private var mounted: Int { PriceTagWelcomeMotion.visibleMask(offset: frame.x) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            PriceTagWelcomeBackdrop(size: size, unit: unit)
            cardIcon
            copy
            conveyor
            getStarted
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private var cardIcon: some View {
        let icon = PriceTagWelcomeTheme.Layout.icon
        let box = PriceTagWelcomeTheme.Layout.iconBox
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: PriceTagWelcomeTheme.iconRadius * unit, style: .continuous)
                .fill(Color(red: 0xB1 / 255, green: 0xE5 / 255, blue: 0x89 / 255))
                .shadow(color: .black.opacity(0.06), radius: 4 * unit, x: 0, y: 2 * unit)
                .shadow(color: .black.opacity(0.07), radius: 14 * unit, x: 0, y: 6 * unit)
                .frame(width: box.width * unit, height: box.height * unit)
                .position(x: box.midX * unit, y: box.midY * unit)

            Image(uiImage: PriceTagWelcomeAssets.image("card-icon"))
                .resizable()
                .interpolation(.high)
                .frame(width: icon.width * unit, height: icon.height * unit)
                .position(x: icon.midX * unit, y: icon.midY * unit)
                .accessibilityIgnoresInvertColors()
                .accessibilityHidden(true)

            PriceTagWelcomeGlint(frame: frame, reduced: reduced, unit: unit)
        }
    }

    private var copy: some View {
        ZStack(alignment: .topLeading) {
            baselineText(
                PriceTagWelcomeTheme.Copy.headline[0],
                style: PriceTagWelcomeTheme.Typography.headline,
                baseline: PriceTagWelcomeTheme.Layout.headlineBaselines[0]
            )
            .accessibilityAddTraits(.isHeader)
            baselineText(
                PriceTagWelcomeTheme.Copy.headline[1],
                style: PriceTagWelcomeTheme.Typography.headline,
                baseline: PriceTagWelcomeTheme.Layout.headlineBaselines[1]
            )
            baselineText(
                PriceTagWelcomeTheme.Copy.sub[0],
                style: PriceTagWelcomeTheme.Typography.sub,
                baseline: PriceTagWelcomeTheme.Layout.subBaselines[0]
            )
            baselineText(
                PriceTagWelcomeTheme.Copy.sub[1],
                style: PriceTagWelcomeTheme.Typography.sub,
                baseline: PriceTagWelcomeTheme.Layout.subBaselines[1]
            )
        }
    }

    private func baselineText(_ value: String, style: PriceTagWelcomeTheme.TextStyle,
                              baseline: Double) -> some View {
        PriceTagWelcomeBaselineLayout(baseline: baseline * unit) {
            Text(value)
                .font(.system(size: style.size * fontScale, weight: style.weight))
                .tracking(style.letterSpacing * fontScale)
                .foregroundStyle(style.color)
                .fixedSize()
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private var conveyor: some View {
        let rowHeight = (PriceTagWelcomeTheme.rowBottom - PriceTagWelcomeTheme.rowTop) * unit
        return ZStack(alignment: .topLeading) {
            ForEach(Array(PriceTagWelcomeTheme.items.enumerated()), id: \.element.id) { index, item in
                PriceTagWelcomeSticker(
                    item: item, index: index, frame: frame, reduced: reduced,
                    mounted: mounted & (1 << index) != 0, unit: unit, fontScale: fontScale
                )
            }
            ForEach(Array(PriceTagWelcomeTheme.items.enumerated()), id: \.element.id) { index, item in
                PriceTagWelcomeTag(
                    item: item,
                    index: index,
                    frame: frame,
                    reduced: reduced,
                    mounted: mounted & (1 << index) != 0,
                    unit: unit,
                    fontScale: fontScale
                )
                .accessibilityHidden(true)
            }
            ForEach(Array(PriceTagWelcomeTheme.items.enumerated()), id: \.element.id) { index, item in
                let originX = PriceTagWelcomeMotion.wrapX(restX: item.x, offset: frame.x)
                let frosted = frame.freezeTime[index] >= 0
                Button {
                    onAccessibilityToggle(index)
                } label: {
                    Color.clear
                }
                .buttonStyle(.plain)
                .frame(width: item.width * unit, height: item.height * unit)
                .position(
                    x: (originX + item.width / 2) * unit,
                    y: (item.y - PriceTagWelcomeTheme.rowTop + item.height / 2) * unit
                )
                .accessibilityLabel(
                    frosted
                        ? "\(item.name), \(item.price), \(PriceTagWelcomeTheme.Copy.frozen)"
                        : "\(item.name), \(item.price)"
                )
                .accessibilityHint(PriceTagWelcomeTheme.Copy.itemHint)
            }
            PriceTagWelcomeTouch(
                scale: unit,
                onDown: { x, y, time in onWake(); onDown(x, y, time) },
                onBeginDrag: { time in onWake(); onBeginDrag(time) },
                onMoveDrag: { translation, time in onWake(); onMoveDrag(translation, time) },
                onEndDrag: { velocity, time in onWake(); onEndDrag(velocity, time) },
                onTap: { x, y, time in onWake(); onTap(x, y, time) },
                onCancel: { time in onWake(); onCancel(time) }
            )
            .frame(width: size.width, height: rowHeight)
        }
        .frame(width: size.width, height: rowHeight, alignment: .topLeading)
        .position(x: size.width / 2, y: PriceTagWelcomeTheme.rowTop * unit + rowHeight / 2)
    }

    private var getStarted: some View {
        let width = PriceTagWelcomeTheme.Layout.buttonWidth * unit
        let height = PriceTagWelcomeTheme.Layout.buttonHeight * unit
        let top = size.height - PriceTagWelcomeTheme.Layout.buttonFromBottom * unit
        return Button(action: onButton) {
            PriceTagWelcomeBaselineLayout(
                baseline: PriceTagWelcomeTheme.Layout.buttonBaseline * unit
            ) {
                Text(PriceTagWelcomeTheme.Copy.button)
                    .font(.system(
                        size: PriceTagWelcomeTheme.Typography.button.size * fontScale,
                        weight: PriceTagWelcomeTheme.Typography.button.weight
                    ))
                    .foregroundStyle(PriceTagWelcomeTheme.Colors.buttonText)
                    .fixedSize()
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .background(PriceTagWelcomeTheme.Colors.button)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(PriceTagWelcomeTheme.Colors.buttonRim, lineWidth: 1))
            .shadow(color: .black.opacity(0.10), radius: 26 * unit, x: 0, y: 8 * unit)
        }
        .buttonStyle(PriceTagWelcomeButtonStyle())
        .frame(width: width, height: height)
        .position(
            x: (PriceTagWelcomeTheme.Layout.buttonX + PriceTagWelcomeTheme.Layout.buttonWidth / 2) * unit,
            y: top + height / 2
        )
        .accessibilityLabel(PriceTagWelcomeTheme.Copy.button)
    }
}

struct PriceTagWelcomeBaselineLayout: Layout {
    let baseline: Double

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews,
                     cache: inout ()) -> CGSize {
        proposal.replacingUnspecifiedDimensions()
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        guard let subview = subviews.first else { return }
        let dimensions = subview.dimensions(in: .unspecified)
        subview.place(
            at: CGPoint(
                x: bounds.midX - dimensions.width / 2,
                y: bounds.minY + baseline - dimensions[.firstTextBaseline]
            ),
            anchor: .topLeading,
            proposal: ProposedViewSize(width: dimensions.width, height: dimensions.height)
        )
    }
}

struct PriceTagWelcomeButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? PriceTagWelcomeMotion.Press.scale : 1)
            .animation(
                .timingCurve(
                    0.23, 1, 0.32, 1,
                    duration: configuration.isPressed
                        ? PriceTagWelcomeMotion.Press.durationIn
                        : PriceTagWelcomeMotion.Press.durationOut
                ),
                value: configuration.isPressed
            )
    }
}

struct PriceTagWelcomeBackdrop: View {
    let size: CGSize
    let unit: Double

    var body: some View {
        Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(PriceTagWelcomeTheme.Colors.glowBase))
            let positions = [0.0, 0.5, 1, 1.5, 2, 2.5, 3]
            for blob in PriceTagWelcomeTheme.glow {
                let radius = 3 * blob.sigma * unit
                let center = CGPoint(x: blob.centerX * unit, y: blob.centerY * unit)
                let stops = positions.map { position in
                    Gradient.Stop(
                        color: Color(
                            red: blob.red / 255,
                            green: blob.green / 255,
                            blue: blob.blue / 255,
                            opacity: exp(-position * position / 2)
                        ),
                        location: position / 3
                    )
                }
                let circle = CGRect(
                    x: center.x - radius,
                    y: center.y - radius,
                    width: radius * 2,
                    height: radius * 2
                )
                context.fill(
                    Path(ellipseIn: circle),
                    with: .radialGradient(Gradient(stops: stops), center: center, startRadius: 0, endRadius: radius)
                )
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .accessibilityHidden(true)
    }
}

struct PriceTagWelcomeGlint: View {
    let frame: PriceTagWelcomeMotion.Frame
    let reduced: Bool
    let unit: Double

    var body: some View {
        let box = PriceTagWelcomeTheme.Layout.iconBox
        let bandWidth = box.width * 0.5 * unit
        let bandHeight = box.height * 1.6 * unit
        let left = -bandWidth - box.width * 0.45 * unit
            + frame.glint * box.width * 1.9 * unit
        ZStack(alignment: .topLeading) {
            LinearGradient(
                colors: [.white.opacity(0), .white, .white.opacity(0)],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: bandWidth, height: bandHeight)
            .rotationEffect(.degrees(20))
            .position(
                x: left + bandWidth / 2,
                y: -box.height * 0.3 * unit + bandHeight / 2
            )
        }
        .frame(width: box.width * unit, height: box.height * unit, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: PriceTagWelcomeTheme.iconRadius * unit, style: .continuous))
        .opacity(reduced ? 0 : PriceTagWelcomeMotion.Glint.alpha * frame.glintAlpha)
        .position(x: box.midX * unit, y: box.midY * unit)
        .accessibilityHidden(true)
    }
}

