import CoreGraphics
import MetalKit

// Reduced motion disables the RN WebGPU view. Draw its stationary print mask once,
// then composite that mask through the same Metal view while the card gently fades.
enum PasskeyGlassSealArt {
    static let printPath: CGPath = {
        let p = CGMutablePath()
        for ridge in PasskeyGlassSealPrint.ridges() {
            for (i,pt) in ridge.pts.enumerated() {
                let at = CGPoint(x:pt.x,y:pt.y)
                if i == 0 { p.move(to:at) } else { p.addLine(to:at) }
            }
        }
        return p
    }()
    static func printMask(_ device: MTLDevice, _ l: PasskeyGlassSealTheme.Layout, _ scale: Double) -> MTLTexture? {
        let width = max(1,Int((l.cw*scale).rounded())), height = max(1,Int((l.ch*scale).rounded()))
        var pixels = [UInt8](repeating:0,count:width*height)
        let good = pixels.withUnsafeMutableBytes { bytes -> Bool in
            guard let c = CGContext(data:bytes.baseAddress,width:width,height:height,bitsPerComponent:8,bytesPerRow:width,space:CGColorSpaceCreateDeviceGray(),bitmapInfo:CGImageAlphaInfo.none.rawValue) else { return false }
            c.translateBy(x:0,y:Double(height)); c.scaleBy(x:scale,y:-scale)
            c.translateBy(x:l.px,y:l.py); c.scaleBy(x:l.s,y:l.s)
            c.setStrokeColor(gray:1,alpha:1); c.setLineWidth(1.8); c.setLineCap(.round); c.setLineJoin(.round)
            c.addPath(printPath); c.strokePath()
            return true
        }
        guard good else { return nil }
        let d = MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.r8Unorm,width:width,height:height,mipmapped:false)
        d.usage = .shaderRead
        guard let texture = device.makeTexture(descriptor:d) else { return nil }
        pixels.withUnsafeBytes { texture.replace(region:MTLRegionMake2D(0,0,width,height),mipmapLevel:0,withBytes:$0.baseAddress!,bytesPerRow:width) }
        return texture
    }
}
