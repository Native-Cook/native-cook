import SwiftUI

private let centerLabelExclusionDegrees: Double = 8

private func getPolarPoint(center: Double, radius: Double, angle: Double) -> CGPoint {
    let radians = angle * Double.pi / 180
    return CGPoint(
        x: center + sin(radians) * radius,
        y: center - cos(radians) * radius
    )
}

/// iPhone-camera-style logarithmic zoom wheel. Touch a compact zoom stop to
/// jump to it, or touch and hold then drag horizontally for fine adjustment.
public struct CameraZoomDial: View {
    private let props: CameraZoomProps
    private let accentColor: Color
    private let surfaceColor: Color
    private let labelColor: Color
    private let accessibilityLabel: String
    private let accessibilityHint: String

    @StateObject private var model: CameraZoomModel
    @State private var layoutWidth: CGFloat = 0
    @State private var expansionProgress: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// - Parameters:
    ///   - minZoom: Smallest supported zoom factor. Must be positive. Defaults to `0.5`.
    ///   - maxZoom: Largest supported zoom factor. Must exceed minZoom. Defaults to `10`.
    ///   - step: Display/callback precision. Defaults to `0.1`.
    ///   - value: Controlled zoom factor.
    ///   - defaultValue: Initial uncontrolled zoom factor. Defaults to `1` when it is in range.
    ///   - zoomStops: Optical or quick-zoom stops shown in the compact control and
    ///     on the wheel. Empty or entirely invalid input falls back to the default
    ///     in-range stops.
    ///   - expanded: Controlled expanded state.
    ///   - defaultExpanded: Initial expanded state for uncontrolled use. Defaults to `false`.
    ///   - onExpandedChange: Reports compact/wheel state requests.
    ///   - onZoomChange: Reports each crossed zoom step while dragging or animating.
    ///   - onInteractionEnd: Reports the final snapped zoom factor after a drag.
    public init(
        minZoom: Double = CameraZoomDefaults.min,
        maxZoom: Double = CameraZoomDefaults.max,
        step: Double = CameraZoomDefaults.step,
        value: Binding<Double>? = nil,
        defaultValue: Double? = nil,
        zoomStops: [CameraZoomStop]? = nil,
        expanded: Binding<Bool>? = nil,
        defaultExpanded: Bool = false,
        onExpandedChange: ((Bool) -> Void)? = nil,
        onZoomChange: ((Double) -> Void)? = nil,
        onInteractionStart: (() -> Void)? = nil,
        onInteractionEnd: ((Double) -> Void)? = nil,
        formatValue: ((Double) -> String)? = nil,
        accentColor: Color = CameraZoomDialColors.accent,
        surfaceColor: Color = CameraZoomDialColors.surface,
        labelColor: Color = CameraZoomDialColors.label,
        disabled: Bool = false,
        accessibilityLabel: String = "Camera zoom",
        accessibilityHint: String = "Touch and hold, then drag left or right. You can also use increment and decrement actions."
    ) {
        let props = CameraZoomProps(
            minZoom: minZoom,
            maxZoom: maxZoom,
            step: step,
            value: value,
            defaultValue: defaultValue,
            zoomStops: zoomStops,
            expanded: expanded,
            defaultExpanded: defaultExpanded,
            onExpandedChange: onExpandedChange,
            onZoomChange: onZoomChange,
            onInteractionStart: onInteractionStart,
            onInteractionEnd: onInteractionEnd,
            formatValue: formatValue,
            disabled: disabled
        )
        self.props = props
        self.accentColor = accentColor
        self.surfaceColor = surfaceColor
        self.labelColor = labelColor
        self.accessibilityLabel = accessibilityLabel
        self.accessibilityHint = accessibilityHint
        _model = StateObject(wrappedValue: CameraZoomModel(props: props))
        _expansionProgress = State(initialValue: (expanded?.wrappedValue ?? defaultExpanded) ? 1 : 0)
    }

