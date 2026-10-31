import SwiftUI

struct ParkAssistScrub: View {
  static let loop = ParkAssistScrubMotion.loop
  let autoPlay: Bool
  @StateObject private var driver = ParkAssistScrubDriver()
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var displayScale
  init(autoPlay: Bool = false) { self.autoPlay = autoPlay }
  private typealias T = ParkAssistScrubTheme
  private typealias M = ParkAssistScrubMotion
  var body: some View {
    GeometryReader { outer in
      let top = outer.safeAreaInsets.top, bottom = outer.safeAreaInsets.bottom
      GeometryReader { geo in
        let w = geo.size.width, h = geo.size.height
        let headTop = top+8, stageTop = headTop+64
        let buttonTop = h-max(bottom,16)-8-64
        let stageH = min(560,buttonTop-24-stageTop)
        TimelineView(.animation(paused:driver.manual || scenePhase != .active)) { timeline in
          let f = driver.displayed(timeline.date), pose = M.poseAt(f.s), gap = M.gapAt(f.s)
          ZStack(alignment:.topLeading) {
            T.page
            VStack(alignment:.leading,spacing:2) {
              Text("Park Assist").font(T.font(24)).foregroundStyle(T.ink).frame(height:30).accessibilityAddTraits(.isHeader)
              Text(f.parked > 0.5 ? "Parked · 30 cm to the wall" : "Hold the button to reverse into the bay")
                .font(T.font(14,"Regular")).foregroundStyle(T.mute).frame(height:20)
            }.offset(x:20,y:headTop)
            ZStack(alignment:.topLeading) {
              Rectangle().fill(.white).colorEffect(ShaderLibrary.ParkAssistScrubMain(
                .float4(Float(w),Float(stageH),58,Float(displayScale)),
                .float4(Float(pose.x),Float(pose.y),Float(pose.heading),Float((f.out > 0.5 ? -1 : 1)*M.steerAt(f.s))),
                .float4(Float(gap),Float(f.assist),Float(f.brake),Float(min(1,f.hazard))),
                .float4(0.75,1.9,0.62,0)))
              VStack(alignment:.leading,spacing:0) {
                Text("REAR").font(T.font(11)).tracking(0.8).foregroundStyle(T.mute)
                Text(gap >= 10 ? "10 m" : String(format:"%.2f m",gap)).font(T.font(28)).monospacedDigit()
                  .foregroundStyle(T.gapColor(gap)).frame(minWidth:110,alignment:.leading).frame(height:34)
              }.padding(.horizontal,14).padding(.vertical,8).padding(1)
                .background(T.page.opacity(0.72),in:RoundedRectangle(cornerRadius:16))
                .overlay(RoundedRectangle(cornerRadius:16).strokeBorder(T.line,lineWidth:1))
                .opacity(0.35+0.65*f.assist).offset(x:16,y:12)
                .accessibilityElement(children:.ignore).accessibilityLabel("Gap to the wall")
                .accessibilityValue(String(format:"%.2f metres",gap)).accessibilityAddTraits(.updatesFrequently)
            }.frame(width:w,height:stageH,alignment:.topLeading).clipped().offset(y:stageTop)
            ZStack {
              T.card
              T.accent.opacity(0.28).frame(width:(w-40)*max(0.0001,f.s)).frame(maxWidth:.infinity,alignment:.leading)
              Text("Hold to park").opacity(1-f.parked)
              Text("Drive out").opacity(f.parked)
            }.font(T.font(17)).foregroundStyle(T.ink).frame(width:w-40,height:64)
              .clipShape(Capsule()).overlay(Capsule().strokeBorder(T.line,lineWidth:1))
              .scaleEffect(1-0.03*f.holding).offset(x:20,y:buttonTop)
              .accessibilityElement(children:.ignore).accessibilityAddTraits(.isButton)
              .accessibilityLabel(f.parked > 0.5 ? "Drive out" : "Hold to park")
              .accessibilityHint(f.parked > 0.5 ? "" : "Reverses the car while held")
              .accessibilityAction { if f.parked > 0.5 { driver.accessibilityOut() } else { driver.accessibilityHold() } }
              .accessibilityAction(named:"Stop reversing") { driver.end() }
            Circle().fill(.white.opacity(0.35)).overlay(Circle().strokeBorder(.white.opacity(0.8),lineWidth:1.5))
              .frame(width:44,height:44).scaleEffect(1-0.12*f.holding).opacity(0.85*f.finger)
              .offset(x:w*0.5-22,y:buttonTop+32-22).allowsHitTesting(false).accessibilityHidden(true)
            ParkAssistScrubTouchView { state, point in
              if state == .began { driver.begin(inButton:CGRect(x:20,y:buttonTop,width:w-40,height:64).contains(point),point:point) }
              else if state == .changed { driver.move(point) }
              else if state == .ended { driver.move(point); driver.end() }
              else if state == .cancelled || state == .failed { driver.end(cancelled:true) }
            }.accessibilityHidden(true)
          }.frame(width:w,height:h,alignment:.topLeading)
        }
      }.ignoresSafeArea()
    }.preferredColorScheme(.dark)
      .onAppear { driver.configure(autoPlay:autoPlay,reduced:reduced) }
      .onChange(of:reduced) { _, value in driver.updateReduced(value) }
      .onChange(of:scenePhase) { _, value in driver.setActive(value == .active) }
      .onDisappear { driver.stop() }
  }
}
