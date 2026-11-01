import SwiftUI

struct RamenBowlBuilder: View {
  static let loop = RamenBowlBuilderMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.displayScale) private var displayScale
  @StateObject private var clock = RamenBowlBuilderClock()
  @State private var dragging = false
  @GestureState private var gestureActive = false
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey:"ramenBowlBuilderTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { full in
        let g = RamenBowlBuilderMotion.Geo(full.size)
        let art = RamenBowlBuilderArt.cached(full.size)
        TimelineView(.animation(paused:pinned != nil || scenePhase != .active)) { timeline in
          let frame = clock.sample(timeline.date,autoPlay,reduced,pinned)
          let count = frame.count
          ZStack(alignment:.topLeading) {
            RamenBowlBuilderTheme.color(0xffc891)
            header
              .frame(width:full.size.width-48,alignment:.leading)
              .offset(x:24,y:safe.safeAreaInsets.top+10+1/displayScale)
            Canvas(opaque:false,colorMode:.nonLinear,rendersAsynchronously:false) { c, _ in
              art.draw(c,frame,reduced)
            }
            .allowsHitTesting(false)
            Color.clear.contentShape(Rectangle())
              .frame(width:full.size.width,height:max(0,full.size.height-safe.safeAreaInsets.top-safe.safeAreaInsets.bottom-240))
              .offset(y:safe.safeAreaInsets.top+120)
              .gesture(DragGesture(minimumDistance:0,coordinateSpace:.named("RamenBowlBuilderScreen"))
                .updating($gestureActive) { _, active, _ in active = true }
                .onChanged { value in
                  guard pinned == nil else { return }
                  if !dragging { dragging = true; clock.begin(value.startLocation,g) }
                  clock.update(value.location)
                }
                .onEnded { _ in
                  guard pinned == nil else { return }
                  dragging = false; clock.end(g)
                })
              .onChange(of:gestureActive) { _, active in
                // RN onFinalize also drops/returns an ingredient when the gesture is cancelled.
                if !active && dragging && pinned == nil { dragging = false; clock.end(g) }
              }
              .accessibilityElement(children:.ignore)
              .accessibilityLabel("Ramen bowl with \(count) of 5 toppings. Drag a topping into the bowl")
            card(count,g)
              .frame(width:full.size.width-40,height:76)
              .offset(x:20,y:full.size.height-safe.safeAreaInsets.bottom-18-76)
          }
          .allowsHitTesting(pinned == nil)
          .coordinateSpace(name:"RamenBowlBuilderScreen")
          .frame(width:full.size.width,height:full.size.height,alignment:.topLeading)
        }
      }
      .ignoresSafeArea()
    }
    .background(RamenBowlBuilderTheme.color(0xffc891))
    .preferredColorScheme(.light)
    .persistentSystemOverlays(.hidden)
    .onChange(of:scenePhase) { _,_ in clock.resync() }
  }
  private var header: some View {
    VStack(alignment:.leading,spacing:0) {
      Text("STEP 2 OF 4").font(RamenBowlBuilderTheme.font("Nunito-Black",12))
        .tracking(1.4).foregroundStyle(RamenBowlBuilderTheme.color(0xb4532b))
      Text("Top your bowl").font(RamenBowlBuilderTheme.font("TitanOne",30))
        .foregroundStyle(RamenBowlBuilderTheme.color(0x3a1a10)).padding(.top,4)
      Text("Drag the toppings into the broth").font(RamenBowlBuilderTheme.font("Nunito-ExtraBold",15))
        .foregroundStyle(RamenBowlBuilderTheme.color(0x8a4a2e)).padding(.top,2)
    }
  }
  private func card(_ count:Int, _ g:RamenBowlBuilderMotion.Geo) -> some View {
    HStack(spacing:10) {
      VStack(alignment:.leading,spacing:2) {
        Text("Toppings \(count)/5").font(RamenBowlBuilderTheme.font("Nunito-Black",17))
          .foregroundStyle(RamenBowlBuilderTheme.color(0x3a1a10))
        Text(count == 0 ? "Pick five to finish the bowl" : RamenBowlBuilderMotion.names.prefix(max(1,count)).joined(separator:" · "))
          .font(RamenBowlBuilderTheme.font("Nunito-ExtraBold",13))
          .foregroundStyle(RamenBowlBuilderTheme.color(0x9a6a50)).lineLimit(1).offset(y:1/displayScale)
      }
      .frame(maxWidth:.infinity,alignment:.leading)
      Button { if pinned == nil { clock.next(g) } } label: {
        Text(count >= 5 ? "Next" : "\(5-count) to go")
          .font(RamenBowlBuilderTheme.font("Nunito-Black",16)).foregroundStyle(.white)
          .fixedSize().padding(.horizontal,22).frame(height:56)
          .background(RamenBowlBuilderTheme.color(count < 5 ? 0xf2a090:0xe8443a),in:Capsule())
      }
      .buttonStyle(RamenBowlBuilderButtonStyle())
      .accessibilityLabel(count >= 5 ? "Next step" : "\(5-count) toppings to go")
    }
    .padding(.leading,18).padding(.trailing,10)
    .frame(height:76)
    .background(RamenBowlBuilderTheme.color(0xfff3e6),in:RoundedRectangle(cornerRadius:26))
  }
}

struct RamenBowlBuilderButtonStyle: ButtonStyle {
  func makeBody(configuration:Configuration) -> some View {
    configuration.label.scaleEffect(configuration.isPressed ? 0.96:1)
  }
}