    public var body: some View {
        model.props = props
        model.reduceMotion = reduceMotion

        let range = model.range
        let step = model.step
        let zoomStops = model.zoomStops
        let resolvedZoom = model.resolvedZoom
        let resolvedExpanded = model.resolvedExpanded
        let displayZoom = model.displayZoom
        let formattedValue = model.formattedValue(displayZoom)

        return GeometryReader { geometry in
            let width = Double(geometry.size.width)
            let diameter = width * CameraZoomDialGeometry.diameterToWidth
            let unit = width > 0 ? Swift.max(0.75, Swift.min(1.5, width / 393)) : 1

            ZStack(alignment: .topLeading) {
                if width > 0 {
                    CameraZoomWheel(
                        model: model,
                        diameter: diameter,
                        unit: unit,
                        range: range,
                        step: step,
                        zoomStops: zoomStops,
                        displayZoom: displayZoom,
                        formattedValue: formattedValue,
                        accentColor: accentColor,
                        surfaceColor: surfaceColor,
                        labelColor: labelColor
                    )
                    .frame(width: diameter, height: diameter)
                    .opacity(expansionProgress)
                    .scaleEffect(reduceMotion ? 1 : 0.46 + 0.54 * expansionProgress)
                    .offset(x: (width - diameter) / 2, y: 0)
                    .allowsHitTesting(false)
                }

                compactRow(
                    zoomStops: zoomStops,
                    step: step,
                    displayZoom: displayZoom,
                    unit: unit,
                    expanded: resolvedExpanded
                )
                .frame(width: geometry.size.width, height: 44 * unit)
                .opacity(1 - expansionProgress)
                .scaleEffect(reduceMotion ? 1 : 1 - 0.06 * expansionProgress)
                .offset(y: CameraZoomDialGeometry.compactRowTop * unit)
                .allowsHitTesting(!resolvedExpanded)
                .accessibilityHidden(resolvedExpanded)
            }
            .onAppear {
                layoutWidth = geometry.size.width
                model.radius = diameter / 2
            }
            .onChange(of: geometry.size.width) { newWidth in
                layoutWidth = newWidth
                model.radius = Double(newWidth) * CameraZoomDialGeometry.diameterToWidth / 2
            }
        }
        .aspectRatio(1 / CameraZoomDialGeometry.visibleHeightToWidth, contentMode: .fit)
        .clipped()
        .contentShape(Rectangle())
        .simultaneousGesture(
            wheelGesture(expanded: resolvedExpanded),
            including: props.disabled ? .subviews : .all
        )
        .modifier(
            AdjustableAccessibility(
                enabled: resolvedExpanded,
                label: accessibilityLabel,
                hint: accessibilityHint,
                value: formattedValue,
                onIncrement: { model.handleAccessibilityStep(increment: true) },
                onDecrement: { model.handleAccessibilityStep(increment: false) }
            )
        )
        .onAppear {
            model.animateExpansion = { expanded in
                animateExpansion(expanded)
            }
            model.syncZoomFromProps()
        }
        .onChange(of: resolvedZoom) { _ in model.syncZoomFromProps() }
        .onChange(of: model.isInteracting) { _ in model.syncZoomFromProps() }
        .onChange(of: resolvedExpanded) { expanded in animateExpansion(expanded) }
    }

    private func animateExpansion(_ expanded: Bool) {
        let animation: Animation? = reduceMotion
            ? nil
            : .timingCurve(0.23, 1, 0.32, 1, duration: 0.17)
        withAnimation(animation) {
            expansionProgress = expanded ? 1 : 0
        }
    }

