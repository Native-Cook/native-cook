import SwiftUI

private struct SaveToCollectionFlightGhost: View {
    let card: SaveToCollectionTokens.CardToken
    let from: CGRect
    let to: CGPoint
    let rawProgress: Double
    let lag: Int
    let smear: Bool

    var body: some View {
        let delayed = max(rawProgress - Double(lag) * SaveToCollectionTokens.Save.trailStep, 0)
        let progress = SaveToCollectionMath.cubicInOut(delayed)
        let centre = SaveToCollectionMath.curve(
            from: CGPoint(x: from.midX, y: from.midY),
            to: to,
            arc: SaveToCollectionTokens.Save.arc,
            progress: progress
        )
        let ahead = SaveToCollectionMath.curve(
            from: CGPoint(x: from.midX, y: from.midY),
            to: to,
            arc: SaveToCollectionTokens.Save.arc,
            progress: min(progress + SaveToCollectionTokens.FlightStyle.speedSample, 1)
        )
        let angle = atan2(ahead.y - centre.y, ahead.x - centre.x) - .pi / 2
        let uniformScale = SaveToCollectionMath.mix(1, SaveToCollectionTokens.Save.endScale, progress)
        let speed = SaveToCollectionMath.cubicSpeed(delayed) / SaveToCollectionTokens.FlightStyle.speedDivisor
        let multiplier = lag == 0
            ? (smear ? SaveToCollectionTokens.FlightStyle.smearMultiplier : SaveToCollectionTokens.FlightStyle.leadStretchMultiplier)
            : 1 + Double(lag) * SaveToCollectionTokens.FlightStyle.lagStretchMultiplier
        let stretch = 1 + SaveToCollectionTokens.Save.streak * speed * multiplier
        let opacity: Double = {
            if lag == 0 {
                let lead = 1 - max(rawProgress - SaveToCollectionTokens.FlightStyle.leadFadeStart, 0) /
                    (1 - SaveToCollectionTokens.FlightStyle.leadFadeStart)
                return smear ? min(speed * SaveToCollectionTokens.FlightStyle.smearFadeMultiplier, 1) * lead : lead
            }
            return (SaveToCollectionTokens.FlightStyle.ghostOpacity / pow(Double(lag), SaveToCollectionTokens.FlightStyle.ghostPower)) *
                min(rawProgress * SaveToCollectionTokens.FlightStyle.ghostFadeInMultiplier, 1) *
                (1 - rawProgress)
        }()
        let radius = SaveToCollectionMath.mix(
            SaveToCollectionTokens.Layout.cardRadius,
            min(from.width, from.height) / 2,
            progress * progress
        )
        let transform = CGAffineTransform(rotationAngle: angle)
            .scaledBy(x: 1, y: stretch)
            .rotated(by: -angle)

        SaveToCollectionImage.named(card.art)
            .resizable()
            .aspectRatio(contentMode: .fill)
            .frame(width: from.width, height: from.height)
            .blur(radius: lag == 0 && !smear ? 0 : SaveToCollectionTokens.Save.streakBlur)
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .projectionEffect(ProjectionTransform(transform))
            .scaleEffect(uniformScale)
            .opacity(opacity)
            .position(centre)
    }
}

struct SaveToCollectionFlightView: View {
    let flight: SaveToCollectionFlight
    @ObservedObject var model: SaveToCollectionModel
    let layout: SaveToCollectionLayout

    var body: some View {
        let progress = SaveToCollectionMath.clamp((model.clock - flight.start) / flight.duration)
        let card = SaveToCollectionTokens.cards[flight.card]

        ZStack(alignment: .topLeading) {
            if flight.isDrop {
                let eased = SaveToCollectionMath.cubicInOut(progress)
                let centre = CGPoint(
                    x: SaveToCollectionMath.mix(flight.from.midX, layout.bookmark.x, eased),
                    y: SaveToCollectionMath.mix(flight.from.midY, layout.bookmark.y, eased)
                )
                SaveToCollectionImage.named(card.art)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: flight.from.width, height: flight.from.height)
                    .clipShape(RoundedRectangle(cornerRadius: SaveToCollectionTokens.Layout.cardRadius, style: .continuous))
                    .scaleEffect(SaveToCollectionMath.mix(
                        flight.startScale,
                        SaveToCollectionTokens.Save.endScale,
                        eased
                    ))
                    .opacity(1 - max(progress - SaveToCollectionTokens.FlightStyle.leadFadeStart, 0) /
                        (1 - SaveToCollectionTokens.FlightStyle.leadFadeStart))
                    .position(centre)
            } else {
                ForEach(Array((1 ... SaveToCollectionTokens.Save.trailCount).reversed()), id: \.self) { lag in
                    SaveToCollectionFlightGhost(
                        card: card,
                        from: flight.from,
                        to: layout.bookmark,
                        rawProgress: progress,
                        lag: lag,
                        smear: false
                    )
                }
                SaveToCollectionFlightGhost(
                    card: card,
                    from: flight.from,
                    to: layout.bookmark,
                    rawProgress: progress,
                    lag: 0,
                    smear: false
                )
                SaveToCollectionFlightGhost(
                    card: card,
                    from: flight.from,
                    to: layout.bookmark,
                    rawProgress: progress,
                    lag: 0,
                    smear: true
                )
            }
        }
        .accessibilityHidden(true)
    }
}

