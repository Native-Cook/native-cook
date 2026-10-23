import SwiftUI

/// Ten invented albums with covers drawn in code, and the record that slides out of a sleeve.
enum RecordStackDeckArt {
    struct Album {
        let title: String
        let artist: String
        /// The cover's main colour, also the light it casts on the screen.
        let glow: UInt32
    }

    static let albums = [
        Album(title: "Afterglow Arcade", artist: "Mina Sol", glow: 0xFF5A36),
        Album(title: "Blue Static", artist: "The Low Hours", glow: 0x2747FF),
        Album(title: "Lime Hours", artist: "Kite Club", glow: 0xB6F23A),
        Album(title: "Velvet Exit", artist: "Ora Vance", glow: 0xC23BFF),
        Album(title: "Salt & Cedar", artist: "Fen Harlow", glow: 0xE8B98A),
        Album(title: "Night Swim", artist: "Coral Hum", glow: 0x1FB5C9),
        Album(title: "Chroma Rings", artist: "Halcyon Vale", glow: 0x8A7CFF),
        Album(title: "Pink Noise", artist: "Dot Mercer", glow: 0xFF7EB6),
        Album(title: "Hour Twenty-Four", artist: "Sunday Static", glow: 0xF5C431),
        Album(title: "White Room Tapes", artist: "Lune Archive", glow: 0xDADAD6),
    ]

