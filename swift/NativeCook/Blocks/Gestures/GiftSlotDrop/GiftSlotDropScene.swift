import SwiftUI

struct GiftSlotDropScene: View {
    let model: GiftSlotDropModel
    let size: CGSize

    var body: some View {
        ZStack(alignment: .topLeading) {
            GiftSlotDropBackdrop(model: model, size: size)
            GiftSlotDropChevron(model: model, width: size.width)
            GiftSlotDropPlaceholder(opacity: model.placeholderOpacity, width: size.width)
            GiftSlotDropCards(model: model, size: size)
                .mask(alignment: .top) { Rectangle().frame(height: GiftSlotDropTokens.Layout.slotY) }
            GiftSlotDropConfetti(model: model)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

private struct GiftSlotDropBackdrop: View {
    let model: GiftSlotDropModel
    let size: CGSize

    var body: some View {
        let layout = GiftSlotDropTokens.Layout.self
        let colors = GiftSlotDropTokens.Colors.self
        let glow = GiftSlotDropTokens.color(GiftSlotDropTokens.gifts[model.selected].glow)
        let slotGlow = model.slotGlow
        ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(RadialGradient(colors: [GiftSlotDropTokens.color(colors.stage), GiftSlotDropTokens.color(colors.stageEdge)], center: UnitPoint(x: 0.5, y: 0.08), startRadius: 0, endRadius: size.height * 0.85))
            Rectangle()
                .fill(GiftSlotDropTokens.color(colors.stageWarmInk))
                .frame(height: layout.floorY)
                .opacity(model.warmOpacity)
                .blendMode(.multiply)
            Ellipse()
                .fill(glow)
                .frame(width: 400, height: 440)
                .position(x: size.width / 2, y: layout.cardY)
                .blur(radius: 60)
                .opacity(colors.hueTint)
            Rectangle()
                .fill(LinearGradient(colors: [GiftSlotDropTokens.color(colors.floor), GiftSlotDropTokens.color(colors.floorLow)], startPoint: .top, endPoint: .bottom))
                .frame(width: size.width, height: max(0, size.height - layout.floorY))
                .offset(y: layout.floorY)
            Rectangle().fill(GiftSlotDropTokens.color(colors.floorLine)).frame(width: size.width, height: 1).offset(y: layout.floorY)
            RoundedRectangle(cornerRadius: 16)
                .fill(LinearGradient(stops: [
                    .init(color: GiftSlotDropTokens.color(colors.pocketFade), location: 0),
                    .init(color: GiftSlotDropTokens.color(colors.pocketFade), location: 0.35),
                    .init(color: GiftSlotDropTokens.color(colors.pocketLow), location: 1)
                ], startPoint: .top, endPoint: .bottom))
                .frame(width: layout.pocketW, height: layout.floorY - layout.pocketTop + layout.pocketDepth)
                .position(x: size.width / 2, y: layout.pocketTop + (layout.floorY - layout.pocketTop + layout.pocketDepth) / 2)
            GiftSlotDropSlotGlow(glow: slotGlow)
                .position(x: size.width / 2, y: layout.slotY)
        }
    }
}

private struct GiftSlotDropSlotGlow: View {
    let glow: Double

    var body: some View {
        let layout = GiftSlotDropTokens.Layout.self
        ZStack {
            Ellipse()
                .fill(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.yellowDeep))
                .frame(width: layout.slotW + 48, height: layout.slotH + 26)
                .blur(radius: 20)
                .opacity(0.2)
                .offset(y: -layout.slotH / 2 - 10 + layout.glowDrop)
            Ellipse()
                .fill(RadialGradient(colors: [
                    GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.glowCore),
                    GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.yellowDeep),
                    GiftSlotDropTokens.color("rgba(242,169,0,0)")
                ], center: UnitPoint(x: 0.5, y: 0.33), startRadius: 0, endRadius: layout.pocketW * 0.45))
                .frame(width: layout.pocketW * 1.2, height: layout.glowH)
                .blur(radius: 6)
                .opacity(0.72)
                .offset(y: layout.glowTop)
                .mask(RoundedRectangle(cornerRadius: 16).frame(width: layout.pocketW, height: layout.floorY + layout.pocketDepth - layout.pocketTop).offset(y: layout.pocketTop - layout.slotY))
        }
        .scaleEffect(x: CGFloat(0.9 + 0.12 * min(1.2, glow)), y: 1)
        .opacity(min(1, glow))
    }
}