    private func wheelGesture(expanded: Bool) -> some Gesture {
        LongPressGesture(
            minimumDuration: expanded ? 0.01 : CameraZoomDialGeometry.longPressDuration,
            maximumDistance: 40
        )
        .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .local))
        .onChanged { value in
            guard case .second(true, let drag) = value else { return }
            model.gestureBegan()
            if let drag {
                model.gestureChanged(translationX: drag.translation.width)
            }
        }
        .onEnded { value in
            if case .second(true, let drag) = value, let drag {
                model.gestureChanged(translationX: drag.translation.width)
            }
            model.gestureEnded()
        }
    }

    private func compactRow(
        zoomStops: [CameraZoomStop],
        step: Double,
        displayZoom: Double,
        unit: Double,
        expanded: Bool
    ) -> some View {
        let compactSelectedStop = findNearestCameraZoomStop(displayZoom, stops: zoomStops)
        return HStack(spacing: 0) {
            ForEach(zoomStops, id: \.value) { stop in
                let selected = compactSelectedStop?.value == stop.value
                let label = selected
                    ? model.formattedValue(displayZoom)
                    : (stop.compactLabel ?? stop.label ?? formatCameraZoomCompactNumber(stop.value, step: step))
                Button {
                    model.handleCompactStopPress(stop.value, selected: selected)
                } label: {
                    Text(label)
                        .font(.system(size: 14 * unit, weight: .medium))
                        .monospacedDigit()
                        .multilineTextAlignment(.center)
                        .foregroundColor(selected ? accentColor : labelColor)
                        .frame(width: 42 * unit, height: 42 * unit)
                        .background(
                            selected
                                ? RoundedRectangle(cornerRadius: 21 * unit)
                                    .fill(CameraZoomDialColors.compactSurface)
                                : nil
                        )
                }
                .buttonStyle(CompactStopPressStyle())
                .disabled(props.disabled)
                .accessibilityLabel("\(label) zoom")
                .accessibilityHint(selected ? "Opens the adjustable zoom wheel." : "Changes zoom to \(label).")
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct CompactStopPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

/// Applies the adjustable accessibility contract only while expanded, the
/// compact buttons speak for themselves otherwise.
private struct AdjustableAccessibility: ViewModifier {
    var enabled: Bool
    var label: String
    var hint: String
    var value: String
    var onIncrement: () -> Void
    var onDecrement: () -> Void

    func body(content: Content) -> some View {
        if enabled {
            content
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(label)
                .accessibilityHint(hint)
                .accessibilityValue(value)
                .accessibilityAdjustableAction { direction in
                    switch direction {
                    case .increment: onIncrement()
                    case .decrement: onDecrement()
                    @unknown default: break
                    }
                }
        } else {
            content
        }
    }
}

/// The circular scale: surface, masked rotating ticks and labels, pointer,
/// and the current-value label.
private struct CameraZoomWheel: View {
    @ObservedObject var model: CameraZoomModel
    var diameter: Double
    var unit: Double
    var range: CameraZoomRange
    var step: Double
    var zoomStops: [CameraZoomStop]
    var displayZoom: Double
    var formattedValue: String
    var accentColor: Color
    var surfaceColor: Color
    var labelColor: Color

    var body: some View {
        let radius = diameter / 2
        let logZoom = model.logZoom
        let ticks = buildCameraZoomTicks(minZoom: range.min, maxZoom: range.max, step: step)
        let wheelLabels = normalizeCameraZoomStops(
            zoomStops + [CameraZoomStop(value: range.min), CameraZoomStop(value: range.max)],
            minZoom: range.min,
            maxZoom: range.max,
            step: step
        )
        let activeStop = findCameraZoomStop(displayZoom, stops: zoomStops, step: step)
        let geometry = CameraZoomDialGeometry.self

        let outerTickRadius = radius - geometry.outerTickInset * unit
        let pointerTop = geometry.outerTickInset * unit
        let pointerHalfWidth = geometry.pointerHalfWidth * unit
        let pointerHeight = geometry.pointerHeight * unit
        let pointerOcclusionPadding = geometry.pointerOcclusionPadding * unit
        let pointerOcclusionTop = Swift.max(0, pointerTop - pointerOcclusionPadding)
        let pointerOcclusionApex = pointerTop + pointerHeight + geometry.pointerOcclusionApexOffset * unit
        let pointerOcclusionEnd = pointerTop + geometry.majorTickLength * unit + pointerOcclusionPadding
        let pointerOcclusionHalfWidth = getCameraZoomTriangleHalfWidth(
            height: pointerOcclusionApex - pointerOcclusionTop,
            apexAngleDegrees: geometry.pointerOcclusionApexAngle
        )
        let pointerOcclusionTailHalfWidth = geometry.pointerOcclusionTailHalfWidth * unit
        let pointerOcclusionTailTop = pointerOcclusionApex - pointerOcclusionTailHalfWidth
        let occlusionTriangle = getCameraZoomRoundedTriangle(
            centerX: radius,
            topY: pointerOcclusionTop,
            halfWidth: pointerOcclusionHalfWidth,
            apexY: pointerOcclusionApex,
            cornerRadius: geometry.pointerOcclusionCornerRadius * unit
        ).path()
        let occlusionTail = Path(
            roundedRect: CGRect(
                x: radius - pointerOcclusionTailHalfWidth,
                y: pointerOcclusionTailTop,
                width: pointerOcclusionTailHalfWidth * 2,
                height: Swift.max(0, pointerOcclusionEnd - pointerOcclusionTailTop)
            ),
            cornerRadius: pointerOcclusionTailHalfWidth
        )
        let pointerPath = getCameraZoomRoundedTriangle(
            centerX: radius,
            topY: pointerTop,
            halfWidth: pointerHalfWidth,
            apexY: pointerTop + pointerHeight,
            cornerRadius: geometry.pointerCornerRadius * unit
        ).path()

        ZStack(alignment: .topLeading) {
            Canvas { context, size in
                context.fill(
                    Path(ellipseIn: CGRect(x: 0, y: 0, width: size.width, height: size.height)),
                    with: .color(surfaceColor)
                )

                var masked = context
                masked.clip(to: occlusionTriangle, options: .inverse)
                masked.clip(to: occlusionTail, options: .inverse)
                masked.translateBy(x: radius, y: radius)
                masked.rotate(by: .degrees(-logZoom * geometry.degreesPerOctave))
                masked.translateBy(x: -radius, y: -radius)

                for tick in ticks {
                    let angle = cameraZoomToAngle(tick, selectedZoom: 1, degreesPerOctave: geometry.degreesPerOctave)
                    let major = isCameraZoomMajorTick(
                        tick,
                        stops: zoomStops,
                        step: step,
                        minZoom: range.min,
                        maxZoom: range.max
                    )
                    let length = (major ? geometry.majorTickLength : geometry.minorTickLength) * unit
                    let outer = getPolarPoint(center: radius, radius: outerTickRadius, angle: angle)
                    let inner = getPolarPoint(center: radius, radius: outerTickRadius - length, angle: angle)
                    var line = Path()
                    line.move(to: outer)
                    line.addLine(to: inner)
                    masked.stroke(
                        line,
                        with: .color(major ? CameraZoomDialColors.majorTick : CameraZoomDialColors.minorTick),
                        style: StrokeStyle(lineWidth: (major ? 1.45 : 0.75) * unit, lineCap: .round)
                    )
                }

                for stop in wheelLabels {
                    let relativeAngle = cameraZoomToAngle(
                        stop.value,
                        selectedZoom: displayZoom,
                        degreesPerOctave: geometry.degreesPerOctave
                    )
                    if abs(relativeAngle) < centerLabelExclusionDegrees { continue }
                    let angle = cameraZoomToAngle(stop.value, selectedZoom: 1, degreesPerOctave: geometry.degreesPerOctave)
                    let labelPoint = getPolarPoint(center: radius, radius: radius - geometry.labelInset * unit, angle: angle)
                    let focalPoint = getPolarPoint(center: radius, radius: radius - geometry.focalLabelInset * unit, angle: angle)
                    let label = stop.label ?? formatCameraZoomNumber(stop.value, step: step)

                    var labelContext = masked
                    labelContext.translateBy(x: labelPoint.x, y: labelPoint.y)
                    labelContext.rotate(by: .degrees(angle))
                    labelContext.draw(
                        Text(label)
                            .font(.system(size: 17 * unit, weight: .regular))
                            .foregroundColor(labelColor),
                        at: .zero,
                        anchor: .center
                    )

                    if let focalLength = stop.focalLength {
                        var focalContext = masked
                        focalContext.translateBy(x: focalPoint.x, y: focalPoint.y)
                        focalContext.rotate(by: .degrees(angle))
                        focalContext.draw(
                            Text(focalLength)
                                .font(.system(size: 12.5 * unit, weight: .regular))
                                .kerning(0.35 * unit)
                                .foregroundColor(CameraZoomDialColors.secondaryLabel),
                            at: .zero,
                            anchor: .center
                        )
                    }
                }

                context.fill(pointerPath, with: .color(accentColor))
            }
            .frame(width: diameter, height: diameter)

            VStack(spacing: 0) {
                Text(formattedValue)
                    .font(.system(size: 17 * unit, weight: .regular))
                    .monospacedDigit()
                    .lineLimit(1)
                    .frame(height: 21 * unit)
                    .foregroundColor(accentColor)
                if let focalLength = activeStop?.focalLength {
                    Text(focalLength)
                        .font(.system(size: 12.5 * unit, weight: .regular))
                        .monospacedDigit()
                        .kerning(0.35)
                        .lineLimit(1)
                        .frame(height: 17 * unit)
                        .foregroundColor(accentColor)
                }
            }
            .frame(width: 140 * unit)
            .offset(x: radius - 70 * unit, y: 29 * unit)
        }
        .frame(width: diameter, height: diameter)
    }
}
