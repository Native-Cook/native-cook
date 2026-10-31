import AVFoundation
import CoreImage
import CoreText
import ImageIO
import MetalKit
import UIKit
import SwiftUI

final class ZodiacJellyDishResources {
  static let shared = ZodiacJellyDishResources()
  let dish: CGImage?
  private var labels: [Int: CGImage] = [:]
  lazy var shadowImage: Image? = shadow.map { Image(decorative:$0,scale:1) }
  struct Scene {
    let size: CGSize
    let top: CGFloat
    let background: Path
    let sheet: Path
    let stars: [Path]
  }
  private var cachedScene: Scene?
  func scene(_ size: CGSize, _ top: CGFloat) -> Scene {
    if let scene = cachedScene, scene.size == size && scene.top == top { return scene }
    let stars = (0..<70).map { i -> Path in
      let s1 = sin(Double(i)*12.9898+78.233)*43758.5453
      let s2 = sin(Double(i)*7.31+3.1)*24634.6345
      let side = i%7 == 0 ? 1.4 : 0.9
      return Path(CGRect(x:(s1-floor(s1))*size.width,y:(s2-floor(s2))*size.height,width:side,height:side))
    }
    let result = Scene(size:size,top:top,background:Path(CGRect(origin:.zero,size:size)),
      sheet:Path(roundedRect:CGRect(x:0,y:top+2,width:size.width,height:size.height-top-2+40),cornerRadius:34),stars:stars)
    cachedScene = result; return result
  }
  let shadow: CGImage?
  private var faces: [String: CGFont] = [:]
  private let ci = CIContext(options:[.workingColorSpace:NSNull()])
  static let bodyLeading = 19-UIFont.systemFont(ofSize:13.5).lineHeight
  private var sounds: [String: AVAudioPlayer] = [:]
  init() {
    for weight in ["Black", "ExtraBold", "SemiBold"] {
      if let url = Self.url("Nunito-\(weight)", "ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        if let provider = CGDataProvider(url:url as CFURL), let face = CGFont(provider) { faces[weight] = face }
      }
    }
    if let url = Self.url("dish", "webp"), let src = CGImageSourceCreateWithURL(url as CFURL, nil) {
      dish = CGImageSourceCreateImageAtIndex(src, 0, [kCGImageSourceShouldCacheImmediately:true] as CFDictionary).map(Self.srgb)
    } else { dish = nil }
    let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.preferredRange = .standard
    let base = UIGraphicsImageRenderer(size: CGSize(width: 467, height: 599), format: format).image { r in
      r.cgContext.setFillColor(UIColor.black.withAlphaComponent(0.55).cgColor)
      r.cgContext.addPath(UIBezierPath(roundedRect: CGRect(x:100,y:118,width:267,height:409), cornerRadius:64).cgPath)
      r.cgContext.fillPath()
    }
    if let cg = base.cgImage {
      let input = CIImage(cgImage:cg)
      let blur = input.applyingFilter("CIGaussianBlur", parameters:[kCIInputRadiusKey:26])
      shadow = CIContext().createCGImage(blur, from: input.extent)
    } else { shadow = nil }
  }
  // Keep original sRGB texels: the shader converts to P3 after bilinear sampling, as Skia does.
  static func srgb(_ image: CGImage) -> CGImage {
    guard let space = CGColorSpace(name:CGColorSpace.sRGB), let c = CGContext(data:nil,width:image.width,height:image.height,bitsPerComponent:8,bytesPerRow:image.width*4,space:space,bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue) else { return image }
    c.draw(image,in:CGRect(x:0,y:0,width:image.width,height:image.height))
    return c.makeImage() ?? image
  }
  static func url(_ name: String, _ ext: String) -> URL? {
    Bundle.main.url(forResource:"ZodiacJellyDish-\(name)",withExtension:ext)
  }
  func label(_ scale: CGFloat) -> CGImage? {
    let key = Int((scale*100).rounded())
    if let image = labels[key] { return image }
    let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.preferredRange = .standard
    let bitmap = UIGraphicsImageRenderer(size:CGSize(width:287,height:419), format:format).image { r in
      let c = r.cgContext
      c.translateBy(x:143.5,y:209.5)
      func put(_ text:String, _ weight:String, _ size:CGFloat, _ cx:CGFloat, _ base:CGFloat, _ color:UIColor, _ glow:UIColor) {
        // The supplied static TTFs all retain the same Nunito-ExtraLight PostScript name.
        // A file-backed CGFont preserves their distinct 900/800/600 outlines despite that collision.
        let f = faces[weight].map { CTFontCreateWithGraphicsFont($0,size,nil,nil) }
          ?? (UIFont.systemFont(ofSize:size,weight:.black) as CTFont)
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating:0,count:chars.count)
        CTFontGetGlyphsForCharacters(f, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating:.zero,count:chars.count)
        CTFontGetAdvancesForGlyphs(f,.horizontal,glyphs,&advances,chars.count)
        var x = cx-advances.reduce(0) { $0+$1.width }/2
        let points = advances.map { a -> CGPoint in let p = CGPoint(x:x,y:0); x += a.width; return p }
        // Skia MakeBlur takes sigma directly: use Gaussian radius 5 pt, scaled to texture pixels.
        let glowImage = UIGraphicsImageRenderer(size:CGSize(width:287,height:419),format:format).image { renderer in
          let g = renderer.cgContext
          g.translateBy(x:143.5,y:209.5+base); g.scaleBy(x:1,y:-1)
          g.setFillColor(glow.withAlphaComponent(0.75).cgColor)
          CTFontDrawGlyphs(f,glyphs,points,glyphs.count,g)
        }
        if let cg = glowImage.cgImage {
          let input = CIImage(cgImage:cg)
          if let blurred = ci.createCGImage(input.applyingFilter("CIGaussianBlur",parameters:[kCIInputRadiusKey:5*scale]),from:input.extent) {
            c.saveGState(); c.translateBy(x:-143.5,y:-209.5)
            UIImage(cgImage:blurred,scale:scale,orientation:.up).draw(in:CGRect(x:0,y:0,width:287,height:419))
            c.restoreGState()
          }
        }
        c.saveGState(); c.translateBy(x:0,y:base); c.scaleBy(x:1,y:-1)
        c.setFillColor(color.cgColor)
        CTFontDrawGlyphs(f,glyphs,points,glyphs.count,c)
        c.restoreGState()
      }
      let yellow = UIColor(red:1,green:210/255,blue:58/255,alpha:1)
      let pink = UIColor(red:1,green:95/255,blue:180/255,alpha:1)
      let orange = UIColor(red:1,green:154/255,blue:31/255,alpha:1)
      put("CHEESE","Black",30,27,-148,.white,pink)
      put("PANCAKES","Black",30,27,-99,.white,pink)
      put("VIRGO","Black",29,8,113,yellow,orange)
      put("1.09–7.09","ExtraBold",21,15,146,yellow,orange)
    }
    let image = bitmap.cgImage.map(Self.srgb)
    labels[key] = image
    return image
  }
  func preload(_ enabled: Bool) {
    guard enabled else { for p in sounds.values { p.stop() }; return }
    if !sounds.isEmpty { return }
    try? AVAudioSession.sharedInstance().setCategory(.ambient,mode:.default,options:.mixWithOthers)
    for cue in ["squish","add","tap"] {
      if let url = Self.url("zjd-\(cue)","wav"), let p = try? AVAudioPlayer(contentsOf:url) {
        p.volume = cue == "tap" ? 0.5 : 0.85; p.prepareToPlay(); sounds[cue] = p
      }
    }
  }
  func play(_ cue: String, _ reduced: Bool) {
    guard !reduced, let p = sounds[cue] else { return }
    p.currentTime = 0; p.play()
  }
}
