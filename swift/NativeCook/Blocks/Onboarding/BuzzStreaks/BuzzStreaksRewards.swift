import SwiftUI
import UIKit

/// Measured growth and flights; the first pair duplicates into four separate coins.
final class BuzzStreaksRewards {
    private struct Sprite: Decodable { let size: [Double]; let rect: [Double] }
    private struct Coin: Decodable { let id:String; let image: String; let frames: [[Double]] }
    private struct Stroke: Decodable { let points:[[Double]]; let opacity:Double }
    private struct Badge: Decodable {
        let id:String; let text:String; let size:[Double]; let frames:[[Double]]
        let appearance:[[Double]]; let strokes:[[Stroke]]
        let fillRGB:[Double]; let fillOpacity:Double; let fontSize:Double
    }
    private struct StrokeParameters:Decodable { let rgb:[Double]; let width:Double; let glowRadius:Double; let glowOpacity:Double }
    private struct WaveParameters:Decodable { let width:Double; let alphaGain:Double; let blur:Double }
    private struct Wave: Decodable { let points: [[Double]]; let opacity: Double; let closed: Bool }
    private struct Glow: Decodable { let rgb: [Double]; let frames: [[Double]] }
    private struct Scene: Decodable {
        let images: [String: Sprite]
        let coins: [Coin]
        let badges: [Badge]
        let waves: [[Wave]]
        let glow: Glow
        let strokeStyle:StrokeParameters
        let waveStyle:WaveParameters
    }
    private let scene: Scene
    private let images: [String: Image]
    private let waves: [[(Path, Double)]]
    private let strokes: [[[(Path,Double)]]]

    init() {
        guard let url = Bundle.main.url(forResource: "BuzzStreaks-effects", withExtension: "json"),
              let data = try? Data(contentsOf: url), let scene = try? JSONDecoder().decode(Scene.self, from: data)
        else { preconditionFailure("BuzzStreaks requires valid bundled reward motion.") }
        self.scene = scene
        var found: [String: Image] = [:]
        for name in scene.images.keys {
            if let url = Bundle.main.url(forResource: "BuzzStreaks-\(name)", withExtension: "png"),
               let image = UIImage(contentsOfFile: url.path) {
                found[name] = Image(uiImage: image.preparingForDisplay() ?? image)
            }
        }
        precondition(found.count == scene.images.count, "BuzzStreaks requires all three generated coin images.")
        images = found
        waves = scene.waves.map { frame in frame.map { wave in
            (Path { p in
                for (index, point) in wave.points.enumerated() {
                    let position = CGPoint(x: point[0], y: point[1])
                    if index == 0 { p.move(to: position) } else { p.addLine(to: position) }
                }
                if wave.closed { p.closeSubpath() }
            }, wave.opacity)
        } }
        strokes=scene.badges.map { badge in badge.strokes.map { frame in frame.map { stroke in
            (Path { p in
                for (i,point) in stroke.points.enumerated() {
                    let position=CGPoint(x:point[0],y:point[1])
                    if i==0 {p.move(to:position)} else {p.addLine(to:position)}
                }
            },stroke.opacity)
        } } }
    }

    private func sample(_ rows: [[Double]], _ t: Double) -> [Double] {
        let frame = max(0, min(Double(rows.count - 1), t * 60)), i = Int(frame)
        let j = min(rows.count - 1, i + 1), progress = frame - Double(i)
        return zip(rows[i], rows[j]).map { $0 + ($1 - $0) * progress }
    }

