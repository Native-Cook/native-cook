import SwiftUI
import UIKit
import CoreText

/// Private, faithful study of the supplied Swiggy reference. Artwork is bundled separately from UI.
struct BuzzStreaks: View {
    var autoPlay = false
    var enhancedText = true
    var motionProfile: BuzzStreaksMotionProfile = .approved
    private var signature: BuzzStreaksSignature? { motionProfile == .signature && !reduced ? art.signature : nil }
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var art = BuzzStreaksArt()
    @State private var started = Date()
    @State private var base = 0.0
    @State private var playing = false
    @State private var suspended = 0.0
    @State private var resumeOnActive = false
    @State private var tap = 0

    private var clock: Double { min(10.699, max(0, base + (playing ? Date().timeIntervalSince(started) : 0))) }
    private func seek(_ t: Double, play: Bool = false, haptic:Bool = false) {
        base = reduced ? 10.699 : t
        started = Date()
        playing = play && !reduced
        resumeOnActive = false
        if haptic {tap += 1}
    }

    var body: some View {
        GeometryReader { proxy in
            let scale = min(proxy.size.width / 360, proxy.size.height / 780)
            TimelineView(.animation(minimumInterval: 1 / 60, paused: !playing || reduced || scenePhase != .active)) { tick in
                let t = reduced ? 10.699 : min(10.699, max(0, base + (playing ? tick.date.timeIntervalSince(started) : 0)))
                stage(t)
                    .frame(width: 360, height: 780, alignment: .topLeading)
                    .scaleEffect(scale, anchor: .topLeading)
                    .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
                    .offset(x: (proxy.size.width - 360 * scale) / 2,
                            y: (proxy.size.height - 780 * scale) / 2)
                    .onChange(of: t >= 10.699) { _, ended in if ended { base = 10.699; playing = false } }
            }
        }
        .background(Color(red: 0.38, green: 0.14, blue: 0.64).ignoresSafeArea())
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .onAppear { seek(0, play: autoPlay) }
        .onChange(of: reduced) { _, value in if value {seek(10.699)} }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active, playing { resumeOnActive = true; suspended = clock; playing = false; base = suspended }
            else if phase == .active && resumeOnActive && !reduced { started = Date(); playing = true; resumeOnActive = false }
        }
        .sensoryFeedback(.impact(weight: .light), trigger: tap)
    }

    private func stage(_ t: Double) -> some View {
        let panel = BuzzStreaksMotion.flow(t, art.layout["panel"] ?? BuzzStreaksMotion.panel)
        let header = BuzzStreaksMotion.flow(t, art.layout["header"] ?? BuzzStreaksMotion.header)
        let bonus = BuzzStreaksMotion.flow(t, BuzzStreaksMotion.bonus)
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                art.draw("background", in: CGRect(x: 0, y: 0, width: 360, height: 780), context: &context)
                art.rewards.drawBackground(time:t,context:&context)
                art.mascots.draw("orange", time:t, signature:signature, context:&context)
                art.mascots.draw("pink", time:t, signature:signature, context:&context)
                signature?.drawRelay(t,context:&context)
                art.rewards.drawForeground(time:t,signature:signature,context:&context)
            }.accessibilityHidden(true)
            ZStack(alignment:.topLeading) {
                art.image("wordmark").resizable().frame(width:107,height:51).offset(x:40)
                Canvas { context,_ in art.illustrations.drawPrize(time:t,enhanced:enhancedText && !reduced,context:&context) }.frame(width:190,height:99).offset(y:64)
            }.frame(width:190,height:163,alignment:.topLeading).offset(x:86,y:79+header)
                .accessibilityElement(children:.ignore).accessibilityLabel("Buzz Streaks. Win up to ₹1000.").accessibilityAddTraits(.isHeader)
            caption("Invite your friends by buzzing",phrase:"invite",time:t)
            caption("They buzz you back",phrase:"back",time:t)
            ZStack(alignment:.topLeading) {
                how(t).frame(width:330,height:224).offset(x:15,y:panel)
                    .accessibilityHidden(panel > 670)
                bonuses(t).frame(width:330,height:158).offset(x:15,y:BuzzStreaksMotion.flow(t,art.layout["bonusY"] ?? [[0,780],[10.7,511]])).opacity(t >= 7.5 ? 1 : 0).accessibilityHidden(bonus < 0.5)
            }.frame(width:360,height:674,alignment:.topLeading).clipped()
            Button { seek(0, play:true,haptic:true) } label: {
                ZStack {
                    RoundedRectangle(cornerRadius:7).fill(LinearGradient(colors:[Color(hex:0xB4D61A),Color(hex:0xB1D214)],startPoint:.leading,endPoint:.trailing))
                    RoundedRectangle(cornerRadius:4).fill(LinearGradient(colors:[Color(hex:0xC9E73C),Color(hex:0xD9F650)],startPoint:.leading,endPoint:.trailing)).padding(.horizontal,6).padding(.top,4).padding(.bottom,6)
                    art.image("start-label").resizable().frame(width:106,height:23).overlay {
                        if enhancedText && !reduced,let center=BuzzStreaksTextMotionMath.sweep(t,start:9.45) {
                            BuzzStreaksTextMotionMath.gradient(center,peak:0.16)
                                .mask(art.image("start-label").resizable())
                        }
                    }
                }.overlay(RoundedRectangle(cornerRadius:7).stroke(Color(hex:0xABD416),lineWidth:1))
            }.buttonStyle(BuzzStreaksPressStyle()).accessibilityLabel("Start buzzing")
                .frame(width:332,height:57).offset(x:14,y:674)
            circleButton("arrow.left", label:"Replay introduction") { seek(0,play:true,haptic:true) }.offset(x:9,y:66)
            circleButton("info.circle", label:"How buzzing works") { seek(7.25,play:true,haptic:true) }.offset(x:307,y:66)
        }.clipped()
    }

    private func caption(_ text:String,phrase:String,time t:Double)->some View {
        let alpha=BuzzStreaksMotion.flow(t,art.layout[phrase+"_opacity"] ?? [[0,0]])
        let scale=BuzzStreaksMotion.flow(t,art.layout[phrase+"_scale"] ?? [[0,1]])
        let y=BuzzStreaksMotion.flow(t,art.layout[phrase+"_y"] ?? [[0,0]])
        return Group {
            if enhancedText && !reduced {
                Canvas { context,_ in art.textMotion.drawCaption(text,time:t,start:phrase=="invite" ? 0.05 : 1.52,
                    animate:autoPlay || playing || resumeOnActive || t>0,context:&context) }
                    .accessibilityElement(children:.ignore).accessibilityLabel(text)
            } else {Text(text).font(.custom("Gilroy-SemiBold",size:19.6)).foregroundStyle(.white)}
        }
            .frame(width:360,height:24).scaleEffect(scale).position(x:180,y:646+y)
            .opacity(alpha).accessibilityHidden(alpha<0.1)
    }
    private func circleButton(_ icon:String,label:String,action:@escaping ()->Void)->some View {
        Button(action:action) {
            Image(systemName:icon).font(.system(size:15,weight:.regular)).foregroundStyle(Color(hex:0x17221B))
                .frame(width:33,height:33).background(.white,in:Circle()).frame(width:45,height:45)
        }.buttonStyle(BuzzStreaksPressStyle()).accessibilityLabel(label)
    }
    private func how(_ t:Double) -> some View {
        ZStack(alignment:.topLeading) {
            RoundedRectangle(cornerRadius:25).fill(Color(hex:0x7A3BBB))
                .overlay(RoundedRectangle(cornerRadius:25).stroke(LinearGradient(colors:[Color(hex:0xAA65D3),.clear],startPoint:.top,endPoint:.bottom),lineWidth:1))
            Canvas { context,_ in signature?.drawPortalLip(t,context:&context) }.allowsHitTesting(false)
            Path { p in p.move(to:CGPoint(x:33,y:60));p.addLine(to:CGPoint(x:33,y:185)) }.stroke(Color(hex:0xBD84E3),style:StrokeStyle(lineWidth:1,dash:[2,3]))
            VStack(spacing:0) {
                row("invite","Buzz friends by inviting","Send them a link to join the game")
                row("phone","They “Buzz” you back","By opening the link")
                row("coin","Both earn Free Cash!","After buzzing each other")
            }.offset(y:37)
            Text("HOW BUZZING WORKS").font(.custom("Gilroy-ExtraBold",size:9.5)).foregroundStyle(.white)
                .frame(width:132,height:20,alignment:.top).padding(.top,1)
                .background(LinearGradient(colors:[Color(hex:0xE875FF),Color(hex:0xAA00F0)],startPoint:.top,endPoint:.bottom))
                .clipShape(BuzzStreaksRibbon()).offset(x:98)
        }.clipShape(RoundedRectangle(cornerRadius:25))
    }
    private func row(_ image:String,_ title:String,_ detail:String)->some View {
        let size = image == "coin" ? CGSize(width:28,height:30) : image == "phone" ? CGSize(width:36,height:44) : CGSize(width:32,height:33)
        let offset = image == "coin" ? CGSize(width:5,height:6) : image == "phone" ? CGSize(width:1,height:-4) : CGSize(width:4,height:4)
        return HStack(alignment:.top,spacing:10) {
            art.image(image).resizable().frame(width:size.width,height:size.height)
                .offset(x:offset.width,y:offset.height).frame(width:42,height:44,alignment:.topLeading)
            VStack(alignment:.leading,spacing:2) {
                Text(title).font(.custom("Gilroy-SemiBold",size:15.25)).foregroundStyle(.white)
                Text(detail).font(.custom("Gilroy-Medium",size:12.8)).foregroundStyle(Color(hex:0xC9A5E6))
            }.padding(.top,3)
            Spacer(minLength:0)
        }.padding(.leading,16).frame(height:66,alignment:.top)
    }
    private func bonuses(_ t:Double)->some View {
        HStack(spacing:8) {
            ZStack(alignment:.topLeading) {
                RoundedRectangle(cornerRadius:21).fill(Color(hex:0x7A3BBB)).frame(width:157,height:158)
                VStack(alignment:.leading,spacing:8) {
                    Text("Buzz more friends").font(.custom("Gilroy-SemiBold",size:15))
                    VStack(alignment:.leading,spacing:0) {
                        HStack(spacing:0) {
                            Text("Get ")
                            Text("₹5 bonus").foregroundStyle(.white).background(Color(hex:0xF16CA4))
                            Text(" each")
                        }
                        Text("for your first 5 friends")
                    }.font(.custom("Gilroy-Medium",size:12.8)).foregroundStyle(Color(hex:0xC9A5E6))
                }.frame(width:125,alignment:.leading).offset(x:16,y:16)
                Canvas { context,_ in art.illustrations.drawBonus(time:t,context:&context) }.frame(width:157,height:158)
            }.frame(width:157,height:158,alignment:.topLeading).clipped()
            ZStack(alignment:.topLeading) {
                RoundedRectangle(cornerRadius:21).fill(Color(hex:0x7A3BBB)).frame(width:165,height:158)
                VStack(alignment:.leading,spacing:8) {
                    Text("Daily streaks").font(.custom("Gilroy-SemiBold",size:15))
                    Text("Keep the streak alive\nto earn more").font(.custom("Gilroy-Medium",size:12.8)).foregroundStyle(Color(hex:0xC9A5E6))
                }.frame(width:133,alignment:.leading).offset(x:16,y:16)
                HStack(spacing:17) {
                    ForEach(1...7,id:\.self) { day in
                        let motion=signature?.domino(day,t) ?? (y:0.0,rotation:0.0,scale:1.0)
                        VStack(spacing:7) {
                            art.image(day >= 5 ? "daycoin" : "day-\(day)").resizable().frame(width:34,height:34)
                                .scaleEffect(motion.scale).rotationEffect(.degrees(motion.rotation)).offset(y:motion.y)
                            Text("DAY \(day)").font(.custom("Gilroy-ExtraBold",size:11))
                        }.frame(width:34)
                    }
                }.fixedSize(horizontal:true,vertical:false).frame(width:165,height:69,alignment:.topLeading)
                    .offset(x:5+BuzzStreaksMotion.flow(t,art.layout["daysX"] ?? [[0,0],[10.7,-204]]),y:89)
            }.frame(width:165,height:158,alignment:.topLeading).clipped()
        }.foregroundStyle(.white)
    }
}

