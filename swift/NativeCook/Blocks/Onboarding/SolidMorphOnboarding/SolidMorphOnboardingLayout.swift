import Foundation

struct SolidMorphOnboardingLayout: Equatable {
    let width: Double, viewportHeight: Double, topInset: Double, bottomInset: Double, fontScale: Double
    var scale: Double { width/402 }
    var compact: Bool { viewportHeight < 740 || largeText }
    var largeText: Bool { fontScale > 1.35 }
    var top: Double { max(topInset,24) }
    var progressY: Double { top+18*scale }
    var chipY: Double { top+(compact ? 64 : 82)*scale }
    var titleY: Double { chipY+(largeText ? 34*fontScale+10 : 40)*scale }
    var titleSize: Double { (compact ? 31 : 40)*scale*fontScale }
    var titleHeight: Double { titleSize*(largeText ? ceil(2*fontScale)*1.26 : 2*48/40) }
    var bodyY: Double { titleY+titleHeight+(largeText ? 6 : 12)*scale }
    var bodySize: Double { 16*scale*fontScale }
    var bodyHeight: Double { bodySize*(largeText ? ceil(3*fontScale+1)*1.35 : 3.1) }
    var buttonHeight: Double { max(56*scale,(fontScale > 2.5 ? 17*fontScale*2.4+24 : 26*fontScale+24)*scale) }
    var footerHeight: Double { largeText ? buttonHeight*2+8*scale : buttonHeight }
    var height: Double { max(viewportHeight,bodyY+bodyHeight+(largeText ? 240 : 0)*scale+footerHeight+bottomInset+52*scale) }
    var footerY: Double { height-max(bottomInset,20)-24*scale-footerHeight }
    var heroTop: Double { bodyY+bodyHeight+12*scale }
    var heroBottom: Double { footerY-18*scale }
    var heroRadius: Double { max(0,min(128*scale,(heroBottom-heroTop)/1.9)) }
    var heroX: Double { width/2 }
    var heroY: Double {
        compact ? (heroTop+heroBottom)/2 : max(heroTop+heroRadius*0.7,min(viewportHeight*(521/874),heroBottom-heroRadius*0.7))
    }
    var skipWidth: Double { max(48,30*fontScale+18)*scale }
    var buttonWidth: Double { largeText ? width-34*scale : (width-34*scale-8*scale)/2 }
}

enum SolidMorphOnboardingPages {
    struct Page {
        let chip: String, title: String, body: String
        let sides: Double, roundness: Double, split: Double, base: Double, light: Double
    }
    static let all = [
        Page(chip: "Go global", title: "Your money,\nwherever you go", body: "Hold, send and receive money across currencies,\nall from one account.", sides: 0, roundness: 1, split: 0, base: 0, light: 0),
        Page(chip: "Built to protect", title: "Your money,\nprotected", body: "Secure technology keeps your account and every\ntransaction protected around the clock.", sides: 0, roundness: 1, split: 1, base: 0, light: 1),
        Page(chip: "Zero delays", title: "Money moves,\nwhen you do", body: "Send, receive and access your money quickly\nwhenever you need it.", sides: 7, roundness: 0.15, split: 0, base: 2, light: 0)
    ]
}
