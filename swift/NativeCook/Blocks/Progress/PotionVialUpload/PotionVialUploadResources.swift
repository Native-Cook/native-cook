import CoreGraphics
import CoreText
import CoreImage
import ImageIO
import SwiftUI
import UIKit

final class PotionVialUploadResources {
  static let shared = PotionVialUploadResources()
  struct PotionVialUploadLabel { let image: UIImage; let width, baseline: Double }
  var labels: [String:PotionVialUploadLabel] = [:]
  private var faces: [String:CGFont] = [:]
  var glows: [Double:CGImage] = [:]
  private var cardPaths: [Double:CGPath] = [:]
  func cardPath(_ y: Double) -> CGPath {
    if let path = cardPaths[y] { return path }
    let path = CGPath(roundedRect:CGRect(x:76,y:y,width:250,height:68),cornerWidth:14,cornerHeight:14,transform:nil)
    cardPaths[y] = path; return path
  }
  var halo: CGImage?
  var bands: [CGGradient] = []
  let file: UIImage
  init() {
    typealias A = PotionVialUploadArt
    for weight in ["Regular","Medium","Bold"] {
      if let url = Bundle.main.url(forResource:"PotionVialUpload-PixelifySans-\(weight)",withExtension:"ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
        if let provider = CGDataProvider(url:url as CFURL), let face = CGFont(provider) { faces[weight] = face }
      }
    }
    if let url = Bundle.main.url(forResource:"PotionVialUpload-pvu-file",withExtension:"png"), let src = CGImageSourceCreateWithURL(url as CFURL,nil), let cg = CGImageSourceCreateImageAtIndex(src,0,[kCGImageSourceShouldCacheImmediately:true] as CFDictionary) { file = UIImage(cgImage:cg) } else { file = UIImage() }
    let entries: [(String,String,Double)] = [
      ("Grimoire","Medium",15),("Slot 3 of 5","Regular",12),("Add a spell pack","Bold",26),
      ("Zip files up to 200 MB. Every pack is checked for curses.","Regular",12),
      ("Drop a file into the potion","Regular",13),("Release to drop it in","Regular",13),
      ("Upload complete","Regular",13),("spells.zip","Medium",15),("spells.zip","Regular",12),
      ("48.2 MB","Regular",12),("48.2 MB · drag it into the potion","Regular",12),
      ("Brewing into your grimoire…","Regular",12),("Added to your grimoire","Regular",12),("%","Bold",17)]
    for (text,weight,size) in entries { add(text,weight,size) }
    for i in 0...100 { add("\(i)","Bold",38) }
    for i in 0...482 { add(String(format:"%.1f of 48.2 MB",locale:Locale(identifier:"en_US_POSIX"),Double(i)/10),"Regular",13) }
    for cols in [["#ffd277","#e9a944","#e9a944","#a35424"],["#d4d0dc","#918d9b","#918d9b","#55525e"]] {
      bands.append(CGGradient(colorsSpace:CGColorSpace(name:CGColorSpace.sRGB)!,colors:cols.map{A.color($0)} as CFArray,locations:[0,0.28,0.62,1])!)
    }
    let ci = CIContext(options:[.workingColorSpace:CGColorSpace(name:CGColorSpace.sRGB)!])
    for sigma in [9.0,24] {
      let source = bitmap(540,240) { c in c.translateBy(x:100,y:120); A.fill(c,A.outer,"#4fe02f") }
      if let source { let image = CIImage(cgImage:source).applyingFilter("CIGaussianBlur",parameters:[kCIInputRadiusKey:sigma]); glows[sigma] = ci.createCGImage(image,from:CGRect(x:0,y:0,width:540,height:240)) }
    }
    if let source = bitmap(125,125,{ c in A.circle(c,62.4,62.4,26.4,"#4fe02f") }) {
      halo = ci.createCGImage(CIImage(cgImage:source).applyingFilter("CIGaussianBlur",parameters:[kCIInputRadiusKey:11.0]),from:CGRect(x:0,y:0,width:125,height:125))
    }
  }
  private func bitmap(_ w: Int, _ h: Int, _ draw: (CGContext)->Void) -> CGImage? {
    guard let c = CGContext(data:nil,width:w,height:h,bitsPerComponent:8,bytesPerRow:w*4,space:CGColorSpace(name:CGColorSpace.sRGB)!,bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
    draw(c); return c.makeImage()
  }
  static func key(_ text: String,_ size: Double) -> String { "\(size):\(text)" }
  private func add(_ text: String,_ weight: String,_ size: Double) {
    // All three source files share the PostScript name PixelifySans-Regular.
    // Bind the file's CGFont directly so native registration cannot substitute the 400 face
    // for the 500/700 faces (Skia likewise creates each face from its own font data).
    let font = faces[weight].map { CTFontCreateWithGraphicsFont($0,size,nil,nil) } ?? (UIFont.systemFont(ofSize:size) as CTFont)
    let chars = Array(text.utf16); var glyphs = [CGGlyph](repeating:0,count:chars.count), advances = [CGSize](repeating:.zero,count:chars.count)
    CTFontGetGlyphsForCharacters(font,chars,&glyphs,chars.count); CTFontGetAdvancesForGlyphs(font,.horizontal,glyphs,&advances,chars.count)
    var x = 0.0; let points = advances.map { advance -> CGPoint in defer { x += advance.width }; return CGPoint(x:x,y:0) }
    let baseline = 4+CTFontGetAscent(font), dimensions = CGSize(width:ceil(x+8),height:ceil(baseline+CTFontGetDescent(font)+4))
    let format = UIGraphicsImageRendererFormat(); format.scale = 3
    let image = UIGraphicsImageRenderer(size:dimensions,format:format).image { renderer in
      let c = renderer.cgContext; c.setFillColor(UIColor.white.cgColor); c.translateBy(x:4,y:baseline); c.scaleBy(x:1,y:-1); CTFontDrawGlyphs(font,glyphs,points,glyphs.count,c)
    }
    labels[Self.key(text,size)] = .init(image:image,width:x,baseline:baseline)
  }
}

extension PotionVialUploadArt {
  static func text(_ c: GraphicsContext,_ string: String,_ size: Double,_ x: Double,_ y: Double,_ col: String,_ alpha: Double = 1,_ centered: Bool = false) {
    guard alpha>0.01, let label = PotionVialUploadResources.shared.labels[PotionVialUploadResources.key(string,size)] else { return }
    var ctx = c; ctx.opacity *= alpha
    var image = ctx.resolve(Image(uiImage:label.image)); image.shading = .color(Color(cgColor:color(col)))
    ctx.draw(image,in:CGRect(x:x-(centered ? label.width/2:0)-4,y:y-label.baseline,width:label.image.size.width,height:label.image.size.height))
  }
  static func width(_ text: String,_ size: Double) -> Double { PotionVialUploadResources.shared.labels[PotionVialUploadResources.key(text,size)]?.width ?? 0 }
  static func type(_ context: GraphicsContext,_ g: G,_ f: F) {
    var c = context; c.translateBy(x:g.ox,y:0); c.scaleBy(x:g.s,y:g.s)
    let top = g.top/g.s
    var b = c; b.translateBy(x:0,y:top); b.fill(Path(back).strokedPath(StrokeStyle(lineWidth:2,lineCap:.round,lineJoin:.round)),with:.color(Color(cgColor:color(C.mute))),style:FillStyle(antialiased:false))
    text(c,"Grimoire",15,34,top+28,C.ink,0.92)
    text(c,"Slot 3 of 5",12,382-width("Slot 3 of 5",12),top+27,C.mute)
    text(c,"Add a spell pack",26,20,top+82,C.ink)
    text(c,"Zip files up to 200 MB. Every pack is checked for curses.",12,20,top+106,C.mute)
    let cy = g.cy/g.s, ty = cy+82
    text(c,"Drop a file into the potion",13,201,ty,C.mute,f.restA,true)
    text(c,"Release to drop it in",13,201,ty,C.ready,f.readyA,true)
    if f.counterA>0.01 {
      let pct = "\(Int(floor(f.p*100+0.0001)))", wd = width(pct,38), wp = width("%",17), x = 201-(wd+wp+2)/2, base = cy+52+38*0.72
      text(c,pct,38,x,base,C.ink,f.counterA); text(c,"%",17,x+wd+2,base-38*0.42,C.mute,f.counterA)
      text(c,M.mbText(f.p),13,201,base+24,C.mute,f.counterA*(1-M.smooth(f.doneA*3)),true)
      text(c,"Upload complete",13,201,base+24,C.done,f.counterA*M.smooth((f.doneA-0.35)/0.65),true)
    }
    let cardTop = (g.cy+g.cardY*g.s)/g.s, tx = 134.0
    text(c,"spells.zip",15,tx,cardTop+30,C.ink,0.95)
    let lines = ["48.2 MB · drag it into the potion","Brewing into your grimoire…","Added to your grimoire"], cols = [C.mute,C.mute,C.done], st = f.cardState
    let since = st==2 ? f.t-8.4:st==1 ? f.t-1.75:1, inK = M.smooth(M.seg(since,0.1,0.2))
    if since<0.1 && st>0 { text(c,lines[st-1],12,tx,cardTop+49,cols[st-1],1-M.smooth(since/0.1)) }
    text(c,lines[st],12,tx,cardTop+49,cols[st],st==0 ? 1:inK)
    func icon(_ x:Double,_ y:Double,_ scale:Double,_ rot:Double,_ a:Double) {
      if a<=0.01 { return }; var q = c; q.translateBy(x:x,y:y); q.rotate(by:.radians(rot)); q.scaleBy(x:scale,y:scale); q.opacity *= a
      q.draw(Image(uiImage:PotionVialUploadResources.shared.file).interpolation(.none),in:CGRect(x:-14,y:-17,width:28,height:34))
    }
    icon(g.icon.x,(g.cy+g.icon.y*g.s)/g.s,1,0,1-f.slotEmpty)
    if f.ghostAlpha>0.01 {
      let gy = (g.cy+f.ghostY*g.s)/g.s, la = f.ghostAlpha*M.clamp01((f.ghostScale-0.7)/0.3)
      icon(f.ghostX,gy,f.ghostScale,f.ghostRot,f.ghostAlpha)
      text(c,"spells.zip",12,f.ghostX,gy+31,C.ink,la,true); text(c,"48.2 MB",12,f.ghostX,gy+45,C.mute,la,true)
    }
    if f.fade>0.003 { context.fill(Path(CGRect(x:0,y:0,width:g.w,height:g.h)),with:.color(Color(cgColor:color(C.page,f.fade)))) }
  }
}
