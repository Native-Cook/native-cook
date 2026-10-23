import AppKit
import ImageIO
import UniformTypeIdentifiers

// Crops the authored generated textures and packs equal circular sprites; no source frames are used.
// Usage: swift pack-materials.swift <generated-materials.png> <generated-clear.png> <output.png>
let arguments = CommandLine.arguments
precondition(arguments.count == 4)
func load(_ path: String) -> CGImage {
    CGImageSourceCreateImageAtIndex(CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil)!, 0, nil)!
}
let materials = load(arguments[1]), glass = load(arguments[2])
let crops: [(Double, Double, Double)] = [(250.5,249.5,174),(675.5,250.5,174),(1101.5,252.5,173),(625.5,624.5,414),
                                         (247.5,648.5,174),(675.5,648.5,176),(1098.5,649.5,177),(1527.5,651.5,175)]
let context = CGContext(data: nil, width: 2048, height: 256, bitsPerComponent: 8, bytesPerRow: 2048 * 4,
                        space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
context.interpolationQuality = .high
for (index, crop) in crops.enumerated() {
    let (x,y,r) = crop
    let source = (index == 3 ? glass : materials).cropping(to: CGRect(x: x-r, y: y-r, width: r*2, height: r*2))!
    let rect = CGRect(x: Double(index * 256 + 16), y: 16, width: 224, height: 224)
    context.saveGState(); context.addEllipse(in: rect); context.clip(); context.draw(source, in: rect); context.restoreGState()
}
let destination = CGImageDestinationCreateWithURL(URL(fileURLWithPath: arguments[3]) as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(destination, context.makeImage()!, nil)
precondition(CGImageDestinationFinalize(destination))
