import AVFoundation
import CoreText
import SwiftUI
import UIKit

enum BellPermissionPrimerResources {
    static func color(_ hex: Int) -> Color { Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255) }
    static let page=color(0xFDFDFE), ink=color(0x25323B), body=color(0x545C68)
    static let register: Void = {
        for name in ["Barlow-Regular","Barlow-Medium","Barlow-SemiBold","Barlow-Bold","BarlowSemiCondensed-Bold"] {
            if let url=Bundle.main.url(forResource:"BellPermissionPrimer-"+name,withExtension:"ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
            }
        }
    }()
    static let fonts: [String:UIFont] = {
        _ = register
        let specs: [(String,Double)]=[("BarlowSemiCondensed-Bold",43),("Barlow-Regular",22),("Barlow-SemiBold",17),("Barlow-Bold",17),("Barlow-SemiBold",12),("Barlow-Regular",12),("Barlow-SemiBold",16),("Barlow-Regular",14)]
        return Dictionary(uniqueKeysWithValues:specs.map { name,size in
            ("\(name)-\(size)",UIFont(name:name,size:size) ?? UIFont.systemFont(ofSize:size))
        })
    }()
}

struct BellPermissionPrimerLabel: UIViewRepresentable {
    let text: String, font: String
    let size: Double, line: Double?
    let color: Color
    var center=false, right=false, tracking=0.0, lines=1
    final class BellPermissionPrimerLabelCache { var key="" }
    func makeCoordinator() -> BellPermissionPrimerLabelCache { BellPermissionPrimerLabelCache() }
    func makeUIView(context:Context) -> BellPermissionPrimerNativeLabel {
        let label=BellPermissionPrimerNativeLabel(); label.backgroundColor = .clear
        label.setContentCompressionResistancePriority(.defaultLow,for:.horizontal)
        label.isAccessibilityElement=true
        return label
    }
    func updateUIView(_ view:BellPermissionPrimerNativeLabel,context:Context) {
        let key="\(text)|\(font)|\(size)|\(String(describing:line))|\(tracking)|\(center)|\(right)"
        guard context.coordinator.key != key else { return }; context.coordinator.key=key
        let face=BellPermissionPrimerResources.fonts["\(font)-\(size)"]!
        let p=NSMutableParagraphStyle(); p.alignment=center ? .center : (right ? .right : .left)
        p.lineBreakMode=lines==1 ? .byTruncatingTail : .byWordWrapping
        var attrs:[NSAttributedString.Key:Any] = [.font:face,.foregroundColor:UIColor(color),.paragraphStyle:p]
        if tracking != 0 { attrs[.kern]=tracking }
        if let line { p.minimumLineHeight=line; p.maximumLineHeight=line; attrs[.baselineOffset]=(line-face.lineHeight)/2 }
        view.baselinePixels = (font == "Barlow-Regular" && size == 22) ? -1 : ((font == "Barlow-SemiBold" && size == 16) ? 1 : 0)
        view.horizontalPixels = text == "now" ? 1 : 0
        view.numberOfLines=lines
        view.attributedText=NSAttributedString(string:text,attributes:attrs)
        view.accessibilityLabel=text
    }
}

final class BellPermissionPrimerAudio {
    private var players:[String:AVAudioPlayer]=[:]
    init() {
        try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
        for (cue,volume) in [("ding",Float(0.55)),("allow",0.6),("banner",0.6)] {
            if let url=Bundle.main.url(forResource:"BellPermissionPrimer-bpp-\(cue)",withExtension:"wav"),let player=try? AVAudioPlayer(contentsOf:url) {
                player.volume=volume; player.prepareToPlay(); players[cue]=player
            }
        }
    }
    func play(_ cue:String) { if let p=players[cue] { p.currentTime=0; p.play() } }
    func stop() { for p in players.values { p.stop() } }
}

final class BellPermissionPrimerNativeLabel:UILabel {
    var baselinePixels=0, horizontalPixels=0
    override func drawText(in rect:CGRect) {
        let scale=max(1,traitCollection.displayScale)
        super.drawText(in:rect.offsetBy(dx:CGFloat(horizontalPixels)/scale,dy:CGFloat(baselinePixels)/scale))
    }
}
