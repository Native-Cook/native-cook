import SwiftUI
import UIKit

/*
 AddressLookupMapStreetMap — a town drawn in code: river, parks, house footprints, streets with
 casings, one main road and labels. Nothing comes from a map service. A camera (centre + scale, in
 the town's world units) looks at it; the pin is drawn in screen space at the middle of the map,
 so a drag moves the town under it, the way delivery apps let you fine-tune a drop-off point.
*/

struct AddressLookupMapCamera {
    var x: Double
    var y: Double
    var s: Double
}

struct AddressLookupMapPinPose {
    /// Height above the ground, pt (negative = up).
    var y: Double
    /// Touch-down squash 0 ... 1.
    var squash: Double
    var alpha: Double
    /// 0 ... 1 while the map is held: the pin floats off the ground.
    var lift: Double
    var ring1: Double
    var ring2: Double
}

enum AddressLookupMapColors {
    static let land = Color(hex: 0xECE9E1)
    static let house = Color(hex: 0xDAD5C8)
    static let houseShade = Color(hex: 0xCDC6B6)
    static let casing = Color(hex: 0xD6D0C1)
    static let street = Color(hex: 0xFBFAF6)
    static let main = Color(hex: 0xF7DFA6)
    static let mainCasing = Color(hex: 0xE2C27E)
    static let water = Color(hex: 0xBCD5E4)
    static let waterEdge = Color(hex: 0xA6C4D8)
    static let park = Color(hex: 0xD2E2BE)
    static let parkPath = Color(hex: 0xE4ECD6)
}

/// The town's paths and label bitmaps, built once.
final class AddressLookupMapTown {
    static let shared = AddressLookupMapTown()
    typealias D = AddressLookupMapData

    struct Label {
        let image: Image
        /// Where the image's top-left sits relative to the label's anchor, and its size (world units).
        let rect: CGRect
        let anchor: CGPoint
        let angle: Double
    }

    let houses: Path
    let shade: Path
    let streets: Path
    let main: Path
    let river: Path
    let riverEdge: Path
    let parkPaths: Path
    let parks: Path
    let streetLabels: [Label]
    let districtLabels: [Label]

    private init() {
        let footprint = D.houses()
        var h = Path()
        var sh = Path()
        for r in footprint {
            h.addRect(r)
            sh.addRect(r.offsetBy(dx: 1.6, dy: 2.2))
        }
        houses = h
        shade = sh

        var st = Path()
        for r in D.streets {
            st.move(to: r.points[0])
            for p in r.points.dropFirst() { st.addLine(to: p) }
        }
        st.move(to: D.crescentFrom)
        st.addQuadCurve(to: D.crescentTo, control: D.crescentCtrl)
        streets = st

        var m = Path()
        m.move(to: D.mainRoad.points[0])
        m.addLine(to: D.mainRoad.points[1])
        main = m

        var rv = Path()
        Self.smooth(D.riverNorth, into: &rv, move: true)
        Self.smooth(D.riverSouth.reversed(), into: &rv, move: false)
        rv.closeSubpath()
        river = rv
        var edge = Path()
        Self.smooth(D.riverNorth, into: &edge, move: true)
        Self.smooth(D.riverSouth, into: &edge, move: true)
        riverEdge = edge

        var pp = Path()
        var pk = Path()
        for (x0, y0, x1, y1, _) in D.parks {
            let cx = (x0 + x1) / 2, cy = (y0 + y1) / 2
            pp.move(to: CGPoint(x: x0 + 14, y: y1 - 18))
            pp.addQuadCurve(to: CGPoint(x: x1 - 14, y: y0 + 20), control: CGPoint(x: cx, y: cy - 10))
            pp.move(to: CGPoint(x: x0 + 20, y: y0 + 16))
            pp.addQuadCurve(to: CGPoint(x: x1 - 18, y: y1 - 14), control: CGPoint(x: cx - 10, y: cy + 20))
            pk.addRoundedRect(in: CGRect(x: x0 + 9, y: y0 + 9, width: x1 - x0 - 18, height: y1 - y0 - 18),
                              cornerSize: CGSize(width: 10, height: 10), style: .circular)
        }
        parkPaths = pp
        parks = pk

        // Labels, drawn once into bitmaps (a halo is a stroke under the fill, as in the Skia version).
        let streetFont = UIFont.systemFont(ofSize: 12, weight: .medium)
        let parkFont = UIFont.systemFont(ofSize: 12, weight: .semibold)
        let mainFont = UIFont.systemFont(ofSize: 13, weight: .semibold)
        let districtFont = UIFont.systemFont(ofSize: 40, weight: .bold)
        let labelInk = UIColor(red: 0x7E / 255, green: 0x77 / 255, blue: 0x6A / 255, alpha: 1)
        let halo = UIColor(red: 0xFB / 255, green: 0xFA / 255, blue: 0xF6 / 255, alpha: 1)

        var labels: [Label] = []
        for r in D.streets {
            labels.append(Self.label(r.name, font: streetFont, color: labelInk, halo: halo, haloWidth: 3,
                                     anchor: r.label, baseline: 4, angle: r.angle, scale: 5))
        }
        labels.append(Self.label("Lark Rise", font: streetFont, color: labelInk, halo: halo, haloWidth: 3,
                                 anchor: CGPoint(x: 805, y: 872), baseline: 4, angle: 0, scale: 5))
        labels.append(Self.label(D.mainRoad.name, font: mainFont, color: UIColor(red: 0x8C / 255, green: 0x6A / 255, blue: 0x22 / 255, alpha: 1),
                                 halo: nil, haloWidth: 0, anchor: D.mainRoad.label, baseline: 4.5, angle: D.mainRoad.angle, scale: 5))
        for (x0, y0, x1, y1, name) in D.parks {
            labels.append(Self.label(name, font: parkFont, color: UIColor(red: 0x6C / 255, green: 0x8A / 255, blue: 0x55 / 255, alpha: 1),
                                     halo: nil, haloWidth: 0, anchor: CGPoint(x: (x0 + x1) / 2, y: (y0 + y1) / 2 + 4), baseline: 0, angle: 0, scale: 5))
        }
        labels.append(Self.label("Wendle Water", font: streetFont, color: UIColor(red: 0x5F / 255, green: 0x86 / 255, blue: 0xA1 / 255, alpha: 1),
                                 halo: nil, haloWidth: 0, anchor: D.riverLabel, baseline: 4, angle: 0, scale: 5))
        streetLabels = labels

        let landUI = UIColor(red: 0xEC / 255, green: 0xE9 / 255, blue: 0xE1 / 255, alpha: 1)
        let districtInk = UIColor(red: 0x6E / 255, green: 0x67 / 255, blue: 0x5B / 255, alpha: 0.62)
        districtLabels = D.districts.map {
            Self.label($0.name, font: districtFont, color: districtInk, halo: landUI, haloWidth: 8,
                       anchor: CGPoint(x: $0.x, y: $0.y), baseline: 0, angle: 0, scale: 3)
        }
    }

