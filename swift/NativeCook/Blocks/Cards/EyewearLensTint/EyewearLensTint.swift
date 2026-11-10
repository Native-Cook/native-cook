import SwiftUI

struct EyewearLensTint: View {
  static let loop = EyewearLensTintMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var displayScale
  @StateObject private var clock = EyewearLensTintClock()
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey: "eyewearLensTintTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        let l = EyewearLensTintTheme.Layout(full.size, safe.safeAreaInsets.top, safe.safeAreaInsets.bottom)
        let r = EyewearLensTintResources.shared
        TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
          let f = clock.sample(timeline.date, pinned, l)
          ZStack(alignment: .topLeading) {
            EyewearLensTintTheme.color(0xE9EAEE)
            Image(uiImage: r.portrait).resizable().frame(width: l.photo.width, height: l.photo.height)
              .offset(x: l.photo.minX, y: l.photo.minY).accessibilityHidden(true)
            EyewearLensTintMetalView(layout: l, frame: f, scale: displayScale, reduced: reduced && pinned == nil)
              .frame(width: l.w, height: l.h).accessibilityHidden(true)
            EyewearLensTintTouchView(layout:l,tint:f.shownTint,clock:clock)
              .frame(width:l.touch.width,height:l.touch.height).offset(y:l.touch.minY)
            Image(uiImage: r.drops(l, displayScale)).allowsHitTesting(false).accessibilityHidden(true)
            Canvas { c, _ in
              for i in 0..<4 {
                c.stroke(r.rings[i], with: .color(i == f.shownTint ? EyewearLensTintTheme.color(0x1E1E20,0.8) : .white.opacity(0.7)), lineWidth:1.2)
              }
              let radius = 13*l.sx*(1+0.25*f.carryOn)*f.carryOn
              if radius > 0 {
                var drop = c
                let k = 1 + f.stretch
                drop.translateBy(x:f.carryX,y:f.carryY)
                drop.rotate(by:.radians(f.heading)); drop.scaleBy(x:k,y:1/k)
                drop.fill(Path(ellipseIn:CGRect(x:-radius,y:-radius,width:radius*2,height:radius*2)),with:.color(EyewearLensTintTheme.color(EyewearLensTintTheme.tints[f.carryTint].swatch)))
                let rr=radius*0.28
                c.fill(Path(ellipseIn:CGRect(x:f.carryX-radius*0.35-rr,y:f.carryY-radius*0.35-rr,width:rr*2,height:rr*2)),with:.color(.white.opacity(0.75*f.carryOn)))
              }
            }.allowsHitTesting(false).accessibilityHidden(true)
            Image(uiImage:r.overlay(l,displayScale)).allowsHitTesting(false).accessibilityHidden(true)
            EyewearLensTintControls(layout:l,reduced:reduced,scale:displayScale)
            Canvas { c, _ in
              if f.finger > 0 {
                let radius=19.8
                let p=Path(ellipseIn:CGRect(x:f.carryX-radius,y:f.carryY+28-radius,width:radius*2,height:radius*2))
                c.opacity=f.finger
                c.fill(p,with:.color(EyewearLensTintTheme.color(0x141414,0.06)))
                let borderRadius=radius-0.675
                let border=Path(ellipseIn:CGRect(x:f.carryX-borderRadius,y:f.carryY+28-borderRadius,width:borderRadius*2,height:borderRadius*2))
                c.stroke(border,with:.color(EyewearLensTintTheme.color(0x141414,0.35)),lineWidth:1.35)
              }
            }.allowsHitTesting(false).accessibilityHidden(true)
          }.frame(width:l.w,height:l.h,alignment:.topLeading)
            .coordinateSpace(name:"eyewearLensTintScreen").clipped().allowsHitTesting(pinned == nil)
        }
      }.ignoresSafeArea()
    }
    .background(EyewearLensTintTheme.color(0xE9EAEE)).preferredColorScheme(.light)
    .onAppear { clock.reset(autoPlay,reduced) }
    .onChange(of: reduced) { _, value in clock.reset(autoPlay,value) }
    .onChange(of: autoPlay) { _, value in clock.reset(value,reduced) }
    .onChange(of: scenePhase) { _, _ in clock.suspend() }
  }
}
struct EyewearLensTintPressStyle: ButtonStyle {
  let reduced: Bool
  var scale = 0.96
  func makeBody(configuration: Configuration) -> some View {
    configuration.label.scaleEffect(configuration.isPressed && !reduced ? scale : 1)
      .opacity(configuration.isPressed && reduced ? 0.72 : 1)
      .animation(.timingCurve(0.23,1,0.32,1,duration:0.15),value:configuration.isPressed)
  }
}
struct EyewearLensTintControls: View {
  let layout: EyewearLensTintTheme.Layout
  let reduced: Bool
  let scale: Double
  var body: some View {
    let l=layout, r=EyewearLensTintResources.shared
    ZStack(alignment:.topLeading) {
      Button {} label: { Image(uiImage:r.control(0,l,scale)).frame(width:44,height:44).contentShape(Rectangle()) }
        .accessibilityLabel("Menu").offset(x:32*l.sx-22,y:l.y(90)-22)
      Button {} label: { Image(uiImage:r.control(1,l,scale)).frame(width:44,height:44).contentShape(Rectangle()) }
        .accessibilityLabel("Search").offset(x:372*l.sx-22,y:l.y(90)-22)
      Button {} label: { Image(uiImage:r.control(2,l,scale)).frame(width:48,height:48).contentShape(Circle()) }
        .accessibilityLabel("More about these sunglasses").offset(x:200*l.sx-24,y:l.y(562)-24)
      Button {} label: { Image(uiImage:r.control(3,l,scale)).frame(width:122*l.sx,height:37).contentShape(Rectangle()) }
        .buttonStyle(EyewearLensTintPressStyle(reduced:reduced,scale:0.97)).accessibilityLabel("Add to bag")
        .offset(x:56*l.sx,y:l.y(748))
      Color.clear.frame(width:220,height:32).accessibilityElement().accessibilityLabel("SUNGLASSES").accessibilityAddTraits(.isHeader).offset(x:56*l.sx,y:l.y(622)-16)
      Color.clear.frame(width:50,height:30).accessibilityElement().accessibilityLabel("$220").offset(x:345*l.sx-50,y:l.y(622)-14)
      Color.clear.frame(width:240,height:15).accessibilityElement().accessibilityLabel("4.5 stars, 18 reviews").offset(x:56*l.sx,y:l.y(649)-8)
      Color.clear.frame(width:290*l.sx,height:42).accessibilityElement().accessibilityLabel(EyewearLensTintTheme.body).offset(x:56*l.sx,y:l.y(681)-10)
      Color.clear.frame(width:120,height:14).accessibilityElement().accessibilityLabel("ADD TO WISHLIST").offset(x:345*l.sx-120,y:l.y(768)-8)
    }.buttonStyle(EyewearLensTintPressStyle(reduced:reduced))
      .frame(width:l.w,height:l.h,alignment:.topLeading)
  }
}
