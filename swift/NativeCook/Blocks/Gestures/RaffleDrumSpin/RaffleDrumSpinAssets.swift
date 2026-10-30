import AVFoundation
import CoreText
import ImageIO
import SwiftUI
import UIKit

enum RaffleDrumSpinAssets {
    static let archivo = "Archivo-ExtraBold"
    static let interRegular = "Inter24pt-Regular"
    static let interMedium = "Inter-Medium"
    static let interSemiBold = "Inter-SemiBold"
    static let interBold = "Inter24pt-Bold"
    static let sourceSerif = "SourceSerif4-Semibold"

    private static let fontFiles = [
        "RaffleDrumSpin-Archivo-ExtraBold",
        "RaffleDrumSpin-Inter-Regular",
        "RaffleDrumSpin-Inter-Medium",
        "RaffleDrumSpin-Inter-SemiBold",
        "RaffleDrumSpin-Inter-Bold",
        "RaffleDrumSpin-SourceSerif4-Semibold"
    ]

    private static var registered = false
    private static let images = NSCache<NSString, UIImage>()

    static func registerFonts() {
        guard !registered else { return }
        registered = true
        for name in fontFiles {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    static func image(_ name: String) -> Image? {
        if let cached = images.object(forKey:name as NSString) { return Image(uiImage:cached) }
        guard let url = Bundle.main.url(forResource: "RaffleDrumSpin-\(name)", withExtension: name == "env-studio" ? "jpg" : "webp") else { return nil }
        let image: UIImage
        if let source=CGImageSourceCreateWithURL(url as CFURL,nil),
           let decoded=CGImageSourceCreateImageAtIndex(source,0,[kCGImageSourceShouldCacheImmediately:true] as CFDictionary) {
            // Materialize the ImageIO provider once. Preserve its exact pixel format
            // and bytes so shader draws no longer enter the WebP decoder.
            if let pixels=decoded.dataProvider?.data,
               CFDataGetLength(pixels) == decoded.bytesPerRow * decoded.height,
               let provider=CGDataProvider(data:pixels), let space=decoded.colorSpace,
               let stored=CGImage(
                width:decoded.width,height:decoded.height,bitsPerComponent:decoded.bitsPerComponent,
                bitsPerPixel:decoded.bitsPerPixel,bytesPerRow:decoded.bytesPerRow,space:space,
                bitmapInfo:decoded.bitmapInfo,provider:provider,decode:nil,
                shouldInterpolate:decoded.shouldInterpolate,intent:decoded.renderingIntent
               ) {
                image=UIImage(cgImage:stored)
            } else { image=UIImage(cgImage:decoded) }
        } else if let fallback=UIImage(contentsOfFile:url.path) { image=fallback }
        else { return nil }
        images.setObject(image,forKey:name as NSString)
        return Image(uiImage: image)
    }
}

@MainActor
final class RaffleDrumSpinAudio {
    enum Cue: String {
        case select
        case spin
        case pop
        case stitches
        case morph
        case burst
        case tick
        case tear
        case dust
        case ratchet
        case landStub = "land-stub"
        case land
    }

    private var players: [Cue: [AVAudioPlayer]] = [:]
    private var next: [Cue: Int] = [:]
    private var last: [Cue: TimeInterval] = [:]
    private var active = true

    init() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.ambient, options: [.mixWithOthers])
        for cue in [Cue.select, .spin, .pop, .stitches, .morph, .burst, .tick, .tear, .dust, .ratchet, .landStub, .land] {
            let names = cue == .land ? ["land1", "land2", "land3"] : [cue.rawValue]
            let count = cue == .ratchet || cue == .tick ? 3 : names.count
            players[cue] = (0..<count).compactMap { index in
                guard let url = Bundle.main.url(forResource: "RaffleDrumSpin-\(names[index % names.count])", withExtension: "wav"),
                      let player = try? AVAudioPlayer(contentsOf: url) else { return nil }
                player.prepareToPlay()
                player.volume = volume(cue)
                return player
            }
        }
    }

    private func volume(_ cue: Cue) -> Float {
        switch cue {
        case .select, .morph: 0.3
        case .land: 0.46
        case .ratchet: 0.14
        case .spin: 0.12
        case .pop, .landStub: 0.42
        case .stitches: 0.24
        case .burst: 0.28
        case .tick: 0.13
        case .tear: 0.36
        case .dust: 0.22
        }
    }

    func setActive(_ value: Bool) {
        active = value
        if !value { for pool in players.values { for player in pool { player.pause() } } }
    }

    func play(_ cue: Cue) {
        guard active else { return }
        guard let pool = players[cue], !pool.isEmpty else { return }
        let now = ProcessInfo.processInfo.systemUptime
        if cue == .ratchet || cue == .tick, now - (last[cue] ?? -.infinity) < 0.05 { return }
        last[cue] = now
        let index = next[cue, default: 0] % pool.count
        next[cue] = index + 1
        pool[index].currentTime = 0
        pool[index].play()
    }
}
