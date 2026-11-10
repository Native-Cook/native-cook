import CoreText
import ImageIO
import MetalKit
import UIKit

final class SmokedClocheRevealResources {
  static let shared = SmokedClocheRevealResources()
  let device: MTLDevice
  let queue: MTLCommandQueue
  let glass: MTLRenderPipelineState
  let smoke: MTLRenderPipelineState
  let compute: MTLComputePipelineState
  let texture: MTLTexture
  let sampler: MTLSamplerState
  let bowl: UIImage
  let leaves: [UIImage]
  private var faces=[String:String]()
  private var fonts=[String:UIFont]()
  static func color(_ hex: Int, _ alpha: Double = 1) -> UIColor {
    UIColor(red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,alpha:alpha)
  }
  init() {
    for weight in ["Regular","Medium","SemiBold","Bold"] {
      if let url=Bundle.main.url(forResource:"SmokedClocheReveal-Outfit-\(weight)",withExtension:"ttf") {
        CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
        if let ds=CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],let d=ds.first {
          faces[weight]=CTFontCopyPostScriptName(CTFontCreateWithFontDescriptor(d,14,nil)) as String
        }
      }
    }
    func image(_ name: String) -> UIImage {
      let url=Bundle.main.url(forResource:"SmokedClocheReveal-\(name)",withExtension:"png")!
      let source=CGImageSourceCreateWithURL(url as CFURL,nil)!
      let cg=CGImageSourceCreateImageAtIndex(source,0,[kCGImageSourceShouldCacheImmediately:true] as CFDictionary)!
      return UIImage(cgImage:cg)
    }
    bowl=image("scr-bowl");leaves=[image("scr-leaf-a"),image("scr-leaf-b")]
    let gpu=MTLCreateSystemDefaultDevice()!
    device=gpu;queue=gpu.makeCommandQueue()!
    let library=device.makeDefaultLibrary()!
    func pipeline(_ vertex: String, _ fragment: String) -> MTLRenderPipelineState {
      let d=MTLRenderPipelineDescriptor()
      d.vertexFunction=library.makeFunction(name:vertex);d.fragmentFunction=library.makeFunction(name:fragment)
      let a=d.colorAttachments[0]!
      a.pixelFormat = .bgra8Unorm;a.isBlendingEnabled=true
      a.sourceRGBBlendFactor = .one;a.destinationRGBBlendFactor = .oneMinusSourceAlpha
      a.sourceAlphaBlendFactor = .one;a.destinationAlphaBlendFactor = .oneMinusSourceAlpha
      return try! gpu.makeRenderPipelineState(descriptor:d)
    }
    glass=pipeline("smokedClocheRevealFullVs","smokedClocheRevealClocheFs")
    smoke=pipeline("smokedClocheRevealSmokeVs","smokedClocheRevealSmokeFs")
    compute=try! device.makeComputePipelineState(function:library.makeFunction(name:"smokedClocheRevealSmokeCs")!)
    let sd=MTLSamplerDescriptor();sd.minFilter = .linear;sd.magFilter = .linear
    sd.sAddressMode = .clampToEdge;sd.tAddressMode = .clampToEdge
    sampler=device.makeSamplerState(descriptor:sd)!
    let cg=bowl.cgImage!,w=cg.width,h=cg.height
    let td=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.rgba8Unorm,width:w,height:h,mipmapped:false)
    td.usage = .shaderRead;texture=device.makeTexture(descriptor:td)!
    var bytes=[UInt8](repeating:0,count:w*h*4)
    bytes.withUnsafeMutableBytes { b in
      let c=CGContext(data:b.baseAddress,width:w,height:h,bitsPerComponent:8,bytesPerRow:w*4,space:CGColorSpace(name:CGColorSpace.sRGB)!,bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue)!
      c.draw(cg,in:CGRect(x:0,y:0,width:w,height:h))
    }
    // copyExternalImageToTexture(premultipliedAlpha:false, colorSpaceConversion:none).
    for i in stride(from:0,to:bytes.count,by:4) where bytes[i+3] > 0 && bytes[i+3] < 255 {
      let a=Double(bytes[i+3])/255
      for j in 0..<3 { bytes[i+j]=UInt8(min(255,(Double(bytes[i+j])/a).rounded())) }
    }
    bytes.withUnsafeBytes { b in texture.replace(region:MTLRegionMake2D(0,0,w,h),mipmapLevel:0,withBytes:b.baseAddress!,bytesPerRow:w*4) }
  }
  func font(_ weight: String, _ size: Double) -> UIFont {
    let key="\(weight)-\(size)"
    if let f=fonts[key] { return f }
    let f=UIFont(name:faces[weight] ?? "Outfit-\(weight)",size:size) ?? UIFont.systemFont(ofSize:size)
    fonts[key]=f;return f
  }
}
