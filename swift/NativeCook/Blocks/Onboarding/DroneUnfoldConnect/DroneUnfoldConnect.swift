import SwiftUI
import UIKit

struct DroneUnfoldConnect: View {
  static let loop = DroneUnfoldConnectMotion.loop
  var autoPlay: Bool
  init(autoPlay: Bool = false) { self.autoPlay=autoPlay }
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var pixelRatio
  @StateObject private var driver=DroneUnfoldConnectDriver()
  private typealias C = DroneUnfoldConnectTheme
  var body: some View {
    GeometryReader { safe in
      let top=safe.safeAreaInsets.top, bottom=safe.safeAreaInsets.bottom
      GeometryReader { g in
        let w=g.size.width, h=g.size.height
        let floorY=top+((h-top-bottom)*0.52).rounded()
        let cardTop=floorY+86, bottomTop=h-max(bottom,16)-8-120
        let auto=autoPlay && !reduced
        TimelineView(.animation(paused:scenePhase != .active || (!auto && driver.stage == 0 && driver.action == 0 && driver.manual.light < 0.001))) { timeline in
          let now=timeline.date, f=driver.frame(now,auto:auto)
          let step=driver.currentStage(now,auto:auto)
          let status=["Folded · 92%","Connecting…","Connected · flying","Landing…"][step]
          let clock=max(0,now.timeIntervalSince(driver.origin))
          let bob=(!reduced && (!auto || driver.taken)) ? 0.05*sin(clock*1.9)*f.lift/0.9 : 0
          ZStack(alignment:.topLeading) {
            C.page
            if scenePhase == .active {
              Rectangle().fill(.white).colorEffect(ShaderLibrary.DroneUnfoldConnectMain(
                .float4(Float(w),Float(h),Float((w*0.36).rounded()),Float(pixelRatio)),
                .float4(Float(f.drift),Float(f.lift+bob),Float(f.pitch),Float(f.roll)),
                .float4(Float(f.unfold),Float(f.spin),Float(0.5+0.25*f.roll),Float(clock*0.7)),
                .float4(Float(floorY),0.38,Float(f.light),0)))
                .allowsHitTesting(false).accessibilityHidden(true)
            }
            HStack {
              Path { p in p.move(to:CGPoint(x:10,y:2)); p.addLine(to:CGPoint(x:2,y:10)); p.addLine(to:CGPoint(x:10,y:18)) }
                .stroke(C.ink,style:StrokeStyle(lineWidth:2.4,lineCap:.round,lineJoin:.round))
                .frame(width:12,height:20).accessibilityLabel("Back").accessibilityAddTraits(.isButton)
              Spacer()
              Text("Add drone").font(C.font(17)).foregroundStyle(C.ink).accessibilityAddTraits(.isHeader)
              Spacer()
              if step == 2 {
                Text("Land").font(C.font(16)).foregroundStyle(C.accent)
                  .accessibilityAddTraits(.isButton).accessibilityAction { driver.land(Date(),auto:auto) }
              } else { Color.clear.frame(width:36,height:1) }
            }.frame(width:w-40,height:44).position(x:w/2,y:top+30)
            VStack(spacing:6) {
              Text("Skyline Mini").font(C.font(26)).foregroundStyle(C.ink).frame(height:32)
              HStack(spacing:8) {
                Circle().fill(C.statusColor(f.light)).frame(width:8,height:8)
                Text(status).font(C.font(15,"Medium")).foregroundStyle(C.mute)
              }
            }.frame(width:w).fixedSize(horizontal:false,vertical:true).offset(y:cardTop)
              .accessibilityElement(children:.ignore).accessibilityLabel("Skyline Mini, \(status)")
            Text("Connect").font(C.font(17)).foregroundStyle(C.accentInk)
              .frame(width:w-40,height:56).background(C.accent,in:Capsule())
              .scaleEffect(1-0.04*f.pressConnect)
              .opacity((1-f.connected)*(1-0.55*min(1,f.unfold*3)))
              .position(x:w/2,y:bottomTop+60)
              .accessibilityAddTraits(.isButton).accessibilityLabel("Connect")
              .accessibilityHidden(step != 0).accessibilityAction { driver.connect(Date(),auto:auto) }
            Circle().fill(C.card).overlay(Circle().strokeBorder(C.line,lineWidth:1))
              .overlay {
                Circle().fill(C.color(0x2C333D)).overlay(Circle().strokeBorder(C.color(0x3A434F),lineWidth:1))
                  .frame(width:52,height:52).offset(x:f.stickX*44,y:f.stickY*44)
              }.frame(width:120,height:120).scaleEffect(0.9+0.1*f.connected).opacity(f.connected)
              .position(x:w/2,y:bottomTop+60)
              .accessibilityLabel("Flight stick").accessibilityHidden(step != 2)
              .accessibilityAdjustableAction { direction in
                driver.takeOver(Date(),auto:auto)
                driver.move(CGSize(width:direction == .increment ? 44 : -44,height:0),Date())
                driver.release(Date())
              }
            Circle().fill(.white.opacity(0.35)).overlay(Circle().strokeBorder(.white.opacity(0.8),lineWidth:1.5))
              .frame(width:44,height:44).scaleEffect(1-0.12*f.pressConnect).opacity(0.85*f.finger)
              .position(x:w/2+(f.fingerAt == 1 ? f.stickX*44 : 0),
                        y:bottomTop+60+(f.fingerAt == 1 ? f.stickY*44 : 0))
              .allowsHitTesting(false).accessibilityHidden(true)
            DroneUnfoldConnectTouchView { state, point, translation in
              let date=Date()
              if state == .began {
                driver.takeOver(date,auto:auto)
                if step == 2 && hypot(point.x-w/2,point.y-bottomTop-60) <= 60 { driver.touchZone=1 }
                else if step == 0 && point.x >= 20 && point.x <= w-20 && abs(point.y-bottomTop-60) <= 28 { driver.touchZone=2 }
                else if step == 2 && point.x >= w-80 && point.y >= top-4 && point.y <= top+64 { driver.touchZone=3 }
                else { driver.touchZone=0 }
              } else if state == .changed && driver.touchZone == 1 { driver.move(translation,date) }
              else if state == .ended || state == .cancelled || state == .failed {
                if state == .ended && hypot(translation.width,translation.height) < 12 {
                  if driver.touchZone == 2 { driver.connect(date,auto:auto) }
                  if driver.touchZone == 3 { driver.land(date,auto:auto) }
                }
                if driver.touchZone == 1 { driver.release(date) }
                else { driver.lastTouch=date }
                driver.touchZone=0
              }
            }.accessibilityHidden(true)
          }.frame(width:w,height:h,alignment:.topLeading)
            .onChange(of:step) { _, _ in
              if UIAccessibility.isVoiceOverRunning { UIAccessibility.post(notification:.announcement,argument:status) }
            }
            .onChange(of:timeline.date) { _, date in driver.advance(date,auto:auto,reduced:reduced) }
        }
      }.ignoresSafeArea()
    }.preferredColorScheme(.dark)
      .onChange(of:scenePhase) { _, phase in driver.activity(phase == .active) }
  }
}

