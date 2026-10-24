import SwiftUI
import UIKit

/// Native vectors preserve the reference's silhouette and independent flame acting.
final class BuzzStreaksIllustrations {
    private struct GradientData: Decodable { let start: [Double]; let end: [Double]; let colors: [[Double]] }
    private struct Layer: Decodable {
        let paths: [[Double]]
        let curves: [[Double]]
        let fillRGB: [Double]?
        let gradient: GradientData?
    }
    private struct Prize: Decodable { let layers: [Layer] }
    private struct BonusFrame: Decodable { let flame: [[Double]]; let white: [[Double]]; let visible: Bool }
    private struct Bonus: Decodable { let coin: [Double]; let colors: [String:[Double]]; let frames: [BonusFrame]; let glyphCurves: [[Double]] }
    private struct Scene: Decodable { let prize: Prize; let bonus: Bonus }
    private let scene: Scene
    private let prizePaths: [Path]
    private let flamePaths: [Path]
    private let whitePaths: [Path]
    private let glyphPath: Path

    init() {
        guard let url=Bundle.main.url(forResource:"BuzzStreaks-illustrations",withExtension:"json"),
              let data=try? Data(contentsOf:url), let scene=try? JSONDecoder().decode(Scene.self,from:data)
        else { preconditionFailure("BuzzStreaks requires source illustration geometry.") }
        self.scene=scene
        prizePaths=scene.prize.layers.map { Self.cubicPath($0.curves) }
        flamePaths=scene.bonus.frames.map { Self.softPath($0.flame) }
        whitePaths=scene.bonus.frames.map { Self.softPath($0.white) }
        glyphPath=Self.cubicPath(scene.bonus.glyphCurves)
    }

    private static func cubicPath(_ contours:[[Double]])->Path {
        Path { p in
            for points in contours {
                p.move(to:CGPoint(x:points[0],y:points[1]))
                for i in stride(from:2,to:points.count,by:6) {
                    p.addCurve(to:CGPoint(x:points[i+4],y:points[i+5]),
                        control1:CGPoint(x:points[i],y:points[i+1]),
                        control2:CGPoint(x:points[i+2],y:points[i+3]))
                }
                p.closeSubpath()
            }
        }
    }

    private static func softPath(_ points:[[Double]])->Path {
        guard points.count>2 else { return Path() }
        func mid(_ a:[Double],_ b:[Double])->CGPoint { CGPoint(x:(a[0]+b[0])/2,y:(a[1]+b[1])/2) }
        return Path { p in
            p.move(to:mid(points.last!,points[0]))
            for i in points.indices {
                p.addQuadCurve(to:mid(points[i],points[(i+1)%points.count]),control:CGPoint(x:points[i][0],y:points[i][1]))
            }
            p.closeSubpath()
        }
    }
    private func color(_ rgb:[Double])->Color { Color(red:rgb[0]/255,green:rgb[1]/255,blue:rgb[2]/255) }
    func drawPrize(time:Double,enhanced:Bool,context:inout GraphicsContext) {
        for (i,layer) in scene.prize.layers.enumerated() {
            let shading:GraphicsContext.Shading
            if let g=layer.gradient {
                shading = .linearGradient(Gradient(colors:g.colors.map(color)),startPoint:CGPoint(x:g.start[0],y:g.start[1]),endPoint:CGPoint(x:g.end[0],y:g.end[1]))
            } else { shading = .color(color(layer.fillRGB ?? [255,255,255])) }
            context.fill(prizePaths[i],with:shading,style:FillStyle(eoFill:true))
        }
        if enhanced,let center=BuzzStreaksTextMotionMath.sweep(time,start:0.45) {
            var c=context
            c.clip(to:prizePaths[3],style:FillStyle(eoFill:true));c.blendMode = .screen
            let colors=Gradient(stops:[.init(color:.clear,location:0),.init(color:.clear,location:0.2),
                .init(color:.white.opacity(0.42),location:0.5),.init(color:.clear,location:0.8),.init(color:.clear,location:1)])
            c.fill(Path(CGRect(x:0,y:0,width:190,height:99)),with:.linearGradient(colors,
                startPoint:CGPoint(x:(center-0.5)*190,y:49.5),endPoint:CGPoint(x:(center+0.5)*190,y:49.5)))
        }
    }
    func drawBonus(time:Double,context:inout GraphicsContext) {
        let index=min(scene.bonus.frames.count-1,max(0,Int(time*60 + 1e-7)))
        guard scene.bonus.frames[index].visible else { return }
        let colors=scene.bonus.colors, coin=scene.bonus.coin
        context.fill(Path(ellipseIn:CGRect(x:67,y:90,width:76,height:86)),with:.color(color(colors["halo"]!).opacity(0.18)))
        context.fill(flamePaths[index],with:.linearGradient(Gradient(colors:[color(colors["flameTop"]!),color(colors["flameBottom"]!)]),
            startPoint:CGPoint(x:110,y:75),endPoint:CGPoint(x:125,y:158)))
        context.fill(whitePaths[index],with:.color(color(colors["white"]!)))
        var c=context
        c.translateBy(x:coin[0],y:coin[1]);c.rotate(by:.degrees(coin[4]))
        let oval=Path(ellipseIn:CGRect(x:-coin[2],y:-coin[3],width:2*coin[2],height:2*coin[3]))
        c.fill(oval,with:.linearGradient(Gradient(colors:[color(colors["coinTop"]!),color(colors["coinBottom"]!)]),
            startPoint:CGPoint(x:0,y:-coin[3]),endPoint:CGPoint(x:0,y:coin[3])))
        c.stroke(oval,with:.color(color(colors["coinTop"]!).opacity(0.65)),lineWidth:0.6)
        context.fill(glyphPath,with:.color(color(colors["glyph"]!)),style:FillStyle(eoFill:true))
    }
}
