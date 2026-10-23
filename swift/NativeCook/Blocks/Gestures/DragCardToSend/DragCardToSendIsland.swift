import SwiftUI

struct DragCardToSendIsland: View {
    typealias T = DragCardToSendTokens
    @ObservedObject var motion: DragCardToSendMotion
    let size: CGSize

    var body: some View {
        let glass = min(max((motion.g - T.effects.glassStart) / T.effects.glassSpan, 0), 1) * (1 - min(max((motion.g - T.effects.glassEnd) / T.effects.glassEndSpan, 0), 1))
        let arrowOpacity = motion.isReduced ? 0 : T.pull.arrow * min(motion.g / T.effects.arrowIn, 1) * (1 - min(max((motion.g - T.effects.arrowOut) / T.effects.arrowOutSpan, 0), 1))
        let body = DragCardToSendIslandGeometry.body(g: min(motion.g, 1), size: size)
        ZStack(alignment: .topLeading) {
            DragCardToSendIslandShape(progress: motion.g)
                .fill(T.ColorToken.island.opacity(1 - (1 - T.pull.dropOpacity) * glass))
            Image(systemName: "arrow.up.left")
                .font(.system(size: T.space.xxl, weight: .regular))
                .foregroundStyle(T.ColorToken.sheetInk)
                .frame(width: T.space.xxl, height: T.space.xxl)
                .position(x: size.width / 2, y: body.minY + body.height * T.shape.arrowY)
                .opacity(arrowOpacity)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }
}

struct DragCardToSendIslandShape: Shape {
    typealias T = DragCardToSendTokens
    let progress: Double

    func path(in rect: CGRect) -> Path {
        let pill = DragCardToSendIslandGeometry.pill(g: progress, width: rect.width)
        let body = DragCardToSendIslandGeometry.body(g: progress, size: rect.size)
        var path = Path()
        path.addRoundedRect(in: pill, cornerSize: CGSize(width: pill.height / 2, height: pill.height / 2))
        if progress > 0.001 {
            path.addRoundedRect(in: body, cornerSize: DragCardToSendIslandGeometry.bodyCorner(g: progress, body: body))
            let gap = body.minY - pill.maxY
            if gap < DragCardToSendTokens.drop.neckBreak {
                let amount = max(0, 1 - max(gap, 0) / DragCardToSendTokens.drop.neckBreak)
                let topHalf = min(T.shape.neckTop, pill.width * T.shape.neckTopRatio) * sqrt(amount)
                let bottomHalf = min(T.shape.neckBottom, body.width * T.shape.neckBottomRatio) * sqrt(amount)
                let center = rect.midX
                var neck = Path()
                neck.move(to: CGPoint(x: center - topHalf, y: pill.maxY - T.shape.neckTopInset))
                neck.addLine(to: CGPoint(x: center + topHalf, y: pill.maxY - T.shape.neckTopInset))
                neck.addQuadCurve(to: CGPoint(x: center + bottomHalf, y: body.minY + T.shape.neckBottomInset), control: CGPoint(x: center + min(topHalf, bottomHalf) * amount * T.shape.neckWaist, y: (pill.maxY + body.minY) / 2))
                neck.addLine(to: CGPoint(x: center - bottomHalf, y: body.minY + T.shape.neckBottomInset))
                neck.addQuadCurve(to: CGPoint(x: center - topHalf, y: pill.maxY - T.shape.neckTopInset), control: CGPoint(x: center - min(topHalf, bottomHalf) * amount * T.shape.neckWaist, y: (pill.maxY + body.minY) / 2))
                neck.closeSubpath()
                path.addPath(neck)
            }
        }
        return path
    }
}

enum DragCardToSendIslandGeometry {
    typealias T = DragCardToSendTokens

    static func pill(g: Double, width: Double) -> CGRect {
        let transition = smooth((g - 1) * T.shape.pillTransition)
        let narrow = g < 1 ? mix(T.layout.pillWidth, T.drop.width, smooth(g)) : T.drop.width
        let w = mix(narrow, T.layout.islandWidth, transition)
        let h = mix(T.layout.pillHeight, T.layout.islandHeight, transition)
        let y = mix(T.layout.pillTop, T.layout.islandTop, transition)
        return CGRect(x: (width - w) / 2, y: y, width: w, height: h)
    }

