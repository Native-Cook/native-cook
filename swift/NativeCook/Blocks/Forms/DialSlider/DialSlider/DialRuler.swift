import SwiftUI

private let maxRenderedTicks: Double = 201
private let rulerHeight: Double = DialConfig.centerTickHeight + 24
private let edgeFadeWidth: Double = 80

/// Horizontal tick ruler with pan, fling, snap, and tick wave animations.
struct DialRuler: View {
    /// Committed dial value owned by the parent.
    var value: Double
    var minValue: Double
    var maxValue: Double
    /// Edge fade color; should match the surface behind the ruler.
    var fadeColor: Color
    /// Disable ruler gestures while preserving its visual state.
    var enabled: Bool
    /// Invalidates decay/value callbacks when the owning preset changes.
    var interactionRevision: Int
    var onValueChange: (Double, Int) -> Void
    var onInteractionStart: () -> Void
    var onInteractionEnd: () -> Void

    @StateObject private var motion: DialRulerMotion
    @State private var contentOpacity: Double = 1
    @State private var dragStartLocation: CGPoint?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(
        value: Double,
        minValue: Double,
        maxValue: Double,
        fadeColor: Color,
        enabled: Bool,
        interactionRevision: Int,
        onValueChange: @escaping (Double, Int) -> Void,
        onInteractionStart: @escaping () -> Void,
        onInteractionEnd: @escaping () -> Void
    ) {
        self.value = value
        self.minValue = minValue
        self.maxValue = maxValue
        self.fadeColor = fadeColor
        self.enabled = enabled
        self.interactionRevision = interactionRevision
        self.onValueChange = onValueChange
        self.onInteractionStart = onInteractionStart
        self.onInteractionEnd = onInteractionEnd
        _motion = StateObject(
            wrappedValue: DialRulerMotion(
                initialValue: value,
                minValue: minValue,
                maxValue: maxValue
            )
        )
    }

    private var tickValues: [Double] {
        buildDialTickValues(
            minValue: minValue,
            maxValue: maxValue,
            baseStep: DialConfig.tickStep,
            maxTickCount: maxRenderedTicks
        )
    }

    var body: some View {
        let ticks = tickValues
        // Keep the latest parent state visible to the frame-driven motion.
        motion.currentRevision = interactionRevision
        motion.parentValue = value
        motion.reduceMotion = reduceMotion
        motion.onValueChange = onValueChange
        motion.updateRange(minValue: minValue, maxValue: maxValue)
        motion.updateTicks(ticks)

        return GeometryReader { geometry in
            ZStack(alignment: .bottom) {
                DialRulerTickCanvas(motion: motion, ticks: ticks)
                DialRulerOriginDot(translationX: motion.translationX)
            }
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .bottom)
        }
        .frame(height: rulerHeight)
        .contentShape(Rectangle())
        .highPriorityGesture(dragGesture, including: enabled ? .all : .subviews)
        .overlay(DialRulerEdgeFade(fadeColor: fadeColor))
        .opacity(contentOpacity)
        .clipped()
        .onChange(of: value) { newValue in
            motion.syncToValue(newValue, revisionChanged: false)
        }
        .onChange(of: interactionRevision) { _ in
            flashContent()
            motion.syncToValue(value, revisionChanged: true)
        }
        .onChange(of: enabled) { isEnabled in
            if !isEnabled, dragStartLocation != nil {
                dragStartLocation = nil
                motion.cancelGesture()
                onInteractionEnd()
            }
        }
        .accessibilityHidden(true)
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { drag in
                if dragStartLocation != drag.startLocation {
                    dragStartLocation = drag.startLocation
                    motion.beginGesture(revision: interactionRevision)
                    onInteractionStart()
                }
                motion.updateGesture(translation: drag.translation.width)
            }
            .onEnded { drag in
                guard dragStartLocation != nil else { return }
                dragStartLocation = nil
                motion.updateGesture(translation: drag.translation.width)
                motion.endGesture()
                onInteractionEnd()
            }
    }

    private func flashContent() {
        if reduceMotion {
            contentOpacity = 1
            return
        }
        withAnimation(.easeInOut(duration: 0.16)) {
            contentOpacity = 0.28
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.16) {
            withAnimation(.easeInOut(duration: 0.38)) {
                contentOpacity = 1
            }
        }
    }
}

