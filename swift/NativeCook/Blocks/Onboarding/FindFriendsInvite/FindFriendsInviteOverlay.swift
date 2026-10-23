import SwiftUI
import UIKit

/// Everything that leaves the layout: the folded paper and its flight (a Canvas), the liquid contacts
/// (a Metal field on a band), and the faces riding in the blob and the droplets (a second Canvas).
struct FindFriendsInviteOverlay: View {
    typealias M = FindFriendsInviteMotion
    typealias T = FindFriendsInviteTheme
    typealias Fold = FindFriendsInviteFold
    typealias Goo = FindFriendsInviteGoo

    let paper: Fold.Frame
    let paperWidth: Double
    let paperHeight: Double
    let goo: Goo.Frame
    let gooGeo: Goo.Geo
    let faces: [FindFriendsInviteFaceSpec]
    let dropFaces: [FindFriendsInviteDropSpec]
    let width: Double
    let height: Double
    let warm: Bool

    private var band: (y: Double, h: Double) {
        var top = Double.infinity, bottom = -Double.infinity
        for b in gooGeo.rest {
            top = min(top, b.y - b.r)
            bottom = max(bottom, b.y + b.r)
        }
        return (top - M.Goo.bandPad, bottom - top + 2 * M.Goo.bandPad)
    }

    var body: some View {
        let band = band
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                drawPaper(context)
            }
            .frame(width: width, height: height)

            if goo.on {
                Color.white
                    .frame(width: width, height: band.h)
                    .colorEffect(ShaderLibrary.findFriendsInviteGoo(
                        .floatArray(goo.data), .float(goo.goo), .float(goo.gloss), .float2(0, band.y)))
                    .offset(y: band.y)
            }

            Canvas { context, _ in
                drawFaces(context)
            }
            .frame(width: width, height: height)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .opacity(warm ? M.Warm.alpha : 1)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: paper

    private static let gradient = Gradient(stops: T.ColorToken.inviteStops.map { Gradient.Stop(color: $0.1, location: $0.0) })

    private static func project(_ h: [Double], _ p: CGPoint) -> CGPoint {
        let w = h[6] * p.x + h[7] * p.y + h[8]
        return CGPoint(x: (h[0] * p.x + h[1] * p.y + h[2]) / w, y: (h[3] * p.x + h[4] * p.y + h[5]) / w)
    }

    private static func path(_ pts: [CGPoint], _ h: [Double]) -> Path {
        var p = Path()
        for (i, q) in pts.enumerated() {
            let s = project(h, q)
            if i == 0 { p.move(to: s) } else { p.addLine(to: s) }
        }
        p.closeSubpath()
        return p
    }

    private func outline(_ i: Int, _ darts: [[CGPoint]]) -> [CGPoint] {
        let radius = min(paper.radius, paperHeight / 2)
        switch i {
        case 0: return Fold.sheetOutline(paperWidth, paperHeight, radius: radius, side: paper.flat ? .full : .left)
        case 1: return Fold.sheetOutline(paperWidth, paperHeight, radius: radius, side: .right)
        default: return darts[i - 2]
        }
    }

