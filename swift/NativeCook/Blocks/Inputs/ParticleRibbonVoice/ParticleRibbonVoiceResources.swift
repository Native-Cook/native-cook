import AVFoundation
import CoreText
import SwiftUI
import UIKit

@MainActor
enum ParticleRibbonVoiceResources {
    static let register: Void = {
        for weight in ["ExtraLight", "Light"] {
            if let url = Bundle.main.url(forResource: "ParticleRibbonVoice-InterTight-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static let page = color(0x030304), ink = color(0xFDFDFE), greeting = color(0x95959A)
    static let ramp: [[Double]] = [[231.0/255,185.0/255,74.0/255],[110.0/255,220.0/255,128.0/255],[39.0/255,211.0/255,230.0/255]]
    static let reply = ["Booked", "for", "8", "pm."]
    static let ask = ["Book", "a", "table", "for", "two", "at", "eight"]
    static let states = ["Ready", "Listening", "Thinking", "Answering"]
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red:Double(hex>>16&255)/255,green:Double(hex>>8&255)/255,blue:Double(hex&255)/255,opacity:1)
    }
    static func rampAt(_ u: Double) -> [Double] {
        let a = ParticleRibbonVoiceMotion.smooth(0.3,0.47,u), b = ParticleRibbonVoiceMotion.smooth(0.49,0.66,u)
        return (0..<3).map { i in
            let c = ramp[0][i]+(ramp[1][i]-ramp[0][i])*a
            return c+(ramp[2][i]-c)*b
        }
    }
}

/// Shape with CoreText like RN's native Text, retaining kerning and fractional word widths.
@MainActor
final class ParticleRibbonVoiceTypography {
    struct Line {
        let path: Path, width: Double, ascent: Double, descent: Double, leading: Double
    }
    private var cache: [String: Line] = [:]
    private var fonts: [String: CTFont] = [:]
    func line(_ text: String, size: Double, tracking: Double = 0, weight: String = "Light") -> Line {
        let key = "\(weight)|\(size)|\(tracking)|\(text)"
        if let value = cache[key] { return value }
        let fontKey = "\(weight)|\(size)"
        let font: CTFont
        if let existing = fonts[fontKey] { font = existing }
        else {
            let url = Bundle.main.url(forResource:"ParticleRibbonVoice-InterTight-\(weight)",withExtension:"ttf")!
            let provider = CGDataProvider(url:url as CFURL)!
            font = CTFontCreateWithGraphicsFont(CGFont(provider)!,size,nil,nil)
            fonts[fontKey] = font
        }
        let attr = NSAttributedString(string:text,attributes:[.font:font,.kern:tracking])
        let ct = CTLineCreateWithAttributedString(attr), runs = CTLineGetGlyphRuns(ct) as! [CTRun]
        var path = Path()
        for run in runs {
            let count = CTRunGetGlyphCount(run)
            var glyphs = [CGGlyph](repeating:0,count:count), positions = [CGPoint](repeating:.zero,count:count)
            CTRunGetGlyphs(run,CFRange(location:0,length:count),&glyphs)
            CTRunGetPositions(run,CFRange(location:0,length:count),&positions)
            for i in 0..<count {
                if let p = CTFontCreatePathForGlyph(font,glyphs[i],nil) {
                    path.addPath(Path(p),transform:CGAffineTransform(a:1,b:0,c:0,d:-1,tx:positions[i].x,ty:-positions[i].y))
                }
            }
        }
        let result = Line(path:path,width:CTLineGetTypographicBounds(ct,nil,nil,nil),
                          ascent:CTFontGetAscent(font),descent:CTFontGetDescent(font),leading:CTFontGetLeading(font))
        cache[key] = result
        return result
    }
    func rows(_ text: String, size: Double, tracking: Double, weight: String, width: Double) -> [Line] {
        let words = text.split(separator:" ").map(String.init)
        var result: [Line] = [], current = ""
        for word in words {
            let next = current.isEmpty ? word:current+" "+word
            if !current.isEmpty && line(next,size:size,tracking:tracking,weight:weight).width > width {
                result.append(line(current,size:size,tracking:tracking,weight:weight)); current = word
            } else { current = next }
        }
        if !current.isEmpty { result.append(line(current,size:size,tracking:tracking,weight:weight)) }
        return result
    }
}

/// Touch-only cues, including RN's 60 ms gate. Ambient respects the mute switch and mixes with host audio.
@MainActor
final class ParticleRibbonVoiceFeedback {
    private var players: [String:AVAudioPlayer] = [:]
    private var lastCue = -Double.infinity
    func prepare() {
        for name in ["open","close"] {
            if let url = Bundle.main.url(forResource:"ParticleRibbonVoice-listen-\(name)",withExtension:"wav"),
               let player = try? AVAudioPlayer(contentsOf:url) {
                _ = player.prepareToPlay(); players[name] = player
            }
        }
    }
    func cue(_ name: String, sound: Bool) {
        let now = ProcessInfo.processInfo.systemUptime
        guard now-lastCue>=0.06 else { return }
        lastCue = now
        switch name {
        case "onset", "part": UIImpactFeedbackGenerator(style:.light).impactOccurred()
        case "release", "cross": UISelectionFeedbackGenerator().selectionChanged()
        case "answer": UINotificationFeedbackGenerator().notificationOccurred(.success)
        default: break
        }
        guard sound, name == "onset" || name == "release" else { return }
        // Do not activate an audio session at launch or during silent autoplay.
        let session = AVAudioSession.sharedInstance()
        if session.category != .ambient { try? session.setCategory(.ambient,mode:.default,options:[.mixWithOthers]) }
        try? session.setActive(true)
        let player = players[name == "onset" ? "open":"close"]
        player?.currentTime = 0; _ = player?.play()
    }
    func stop() { for player in players.values { player.stop() } }
}
