import SwiftUI
import UIKit

struct GiftSlotDropScene: View {
    let model: GiftSlotDropModel
    let layout: GiftSlotDropTokens.ScreenLayout

    var body: some View {
        ZStack(alignment: .topLeading) {
            Image(uiImage: GiftSlotDropBackdropCache.image(for: layout))
                .resizable().frame(width: layout.width, height: layout.height)
            GiftSlotDropHeader(model: model, layout: layout)
            GiftSlotDropTitle(model: model, layout: layout)
            GiftSlotDropCarousel(model: model, layout: layout)
            GiftSlotDropShelf(model: model, layout: layout)
            GiftSlotDropOffer(model: model, layout: layout)
            GiftSlotDropFinger(model: model)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
        .accessibilityHidden(true)
    }
}

@MainActor
private enum GiftSlotDropBackdropCache {
    private static var cache: [String: UIImage] = [:]

    static func image(for layout: GiftSlotDropTokens.ScreenLayout) -> UIImage {
        let key = "\(Int(layout.width))x\(Int(layout.height))-\(Int(layout.top))"
        if let image = cache[key] { return image }
        let format = UIGraphicsImageRendererFormat()
        format.opaque = true
        format.scale = UIScreen.main.scale
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: layout.width, height: layout.height), format: format)
        let image = renderer.image { output in
            let context = output.cgContext
            let colorSpace = CGColorSpaceCreateDeviceRGB()
            let base = CGGradient(colorsSpace: colorSpace, colors: [
                UIColor(giftSlotHex: "#100D0A").cgColor,
                UIColor(giftSlotHex: "#0C0A08").cgColor,
                UIColor(giftSlotHex: "#120E0A").cgColor,
            ] as CFArray, locations: [0, 0.5, 1])!
            context.drawLinearGradient(base, start: .zero, end: CGPoint(x: 0, y: layout.height), options: [])

            context.saveGState()
            context.translateBy(x: layout.cx, y: layout.slotY + 10)
            context.scaleBy(x: 1, y: 0.78 / 0.95)
            context.translateBy(x: -layout.cx, y: -(layout.slotY + 10))
            let warm = CGGradient(colorsSpace: colorSpace, colors: [
                UIColor(red: 1, green: 170 / 255, blue: 80 / 255, alpha: 0.34).cgColor,
                UIColor(red: 230 / 255, green: 110 / 255, blue: 50 / 255, alpha: 0.14).cgColor,
                UIColor(red: 90 / 255, green: 40 / 255, blue: 20 / 255, alpha: 0.05).cgColor,
                UIColor.clear.cgColor,
            ] as CFArray, locations: [0, 0.35, 0.7, 1])!
            context.drawRadialGradient(warm, startCenter: CGPoint(x: layout.cx, y: layout.slotY + 10), startRadius: 0,
                endCenter: CGPoint(x: layout.cx, y: layout.slotY + 10), endRadius: layout.width * 0.95,
                options: [.drawsAfterEndLocation])
            context.restoreGState()

            context.saveGState()
            context.translateBy(x: layout.width * 0.15, y: layout.top)
            context.scaleBy(x: 1, y: 0.75)
            context.translateBy(x: -layout.width * 0.15, y: -layout.top)
            let cool = CGGradient(colorsSpace: colorSpace, colors: [
                UIColor(red: 120 / 255, green: 140 / 255, blue: 190 / 255, alpha: 0.08).cgColor,
                UIColor.clear.cgColor,
            ] as CFArray, locations: [0, 1])!
            context.drawRadialGradient(cool, startCenter: CGPoint(x: layout.width * 0.15, y: layout.top), startRadius: 0,
                endCenter: CGPoint(x: layout.width * 0.15, y: layout.top), endRadius: layout.width * 0.8,
                options: [.drawsAfterEndLocation])
            context.restoreGState()

            var seed: UInt64 = 0xC001_CAFE
            for _ in 0..<Int(layout.width * layout.height / 9) {
                seed = seed &* 6_364_136_223_846_793_005 &+ 1
                let x = CGFloat((seed >> 33) % UInt64(max(1, Int(layout.width))))
                seed = seed &* 6_364_136_223_846_793_005 &+ 1
                let y = CGFloat((seed >> 33) % UInt64(max(1, Int(layout.height))))
                seed = seed &* 6_364_136_223_846_793_005 &+ 1
                let white = CGFloat((seed >> 48) & 0xFF) / 255
                context.setFillColor(UIColor(white: white, alpha: 0.025).cgColor)
                context.fill(CGRect(x: x, y: y, width: 0.55, height: 0.55))
            }
        }
        cache[key] = image
        return image
    }
}

