import SwiftUI

struct ClayCategoryTabs: View {
  static let loop = ClayCategoryTabsMotion.loop
  var autoPlay: Bool
  init(autoPlay: Bool = false) { self.autoPlay = autoPlay }
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scene
  @Environment(\.displayScale) private var scale
  @StateObject private var driver = ClayCategoryTabsDriver()
  private var pinned: Double? {
    #if DEBUG
    if UserDefaults.standard.object(forKey:"ncPinTime") != nil { return UserDefaults.standard.double(forKey:"ncPinTime") }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      GeometryReader { geometry in
        let layout = ClayCategoryTabsTheme.Layout(w:geometry.size.width,top:safe.safeAreaInsets.top)
        TimelineView(.animation(paused:pinned != nil || scene != .active || (reduced && !driver.moving))) { timeline in
          let sample = driver.sample(timeline.date,auto:autoPlay,reduced:reduced,pin:pinned)
          let frame = sample.frame
          let time = sample.time
          ZStack(alignment:.topLeading) {
            Canvas { context, size in
              ClayCategoryTabsArtwork(layout:layout,height:size.height,bottom:safe.safeAreaInsets.bottom,scale:scale)
                .draw(&context,frame:frame,time:time,reduced:reduced)
            }
            .accessibilityHidden(true)
            ClayCategoryTabsTouchView { state,point,delta,velocity in
              driver.touch(state,point,delta,velocity,layout:layout,reduced:reduced)
            }
            .accessibilityHidden(true)
            accessibility(layout,height:geometry.size.height,bottom:safe.safeAreaInsets.bottom)
          }
        }
        .frame(width:geometry.size.width,height:geometry.size.height,alignment:.topLeading)
      }.ignoresSafeArea()
    }
    .background(ClayCategoryTabsTheme.page).preferredColorScheme(.dark)
    .onAppear { driver.reset(autoPlay,reduced) }
    .onChange(of:reduced) { _, value in driver.reset(autoPlay,value) }
    .onChange(of:autoPlay) { _, value in driver.reset(value,reduced) }
    .onChange(of:scene) { _, _ in driver.reactivate() }
  }
  private func accessibility(_ l: ClayCategoryTabsTheme.Layout, height: Double, bottom: Double) -> some View {
    ZStack(alignment:.topLeading) {
      Color.clear.frame(width:l.w-88,height:56).offset(x:16,y:l.searchTop)
        .accessibilityElement().accessibilityLabel("Where to? Anywhere, any week, add guests").accessibilityAddTraits(.isSearchField)
      Color.clear.frame(width:44,height:44).offset(x:l.w-60,y:l.searchTop+6)
        .accessibilityElement().accessibilityLabel("Filters").accessibilityAddTraits(.isButton)
      ForEach(0..<3,id:\.self) { i in
        Color.clear.frame(width:l.slot,height:244).offset(x:l.xs[i]-l.slot/2,y:l.stageTop)
          .accessibilityElement().accessibilityLabel(ClayCategoryTabsTheme.categories[i].name)
          .accessibilityAddTraits(driver.selected == i ? [.isButton,.isSelected] : [.isButton])
          .accessibilityAction { driver.tap(i,reduced:reduced) }
      }
      let category = ClayCategoryTabsTheme.categories[driver.selected]
      Color.clear.frame(width:l.w-32,height:26).offset(x:16,y:l.pagerTop+20)
        .accessibilityElement().accessibilityLabel(category.heading).accessibilityAddTraits(.isHeader)
      ForEach(0..<3,id:\.self) { i in
        Color.clear.frame(width:l.w-32,height:94).offset(x:16,y:l.pagerTop+58+Double(i)*106)
          .accessibilityElement().accessibilityLabel(category.listings[i].label)
      }
      Color.clear.frame(width:96,height:48).offset(x:l.w/2-48,y:height-max(bottom,16)-56)
        .accessibilityElement().accessibilityLabel("Show map").accessibilityAddTraits(.isButton)
    }.allowsHitTesting(false)
  }
}
