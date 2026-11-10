import SwiftUI
import UIKit

struct VitaminCapsuleDose:View {
  static let loop=VitaminCapsuleDoseMotion.loop
  var autoPlay=false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var clock=VitaminCapsuleDoseClock()
  private var pinned:Double? {
    #if DEBUG
    if let value=UserDefaults.standard.object(forKey:"vitaminCapsuleDoseTime") {return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0}
    #endif
    return nil
  }
  var body:some View {
    GeometryReader { safe in
      GeometryReader { proxy in
        let l=VitaminCapsuleDoseLayout(w:proxy.size.width,h:proxy.size.height,top:safe.safeAreaInsets.top,bottom:safe.safeAreaInsets.bottom)
        TimelineView(.animation(paused:pinned != nil || scenePhase != .active || !clock.running)) { timeline in
          let f=pinned.map{VitaminCapsuleDoseMotion.at($0)} ?? clock.sample(timeline.date,l)
          let m=VitaminCapsuleDoseTheme.meds[f.med]
          ZStack(alignment:.topLeading) {
            VitaminCapsuleDoseResources.shared.page(l,f.med).resizable().frame(width:l.w,height:l.h).allowsHitTesting(false)
            Canvas {c,_ in
              let art=VitaminCapsuleDoseArt(l:l)
              if pinned==nil {art.transitions(c,f,clock)}
              art.controls(c,f,clock,pinned != nil)
            }.allowsHitTesting(false)
            VitaminCapsuleDoseMetal(layout:l,frame:f,pinned:pinned,scripted:clock.autoPlaying || pinned != nil,reduced:reduced).allowsHitTesting(false)
            Canvas {c,_ in VitaminCapsuleDoseArt(l:l).finger(c,f)}.allowsHitTesting(false)
            if pinned==nil {
              VitaminCapsuleDoseTouch(layout:l,clock:clock).accessibilityHidden(true)
              accessibility(l,f,m)
            }
          }.frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
        }
      }.ignoresSafeArea()
    }
    .background(.black).preferredColorScheme(.dark)
    .onAppear {_ = VitaminCapsuleDoseResources.shared;clock.configure(autoPlay,reduced)}
    .onChange(of:autoPlay){_,_ in clock.configure(autoPlay,reduced)}
    .onChange(of:reduced){_,_ in clock.configure(autoPlay,reduced)}
    .onChange(of:scenePhase){_,_ in clock.active()}
  }
  private func accessibility(_ l:VitaminCapsuleDoseLayout,_ f:VitaminCapsuleDoseMotion.Frame,_ m:VitaminCapsuleDoseTheme.Med)->some View {
    ZStack(alignment:.topLeading) {
      Color.clear.frame(width:280,height:70).position(x:25*l.sx+140,y:l.y(338)+15).allowsHitTesting(false)
        .accessibilityElement().accessibilityLabel("\(m.name). \(m.kind), \(m.dose)")
      Color.clear.frame(width:353*l.sx,height:l.chipH).position(x:(25+353/2.0)*l.sx,y:l.chipTop+l.chipH/2).allowsHitTesting(false)
        .accessibilityElement().accessibilityLabel("\(m.daily) Daily Dosage. \(m.each) Each Day")
      Color.clear.frame(width:120,height:30).position(x:25*l.sx+60,y:l.y(512)).allowsHitTesting(false)
        .accessibilityElement().accessibilityLabel("Schedule")
      access("Back",CGRect(x:35*l.sx-14,y:l.y(124)-20,width:90,height:40)){}
      access("Edit \(m.name)",CGRect(x:353*l.sx-24,y:l.y(349)-24,width:48,height:48)){}
      access("All logs",CGRect(x:l.w-115,y:l.y(512)-16,width:100,height:32)){}
      access("Export to PDF",CGRect(x:25*l.sx,y:l.y(689),width:353*l.sx,height:49*l.k)){}
      access("Log Medication",CGRect(x:25*l.sx,y:l.y(777),width:353*l.sx,height:56*l.k)){clock.accessibilityTake()}
        .accessibilityHint("Press and hold to log this dose")
      Color.clear.frame(width:l.unit*2.5,height:l.unit*2.2).position(x:l.cx,y:l.cy-l.unit*0.05)
        .accessibilityElement().accessibilityLabel(m.name).accessibilityHint("Swipe for the next medicine")
        .accessibilityAdjustableAction{ direction in clock.takeOver();clock.select(direction == .increment ? 1:-1) }
      ForEach(0..<3,id:\.self) {r in
        let on=clock.checks[f.med*3+r]==1
        access("\(m.times[r])\(on ? ", taken: undo":"")",CGRect(x:l.checkX-22,y:l.rowsY[r]-22,width:44,height:44)){clock.takeOver();clock.undo(r)}
          .accessibilityValue(on ? "Checked":"Unchecked").disabled(!on)
      }
    }
  }
  private func access(_ label:String,_ rect:CGRect,_ action:@escaping ()->Void)->some View {
    Color.clear.frame(width:rect.width,height:rect.height).position(x:rect.midX,y:rect.midY).allowsHitTesting(false)
      .accessibilityElement().accessibilityLabel(label).accessibilityAddTraits(.isButton).accessibilityAction { action() }
  }
}
struct VitaminCapsuleDoseTouch:UIViewRepresentable {
  let layout:VitaminCapsuleDoseLayout
  let clock:VitaminCapsuleDoseClock
  func makeUIView(context:Context)->VitaminCapsuleDoseTouchSurface {VitaminCapsuleDoseTouchSurface()}
  func updateUIView(_ view:VitaminCapsuleDoseTouchSurface,context:Context){view.layout=layout;view.clock=clock}
}
final class VitaminCapsuleDoseTouchSurface:UIView {
  var layout=VitaminCapsuleDoseLayout(w:402,h:874,top:62,bottom:34)
  weak var clock:VitaminCapsuleDoseClock?
  private var origin=CGPoint.zero,prev=CGPoint.zero,prevTime=0.0,velocity=CGPoint.zero
  override init(frame:CGRect){super.init(frame:frame);backgroundColor = .clear;isMultipleTouchEnabled=false}
  required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
  override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent?) {
    guard let t=touches.first else{return};let p=t.location(in:self);origin=p;prev=p;prevTime=t.timestamp;velocity = .zero
    clock?.touch(.began,p,.zero,.zero,layout)
  }
  override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent?) {
    guard let t=touches.first else{return};let p=t.location(in:self),dt=max(0.001,t.timestamp-prevTime)
    velocity=CGPoint(x:(p.x-prev.x)/dt,y:(p.y-prev.y)/dt);prev=p;prevTime=t.timestamp
    clock?.touch(.changed,p,CGPoint(x:p.x-origin.x,y:p.y-origin.y),velocity,layout)
  }
  override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent?){finish(touches,.ended)}
  override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent?){finish(touches,.cancelled)}
  private func finish(_ touches:Set<UITouch>,_ state:UIGestureRecognizer.State) {
    guard let t=touches.first else{return};let p=t.location(in:self)
    clock?.touch(state,p,CGPoint(x:p.x-origin.x,y:p.y-origin.y),velocity,layout)
  }
}