private struct GiftSlotDropHeader: View {
    let model: GiftSlotDropModel
    let layout: GiftSlotDropTokens.ScreenLayout

    var body: some View {
        let offer = model.offerOpacity
        ZStack(alignment: .topLeading) {
            GiftSlotDropGlass(cornerRadius: 22).frame(width: 44, height: 44).offset(x: 16, y: layout.top)
            Image(systemName: "chevron.left").font(.system(size: 16, weight: .semibold))
                .position(x: 38, y: layout.top + 22).opacity(1 - offer)
            Image(systemName: "xmark").font(.system(size: 16, weight: .semibold))
                .position(x: 38, y: layout.top + 22).opacity(offer)
            GiftSlotDropGlass(cornerRadius: 18).frame(width: 79, height: 36)
                .offset(x: layout.width - 95, y: layout.top + 4)
            Text("Terms").font(GiftSlotDropFonts.font(GiftSlotDropFonts.medium, size: 15))
                .position(x: layout.width - 55.5, y: layout.top + 22)
            Text("ORRA PAY  ·  NEW MEMBER")
                .font(GiftSlotDropFonts.font(GiftSlotDropFonts.mono, size: 11)).tracking(1.4)
                .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.faint))
                .position(x: layout.cx, y: layout.top + GiftSlotDropTokens.Layout.eyebrowY - 5)
        }
        .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.ink))
    }
}

private struct GiftSlotDropTitle: View {
    let model: GiftSlotDropModel
    let layout: GiftSlotDropTokens.ScreenLayout
    private let titles = [["Choose your", "welcome gift"], ["Let go to", "activate it"], ["Activating", "your gift…"], ["Your gift", "is ready"]]

    var body: some View {
        let progress = model.titleProgress()
        ZStack {
            title(model.titleWas).opacity(progress.old).offset(y: progress.oldY)
            title(model.titleIndex).opacity(progress.current).offset(y: progress.currentY)
        }
        .frame(width: layout.width, height: 72)
        .position(x: layout.cx, y: layout.top + GiftSlotDropTokens.Layout.titleY + 5)
    }

    private func title(_ index: Int) -> some View {
        VStack(spacing: 0) {
            Text(titles[index][0]).frame(height: GiftSlotDropTokens.Layout.titleLine)
            Text(titles[index][1]).frame(height: GiftSlotDropTokens.Layout.titleLine)
        }
        .font(GiftSlotDropFonts.font(GiftSlotDropFonts.semibold, size: 28))
        .multilineTextAlignment(.center)
        .foregroundStyle(GiftSlotDropTokens.color(index == 1 ? GiftSlotDropTokens.Colors.amberInk : GiftSlotDropTokens.Colors.ink))
    }
}

private struct GiftSlotDropCarousel: View {
    let model: GiftSlotDropModel
    let layout: GiftSlotDropTokens.ScreenLayout

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(GiftSlotDropTokens.gifts.enumerated()), id: \.element.id) { index, gift in
                let pose = model.cardPose(index: index)
                GiftSlotDropCard(gift: gift, back: false)
                    .shadow(color: .black.opacity(index == model.selected ? 0.46 : 0), radius: 18, y: 18)
                    .overlay {
                        if index != model.selected {
                            RoundedRectangle(cornerRadius: GiftSlotDropTokens.Layout.cardR)
                                .fill(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.page).opacity(0.5))
                        }
                    }
                    .rotation3DEffect(.degrees(pose.turn), axis: (x: 0, y: 1, z: 0), perspective: 1 / 900)
                    .rotation3DEffect(.degrees(pose.lean), axis: (x: 1, y: 0, z: 0), perspective: 1 / 900)
                    .scaleEffect(pose.scale)
                    .position(x: pose.x, y: pose.y)
                    .opacity(pose.opacity)
            }
            Text("Drag a gift into the slot")
                .font(GiftSlotDropFonts.font(GiftSlotDropFonts.medium, size: 13))
                .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.mute))
                .position(x: layout.cx, y: layout.hintY - 5)
                .opacity(model.phase == .choosing || model.phase == .swiping ? 1 : 0)
            GiftSlotDropDots(model: model).position(x: layout.cx, y: layout.dotsY).opacity(model.carouselOpacity)
            Text("One welcome gift per member  ·  valid 30 days")
                .font(GiftSlotDropFonts.font(GiftSlotDropFonts.regular, size: 13))
                .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.faint))
                .position(x: layout.cx, y: layout.printY - 5).opacity(model.carouselOpacity)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .mask(alignment: .top) { Rectangle().frame(width: layout.width, height: layout.slotY) }
    }
}

