import AVFoundation
import CoreText
import QuartzCore
import SwiftUI
import UIKit

enum PourJarGoalResources {
    static let register: Void = {
        for name in ["InstrumentSerif-Regular", "Inter-Regular", "Inter-Medium", "Inter-SemiBold", "JetBrainsMono-Medium"] {
            if let url = Bundle.main.url(forResource: "PourJarGoal-" + name, withExtension: "ttf") {
                _ = CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    struct Line { let path: Path; let width: Double }
    /// SkFont.getGlyphWidths sums fractional advances without kerning or tracking.
    final class Font {
        let size: Double
        private let font: CTFont
        private var lines: [String: Line] = [:]
        init(_ name: String, _ size: Double) {
            _ = register
            self.size = size
            if let url = Bundle.main.url(forResource: "PourJarGoal-" + name, withExtension: "ttf"),
               let provider = CGDataProvider(url: url as CFURL), let cg = CGFont(provider) {
                font = CTFontCreateWithGraphicsFont(cg, size, nil, nil)
            } else { font = CTFontCreateWithName(name as CFString, size, nil) }
        }
        func line(_ text: String) -> Line {
            if let found = lines[text] { return found }
            let chars = Array(text.utf16)
            var ids = Array(repeating: CGGlyph(0), count: chars.count)
            _ = CTFontGetGlyphsForCharacters(font, chars, &ids, ids.count)
            var advances = Array(repeating: CGSize.zero, count: ids.count)
            _ = CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &advances, ids.count)
            var path = Path(), x = 0.0
            for i in ids.indices {
                if let raw = CTFontCreatePathForGlyph(font, ids[i], nil) {
                    path.addPath(Path(raw), transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
                }
                x += advances[i].width
            }
            let result = Line(path: path, width: x)
            // Keep the live money labels bounded without retaining thousands of paths.
            if lines.count > 384 { lines.removeAll(keepingCapacity: true) }
            lines[text] = result
            return result
        }
    }
    struct Fonts {
        let number: Font, body: Font, label: Font, title: Font, button: Font, stat: Font, mono: Font, toast: Font
        init(_ l: PourJarGoalLayout) {
            let s = l.s, f = l.fontScale
            number = Font("InstrumentSerif-Regular", min(80 * s * min(f, 1.08), 76))
            body = Font("Inter-Regular", 15 * s * f)
            label = Font("Inter-Medium", 12 * s * f)
            title = Font("Inter-SemiBold", 17 * s * (l.largestType ? f : min(f, 1.4)))
            button = Font("Inter-SemiBold", 16 * s * (l.largestType ? f : min(f, 1.5)))
            stat = Font("Inter-SemiBold", 23 * s * f)
            mono = Font("JetBrainsMono-Medium", max(10 * s, 10) * min(f, 1.35))
            toast = Font("Inter-Medium", 14 * s * (l.largeType ? f : 1))
        }
    }
    static func color(_ hex: UInt32, _ opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: opacity)
    }
    static let ink = color(0x101D29), mute = color(0x647180), white = color(0xFDFEFE)
    static func draw(_ context: GraphicsContext, _ font: Font, _ text: String,
                     _ x: Double, _ baseline: Double, _ color: Color, _ alpha: Double = 1, _ blur: Double = 0) {
        guard alpha > 0 else { return }
        var c = context
        c.opacity *= alpha
        if blur > 0.001 { c.addFilter(.blur(radius: blur)) }
        c.translateBy(x: x, y: baseline)
        c.fill(font.line(text).path, with: .color(color))
    }
}

/// Touch-only voices, the source's playback-rate control, and ringer-respecting audio.
@MainActor
final class PourJarGoalFeedback {
    private var pour: AVAudioPlayer?, land: AVAudioPlayer?
    private var lastHaptic = -1000.0
    init() {
        for name in ["pour", "land"] {
            if let url = Bundle.main.url(forResource: "PourJarGoal-" + name, withExtension: "wav"),
               let player = try? AVAudioPlayer(contentsOf: url) {
                player.enableRate = true; _ = player.prepareToPlay()
                if name == "pour" { pour = player } else { land = player }
            }
        }
    }
    func cue(_ cue: String) {
        let now = CACurrentMediaTime()
        guard now - lastHaptic >= 0.085 else { return }
        lastHaptic = now
        switch cue {
        case "detent": UISelectionFeedbackGenerator().selectionChanged()
        case "success": UINotificationFeedbackGenerator().notificationOccurred(.success)
        default:
            let style: UIImpactFeedbackGenerator.FeedbackStyle = cue == "impact" ? .medium : cue == "limit" ? .rigid : .light
            UIImpactFeedbackGenerator(style: style).impactOccurred()
        }
    }
    func sound(_ cue: String, _ level: Double, _ gain: Double = 0.6) {
        if cue == "stop" || cue == "tail" {
            pour?.numberOfLoops = 0
            if cue == "stop" { pour?.pause() }
            return
        }
        if cue == "flow" {
            pour?.volume = Float(min(0.7, gain)); pour?.rate = Float(0.8 + 0.6 * PourJarGoalMotion.clamp(level))
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        let voice = cue == "pour" ? pour : land
        voice?.volume = Float(min(0.7, gain)); voice?.rate = Float(0.8 + 0.6 * PourJarGoalMotion.clamp(level))
        voice?.numberOfLoops = cue == "pour" ? -1 : 0
        voice?.currentTime = 0; _ = voice?.play()
    }
    func stop() { pour?.pause(); land?.pause() }
}
