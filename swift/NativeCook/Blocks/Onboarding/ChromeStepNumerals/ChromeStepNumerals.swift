import SwiftUI
import UIKit

struct ChromeStepNumerals: View {
  static let loop = ChromeStepNumeralsMotion.loop
  var autoPlay: Bool
  init(autoPlay: Bool = false) { self.autoPlay=autoPlay }
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var displayScale
  @StateObject private var driver=ChromeStepNumeralsDriver()
  private var pin: Double? {
    #if DEBUG
    if UserDefaults.standard.object(forKey:"ncPinTime") != nil {
      return UserDefaults.standard.double(forKey:"ncPinTime")
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        let layout=ChromeStepNumeralsTheme.Layout(w:full.size.width,h:full.size.height,
          top:safe.safeAreaInsets.top,bottom:safe.safeAreaInsets.bottom)
        TimelineView(.animation(paused: scenePhase != .active || pin != nil || (!autoPlay && !driver.live) || (reduced && !driver.live))) { timeline in
          let frame=driver.sample(timeline.date.timeIntervalSinceReferenceDate,autoPlay && !reduced,reduced,pin)
          let page=Int(floor(min(2,max(0,frame.r))+0.5))
          ZStack(alignment:.topLeading) {
            ChromeStepNumeralsTheme.page
            Canvas { context,size in
              let shader=ShaderLibrary.ChromeStepNumeralsMain(
                .float4(Float(layout.w/2),Float(layout.cy),Float(layout.unit),Float(displayScale)),
                .float4(Float(frame.r),Float(reduced ? 0.3 : ChromeStepNumeralsMotion.yawOf(frame.r)),0,Float(frame.bob)),
                .float4(0,Float(layout.floorY),0,0))
              context.fill(Path(CGRect(origin:.zero,size:size)),with:.shader(shader))
            }.accessibilityHidden(true)
            HStack {
              Text("← Back").foregroundStyle(ChromeStepNumeralsTheme.accent).accessibilityAddTraits(.isButton)
              Spacer()
              Text("Skip").foregroundStyle(ChromeStepNumeralsTheme.mute).accessibilityAddTraits(.isButton)
            }.font(ChromeStepNumeralsTheme.font("Medium",15))
              .frame(width:layout.w-40).offset(x:20,y:layout.top+8)
            ForEach(0..<3,id:\.self) { i in
              let d=frame.r-Double(i), k=max(0,1-abs(d)*1.8)
              VStack(spacing:8) {
                Text(ChromeStepNumeralsTheme.titles[i])
                  .font(ChromeStepNumeralsTheme.font("SemiBold",26))
                  .foregroundStyle(ChromeStepNumeralsTheme.ink).frame(height:32)
                Text(ChromeStepNumeralsTheme.lines[i])
                  .font(ChromeStepNumeralsTheme.font("Regular",16))
                  .foregroundStyle(ChromeStepNumeralsTheme.mute)
                  .lineSpacing(23-(UIFont(name:"Inter24pt-Regular",size:16)?.lineHeight ?? 19))
                  .fixedSize(horizontal:false,vertical:true)
                  .padding(.vertical,max(0,(23-(UIFont(name:"Inter24pt-Regular",size:16)?.lineHeight ?? 19))/2))
              }.multilineTextAlignment(.center).frame(width:layout.w-64)
                .opacity(k*k).offset(x:32-d*layout.w*0.3,y:layout.titleTop)
            }
            HStack(spacing:6) {
              ForEach(0..<3,id:\.self) { i in
                let k=max(0,1-abs(frame.r-Double(i)))
                Capsule().fill(k > 0.5 ? ChromeStepNumeralsTheme.accent : ChromeStepNumeralsTheme.dot)
                  .frame(width:8+16*k,height:8)
              }
            }.frame(width:layout.w).offset(y:layout.dotsTop)
              .accessibilityElement(children:.ignore).accessibilityLabel("Step \(page+1) of 3")
            Text(page == 2 ? "Get started" : "Next")
              .font(ChromeStepNumeralsTheme.font("SemiBold",17)).foregroundStyle(.white)
              .frame(width:layout.w-48,height:52).background(ChromeStepNumeralsTheme.accent,in:Capsule())
              .scaleEffect(1-0.04*frame.press).offset(x:24,y:layout.buttonTop)
              .accessibilityAddTraits(.isButton)
              .accessibilityAction { driver.next(autoPlay: autoPlay && !reduced,reduced:reduced) }
            Circle().fill(Color(.sRGB,red:40/255,green:30/255,blue:20/255,opacity:0.18))
              .overlay(Circle().strokeBorder(Color(.sRGB,red:40/255,green:30/255,blue:20/255,opacity:0.35),lineWidth:1.5))
              .frame(width:44,height:44).scaleEffect(1-0.12*frame.press)
              .opacity(0.85*frame.finger)
              .offset(x:(frame.fingerAt == 0 ? layout.w/2 : layout.w*frame.fingerX)-22,
                y:(frame.fingerAt == 0 ? layout.buttonTop+26 : layout.cy+layout.unit*0.4)-22)
              .accessibilityHidden(true)
            if pin == nil {
              ChromeStepNumeralsTouchView { state,point,translation,velocity in
                driver.touch(state,point,translation,velocity,layout,autoPlay && !reduced,reduced)
              }.accessibilityHidden(true)
            }
          }.frame(width:layout.w,height:layout.h,alignment:.topLeading).clipped()
            .onChange(of:driver.needsFrames(autoPlay && !reduced)) { _, value in driver.live=value }
            .onChange(of:page) { _, _ in if driver.manual { UISelectionFeedbackGenerator().selectionChanged() } }
        }
      }.ignoresSafeArea()
    }.background(ChromeStepNumeralsTheme.page).preferredColorScheme(.light)
      .onAppear { driver.reset(autoPlay && !reduced) }
      .onChange(of:reduced) { _,_ in driver.configure(autoPlay && !reduced) }
      .onChange(of:autoPlay) { _,_ in driver.configure(autoPlay && !reduced) }
      .onChange(of:scenePhase) { _, phase in driver.active(phase == .active) }
  }
}

