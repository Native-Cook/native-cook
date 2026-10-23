import SwiftUI
import UIKit

struct DragCardToSendWalletCard: View {
    let frame: DragCardToSendFrame
    let layout: DragCardToSendLayout

    var body: some View {
        ZStack(alignment: .topLeading) {
            Image(uiImage: DragCardToSendCardRaster.image(size: CGSize(width: layout.cardWidth, height: layout.cardHeight)))
                .resizable()
                .interpolation(.high)

            LinearGradient(
                colors: [.white.opacity(0.16), .clear, DragCardToSendPalette.accent.opacity(0.1)],
                startPoint: UnitPoint(x: 0.05 + frame.cardOffset / 220, y: 0),
                endPoint: UnitPoint(x: 0.72, y: 1)
            )
            .blendMode(.screen)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

            Text("cove")
                .font(.system(size: 27, weight: .medium, design: .rounded))
                .tracking(1.4)
                .foregroundStyle(.white)
                .position(x: 77, y: 39)

            DragCardToSendContactless()
                .stroke(.white.opacity(0.7), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
                .frame(width: 25, height: 34)
                .position(x: layout.cardWidth - 35, y: 39)

            VStack(alignment: .leading, spacing: 0) {
                Text("Balance")
                    .font(.system(size: 12, weight: .medium))
                    .tracking(0.2)
                    .foregroundStyle(.white.opacity(0.66))
                    .frame(height: 16, alignment: .top)
                Text(DragCardToSendFormat.money(balanceCents, signed: false))
                    .font(.system(size: 32, weight: .bold))
                    .tracking(-0.8)
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .frame(height: 36)
            }
            .offset(x: 24, y: layout.cardHeight - 76)

            Text("•••• 4821")
                .font(.system(size: 14, weight: .semibold))
                .tracking(0.6)
                .monospacedDigit()
                .foregroundStyle(.white.opacity(0.72))
                .frame(width: 112, height: 20, alignment: .trailing)
                .offset(x: layout.cardWidth - 136, y: layout.cardHeight - 45)
        }
        .shadow(color: Color(red: 10 / 255, green: 18 / 255, blue: 70 / 255).opacity(0.2), radius: 18, y: 12)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Cove card, balance \(DragCardToSendFormat.money(balanceCents, signed: false)), ending in 4821")
    }

    private var balanceCents: Int { frame.sentCommitted ? 243_620 : 248_120 }
}

final class DragCardToSendCardRaster {
    private static var cached: [String: UIImage] = [:]

    static func image(size: CGSize) -> UIImage {
        let key = "\(Int(size.width))x\(Int(size.height))"
        if let image = cached[key] { return image }
        let renderer = UIGraphicsImageRenderer(size: size)
        let image = renderer.image { context in
            let cg = context.cgContext
            let bounds = CGRect(origin: .zero, size: size)
            UIBezierPath(roundedRect: bounds, cornerRadius: 18).addClip()

            let colors = [
                UIColor(red: 0.08, green: 0.14, blue: 0.48, alpha: 1).cgColor,
                UIColor(red: 0.018, green: 0.043, blue: 0.23, alpha: 1).cgColor,
                UIColor(red: 0.015, green: 0.025, blue: 0.12, alpha: 1).cgColor
            ] as CFArray
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 0.58, 1])!
            cg.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: size.width, y: size.height), options: [])

            cg.setStrokeColor(UIColor.white.withAlphaComponent(0.052).cgColor)
            cg.setLineWidth(0.7)
            for index in 0..<20 {
                let inset = CGFloat(index) * 9
                cg.addEllipse(in: CGRect(x: size.width * 0.42 + inset * 0.32, y: -size.height * 0.25 + inset * 0.18,
                                         width: size.width * 0.94 - inset, height: size.height * 1.65 - inset))
                cg.strokePath()
            }

            cg.setStrokeColor(UIColor.white.withAlphaComponent(0.018).cgColor)
            cg.setLineWidth(0.45)
            for y in stride(from: CGFloat(4), to: size.height, by: 3) {
                cg.move(to: CGPoint(x: 0, y: y))
                cg.addLine(to: CGPoint(x: size.width, y: y + 0.35))
                cg.strokePath()
            }

            let chipRect = CGRect(x: 24, y: 80, width: 46, height: 35)
            UIBezierPath(roundedRect: chipRect, cornerRadius: 6).addClip()
            let gold = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [
                UIColor(red: 0.87, green: 0.73, blue: 0.49, alpha: 1).cgColor,
                UIColor(red: 0.48, green: 0.35, blue: 0.19, alpha: 1).cgColor
            ] as CFArray, locations: [0, 1])!
            cg.drawLinearGradient(gold, start: chipRect.origin, end: CGPoint(x: chipRect.maxX, y: chipRect.maxY), options: [])
            cg.resetClip()
            cg.setStrokeColor(UIColor(red: 0.35, green: 0.27, blue: 0.17, alpha: 0.55).cgColor)
            cg.setLineWidth(0.8)
            cg.stroke(chipRect.insetBy(dx: 0.5, dy: 0.5))
            cg.stroke(CGRect(x: 39, y: 88, width: 17, height: 19))
            [31.5, 62.5].forEach { x in
                cg.move(to: CGPoint(x: x, y: 80)); cg.addLine(to: CGPoint(x: x, y: 115)); cg.strokePath()
            }
            [91.5, 103.5].forEach { y in
                cg.move(to: CGPoint(x: 24, y: y)); cg.addLine(to: CGPoint(x: 70, y: y)); cg.strokePath()
            }
        }
        cached[key] = image
        return image
    }
}

struct DragCardToSendContactless: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        for radius in [7.0, 12.0, 17.0] {
            path.addArc(center: CGPoint(x: rect.minX + 1, y: rect.midY), radius: radius,
                        startAngle: .degrees(-48), endAngle: .degrees(48), clockwise: false)
        }
        return path
    }
}
