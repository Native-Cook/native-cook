import SwiftUI

struct MailboxInbox: View {
    static let loop = MailboxInboxMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var clock = MailboxInboxClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "mailboxInboxTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            let top = safe.safeAreaInsets.top
            GeometryReader { full in
                TimelineView(.animation(paused: pinned != nil || phase != .active || (!clock.running && (!autoPlay || reduced || clock.holdingOpen)))) { timeline in
                    let frame = clock.sample(timeline.date, auto:autoPlay, reduced:reduced, pinned:pinned)
                    let width = full.size.width
                    ZStack(alignment:.topLeading) {
                        MailboxInboxTheme.color(0xF4F1EA)
                        Canvas { context, _ in MailboxInboxArt.box(context,cx:width/2,cy:top+150,open:frame.open,reduced:reduced) }
                        HStack {
                            Text("Postbox").font(MailboxInboxTheme.font("Bold",30)).tracking(-0.5).foregroundStyle(MailboxInboxTheme.color(0x2B2F3A))
                            Spacer()
                            Button {} label: {
                                MailboxInboxComposeIcon().frame(width:21,height:22).offset(x:-1/3)
                                    .frame(width:44,height:44).background(.white,in:Circle())
                            }.accessibilityLabel("Write a letter")
                        }.frame(width:width-40,height:44).offset(x:20,y:top+8)
                        Button { clock.toggle() } label: { Color.clear.contentShape(Rectangle()) }
                            .frame(width:176,height:193.6).offset(x:width/2-88,y:top+150-61.6)
                            .accessibilityLabel("Letterbox, 4 new letters. Tap to open or close")
                            .disabled(pinned != nil)
                        Text("4").font(MailboxInboxTheme.font("Bold",13)).foregroundStyle(.white)
                            .frame(width:24,height:24).background(MailboxInboxTheme.color(0x2B2F3A),in:Capsule())
                            .opacity(1-MailboxInboxMotion.clamp(frame.open*3))
                            .offset(x:width/2+88*0.55,y:top+150-88*0.85).allowsHitTesting(false)
                        Text("Today’s post").font(MailboxInboxTheme.font("SemiBold",15)).foregroundStyle(MailboxInboxTheme.color(0x8A8578))
                            .frame(height:MailboxInboxTheme.lineHeight("SemiBold",15)).offset(x:20,y:top+262)
                        ForEach(0..<4) { i in
                            let p = MailboxInboxMotion.pose(frame,i,width,top)
                            MailboxInboxLetter(i:i,width:width-32,k:p.k,scale:p.scale,angle:p.angle,onFile:{clock.file(i)})
                                .offset(x:16+p.x,y:top+296+Double(i)*88+p.y)
                                .opacity(p.k < 0.02 ? 0 : 1).allowsHitTesting(pinned == nil)
                        }
                    }.frame(width:width,height:full.size.height,alignment:.topLeading)
                }
            }.ignoresSafeArea()
        }.background(MailboxInboxTheme.color(0xF4F1EA)).preferredColorScheme(.light).persistentSystemOverlays(.hidden)
            .onChange(of:phase) { _, _ in clock.resetDate() }
    }
}

private struct MailboxInboxLetter: View {
    let i: Int
    let width: Double
    let k: Double
    let scale: Double
    let angle: Double
    let onFile: () -> Void
    @State private var dx = 0.0
    private var m: (from:String,text:String,time:String,color:UInt32) { MailboxInboxTheme.mail[i] }
    var body: some View {
        ZStack(alignment:.topLeading) {
            RoundedRectangle(cornerRadius:18).fill(.white)
                .shadow(color:MailboxInboxTheme.color(0x2B2F3A).opacity(0.06),radius:10,x:0,y:4)
            ZStack(alignment:.topLeading) {
                MailboxInboxTheme.color(0xFFF8EC)
                // RN absolute children are positioned inside the envelope’s 1 pt border.
                Rectangle().fill(MailboxInboxTheme.color(0xF3E7CF)).frame(width:120,height:120)
                    .rotationEffect(.degrees(45)).offset(x:width/2-60,y:-59)
                RoundedRectangle(cornerRadius:3).fill(MailboxInboxTheme.color(m.color)).frame(width:22,height:26).offset(x:width-35,y:11)
            }.frame(width:width,height:78,alignment:.topLeading).clipShape(RoundedRectangle(cornerRadius:18))
                .overlay(RoundedRectangle(cornerRadius:18).strokeBorder(MailboxInboxTheme.color(0xEADFC8),lineWidth:1))
                .compositingGroup().opacity(1-MailboxInboxMotion.clamp((k-0.45)/0.3))
            HStack(spacing:12) {
                Text(String(m.from.prefix(1))).font(MailboxInboxTheme.font("Bold",18)).foregroundStyle(.white)
                    .offset(x:(i == 0 || i == 2) ? -1/3 : 0,y:1/3)
                    .frame(width:46,height:46).background(MailboxInboxTheme.color(m.color),in:Circle())
                VStack(spacing:3) {
                    HStack(alignment:.top,spacing:0) {
                        Text(m.from).font(MailboxInboxTheme.font("SemiBold",16)).foregroundStyle(MailboxInboxTheme.color(0x2B2F3A))
                        Spacer(minLength:0)
                        Text(m.time).font(MailboxInboxTheme.font("Regular",13)).foregroundStyle(MailboxInboxTheme.color(0x9A958A))
                    }.frame(height:MailboxInboxTheme.lineHeight("SemiBold",16),alignment:.top)
                    Text(m.text).font(MailboxInboxTheme.font("Regular",14)).foregroundStyle(MailboxInboxTheme.color(0x6B6760))
                        .lineLimit(1).frame(maxWidth:.infinity,alignment:.leading)
                        .frame(height:MailboxInboxTheme.lineHeight("Regular",14)).offset(y:1/3)
                }
            }.padding(.horizontal,14).frame(width:width,height:78).compositingGroup().opacity(MailboxInboxMotion.clamp((k-0.55)/0.3))
        }.frame(width:width,height:78)
            .overlay {
                MailboxInboxSwipe(onChange:{dx=$0},onEnd:{x,velocity in
                    if x > 90 || velocity > 700 { onFile() }
                    dx=0
                })
            }
            .scaleEffect(scale).rotationEffect(.degrees(angle)).offset(x:dx)
            .accessibilityElement(children:.ignore).accessibilityAddTraits(.isButton)
            .accessibilityLabel("\(m.from): \(m.text). \(m.time)")
            .accessibilityHint("Swipe right to file it")
    }
}