private struct GiftSlotDropChevron: View {
    let model: GiftSlotDropModel
    let width: CGFloat

    var body: some View {
        let pose = chevronPose
        Path { path in
            path.move(to: CGPoint(x: -11.5, y: -7.5)); path.addLine(to: CGPoint(x: 0, y: 1)); path.addLine(to: CGPoint(x: 11.5, y: -7.5))
            path.move(to: CGPoint(x: -11.5, y: 2.5)); path.addLine(to: CGPoint(x: 0, y: 11)); path.addLine(to: CGPoint(x: 11.5, y: 2.5))
        }
        .stroke(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.mute), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
        .frame(width: 23, height: 19)
        .position(x: width / 2, y: pose.y)
        .scaleEffect(pose.visible ? 1 : 0)
    }

    private var chevronPose: (y: CGFloat, visible: Bool) {
        let layout = GiftSlotDropTokens.Layout.self
        let reduced = model.reducedMotion
        let base = layout.chevronY + (reduced ? 0 : GiftSlotDropTokens.Chevron.bobPt * sin(CGFloat(model.clock / GiftSlotDropTokens.Chevron.bobMs * 2 * .pi)))
        guard model.releasedAt < 0 else { return (base, false) }
        let progress = min(1, max(0, model.pullY / Double(GiftSlotDropTokens.Drag.full)))
        let half = Double(layout.cardH) * (1 - (1 - GiftSlotDropTokens.Drag.minScale) * progress) * cos(progress * Double(GiftSlotDropTokens.Drag.leanDeg) * .pi / 180) / 2
        let top = Double(layout.cardY) + model.pullY - half
        let bottom = Double(layout.cardY) + model.pullY + half
        if bottom > Double(base - GiftSlotDropTokens.Chevron.gap) {
            if top < Double(base - GiftSlotDropTokens.Chevron.gap) { return (base, false) }
            return (CGFloat(top) - GiftSlotDropTokens.Chevron.gap, true)
        }
        return (base, true)
    }
}

private struct GiftSlotDropPlaceholder: View {
    let opacity: Double
    let width: CGFloat

    var body: some View {
        RoundedRectangle(cornerRadius: GiftSlotDropTokens.Layout.cardR + 6)
            .stroke(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.outline), lineWidth: 1.2)
            .frame(width: GiftSlotDropTokens.Layout.cardW + 14, height: GiftSlotDropTokens.Layout.cardH + 14)
            .position(x: width / 2, y: GiftSlotDropTokens.Layout.cardY)
            .opacity(opacity)
    }
}

private struct GiftSlotDropCards: View {
    let model: GiftSlotDropModel
    let size: CGSize

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(GiftSlotDropTokens.gifts.enumerated()), id: \.element.id) { index, gift in
                GiftSlotDropCarouselCard(model: model, gift: gift, index: index)
            }
            GiftSlotDropOfferCard(model: model, gift: model.gift, width: size.width)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

private struct GiftSlotDropCarouselCard: View {
    let model: GiftSlotDropModel
    let gift: GiftSlotDropTokens.Gift
    let index: Int

