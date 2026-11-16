import SwiftUI

struct CardVacuumSealFrame: View {
    let frame: CardVacuumSealDriver.Frame
    let layout: CardVacuumSealLayout
    let resources: CardVacuumSealResources
    let displayScale: Double
    let typeScale: Double
    let reduced: Bool

    var body: some View {
        let s = layout.scale
        ZStack(alignment: .topLeading) {
            Canvas(opaque: false,colorMode: .nonLinear) { context,_ in
                var c = context
                c.scaleBy(x: s,y: s); c.translateBy(x: 0,y: layout.dy/s)
                CardVacuumSealArt.tiles(c,resources: resources,look: frame.look,label: frame.label,reduced: reduced,typeScale: typeScale)
            }
            .frame(width: layout.width,height: layout.contentHeight)
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.cardVacuumSeal(.floatArray(uniforms),
                    .image(resources.atlas(pixelScale: s*displayScale))))
                .frame(width: 434*s,height: 403*s,alignment: .topLeading)
                .offset(x: -16*s,y: layout.y(128)-100*s)
        }
        .frame(width: layout.width,height: layout.contentHeight,alignment: .topLeading)
        .allowsHitTesting(false)
    }
    private var uniforms: [Float] {
        let f = frame, l = f.look, s = layout.scale
        let az = 315*Double.pi/180+CardVacuumSealMotion.lightDrift(f.ambient)
        var u: [Double] = [1,s,217,211.5,177,111.5,16,6,14,l.p,l.puff,l.settle,l.glint,l.sweep,l.sweepK,az]
        u += [f.dimple.x+40,f.dimple.y+100,f.dimpleK,f.finger.x+40,f.finger.y+100,f.fingerK]
        u += [f.stretch,CardVacuumSealMotion.wrap(f.ambient)/6,f.fade,ceil(354*s*displayScale)/354,f.seed,f.amp,f.extra]
        u += [201.0/255,184.0/255,1]
        return u.map(Float.init)
    }
}