private struct GiftSlotDropShelf: View {
    let model: GiftSlotDropModel
    let layout: GiftSlotDropTokens.ScreenLayout

    var body: some View {
        let pose = model.shelfPose
        ZStack {
            Ellipse()
                .fill(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.amber).opacity(0.22 + 0.4 * model.slotGlow))
                .frame(width: GiftSlotDropTokens.Layout.slotW * 1.4, height: 80).blur(radius: 22).blendMode(.plusLighter)
            GiftSlotDropGlass(cornerRadius: GiftSlotDropTokens.Layout.shelfH / 2, warm: true)
                .frame(width: GiftSlotDropTokens.Layout.shelfW, height: GiftSlotDropTokens.Layout.shelfH)
            Capsule()
                .fill(LinearGradient(colors: [
                    GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.slotDeep),
                    GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.slotDeep),
                    GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.amber).opacity(0.35 + 0.65 * model.slotGlow),
                ], startPoint: .top, endPoint: .bottom))
                .frame(width: GiftSlotDropTokens.Layout.slotW, height: GiftSlotDropTokens.Layout.slotH)
                .overlay(alignment: .top) {
                    LinearGradient(colors: [.clear, GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.lip), .clear], startPoint: .leading, endPoint: .trailing).frame(height: 1)
                }
        }
        .position(x: layout.cx, y: layout.slotY + pose.y).opacity(pose.opacity)
    }
}

private struct GiftSlotDropOffer: View {
    let model: GiftSlotDropModel
    let layout: GiftSlotDropTokens.ScreenLayout

    var body: some View {
        let pose = model.offerPose()
        let back = pose.angle > .pi / 2
        let angle = back ? pose.angle - .pi : pose.angle
        ZStack(alignment: .topLeading) {
            GiftSlotDropCard(gift: model.gift, back: back)
                .shadow(color: .black.opacity(0.5), radius: 18, y: 20)
                .rotation3DEffect(.radians(angle), axis: (x: 0, y: 1, z: 0), perspective: 1 / 900)
                .scaleEffect(pose.scale).position(x: layout.cx, y: pose.y).opacity(pose.opacity)
            ForEach(0..<2, id: \.self) { index in
                let progress = model.detailProgress(index)
                HStack(spacing: 19) {
                    Text(String(format: "0%d", index + 1))
                        .font(GiftSlotDropFonts.font(GiftSlotDropFonts.mono, size: 13))
                        .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.amberInk))
                        .frame(width: 21, alignment: .leading)
                    Text(model.gift.steps[index])
                        .font(GiftSlotDropFonts.font(GiftSlotDropFonts.regular, size: 15))
                        .foregroundStyle(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.ink))
                }
                .offset(x: 40, y: layout.stepsY - 13 + CGFloat(index * 32) + (model.reducedMotion ? 0 : 8 * CGFloat(1 - progress)))
                .opacity(progress)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .mask(alignment: .top) { Rectangle().frame(width: layout.width, height: pose.rising ? layout.slotY : layout.height) }
    }
}