    /// Smooth path through points (Catmull-Rom as cubic Béziers).
    private static func smooth(_ points: [CGPoint], into path: inout Path, move: Bool) {
        if move { path.move(to: points[0]) } else { path.addLine(to: points[0]) }
        for i in 0 ..< points.count - 1 {
            let p0 = points[max(0, i - 1)], p1 = points[i], p2 = points[i + 1], p3 = points[min(points.count - 1, i + 2)]
            let c1 = CGPoint(x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6)
            let c2 = CGPoint(x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6)
            path.addCurve(to: p2, control1: c1, control2: c2)
        }
    }

    /// A label as a bitmap; the text's left edge sits at -width/2 and its baseline at `baseline`
    /// below the anchor, in the label's rotated frame.
    private static func label(_ text: String, font: UIFont, color: UIColor, halo: UIColor?, haloWidth: CGFloat,
                              anchor: CGPoint, baseline: CGFloat, angle: Double, scale: CGFloat) -> Label {
        let w = (text as NSString).size(withAttributes: [.font: font]).width
        let pad = haloWidth / 2 + 1
        let size = CGSize(width: ceil(w + pad * 2), height: ceil(font.lineHeight + pad * 2))
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let ui = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            let origin = CGPoint(x: pad, y: pad)
            if let halo {
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: font, .strokeColor: halo, .strokeWidth: haloWidth / font.pointSize * 100,
                ]
                (text as NSString).draw(at: origin, withAttributes: attrs)
            }
            (text as NSString).draw(at: origin, withAttributes: [.font: font, .foregroundColor: color])
        }
        let rect = CGRect(x: -w / 2 - pad, y: baseline - font.ascender - pad, width: size.width, height: size.height)
        return Label(image: Image(uiImage: ui), rect: rect, anchor: anchor, angle: angle)
    }
}

/// Draws the town through the camera, the light over it, and the pin with its rings.
struct AddressLookupMapStreetMap: View {
    let width: CGFloat
    let height: CGFloat
    let camera: AddressLookupMapCamera
    let pin: AddressLookupMapPinPose
    let rotation: Double
    var accent = Color(hex: 0xE0562E)

    private static let pinHeadY: CGFloat = -36
    private static let pinR: CGFloat = 14
    private static let liftPt: CGFloat = 14

    static let pinPath: Path = {
        var p = Path()
        let r = pinR, hy = pinHeadY
        p.move(to: .zero)
        p.addCurve(to: CGPoint(x: -r, y: hy), control1: CGPoint(x: -2, y: -6), control2: CGPoint(x: -r, y: hy + 14))
        p.addArc(center: CGPoint(x: 0, y: hy), radius: r, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false)
        p.addCurve(to: .zero, control1: CGPoint(x: r, y: hy + 14), control2: CGPoint(x: 2, y: -6))
        p.closeSubpath()
        return p
    }()

