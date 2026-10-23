import CoreText
import SwiftUI

/// The tapes, the cassette, the player and the home video on its screen, drawn in code.
enum TapeShelfPlayerArt {
    typealias M = TapeShelfPlayerMotion

    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "TapeShelfPlayer-GochiHand-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func hand(_ size: Double) -> Font {
        _ = registered
        return .custom("GochiHand-Regular", fixedSize: size)
    }

    /// Handwriting at `size`, made smaller when the line would be wider than `maxWidth`.
    static func fittedHand(_ ctx: GraphicsContext, _ s: String, size: Double, maxWidth: Double, color: Color) -> GraphicsContext.ResolvedText {
        let full = ctx.resolve(Text(s).font(hand(size)).foregroundColor(color))
        let width = full.measure(in: CGSize(width: CGFloat.infinity, height: .infinity)).width
        guard width > maxWidth, width > 0 else { return full }
        return ctx.resolve(Text(s).font(hand(size * maxWidth / width)).foregroundColor(color))
    }

    static func brandFont(_ size: Double) -> Font { .custom("HelveticaNeue-CondensedBlack", fixedSize: size) }

    static let page = Color(hex: 0xF4F2EE)
    static let ink = Color(hex: 0x141414)
    static let magenta = Color(hex: 0xC4125A)

    struct Tape {
        let label: String
        let brand: String
        let color: UInt32
        let brandInk: UInt32
        /// The home video: sky top and bottom, sun, ground.
        let scene: [UInt32]
    }

    static let tapes = [
        Tape(label: "Summer '98", brand: "KORAL", color: 0xE5352F, brandInk: 0xFFFFFF, scene: [0xFF8A5B, 0xFFD08A, 0xFFF3C4, 0x2C6E8F]),
        Tape(label: "Lake house", brand: "STRATA", color: 0xF2EBD3, brandInk: 0x1C1C1C, scene: [0x7FB7D9, 0xD9EEF7, 0xFFFFFF, 0x2F5D50]),
        Tape(label: "First snow", brand: "NOVAVIEW", color: 0x1F2F6E, brandInk: 0xFFFFFF, scene: [0xB8C4D6, 0xE9EEF5, 0xFFF7E0, 0xC9D2DF]),
        Tape(label: "Road trip", brand: "SOLACE", color: 0x9B87C8, brandInk: 0xFFFFFF, scene: [0xF2A65A, 0xF7D9A8, 0xFFF1C9, 0x7A5A3A]),
        Tape(label: "Sunset pier", brand: "DRIFT", color: 0x1A1A1C, brandInk: 0xFFFFFF, scene: [0x5B3A8C, 0xF28C8C, 0xFFD58A, 0x2A2350]),
        Tape(label: "Nana's 70th", brand: "STRATA HQ", color: 0xE6EFC4, brandInk: 0x1E5E3A, scene: [0xE9C46A, 0xF4E3B5, 0xFFFFFF, 0xA0522D]),
        Tape(label: "Beach day", brand: "KORAL", color: 0xF08A24, brandInk: 0xFFFFFF, scene: [0x4FC3F7, 0xB3E5FC, 0xFFF59D, 0x0277BD]),
        Tape(label: "New puppy", brand: "NOVAVIEW", color: 0x2F7F86, brandInk: 0xFFFFFF, scene: [0xA5D6A7, 0xE8F5E9, 0xFFFFFF, 0x558B2F]),
        Tape(label: "Night drive", brand: "SOLACE", color: 0xC9A27A, brandInk: 0x2A1A0E, scene: [0x0B1026, 0x2B2F77, 0xF5F3CE, 0x05060F]),
    ]
    static let rainbow: [UInt32] = [0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x1E88E5]

    /// The screen's geometry, from its size and safe area.
    struct Geo {
        let w: Double, h: Double, top: Double, bottom: Double
        var shelfTop: Double { top + 44 }
        var shelfBottom: Double { shelfTop + 226 }
        var body: CGRect { CGRect(x: (w - 0.52 * w) / 2, y: top + 58, width: 0.52 * w, height: 188) }
        var screen: CGRect { CGRect(x: body.minX + 14, y: body.minY + 14, width: body.width - 28, height: 112) }
        var slot: CGRect {
            let sw = 0.62 * body.width
            return CGRect(x: w / 2 - sw / 2, y: screen.maxY + 20, width: sw, height: 9)
        }
        var gridTop: Double { shelfBottom + 58 }
        var cellW: Double { (w - 48 - 28) / 3 }
        var cellH: Double { cellW * 1.3 }
        func cell(_ i: Int) -> CGRect {
            CGRect(x: 24 + Double(i % 3) * (cellW + 14), y: gridTop + Double(i / 3) * (cellH + 14), width: cellW, height: cellH)
        }
        /// The cassette: its length, and its thickness seen from above (a VHS is 1.83 : 1).
        var cassetteL: Double { cellH * 0.88 }
        var cassetteT: Double { cassetteL / 1.83 }
        var pillY: Double { h - max(bottom, 16) - 12 - 44 }
        var transportY: Double { h - max(bottom, 16) - 8 - 60 }
    }

    // MARK: Tapes on the shelf

    static func sleeve(_ ctx: GraphicsContext, _ i: Int, _ r: CGRect, lift: Double) {
        let t = tapes[i]
        var c = ctx
        if lift > 0 {
            c.translateBy(x: r.midX, y: r.midY)
            c.scaleBy(x: 1 + 0.05 * lift, y: 1 + 0.05 * lift)
            c.translateBy(x: -r.midX, y: -r.midY)
        }
        let box = Path(roundedRect: r, cornerRadius: 4)
        c.drawLayer { l in
            l.addFilter(.blur(radius: 3 + 8 * lift))
            l.fill(Path(roundedRect: r.offsetBy(dx: 0, dy: 2 + 8 * lift), cornerRadius: 4), with: .color(.black.opacity(0.16 + 0.12 * lift)))
        }
        c.fill(box, with: .color(Color(hex: t.color)))
        var a = c
        a.clip(to: box)
        let w = r.width, h = r.height
        // Spine.
        a.fill(Path(CGRect(x: r.minX, y: r.minY, width: w * 0.11, height: h)), with: .color(.black.opacity(0.18)))
        // Brand and length.
        let brandInk = Color(hex: t.brandInk)
        a.draw(Text(t.brand).font(brandFont(w * 0.16)).foregroundColor(brandInk), at: CGPoint(x: r.minX + w * 0.17, y: r.minY + h * 0.05), anchor: .topLeading)
        a.draw(Text("T-120").font(.system(size: w * 0.075, weight: .bold)).foregroundColor(brandInk.opacity(0.75)),
               at: CGPoint(x: r.minX + w * 0.17, y: r.minY + h * 0.05 + w * 0.17), anchor: .topLeading)
        // Rainbow band.
        for (k, hex) in rainbow.enumerated() {
            a.fill(Path(CGRect(x: r.minX + w * 0.11, y: r.minY + h * (0.66 + Double(k) * 0.026), width: w * 0.89, height: h * 0.026)),
                   with: .color(Color(hex: hex)))
        }
        // The sticker with the handwritten title.
        var s = a
        s.translateBy(x: r.midX + w * 0.04, y: r.minY + h * 0.44)
        s.rotate(by: .degrees(-4))
        let sticker = CGRect(x: -w * 0.37, y: -h * 0.12, width: w * 0.74, height: h * 0.24)
        s.fill(Path(roundedRect: sticker.offsetBy(dx: 0.5, dy: 1), cornerRadius: 3), with: .color(.black.opacity(0.12)))
        s.fill(Path(roundedRect: sticker, cornerRadius: 3), with: .color(Color(hex: 0xFBFAF6)))
        s.fill(Path(CGRect(x: sticker.minX + 5, y: sticker.maxY - h * 0.055, width: sticker.width - 10, height: 0.6)), with: .color(Color(hex: 0x8FB5D9)))
        s.draw(fittedHand(s, t.label, size: w * 0.15, maxWidth: sticker.width - 8, color: Color(hex: 0x1D2A6B)), at: CGPoint(x: 0, y: -h * 0.005), anchor: .center)
        // A small format badge.
        let badge = CGRect(x: r.maxX - w * 0.27, y: r.maxY - h * 0.11, width: w * 0.2, height: h * 0.07)
        a.stroke(Path(roundedRect: badge, cornerRadius: 2), with: .color(brandInk.opacity(0.7)), lineWidth: 0.8)
        a.draw(Text("VHS").font(.system(size: w * 0.06, weight: .heavy)).foregroundColor(brandInk.opacity(0.8)), at: CGPoint(x: badge.midX, y: badge.midY), anchor: .center)
        // A faint sheen.
        a.fill(box, with: .linearGradient(Gradient(colors: [.white.opacity(0.14), .white.opacity(0)]), startPoint: CGPoint(x: r.minX, y: r.minY), endPoint: CGPoint(x: r.minX + w * 0.6, y: r.minY + h * 0.5)))
    }

    // MARK: The cassette

    /// A VHS cassette seen from above, centred at the origin of `ctx`, horizontal, length l and thickness t.
    static func cassette(_ ctx: GraphicsContext, l: Double, t: Double, label: String, reels: Double) {
        let body = CGRect(x: -l / 2, y: -t / 2, width: l, height: t)
        ctx.fill(Path(roundedRect: body, cornerRadius: l * 0.05), with: .linearGradient(Gradient(colors: [Color(hex: 0x2A2A2E), Color(hex: 0x141416)]),
                                                                                         startPoint: CGPoint(x: 0, y: -t / 2), endPoint: CGPoint(x: 0, y: t / 2)))
        ctx.stroke(Path(roundedRect: body.insetBy(dx: 0.5, dy: 0.5), cornerRadius: l * 0.05), with: .color(.white.opacity(0.12)), lineWidth: 1)
        // Label.
        let lab = CGRect(x: -l * 0.36, y: -t * 0.4, width: l * 0.72, height: t * 0.32)
        ctx.fill(Path(roundedRect: lab, cornerRadius: 2), with: .color(Color(hex: 0xF7F5EE)))
        ctx.draw(fittedHand(ctx, label, size: t * 0.2, maxWidth: lab.width - 6, color: Color(hex: 0x1D2A6B)), at: CGPoint(x: lab.midX, y: lab.midY), anchor: .center)
        // Window with the two reels.
        let win = CGRect(x: -l * 0.27, y: t * 0.0, width: l * 0.54, height: t * 0.34)
        ctx.fill(Path(roundedRect: win, cornerRadius: 3), with: .color(Color(hex: 0x0A0A0B)))
        for (k, side) in [-1.0, 1.0].enumerated() {
            let c = CGPoint(x: side * l * 0.15, y: win.midY)
            let tapeR = t * (k == 0 ? 0.2 : 0.13)
            var reel = ctx
            reel.clip(to: Path(roundedRect: win, cornerRadius: 3))
            reel.fill(Path(ellipseIn: CGRect(x: c.x - tapeR, y: c.y - tapeR, width: 2 * tapeR, height: 2 * tapeR)), with: .color(Color(hex: 0x4A2E1A)))
            let hub = t * 0.07
            reel.fill(Path(ellipseIn: CGRect(x: c.x - hub, y: c.y - hub, width: 2 * hub, height: 2 * hub)), with: .color(Color(hex: 0xEDEDED)))
            for s in 0..<6 {
                let a = Double(s) * .pi / 3 + reels
                var tooth = Path()
                tooth.move(to: c)
                tooth.addLine(to: CGPoint(x: c.x + cos(a) * hub * 0.9, y: c.y + sin(a) * hub * 0.9))
                reel.stroke(tooth, with: .color(Color(hex: 0x555555)), lineWidth: 1)
            }
        }
        for x in [-0.44, 0.44] {
            ctx.fill(Path(ellipseIn: CGRect(x: l * x - 1.5, y: t * 0.3 - 1.5, width: 3, height: 3)), with: .color(.white.opacity(0.25)))
        }
    }

    // MARK: The player

    static func player(_ ctx: GraphicsContext, _ g: Geo, pose p: M.Pose, clock: Double, still: Bool) {
        var c = ctx
        c.translateBy(x: 0, y: p.bounce)
        let body = g.body
        // Shadow on the shelf, then the body.
        c.drawLayer { l in
            l.addFilter(.blur(radius: 14))
            l.fill(Path(roundedRect: body.offsetBy(dx: 0, dy: 10).insetBy(dx: 8, dy: 4), cornerRadius: 26), with: .color(.black.opacity(0.35)))
        }
        c.fill(Path(roundedRect: body, cornerRadius: 26), with: .linearGradient(Gradient(colors: [Color(hex: 0x2B2B2E), Color(hex: 0x19191B)]),
                                                                                startPoint: CGPoint(x: 0, y: body.minY), endPoint: CGPoint(x: 0, y: body.maxY)))
        c.stroke(Path(roundedRect: body.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 26), with: .linearGradient(
            Gradient(colors: [.white.opacity(0.22), .white.opacity(0.04)]), startPoint: CGPoint(x: 0, y: body.minY), endPoint: CGPoint(x: 0, y: body.maxY)), lineWidth: 1)
        screen(c, g.screen, pose: p, clock: clock, still: still)
        // Slot, LED, buttons.
        let slot = g.slot
        c.fill(Path(roundedRect: slot.insetBy(dx: -4, dy: -4), cornerRadius: 8), with: .color(Color(hex: 0x111113)))
        c.fill(Path(roundedRect: slot, cornerRadius: 4), with: .color(.black))
        c.fill(Path(CGRect(x: slot.minX + 4, y: slot.maxY - 1.5, width: slot.width - 8, height: 1)), with: .color(.white.opacity(0.15)))
        let led = CGPoint(x: body.maxX - 22, y: body.maxY - 20)
        let ledColor = p.led > 0.5 ? Color(hex: 0x3BE37A) : Color(hex: 0xFF3B5C)
        c.drawLayer { l in
            l.addFilter(.blur(radius: 4))
            l.fill(Path(ellipseIn: CGRect(x: led.x - 6, y: led.y - 6, width: 12, height: 12)), with: .color(ledColor.opacity(0.7)))
        }
        c.fill(Path(ellipseIn: CGRect(x: led.x - 3.5, y: led.y - 3.5, width: 7, height: 7)), with: .color(ledColor))
        for k in 0..<3 {
            let b = CGPoint(x: body.minX + 22 + Double(k) * 16, y: body.maxY - 20)
            c.fill(Path(ellipseIn: CGRect(x: b.x - 5, y: b.y - 5, width: 10, height: 10)), with: .color(Color(hex: 0x0F0F11)))
            c.stroke(Path(ellipseIn: CGRect(x: b.x - 5, y: b.y - 5, width: 10, height: 10)), with: .color(.white.opacity(0.12)), lineWidth: 0.8)
        }
    }

    /// The CRT: idle text, the power line, the home video, scanlines and glass.
    static func screen(_ ctx: GraphicsContext, _ r: CGRect, pose p: M.Pose, clock: Double, still: Bool) {
        let glass = Path(roundedRect: r, cornerRadius: 16)
        var c = ctx
        c.clip(to: glass)
        c.fill(glass, with: .color(Color(hex: 0x0E0F11)))
        let mono = { (s: String, size: Double, color: Color) in
            Text(s).font(.system(size: size, weight: .semibold, design: .monospaced)).foregroundColor(color)
        }
        if p.screen <= 0.001 {
            // Idle: a blinking prompt, once a second.
            let on = still || clock.truncatingRemainder(dividingBy: 1) < 0.6
            if on { c.draw(mono("INSERT TAPE", 9, .white.opacity(0.45)), at: CGPoint(x: r.midX, y: r.midY), anchor: .center) }
        } else {
            let u = p.screen
            var band = r.height
            if still {
                band = r.height
            } else if p.powering > 0 {
                band = r.height * easeOut((u - 0.15) / 0.85)
            } else {
                band = r.height * M.smoothstep(0.35, 1, u)
            }
            if band > 0.5 {
                var v = c
                v.opacity = still ? u : 1
                v.clip(to: Path(CGRect(x: r.minX, y: r.midY - band / 2, width: r.width, height: band)))
                video(v, r, tape: p.tape, time: p.video)
            }
            // The power line: a bright horizontal streak growing (on) or shrinking to a dot (off).
            if !still {
                if p.powering > 0 && u < 0.4 {
                    let lw = r.width * min(1, u / 0.15)
                    c.fill(Path(CGRect(x: r.midX - lw / 2, y: r.midY - 1, width: lw, height: 2)), with: .color(.white.opacity(1 - u / 0.4)))
                } else if p.powering < 0 && u < 0.35 {
                    let lw = max(3, r.width * u / 0.35)
                    c.fill(Path(roundedRect: CGRect(x: r.midX - lw / 2, y: r.midY - 1.5, width: lw, height: 3), cornerRadius: 1.5), with: .color(.white.opacity(min(1, u / 0.05))))
                }
            }
        }
        // Scanlines, vignette, glass highlight.
        var y = r.minY
        while y < r.maxY {
            c.fill(Path(CGRect(x: r.minX, y: y, width: r.width, height: 1)), with: .color(.black.opacity(0.18)))
            y += 3
        }
        c.fill(glass, with: .radialGradient(Gradient(colors: [.clear, .black.opacity(0.45)]), center: CGPoint(x: r.midX, y: r.midY), startRadius: r.width * 0.3, endRadius: r.width * 0.72))
        c.fill(glass, with: .linearGradient(Gradient(colors: [.white.opacity(0.14), .white.opacity(0)]), startPoint: CGPoint(x: r.minX, y: r.minY), endPoint: CGPoint(x: r.minX + r.width * 0.45, y: r.midY)))
        ctx.stroke(glass, with: .color(.black.opacity(0.8)), lineWidth: 2)
    }

    static func easeOut(_ x: Double) -> Double { M.easeOut(x) }

    /// The home video: a sky, a sun, the water with moving glints, the camcorder's OSD and a rolling tracking band.
    static func video(_ ctx: GraphicsContext, _ r: CGRect, tape: Int, time: Double) {
        let sc = tapes[tape].scene
        let horizon = r.minY + r.height * 0.62
        ctx.fill(Path(r), with: .linearGradient(Gradient(colors: [Color(hex: sc[0]), Color(hex: sc[1])]),
                                                startPoint: CGPoint(x: 0, y: r.minY), endPoint: CGPoint(x: 0, y: horizon)))
        // The sun, cut by bands, slowly sinking.
        let sunR = r.height * 0.2
        let sun = CGPoint(x: r.minX + r.width * 0.66, y: horizon - sunR * 0.55 + 3 * sin(time * 0.6))
        var s = ctx
        s.clip(to: Path(CGRect(x: r.minX, y: r.minY, width: r.width, height: horizon - r.minY)))
        s.fill(Path(ellipseIn: CGRect(x: sun.x - sunR, y: sun.y - sunR, width: 2 * sunR, height: 2 * sunR)), with: .color(Color(hex: sc[2])))
        for k in 0..<3 {
            s.fill(Path(CGRect(x: sun.x - sunR, y: sun.y + sunR * (0.15 + Double(k) * 0.28), width: 2 * sunR, height: 1.2 + Double(k))), with: .color(Color(hex: sc[1])))
        }
        // The water, with glints drifting.
        ctx.fill(Path(CGRect(x: r.minX, y: horizon, width: r.width, height: r.maxY - horizon)), with: .color(Color(hex: sc[3])))
        for k in 0..<9 {
            let row = Double(k % 3)
            let y = horizon + 3 + row * (r.maxY - horizon - 6) / 3 + 2
            let span = r.width * 0.9
            let x = r.minX + ((Double(k) * 37 + time * (14 + row * 6)).truncatingRemainder(dividingBy: span)) + r.width * 0.05
            let len = 6 + 10 * M.clamp01(1 - abs(x - sun.x) / (r.width * 0.5))
            ctx.fill(Path(CGRect(x: x - len / 2, y: y, width: len, height: 1.2)), with: .color(Color(hex: sc[2]).opacity(0.75)))
        }
        // A dark pier reaching into the water.
        ctx.fill(Path(CGRect(x: r.minX, y: horizon - 3, width: r.width * 0.34, height: 3)), with: .color(.black.opacity(0.55)))
        for k in 0..<4 {
            ctx.fill(Path(CGRect(x: r.minX + Double(k) * r.width * 0.1 + 4, y: horizon, width: 2, height: 7 + Double(k))), with: .color(.black.opacity(0.5)))
        }
        // The rolling tracking band.
        let bandY = r.minY + (time * 0.35).truncatingRemainder(dividingBy: 1.3) * r.height - r.height * 0.15
        for k in 0..<7 {
            let lx = r.minX + M.hash(k, Int(time * 12)) * r.width
            let ly = bandY + Double(k) * 2.2
            ctx.fill(Path(CGRect(x: lx, y: ly, width: 10 + 30 * M.hash(k, Int(time * 12) + 7), height: 1)), with: .color(.white.opacity(0.35)))
        }
        // OSD.
        let mono = { (s: String, color: Color) in Text(s).font(.system(size: 8, weight: .bold, design: .monospaced)).foregroundColor(color) }
        ctx.draw(mono(time < 2.5 && time.truncatingRemainder(dividingBy: 0.8) < 0.5 ? "PLAY ▶" : "PLAY", Color(hex: 0x9CFFB0)), at: CGPoint(x: r.minX + 10, y: r.minY + 8), anchor: .topLeading)
        ctx.draw(mono("SP", .white.opacity(0.85)), at: CGPoint(x: r.maxX - 10, y: r.minY + 8), anchor: .topTrailing)
        ctx.draw(mono("JUL 14 1998", Color(hex: 0xFFB347)), at: CGPoint(x: r.maxX - 10, y: r.maxY - 8), anchor: .bottomTrailing)
    }
}

extension TapeShelfPlayerMotion {
    /// A small integer hash (PCG), the same in every stack: 0 to 1.
    static func hash(_ x: Int, _ salt: Int) -> Double {
        var h = UInt32(truncatingIfNeeded: x) &* 747_796_405 &+ UInt32(truncatingIfNeeded: salt) &* 2_891_336_453 &+ 1
        h = ((h >> ((h >> 28) &+ 4)) ^ h) &* 277_803_737
        h = (h >> 22) ^ h
        return Double(h) / 4_294_967_295
    }
}
