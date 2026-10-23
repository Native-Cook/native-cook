import SwiftUI

struct WalletFirstDepositDebitCard: View {
    let frame: WalletFirstDepositFrame
    let scale: Double
    let y0: Double
    var cardPress: Double = 0
    var bannerPress: Double = 0

    var body: some View {
        let pose = cardPose
        let width = 232 * scale
        let height = 147 * scale
        let fly = min(max(frame.flight, 0), 1)
        let ambientBlur = interpolate(interpolate(9, 4, fly), 20, frame.lift) * scale
        let ambientAlpha = interpolate(interpolate(0.42, 0.2, fly), 0.26, frame.lift)
        let ambientY = interpolate(interpolate(13, 5, fly), 34, frame.lift) * scale
        let contactAlpha = interpolate(interpolate(0.22, 0.16, fly), 0, frame.lift)

        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 18 * scale, style: .continuous)
                .fill(Color(hex: 0x2a4466).opacity(ambientAlpha))
                .frame(width: width * 0.88, height: height * 0.9)
                .blur(radius: ambientBlur)
                .offset(x: width * 0.06, y: ambientY + height * 0.06)
            RoundedRectangle(cornerRadius: 18 * scale, style: .continuous)
                .fill(Color(hex: 0x1a2a40).opacity(contactAlpha))
                .frame(width: width * 0.96, height: height)
                .blur(radius: 2.5 * scale)
                .offset(x: width * 0.02, y: 2 * scale)
            cardBody(width: width, height: height)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .scaleEffect(pose.scale)
        .rotationEffect(.degrees(pose.tilt))
        .position(x: pose.x * scale, y: y0 + pose.y * scale)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func cardBody(width: Double, height: Double) -> some View {
        let corner = 18 * scale
        let stops = [
            Gradient.Stop(color: Color(hex: 0x2062d8), location: 0),
            Gradient.Stop(color: Color(hex: 0x134bca), location: 0.09),
            Gradient.Stop(color: Color(hex: 0x155dd3), location: 0.22),
            Gradient.Stop(color: Color(hex: 0x1174e2), location: 0.38),
            Gradient.Stop(color: Color(hex: 0x0c8cf2), location: 0.54),
            Gradient.Stop(color: Color(hex: 0x0c9fff), location: 0.7),
            Gradient.Stop(color: Color(hex: 0x09b5f9), location: 0.79),
            Gradient.Stop(color: Color(hex: 0x11e4fe), location: 0.87),
            Gradient.Stop(color: Color(hex: 0x12fdfe), location: 0.92),
            Gradient.Stop(color: Color(hex: 0x2ff5ec), location: 1),
        ]
        return RoundedRectangle(cornerRadius: corner, style: .continuous)
            .fill(LinearGradient(gradient: Gradient(stops: stops), startPoint: .top, endPoint: .bottom))
            .overlay {
                RadialGradient(
                    colors: [Color(hex: 0x7ff6ff).opacity(0.35), Color(hex: 0x7ff6ff).opacity(0)],
                    center: UnitPoint(x: 0.55, y: 1.05),
                    startRadius: 0,
                    endRadius: width * 0.75
                )
            }
            .overlay(alignment: .top) {
                LinearGradient(colors: [.white.opacity(0.28), .white.opacity(0)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 3 * scale)
            }
            .overlay {
                if frame.sheen > 0, frame.sheen < 1 {
                    let x = interpolate(-0.6, 1.6, WalletFirstDepositMotion.inOut(frame.sheen))
                    LinearGradient(
                        stops: [
                            .init(color: .white.opacity(0), location: 0),
                            .init(color: .white.opacity(0.55), location: 0.5),
                            .init(color: .white.opacity(0), location: 1),
                        ],
                        startPoint: UnitPoint(x: x - 0.22, y: 0),
                        endPoint: UnitPoint(x: x + 0.22, y: 0.35)
                    )
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
            .overlay {
                Canvas { context, _ in
                    let center = CGPoint(x: 32 * scale, y: 33 * scale)
                    let arm = 11.5 * scale
                    for index in 0..<3 {
                        let angle = Double(index) * .pi / 3 + .pi / 2
                        context.stroke(
                            Path { path in
                                path.move(to: CGPoint(x: center.x - cos(angle) * arm, y: center.y - sin(angle) * arm))
                                path.addLine(to: CGPoint(x: center.x + cos(angle) * arm, y: center.y + sin(angle) * arm))
                            },
                            with: .color(.white),
                            style: StrokeStyle(lineWidth: 6 * scale, lineCap: .round, lineJoin: .round)
                        )
                    }
                    let markCenter = CGPoint(x: 192 * scale, y: 28 * scale)
                    context.fill(
                        Path(roundedRect: CGRect(x: markCenter.x - 19 * scale, y: markCenter.y - 13.5 * scale, width: 38 * scale, height: 27 * scale), cornerRadius: 8 * scale),
                        with: .color(.white.opacity(0.95))
                    )
                    context.fill(
                        Path(roundedRect: CGRect(x: markCenter.x - 16.5 * scale, y: markCenter.y - 10.5 * scale, width: 21 * scale, height: 21 * scale), cornerRadius: 6 * scale),
                        with: .color(Color(hex: 0xf23b2e))
                    )
                    context.fill(
                        Path(roundedRect: CGRect(x: markCenter.x - 4.5 * scale, y: markCenter.y - 10.5 * scale, width: 21 * scale, height: 21 * scale), cornerRadius: 6 * scale),
                        with: .color(Color(hex: 0xffa41c).opacity(0.9))
                    )
                }
            }
            .frame(width: width, height: height)
    }

    private var cardPose: (x: Double, y: Double, scale: Double, tilt: Double) {
        let flight = frame.flight
        let inverse = 1 - flight
        var x = inverse * inverse * 1005.5 + 2 * flight * inverse * 960 + flight * flight * 595.5
        var y = inverse * inverse * 882 + 2 * flight * inverse * 190 + flight * flight * 268.5
        var cardScale = interpolate(1, 0.752, flight) * (1 + 0.08 * frame.lift) * (1 - frame.cardSqueeze)
        cardScale *= 1 - 0.03 * cardPress * min(max(flight, 0), 1)
        if flight < 0.05, bannerPress > 0 {
            let pressScale = 1 - 0.03 * bannerPress
            let bannerCenter = CGPoint(x: 601.875, y: 901.9)
            x = bannerCenter.x + (x - bannerCenter.x) * pressScale
            y = bannerCenter.y + (y - bannerCenter.y) * pressScale
            cardScale *= pressScale
        }
        return (x, y, cardScale, frame.tilt)
    }
}

private func interpolate(_ from: Double, _ to: Double, _ amount: Double) -> Double {
    from + (to - from) * amount
}