struct DroneUnfoldConnectTouchView: UIViewRepresentable {
  var event: (UIGestureRecognizer.State,CGPoint,CGSize)->Void
  func makeUIView(context: Context) -> DroneUnfoldConnectTouchSurface {
    let view=DroneUnfoldConnectTouchSurface(); view.event=event; return view
  }
  func updateUIView(_ view: DroneUnfoldConnectTouchSurface, context: Context) { view.event=event }
}
final class DroneUnfoldConnectTouchSurface: UIView, UIGestureRecognizerDelegate {
  var event: ((UIGestureRecognizer.State,CGPoint,CGSize)->Void)?
  private var start=CGPoint.zero
  override init(frame:CGRect) {
    super.init(frame:frame); backgroundColor = .clear; isOpaque=false; isAccessibilityElement=false
    let g=UILongPressGestureRecognizer(target:self,action:#selector(handle(_:)))
    g.minimumPressDuration=0; g.allowableMovement = .greatestFiniteMagnitude
    g.cancelsTouchesInView=false; g.delegate=self; addGestureRecognizer(g)
  }
  required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
  func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer) -> Bool { true }
  @objc private func handle(_ g:UILongPressGestureRecognizer) {
    let p=g.location(in:self); if g.state == .began { start=p }
    event?(g.state,p,CGSize(width:p.x-start.x,height:p.y-start.y))
  }
}
