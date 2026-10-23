import SwiftUI

/// The stamps that sit in their cells (hidden while their copy is out) and the moving one.
struct StampDiaryCalendarMovingLayers: View {
    typealias M = StampDiaryCalendarMotion

    var space: StampDiaryCalendarSpace
    var pose: M.Pose
    var actorMemory: Int

    var body: some View {
        ZStack {
            StampDiaryCalendarPile(space: space, dim: pose.pileDim, nudge: pose.pileNudge, landing: M.landingQuad(pose.memory))
            ForEach(0..<StampDiaryCalendarData.memories.count, id: \.self) { index in
                cell(index)
            }
            if pose.active == 1 && pose.memory == actorMemory && pose.opacity > 0 {
                actor
            }
        }
        .frame(width: space.size.width, height: space.size.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    @ViewBuilder
    private func cell(_ index: Int) -> some View {
        let hidden = pose.active == 1 && pose.memory == index && actorMemory == index
        if !hidden {
            let mem = StampDiaryCalendarData.memories[index]
            let c = M.cellCenter(mem.day, M.may2026)
            StampDiaryCalendarShaderStamp(
                space: space,
                projection: .init(cx: c.x, cy: c.y, skew: 0, rotX: 0, rotY: 0, rotZ: 0, sx: 1, sy: 1, depth: 1e6),
                halfWidth: mem.w / 2 + 2, halfHeight: mem.h / 2 + 2,
                art: StampDiaryCalendarArt.smallImage(mem.art), artSide: StampDiaryCalendarArt.smallSize,
                mark: StampDiaryCalendarArt.blank,
                w: mem.w, h: mem.h, aa: 0.7 / (3 * space.s), dim: 1,
                holes: M.holesOf(index)
            )
        }
    }

    @ViewBuilder
    private var actor: some View {
        let p = pose
        let mem = StampDiaryCalendarData.memories[actorMemory]
        let pad = M.stampPad * max(p.w, p.h)
        let depth = M.Flight.perspective
        // The actor group multiplies the opacity once more, as in the Skia version.
        let shadowOpacity = p.opacity * p.opacity * (0.2 + 0.35 * p.height)
        StampDiaryCalendarShadow(
            space: space,
            projection: .init(cx: p.cx + 5 + 9 * p.height, cy: p.cy + 8 + 16 * p.height, skew: p.skew, rotX: p.rotX * 0.6, rotY: p.rotY * 0.6, rotZ: p.rotZ, sx: p.lift * 0.97, sy: p.lift * 0.97, depth: depth),
            w: p.w, h: p.h, sigma: 2 + 12 * p.height, opacity: shadowOpacity
        )
        StampDiaryCalendarShaderStamp(
            space: space,
            projection: .init(cx: p.cx, cy: p.cy, skew: p.skew, rotX: p.rotX, rotY: p.rotY, rotZ: p.rotZ, sx: p.lift * p.sx, sy: p.lift * p.sy, depth: depth),
            halfWidth: p.w / 2 + pad, halfHeight: p.h / 2 + pad,
            art: StampDiaryCalendarArt.image(mem.art), artSide: StampDiaryCalendarArt.size,
            mark: StampDiaryCalendarArt.postmark(day: mem.day),
            w: p.w, h: p.h, aa: 0.7 / (3 * space.s * max(0.5, p.lift)), dim: 1,
            holes: M.holesOf(actorMemory),
            shadow: p.peelShadow, curl: p.curl,
            sheenPos: p.sheenPos, sheenAmt: p.sheenAmt, inkWet: p.inkWet, inkOn: p.inkOn,
            inkSpread: p.inkSpread, inkThreshold: p.inkThreshold
        )
        .opacity(p.opacity)
    }
}