private struct GiftSlotDropCard: View {
    let gift: GiftSlotDropTokens.Gift
    let back: Bool

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: GiftSlotDropTokens.Layout.cardR)
                .fill(LinearGradient(colors: [GiftSlotDropTokens.color(back ? gift.bg2 : gift.bg), GiftSlotDropTokens.color(back ? gift.bg : gift.bg2)], startPoint: .topLeading, endPoint: .bottomTrailing))
            if !back {
                GiftSlotDropMark(kind: gift.mark)
                    .stroke(GiftSlotDropTokens.color(gift.ink).opacity(0.07), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                    .frame(width: 168, height: 168).offset(x: 84, y: 148)
                LinearGradient(colors: [.white.opacity(0.09), .clear], startPoint: .topLeading, endPoint: .center)
                    .clipShape(RoundedRectangle(cornerRadius: GiftSlotDropTokens.Layout.cardR))
            }
            RoundedRectangle(cornerRadius: GiftSlotDropTokens.Layout.cardR - 0.5)
                .stroke(.white.opacity(0.16), lineWidth: 1).padding(0.5)
            GiftSlotDropMark(kind: gift.mark)
                .stroke(GiftSlotDropTokens.color(gift.ink), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                .frame(width: 16, height: 16).offset(x: 20, y: 22)
            Text(gift.shop)
                .font(GiftSlotDropFonts.font(GiftSlotDropFonts.semibold, size: 14))
                .foregroundStyle(GiftSlotDropTokens.color(gift.ink)).offset(x: 44, y: 20)
            if back { backContents } else { frontContents }
        }
        .frame(width: GiftSlotDropTokens.Layout.cardW, height: GiftSlotDropTokens.Layout.cardH)
        .clipShape(RoundedRectangle(cornerRadius: GiftSlotDropTokens.Layout.cardR))
    }

    private var frontContents: some View {
        ZStack(alignment: .topLeading) {
            Text("GIFT")
                .font(GiftSlotDropFonts.font(GiftSlotDropFonts.mono, size: 10)).tracking(1.2)
                .foregroundStyle(GiftSlotDropTokens.color(gift.ink).opacity(0.85)).padding(.horizontal, 7).frame(height: 18)
                .overlay(Capsule().stroke(GiftSlotDropTokens.color(gift.ink).opacity(0.4), lineWidth: 1)).offset(x: 143, y: 22)
            Text(gift.offer).font(GiftSlotDropFonts.font(GiftSlotDropFonts.semibold, size: 54))
                .foregroundStyle(GiftSlotDropTokens.color(gift.ink)).offset(x: 18, y: 91)
            Text(gift.line).font(GiftSlotDropFonts.font(GiftSlotDropFonts.medium, size: 15))
                .foregroundStyle(GiftSlotDropTokens.color(gift.ink)).offset(x: 20, y: 157)
            Text(gift.cap).font(GiftSlotDropFonts.font(GiftSlotDropFonts.regular, size: 13))
                .foregroundStyle(GiftSlotDropTokens.color(gift.sub)).offset(x: 20, y: 181)
            footer(left: "WELCOME GIFT", right: "ORRA PAY", rightBright: false)
        }
    }

    private var backContents: some View {
        ZStack(alignment: .topLeading) {
            Circle().stroke(GiftSlotDropTokens.color(gift.ink).opacity(0.5), lineWidth: 1.2).frame(width: 18, height: 18)
                .overlay(Image(systemName: "checkmark").font(.system(size: 9, weight: .semibold)).foregroundStyle(GiftSlotDropTokens.color(gift.ink)))
                .offset(x: 174, y: 22)
            Text("YOUR CODE").font(GiftSlotDropFonts.font(GiftSlotDropFonts.mono, size: 11)).tracking(1.4)
                .foregroundStyle(GiftSlotDropTokens.color(gift.sub)).offset(x: 20, y: 86)
            Text(gift.code).font(GiftSlotDropFonts.font(GiftSlotDropFonts.mono, size: 19)).minimumScaleFactor(0.75).lineLimit(1)
                .foregroundStyle(GiftSlotDropTokens.color(gift.ink)).frame(width: 144, height: 52)
                .background(.black.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(GiftSlotDropTokens.color(gift.ink).opacity(0.14), lineWidth: 1))
                .offset(x: 20, y: 116)
            Text("\(gift.offer) \(gift.line)").font(GiftSlotDropFonts.font(GiftSlotDropFonts.medium, size: 15)).minimumScaleFactor(0.72).lineLimit(1)
                .foregroundStyle(GiftSlotDropTokens.color(gift.ink)).frame(width: 172, alignment: .leading).offset(x: 20, y: 184)
            Text(gift.cap).font(GiftSlotDropFonts.font(GiftSlotDropFonts.regular, size: 13))
                .foregroundStyle(GiftSlotDropTokens.color(gift.sub)).offset(x: 20, y: 211)
            footer(left: "VALID TO 27 NOV", right: "ACTIVE", rightBright: true)
        }
    }

    private func footer(left: String, right: String, rightBright: Bool) -> some View {
        ZStack(alignment: .topLeading) {
            Rectangle().fill(GiftSlotDropTokens.color(gift.ink).opacity(0.16)).frame(width: 172, height: 1).offset(x: 20, y: 226)
            Text(left).font(GiftSlotDropFonts.font(GiftSlotDropFonts.mono, size: 10)).tracking(1.2)
                .foregroundStyle(GiftSlotDropTokens.color(gift.sub)).offset(x: 20, y: 244)
            Text(right).font(GiftSlotDropFonts.font(GiftSlotDropFonts.mono, size: 10)).tracking(1.2)
                .foregroundStyle(GiftSlotDropTokens.color(rightBright ? gift.ink : gift.sub))
                .frame(width: 92, alignment: .trailing).offset(x: 100, y: 244)
        }
    }
}

