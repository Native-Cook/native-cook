import SwiftUI
import UIKit

// A native image layer preserves RCTImageView's transformed-image filtering. Canvas
// filtering subtly softens the street's fine lines while the photo shrinks and rotates.
struct GlassApertureCameraPhotoView: UIViewRepresentable {
    var layout: GlassApertureCameraTheme.Layout
    var frame: GlassApertureCameraMotion.Frame
    var reduced: Bool
    func makeUIView(context: Context) -> GlassApertureCameraPhotoSurface { GlassApertureCameraPhotoSurface() }
    func updateUIView(_ view: GlassApertureCameraPhotoSurface, context: Context) {
        view.update(layout,frame,reduced)
    }
}
final class GlassApertureCameraPhotoSurface: UIView {
    private let card = UIView()
    private let photo = UIImageView(image:GlassApertureCameraResources.shared.scene)
    override init(frame: CGRect) {
        super.init(frame:frame)
        isUserInteractionEnabled = false; backgroundColor = .clear; isOpaque = false
        card.clipsToBounds = true; card.layer.cornerRadius = 18
        card.layer.borderWidth = 7; card.layer.borderColor = UIColor.white.withAlphaComponent(0.92).cgColor
        card.layer.allowsEdgeAntialiasing = true
        photo.clipsToBounds = true
        photo.contentMode = .scaleAspectFill
        // RCTImageComponentView selects trilinear filtering for both directions.
        photo.layer.minificationFilter = .trilinear
        photo.layer.magnificationFilter = .trilinear
        addSubview(card); card.addSubview(photo)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func update(_ l:GlassApertureCameraTheme.Layout,_ f:GlassApertureCameraMotion.Frame,_ reduced:Bool) {
        let s = f.shot
        let p = GlassApertureCameraMotion.shotAt(s)
        let st = 1+0.14*abs(p.stretch)
        let ty = reduced ? 0 : (l.viewH+GlassApertureCameraMotion.Shot.k.to*l.viewH/2+6-l.viewH/2)*p.y
        let scaleX = reduced ? 1 : p.k/sqrt(st), scaleY = reduced ? 1 : p.k*st
        CATransaction.begin(); CATransaction.setDisableActions(true)
        card.bounds = CGRect(x:0,y:0,width:l.w,height:l.viewH)
        card.center = CGPoint(x:l.w/2,y:l.viewH/2)
        // Yoga places the image inside the print's 7 pt border without shrinking it.
        photo.frame = CGRect(x:7,y:7,width:l.w,height:l.photoH)
        card.transform = CGAffineTransform(translationX:0,y:ty).rotated(by:reduced ? 0 : p.rot*Double.pi/180).scaledBy(x:scaleX,y:scaleY)
        card.alpha = s < 0 ? 0 : reduced ? (s < GlassApertureCameraMotion.Shot.go ? 1 : 1-GlassApertureCameraMotion.clamp01((s-GlassApertureCameraMotion.Shot.go)/0.25)) : p.alpha
        CATransaction.commit()
    }
}
