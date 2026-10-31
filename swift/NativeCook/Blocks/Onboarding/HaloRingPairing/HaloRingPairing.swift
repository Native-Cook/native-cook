import SwiftUI
import UIKit

struct HaloRingPairing: View {
  static let loop = HaloRingPairingMotion.loop
  var autoPlay: Bool
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var displayScale
  @State private var asleep = false
  @State private var driver = HaloRingPairingDriver()
  init(autoPlay: Bool = false) { self.autoPlay = autoPlay }

  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
      GeometryReader { geo in
        let layout = HaloRingPairingLayout(w:geo.size.width,h:geo.size.height,top:top,bottom:bottom)
        TimelineView(.animation(paused:scenePhase != .active || (reduced && asleep))) { timeline in
          let _ = driver.revision
          let frame = driver.frame(at:timeline.date)
          page(layout,frame)
            .onChange(of:driver.idle(at:timeline.date)) { _,idle in if idle { asleep = true } }
        }
      }.ignoresSafeArea()
    }
    .background(HaloRingPairingTheme.page)
    .preferredColorScheme(.dark)
    .onChange(of:driver.revision) { _,_ in asleep = false }
    .onChange(of:scenePhase) { _,phase in driver.setActive(phase == .active) }
    .onDisappear { driver.cancel() }
    .onAppear { asleep = false; driver.configure(autoPlay:autoPlay,reduced:reduced) }
    .onChange(of:reduced) { _,value in driver.configure(autoPlay:autoPlay,reduced:value) }
    .onChange(of:autoPlay) { _,_ in driver.configure(autoPlay:autoPlay,reduced:reduced) }
  }

  private func page(_ l: HaloRingPairingLayout,_ f: HaloRingPairingFrame) -> some View {
    let searchWords = (1-HaloRingPairingMotion.smooth(0.2,0.7,f.condense))*(1-f.paired)
    return ZStack(alignment:.topLeading) {
      HaloRingPairingTheme.page
      HaloRingPairingTouchView { state,translation,velocity in driver.drag(state,translation,velocity) }
        .frame(width:l.w,height:300).offset(y:l.stageY-150)
        .accessibilityElement().accessibilityLabel(f.step == 0 ? "Looking for your ring" : "Halo Ring, titanium. Drag to turn it")
        .accessibilityAdjustableAction { direction in
          driver.drag(.began,.zero,.zero)
          driver.drag(.changed,CGSize(width:direction == .increment ? 40 : -40,height:0),.zero)
          driver.drag(.ended,.zero,.zero)
        }
      words("Looking for your ring","Hold it close to your phone",l)
        .offset(y:l.titleTop).opacity(searchWords).allowsHitTesting(false).accessibilityHidden(searchWords < 0.01)
      words("Found Halo Ring","Titanium · Size \(f.size)",l)
        .offset(y:l.titleTop+8*(1-f.found)).opacity(f.found).accessibilityHidden(f.found < 0.01)
      sizes(f).frame(width:l.w).offset(y:l.sizesTop+8*(1-f.found))
        .opacity(f.found).allowsHitTesting(f.step == 1).accessibilityHidden(f.step != 1)
      Button { driver.pair() } label: {
        Text("Pair ring").font(HaloRingPairingTheme.font(17,"SemiBold"))
          .foregroundStyle(HaloRingPairingTheme.greenInk).frame(width:l.w-40,height:56)
          .background(HaloRingPairingTheme.green,in:Capsule())
      }.buttonStyle(.plain).scaleEffect(1-0.04*f.pressPair)
        .offset(x:20,y:l.buttonTop).opacity(f.found).allowsHitTesting(f.step == 1).accessibilityHidden(f.step != 1)
      dashboard(l).offset(x:20,y:l.top+10+12*(1-f.paired)).opacity(f.paired)
        .allowsHitTesting(f.step == 2).accessibilityHidden(f.step != 2)
      Circle().fill(HaloRingPairingTheme.card)
        .overlay(Circle().strokeBorder(HaloRingPairingTheme.line,lineWidth:1))
        .frame(width:44,height:44).offset(x:l.chipX-22,y:l.chipY-22)
        .opacity(HaloRingPairingMotion.smooth(0.6,1,f.fly)).allowsHitTesting(false).accessibilityHidden(true)
      ring(l,f).allowsHitTesting(false).accessibilityHidden(true)
      Circle().fill(.white.opacity(0.35)).overlay(Circle().strokeBorder(.white.opacity(0.85),lineWidth:1.5))
        .frame(width:44,height:44)
        .offset(x:fingerX(l,f)-22,y:fingerY(l,f)-22).opacity(0.85*f.finger)
        .allowsHitTesting(false).accessibilityHidden(true)
    }
    .frame(width:l.w,height:l.h,alignment:.topLeading)
    .contentShape(Rectangle())
    .simultaneousGesture(DragGesture(minimumDistance:0).onChanged { _ in driver.beginScreen() }.onEnded { _ in driver.endScreen() })
  }
  private func words(_ title: String,_ sub: String,_ l: HaloRingPairingLayout) -> some View {
    VStack(spacing:6) {
      Text(title).font(HaloRingPairingTheme.font(26,"SemiBold")).foregroundStyle(HaloRingPairingTheme.ink)
        .frame(height:32).accessibilityAddTraits(.isHeader)
      Text(sub).font(HaloRingPairingTheme.font(15)).foregroundStyle(HaloRingPairingTheme.mute).frame(height:20)
    }.multilineTextAlignment(.center).frame(width:l.w-48).padding(.horizontal,24)
  }
  private func sizes(_ f: HaloRingPairingFrame) -> some View {
    HStack(spacing:8) {
      ForEach(HaloRingPairingMotion.sizes,id:\.self) { size in
        Button { driver.choose(size) } label: {
          Text("\(size)").font(HaloRingPairingTheme.font(15,"Medium")).monospacedDigit()
            .foregroundStyle(size == f.size ? HaloRingPairingTheme.ink : HaloRingPairingTheme.mute)
            .frame(width:46,height:36)
            .background(size == f.size ? HaloRingPairingTheme.green.opacity(0.12) : .clear,in:Capsule())
            .overlay(Capsule().strokeBorder(size == f.size ? HaloRingPairingTheme.green : HaloRingPairingTheme.line,lineWidth:1))
        }.buttonStyle(.plain).accessibilityLabel("Size \(size)")
          .accessibilityAddTraits(size == f.size ? [.isSelected] : [])
      }
    }.accessibilityLabel("Ring size")
  }
  private func dashboard(_ l: HaloRingPairingLayout) -> some View {
    VStack(alignment:.leading,spacing:0) {
      HStack(spacing:12) {
        Button { driver.unpair() } label: { Color.clear.frame(width:44,height:44).contentShape(Circle()) }
          .buttonStyle(.plain).accessibilityLabel("Halo Ring, connected. Show the ring")
        VStack(alignment:.leading,spacing:0) {
          Text("Halo").font(HaloRingPairingTheme.font(16,"SemiBold")).foregroundStyle(HaloRingPairingTheme.ink).frame(height:21)
          Text("Connected").font(HaloRingPairingTheme.font(13,"Medium")).foregroundStyle(HaloRingPairingTheme.green).frame(height:17)
        }
      }.frame(height:44)
      Text("Good morning").font(HaloRingPairingTheme.font(30,"SemiBold")).foregroundStyle(HaloRingPairingTheme.ink)
        .frame(height:38).padding(.top,28).padding(.bottom,16).accessibilityAddTraits(.isHeader)
      VStack(alignment:.leading,spacing:12) {
        ForEach([0,1],id:\.self) { row in
          HStack(spacing:12) {
            stat(row*2,l); stat(row*2+1,l)
          }
        }
      }
    }.frame(width:l.w-40,alignment:.leading)
  }
  private func stat(_ index: Int,_ l: HaloRingPairingLayout) -> some View {
    let s = HaloRingPairingTheme.stats[index]
    return VStack(alignment:.leading,spacing:0) {
      Text(s.0).font(HaloRingPairingTheme.font(14,"Medium")).foregroundStyle(HaloRingPairingTheme.mute)
      Spacer(minLength:0)
      (Text(s.1).font(HaloRingPairingTheme.font(28,"SemiBold")).foregroundStyle(HaloRingPairingTheme.ink)
       + Text(s.2.isEmpty ? "" : " "+s.2).font(HaloRingPairingTheme.font(15,"Medium")).foregroundStyle(HaloRingPairingTheme.mute)).monospacedDigit()
    }.padding(16).frame(width:(l.w-40)*0.475,height:108,alignment:.leading)
      .background(HaloRingPairingTheme.card,in:RoundedRectangle(cornerRadius:22))
      .accessibilityElement(children:.ignore).accessibilityLabel("\(s.0) \(s.1) \(s.2)")
  }
  private func ring(_ l: HaloRingPairingLayout,_ f: HaloRingPairingFrame) -> some View {
    let arc = sin(Double.pi*f.fly)
    let x = l.w/2+(l.chipX-l.w/2)*f.fly-30*arc
    let y = l.stageY+(l.chipY-l.stageY)*f.fly-20*arc
    let size = 148+(13-148)*f.fly
    let bump = HaloRingPairingMotion.bump(f.bumpProgress*0.7)
    let scale = f.scale*(1+0.04*bump)*(1-f.fly)+f.fly
    return Canvas { context,canvasSize in
      context.fill(Path(CGRect(origin:.zero,size:canvasSize)),with:.shader(ShaderLibrary.HaloRingPairingMain(
        .float4(Float(x),Float(y),Float(size),Float(displayScale)),
        .float4(Float(f.condense),Float(f.yaw),Float(1.05+(0.3-1.05)*f.fly),Float(scale)),
        .float4(Float(f.phase),Float(f.search*(reduced ? 0.6 : 1)),1,0)
      )))
    }.frame(width:l.w,height:l.h)
  }
  private func fingerX(_ l: HaloRingPairingLayout,_ f: HaloRingPairingFrame) -> Double {
    f.fingerAt == 0 ? l.w*0.3+f.fingerDx : f.fingerAt == 1 ? l.w/2+28 : l.w/2
  }
  private func fingerY(_ l: HaloRingPairingLayout,_ f: HaloRingPairingFrame) -> Double {
    f.fingerAt == 0 ? l.stageY+40 : f.fingerAt == 1 ? l.sizesTop+18 : l.buttonTop+28
  }
}
