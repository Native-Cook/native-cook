import SwiftUI

// Port of EmeraldRecastScene.tsx: the 393 x 335 arena. The glass bodies are one shader pass;
// portraits, names, scores, numerals, crown and caption are siblings above it.

enum EmeraldTierSceneConst {
    static let LABELS: [EmeraldTierRecast.ActorID: String] = [.ari: "Ari Vale", .milo: "Milo Chen", .juno: "Juno Reed", .theo: "Theo Park"]
    static let INITIAL_SCORES: [EmeraldTierRecast.ActorID: Int] = [.ari: 1000, .milo: 950, .juno: 900, .theo: 850]
    /// [from, to] rank of each plinth.
    static let RANKS: [EmeraldTierRecast.ActorID: (Int, Int)] = [.ari: (1, 2), .milo: (2, 3), .juno: (3, 4), .theo: (4, 1)]
    static let RANK_COLORS: [Int: String] = [1: "#FFD66C", 2: "#BBBDB6", 3: "#BD8C66", 4: "#9EA39E"]
    static let RANK_LINE = 40.0
}

private typealias ActorID = EmeraldTierRecast.ActorID

/// One participant: portrait, name, score and the row's "04" numeral.
private struct EmeraldTierParticipant<AvatarView: View>: View {
    let id: ActorID
    let frame: EmeraldTierRecast.Frame
    let winner: Bool
    let avatar: (ActorID, Double) -> AvatarView

    var body: some View {
        let a = frame.actors[id]
        let label = EmeraldTierSceneConst.LABELS[id] ?? ""
        let oldRank = [ActorID.ari: 1, .milo: 2, .juno: 3, .theo: 4][id] ?? 1
        let newRank = [ActorID.ari: 2, .milo: 3, .juno: 4, .theo: 1][id] ?? 1
        let semanticScore = id == .theo && winner ? 1050 : (EmeraldTierSceneConst.INITIAL_SCORES[id] ?? 0)
        ZStack(alignment: .topLeading) {
            // face
            avatar(id, 70)
                .frame(width: 70, height: 70)
                .scaleEffect(a.size / 70)
                .offset(x: a.cx - 35, y: a.cy - 35)
                .zIndex(id == .theo ? 12 : 10)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(label), rank \(winner ? newRank : oldRank), \(semanticScore) points")
            // name: centered on the podium, left-aligned in the row
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .tracking(-0.1)
                .foregroundStyle(Color.white)
                .lineLimit(1)
                .frame(width: 110, height: 17, alignment: a.rowMix > 0.5 ? .leading : .center)
                .frame(width: 110, height: 18, alignment: .top)
                .opacity(a.labelOpacity)
                .offset(x: a.nameX, y: a.nameY)
                .zIndex(14)
                .accessibilityHidden(true)
            // score
            HStack(spacing: 3) {
                Text(String(Int(a.score)))
                    .font(.system(size: 14).monospacedDigit())
                    .foregroundStyle(Color.white)
                    .frame(width: id == .theo ? 35 : nil, height: id == .theo ? 21 : 19, alignment: id == .theo ? .trailing : .center)
                Text("ϟ")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundStyle(EmeraldTierPalette.color("#FFC846"))
                    .frame(height: 21)
            }
            .fixedSize()
            .frame(width: 54, height: 21)
            .opacity(a.labelOpacity)
            .offset(x: a.scoreX - 27, y: a.scoreY)
            .zIndex(14)
            .accessibilityHidden(true)
            // row numeral
            Text("04")
                .font(.system(size: 12))
                .foregroundStyle(EmeraldTierPalette.color("#B5B9B5"))
                .frame(height: 18)
                .fixedSize()
                .opacity(a.rowMix * a.labelOpacity)
                .offset(x: 33, y: 303)
                .zIndex(14)
                .accessibilityHidden(true)
        }
        .frame(width: 393, height: 335, alignment: .topLeading)
        .allowsHitTesting(false)
    }
}

