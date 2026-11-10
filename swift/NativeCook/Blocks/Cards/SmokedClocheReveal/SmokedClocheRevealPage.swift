import UIKit

final class SmokedClocheRevealPage: UIView {
  typealias R=SmokedClocheRevealResources
  let backdrop=UIImageView(),bowl=UIImageView(),leafA=UIImageView(),leafB=UIImageView()
  let metal=SmokedClocheRevealMetalView(),touch=SmokedClocheRevealTouch()
  let back=SmokedClocheRevealButton(type:.custom),more=SmokedClocheRevealButton(type:.custom)
  let heart=SmokedClocheRevealButton(type:.custom),cta=SmokedClocheRevealButton(type:.custom)
  let name=UILabel(),time=UILabel(),bodyText=UILabel(),total=UILabel(),price=UILabel(),ctaText=UILabel(),badgeText=UILabel()
  let plus=UIView(),badge=UIView()
  let arrow=CAShapeLayer(),dots=CAShapeLayer(),watch=CAShapeLayer(),heartLine=CAShapeLayer(),plusLine=CAShapeLayer(),finger=CAShapeLayer()
  var clock: SmokedClocheRevealClock?
  var sample=SmokedClocheRevealMotion.Frame()
  var pinned=false,reduced=false
  private var layout: SmokedClocheRevealLayout?
  override init(frame: CGRect) {
    super.init(frame:frame)
    clipsToBounds=true;backgroundColor=R.color(0xd9d4ce)
    [backdrop,back,more,leafA,leafB,bowl,metal,touch].forEach(addSubview)
    bowl.accessibilityIgnoresInvertColors=true
    bowl.image=R.shared.bowl;leafA.image=R.shared.leaves[0];leafB.image=R.shared.leaves[1]
    for v in [backdrop,bowl,leafA,leafB] { v.isUserInteractionEnabled=false;v.contentMode = .scaleToFill }
    func label(_ v: UILabel, _ text: String, _ weight: String, _ size: Double, _ hex: Int, _ line: Double? = nil) {
      let style=NSMutableParagraphStyle()
      if let line { style.minimumLineHeight=line;style.maximumLineHeight=line }
      let font=R.shared.font(weight,size)
      v.attributedText=NSAttributedString(string:text,attributes:[.font:font,.foregroundColor:R.color(hex),.paragraphStyle:style])
      v.numberOfLines=0;v.isUserInteractionEnabled=false;addSubview(v)
    }
    label(name,"Eggs Curry","Bold",28,0x1d1b19,36);name.accessibilityTraits = .header
    label(time,"25 Mins","Medium",16.5,0x1d1b19)
    label(bodyText,"Eggs Curry with tomato and cucumbers, our chefs’ healthy, low-fat dish, smoked over applewood before it reaches you.","Regular",14.2,0x4a4541,22.5)
    label(total,"Total Price","Regular",13.2,0x5e5853)
    label(price,"$ 15.00","Bold",20.5,0x1d1b19)
    addSubview(heart);addSubview(cta)
    label(ctaText,"Add to Cart","SemiBold",15,0xffffff);ctaText.removeFromSuperview();cta.addSubview(ctaText)
    cta.backgroundColor=R.color(0x050505);cta.layer.cornerRadius=26;cta.layer.cornerCurve = .circular;cta.layer.maskedCorners=[.layerMinXMinYCorner,.layerMinXMaxYCorner]
    cta.pressScale=0.97;cta.addTarget(self,action:#selector(addToCart),for:.touchUpInside)
    cta.addSubview(plus);plus.backgroundColor = .white;plus.layer.cornerRadius=16;plus.isUserInteractionEnabled=false
    plus.addSubview(badge);badge.backgroundColor=R.color(0xf26b3a);badge.layer.cornerRadius=9
    label(badgeText,"","SemiBold",11,0xffffff);badgeText.removeFromSuperview();badge.addSubview(badgeText)
    badge.isHidden=true
    heart.backgroundColor=R.color(0xe3dcd5);heart.layer.cornerRadius=23
    back.accessibilityLabel="Back";more.accessibilityLabel="More";heart.accessibilityLabel="Save"
    touch.isAccessibilityElement=true;touch.accessibilityTraits = .button
    touch.event={ [weak self] phase,p,dy,v in guard let self,!self.pinned else { return };self.clock?.touch(phase,p,dy,v) }
    touch.activate={ [weak self] in guard let self,!self.pinned else { return };self.clock?.accessibilityToggle() }
    func stroke(_ layer: CAShapeLayer, _ parent: CALayer, _ width: Double, _ hex: Int = 0x1d1b19) {
      layer.strokeColor=R.color(hex).cgColor;layer.fillColor=nil;layer.lineWidth=width;layer.lineCap = .round;layer.lineJoin = .round;parent.addSublayer(layer)
    }
    let a=UIBezierPath();a.move(to:CGPoint(x:10,y:2));a.addLine(to:CGPoint(x:2,y:11));a.addLine(to:CGPoint(x:10,y:20));arrow.path=a.cgPath;stroke(arrow,back.layer,2,0x3a3632)
    let d=UIBezierPath();for y in [4.0,11,18] { d.append(UIBezierPath(ovalIn:CGRect(x:0.6,y:y-2.4,width:4.8,height:4.8))) };dots.path=d.cgPath;dots.fillColor=R.color(0x3a3632).cgColor;more.layer.addSublayer(dots)
    let w=UIBezierPath(ovalIn:CGRect(x:1.4,y:1.4,width:15.2,height:15.2));w.move(to:CGPoint(x:9,y:4.8));w.addLine(to:CGPoint(x:9,y:9));w.addLine(to:CGPoint(x:11.8,y:10.6));watch.path=w.cgPath;stroke(watch,layer,1.5)
    // The two SVG arc segments have radius 4.8 and connect exactly these endpoints.
    let h=UIBezierPath();h.move(to:CGPoint(x:11,y:18.2));h.addCurve(to:CGPoint(x:1.6,y:6.6),controlPoint1:CGPoint(x:4,y:13.4),controlPoint2:CGPoint(x:1.6,y:10.2))
    h.addArc(withCenter:CGPoint(x:6.3,y:5.6),radius:sqrt(23.09),startAngle:atan2(1,-4.7),endAngle:atan2(-1,4.7),clockwise:true)
    h.addArc(withCenter:CGPoint(x:15.7,y:5.6),radius:sqrt(23.09),startAngle:atan2(-1,-4.7),endAngle:atan2(1,4.7),clockwise:true)
    h.addCurve(to:CGPoint(x:11,y:18.2),controlPoint1:CGPoint(x:20.4,y:10.2),controlPoint2:CGPoint(x:18,y:13.4));h.close();heartLine.path=h.cgPath;stroke(heartLine,heart.layer,1.7)
    let p=UIBezierPath();p.move(to:CGPoint(x:7,y:1.5));p.addLine(to:CGPoint(x:7,y:12.5));p.move(to:CGPoint(x:1.5,y:7));p.addLine(to:CGPoint(x:12.5,y:7));plusLine.path=p.cgPath;stroke(plusLine,plus.layer,2,0x050505)
    finger.path=UIBezierPath(ovalIn:CGRect(x:-22,y:-22,width:44,height:44)).cgPath
    finger.fillColor=R.color(0x141414,0.08).cgColor;finger.strokeColor=UIColor.white.withAlphaComponent(0.8).cgColor;finger.lineWidth=1.5;layer.addSublayer(finger)
  }
  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
  @objc private func addToCart() { if !pinned { clock?.toCart() } }
  override func layoutSubviews() { super.layoutSubviews();update() }
  override func safeAreaInsetsDidChange() { super.safeAreaInsetsDidChange();update() }
  private func backgroundImage(_ l: SmokedClocheRevealLayout) -> UIImage {
    let format=UIGraphicsImageRendererFormat();format.scale=window?.screen.scale ?? UIScreen.main.scale
    return UIGraphicsImageRenderer(size:bounds.size,format:format).image { r in
      let c=r.cgContext,s=CGColorSpace(name:CGColorSpace.sRGB)!
      let colors=[R.color(0xd9d4ce).cgColor,R.color(0xddd6ce).cgColor,R.color(0xedd7bf).cgColor] as CFArray
      c.drawLinearGradient(CGGradient(colorsSpace:s,colors:colors,locations:[0,0.5,1])!,start:.zero,end:CGPoint(x:l.w*0.3,y:l.h),options:[.drawsBeforeStartLocation,.drawsAfterEndLocation])
      c.drawRadialGradient(CGGradient(colorsSpace:s,colors:[R.color(0xf3c9a0,0.55).cgColor,R.color(0xf3c9a0,0).cgColor] as CFArray,locations:[0,1])!,startCenter:CGPoint(x:-20,y:l.h*0.95),startRadius:0,endCenter:CGPoint(x:-20,y:l.h*0.95),endRadius:l.w*0.7,options:[])
    }
  }
  func update() {
    guard bounds.width>0,bounds.height>0 else { return }
    let i=window?.safeAreaInsets ?? safeAreaInsets
    let l=SmokedClocheRevealLayout(w:bounds.width,h:bounds.height,top:i.top,bottom:i.bottom)
    clock?.lastLayout=l
    CATransaction.begin();CATransaction.setDisableActions(true)
    if layout != l {
      layout=l;backdrop.frame=bounds;backdrop.image=backgroundImage(l);metal.frame=bounds
      back.frame=CGRect(x:37*l.sx-22,y:l.y(63)-22,width:44,height:44);arrow.frame=CGRect(x:16,y:11,width:12,height:22)
      more.frame=CGRect(x:364*l.sx-22,y:l.y(63)-22,width:44,height:44);dots.frame=CGRect(x:19,y:11,width:6,height:22)
      for (v,x,y,s,rot) in [(leafA,18.0,560.0,64.0,-20.0),(leafB,380,425,58,35)] {
        v.bounds=CGRect(x:0,y:0,width:s,height:s);v.center=CGPoint(x:x*l.sx,y:l.y(y));v.transform=CGAffineTransform(rotationAngle:rot*Double.pi/180)
      }
      let scale=window?.screen.scale ?? UIScreen.main.scale
      let imageWidth=((l.cx+l.half)*scale).rounded()/scale-((l.cx-l.half)*scale).rounded()/scale
      let imageHeight=((l.cy+l.half)*scale).rounded()/scale-((l.cy-l.half)*scale).rounded()/scale
      bowl.bounds=CGRect(x:0,y:0,width:imageWidth,height:imageHeight)
      touch.frame=CGRect(x:l.cx-l.radius,y:l.cy-l.radius,width:l.radius*2,height:l.radius*2)
      func place(_ label: UILabel, _ x: Double, _ y: Double, _ width: Double? = nil) {
        let size=label.sizeThatFits(CGSize(width:width ?? 1000,height:1000))
        label.frame=CGRect(x:x,y:y,width:width ?? ceil(size.width),height:ceil(size.height))
      }
      place(name,33*l.sx,l.y(636)-20)
      let ts=time.sizeThatFits(CGSize(width:200,height:50)),rowW=18+8+ceil(ts.width),rowH=max(18,ceil(ts.height))
      place(time,360*l.sx-ceil(ts.width)+2.0/3,l.y(637)-12+(rowH-ts.height)/2)
      watch.frame=CGRect(x:360*l.sx-rowW+2.0/3,y:l.y(637)-12+(rowH-18)/2,width:18,height:18)
      // UILabel's fixed-line-height block has an extra 7/3 pt leading inset
      // compared with RN's attributed Text; preserve RN's first baseline.
      place(bodyText,33*l.sx,l.y(682)-11-7.0/3,330*l.sx);place(total,33*l.sx,l.y(780)-9-1.0/3);place(price,33*l.sx,l.y(812)-14)
      heart.frame=CGRect(x:200*l.sx-23,y:l.y(808)-23,width:46,height:46);heartLine.frame=CGRect(x:12,y:13,width:22,height:20)
      cta.frame=CGRect(x:236*l.sx,y:l.y(772),width:190*l.sx,height:70*l.k)
      let cs=ctaText.sizeThatFits(CGSize(width:300,height:60))
      ctaText.frame=CGRect(x:22,y:(cta.bounds.height-cs.height)/2,width:ceil(cs.width),height:cs.height)
      let plusX=((22+cs.width+12)*scale).rounded()/scale
      plus.frame=CGRect(x:plusX,y:(cta.bounds.height-32)/2,width:32,height:32)
      plusLine.frame=CGRect(x:9,y:9,width:14,height:14)
    }
    let c=sample.cart
    bowl.center=CGPoint(x:l.cx+(l.ctaX-l.cx)*c,y:l.cy+(l.ctaY-l.cy)*c-sin(Double.pi*c)*120)
    bowl.transform=CGAffineTransform(rotationAngle:c*160*Double.pi/180).scaledBy(x:1-0.88*c,y:1-0.88*c)
    bowl.alpha=1-max(0,(c-0.85)/0.15)
    let p=sample.demoPoint ?? SmokedClocheRevealMotion.fingerPoint(sample,l)
    finger.position=p;finger.setAffineTransform(CGAffineTransform(scaleX:1-0.12*sample.press,y:1-0.12*sample.press));finger.lineWidth=1.5/(1-0.12*sample.press);finger.opacity=Float(sample.finger)
    touch.accessibilityLabel=sample.lift>0.98 ? "Eggs Curry. Tap to cover it again" : "Cloche. Draw it up to uncover the dish"
    touch.isUserInteractionEnabled = !pinned
    for b in [back,more,heart,cta] { b.reduced=reduced;b.isUserInteractionEnabled = !pinned }
    let count=clock?.count ?? 0
    cta.accessibilityLabel=count>0 ? "Add to cart, \(count) in the cart" : "Add to cart"
    if count>0 {
      badge.isHidden=false;badgeText.text="\(count)";badgeText.font=R.shared.font("SemiBold",11);badgeText.textColor = .white
      let s=badgeText.sizeThatFits(CGSize(width:300,height:18));let w=max(18,s.width+8)
      badge.frame=CGRect(x:32+6-w,y:-6,width:w,height:18);badgeText.frame=CGRect(x:(w-s.width)/2,y:(18-s.height)/2,width:s.width,height:s.height)
    } else { badge.isHidden=true }
    CATransaction.commit()
    metal.render(l,sample)
  }
}