    func drawBackground(time: Double, context: inout GraphicsContext) {
        let glow = sample(scene.glow.frames, time), rgb = scene.glow.rgb
        if glow[3] > 0 {
            let center = CGPoint(x: glow[0], y: glow[1])
            context.fill(Path(ellipseIn: CGRect(x: center.x-glow[2], y: center.y-glow[2], width: 2*glow[2], height: 2*glow[2])),
                         with: .radialGradient(Gradient(colors: [Color(red: rgb[0]/255, green: rgb[1]/255, blue: rgb[2]/255).opacity(glow[3]), .clear]),
                                               center: center, startRadius: 0, endRadius: glow[2]))
        }
        let index = min(waves.count - 1, max(0, Int(time * 60 + 1e-7)))
        var field=context
        field.addFilter(.blur(radius:scene.waveStyle.blur))
        field.drawLayer { layer in
            for (path,opacity) in waves[index] {
                layer.stroke(path,with:.color(.white.opacity(min(1,opacity*scene.waveStyle.alphaGain))),
                    style:StrokeStyle(lineWidth:scene.waveStyle.width,lineCap:.round,lineJoin:.round))
            }
        }
    }

    func drawForeground(time: Double, signature:BuzzStreaksSignature? = nil, context: inout GraphicsContext) {
        for coin in scene.coins {
            let source=sample(coin.frames,time)
            let value=signature?.coin(coin.id,time,source) ?? source, radius=value[2]
            guard value[3] > 0.001, radius > 0.01,
                  let image = images[coin.image], let sprite = scene.images[coin.image] else { continue }
            var c = context
            c.opacity = min(1, max(0, value[3]))
            let sx = 2*radius/sprite.rect[2], sy = 2*radius/sprite.rect[3]
            c.draw(image, in: CGRect(x: value[0]-radius-sprite.rect[0]*sx,
                                     y: value[1]-radius-sprite.rect[1]*sy,
                                     width: sprite.size[0]*sx, height: sprite.size[1]*sy))
            let gloss=signature?.coinGloss(coin.id,time) ?? 0
            if gloss>0 {
                c.clip(to:Path(ellipseIn:CGRect(x:value[0]-radius,y:value[1]-radius,width:2*radius,height:2*radius)))
                c.fill(Path(ellipseIn:CGRect(x:value[0]-radius*0.65,y:value[1]-radius*0.7,width:radius*1.3,height:radius*0.45)),with:.color(.white.opacity(gloss)))
            }
        }
        for (badgeIndex,badge) in scene.badges.enumerated() {
            let value = sample(badge.frames, time)
            let appearance=sample(badge.appearance,time)
            let gate=signature?.badgeGate(badge.id,time) ?? 1
            guard gate>0.001 else {continue}
            var c = context
            c.opacity = min(1, max(0, value[2]))*gate
            if value[2]>0.001 {
                let shape=Path(roundedRect:CGRect(x:value[0]-appearance[1]/2,y:value[1]-appearance[2]/2,
                    width:appearance[1],height:appearance[2]),cornerRadius:appearance[3])
                c.fill(shape,with:.color(Color(red:badge.fillRGB[0]/255,green:badge.fillRGB[1]/255,blue:badge.fillRGB[2]/255).opacity(badge.fillOpacity)))
            }
            let index=min(strokes[badgeIndex].count-1,max(0,Int(time*60 + 1e-7))), style=scene.strokeStyle
            let cyan=Color(red:style.rgb[0]/255,green:style.rgb[1]/255,blue:style.rgb[2]/255)
            for (path,opacity) in strokes[badgeIndex][index] {
                var glow=context
                glow.addFilter(.blur(radius:style.glowRadius))
                glow.stroke(path,with:.color(cyan.opacity(opacity*style.glowOpacity*gate)),style:StrokeStyle(lineWidth:style.width,lineCap:.round,lineJoin:.round))
                context.stroke(path,with:.color(cyan.opacity(opacity*gate)),style:StrokeStyle(lineWidth:style.width,lineCap:.round,lineJoin:.round))
            }
            if value[2]>0.001 {
                c.draw(Text(badge.text).font(.custom("Gilroy-ExtraBold",size:badge.fontSize*appearance[0])).foregroundStyle(.white),
                       at:CGPoint(x:value[0],y:value[1]))
            }
        }
    }
}
