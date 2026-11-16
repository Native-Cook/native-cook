import SwiftUI

struct FlipFocusBadgeFrame: View {
    let frame: FlipFocusBadgeDriver.Frame
    let layout: FlipFocusBadgeLayout
    let displayScale: Double
    let reduce: Bool
    let onSelect: (Int) -> Void
    private let resources = FlipFocusBadgeResources.shared
    private typealias R = FlipFocusBadgeResources
    private typealias M = FlipFocusBadgeMotion

    var body: some View {
        let l = layout, s = l.scale, f = frame
        ZStack(alignment: .topLeading) {
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.flipFocusBadgeBackdrop(.floatArray(l.backdrop(f.look.yaw))))
                .frame(width: l.width, height: l.height)
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in drawPage(c) }
                .allowsHitTesting(false)
                .accessibilityLabel("\(frame.labA) hours to \(frame.labA + 1) hours. \(frame.hint > 0.5 ? "focusing… flip back to finish" : "flip your phone to start a focus session…")")
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.flipFocusBadgeScene(
                    .floatArray(FlipFocusBadgeGeometry.uniforms(f.look, scale: s, displayScale: displayScale, reduce: reduce)),
                    .image(resources.atlas)))
                .frame(width: l.width, height: 300 * s)
                .opacity(f.fade)
                .offset(y: l.badgeTop)
                .allowsHitTesting(false)
            topRow
            sheet
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
    }

    private func drawPage(_ raw: GraphicsContext) {
        let l = layout, f = frame, s = l.scale
        var c = raw
        c.scaleBy(x: s, y: s)
        let x = 109.3, y = l.yc(468) / s, w = 183.0, h = 3.7
        let track = Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: h / 2)
        var tc = c
        tc.clip(to: track)
        tc.fill(track, with: .color(R.color(0x262626)))
        tc.fill(Path(roundedRect: CGRect(x: x, y: y, width: max(f.fill * w, h), height: h), cornerRadius: h / 2), with: .color(R.color(0x898989)))
        if f.sweep >= 0 {
            let sx = x + max(f.sweep, 0) * (w + 44) - 44
            tc.opacity = 0.6 * sin(.pi * f.sweep)
            tc.fill(Path(CGRect(x: sx, y: y, width: 44, height: h)), with: .linearGradient(
                Gradient(stops: [.init(color: .white.opacity(0), location: 0), .init(color: .white, location: 0.5), .init(color: .white.opacity(0), location: 1)]),
                startPoint: CGPoint(x: sx, y: 0), endPoint: CGPoint(x: sx + 44, y: 0)))
        }
        crossfade(c, a: "\(f.labA)h", b: "\(f.labB)h", k: f.labels, size: 11.5, x: x, base: l.yc(461.3) / s, color: 0x676767, align: 0)
        crossfade(c, a: "\(f.labA + 1)h", b: "\(f.labB + 1)h", k: f.labels, size: 11.5, x: x + w, base: l.yc(461.3) / s, color: 0x676767, align: 1)
        crossfade(c, a: "flip your phone to start a focus session…", b: "focusing… flip back to finish", k: f.hint,
                  size: 15.4, x: 201, base: l.yc(521.2) / s, color: 0x646464, slide: 8)
    }
    private func crossfade(_ c: GraphicsContext, a: String, b: String, k: Double, size: Double, x: Double, base: Double,
                           color: UInt32, align: Double = 0.5, slide: Double = 0) {
        resources.draw(c, a, "Medium", size, x, base - slide * k, color: color, alpha: max(1 - 2 * k, 0), align: align)
        resources.draw(c, b, "Medium", size, x, base + slide * (1 - k), color: color, alpha: max(2 * k - 1, 0), align: align)
    }
    private var topRow: some View {
        let l = layout, s = l.scale
        return ZStack(alignment: .topLeading) {
            chip(x: 19.3, y: 82.3, w: 64.4, h: 33.7, pad: 6, gap: 3, count: "75", kind: 0, label: "75 day streak")
            chip(x: 94.3, y: 82, w: 65.4, h: 34.7, pad: 8.8, gap: 5, count: "17", kind: 1, label: "17 trophies")
            disc(kind: 2, width: 44.3, height: 44.7, iconSize: 22, label: "Help")
                .offset(x: 287.7 * s, y: l.yt(77.3))
            disc(kind: 3, width: 44, height: 45.7, iconSize: 23, label: "Settings")
                .offset(x: 339 * s, y: l.yt(76))
        }
    }
    private func chip(x: Double, y: Double, w: Double, h: Double, pad: Double, gap: Double, count: String, kind: Int, label: String) -> some View {
        let s = layout.scale
        return RoundedRectangle(cornerRadius: h / 2 * s, style: .continuous).fill(R.color(0x181818))
            .overlay {
                Canvas(opaque: false, colorMode: .nonLinear) { raw, _ in
                    var c = raw; c.scaleBy(x: s, y: s)
                    FlipFocusBadgeIcons.draw(c, kind: kind, x: pad, y: (h - 24) / 2, size: 24)
                    resources.draw(c, count, "SemiBold", 17, pad + 24 + gap, h / 2 + 0.364 * 17, align: 0)
                }
            }
            .frame(width: w * s, height: h * s)
            .offset(x: x * s, y: layout.yt(y))
            .accessibilityElement(children: .ignore).accessibilityLabel(label)
    }
    private func disc(kind: Int, width: Double, height: Double, iconSize: Double, label: String) -> some View {
        let s = layout.scale
        return Button {} label: {
            RoundedRectangle(cornerRadius: height / 2 * s, style: .circular).fill(R.color(0x1B1B1B))
                .overlay {
                    Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                        FlipFocusBadgeIcons.draw(c, kind: kind, x: (width - iconSize) * s / 2, y: (height - iconSize) * s / 2, size: iconSize * s)
                    }
                }
                .frame(width: width * s, height: height * s)
                .contentShape(Rectangle().inset(by: -4))
        }
        .buttonStyle(FlipFocusBadgeDiscStyle())
        .accessibilityLabel(label)
    }
    private var sheet: some View {
        let s = layout.scale
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 40 * s, style: .continuous).fill(R.color(0x1F1F1F))
            RoundedRectangle(cornerRadius: 2.25 * s).fill(R.color(0x606060))
                .frame(width: 36 * s, height: 4.5 * s)
                .offset(x: (368 - 36) / 2 * s, y: 16.3 * s)
            Canvas(opaque: false, colorMode: .nonLinear) { raw, _ in
                var c = raw; c.scaleBy(x: s, y: s)
                let dates = ["Sunday, Mar 8", "Mar 2 – Mar 8", "March", "2026"]
                let totals = ["", "47h 05m", "182h 40m", "1,206h"]
                resources.draw(c, dates[frame.range], "Medium", 13.3, 184, 45.4, color: 0xACACAC, alpha: frame.rangeFade)
                if frame.range == 0 { drawTotal(c) }
                else { resources.draw(c, totals[frame.range], "Bold", 48, 184, 100.4, alpha: frame.rangeFade, tracking: -0.48) }
                resources.draw(c, "total flip time", "Medium", 12.4, 184, 132.4, color: 0xA4A4A4)
            }
            .allowsHitTesting(false)
            .accessibilityLabel(sheetLabel)
            segmented.offset(x: 22 * s, y: 152 * s)
        }
        .frame(width: 368 * s, height: 220 * s, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 40 * s, style: .continuous))
        .offset(x: 17 * s, y: layout.sheetTop)
    }
    private var sheetLabel: String {
        let date = ["Sunday, Mar 8", "Mar 2 – Mar 8", "March", "2026"][frame.range]
        let total = frame.range == 0 ? frame.rollTo : ["", "47h 05m", "182h 40m", "1,206h"][frame.range]
        return "\(date), \(total.replacingOccurrences(of: "h", with: " hours ").replacingOccurrences(of: "m", with: " minutes")), total flip time"
    }
    private func drawTotal(_ c: GraphicsContext) {
        let a = Array(frame.rollFrom), b = Array(frame.rollTo), count = max(a.count, b.count)
        var cells: [(String, String, Int)] = []
        var slot = 0
        for i in (0..<count).reversed() {
            let from = i < a.count ? String(a[i]) : " ", to = i < b.count ? String(b[i]) : " "
            let roll = from != to
            cells.insert((from, to, roll ? slot : -1), at: 0)
            if roll { slot += 1 }
        }
        // RN renders each cell as its own Text. Changed digits have tabular advances
        // and no tracking; unchanged characters retain TYPE.total's −0.48 tracking.
        let factor = layout.scale * displayScale
        let widths = cells.map { cell in
            ceil(resources.line(cell.1, "Bold", 48, tracking: cell.2 < 0 ? -0.48 : 0, tabular: true).width * factor) / factor
        }
        var x = (368 - widths.reduce(0, +)) / 2
        let base = 100.4, lh = 52.0
        // Same Inter ascent/content ratios as textTop in FlipFocusParts.
        let top = base - (lh - 1.21 * 48) / 2 - 0.969 * 48
        for (i, cell) in cells.enumerated() {
            let (from, to, slot) = cell
            if slot < 0 {
                resources.draw(c, to, "Bold", 48, x, base, align: 0, tracking: -0.48, tabular: true)
            } else {
                let p = M.rollAt(frame.roll, slot)
                var clipped = c
                clipped.clip(to: Path(CGRect(x: x, y: top, width: widths[i], height: lh)))
                resources.draw(clipped, from, "Bold", 48, x, base - p * lh, alpha: 1 - min(p * 1.3, 1), align: 0, tabular: true)
                resources.draw(clipped, to, "Bold", 48, x, base + (1 - p) * lh, alpha: min(p * 1.3, 1), align: 0, tabular: true)
            }
            x += widths[i]
        }
    }
    private var segmented: some View {
        let s = layout.scale, itemW = (325.0 - 10 - 18) / 4
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 25 * s, style: .continuous).fill(R.color(0x2D2D2D))
            ForEach(0..<4) { i in
                RoundedRectangle(cornerRadius: 19 * s, style: .continuous).fill(R.color(0x303030))
                    .frame(width: itemW * s, height: 38 * s).offset(x: (5 + Double(i) * (itemW + 6)) * s, y: 6 * s)
            }
            RoundedRectangle(cornerRadius: 19 * s, style: .continuous).fill(R.color(0x404040))
                .frame(width: itemW * s, height: 38 * s).offset(x: (5 + frame.pill * (itemW + 6)) * s, y: 6 * s)
            ForEach(0..<4) { i in
                Button { onSelect(i) } label: {
                    Canvas(opaque: false, colorMode: .nonLinear) { raw, _ in
                        var c = raw; c.scaleBy(x: s, y: s)
                        resources.draw(c, ["D", "W", "M", "Y"][i], "SemiBold", 15, itemW / 2, 25 + 0.364 * 15,
                                       color: frame.range == i ? 0xFFFFFF : 0x6D6D6D)
                    }
                    .frame(width: itemW * s, height: 50 * s).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(["Day", "Week", "Month", "Year"][i])
                .accessibilityAddTraits(frame.range == i ? .isSelected : [])
                .offset(x: (5 + Double(i) * (itemW + 6)) * s)
            }
        }
        .frame(width: 325 * s, height: 50 * s)
    }
}

struct FlipFocusBadgeDiscStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.opacity(configuration.isPressed ? 0.7 : 1) }
}
