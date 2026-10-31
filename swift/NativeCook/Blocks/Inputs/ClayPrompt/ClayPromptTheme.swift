import SwiftUI
import CoreText

enum ClayPromptTheme {
  static let prompt="a cat on a sofa under the moon"
  static func color(_ hex:UInt32)->Color { Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,opacity:1) }
  static let page=color(0x121018), surface=color(0x1E1B26), line=color(0x2E2A3A)
  static let ink=color(0xF6F3FA), mute=color(0x9792A6), faint=color(0x5E596D)
  static let accent=color(0xFFB86B), accentInk=color(0x1E1206)
  static let tints=[color(0xFFA15A),color(0x6FA4FF),color(0xFFD86A),color(0x73D883)]
  static let words=[["cat","cats","kitten","kitty"],["sofa","couch","sofas"],["moon","crescent"],["plant","plants","cactus","fern"]]
  static func kind(_ word:String)->Int? {
    let w=word.lowercased().filter { $0.isASCII && $0.isLetter }
    return words.firstIndex { $0.contains(w) }
  }
  static func kinds(_ text:String)->[Int] {
    var out=[Int]()
    for w in text.split(whereSeparator: { $0.isWhitespace }) {
      if let k=kind(String(w)), !out.contains(k), out.count < 4 { out.append(k) }
    }
    return out
  }
  static let fonts:[String:String] = {
    var names=[String:String]()
    for weight in ["Regular","Medium","SemiBold"] {
      if let url=Bundle.main.url(forResource:"ClayPrompt-Inter-\(weight)",withExtension:"ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
        if let descriptors=CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor], let d=descriptors.first,
           let name=CTFontDescriptorCopyAttribute(d,kCTFontNameAttribute) as? String { names[weight]=name }
      }
    }
    return names
  }()
  static func font(_ size:Double,_ weight:String="Regular")->Font { .custom(fonts[weight] ?? (weight == "Regular" ? "Inter24pt-Regular" : "Inter-\(weight)"),size:size) }
  struct Layout {
    var w,h,barTop,cardY,cardW,cardH,captionTop,tokenY,composerTop:Double
    var cx:Double { w/2 }; var cy:Double { cardY+cardH*0.52 }
    init(_ w:Double,_ h:Double,_ top:Double,_ bottom:Double) {
      self.w=w;self.h=h;barTop=top+8;composerTop=h-bottom-12-120;tokenY=composerTop-56
      cardY=barTop+56;cardW=w-32;cardH=min(cardW*1.18,tokenY-60-cardY);captionTop=cardY+cardH+16
    }
  }
}
