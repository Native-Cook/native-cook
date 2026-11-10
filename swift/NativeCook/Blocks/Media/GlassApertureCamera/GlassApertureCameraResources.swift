import AVFoundation
import ImageIO
import SwiftUI
import UIKit

final class GlassApertureCameraResources {
    static let shared = GlassApertureCameraResources()
    let scene: UIImage
    let sceneImage: Image
    struct Label { let text: Text; let size: CGSize }
    let chips: [String: Label]
    let to, share, sent, five: Label
    static let star = Color(.sRGB, red: 31/255, green: 211/255, blue: 242/255)
    init() {
        if let url = Bundle.main.url(forResource: "GlassApertureCamera-gac-street", withExtension: "jpg"),
           let source = CGImageSourceCreateWithURL(url as CFURL, nil),
           let cg = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary) {
            scene = UIImage(cgImage: cg)
        } else { scene = UIImage() }
        sceneImage = Image(uiImage: scene).interpolation(.low)
        func label(_ text: String, _ size: Double, _ weight: UIFont.Weight, _ tabular: Bool = false) -> Label {
            let font = tabular ? UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight) : UIFont.systemFont(ofSize: size, weight: weight)
            let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.white]
            let width = ceil((text as NSString).size(withAttributes: attrs).width)
            let dimensions = CGSize(width: width+4, height: ceil(font.lineHeight)+4)
            let swiftWeight: Font.Weight = weight == .semibold ? .semibold : weight == .medium ? .medium : .regular
            var textView = Text(text).font(.system(size: size, weight: swiftWeight))
            if tabular { textView = textView.monospacedDigit() }
            return Label(text: textView, size: dimensions)
        }
        var table: [String: Label] = [:]
        for i in 14...99 { let key = String(format: "%.1f", Double(i)/10); table[key] = label("ƒ"+key,14,.semibold,true) }
        for i in 10...16 { table[String(i)] = label("ƒ"+String(i),14,.semibold,true) }
        chips = table
        to = label("Friends @mira",13,.semibold)
        share = label("Sharable with other apps",12,.regular)
        sent = label("Sent to 4 friends",13,.semibold)
        five = label("5",13,.medium)
    }
}

final class GlassApertureCameraAudio {
    private var players: [String: AVAudioPlayer] = [:]
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: .mixWithOthers)
        for cue in ["shutter", "tick"] {
            if let url = Bundle.main.url(forResource: "GlassApertureCamera-gac-"+cue, withExtension: "wav"),
               let player = try? AVAudioPlayer(contentsOf: url) {
                player.volume = cue == "shutter" ? 0.7 : 0.45
                player.prepareToPlay(); players[cue] = player
            }
        }
    }
    func play(_ cue: String) { if let p = players[cue] { p.currentTime = 0; p.play() } }
    func stop() { for p in players.values { p.stop() } }
}