/// Each plinth keeps its numeral and rolls it to the new order, like a mechanical counter.
private struct EmeraldTierRank: View {
    let id: ActorID
    let frame: EmeraldTierRecast.Frame

    var body: some View {
        let (from, to) = EmeraldTierSceneConst.RANKS[id] ?? (1, 1)
        let b = frame.surfaces[id]
        let r = frame.roll
        // Laurels belong to first place: they leave Ari's plinth and grow on Theo's.
        let shown = from == 1 ? 1 - r : to == 1 ? r : 0
        let hasLaurel = from == 1 || to == 1
        HStack(spacing: 5) {
            if hasLaurel { laurel(shown: shown, mirror: false) }
            VStack(spacing: 0) {
                ForEach([from, to], id: \.self) { rank in
                    Text(String(rank))
                        .font(.system(size: 29, weight: .bold).monospacedDigit())
                        .foregroundStyle(EmeraldTierPalette.color(EmeraldTierSceneConst.RANK_COLORS[rank] ?? "#FFFFFF"))
                        .frame(minWidth: 22, minHeight: EmeraldTierSceneConst.RANK_LINE, maxHeight: EmeraldTierSceneConst.RANK_LINE)
                }
            }
            .offset(y: -r * EmeraldTierSceneConst.RANK_LINE)
            .frame(minWidth: 22, minHeight: EmeraldTierSceneConst.RANK_LINE, maxHeight: EmeraldTierSceneConst.RANK_LINE, alignment: .top)
            .clipped()
            if hasLaurel { laurel(shown: shown, mirror: true) }
        }
        .frame(width: 72, height: 40)
        .opacity(b.rankOpacity * (1 - frame.actors[id].rowMix))
        .offset(x: b.x + b.width / 2 - 36, y: b.y + b.height / 2 - 20)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func laurel(shown: Double, mirror: Bool) -> some View {
        Text("❧")
            .font(.system(size: 25))
            .foregroundStyle(EmeraldTierPalette.color("#BDA448"))
            .scaleEffect(x: mirror ? -1 : 1, y: 1)
            .opacity(shown)
            .scaleEffect(0.7 + 0.3 * shown)
    }
}

/// The boundary between the podium (promoted) and the list lights up where someone crosses it.
private struct EmeraldTierPromotionLine: View {
    let frame: EmeraldTierRecast.Frame

    var body: some View {
        let theo = frame.actors.theo
        let juno = frame.actors.juno
        let nearRising = exp(-pow((theo.cy - EmeraldTierRecast.PROMOTION_LINE_Y) / 22, 2))
        let nearFalling = exp(-pow((juno.cy - EmeraldTierRecast.PROMOTION_LINE_Y) / 22, 2))
        ZStack(alignment: .topLeading) {
            Rectangle().fill(EmeraldTierPalette.color("#7DF0B526")).frame(width: 355 - 96, height: 1).offset(y: 6.5)
            flare(core: "#B9FFD9", shadow: "#5CF2A8", near: nearRising, cx: theo.cx, opacity: nearRising)
            flare(core: "#E8F3EE", shadow: "#9DB5AA", near: nearFalling, cx: juno.cx, opacity: nearFalling * 0.55)
            Text("▲ PROMOTION ZONE")
                .font(.system(size: 7.5, weight: .bold))
                .tracking(1.1)
                .foregroundStyle(EmeraldTierPalette.color("#7DF0B5"))
                .fixedSize()
                .frame(height: 14)
                .frame(width: 355, height: 14, alignment: .trailing)
        }
        .frame(width: 355, height: 14, alignment: .topLeading)
        .offset(x: 19, y: EmeraldTierRecast.PROMOTION_LINE_Y - 7)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    /// 120 x 14 box (left -19 of the line), with a 120 x 2 core and a soft glow; scaled in x about its centre.
    private func flare(core: String, shadow: String, near: Double, cx: Double, opacity: Double) -> some View {
        RoundedRectangle(cornerRadius: 1)
            .fill(EmeraldTierPalette.color(core))
            .frame(width: 120, height: 2)
            .shadow(color: EmeraldTierPalette.color(shadow).opacity(0.9), radius: 6)
            .frame(width: 120, height: 14)
            .scaleEffect(x: 0.6 + near * 0.4, y: 1)
            .offset(x: -19 + cx - 60, y: 0)
            .opacity(opacity)
    }
}

private struct EmeraldTierCrownSpark: View {
    let index: Int
    let sparkle: Double