final class ChromeStepNumeralsDriver: ObservableObject {
  @Published var live=false
  @Published var revision=0
  var manual=true
  private var frame=ChromeStepNumeralsMotion.Frame()
  private var epoch=Date.timeIntervalSinceReferenceDate, last=Date.timeIntervalSinceReferenceDate
  private var lastTouch = -100.0, r0=0.0, fadeStart=0.0, fadeFinger=0.0
  private var dragging=false, returning=false
  private var move: ChromeStepNumeralsMotion.Move?
  private var pausedAt: Double?
  func reset(_ auto: Bool) {
    manual = !auto; frame = .init(); move=nil; returning=false; dragging=false
    epoch=Date.timeIntervalSinceReferenceDate; last=epoch; live=auto; revision += 1
  }
  func configure(_ auto: Bool) {
    manual = !auto; epoch=Date.timeIntervalSinceReferenceDate; last=epoch
    live=needsFrames(auto); revision += 1
  }
  func active(_ value: Bool) {
    let now=Date.timeIntervalSinceReferenceDate
    if !value { pausedAt=now }
    else if let pause=pausedAt {
      let gap=now-pause; epoch += gap; lastTouch += gap; fadeStart += gap
      if move != nil { move!.start += gap }; last=now; pausedAt=nil; revision += 1
    }
  }
  func needsFrames(_ auto: Bool) -> Bool { auto || move != nil || dragging || frame.finger > 0.001 || abs(frame.bob) > 0.00001 }
  func sample(_ now: Double,_ auto: Bool,_ reduced: Bool,_ pin: Double?) -> ChromeStepNumeralsMotion.Frame {
    let dt=min(0.05,max(0,now-last)); last=now
    if !manual && auto {
      let t=pin ?? ((now-epoch).truncatingRemainder(dividingBy:ChromeStepNumeralsMotion.loop)+ChromeStepNumeralsMotion.loop).truncatingRemainder(dividingBy:ChromeStepNumeralsMotion.loop)
      frame=ChromeStepNumeralsMotion.frameAt(t); return frame
    }
    frame.bob = reduced ? 0 : frame.bob*(1-min(1,dt*3))
    let f=ChromeStepNumeralsMotion.seg(now,fadeStart,fadeStart+0.15)
    let eased=f < 0.5 ? 2*f*f : 1-pow(-2*f+2,2)/2
    frame.finger=fadeFinger*(1-eased)
    if let animation=move {
      let value=animation.sample(now); frame.r=value.done ? animation.to : value.r
      if value.done { move=nil }
      lastTouch=now
    }
    if dragging { lastTouch=now }
    if auto && !dragging && move == nil {
      if returning && frame.r < 0.001 {
        frame = .init(); manual=false; returning=false; epoch=now
      } else if now-lastTouch >= 2 && !returning {
        returning=true
        if frame.r > 0.001 { go(0,0,now,reduced) }
      }
    }
    return frame
  }
  private func takeOver(_ now: Double,_ auto: Bool,_ reduced: Bool) {
    _ = sample(now,auto,reduced,nil)
    lastTouch=now; returning=false
    if !manual { manual=true; fadeStart=now; fadeFinger=frame.finger; frame.press=0 }
    live=true; revision += 1
  }
  private func go(_ k: Double,_ velocity: Double,_ now: Double,_ reduced: Bool) {
    move = .init(start:now,from:frame.r,to:k,velocity:velocity,reduced:reduced)
    live=true
  }
  func next(autoPlay: Bool,reduced: Bool) {
    let now=Date.timeIntervalSinceReferenceDate
    takeOver(now,autoPlay,reduced)
    let k=floor(frame.r+0.5); go(k >= 2 ? 0 : k+1,0,now,reduced)
  }
  func touch(_ state: UIGestureRecognizer.State,_ point: CGPoint,_ translation: CGSize,_ velocity: CGVector,
             _ layout: ChromeStepNumeralsTheme.Layout,_ auto: Bool,_ reduced: Bool) {
    let now=Date.timeIntervalSinceReferenceDate
    if state == .began { takeOver(now,auto,reduced); dragging=false }
    lastTouch=now
    if state == .changed {
      _ = sample(now,auto,reduced,nil)
      if !dragging && abs(translation.width) > 10 {
        dragging=true; move=nil; r0=frame.r
      }
      if dragging { frame.r=ChromeStepNumeralsMotion.rubber(r0-translation.width/layout.w) }
    }
    if state == .ended || state == .cancelled || state == .failed {
      if dragging {
        dragging=false
        let v = -velocity.dx/layout.w
        go(min(2,max(0,floor(frame.r+0.25*v+0.5))),v == 0 ? 0.0001 : v,now,reduced)
      } else if state == .ended && abs(translation.width) < 10 && abs(translation.height) < 10 && point.x >= 24 && point.x <= layout.w-24 && point.y >= layout.buttonTop && point.y <= layout.buttonTop+52 {
        let k=floor(frame.r+0.5); go(k >= 2 ? 0 : k+1,0,now,reduced)
      }
    }
    revision += 1
  }
}
