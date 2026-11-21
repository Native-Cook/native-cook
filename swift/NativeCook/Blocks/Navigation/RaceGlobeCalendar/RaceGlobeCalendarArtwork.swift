import SwiftUI
import UIKit
import CoreText

final class RaceGlobeCalendarArtwork {
 static let shared=RaceGlobeCalendarArtwork()
 let atlas:RaceGlobeCalendarAtlas
 let titles:[String],wheres:[String],chips:[String],pre:[String],suf:[String],circuits:[Path]
 let icons:[String:Path]
 private var fonts:[String:CGFont]=[:],cache:[String:(Path,Double)]=[:]
 let stars:[[Double]],arcs:[[[Double]]]
 private init() {
  let maps = ["earth-day.jpg", "earth-night-2048.jpg", "earth-mask-2048.png", "earth-night-512.jpg"].map { name -> URL in
   let split = name.split(separator: ".")
   guard let url = Bundle.main.url(forResource: "RaceGlobeCalendar-" + split[0], withExtension: String(split[1])) else {
    preconditionFailure("Race Globe Calendar: missing bundled map " + name)
   }
   return url
  }
  atlas = RaceGlobeCalendarAtlas(urls: maps)
  let url=Bundle.main.url(forResource:"RaceGlobeCalendar-rounds",withExtension:"json")!
  let rows=try! JSONSerialization.jsonObject(with:Data(contentsOf:url)) as! [[String:Any]]
  titles=rows.map { $0["title"] as! String };wheres=rows.map { ($0["place"] as! String)+", "+($0["country"] as! String) };chips=rows.map { $0["chip"] as! String }
  pre=rows.map { ($0["dateLine"] as! String).components(separatedBy:$0["localStart"] as! String)[0] };suf=rows.map { ($0["dateLine"] as! String).components(separatedBy:$0["localStart"] as! String).last! };circuits=rows.map { Path(RaceGlobeCalendarSVG.path($0["circuitPath"] as! String)) }
  let raw:[String:String]=["SHUFFLE": "M3 6h2c5 0 9 12 14 12h2 M17 14l4 4-4 4 M3 18h2c2 0 4-3 6-6c2-3 5-6 8-6h2 M17 2l4 4-4 4", "TROPHY": "M7 3h10v7c0 7-10 7-10 0z M7 5H3v3c0 4 4 5 5 4 M17 5h4v3c0 4-4 5-5 4 M12 15v5 M8 21h8", "FLAG": "M5 22V3 M5 4c5-4 9 4 15 0v10c-6 4-10-4-15 0"];icons=raw.mapValues { Path(RaceGlobeCalendarSVG.path($0)) }
  for name in ["display-semibold","regular","semibold","bold"] { if let url=Bundle.main.url(forResource:"RaceGlobeCalendar-inter-"+name,withExtension:"ttf"),let provider=CGDataProvider(url:url as CFURL),let font=CGFont(provider) { fonts[name]=font;CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil) } }
  var seed=7.0,stars:[[Double]]=[]
  func rnd()->Double { seed=(seed*16807).truncatingRemainder(dividingBy:2147483647);return seed/2147483647 }
  for _ in 0..<140 { let x=rnd()*402,y=rnd()*874,r=0.5+rnd()*0.7,b=min(3,floor(rnd()*4)),g=rnd()<0.5 ? 0.0 : 1.0;stars.append([x,y,r,0.15+0.15*b,g]) };self.stars=stars
  let m=RaceGlobeCalendarMotion.self,w=RaceGlobeCalendarData.W
  arcs=(0..<23).map { j in let a=w[j],b=w[j+1],om=acos(min(max(m.dot(a,b),-1),1)),lift=0.02+0.04*min(om/(120 * .pi/180),1)
   return (0...40).map { k in let s=Double(k)/40,ka=sin((1-s)*om)/sin(om),kb=sin(s*om)/sin(om),h=1+lift*sin(.pi*s);return (0..<3).map { (a[$0]*ka+b[$0]*kb)*h } }
  }
 }
 func run(_ text:String,_ size:Double,_ face:String="regular")->(Path,Double) {
  let key="\(text)|\(size)|\(face)";if let value=cache[key] { return value };guard let cg=fonts[face] else { return (Path(),0) }
  let font=CTFontCreateWithGraphicsFont(cg,size,nil,nil),chars=Array(text.utf16);var glyphs=[CGGlyph](repeating:0,count:chars.count),advances=[CGSize](repeating:.zero,count:chars.count)
  CTFontGetGlyphsForCharacters(font,chars,&glyphs,chars.count);CTFontGetAdvancesForGlyphs(font,.horizontal,glyphs,&advances,chars.count)
  let p=CGMutablePath();var x=0.0
  for i in glyphs.indices { if let g=CTFontCreatePathForGlyph(font,glyphs[i],nil) { p.addPath(g,transform:CGAffineTransform(a:1,b:0,c:0,d:-1,tx:x,ty:0)) };x+=advances[i].width }
  let result=(Path(p),x);cache[key]=result;return result
 }
 static func color(_ n:UInt32,_ a:Double=1)->Color { Color(.sRGB,red:Double((n>>16)&255)/255,green:Double((n>>8)&255)/255,blue:Double(n&255)/255,opacity:a) }
 func text(_ c:inout GraphicsContext,_ str:String,_ x:Double,_ y:Double,_ size:Double,_ face:String="regular",_ alpha:Double=1) { c.fill(run(str,size,face).0.applying(CGAffineTransform(translationX:x,y:y)),with:.color(.white.opacity(alpha))) }
}
