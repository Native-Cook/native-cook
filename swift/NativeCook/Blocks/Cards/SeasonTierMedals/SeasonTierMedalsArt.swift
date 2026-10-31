import SwiftUI

struct SeasonTierMedalsArt {
    typealias M = SeasonTierMedalsMotion
    typealias H = SeasonTierMedalsTheme
    let art: SeasonTierMedalsResources
    func background(_ context: GraphicsContext, _ frame: M.Frame) {
        let w = art.size.width, h = art.size.height, my = art.top + 248
        let rect = CGRect(origin: .zero, size: art.size)
        let i = min(1, max(0, Int(floor(frame.rail.p))))
        let k = M.clamp(frame.rail.p - Double(i))
        let page = (0..<3).map { Float((M.mix(H.pages[i][$0], H.pages[i + 1][$0], k) * 255).rounded() / 255) }
        context.fill(art.boundsPath, with: .shader(ShaderLibrary.seasonTierMedalsPage(
            .float2(Float(w / 2), Float(my - 40)), .float(Float(h * 0.75)),
            .float3(page[0], page[1], page[2]))))
        var halo = context.resolve(art.haloImage)
        halo.shading = .color(H.blended(H.halos, frame.rail.p, 1))
        var layer = context
        layer.opacity = 0.2 + 0.12 * frame.flash
        layer.draw(halo, in: rect)
        var core = context.resolve(art.coreImage)
        core.shading = .color(.white)
        layer = context; layer.opacity = 0.1 + 0.1 * frame.flash
        layer.draw(core, in: rect)
    }
    func foreground(_ context: GraphicsContext, _ f: M.Frame) {
        let w = art.size.width, k = art.top + 62, p = f.rail.p, b = art.hairline
        context.draw(art.baseImage, in: CGRect(origin: .zero, size: art.size))
        let clamped = max(0, min(2, p)), index = min(1, Int(floor(max(0, min(2, p)))))
        let fill = M.mix(H.fills[index], H.fills[index + 1], clamped - Double(index))
        let bar = CGRect(x: 48 + b, y: k + 361 + b, width: (w - 234 - 2 * b) * fill, height: 10)
        var fillLayer = context
        fillLayer.clip(to: Path(roundedRect: bar, cornerRadius: 5))
        for i in 0..<3 {
            fillLayer.fill(Path(bar), with: .color(H.color(H.bars[i], M.clamp(1 - abs(p - Double(i))))))
        }
        for i in 0..<3 {
            let weight = f.layer(i)
            var layer = context; layer.opacity = pow(weight, 1.3)
            let dx = (Double(i) - p) * 28
            layer.draw(art.labelImages[i][0], in: CGRect(x: dx,y: k,width: w,height: 22))
            var name = layer
            let scale = M.mix(0.92, 1, weight)
            name.translateBy(x: w / 2 + (Double(i) - p) * 70, y: k + 50)
            name.scaleBy(x: scale, y: scale)
            name.draw(art.labelImages[i][1], in: CGRect(x: -w/2,y: -28,width: w,height: 56))
            layer.draw(art.labelImages[i][2], in: CGRect(x: 48 + b + dx,y: k + 314 + b,width: w - 96 - 2 * b,height: 26))
            layer.draw(art.labelImages[i][3], in: CGRect(x: w - 176 - b + dx,y: k + 354 + b,width: 100,height: 24))
            layer.draw(art.labelImages[i][4], in: CGRect(x: 48 + b + dx,y: k + 390 + b,width: w - 96 - 2 * b,height: 20))
        }
        let widths = (0..<3).map { M.mix(22, 30, M.clamp(1 - abs(p - Double($0)))) }
        var x = (w - widths.reduce(0, +) - 12) / 2
        for i in 0..<3 {
            let rect = CGRect(x: x, y: k + 446, width: widths[i], height: 4)
            context.fill(Path(roundedRect: rect, cornerRadius: 2), with: .color(.white.opacity(M.mix(0.28, 1, M.clamp(1 - abs(p - Double(i)))))))
            x += widths[i] + 6
        }
    }
}
