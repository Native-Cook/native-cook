import Foundation
import SwiftUI

struct CardBalanceWalletGlyph: View {
    enum Kind {
        case bell
        case plus
        case arrow
        case scan
        case grid
        case chart
        case chat
        case wallet
        case network
    }

    let kind: Kind
    let color: Color
    let size: Double

    var body: some View {
        Canvas { context, canvasSize in
            let viewBox = kind == .network ? CGSize(width: 22, height: 14) : CGSize(width: 24, height: 24)
            context.scaleBy(x: canvasSize.width / viewBox.width, y: canvasSize.height / viewBox.height)
            draw(in: &context)
        }
        .frame(width: size, height: kind == .network ? size * 0.62 : size)
        .accessibilityHidden(true)
    }

    private func draw(in context: inout GraphicsContext) {
        switch kind {
        case .bell:
            stroke(path("M6 16.5 V11 C6 7.4 8.6 4.8 12 4.8 C15.4 4.8 18 7.4 18 11 V16.5 L19.6 18.3 H4.4 Z"), in: &context, width: 1.9, join: .round)
            stroke(path("M10 20.6 C10.4 21.4 11.1 21.8 12 21.8 C12.9 21.8 13.6 21.4 14 20.6"), in: &context, width: 1.9, cap: .round)
        case .plus:
            stroke(path("M12 4 V20 M4 12 H20"), in: &context, width: 2.2, cap: .round)
        case .arrow:
            stroke(path("M4 12 H19.5 M13.5 5.8 L19.8 12 L13.5 18.2"), in: &context, width: 2.2, cap: .round, join: .round)
        case .scan:
            stroke(path("M3.5 8.5 V6.5 C3.5 4.8 4.8 3.5 6.5 3.5 H8.5 M15.5 3.5 H17.5 C19.2 3.5 20.5 4.8 20.5 6.5 V8.5 M20.5 15.5 V17.5 C20.5 19.2 19.2 20.5 17.5 20.5 H15.5 M8.5 20.5 H6.5 C4.8 20.5 3.5 19.2 3.5 17.5 V15.5"), in: &context, width: 2, cap: .round)
            fillRoundedRects([(7.2, 7.2), (12.8, 7.2), (7.2, 12.8), (12.8, 12.8)], size: 4, radius: 1.3, in: &context)
        case .grid:
            fillRect(CGRect(x: 3.5, y: 3.5, width: 7.5, height: 9.5), radius: 1.8, in: &context)
            fillRect(CGRect(x: 13, y: 3.5, width: 7.5, height: 6), radius: 1.8, in: &context)
            fillRect(CGRect(x: 3.5, y: 15, width: 7.5, height: 5.5), radius: 1.8, in: &context)
            fillRect(CGRect(x: 13, y: 11.5, width: 7.5, height: 9), radius: 1.8, in: &context)
        case .chart:
            fillRect(CGRect(x: 6, y: 10, width: 3, height: 9), radius: 1.2, in: &context)
            fillRect(CGRect(x: 10.6, y: 5, width: 3, height: 14), radius: 1.2, in: &context)
            fillRect(CGRect(x: 15.2, y: 12, width: 3, height: 7), radius: 1.2, in: &context)
            stroke(path("M3.5 20.5 H20.5"), in: &context, width: 1.9, cap: .round)
        case .chat:
            context.fill(path("M5 5 H19 C20.1 5 21 5.9 21 7 V15 C21 16.1 20.1 17 19 17 H13.5 L9 20.5 V17 H5 C3.9 17 3 16.1 3 15 V7 C3 5.9 3.9 5 5 5 Z"), with: .color(color))
            for x in [8.3, 12.0, 15.7] {
                context.fill(Path(ellipseIn: CGRect(x: x - 1.2, y: 9.8, width: 2.4, height: 2.4)), with: .color(Color(hex: 0x8E8E93)))
            }
        case .wallet:
            context.fill(path("M4 7.5 C4 6.1 5.1 5 6.5 5 H16.5 L18 7.5 Z"), with: .color(color.opacity(0.55)))
            fillRect(CGRect(x: 3, y: 7, width: 18, height: 13), radius: 3, in: &context)
            context.fill(Path(roundedRect: CGRect(x: 14, y: 11.5, width: 7, height: 4.5), cornerRadius: 1.6), with: .color(.white))
            context.fill(Path(ellipseIn: CGRect(x: 15.6, y: 12.75, width: 2, height: 2)), with: .color(color))
        case .network:
            context.fill(Path(roundedRect: CGRect(x: 1.6, y: 1.6, width: 10.8, height: 10.8), cornerRadius: 3.2), with: .color(Color(hex: 0xE52A22)))
            context.fill(Path(roundedRect: CGRect(x: 9.6, y: 1.6, width: 10.8, height: 10.8), cornerRadius: 3.2), with: .color(Color(hex: 0xF89C14).opacity(0.9)))
        }
    }

    private func stroke(
        _ path: Path,
        in context: inout GraphicsContext,
        width: Double,
        cap: CGLineCap = .butt,
        join: CGLineJoin = .miter
    ) {
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: cap, lineJoin: join))
    }

    private func fillRoundedRects(
        _ origins: [(Double, Double)],
        size: Double,
        radius: Double,
        in context: inout GraphicsContext
    ) {
        for origin in origins {
            fillRect(CGRect(x: origin.0, y: origin.1, width: size, height: size), radius: radius, in: &context)
        }
    }

    private func fillRect(_ rect: CGRect, radius: Double, in context: inout GraphicsContext) {
        context.fill(Path(roundedRect: rect, cornerRadius: radius), with: .color(color))
    }

    private func path(_ svg: String) -> Path {
        var result = Path()
        let scanner = Scanner(string: svg)
        var command: Character = "M"
        var current = CGPoint.zero
        func number() -> Double { scanner.scanDouble() ?? 0 }
        while !scanner.isAtEnd {
            if let letter = scanner.scanCharacters(from: .letters)?.first { command = letter }
            switch command {
            case "M":
                current = CGPoint(x: number(), y: number())
                result.move(to: current)
                command = "L"
            case "L":
                current = CGPoint(x: number(), y: number())
                result.addLine(to: current)
            case "H":
                current.x = number()
                result.addLine(to: current)
            case "V":
                current.y = number()
                result.addLine(to: current)
            case "C":
                let first = CGPoint(x: number(), y: number())
                let second = CGPoint(x: number(), y: number())
                current = CGPoint(x: number(), y: number())
                result.addCurve(to: current, control1: first, control2: second)
            default:
                result.closeSubpath()
            }
        }
        return result
    }
}