    var body: some View {
        let pose = model.cardPose(index: index)
        let light = abs(Double(index) - model.offset) < 0.5 ? min(1, max(0, (model.pullY - 50) / 100)) : 0
        let bandPhase = (Double(index) - model.offset) * -0.9 + model.pullX / 120 + 0.25 * sin(2 * .pi * model.clock / GiftSlotDropTokens.Ambient.bandMs + Double(index))
        let bandOffset = CGFloat(bandPhase * Double(GiftSlotDropTokens.Layout.cardW))
        ZStack {
            if index == model.selected {
                RoundedRectangle(cornerRadius: GiftSlotDropTokens.Layout.cardR + 6)
                    .stroke(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.yellow).opacity(0.55), lineWidth: 6)
                    .frame(width: GiftSlotDropTokens.Layout.cardW + 14, height: GiftSlotDropTokens.Layout.cardH + 14)
                    .blur(radius: 10)
                    .opacity(model.selectionFrameOpacity)
                RoundedRectangle(cornerRadius: GiftSlotDropTokens.Layout.cardR + 5)
                    .stroke(GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.yellow), lineWidth: 3)
                    .frame(width: GiftSlotDropTokens.Layout.cardW + 12, height: GiftSlotDropTokens.Layout.cardH + 12)
                    .opacity(model.selectionFrameOpacity)
            }
            GiftSlotDropCardArtwork(gift: gift)
                .overlay {
                    LinearGradient(colors: [.clear, .white.opacity(model.reducedMotion ? 0 : GiftSlotDropTokens.Ambient.band), .clear], startPoint: .topLeading, endPoint: .bottomTrailing)
                        .offset(x: bandOffset)
                        .blendMode(.softLight)
                }
                .overlay(Color.white.opacity(light * GiftSlotDropTokens.Colors.cardLift))
                .overlay(LinearGradient(colors: [.clear, GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.pocketLight).opacity(light)], startPoint: .top, endPoint: .bottom))
                .compositingGroup()
                .clipShape(RoundedRectangle(cornerRadius: GiftSlotDropTokens.Layout.cardR))
        }
        .rotation3DEffect(.degrees(pose.turn), axis: (x: 0, y: 1, z: 0), perspective: 1 / 700)
        .rotation3DEffect(.degrees(pose.lean), axis: (x: 1, y: 0, z: 0), perspective: 1 / 700)
        .rotationEffect(.degrees(pose.roll))
        .scaleEffect(CGFloat(pose.scale))
        .position(x: CGFloat(pose.x), y: CGFloat(pose.y))
        .opacity(pose.opacity)
    }
}

private struct GiftSlotDropCardArtwork: View {
    let gift: GiftSlotDropTokens.Gift

    var body: some View {
        let layout = GiftSlotDropTokens.Layout.self
        ZStack {
            GiftSlotDropTokens.color(gift.glow)
            GiftSlotDropAssetImage(name: "GiftSlotDrop-card-\(gift.id).jpg")
                .scaledToFill()
            GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.cardShadeInk).opacity(GiftSlotDropTokens.Colors.cardShade.base)
            GiftSlotDropTokens.color(gift.glow).opacity(GiftSlotDropTokens.Colors.cardShade.multiply).blendMode(.multiply)
            VStack(spacing: 0) {
                GiftSlotDropWordmark(gift: gift, offer: false)
                    .frame(height: 49, alignment: .bottom)
                Text(gift.headline).font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.cardBig).weight(.bold)).padding(.top, 24)
                Text(gift.line).font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.cardLine).weight(.bold))
                Text(gift.cap).font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.cardLine).weight(.bold)).padding(.top, 2)
                Spacer()
            }
            .foregroundStyle(GiftSlotDropTokens.color(gift.ink))
        }
        .frame(width: layout.cardW, height: layout.cardH)
    }
}

private struct GiftSlotDropWordmark: View {
    let gift: GiftSlotDropTokens.Gift
    let offer: Bool