    var body: some View {
        Canvas { ctx, size in
            let town = AddressLookupMapTown.shared
            let C = AddressLookupMapColors.self
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(C.land))
            let cx = width / 2, cy = height / 2

            var w = ctx
            w.translateBy(x: cx, y: cy)
            w.rotate(by: .radians(rotation))
            w.scaleBy(x: camera.s, y: camera.s)
            w.translateBy(x: -camera.x, y: -camera.y)

            w.fill(town.river, with: .color(C.water))
            w.stroke(town.riverEdge, with: .color(C.waterEdge), lineWidth: 2)
            w.fill(town.parks, with: .color(C.park))
            w.stroke(town.parkPaths, with: .color(C.parkPath), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            w.fill(town.shade, with: .color(C.houseShade))
            w.fill(town.houses, with: .color(C.house))
            w.stroke(town.streets, with: .color(C.casing), style: StrokeStyle(lineWidth: 15, lineCap: .round))
            w.stroke(town.main, with: .color(C.mainCasing), style: StrokeStyle(lineWidth: 28, lineCap: .butt))
            w.stroke(town.streets, with: .color(C.street), style: StrokeStyle(lineWidth: 12, lineCap: .round))
            w.stroke(town.main, with: .color(C.main), style: StrokeStyle(lineWidth: 24, lineCap: .butt))

            let s = camera.s
            let labelAlpha = AddressLookupMapMotion.clamp01((s - 0.46) / 0.24)
            let districtAlpha = 1 - AddressLookupMapMotion.clamp01((s - 0.4) / 0.22)
            if labelAlpha > 0.001 {
                var l = w
                l.opacity = labelAlpha
                for label in town.streetLabels { draw(label, in: l) }
            }
            if districtAlpha > 0.001 {
                var l = w
                l.opacity = districtAlpha
                for label in town.districtLabels { draw(label, in: l) }
            }

            // A soft light from the top-left and a vignette, so the drawing sits in the card like a lit surface.
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.18), location: 0),
                    .init(color: .white.opacity(0), location: 0.55),
                    .init(color: Color.rgba(60, 52, 40, 0.10), location: 1),
                ]),
                center: CGPoint(x: width * 0.3, y: height * 0.2), startRadius: 0, endRadius: max(width, height) * 0.95))

            // Rings, contact shadow, pin.
            let r1 = 6 + 34 * (1 - (1 - pin.ring1) * (1 - pin.ring1))
            let r2 = 6 + 34 * (1 - (1 - pin.ring2) * (1 - pin.ring2))
            let a1 = (pin.ring1 <= 0 || pin.ring1 >= 1) ? 0 : 0.5 * (1 - pin.ring1) * pin.alpha
            let a2 = (pin.ring2 <= 0 || pin.ring2 >= 1) ? 0 : 0.38 * (1 - pin.ring2) * pin.alpha
            if a1 > 0 {
                ctx.stroke(Path(ellipseIn: CGRect(x: cx - r1, y: cy - r1, width: r1 * 2, height: r1 * 2)), with: .color(accent.opacity(a1)), lineWidth: 2)
            }
            if a2 > 0 {
                ctx.stroke(Path(ellipseIn: CGRect(x: cx - r2, y: cy - r2, width: r2 * 2, height: r2 * 2)), with: .color(accent.opacity(a2)), lineWidth: 1.5)
            }
            guard pin.alpha > 0.001 else { return }
            let fall = min(1, -pin.y / 84)
            let k = 1 - 0.55 * max(fall, pin.lift * 0.6)
            let sw = 22 * k, shh = 7 * k
            let sa = pin.alpha * (0.32 - 0.2 * max(fall, pin.lift * 0.6))
            ctx.fill(Path(ellipseIn: CGRect(x: cx - sw / 2, y: cy - shh / 2, width: sw, height: shh)), with: .color(Color(hex: 0x2A2015).opacity(max(0, sa))))

            var p = ctx
            p.opacity = pin.alpha
            p.translateBy(x: cx, y: cy + pin.y - Self.liftPt * pin.lift)
            p.scaleBy(x: 1 + 0.1 * pin.squash, y: 1 - 0.14 * pin.squash)
            p.fill(Self.pinPath.offsetBy(dx: 0, dy: 1.5), with: .color(.black.opacity(0.18)))
            p.fill(Self.pinPath, with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color.rgba(255, 124, 76, 1), location: 0),
                    .init(color: accent, location: 0.55),
                    .init(color: Color(hex: 0xB23D1C), location: 1),
                ]),
                startPoint: CGPoint(x: -Self.pinR, y: Self.pinHeadY - Self.pinR), endPoint: CGPoint(x: Self.pinR, y: 0)))
            p.fill(Path(ellipseIn: CGRect(x: -5.2, y: Self.pinHeadY - 5.2, width: 10.4, height: 10.4)), with: .color(Color(hex: 0xFFF8F1)))
            p.fill(Path(ellipseIn: CGRect(x: -5 - 3.4, y: Self.pinHeadY - 6 - 3.4, width: 6.8, height: 6.8)), with: .color(.white.opacity(0.32)))
        }
        .frame(width: width, height: height)
    }

    private func draw(_ label: AddressLookupMapTown.Label, in ctx: GraphicsContext) {
        var c = ctx
        c.translateBy(x: label.anchor.x, y: label.anchor.y)
        if label.angle != 0 { c.rotate(by: .degrees(label.angle)) }
        c.draw(label.image, in: label.rect)
    }
}
