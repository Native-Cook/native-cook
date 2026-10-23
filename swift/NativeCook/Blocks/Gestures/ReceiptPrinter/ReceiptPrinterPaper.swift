import SwiftUI
import UIKit

/// The fixed 333 × 1287 pt thermal slip. The parent positions and clips it as it
/// feeds from the printer; all artwork here remains in paper-local coordinates.
struct ReceiptPrinterPaper: View {
    private let t = ReceiptPrinterTokens.self
    private let width = ReceiptPrinterTokens.SLOT.paperX1 - ReceiptPrinterTokens.SLOT.paperX0
    private let height = ReceiptPrinterTokens.RECEIPT.length + ReceiptPrinterTokens.RECEIPT.edge.depth

    var body: some View {
        ZStack(alignment: .topLeading) {
            ReceiptPrinterPaperSurface()

            Image(uiImage: ReceiptPrinterAssets.logo)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .frame(width: t.RECEIPT.logo.w, height: t.RECEIPT.logo.h)
                .offset(x: (width - t.RECEIPT.logo.w) / 2, y: y(t.RECEIPT.logo.top))

            ReceiptPrinterPaperFastTag()

            ForEach(Array(t.RECEIPT.rows.enumerated()), id: \.offset) { _, row in
                ReceiptPrinterPaperRow(row: row)
            }

            ForEach(Array(t.RECEIPT.paragraph.lines.enumerated()), id: \.offset) { index, line in
                ReceiptPrinterPaperRow(
                    row: ReceiptPrinterRow(
                        cap: t.RECEIPT.paragraph.cap + Double(index) * t.RECEIPT.paragraph.pitch,
                        text: line,
                        align: "left",
                        x: t.RECEIPT.left,
                        size: 11.2,
                        color: t.COLOR.inkSoft
                    )
                )
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Run receipt for 6.21 kilometers")
    }

    private func y(_ screenY: Double) -> Double { screenY - t.SLOT.paperTop }
}

private struct ReceiptPrinterPaperSurface: View {
    private let t = ReceiptPrinterTokens.self
    private let width = ReceiptPrinterTokens.SLOT.paperX1 - ReceiptPrinterTokens.SLOT.paperX0
    // RN draws grain and the edge curl only through H - edge.depth. The torn
    // silhouette continues through its final scallop depth below that overlay.
    private let overlayHeight = ReceiptPrinterTokens.RECEIPT.length - ReceiptPrinterTokens.RECEIPT.edge.depth
    private let surfaceHeight = ReceiptPrinterTokens.RECEIPT.length + ReceiptPrinterTokens.RECEIPT.edge.depth

    var body: some View {
        ZStack(alignment: .topLeading) {
            ReceiptPrinterTornPaperShape()
                .fill(t.COLOR.paper)

            ReceiptPrinterPaperRules()

            Image(uiImage: ReceiptPrinterPaperArtwork.grain)
                .interpolation(.none)
                .opacity(0.05)
                .frame(width: width, height: overlayHeight)
                .clipped()

            LinearGradient(
                stops: [
                    .init(color: t.COLOR.paperShade, location: 0),
                    .init(color: .clear, location: 0.06),
                    .init(color: .clear, location: 0.94),
                    .init(color: t.COLOR.paperShade, location: 1),
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: width, height: overlayHeight)
            .clipped()
        }
        .frame(width: width, height: surfaceHeight, alignment: .topLeading)
    }
}

private struct ReceiptPrinterPaperRules: View {
    private let t = ReceiptPrinterTokens.self
    private let width = ReceiptPrinterTokens.SLOT.paperX1 - ReceiptPrinterTokens.SLOT.paperX0

    var body: some View {
        Canvas { context, _ in
            let left = x(t.RECEIPT.left)
            let right = x(t.RECEIPT.right)
            let dashStyle = StrokeStyle(lineWidth: 1, dash: [3, 2.4])
            for screenY in t.RECEIPT.dashes {
                let yy = y(screenY) + 0.5
                var rule = Path()
                rule.move(to: CGPoint(x: left, y: yy))
                rule.addLine(to: CGPoint(x: right, y: yy))
                context.stroke(rule, with: .color(t.COLOR.dash), style: dashStyle)
            }

            let chart = t.RECEIPT.chart
            let chartTop = y(chart.top)
            let base = y(chart.base)
            let chartBottom = y(chart.bottom)
            let dotted = StrokeStyle(lineWidth: 0.6, dash: [1.5, 2.5])
            for fraction in [0.25, 0.5, 0.75] {
                let yy = chartTop + (base - chartTop) * fraction
                var row = Path()
                row.move(to: CGPoint(x: left, y: yy))
                row.addLine(to: CGPoint(x: right, y: yy))
                context.stroke(row, with: .color(t.COLOR.gridFaint), style: dotted)
            }
            let segments = chart.columns - 1
            for index in 0..<chart.columns {
                let xx = left + (right - left) * Double(index) / Double(segments)
                var column = Path()
                column.move(to: CGPoint(x: xx, y: chartTop))
                column.addLine(to: CGPoint(x: xx, y: chartBottom))
                context.stroke(column, with: .color(t.COLOR.grid), lineWidth: 0.8)
            }
            for index in 0..<(segments * chart.dotted) {
                let xx = left + (right - left) * (Double(index) + 0.5) / Double(segments * chart.dotted)
                var column = Path()
                column.move(to: CGPoint(x: xx, y: chartTop))
                column.addLine(to: CGPoint(x: xx, y: base))
                context.stroke(column, with: .color(t.COLOR.gridFaint), style: dotted)
            }
            var axis = Path()
            axis.move(to: CGPoint(x: left, y: base))
            axis.addLine(to: CGPoint(x: right, y: base))
            context.stroke(axis, with: .color(t.COLOR.axis), lineWidth: chart.baseW)

            var average = Path()
            let averageY = paceY(ReceiptPrinterPace.average)
            average.move(to: CGPoint(x: left, y: averageY))
            average.addLine(to: CGPoint(x: right, y: averageY))
            context.stroke(
                average,
                with: .color(t.COLOR.inkSoft),
                style: StrokeStyle(lineWidth: 0.8, dash: [4, 3])
            )

            var pace = Path()
            for (index, sample) in ReceiptPrinterPace.samples.enumerated() {
                let xx = left + (right - left) * Double(index) / Double(ReceiptPrinterPace.samples.count - 1)
                let point = CGPoint(x: xx, y: paceY(sample))
                if index == 0 { pace.move(to: point) } else { pace.addLine(to: point) }
            }
            context.stroke(
                pace,
                with: .color(t.COLOR.ink),
                style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round)
            )

            for screenY in t.RECEIPT.zone.ys {
                let radius = t.RECEIPT.zone.r
                context.fill(
                    Path(ellipseIn: CGRect(x: x(t.RECEIPT.zone.x) - radius, y: y(screenY) - radius, width: radius * 2, height: radius * 2)),
                    with: .color(t.COLOR.zone)
                )
            }
        }
        .frame(width: width, height: t.RECEIPT.length, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func x(_ screenX: Double) -> Double { screenX - t.SLOT.paperX0 }
    private func y(_ screenY: Double) -> Double { screenY - t.SLOT.paperTop }

    private func paceY(_ pace: Double) -> Double {
        let chart = t.RECEIPT.chart
        let progress = (pace - ReceiptPrinterPace.fast) / (ReceiptPrinterPace.slow - ReceiptPrinterPace.fast)
        return y(chart.top) + 6 + progress * (y(chart.base) - y(chart.top) - 12)
    }
}

private enum ReceiptPrinterPace {
    static let average = 325.0
    static let fast = 280.0
    static let slow = 360.0
    static let kilometers = 6.21

    static let samples: [Double] = (0..<63).map { index in
        let kilometer = Double(index) / 62 * kilometers
        let settle = 15 * exp(-kilometer / 0.35)
        let rise = 7 * exp(-pow(kilometer - 1.9, 2) / 0.05)
            + 6 * exp(-pow(kilometer - 3.6, 2) / 0.04)
        let unit = min(1, max(0, (kilometer - 4.6) / 1.4))
        let kick = -20 * unit * unit * (3 - 2 * unit)
        let breath = 2.2 * sin(Double(index) * 1.3) + 1.4 * sin(Double(index) * 0.55 + 0.7)
        return 326 + settle + rise + kick + breath
    }
}

private struct ReceiptPrinterPaperFastTag: View {
    private let t = ReceiptPrinterTokens.self

    var body: some View {
        Text("FAST")
            .font(ReceiptPrinterAssets.font(size: t.RECEIPT.fast.size, weight: t.FONT.bold))
            .foregroundStyle(t.COLOR.tagInk)
            .frame(width: t.RECEIPT.fast.w, height: t.RECEIPT.fast.h)
            .background(t.COLOR.tag, in: RoundedRectangle(cornerRadius: t.RECEIPT.fast.radius, style: .circular))
            .offset(x: x(t.RECEIPT.fast.x), y: y(t.RECEIPT.fast.top))
    }

    private func x(_ screenX: Double) -> Double { screenX - t.SLOT.paperX0 }
    private func y(_ screenY: Double) -> Double { screenY - t.SLOT.paperTop }
}

private struct ReceiptPrinterPaperRow: View {
    let row: ReceiptPrinterRow
    private let t = ReceiptPrinterTokens.self

    var body: some View {
        let font = ReceiptPrinterAssets.uiFont(size: row.size, weight: t.mono(row.bold))
        let top = y(row.cap) - Double(font.ascender - font.capHeight)
        Text(row.text)
            .font(ReceiptPrinterAssets.font(size: row.size, weight: t.mono(row.bold)))
            .foregroundStyle(row.color ?? t.COLOR.ink)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: true)
            .frame(width: width, alignment: alignment)
            .offset(x: horizontalInset, y: top)
    }

    private var width: Double { t.SLOT.paperX1 - t.SLOT.paperX0 }

    private var alignment: Alignment {
        switch row.align {
        case "center": .center
        case "right": .trailing
        default: .leading
        }
    }

    private var horizontalInset: Double {
        guard let screenX = row.x else { return 0 }
        return switch row.align {
        // CSS `right` is measured from the paper's trailing edge. A SwiftUI
        // trailing-aligned full-width frame therefore moves left by that gap.
        case "right": x(screenX) - width
        case "left": x(screenX)
        default: 0
        }
    }

    private func x(_ screenX: Double) -> Double { screenX - t.SLOT.paperX0 }
    private func y(_ screenY: Double) -> Double { screenY - t.SLOT.paperTop }
}

private struct ReceiptPrinterTornPaperShape: Shape {
    private let t = ReceiptPrinterTokens.self
    private let width = ReceiptPrinterTokens.SLOT.paperX1 - ReceiptPrinterTokens.SLOT.paperX0

    func path(in rect: CGRect) -> Path {
        let edge = t.RECEIPT.edge
        let count = Int((width / edge.period).rounded())
        let period = width / Double(count)
        let bodyHeight = t.RECEIPT.length
        var path = Path()
        path.move(to: .zero)
        path.addLine(to: CGPoint(x: width, y: 0))
        path.addLine(to: CGPoint(x: width, y: bodyHeight - edge.depth))
        for index in stride(from: count - 1, through: 0, by: -1) {
            let x0 = Double(index + 1) * period
            let x1 = Double(index) * period
            path.addLine(to: CGPoint(x: x0, y: bodyHeight - edge.depth))
            path.addQuadCurve(
                to: CGPoint(x: x1, y: bodyHeight - edge.depth),
                control: CGPoint(x: x0 - period / 2, y: bodyHeight + edge.depth * 0.6)
            )
        }
        path.closeSubpath()
        return path
    }
}

/// The source uses Skia FractalNoise(freq=.9, octaves=2). SwiftUI has no
/// equivalent CPU-independent shader, so this is a deterministic, cached two-octave
/// value-noise approximation; the identical 5% opacity is applied by the surface.
private enum ReceiptPrinterPaperArtwork {
    static let grain: UIImage = {
        // Keep the source's paper-scale resolution: no stretched low-resolution
        // texture alters its frequency when this cached image is displayed.
        let width = 333
        let height = 1281
        let bytesPerPixel = 4
        var pixels = [UInt8](repeating: 255, count: width * height * bytesPerPixel)

        for y in 0..<height {
            for x in 0..<width {
                // RN FractalNoise uses frequency .9 and two octaves. Use
                // equal-amplitude, deterministic value-noise octaves here.
                let value = (valueNoise(x, y, frequency: 0.9) + valueNoise(x, y, frequency: 1.8)) / 2
                let gray = UInt8(clamping: Int((value * 255).rounded()))
                let offset = (y * width + x) * bytesPerPixel
                pixels[offset] = gray
                pixels[offset + 1] = gray
                pixels[offset + 2] = gray
            }
        }

        guard
            let provider = CGDataProvider(data: Data(pixels) as CFData),
            let image = CGImage(
                width: width,
                height: height,
                bitsPerComponent: 8,
                bitsPerPixel: 32,
                bytesPerRow: width * bytesPerPixel,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue),
                provider: provider,
                decode: nil,
                shouldInterpolate: false,
                intent: .defaultIntent
            )
        else {
            return UIImage()
        }
        return UIImage(cgImage: image, scale: 1, orientation: .up)
    }()

    private static func valueNoise(_ x: Int, _ y: Int, frequency: Double) -> CGFloat {
        let fx = Double(x) * frequency
        let fy = Double(y) * frequency
        let x0 = Int(floor(fx))
        let y0 = Int(floor(fy))
        let u = smooth(fx - Double(x0))
        let v = smooth(fy - Double(y0))
        let a = lattice(x0, y0)
        let b = lattice(x0 + 1, y0)
        let c = lattice(x0, y0 + 1)
        let d = lattice(x0 + 1, y0 + 1)
        return CGFloat((1 - v) * ((1 - u) * a + u * b) + v * ((1 - u) * c + u * d))
    }

    private static func smooth(_ value: Double) -> Double {
        value * value * (3 - 2 * value)
    }

    private static func lattice(_ x: Int, _ y: Int) -> Double {
        var value = UInt32(truncatingIfNeeded: x &* 374_761_393 &+ y &* 668_265_263 &+ 0x9E37_79B9)
        value = (value ^ (value >> 13)) &* 1_274_126_177
        return Double(value & 0xFFFF) / 65_535
    }
}