private struct BuzzStreaksPressStyle:ButtonStyle {
    func makeBody(configuration:Configuration)->some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration:0.09),value:configuration.isPressed)
    }
}
private struct BuzzStreaksRibbon:Shape {
    func path(in r:CGRect)->Path { Path { p in
        p.move(to:.zero)
        p.addLine(to:CGPoint(x:r.width,y:0))
        p.addLine(to:CGPoint(x:r.width*0.94,y:r.height))
        p.addLine(to:CGPoint(x:r.width*0.06,y:r.height))
        p.closeSubpath()
    } }
}
private extension Color {
    init(hex:Int) { self.init(red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255) }
}

private final class BuzzStreaksArt {
    private struct Sprite: Decodable { let size: [Int]; let rect: [Int] }
    let signature = BuzzStreaksSignature()
    let mascots = BuzzStreaksMascots()
    let rewards = BuzzStreaksRewards()
    let illustrations = BuzzStreaksIllustrations()
    let textMotion = BuzzStreaksTextMotion()
    let images:[String:UIImage]
    let layout:[String:[[Double]]]
    init() {
        for name in ["Medium","SemiBold","Bold","ExtraBold"] {
            if let url=Bundle.main.url(forResource:"BuzzStreaks-Gilroy-\(name)",withExtension:"ttf") { CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil) }
        }
        guard let spriteURL = Bundle.main.url(forResource:"BuzzStreaks-artwork",withExtension:"json"),
              let spriteData = try? Data(contentsOf:spriteURL),
              let sprites = try? JSONDecoder().decode([String:Sprite].self,from:spriteData)
        else { preconditionFailure("BuzzStreaks requires generated artwork crop metadata.") }
        var found:[String:UIImage]=[:]
        for name in ["background","wordmark","invite","phone","coin","daycoin","start-label","day-1","day-2","day-3","day-4"] {
            if let url=Bundle.main.url(forResource:"BuzzStreaks-\(name)",withExtension:"png"),let im=UIImage(contentsOfFile:url.path) {
                if let sprite=sprites[name] {
                    let rect=sprite.rect
                    guard let cropped=im.cgImage?.cropping(to:CGRect(x:rect[0],y:rect[1],width:rect[2],height:rect[3]))
                    else { preconditionFailure("Cannot crop generated BuzzStreaks artwork: \(name)") }
                    let image=UIImage(cgImage:cropped)
                    found[name]=image.preparingForDisplay() ?? image
                } else { found[name]=im.preparingForDisplay() ?? im }
            }
        }
        images=found
        let url=Bundle.main.url(forResource:"BuzzStreaks-poses",withExtension:"json")
        let dict=url.flatMap { try? Data(contentsOf:$0) }.flatMap { try? JSONSerialization.jsonObject(with:$0) as? [String:Any] }
        layout=dict?["layout"] as? [String:[[Double]]] ?? [:]
    }
    func image(_ name:String)->Image { if let im=images[name] {return Image(uiImage:im)}; return Image(systemName:"exclamationmark.triangle") }
    func draw(_ name:String,in rect:CGRect,context:inout GraphicsContext) { context.draw(image(name),in:rect) }
    func character(_ kind:String,time:Double,context:inout GraphicsContext) {
        mascots.draw(kind,time:time,context:&context)
    }
}

