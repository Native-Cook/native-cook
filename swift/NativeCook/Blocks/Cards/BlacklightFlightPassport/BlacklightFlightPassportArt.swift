import CoreText
import SwiftUI
import UIKit

// The daylight and UV worlds share geometry, paths and font resources.
enum BlacklightFlightPassportArt {
    typealias M = BlacklightFlightPassportMotion
    typealias Theme = BlacklightFlightPassportTheme
    static let registered: Void = {
        for name in ["Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold", "JetBrainsMono-Regular"] {
            if let url = Bundle.main.url(forResource: "BlacklightFlightPassport-" + name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static let map: UIImage = {
        guard let url = Bundle.main.url(forResource: "BlacklightFlightPassport-map-dots", withExtension: "webp"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }()
    static let plane: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: -9))
        p.addCurve(to: CGPoint(x: 1.4, y: -6.4), control1: CGPoint(x: 0.9, y: -9), control2: CGPoint(x: 1.4, y: -7.8))
        for point in [CGPoint(x: 1.4, y: -2.2), CGPoint(x: 8.6, y: 2.2), CGPoint(x: 8.6, y: 4), CGPoint(x: 1.4, y: 1.9), CGPoint(x: 1.4, y: 6), CGPoint(x: 3.6, y: 7.8), CGPoint(x: 3.6, y: 9), CGPoint(x: 0, y: 8), CGPoint(x: -3.6, y: 9), CGPoint(x: -3.6, y: 7.8), CGPoint(x: -1.4, y: 6), CGPoint(x: -1.4, y: 1.9), CGPoint(x: -8.6, y: 4), CGPoint(x: -8.6, y: 2.2), CGPoint(x: -1.4, y: -2.2), CGPoint(x: -1.4, y: -6.4)] { p.addLine(to: point) }
        p.addCurve(to: CGPoint(x: 0, y: -9), control1: CGPoint(x: -1.4, y: -7.8), control2: CGPoint(x: -0.9, y: -9))
        p.closeSubpath()
        return p
    }()
    static func project(_ code: String) -> CGPoint {
        let p = Theme.airports[code]!
        return CGPoint(x: (p.x + 170) / 360 * 341, y: 34 + (78 - p.y) * 1.42)
    }
    static let arcs: [(Path, CGPoint, CGPoint, CGPoint)] = Theme.routes.map { from, to, bow in
        let a = project(from), b = project(to)
        let c = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 - hypot(b.x - a.x, b.y - a.y) * bow)
        var p = Path(); p.move(to: a); p.addQuadCurve(to: b, control: c)
        return (p, a, b, c)
    }
    static let guilloche: Path = {
        var p = Path()
        for i in 0..<22 {
            let y = 20 + Double(i) * 19
            p.move(to: CGPoint(x: 0, y: y))
            for x in stride(from: 0.0, through: 341.0, by: 4) {
                p.addLine(to: CGPoint(x: x, y: y + 5 * sin(x / 17 + Double(i) * 0.7) + 3 * sin(x / 7.3 - Double(i))))
            }
        }
        return p
    }()
    static let rosette: Path = {
        var p = Path()
        for k in 0...720 {
            let theta = Double(k) / 720 * .pi * 2 * 13
            let point = CGPoint(x: 21 * cos(theta) + 22 * cos(21.0 / 13 * theta), y: 21 * sin(theta) - 22 * sin(21.0 / 13 * theta))
            if k == 0 { p.move(to: point) } else { p.addLine(to: point) }
        }
        return p
    }()
    static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double = 0) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: radius)
    }
    static func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }
    static func line(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ x2: Double, _ y2: Double, _ color: Color, _ width: Double) {
        var p = Path(); p.move(to: CGPoint(x: x, y: y)); p.addLine(to: CGPoint(x: x2, y: y2))
        c.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
    static func text(_ c: inout GraphicsContext, _ value: String, _ x: Double, _ cy: Double, _ size: Double, _ face: String, _ color: Color, _ anchor: Double = 0) {
        _ = registered
        var t = c.resolve(Text(value).font(.custom(face, fixedSize: size)))
        t.shading = .color(color)
        let bounds = t.measure(in: CGSize(width: 10000, height: 1000))
        let baseline = t.firstBaseline(in: bounds)
        c.draw(t, at: CGPoint(x: x - bounds.width * anchor, y: cy + size * 0.36 - baseline), anchor: .topLeading)
    }
    static func plane(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ angle: Double, _ scale: Double, _ color: Color) {
        var layer = c
        layer.translateBy(x: x, y: y); layer.rotate(by: .degrees(angle)); layer.scaleBy(x: scale, y: scale)
        layer.fill(plane, with: .color(color))
    }
    static func flag(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ index: Int) {
        let (kind, colors) = Theme.flags[index]
        var layer = c
        layer.clip(to: circle(x, y, 5.4))
        if kind == "dot" {
            layer.fill(circle(x, y, 5.4), with: .color(Theme.color(colors[0])))
            layer.fill(circle(x, y, 5.4 * 0.45), with: .color(Theme.color(colors[1])))
        } else {
            let step = 10.8 / Double(colors.count)
            for (i, color) in colors.enumerated() {
                let p = kind == "h" ? rect(x - 5.4, y - 5.4 + step * Double(i), 10.8, step + 0.2) : rect(x - 5.4 + step * Double(i), y - 5.4, step + 0.2, 10.8)
                layer.fill(p, with: .color(Theme.color(color)))
            }
        }
        c.stroke(circle(x, y, 5.4), with: .color(.black.opacity(0.15)), lineWidth: 0.6)
    }
    static func page(_ context: GraphicsContext, _ f: M.Frame, _ uv: Bool, _ live: M.Driver) {
        var c = context
        let p = uv ? Theme.uv : Theme.light
        func color(_ key: String, _ alpha: Double = 1) -> Color { p[key]!.opacity(alpha) }
        for i in 0..<11 {
            let x = 14 + Double(i) * 31
            if i == 2 || i == 9 { text(&c, "SFO", x - 6, 26, 7, "Inter-SemiBold", color("ink", 0.28)) }
            else { plane(&c, x, 26, 90, 0.42, color("ink", 0.28)) }
        }
        var mapImage = c.resolve(Image(uiImage: map).renderingMode(.template))
        mapImage.shading = .color(color("map"))
        c.draw(mapImage, in: CGRect(x: 0, y: 34, width: 341, height: 176))
        if uv {
            c.stroke(guilloche, with: .color(color("glowLine", 0.42 * (0.4 + 0.6 * f.glow))), lineWidth: 0.7)
            var rose = c
            rose.translateBy(x: 341 - 64, y: 318); rose.rotate(by: .degrees(360 * live.amb / M.loop / 6))
            rose.stroke(rosette, with: .color(color("stamp", 0.55 * f.glow)), lineWidth: 0.7)
            var watermark = c
            watermark.translateBy(x: 341 * 0.74, y: 296); watermark.rotate(by: .degrees(-24)); watermark.scaleBy(x: 4.6, y: 4.6)
            watermark.addFilter(.blur(radius: 0.6))
            watermark.fill(plane, with: .color(color("glowLine", 0.34 * f.glow)))
        }
        for (i, arc) in arcs.enumerated() {
            let progress = f.routes[i]
            if progress <= 0.001 { continue }
            c.stroke(progress < 0.999 ? arc.0.trimmedPath(from: 0, to: progress) : arc.0, with: .color(color("arc", f.fade)), style: StrokeStyle(lineWidth: uv ? 1.5 : 1.25, lineCap: .round))
            if progress < 0.999 {
                let q = 1 - progress
                let x = q * q * arc.1.x + 2 * q * progress * arc.3.x + progress * progress * arc.2.x
                let y = q * q * arc.1.y + 2 * q * progress * arc.3.y + progress * progress * arc.2.y
                let dx = 2 * q * (arc.3.x - arc.1.x) + 2 * progress * (arc.2.x - arc.3.x)
                let dy = 2 * q * (arc.3.y - arc.1.y) + 2 * progress * (arc.2.y - arc.3.y)
                plane(&c, x, y, atan2(dy, dx) * 180 / .pi + 90, 0.62, uv ? .white : color("ink"))
            }
        }
        func pop(_ u: Double) -> Double { u <= 0 ? 0 : u >= 1 ? 1 : 1 + 0.6 * sin(.pi * u) * (1 - u) }
        // Dots follow all routes, so crossings never paint over airport markers.
        for (i, arc) in arcs.enumerated() {
            let progress = f.routes[i]
            if progress <= 0.001 { continue }
            c.fill(circle(arc.1.x, arc.1.y, 2.1 * pop(min(1, progress * 6))), with: .color(color("airport", f.fade)))
            if progress > 0.92 { c.fill(circle(arc.2.x, arc.2.y, 2.1 * pop((progress - 0.92) / 0.08)), with: .color(color("airport", f.fade))) }
        }
        for k in 0..<13 { flag(&c, 341.0 / 2 - 6 * 11.6 + Double(k) * 11.6, 214, k) }
        line(&c, 0, 228, 341, 228, color("ink", 0.15), 0.8)
        text(&c, "MY CONTRAIL PASSPORT", 13, 248, 14.5, "Inter-SemiBold", color("ink"))
        c.fill(rect(14, 260, 9, 6), with: .color(color("ink", 0.75)))
        text(&c, "PASSPORT • PASS • PASAPORTE", 26, 263, 6.8, "Inter-SemiBold", color("ink"))
        c.fill(rect(316, 237, 22, 22, 5), with: .color(.white.opacity(uv ? 0.5 : 1)))
        plane(&c, 327, 248, 45, 0.8, uv ? color("ink") : Theme.color(0xC9A8F2))
        let k = f.count
        text(&c, String(Int((27 * k).rounded())), 13, 299, 34, "Inter-SemiBold", color("text"))
        text(&c, "flights", 13, 333, 27, "Inter-Regular", color("inkSoft"))
        let rows = [("Authority", "Contrail"), ("Place of issue", "SFO"), ("Date of issue", "28 JUN 26"), ("Member since", "26 JUN 26")]
        for (i, row) in rows.enumerated() {
            let y = [291.0, 308, 325, 342][i]
            text(&c, row.0, 124, y, 10.5, "Inter-Medium", color("inkSoft"))
            let width = c.resolve(Text(row.0 + " ").font(.custom("Inter-Medium", fixedSize: 10.5))).measure(in: CGSize(width: 1000, height: 100)).width
            text(&c, row.1, 124 + width, y, 10.5, "Inter-Regular", color("grey"))
        }
        let digits = Array(String(Int((128714 * k).rounded())))
        let km = digits.enumerated().map { index, digit in
            let remaining = digits.count - 1 - index
            return String(digit) + (remaining > 0 && remaining % 3 == 0 ? "," : "")
        }.joined()
        let vals = ["\(km) km", "\(Int((7 * k).rounded()))d \(Int(k.rounded()))h", String(Int((21 * k).rounded())), String(Int((14 * k).rounded()))]
        for i in 0..<4 {
            let x = [13.0, 140, 225, 300][i]
            text(&c, ["Distance", "Flight Time", "Airports", "Airlines"][i], x, 367, 10.5, "Inter-Regular", color("grey"))
            text(&c, vals[i], x, 384, 18, "Inter-Medium", color("text"))
        }
        let mrz = ["ALLTIME<<SAM<<RIVERA<<MEMBER26JUN26<<@CONTRAIL<<<<", "ISSUED28JUN26SFO<<<<<<<<<<<<<<<<<<<<<<CONTRAIL.APP"]
        for i in 0..<2 { text(&c, mrz[i], 13, [414.0, 426][i], 10.3, "JetBrainsMono-Regular", color("ink", 0.85)) }
        if uv {
            var stamp = c; stamp.translateBy(x: 237, y: 402); stamp.rotate(by: .degrees(-8))
            stamp.stroke(rect(-70, -13, 140, 26, 6), with: .color(color("stamp", 0.75 * f.glow)), lineWidth: 1.6)
            text(&stamp, "VERIFIED TRAVELLER · 2026", 0, 0, 11, "Inter-Bold", color("stamp", 0.85 * f.glow), 0.5)
        }
    }
    static func world(_ context: GraphicsContext, _ f: M.Frame, _ uv: Bool, _ live: M.Driver, _ dy: Double, _ scale: Double) {
        var c = context
        let p = uv ? Theme.uv : Theme.light
        func color(_ key: String, _ alpha: Double = 1) -> Color { p[key]!.opacity(alpha) }
        let backdrop = rect(0, -dy / scale - 40, 402, 1000)
        if uv {
            c.fill(backdrop, with: .linearGradient(Gradient(stops: [.init(color: Theme.color(0x141052), location: 0), .init(color: color("bg"), location: 0.45), .init(color: Theme.color(0x05061A), location: 1)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 874)))
            c.fill(circle(201, -20, 260), with: .radialGradient(Gradient(colors: [color("bgGlow", 0.85), color("bgGlow", 0)]), center: CGPoint(x: 201, y: -20), startRadius: 0, endRadius: 260))
        } else { c.fill(backdrop, with: .color(color("bg"))) }
        for x in [40.0, 362] {
            if !uv {
                c.drawLayer { layer in
                    layer.addFilter(.blur(radius: 6))
                    layer.fill(circle(x, 91, 23), with: .color(.black.opacity(0.07)))
                }
            }
            c.fill(circle(x, 89, 23), with: .color(color("chrome")))
        }
        line(&c, 33, 82, 47, 96, color("chromeInk"), 2)
        line(&c, 47, 82, 33, 96, color("chromeInk"), 2)
        c.stroke(rect(352, 81, 20, 16, 3), with: .color(color("chromeInk")), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
        line(&c, 352, 86, 372, 86, color("chromeInk"), 1.6)
        line(&c, 364, 86, 364, 97, color("chromeInk"), 1.6)
        text(&c, "Your All-Time Passport", 201, 81, 17, "Inter-Medium", color("header"), 0.5)
        text(&c, "View and Share", 201, 99, 14, "Inter-Regular", color("headerSub"), 0.5)
        c.drawLayer { layer in
            layer.addFilter(.blur(radius: uv ? 22 : 16))
            if uv {
                layer.fill(rect(31, 188, 341, 452, 14), with: .color(Theme.color(0x7B6CFF, 0.35 * (0.5 + 0.5 * f.glow))))
            } else {
                layer.fill(rect(37, 202, 329, 452, 14), with: .color(Color(.sRGB, red: 0.05, green: 0.08, blue: 0.2, opacity: 0.16)))
            }
        }
        c.fill(rect(31, 188, 341, 452, 14), with: .color(color("cover")))
        let pageRect = rect(31, 198, 341, 430, 4)
        c.fill(pageRect, with: .linearGradient(Gradient(colors: [color("page"), color("page2")]), startPoint: CGPoint(x: 31, y: 188), endPoint: CGPoint(x: 372, y: 640)))
        var pageContext = c; pageContext.clip(to: pageRect); pageContext.translateBy(x: 31, y: 188)
        page(pageContext, f, uv, live)
        var button = c; button.translateBy(x: 200, y: 681.5)
        let pressScale = 1 - 0.04 * max(f.press, live.pressP)
        button.scaleBy(x: pressScale, y: pressScale)
        let buttonRect = rect(-51, -15.5, 102, 31, 15.5)
        if uv {
            button.drawLayer { glow in
                glow.addFilter(.blur(radius: 10))
                glow.fill(buttonRect, with: .color(Theme.color(0xB38CFF, 0.55 + 0.25 * sin(2 * .pi * 2 * live.amb / M.loop))))
            }
        }
        button.fill(buttonRect, with: .color(color("button")))
        button.stroke(buttonRect, with: .color(uv ? Theme.color(0xD9C2FF) : .black.opacity(0.1)), lineWidth: uv ? 1.6 : 1)
        button.fill(rect(-35, -6.5, 5, 13, 1.5), with: .color(color("buttonInk")))
        button.fill(rect(-36.4, -8, 7.8, 4, 1), with: .color(color("buttonInk")))
        text(&button, "Blacklight", -21, 0, 15, "Inter-Medium", color("buttonInk"))
        for i in 0..<3 {
            let x = [86.0, 201, 315][i]
            let ink = uv ? Color.white : Theme.color(0x111111)
            let inner = uv ? Theme.color(0x141052) : .white
            c.fill(rect(x - 13, 783, 26, 26, 7), with: .color(ink))
            if i == 0 {
                c.fill(Path(ellipseIn: CGRect(x: x - 8, y: 789.5, width: 16, height: 12)), with: .color(inner))
                c.fill(circle(x - 5, 802, 2), with: .color(inner))
            } else if i == 1 {
                for k in 0..<8 {
                    let a = Double(k) / 8 * .pi * 2
                    c.stroke(circle(x + cos(a) * 3.6, 796 + sin(a) * 3.6, 3.6), with: .color(inner), lineWidth: 1.1)
                }
            } else {
                for k in -1...1 { c.fill(circle(x + Double(k) * 5.5, 796, 1.9), with: .color(inner)) }
            }
            text(&c, ["Messages", "Photos", "More"][i], x, 820, 15, "Inter-Regular", ink, 0.5)
        }
    }
    static func scene(_ context: GraphicsContext, _ size: CGSize, _ f: M.Frame, _ live: M.Driver) {
        let scale = size.width / 402
        let dy = (size.height - 874 * scale) / 2
        var c = context; c.translateBy(x: 0, y: dy); c.scaleBy(x: scale, y: scale)
        world(c, f, false, live, dy, scale)
        let sheenA = 1 - M.smooth(f.uv * 3)
        if sheenA > 0.01 {
            let card = CGRect(x: 31, y: 188, width: 341, height: 452)
            let u = 0.5 + 0.42 * live.tiltX + 0.18 * sin(2 * .pi * live.amb / M.loop) + 0.1 * live.tiltY
            let cx = card.minX + card.width * u
            var sheen = c
            sheen.clip(to: Path(roundedRect: card, cornerRadius: 14))
            sheen.blendMode = .plusLighter
            sheen.fill(Path(card), with: .linearGradient(Gradient(stops: [
                .init(color: Theme.color(0xFFFFFF, 0), location: 0),
                .init(color: Theme.color(0xC9F2FF, 0.1 * sheenA), location: 0.35),
                .init(color: Theme.color(0xFFE7FB, 0.16 * sheenA), location: 0.5),
                .init(color: Theme.color(0xE9FFD9, 0.1 * sheenA), location: 0.65),
                .init(color: Theme.color(0xFFFFFF, 0), location: 1)
            ]), startPoint: CGPoint(x: cx - 90, y: card.minY), endPoint: CGPoint(x: cx + 90, y: card.minY + card.height * 0.5)))
        }
        let reach = f.uv, torch = live.torch
        if reach > 0.001 || torch > 0.01 {
            c.drawLayer { uv in
                uv.clipToLayer { mask in
                    if reach > 0.001 {
                        let radius = 20 + reach * 1080
                        let gradient = Gradient(stops: [.init(color: .black, location: 0), .init(color: .black, location: max(0, (radius - 70) / radius)), .init(color: .clear, location: 1)])
                        mask.fill(circle(200, 681.5, radius), with: .radialGradient(gradient, center: CGPoint(x: 200, y: 681.5), startRadius: 0, endRadius: radius))
                    }
                    if torch > 0.01 {
                        let radius = 84 * M.smooth(torch)
                        let gradient = Gradient(stops: [.init(color: .black, location: 0), .init(color: .black.opacity(0.9), location: 0.55), .init(color: .clear, location: 1)])
                        mask.fill(circle(live.tx, live.ty, radius), with: .radialGradient(gradient, center: CGPoint(x: live.tx, y: live.ty), startRadius: 0, endRadius: radius))
                    }
                }
                var inkFrame = f; inkFrame.glow = max(f.glow, torch)
                world(uv, inkFrame, true, live, dy, scale)
            }
            if reach > 0.001 && reach < 0.999 && 20 + reach * 1080 - 40 > 0 {
                c.drawLayer { edge in
                    edge.addFilter(.blur(radius: 18))
                    edge.stroke(circle(200, 681.5, 20 + reach * 1080 - 40), with: .color(Theme.color(0x8E6BFF, 0.35 * sin(.pi * reach))), lineWidth: 26)
                }
            }
        }
    }
}