    var body: some View {
        let angle = -Double.pi / 2 + (Double(index) - 2.5) * 0.5
        let p = sparkle
        let reach = 10 + 16 * (1 - pow(1 - p, 2))
        RoundedRectangle(cornerRadius: 1)
            .fill(EmeraldTierPalette.color("#FFE7A3"))
            .frame(width: 5, height: 5)
            .scaleEffect(1 - p * 0.6)
            .rotationEffect(.degrees(45))
            .offset(x: cos(angle) * reach - 2.5, y: sin(angle) * reach - 2.5)
            .opacity(p > 0 && p < 1 ? sin(p * .pi) : 0)
    }
}

/// The arena. `rank` is the shared rank frame; the recast frame is derived from it exactly as in RN.
struct EmeraldTierScene<AvatarView: View>: View {
    let rank: EmeraldTierMotion.Frame
    let solid: Bool
    let avatar: (EmeraldTierRecast.ActorID, Double) -> AvatarView

    var body: some View {
        var frame = EmeraldTierRecast.frame(rank.progress, reduced: rank.reduced)
        if !rank.allowResponse { frame.response = 0; frame.light.response = 0 }
        let winner = frame.phase >= 0.90
        let edgeY = rank.reduced ? 0 : (rank.progress - rank.rawProgress) * 18
        let c = frame.crown
        return ZStack(alignment: .topLeading) {
            EmeraldTierSurface(frame: frame, solid: solid)
            ForEach(ActorID.allCases, id: \.rawValue) { id in
                EmeraldTierParticipant(id: id, frame: frame, winner: winner, avatar: avatar)
            }
            ForEach(ActorID.allCases, id: \.rawValue) { id in
                EmeraldTierRank(id: id, frame: frame).zIndex(8)
            }
            EmeraldTierPromotionLine(frame: frame).zIndex(9)
            // XP caption
            Text("+200 XP")
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(EmeraldTierPalette.color("#B2F9D8"))
                .fixedSize()
                .frame(width: 68, height: 15)
                .frame(width: 68, height: 19, alignment: .top)
                .opacity(frame.gain.opacity)
                .offset(x: frame.gain.x, y: frame.gain.y)
                .zIndex(16)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            // crown: 34 x 30, tilting and squashing about 50% 90%
            ZStack(alignment: .topLeading) {
                EmeraldTierArtMaterial(image: EmeraldTierImages.crown, width: 34, height: 30,
                                       responsePhase: rank.responsePhase, response: rank.response)
                    .scaleEffect(x: 1 + 0.09 * c.squash, y: 1 - 0.13 * c.squash, anchor: UnitPoint(x: 0.5, y: 0.9))
                    .rotationEffect(.degrees(c.rotation), anchor: UnitPoint(x: 0.5, y: 0.9))
                ZStack(alignment: .topLeading) {
                    ForEach(0..<6, id: \.self) { index in
                        EmeraldTierCrownSpark(index: index, sparkle: c.sparkle)
                    }
                }
                .frame(width: 0, height: 0)
                .offset(x: 17, y: 18)
            }
            .frame(width: 34, height: 30, alignment: .topLeading)
            .opacity(c.opacity)
            .offset(x: c.x, y: c.y)
            .zIndex(18)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .frame(width: 393, height: 335, alignment: .topLeading)
        .offset(y: edgeY)
    }
}
