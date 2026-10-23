import SwiftUI

private let gridColor = Color.white.opacity(0.16)
private let bandColor = Color.white.opacity(0.05)
private let hatchColor = Color.white.opacity(0.07)
private let averageColor = Color.white.opacity(0.85)
private let ringColor = Color.white.opacity(0.6)

private let hatchStep = 7.0
private let ringWidth = 2.0
private let ringGap = 2.0
// The scrubbed column widens by this share; the others keep this much of their light.
private let lift = 0.12
private let dimmed = 0.7

struct SleepColumnScrubberChartFrame: Equatable {
    var width: Double
    /// Where the time axis starts and ends on the canvas.
    var top: Double
    var bottom: Double
    /// The first minute (top) and the last (bottom) of the axis.
    var from: Double
    var to: Double
    /// Centre of the first column and the step to the next.
    var first: Double
    var step: Double
    var column: Double
    var plotWidth: Double

    func y(_ minutes: Double) -> Double {
        top + (minutes - from) / (to - from) * (bottom - top)
    }
}

struct SleepColumnScrubberBands {
    /// Shaded band of the ideal time to fall asleep, in minutes.
    var ideal: (Double, Double)
    /// Hatched band of the usual wake-up time, in minutes.
    var wake: (Double, Double)
    var grid: [Double]
    var averageAsleep: Double
    var averageAwake: Double
}

/// The chart's drawing: bands, grid, average lines and one column of sleep stages per night. Its
/// paths are built once per frame size and kept.
final class SleepColumnScrubberSleepColumns {
    private struct Column {
        var x: Double
        var top: Double
        var bottom: Double
        var stages: [(color: Color, path: Path)]
    }

    private var key: SleepColumnScrubberChartFrame?
    private var columns: [Column] = []
    private var grid = Path()
    private var hatch = Path()
    private var hatchClip = CGRect.zero
    private var ideal = CGRect.zero
    private var asleep = Path()
    private var awake = Path()

    private func build(_ frame: SleepColumnScrubberChartFrame, _ nights: [SleepColumnScrubberNight], _ bands: SleepColumnScrubberBands) {
        guard key != frame else { return }
        key = frame
        columns = nights.enumerated().map { index, night in
            var paths: [SleepColumnScrubberStage: Path] = [:]
            let x = frame.first + frame.step * Double(index)
            var at = night.asleep
            for run in SleepColumnScrubberData.stagesOf(night, index) {
                let y0 = frame.y(at)
                let y1 = frame.y(at + run.minutes)
                // A hair of overlap so no seam shows between runs.
                paths[run.stage, default: Path()].addRect(CGRect(x: x - frame.column, y: y0, width: frame.column * 2, height: y1 - y0 + 0.5))
                at += run.minutes
            }
            let stages = SleepColumnScrubberStage.allCases.compactMap { stage in paths[stage].map { (stage.color, $0) } }
            return Column(x: x, top: frame.y(night.asleep), bottom: frame.y(night.awake), stages: stages)
        }
        grid = Path()
        for minutes in bands.grid {
            let y = frame.y(minutes).rounded() + 0.5
            grid.move(to: CGPoint(x: 0, y: y))
            grid.addLine(to: CGPoint(x: frame.width, y: y))
        }
        let y0 = frame.y(bands.wake.0)
        let y1 = frame.y(bands.wake.1)
        hatch = Path()
        var x = -(y1 - y0)
        while x < frame.width {
            hatch.move(to: CGPoint(x: x, y: y1))
            hatch.addLine(to: CGPoint(x: x + (y1 - y0), y: y0))
            x += hatchStep
        }
        hatchClip = CGRect(x: 0, y: y0, width: frame.width, height: y1 - y0)
        ideal = CGRect(x: 0, y: frame.y(bands.ideal.0), width: frame.width, height: frame.y(bands.ideal.1) - frame.y(bands.ideal.0))
        let asleepY = frame.y(bands.averageAsleep).rounded() + 0.5
        let awakeY = frame.y(bands.averageAwake).rounded() + 0.5
        asleep = Path { $0.move(to: CGPoint(x: 0, y: asleepY)); $0.addLine(to: CGPoint(x: frame.plotWidth, y: asleepY)) }
        awake = Path { $0.move(to: CGPoint(x: 0, y: awakeY)); $0.addLine(to: CGPoint(x: frame.plotWidth, y: awakeY)) }
    }

    /// Draws the chart; `on` is each column's share of the lift and `press` is 1 while a finger is down.
    func draw(
        _ context: GraphicsContext,
        frame: SleepColumnScrubberChartFrame,
        nights: [SleepColumnScrubberNight],
        bands: SleepColumnScrubberBands,
        on: [Double],
        press: Double,
        still: Bool
    ) {
        build(frame, nights, bands)
        context.fill(Path(ideal), with: .color(bandColor))
        var hatched = context
        hatched.clip(to: Path(hatchClip))
        hatched.fill(Path(hatchClip), with: .color(bandColor))
        hatched.stroke(hatch, with: .color(hatchColor), lineWidth: 2)
        context.stroke(grid, with: .color(gridColor), style: StrokeStyle(lineWidth: 1, dash: [1.5, 5]))

        for (index, column) in columns.enumerated() {
            let lit = index < on.count ? on[index] : 0
            let width = frame.column
            var group = context
            // One night's column: its stage runs clipped to a capsule, widened and ringed while scrubbed.
            group.translateBy(x: column.x, y: (column.top + column.bottom) / 2)
            group.scaleBy(x: 1 + (still ? 0 : lift) * lit * press, y: 1)
            group.translateBy(x: -column.x, y: -(column.top + column.bottom) / 2)
            group.opacity = 1 - (1 - dimmed) * press * (1 - lit)
            let shape = CGRect(x: column.x - width / 2, y: column.top, width: width, height: column.bottom - column.top)
            // One layer per column, so the runs' overlap does not show when the column is dimmed.
            group.drawLayer { layer in
                layer.clip(to: RoundedRectangle(cornerRadius: width / 2).path(in: shape))
                for stage in column.stages { layer.fill(stage.path, with: .color(stage.color)) }
            }
            let ring = lit * press
            if ring > 0 {
                var ringed = group
                ringed.opacity *= ring
                let outline = shape.insetBy(dx: -ringGap, dy: -ringGap)
                ringed.stroke(RoundedRectangle(cornerRadius: width / 2 + ringGap).path(in: outline), with: .color(ringColor), lineWidth: ringWidth)
            }
        }

        context.stroke(asleep, with: .color(averageColor), lineWidth: 1.5)
        context.stroke(awake, with: .color(averageColor), style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
    }
}
