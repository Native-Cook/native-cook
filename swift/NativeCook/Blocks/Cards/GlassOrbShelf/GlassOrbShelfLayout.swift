import Foundation

struct GlassOrbShelfLayout {
    let w: Double
    let headerTop: Double
    let orbX: Double
    let orbY: Double
    let orbR: Double
    let cardLeft: Double
    let cardTop: Double
    let cardW: Double
    let cardH = 196.0
    let radius = 26.0
    let step: Double
    let dotsTop: Double
    init(width: Double, top: Double) {
        w=width; headerTop=top+12; orbR=min(172,width*0.42)
        orbX=width/2; orbY=headerTop+44+24+orbR
        cardW=min(300,width-102); cardLeft=(width-cardW)/2
        cardTop=orbY+orbR+44; step=cardW+14; dotsTop=cardTop+196+18
    }
}