private struct GiftSlotDropMark: Shape {
    let kind: GiftSlotDropTokens.Gift.Mark
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 16, sy = rect.height / 16
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * sx, y: rect.minY + y * sy) }
        var path = Path()
        switch kind {
        case .bowl:
            path.move(to: p(1, 6.5)); path.addLine(to: p(15, 6.5)); path.addCurve(to: p(8, 14), control1: p(15, 10.9), control2: p(11.9, 14)); path.addCurve(to: p(1, 6.5), control1: p(4.1, 14), control2: p(1, 10.9)); path.move(to: p(3.2, 4.2)); path.addLine(to: p(12.8, 4.2))
        case .rails:
            path.move(to: p(4, 15)); path.addLine(to: p(7.5, 1)); path.move(to: p(8.5, 15)); path.addLine(to: p(12, 1)); path.move(to: p(3.9, 11.5)); path.addLine(to: p(10.9, 11.5)); path.move(to: p(4.8, 8)); path.addLine(to: p(11.8, 8)); path.move(to: p(5.7, 4.5)); path.addLine(to: p(12.7, 4.5))
        case .leaf:
            path.move(to: p(8, 1)); path.addCurve(to: p(8, 15), control1: p(13.2, 4.2), control2: p(14, 10.2)); path.addCurve(to: p(8, 1), control1: p(2, 10.2), control2: p(2.8, 4.2)); path.move(to: p(8, 4)); path.addLine(to: p(8, 15))
        case .bean:
            path.addEllipse(in: CGRect(x: rect.minX + 1.6 * sx, y: rect.minY + 1.2 * sy, width: 12.8 * sx, height: 13.6 * sy)); path.move(to: p(9.6, 2.4)); path.addCurve(to: p(6.4, 13.6), control1: p(6.2, 5.2), control2: p(9.8, 10.8))
        }
        return path
    }
}

private struct GiftSlotDropDots: View {
    let model: GiftSlotDropModel
    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<GiftSlotDropTokens.gifts.count, id: \.self) { index in
                let active = max(0, 1 - abs(Double(index) - model.offset))
                Capsule().fill(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.ink).opacity(0.22 + 0.68 * active))
                    .frame(width: 6 + 12 * active, height: 6)
            }
        }
    }
}

private struct GiftSlotDropGlass: View {
    let cornerRadius: CGFloat
    var warm = false
    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill((warm ? Color(red: 0.16, green: 0.105, blue: 0.07) : Color(red: 0.11, green: 0.10, blue: 0.09)).opacity(0.76))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(LinearGradient(colors: [.white.opacity(warm ? 0.10 : 0.08), .clear, .black.opacity(0.08)], startPoint: .topLeading, endPoint: .bottomTrailing))
            }
            .overlay(RoundedRectangle(cornerRadius: cornerRadius).stroke(.white.opacity(0.24), lineWidth: 0.8))
            .shadow(color: .black.opacity(0.22), radius: 12, y: 7)
    }
}

private struct GiftSlotDropFinger: View {
    let model: GiftSlotDropModel
    var body: some View {
        Circle().fill(.white.opacity(0.14)).overlay(Circle().stroke(.white.opacity(0.4), lineWidth: 1))
            .frame(width: 40, height: 40).position(x: model.ghost.x, y: model.ghost.y).opacity(model.ghost.opacity)
    }
}

private extension UIColor {
    convenience init(giftSlotHex: String) {
        let value = UInt64(giftSlotHex.dropFirst(), radix: 16) ?? 0
        self.init(red: CGFloat((value >> 16) & 0xFF) / 255, green: CGFloat((value >> 8) & 0xFF) / 255,
            blue: CGFloat(value & 0xFF) / 255, alpha: 1)
    }
}
