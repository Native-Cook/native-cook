import AVFoundation
import CoreText
import SwiftUI
import UIKit

struct GlassPourMenuItem: Identifiable {
    var key: String
    var label: String
    var icon: String? = nil
    var destructive = false
    var disabled = false
    var id: String { key }
    static let defaults: [Self] = [
        .init(key: "share", label: "Share", icon: "share"),
        .init(key: "duplicate", label: "Duplicate", icon: "duplicate"),
        .init(key: "rename", label: "Rename", icon: "rename"),
        .init(key: "delete", label: "Delete", icon: "delete", destructive: true)
    ]
}

enum GlassPourMenuResources {
    static let register: Void = {
        if let url = Bundle.main.url(forResource: "GlassPourMenu-Inter-Medium", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255,
              green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
    struct Label {
        var path: Path, width: Double
    }
    /// Skia drawText uses unshaped glyph advances. CoreText paths preserve the same
    /// fractional advances and baseline without SwiftUI text layout or automatic kerning.
    private static var labelCache: [String: Label] = [:]
    static func label(_ text: String, size: Double, maxWidth: Double) -> Label {
        _ = register
        let cacheKey = "\(size)|\(maxWidth)|\(text)"
        if let cached = labelCache[cacheKey] { return cached }
        guard let url = Bundle.main.url(forResource: "GlassPourMenu-Inter-Medium", withExtension: "ttf"),
              let data = CGDataProvider(url: url as CFURL), let cg = CGFont(data) else {
            return Label(path: Path(), width: 0)
        }
        let font = CTFontCreateWithGraphicsFont(cg, size, nil, nil)
        func glyphs(_ s: String) -> ([CGGlyph], [CGSize]) {
            let chars = Array(s.utf16)
            var ids = [CGGlyph](repeating: 0, count: chars.count)
            CTFontGetGlyphsForCharacters(font, chars, &ids, chars.count)
            var advances = [CGSize](repeating: .zero, count: ids.count)
            CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &advances, ids.count)
            return (ids, advances)
        }
        var value = text
        if glyphs(value).1.reduce(0, { $0 + $1.width }) > maxWidth {
            while !value.isEmpty && glyphs(value + "…").1.reduce(0, { $0 + $1.width }) > maxWidth { value.removeLast() }
            value += "…"
        }
        let (ids, advances) = glyphs(value)
        var p = Path(), x = 0.0
        for i in ids.indices {
            if let raw = CTFontCreatePathForGlyph(font, ids[i], nil) {
                p.addPath(Path(raw), transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
            }
            x += advances[i].width
        }
        let result = Label(path: p, width: x)
        labelCache[cacheKey] = result
        return result
    }
    static func icon(_ name: String) -> Path {
        var p = Path()
        switch name {
        case "share":
            p.move(to: CGPoint(x: 12.0, y: 3.5))
            p.addLine(to: CGPoint(x: 12.0, y: 14.5))
            p.move(to: CGPoint(x: 8.5, y: 7.0))
            p.addLine(to: CGPoint(x: 12.0, y: 3.5))
            p.addLine(to: CGPoint(x: 15.5, y: 7.0))
            p.move(to: CGPoint(x: 8.5, y: 10.5))
            p.addLine(to: CGPoint(x: 7.0, y: 10.5))
            p.addCurve(to: CGPoint(x: 5.0, y: 12.5), control1: CGPoint(x: 5.9, y: 10.5), control2: CGPoint(x: 5.0, y: 11.4))
            p.addLine(to: CGPoint(x: 5.0, y: 18.5))
            p.addCurve(to: CGPoint(x: 7.0, y: 20.5), control1: CGPoint(x: 5.0, y: 19.6), control2: CGPoint(x: 5.9, y: 20.5))
            p.addLine(to: CGPoint(x: 17.0, y: 20.5))
            p.addCurve(to: CGPoint(x: 19.0, y: 18.5), control1: CGPoint(x: 18.1, y: 20.5), control2: CGPoint(x: 19.0, y: 19.6))
            p.addLine(to: CGPoint(x: 19.0, y: 12.5))
            p.addCurve(to: CGPoint(x: 17.0, y: 10.5), control1: CGPoint(x: 19.0, y: 11.4), control2: CGPoint(x: 18.1, y: 10.5))
            p.addLine(to: CGPoint(x: 15.5, y: 10.5))
        case "duplicate":
            p.move(to: CGPoint(x: 15.5, y: 8.5))
            p.addLine(to: CGPoint(x: 15.5, y: 6.5))
            p.addCurve(to: CGPoint(x: 13.5, y: 4.5), control1: CGPoint(x: 15.5, y: 5.4), control2: CGPoint(x: 14.6, y: 4.5))
            p.addLine(to: CGPoint(x: 6.5, y: 4.5))
            p.addCurve(to: CGPoint(x: 4.5, y: 6.5), control1: CGPoint(x: 5.4, y: 4.5), control2: CGPoint(x: 4.5, y: 5.4))
            p.addLine(to: CGPoint(x: 4.5, y: 13.5))
            p.addCurve(to: CGPoint(x: 6.5, y: 15.5), control1: CGPoint(x: 4.5, y: 14.6), control2: CGPoint(x: 5.4, y: 15.5))
            p.addLine(to: CGPoint(x: 8.5, y: 15.5))
            p.move(to: CGPoint(x: 10.5, y: 8.5))
            p.addLine(to: CGPoint(x: 17.5, y: 8.5))
            p.addCurve(to: CGPoint(x: 19.5, y: 10.5), control1: CGPoint(x: 18.6, y: 8.5), control2: CGPoint(x: 19.5, y: 9.4))
            p.addLine(to: CGPoint(x: 19.5, y: 17.5))
            p.addCurve(to: CGPoint(x: 17.5, y: 19.5), control1: CGPoint(x: 19.5, y: 18.6), control2: CGPoint(x: 18.6, y: 19.5))
            p.addLine(to: CGPoint(x: 10.5, y: 19.5))
            p.addCurve(to: CGPoint(x: 8.5, y: 17.5), control1: CGPoint(x: 9.4, y: 19.5), control2: CGPoint(x: 8.5, y: 18.6))
            p.addLine(to: CGPoint(x: 8.5, y: 10.5))
            p.addCurve(to: CGPoint(x: 10.5, y: 8.5), control1: CGPoint(x: 8.5, y: 9.4), control2: CGPoint(x: 9.4, y: 8.5))
            p.closeSubpath()
        case "rename":
            p.move(to: CGPoint(x: 14.5, y: 6.5))
            p.addLine(to: CGPoint(x: 17.5, y: 9.5))
            p.move(to: CGPoint(x: 5.0, y: 19.0))
            p.addLine(to: CGPoint(x: 5.8, y: 15.2))
            p.addLine(to: CGPoint(x: 15.8, y: 5.2))
            p.addCurve(to: CGPoint(x: 18.8, y: 5.2), control1: CGPoint(x: 16.6, y: 4.4), control2: CGPoint(x: 17.9, y: 4.4))
            p.addCurve(to: CGPoint(x: 18.8, y: 8.2), control1: CGPoint(x: 19.6, y: 6.1), control2: CGPoint(x: 19.6, y: 7.4))
            p.addLine(to: CGPoint(x: 8.8, y: 18.2))
            p.addLine(to: CGPoint(x: 5.0, y: 19.0))
            p.closeSubpath()
        case "delete":
            p.move(to: CGPoint(x: 4.5, y: 7.0))
            p.addLine(to: CGPoint(x: 19.5, y: 7.0))
            p.move(to: CGPoint(x: 9.5, y: 7.0))
            p.addLine(to: CGPoint(x: 9.5, y: 5.5))
            p.addCurve(to: CGPoint(x: 10.5, y: 4.5), control1: CGPoint(x: 9.5, y: 4.9), control2: CGPoint(x: 9.9, y: 4.5))
            p.addLine(to: CGPoint(x: 13.5, y: 4.5))
            p.addCurve(to: CGPoint(x: 14.5, y: 5.5), control1: CGPoint(x: 14.1, y: 4.5), control2: CGPoint(x: 14.5, y: 4.9))
            p.addLine(to: CGPoint(x: 14.5, y: 7.0))
            p.move(to: CGPoint(x: 6.5, y: 7.0))
            p.addLine(to: CGPoint(x: 7.3, y: 18.6))
            p.addCurve(to: CGPoint(x: 9.3, y: 20.5), control1: CGPoint(x: 7.4, y: 19.7), control2: CGPoint(x: 8.3, y: 20.5))
            p.addLine(to: CGPoint(x: 14.7, y: 20.5))
            p.addCurve(to: CGPoint(x: 16.7, y: 18.6), control1: CGPoint(x: 15.7, y: 20.5), control2: CGPoint(x: 16.6, y: 19.7))
            p.addLine(to: CGPoint(x: 17.5, y: 7.0))
            p.move(to: CGPoint(x: 10.5, y: 10.5))
            p.addLine(to: CGPoint(x: 10.5, y: 16.5))
            p.move(to: CGPoint(x: 13.5, y: 10.5))
            p.addLine(to: CGPoint(x: 13.5, y: 16.5))
        default: break
        }
        return p
    }
}

/// Optional, bundled touch-only cues. The demo retains RN's sound=false default.
@MainActor
final class GlassPourMenuFeedback {
    var haptics = true, sound = false
    private var players: [String: AVAudioPlayer] = [:]
    func cue(_ kind: String, audio: String? = nil) {
        if haptics {
            switch kind {
            case "grab": UIImpactFeedbackGenerator(style: .soft).impactOccurred()
            case "pinch": UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            case "merge": UIImpactFeedbackGenerator(style: .light).impactOccurred()
            case "row": UISelectionFeedbackGenerator().selectionChanged()
            case "pick": UINotificationFeedbackGenerator().notificationOccurred(.success)
            case "pickDanger": UINotificationFeedbackGenerator().notificationOccurred(.warning)
            case "limit": UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
            default: break
            }
        }
        guard sound, let audio else { return }
        // Ambient observes the hardware silent switch. Never activate a playback session.
        do { try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default) }
        catch { return }
        if players[audio] == nil,
           let url = Bundle.main.url(forResource: "GlassPourMenu-" + audio, withExtension: "wav") {
            players[audio] = try? AVAudioPlayer(contentsOf: url)
            players[audio]?.prepareToPlay()
        }
        players[audio]?.currentTime = 0
        players[audio]?.play()
    }
}
