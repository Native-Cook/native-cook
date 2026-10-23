import SwiftUI

enum WeekendMoodCarouselScene {
    private static let bodyLayers: [[String]] = [
        ["sleepy"],
        ["foodie"],
        ["recoveryLobe", "recovery"],
        ["binge"],
        ["shoppingHandles", "shopping"],
        ["scroll", "scrollHand", "scrollPhone"],
        ["social"],
    ]

    static func panel(_ context: inout GraphicsContext, size: CGSize, frame: WeekendMoodCarouselFrame, reduced: Bool) {
        let metrics = WeekendMoodCarouselMetrics.self
        let stops = Array(0..<metrics.moods.count).map(Double.init)
        let panelColors = metrics.moods.map(\.panel)
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(WeekendMoodCarouselInterpolation.color(frame.characterPosition, stops, panelColors)))

        let shiftedCharacter = WeekendMoodCarouselInterpolation.value(frame.characterPosition, stops, metrics.characterPositions)
        let middle = size.width / 2
        for index in metrics.moods.indices {
            let centerX = middle + CGFloat(metrics.characterPositions[index] - shiftedCharacter)
            guard abs(Double(centerX - middle)) <= Double(size.width / 2) + metrics.characterBox / 2 else { continue }
            var bodyContext = context
            bodyContext.translateBy(x: centerX, y: 0)
            for layerName in bodyLayers[index] {
                guard let path = WeekendMoodCarouselShapes.paths[layerName] else { continue }
                if layerName == "scrollHand" {
                    bodyContext.fill(
                        path,
                        with: .linearGradient(
                            Gradient(colors: WeekendMoodCarouselShapes.handGradient.colors),
                            startPoint: WeekendMoodCarouselShapes.handGradient.from,
                            endPoint: WeekendMoodCarouselShapes.handGradient.to
                        )
                    )
                } else {
                    let softness: Double = layerName == "recoveryLobe" ? 2.5 : layerName == "shopping" ? 0.55 : 0.22
                    var softened = bodyContext
                    softened.addFilter(.blur(radius: softness))
                    softened.fill(
                        path,
                        with: .color(Color(hex: WeekendMoodCarouselShapes.colors[layerName] ?? 0)),
                        style: FillStyle(eoFill: layerName == "shoppingHandles")
                    )
                }
            }

            let kind = metrics.moods[index].kind
            var act = 0.0
            var age = 9_999.0
            if index == frame.selectedCharacter {
                act = kind == .scroll ? frame.fill : frame.age >= 0 ? 1 : 0
                age = frame.age
            } else if index == frame.previousCharacter,
                      abs(frame.characterPosition - Double(index)) < 1.4,
                      kind != .scroll {
                act = 1
            }
            var faceContext = context
            faceContext.translateBy(x: centerX, y: 0)
            WeekendMoodCarouselFaces.draw(&faceContext, kind: kind, act: act, age: age, phase: frame.phase, still: reduced)
        }
    }

    static func ruler(_ context: inout GraphicsContext, size: CGSize, chipX: Double, anchor: Double, characterPosition: Double) {
        let metrics = WeekendMoodCarouselMetrics.self
        let middle = size.width / 2
        let offset = positiveRemainder(chipX - anchor, metrics.rulerPitch)
        let baseline = metrics.rulerBaseline
        let numberOfTicks = Int(ceil(Double(middle) / metrics.rulerPitch)) + 2
        let maximumDistance = metrics.rulerHeights.count - 1
        let distanceStops = Array(0..<metrics.rulerHeights.count).map(Double.init)

        for k in (-numberOfTicks)...numberOfTicks {
            let x = Double(middle) + Double(k) * metrics.rulerPitch - offset
            let distance = min(abs(x - Double(middle)) / metrics.rulerPitch, Double(maximumDistance))
            let height = WeekendMoodCarouselInterpolation.value(distance, distanceStops, metrics.rulerHeights)
            let alpha = WeekendMoodCarouselInterpolation.value(distance, distanceStops, metrics.rulerAlphas)
            let rect = CGRect(x: CGFloat(x - metrics.rulerTickWidth / 2), y: CGFloat(baseline - height), width: CGFloat(metrics.rulerTickWidth), height: CGFloat(height))
            var tick = context
            tick.addFilter(.blur(radius: metrics.rulerTickSoftness))
            tick.fill(
                Path(roundedRect: rect, cornerRadius: CGFloat(metrics.rulerTickWidth / 2)),
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(alpha), .white.opacity(0.02)]),
                    startPoint: CGPoint(x: 0, y: CGFloat(baseline - height)),
                    endPoint: CGPoint(x: 0, y: CGFloat(baseline))
                )
            )
        }

        let stops = Array(0..<metrics.moods.count).map(Double.init)
        let top = WeekendMoodCarouselInterpolation.color(characterPosition, stops, metrics.moods.map { $0.tick.0 })
        let bottom = WeekendMoodCarouselInterpolation.color(characterPosition, stops, metrics.moods.map { $0.tick.1 })
        let indicator = CGRect(
            x: middle - CGFloat(metrics.rulerIndicatorWidth / 2),
            y: CGFloat(metrics.rulerIndicatorTop),
            width: CGFloat(metrics.rulerIndicatorWidth),
            height: CGFloat(metrics.rulerIndicatorBottom - metrics.rulerIndicatorTop)
        )
        context.fill(
            Path(roundedRect: indicator, cornerRadius: CGFloat(metrics.rulerIndicatorWidth / 2)),
            with: .linearGradient(
                Gradient(colors: [top, bottom]),
                startPoint: CGPoint(x: middle, y: CGFloat(metrics.rulerIndicatorTop)),
                endPoint: CGPoint(x: middle, y: CGFloat(metrics.rulerIndicatorBottom))
            )
        )
    }

    private static func positiveRemainder(_ value: Double, _ divisor: Double) -> Double {
        let remainder = value.truncatingRemainder(dividingBy: divisor)
        return remainder >= 0 ? remainder : remainder + divisor
    }
}