    private func drawPaper(_ context: GraphicsContext) {
        let any = paper.vis.contains { $0 > 0 }
        if !any && paper.trail.isEmpty { return }
        let darts = Fold.dartOutlines(paperWidth, paperHeight)

        // Shadows on the card: one blurred layer.
        if any {
            var sc = context
            sc.addFilter(.blur(radius: paper.shadowBlur))
            sc.opacity = paper.shadowOpacity
            sc.drawLayer { layer in
                for i in Fold.drawOrder where paper.vis[i] > 0 {
                    layer.fill(Self.path(outline(i, darts), paper.shadows[i]), with: .color(.black.opacity(paper.vis[i])))
                }
            }
        }

        // Contrail: six strips of the ribbon, each a little more opaque toward the dart.
        if paper.trailOpacity > 0 && paper.trail.count >= 4 {
            var tc = context
            tc.addFilter(.blur(radius: M.Contrail.blur))
            tc.opacity = paper.trailOpacity
            let K = M.Contrail.samples, S = M.Contrail.segments
            tc.drawLayer { layer in
                for j in 0..<S {
                    let a = (j * (K - 1)) / S
                    let b = ((j + 1) * (K - 1)) / S
                    var p = Path()
                    p.move(to: paper.trail[2 * a])
                    for k in stride(from: a + 1, through: b, by: 1) { p.addLine(to: paper.trail[2 * k]) }
                    for k in stride(from: b, through: a, by: -1) { p.addLine(to: paper.trail[2 * k + 1]) }
                    p.closeSubpath()
                    let alpha = M.Contrail.opacityHead * Double(j + 1) / Double(S)
                    layer.fill(p, with: .color(T.ColorToken.planeInk.opacity(alpha)))
                }
            }
        }

        // The paper: the button's own gradient printed on every face, lit from the upper left.
        for i in Fold.drawOrder where paper.vis[i] > 0 {
            let flat = outline(i, darts)
            let h = paper.mats[i]
            let screen = Self.path(flat, h)
            var fc = context
            let vis = paper.vis[i]
            let draw: (inout GraphicsContext) -> Void = { c in
                // Gradient through the homography's local affine part, clipped to the exact outline.
                var gc = c
                gc.clip(to: screen)
                var minX = Double.infinity, maxX = -Double.infinity, minY = Double.infinity, maxY = -Double.infinity
                for q in flat { minX = min(minX, q.x); maxX = max(maxX, q.x); minY = min(minY, q.y); maxY = max(maxY, q.y) }
                let p0 = CGPoint(x: (minX + maxX) / 2, y: (minY + maxY) / 2)
                let w = h[6] * p0.x + h[7] * p0.y + h[8]
                let X = h[0] * p0.x + h[1] * p0.y + h[2]
                let Y = h[3] * p0.x + h[4] * p0.y + h[5]
                let j00 = (h[0] * w - X * h[6]) / (w * w), j01 = (h[1] * w - X * h[7]) / (w * w)
                let j10 = (h[3] * w - Y * h[6]) / (w * w), j11 = (h[4] * w - Y * h[7]) / (w * w)
                let q0 = CGPoint(x: X / w, y: Y / w)
                gc.concatenate(CGAffineTransform(a: j00, b: j10, c: j01, d: j11,
                                                 tx: q0.x - j00 * p0.x - j01 * p0.y, ty: q0.y - j10 * p0.x - j11 * p0.y))
                gc.fill(Path(CGRect(x: -2, y: -2, width: paperWidth + 4, height: paperHeight + 4)),
                        with: .linearGradient(Self.gradient, startPoint: .zero, endPoint: CGPoint(x: paperWidth, y: 0)))
                if paper.dark[i] > 0 { c.fill(screen, with: .color(.black.opacity(paper.dark[i]))) }
                if paper.bright[i] > 0 { c.fill(screen, with: .color(.white.opacity(paper.bright[i]))) }
            }
            if vis < 0.999 {
                fc.opacity = vis
                fc.drawLayer { layer in
                    var l = layer
                    draw(&l)
                }
            } else {
                draw(&fc)
            }
        }
    }

    // MARK: faces on the blob and on the droplets

    private func drawFaces(_ context: GraphicsContext) {
        guard goo.on else { return }
        for (j, f) in dropFaces.enumerated() where goo.drops[j].live {
            let d = goo.drops[j]
            var c = context
            c.opacity = d.o
            c.translateBy(x: d.x, y: d.y)
            c.scaleBy(x: d.s, y: d.s)
            c.clip(to: Path(ellipseIn: CGRect(x: -f.r, y: -f.r, width: 2 * f.r, height: 2 * f.r)))
            if f.mirror { c.scaleBy(x: -1, y: 1) }
            let img = 2 * f.r * f.head
            c.draw(c.resolve(Image(uiImage: FindFriendsInviteAssets.image(f.key))),
                   in: CGRect(x: -img / 2, y: -img / 2 + f.dy * 2 * f.r, width: img, height: img))
        }
        for (i, f) in faces.enumerated() {
            let s = goo.faces[i]
            var c = context
            c.opacity = s.o
            c.translateBy(x: f.cx + s.tx, y: f.cy + s.ty)
            c.scaleBy(x: s.s, y: s.s)
            let r = f.d / 2
            c.clip(to: Path(ellipseIn: CGRect(x: -r, y: -r, width: f.d, height: f.d)))
            let img = f.d * f.head
            c.draw(c.resolve(Image(uiImage: FindFriendsInviteAssets.image(f.key))),
                   in: CGRect(x: -img / 2, y: -img / 2 + f.dy * f.d, width: img, height: img))
        }
    }
}

struct FindFriendsInviteFaceSpec {
    let key: String
    let cx: Double, cy: Double, d: Double, head: Double, dy: Double
}

/// A droplet's face: the head image, its scale in a circle (x diameter) and offset, the droplet's radius.
struct FindFriendsInviteDropSpec {
    let key: String
    let head: Double, dy: Double
    let mirror: Bool
    let r: Double
}