/// Monotone cubic flow from rossark-motion-skill (MIT), one clock for the whole scene.
private enum BuzzStreaksMotion {
    static let panel:[[Double]]=[[0,780],[4.15,780],[4.35,550],[4.65,445],[7.25,445],[7.5,295],[7.75,246],[10.7,246]]
    static let header:[[Double]]=[[0,0],[7.25,0],[7.75,-20],[10.7,-20]]
    static let bonus:[[Double]]=[[0,0],[7.55,0],[7.85,1],[10.7,1]]
    static let caption:[[Double]]=[[0,1],[4,1],[4.25,0],[10.7,0]]
    static func flow(_ t:Double,_ k:[[Double]])->Double {
        if t<=k[0][0] {return k[0][1]};if t>=k[k.count-1][0] {return k[k.count-1][1]}
        var i=0;while k[i+1][0]<t {i+=1}
        func slope(_ j:Int)->Double {(k[j+1][1]-k[j][1])/(k[j+1][0]-k[j][0])}
        func tangent(_ j:Int)->Double {
            if j==0 || j==k.count-1 {return 0};let a=slope(j-1),b=slope(j);if a*b<=0 {return 0}
            let h0=k[j][0]-k[j-1][0],h1=k[j+1][0]-k[j][0],w0=2*h1+h0,w1=h1+2*h0
            return (w0+w1)/(w0/a+w1/b)
        }
        let h=k[i+1][0]-k[i][0],s=(t-k[i][0])/h,s2=s*s,s3=s2*s
        return (2*s3-3*s2+1)*k[i][1]+(s3-2*s2+s)*h*tangent(i)+(-2*s3+3*s2)*k[i+1][1]+(s3-s2)*h*tangent(i+1)
    }
}