    static func body(g: Double, size: CGSize) -> CGRect {
        if g <= 1 {
            let amount = min(max(g, 0), 1)
            let w = mix(T.shape.bodyStartWidth, T.drop.width, sin(amount * .pi / 2))
            let h = mix(T.shape.bodyStartHeight, T.drop.height, amount)
            return CGRect(x: (size.width - w) / 2, y: mix(T.shape.bodyStartTop, T.drop.top, 1 - pow(1 - amount, 2)), width: w, height: h)
        }
        let amount = min(g - 1, T.shape.overshoot)
        let w = mix(T.drop.width, size.width - T.sheet.inset * 2, amount)
        let h = mix(T.drop.height, size.height - T.sheet.top - T.sheet.bottomGap, amount)
        return CGRect(x: (size.width - w) / 2, y: mix(T.drop.top, T.sheet.top, min(amount, 1)), width: w, height: h)
    }

    static func bodyCorner(g: Double, body: CGRect) -> CGSize {
        if g <= 1 { return CGSize(width: body.width / 2, height: body.height / 2) }
        let amount = min(max(g - 1, 0), 1)
        return CGSize(width: max(T.sheet.radius, mix(T.drop.width / 2, T.sheet.radius, amount)), height: max(T.sheet.radius, mix(T.drop.height / 2, T.sheet.radius, amount)))
    }

    static func clip(g: Double, size: CGSize) -> CGRect {
        let body = body(g: g, size: size)
        let inset = T.shape.clipInset * (1 - smooth((g - 1) * 2))
        return body.insetBy(dx: inset, dy: inset)
    }

    private static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    private static func smooth(_ value: Double) -> Double { let t = min(max(value, 0), 1); return t * t * (3 - 2 * t) }
}

struct DragCardToSendPill: View {
    typealias T = DragCardToSendTokens
    @ObservedObject var motion: DragCardToSendMotion
    let width: Double

    var body: some View {
        let pill = DragCardToSendIslandGeometry.pill(g: motion.g, width: width)
        ZStack(alignment: .leading) {
            HStack {
                Text("Send ") + Text("to").bold()
                Spacer()
                HStack(spacing: -T.layout.avatarOverlap) {
                    ForEach(T.favourites) { person in DragCardToSendAvatar(person: person, size: T.layout.avatarSmall).overlay(Circle().stroke(T.ColorToken.island, lineWidth: 2)) }
                }
            }
            .padding(.leading, T.island.sideLeft)
            .padding(.trailing, T.island.sideRight)
            .opacity(1 - motion.sent)
            .accessibilityLabel("Send to recent people")

            HStack(spacing: T.island.receiptGap) {
                Image(systemName: "checkmark")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(T.ColorToken.island)
                    .frame(width: T.island.tick, height: T.island.tick)
                    .background(T.ColorToken.success, in: Circle())
                Text(receipt)
                    .font(.system(size: T.type.pill, weight: .bold).monospacedDigit())
                    .foregroundStyle(T.ColorToken.chipOn)
                Spacer()
                Button { motion.undoPayment() } label: {
                    Text("Undo")
                        .font(.system(size: T.island.undoFont, weight: .semibold))
                        .foregroundStyle(T.ColorToken.chipOn)
                }
                    .buttonStyle(.plain)
                    .padding(.horizontal, T.island.undoPad)
                    .frame(height: T.island.undoHeight)
                    .background(T.ColorToken.chipOff, in: Capsule())
                    .accessibilityLabel("Undo the payment")
            }
            .padding(.leading, T.island.sideLeft)
            .padding(.trailing, T.island.sideRight)
            .opacity(motion.sent)

            Capsule()
                .fill(Color.white.opacity(0.5))
                .frame(width: max(0.0001, (T.layout.pillWidth - T.island.barInset * 2) * motion.undo), height: T.island.barHeight)
                .offset(x: T.island.barInset, y: T.layout.pillHeight / 2 - T.island.barBottom - T.island.barHeight)
                .opacity(motion.sent)
        }
        .font(.system(size: T.type.pill, weight: .medium))
        .foregroundStyle(Color.white.opacity(0.72))
        .frame(width: T.layout.pillWidth, height: T.layout.pillHeight, alignment: .leading)
        .offset(x: pill.minX - (T.layout.pillWidth - pill.width) / 2, y: T.layout.pillTop)
        .frame(width: pill.width, height: pill.height, alignment: .center)
        .clipped()
    }

    private var receipt: String {
        DragCardToSendFormat.money(T.amounts[T.defaultAmountIndex] * 100).replacingOccurrences(of: ".00", with: "")
    }
}
