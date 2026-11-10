import SwiftUI
import UIKit

// RN's RCTImageComponentView uses UIImageView with trilinear minification and magnification.
// Retaining the same native image layers also avoids resampling the artwork in Canvas each frame.
struct SerumDropperRoutineProducts: UIViewRepresentable {
    let layout: SerumDropperRoutineLayout
    let lift: Double
    let squeeze: Double
    func makeUIView(context: Context) -> SerumDropperRoutineProductsView { SerumDropperRoutineProductsView() }
    func updateUIView(_ view: SerumDropperRoutineProductsView,context: Context) { view.update(layout,lift,squeeze) }
}
final class SerumDropperRoutineProductsView: UIView {
    private let group = UIView(), bulb = UIView(), stem = UIView()
    private let bulbImage = UIImageView(), stemImage = UIImageView(), bottle = UIImageView()
    private let tube = UIImageView(), jar = UIImageView(), white = UIImageView()
    private var cachedLayout: SerumDropperRoutineLayout?
    override init(frame: CGRect) {
        super.init(frame:frame)
        isUserInteractionEnabled = false; isOpaque = false; backgroundColor = .clear
        let resources = SerumDropperRoutineResources.shared
        for (name,image) in [("dropper",bulbImage),("dropper",stemImage),("bottle",bottle),("tube",tube),("jar",jar),("white",white)] {
            image.contentMode = .scaleAspectFill
            image.clipsToBounds = true
            image.layer.minificationFilter = .trilinear
            image.layer.magnificationFilter = .trilinear
            image.image = resources.products[name]
        }
        addSubview(tube); addSubview(jar); addSubview(white)
        addSubview(group); group.addSubview(bulb); group.addSubview(stem)
        bulb.clipsToBounds = true; stem.clipsToBounds = true
        bulb.addSubview(bulbImage); stem.addSubview(stemImage); addSubview(bottle)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func update(_ l: SerumDropperRoutineLayout,_ lift: Double,_ squeeze: Double) {
        let scale = UIScreen.main.scale
        func snap(_ value: Double) -> Double { (value*scale).rounded()/scale }
        func box(_ x: Double,_ y: Double,_ w: Double,_ h: Double) -> CGRect {
            CGRect(x:snap(x),y:snap(y),width:snap(x+w)-snap(x),height:snap(y+h)-snap(y))
        }
        if cachedLayout != l {
            cachedLayout = l
            let left = l.bx-l.dw/2
            let base = box(left,l.dtop,l.dw,l.dh)
            let bulbH = snap(l.dtop+l.dh*0.3)-snap(l.dtop)
            group.transform = .identity
            group.frame = base
            bulb.transform = .identity
            bulb.frame = CGRect(x:0,y:0,width:base.width,height:bulbH)
            bulbImage.frame = CGRect(x:0,y:0,width:base.width,height:base.height)
            stem.frame = CGRect(x:0,y:bulbH,width:base.width,height:base.height-bulbH)
            stemImage.frame = CGRect(x:0,y:-bulbH,width:base.width,height:base.height)
            bottle.frame = box(l.bx-l.bw/2,l.btop,l.bw,l.bh)
            tube.frame = box(243*l.sx,l.y(483)-75*l.sx,100*l.sx,150*l.sx)
            jar.frame = box(47*l.sx,l.y(712)-45.75*l.sx,122*l.sx,91.5*l.sx)
            white.frame = box(258*l.sx,l.y(782)-70*l.sx,70*l.sx,140*l.sx)
            let resources = SerumDropperRoutineResources.shared
            bulbImage.image = resources.image("dropper",l.dw,l.dh)
            stemImage.image = bulbImage.image
            bottle.image = resources.image("bottle",l.bw,l.bh)
            tube.image = resources.image("tube",100*l.sx,150*l.sx)
            jar.image = resources.image("jar",122*l.sx,91.5*l.sx)
            white.image = resources.image("white",70*l.sx,140*l.sx)
        }
        let tip = l.tip(lift)
        group.transform = CGAffineTransform(translationX:tip.x-l.bx,y:tip.y-l.seatTipY)
        bulb.transform = CGAffineTransform(translationX:0,y:l.dh*0.3*0.2*squeeze/2)
            .scaledBy(x:1+0.1*squeeze,y:1-0.2*squeeze)
    }
}