private struct SaveToCollectionDrop: Identifiable {
    let id: Int
    let asset: String
    let x: Double
    let width: Double
    let start: Double
    let duration: Double
    let y0: Double
}

struct SaveToCollectionStream: View {
    let progress: Double
    let layout: SaveToCollectionLayout
    let rise: Double

    private static let drops: [SaveToCollectionDrop] = {
        var random = SaveToCollectionRandom(seed: SaveToCollectionTokens.Stream.seed)
        let assets = SaveToCollectionTokens.streamAssets
        return (0 ..< SaveToCollectionTokens.Stream.count).map { index in
            let big = random.next() < SaveToCollectionTokens.Stream.bigProbability
            let x = (random.next() - 0.5) * 2 * SaveToCollectionTokens.Stream.spread
            let range = big ? SaveToCollectionTokens.Stream.bigWidth : SaveToCollectionTokens.Stream.smallWidth
            let width = range.lowerBound + random.next() * (range.upperBound - range.lowerBound)
            let start = Double(index) / Double(SaveToCollectionTokens.Stream.count) * SaveToCollectionTokens.Stream.pour
            let duration = min(
                SaveToCollectionTokens.Stream.fall * (
                    SaveToCollectionTokens.Stream.fallMinFactor +
                        random.next() * SaveToCollectionTokens.Stream.fallVariation
                ),
                1 - Double(index) / Double(SaveToCollectionTokens.Stream.count) * SaveToCollectionTokens.Stream.pour
            )
            let y0 = SaveToCollectionTokens.Stream.yRange.lowerBound +
                random.next() * (SaveToCollectionTokens.Stream.yRange.upperBound - SaveToCollectionTokens.Stream.yRange.lowerBound)
            return .init(id: index, asset: assets[index % assets.count], x: x, width: width, start: start, duration: duration, y0: y0)
        }
    }()

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Self.drops) { drop in
                let local = SaveToCollectionMath.clamp((progress - drop.start) / drop.duration)
                let eased = SaveToCollectionMath.cubicInOut(local)
                let speed = SaveToCollectionMath.cubicSpeed(local) / SaveToCollectionTokens.FlightStyle.speedDivisor
                let centreX = layout.bookmark.x + drop.x * layout.scale * (1 - eased * eased * eased)
                let targetY = layout.bookmark.y + (layout.riseY - layout.bookmark.y) * rise
                let startY = drop.y0 * layout.scale
                let centreY = startY + (targetY - startY) * eased
                let scale = (drop.width / SaveToCollectionTokens.Stream.cardWidth) * layout.scale *
                    (1 - SaveToCollectionTokens.Stream.endScaleLoss * eased * eased * eased)
                let opacity = local <= 0 || local >= 1
                    ? 0
                    : min(local * SaveToCollectionTokens.Stream.fadeInMultiplier, 1) *
                        (1 - max(local - SaveToCollectionTokens.Stream.fadeOutStart, 0) /
                            (1 - SaveToCollectionTokens.Stream.fadeOutStart))

                SaveToCollectionImage.named(drop.asset)
                    .resizable()
                    .frame(
                        width: SaveToCollectionTokens.Stream.assetWidth,
                        height: SaveToCollectionTokens.Stream.assetHeight
                    )
                    .scaleEffect(x: scale, y: scale * (1 + SaveToCollectionTokens.Stream.stretch * speed))
                    .opacity(opacity)
                    .position(x: centreX, y: centreY)
            }
        }
        .accessibilityHidden(true)
    }
}

private struct SaveToCollectionRandom {
    private var state: Int

    init(seed: Int) {
        state = seed
    }

    mutating func next() -> Double {
        state = (state * SaveToCollectionTokens.Stream.randomMultiplier) %
            SaveToCollectionTokens.Stream.randomModulus
        return Double(state - 1) / SaveToCollectionTokens.Stream.randomDivisor
    }
}

extension SaveToCollectionMath {
    static func curve(from: CGPoint, to: CGPoint, arc: Double, progress: Double) -> CGPoint {
        let middle = CGPoint(x: (from.x + to.x) / 2, y: (from.y + to.y) / 2)
        let dx = to.x - from.x
        let dy = to.y - from.y
        let side = from.x < to.x ? -1.0 : 1.0
        let control = CGPoint(
            x: middle.x + side * abs(dy) * arc,
            y: middle.y - abs(dx) * arc * 0.5
        )
        let t = clamp(progress)
        let u = 1 - t
        return CGPoint(
            x: u * u * from.x + 2 * u * t * control.x + t * t * to.x,
            y: u * u * from.y + 2 * u * t * control.y + t * t * to.y
        )
    }
}
