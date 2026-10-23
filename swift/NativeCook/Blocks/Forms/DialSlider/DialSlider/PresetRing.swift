import SwiftUI

private let ringCenter = DialConfig.itemSize / 2
private let ringRadius = ringCenter - DialConfig.ringStrokeWidth / 2

/// Bipolar progress ring: clockwise for positive values, counter-clockwise
/// for negative values, both starting at 12 o'clock.
struct PresetRing: View {
    var value: Double
    var minValue: Double
    var maxValue: Double
    var color: Color
    var backgroundColor: Color

    var body: some View {
        let progress = getBipolarProgress(value: value, minValue: minValue, maxValue: maxValue)
        let direction: ArcDirection = value < 0 ? .counterClockwise : .clockwise
        let arc = createCircularArc(
            centerX: ringCenter,
            centerY: ringCenter,
            radius: ringRadius,
            progress: progress,
            direction: direction
        )

        ZStack {
            Circle()
                .stroke(backgroundColor, lineWidth: DialConfig.ringBackgroundStrokeWidth)
                .frame(width: ringRadius * 2, height: ringRadius * 2)
            if let arc {
                arc.path()
                    .stroke(
                        color,
                        style: StrokeStyle(lineWidth: DialConfig.ringStrokeWidth, lineCap: .round)
                    )
            }
        }
        .frame(width: DialConfig.itemSize, height: DialConfig.itemSize)
        .allowsHitTesting(false)
    }
}