/// Draws every tick in one pass. Reads the motion driver each frame so
/// heights, colors, and the drag wave follow the ruler translation exactly.
private struct DialRulerTickCanvas: View {
    @ObservedObject var motion: DialRulerMotion
    var ticks: [Double]

    var body: some View {
        let translationX = motion.translationX
        let now = motion.frameTime
        let startValue = -motion.startX / DialConfig.tickSpacing
        let currentValue = -translationX / DialConfig.tickSpacing
        let dragLevel = motion.dragLevel(at: now)
        let nearestTick = getNearestDialTick(currentValue, ticks: ticks)

        Canvas { context, size in
            let center = size.width / 2
            // The React Native ruler fades toward the window edges; here the
            // ruler's own half-width is the fade reference.
            let screenCenter = Double(center)
            let lo = Swift.min(startValue, currentValue)
            let hi = Swift.max(startValue, currentValue)
            let rangeSize = hi - lo

            for tick in ticks {
                let tickX = tick * DialConfig.tickSpacing
                let x = tickX + translationX
                let opacity = getDialTickOpacity(x: x, screenCenter: screenCenter)
                if opacity <= 0 { continue }

                let isMajor = tick.truncatingRemainder(dividingBy: DialConfig.majorTickEvery) == 0
                let inRange = tick >= lo && tick <= hi && rangeSize > 0
                let distFromCurrent = abs(currentValue - tick)
                let rawDist = rangeSize > 0 ? Swift.min(distFromCurrent / rangeSize, 1) : 1
                let normalizedDist = rawDist.squareRoot()
                let waveFactor = inRange
                    ? ((cos(normalizedDist * Double.pi) + 1) / 2) * dragLevel
                    : 0
                let waveHeight = DialConfig.tickHeight
                    + (DialConfig.centerTickHeight - DialConfig.tickHeight) * waveFactor
                let height = Swift.max(motion.nearestTickHeight(for: tick, at: now), waveHeight)
                let isNearest = tick == nearestTick
                let t = isMajor ? 1 : motion.crossingFlash(for: tick, at: now)
                let color: Color = isNearest
                    ? DialColors.positive
                    : DialColors.minorTickRGB.mixed(toward: DialColors.majorTickRGB, amount: t).color

                let rect = CGRect(
                    x: center + x,
                    y: size.height - height,
                    width: DialConfig.tickWidth,
                    height: height
                )
                context.fill(
                    Path(roundedRect: rect, cornerRadius: 1),
                    with: .color(color.opacity(opacity))
                )
            }
        }
    }
}

private struct DialRulerOriginDot: View {
    var translationX: Double

    var body: some View {
        let isAtZero = abs(translationX) < 2
        let fadeOpacity = Swift.max(0, 1 - abs(translationX) / 160)
        Circle()
            .fill(DialColors.originDot)
            .frame(width: 6, height: 6)
            .opacity(isAtZero ? 0 : fadeOpacity)
            .offset(x: translationX)
            .padding(.bottom, DialConfig.tickHeight + 6)
            .allowsHitTesting(false)
    }
}

private struct DialRulerEdgeFade: View {
    var fadeColor: Color

    var body: some View {
        HStack(spacing: 0) {
            LinearGradient(
                colors: [fadeColor, fadeColor.opacity(0)],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: edgeFadeWidth)
            Spacer(minLength: 0)
            LinearGradient(
                colors: [fadeColor.opacity(0), fadeColor],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: edgeFadeWidth)
        }
        .allowsHitTesting(false)
    }
}
