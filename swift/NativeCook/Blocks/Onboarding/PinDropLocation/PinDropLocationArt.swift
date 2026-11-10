import UIKit

final class PinDropLocationInkView: UIView {
  private var l = PinDropLocationLayout(402,874,62,34)
  private weak var driver: PinDropLocationDriver?
  private var scale = 3.0
  private var staticLabels: [(UIImage,CGRect)] = []
  private var buttonLabels: [UIImage] = []
  private var pills: [UIImage] = []
  private var check: CGPath?
  private var skipPath: CGPath?
  private var allowPath: CGPath?
  override init(frame: CGRect) { super.init(frame:frame); isOpaque=false; backgroundColor = .clear; contentMode = .redraw }
  required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
  func configure(_ layout: PinDropLocationLayout,_ driver: PinDropLocationDriver,_ scale: Double) {
    if l != layout || self.scale != scale || staticLabels.isEmpty { l=layout; self.scale=scale; prepare() }
    self.driver=driver; setNeedsDisplay()
  }
  private func prepare() {
    let r=PinDropLocationResources.shared
    skipPath=CGPath(roundedRect:l.skip,cornerWidth:21,cornerHeight:21,transform:nil)
    allowPath=CGPath(roundedRect:l.allow,cornerWidth:14,cornerHeight:14,transform:nil)
    let title=CGRect(x:0,y:l.y(482)-11,width:l.w,height:22)
    let body=CGRect(x:(l.w-380*l.sx)/2,y:l.y(539)-14,width:380*l.sx,height:84)
    staticLabels=[(r.text("LOCATION",15,.bold,0xECE9F7,title.width,22,scale,22,2.2),title),
                  (r.text(PinDropLocationResources.body,17,.regular,0xDEDBEA,body.width,84,scale,28),body)]
    buttonLabels=[r.text("Skip",16,.semibold,0xB9B3E0,69,42,scale)]
    for text in ["Allow","Location on"] {
      let face=r.font(17,.semibold), w=ceil((text as NSString).size(withAttributes:[.font:face]).width*scale)/scale
      buttonLabels.append(r.text(text,17,.semibold,0xABA6D6,w,24,scale))
    }
    pills=(0..<3).map { r.pill($0,scale) }
    let p=CGMutablePath(); p.move(to:CGPoint(x:7.4,y:12.4)); p.addLine(to:CGPoint(x:10.6,y:15.4)); p.addLine(to:CGPoint(x:16.8,y:8.8)); check=p
  }
  override func draw(_ rect: CGRect) {
    guard let d=driver,let c=UIGraphicsGetCurrentContext() else { return }
    let s=d.state
    func bitmap(_ image:UIImage,_ rect:CGRect) { image.draw(in:rect) }
    for i in 0..<3 {
      let k=max(0,min(1,s.places*1.6-Double(i)*0.25)); if k<=0 { continue }
      let p=PinDropLocationResources.positions[i], side=PinDropLocationResources.sides[i], image=pills[i]
      let x=l.cx+p.x*l.radius, y=l.cy+p.y*l.radius
      let left=((side<0 ? x-190 : x+10)*scale).rounded()/scale
      let top=((y-13)*scale).rounded()/scale
      c.saveGState()
      c.translateBy(x:left+90+side*(1-k)*(-8),y:top+13)
      c.scaleBy(x:0.9+0.1*k,y:0.9+0.1*k)
      image.draw(in:CGRect(x:side<0 ? 90-image.size.width : -90,y:-13,width:image.size.width,height:26),blendMode:.normal,alpha:k); c.restoreGState()
    }
    func press(_ i:Int,_ box:CGRect,_ draw:()->Void) {
      c.saveGState(); let amount=d.buttonAmount(i)
      if d.reduced { c.setAlpha(1-0.28*amount) }
      else { let sc=1-(i==0 ? 0.04 : 0.02)*amount; c.translateBy(x:box.midX,y:box.midY); c.scaleBy(x:sc,y:sc); c.translateBy(x:-box.midX,y:-box.midY) }
      draw(); c.restoreGState()
    }
    press(0,l.skip) {
      c.setFillColor(PinDropLocationResources.color(0x1E1B33).cgColor); if let skipPath { c.addPath(skipPath); c.fillPath() }
      bitmap(buttonLabels[0],l.skip)
    }
    for (image,box) in staticLabels { bitmap(image,box) }
    press(1,l.allow) {
      c.setFillColor(PinDropLocationResources.color(0x16132A).cgColor); if let allowPath { c.addPath(allowPath); c.fillPath() }
      let image=buttonLabels[d.allowed ? 2 : 1], total=24+14+image.size.width
      let x=((l.allow.midX-total/2)*scale).rounded()/scale, y=((l.allow.midY-12)*scale).rounded()/scale
      c.saveGState(); c.translateBy(x:x,y:y)
      c.setStrokeColor(PinDropLocationResources.color(0xFF4A78).cgColor); c.setLineWidth(2)
      c.strokeEllipse(in:CGRect(x:2,y:2,width:20,height:20))
      c.setLineCap(.round); c.setLineJoin(.round)
      if let check { c.addPath(check); c.strokePath() }; c.restoreGState()
      let textX=floor((l.allow.midX-total/2+38)*scale)/scale
      bitmap(image,CGRect(x:textX,y:y,width:image.size.width,height:24))
    }
    if s.finger>0 {
      let radius=22*(1-0.12*s.press), box=CGRect(x:d.fingerX-radius,y:d.fingerY-radius,width:2*radius,height:2*radius)
      c.setFillColor(UIColor.white.withAlphaComponent(0.08*s.finger).cgColor); c.fillEllipse(in:box)
      c.setStrokeColor(UIColor.white.withAlphaComponent(0.55*s.finger).cgColor); c.setLineWidth(1.5); c.strokeEllipse(in:box)
    }
  }
}