    var body: some View {
        let size: CGFloat = offer ? 22.4 : 16
        let fontSize = offer ? GiftSlotDropTokens.TypeStyle.offerMark : GiftSlotDropTokens.TypeStyle.mark
        if gift.id == "parcels" {
            VStack(spacing: 0) {
                Text(gift.wordmark)
                GiftSlotDropAssetImage(name: "GiftSlotDrop-mark-arrow.png").scaledToFit().frame(height: size * 0.9)
            }
            .font(.custom("Avenir Next", size: fontSize).weight(.bold))
        } else {
            HStack(spacing: offer ? 7 : 5) {
                if gift.id != "white" {
                    GiftSlotDropAssetImage(name: gift.id == "burger" ? "GiftSlotDrop-mark-bun.png" : "GiftSlotDrop-mark-swirl.png")
                        .scaledToFit().frame(width: size, height: size)
                }
                Text(gift.wordmark)
            }
            .font(.custom("Avenir Next", size: fontSize).weight(.bold))
        }
    }
}

private struct GiftSlotDropOfferCard: View {
    let model: GiftSlotDropModel
    let gift: GiftSlotDropTokens.Gift
    let width: CGFloat

    var body: some View {
        let pose = model.offerPose()
        let words = gift.use.split(separator: " ")
        let half = Int(ceil(Double(words.count) / 2))
        let first = words[..<half].joined(separator: " ")
        let second = words[half...].joined(separator: " ")
        let ink = GiftSlotDropTokens.color(gift.id == "parcels" ? "#FFFFFF" : gift.ink)
        ZStack {
            GiftSlotDropTokens.color(gift.glow)
            GiftSlotDropAssetImage(name: gift.id == "parcels" ? "GiftSlotDrop-offer-bg.jpg" : "GiftSlotDrop-card-\(gift.id).jpg").scaledToFill().saturation(0.75).brightness(-0.04)
            GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.offerWarm)
            VStack(spacing: 0) {
                GiftSlotDropWordmark(gift: gift, offer: true).frame(height: 64, alignment: .bottom)
                Text("\(model.offerPercent)%").font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.offerBig).weight(.bold)).padding(.top, 73)
                Text(gift.line).font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.offerLine).weight(.medium)).offset(y: -5)
                Text(first).padding(.top, 29)
                Text(second).padding(.top, 2)
                Spacer()
                Text(gift.expires).padding(.bottom, 39)
            }
            .font(.custom("Avenir Next", size: GiftSlotDropTokens.TypeStyle.offerSmall).weight(.medium))
            .foregroundStyle(ink)
        }
        .frame(width: GiftSlotDropTokens.Layout.offerW, height: GiftSlotDropTokens.Layout.offerH)
        .compositingGroup()
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .scaleEffect(CGFloat(pose.scale))
        .position(x: width / 2, y: CGFloat(pose.y))
        .opacity(pose.opacity)
    }
}

private struct GiftSlotDropConfetti: View {
    let model: GiftSlotDropModel

    var body: some View {
        Canvas { context, _ in
            let colors = [GiftSlotDropTokens.color(GiftSlotDropTokens.Colors.yellow), Color.white, GiftSlotDropTokens.color(model.gift.glow)]
            for piece in model.confetti where piece.life > 0 {
                let fade = min(1, piece.life / 0.4)
                let width = 10 * fade
                let height = (5.2 * fade * abs(cos(piece.angle * 1.7)) + 1)
                var context = context
                context.translateBy(x: CGFloat(piece.x), y: CGFloat(piece.y))
                context.rotate(by: .radians(piece.angle))
                context.fill(Path(CGRect(x: CGFloat(-width / 2), y: CGFloat(-height / 2), width: CGFloat(width), height: CGFloat(height))), with: .color(colors[piece.color]))
            }
        }
        .allowsHitTesting(false)
    }
}

private struct GiftSlotDropAssetImage: View {
    let name: String

    var body: some View {
        if let image = UIImage(named: name) {
            Image(uiImage: image).resizable()
        } else {
            Color.clear.onAppear { assertionFailure("Missing GiftSlotDrop asset: \(name)") }
        }
    }
}