    /// Draws album i's cover in the square (0, 0, s, s).
    static func cover(_ ctx: GraphicsContext, _ i: Int, _ s: Double) {
        let full = Path(CGRect(x: 0, y: 0, width: s, height: s))
        func circle(_ x: Double, _ y: Double, _ r: Double) -> Path { Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)) }
        func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path { Path(CGRect(x: x, y: y, width: w, height: h)) }
        var c = ctx
        c.clip(to: full)
        func type(_ text: String, _ size: Double, _ color: UInt32, at p: CGPoint, anchor: UnitPoint = .topLeading, weight: Font.Weight = .black) {
            c.draw(Text(text).font(.system(size: size, weight: weight)).tracking(-size * 0.04).foregroundColor(Color(hex: color)), at: p, anchor: anchor)
        }
        switch i {
        case 0: // Afterglow Arcade: a striped sun setting into the bottom edge.
            c.fill(full, with: .color(Color(hex: 0xFF5A36)))
            c.drawLayer { l in
                l.clip(to: circle(s * 0.5, s * 0.86, s * 0.42))
                l.fill(full, with: .color(Color(hex: 0xFFD23F)))
                for k in 0..<6 {
                    let y = s * (0.6 + Double(k) * 0.07)
                    l.fill(rect(0, y, s, s * (0.012 + Double(k) * 0.006)), with: .color(Color(hex: 0xFF5A36)))
                }
            }
            type("AFTERGLOW", s * 0.1, 0x3A0D05, at: CGPoint(x: s * 0.07, y: s * 0.07))
        case 1: // Blue Static: a dot screen with one orange dot out of line.
            c.fill(full, with: .color(Color(hex: 0x1F3FFF)))
            for row in 0..<9 {
                for col in 0..<9 {
                    let r = s * (0.012 + 0.012 * Double((row + col) % 3))
                    c.fill(circle(s * (0.1 + Double(col) * 0.1), s * (0.1 + Double(row) * 0.1), r), with: .color(.white.opacity(0.85)))
                }
            }
            c.fill(circle(s * 0.72, s * 0.34, s * 0.07), with: .color(Color(hex: 0xFF8A2A)))
        case 2: // Lime Hours: lowercase type, very large.
            c.fill(full, with: .color(Color(hex: 0xB6F23A)))
            type("lime", s * 0.42, 0x111111, at: CGPoint(x: s * 0.5, y: s * 0.56), anchor: .center)
            type("hours", s * 0.07, 0x111111, at: CGPoint(x: s * 0.08, y: s * 0.86), weight: .bold)
        case 3: // Velvet Exit: a glowing pink orb on plum.
            c.fill(full, with: .color(Color(hex: 0x2A0F3D)))
            c.fill(full, with: .radialGradient(Gradient(colors: [Color(hex: 0xFF9AD5), Color(hex: 0xC23BFF), Color(hex: 0xC23BFF, opacity: 0)]),
                                                center: CGPoint(x: s * 0.42, y: s * 0.46), startRadius: 0, endRadius: s * 0.42))
            c.stroke(circle(s * 0.42, s * 0.46, s * 0.3), with: .color(.white.opacity(0.5)), lineWidth: max(1, s * 0.006))
            type("VELVET EXIT", s * 0.06, 0xF6D8FF, at: CGPoint(x: s * 0.07, y: s * 0.86), weight: .heavy)
        case 4: // Salt & Cedar: three diagonal bands on cream.
            c.fill(full, with: .color(Color(hex: 0xEFE6D2)))
            for (k, hex) in [UInt32(0x1E6B66), 0x7A4A2B, 0xC8553D].enumerated() {
                var band = Path()
                let o = s * (0.15 + Double(k) * 0.22)
                band.move(to: CGPoint(x: o, y: s))
                band.addLine(to: CGPoint(x: o + s * 0.14, y: s))
                band.addLine(to: CGPoint(x: o + s * 0.64, y: 0))
                band.addLine(to: CGPoint(x: o + s * 0.5, y: 0))
                band.closeSubpath()
                c.fill(band, with: .color(Color(hex: hex)))
            }
        case 5: // Night Swim: wavy lines under a moon.
            c.fill(full, with: .linearGradient(Gradient(colors: [Color(hex: 0x0B4A5E), Color(hex: 0x041C26)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: s)))
            c.fill(circle(s * 0.72, s * 0.24, s * 0.08), with: .color(Color(hex: 0xE6FBFF)))
            for k in 0..<7 {
                var wave = Path()
                let y0 = s * (0.48 + Double(k) * 0.07)
                wave.move(to: CGPoint(x: 0, y: y0))
                for x in stride(from: 0.0, through: s, by: s / 24) {
                    wave.addLine(to: CGPoint(x: x, y: y0 + sin(x / s * 4 * .pi + Double(k)) * s * 0.018))
                }
                c.stroke(wave, with: .color(Color(hex: 0x5FE1EE, opacity: 0.9 - Double(k) * 0.1)), lineWidth: max(1, s * 0.01))
            }
        case 6: // Chroma Rings: three offset rings, like a misregistered print.
            c.fill(full, with: .color(Color(hex: 0x0D0D10)))
            c.drawLayer { l in
                l.blendMode = .screen
                for (k, hex) in [UInt32(0xFF3B5C), 0x3BFF9E, 0x3B7CFF].enumerated() {
                    let dx = Double(k - 1) * s * 0.045
                    l.stroke(circle(s * 0.5 + dx, s * 0.5 - dx * 0.4, s * 0.26), with: .color(Color(hex: hex)), lineWidth: s * 0.05)
                }
            }
        case 7: // Pink Noise: a checkerboard floor under a pink sky.
            c.fill(full, with: .color(Color(hex: 0xFF7EB6)))
            let n = 8, cell = s / Double(n)
            for row in 0..<4 {
                for col in 0..<n where (row + col) % 2 == 0 {
                    c.fill(rect(Double(col) * cell, s * 0.5 + Double(row) * cell, cell, cell), with: .color(Color(hex: 0x16161A)))
                }
            }
            type("pink noise", s * 0.09, 0x16161A, at: CGPoint(x: s * 0.07, y: s * 0.08), weight: .heavy)
        case 8: // Hour Twenty-Four: big numerals.
            c.fill(full, with: .color(Color(hex: 0xF5C431)))
            type("24", s * 0.62, 0x14110A, at: CGPoint(x: s * 0.52, y: s * 0.5), anchor: .center)
            c.fill(rect(s * 0.07, s * 0.86, s * 0.3, s * 0.02), with: .color(Color(hex: 0x14110A)))
        default: // White Room Tapes: one thin circle and a red dot.
            c.fill(full, with: .color(Color(hex: 0xF4F4F2)))
            c.stroke(circle(s * 0.5, s * 0.48, s * 0.28), with: .color(Color(hex: 0x16161A)), lineWidth: max(1, s * 0.006))
            c.fill(circle(s * 0.7, s * 0.3, s * 0.035), with: .color(Color(hex: 0xE53935)))
        }
        // The sleeve's printed edge and a faint sheen.
        c.fill(full, with: .linearGradient(Gradient(colors: [.white.opacity(0.14), .white.opacity(0), .black.opacity(0.12)]),
                                           startPoint: .zero, endPoint: CGPoint(x: s, y: s)))
        c.stroke(full, with: .color(.black.opacity(0.25)), lineWidth: 1)
    }

    /// The record: black vinyl with grooves, a gloss fixed to the light, and the cover as its label.
    static func vinyl(_ ctx: GraphicsContext, album: Int, center c: CGPoint, radius r: Double, angle: Double) {
        func circle(_ rad: Double) -> Path { Path(ellipseIn: CGRect(x: c.x - rad, y: c.y - rad, width: 2 * rad, height: 2 * rad)) }
        ctx.fill(circle(r), with: .color(Color(hex: 0x0B0B0D)))
        let label = r * 0.36
        for k in 0..<30 {
            let rad = label + 6 + (r - label - 10) * Double(k) / 29
            ctx.stroke(circle(rad), with: .color(.white.opacity(k % 2 == 0 ? 0.04 : 0.07)), lineWidth: 0.7)
        }
        ctx.stroke(circle(r - 0.75), with: .color(.white.opacity(0.14)), lineWidth: 1.5)
        var ring = Path()
        ring.addPath(circle(r - 2))
        ring.addPath(circle(label + 2))
        ctx.fill(ring, with: .conicGradient(Gradient(stops: [
            .init(color: .white.opacity(0), location: 0),
            .init(color: .white.opacity(0.14), location: 0.08),
            .init(color: .white.opacity(0), location: 0.18),
            .init(color: .white.opacity(0), location: 0.5),
            .init(color: .white.opacity(0.1), location: 0.58),
            .init(color: .white.opacity(0), location: 0.68),
            .init(color: .white.opacity(0), location: 1),
        ]), center: c, angle: .degrees(-60)), style: FillStyle(eoFill: true))
        var turn = ctx
        turn.translateBy(x: c.x, y: c.y)
        turn.rotate(by: .degrees(angle))
        turn.clip(to: Path(ellipseIn: CGRect(x: -label, y: -label, width: 2 * label, height: 2 * label)))
        turn.translateBy(x: -label, y: -label)
        cover(turn, album, label * 2)
        ctx.fill(circle(3.5), with: .color(Color(hex: 0xD8D8DC)))
        ctx.fill(circle(1.4), with: .color(Color(hex: 0x222226)))
    }
}
