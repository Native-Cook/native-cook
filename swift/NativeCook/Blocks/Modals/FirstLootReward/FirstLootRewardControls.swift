import SwiftUI

struct FirstLootRewardControls:View {
    typealias M=FirstLootRewardMotion
    typealias R=FirstLootRewardResources
    let w:Double, h:Double, top:Double, bottom:Double
    let frame:M.Frame
    @Environment(\.displayScale) private var displayScale
    private static let titleWords=[["You ","just ","earned ","your"],["first ","Loot."]]
    private static let subLeading=20-UIFont.systemFont(ofSize:14.3).lineHeight
    private static let bodyLeading=18-UIFont.systemFont(ofSize:14).lineHeight
    var body:some View {
        let t=frame.t, titleTop=top+351, btnY=h-bottom-68, cardY=btnY-157, cardW=w-40
        let titleA=M.presence(t,1.07,0.38), subA=M.presence(t,1.2,0.32), cardA=M.presence(t,1.33,0.3)
        ZStack(alignment:.topLeading) {
            VStack(spacing:0) {
                ForEach(0..<2) { line in
                    HStack(spacing:0) {
                        ForEach(0..<Self.titleWords[line].count,id:\.self) { word in
                            Text(Self.titleWords[line][word]).font(.system(size:29.5,weight:.regular))
                                .tracking(-0.45).fixedSize().frame(height:34).offset(y:-1/displayScale)
                        }
                    }.frame(height:34)
                }
            }
            .foregroundStyle(.white).frame(width:w-40,height:68,alignment:.top)
            .opacity(titleA).offset(x:20,y:titleTop+(1-titleA)*6)
            Text("Nice start! Keep playing, keep winning, and watch your rewards stack up.")
                .font(.system(size:14.3)).tracking(-0.1).lineSpacing(Self.subLeading)
                .multilineTextAlignment(.center).foregroundStyle(R.color(0x9d9d9d))
                .fixedSize(horizontal:false,vertical:true).padding(.vertical,Self.subLeading/2)
                .frame(width:w-72,height:40,alignment:.top)
                .opacity(subA).offset(x:36,y:titleTop+83+(1-subA)*6+1/displayScale)
            announcement(cardW).compositingGroup().opacity(cardA).offset(x:20,y:cardY+(1-cardA)*5)
            ForEach(0..<2) { i in
                let a=M.presence(t,i == 0 ? 1.47 : 1.55,i == 0 ? 0.2 : 0.25), bw=(w-48)/2
                Text(i == 0 ? "Keep playing" : "View loot")
                    .font(.system(size:17,weight:.semibold)).tracking(-0.2)
                    .foregroundStyle(R.color(i == 0 ? 0xffffff : 0x0b0b0c))
                    .offset(y:-1/displayScale)
                    .frame(width:bw,height:52)
                    .background(R.color(i == 0 ? 0x363638 : 0xfdfdfd),in:Capsule())
                    .scaleEffect(1-(i == 0 ? frame.keepPress : frame.viewPress)*0.04)
                    .compositingGroup()
                    .opacity(a).offset(x:20+Double(i)*(bw+8),y:btnY+(1-a)*10)
            }
        }.frame(width:w,height:h,alignment:.topLeading)
    }
    private func announcement(_ width:Double) -> some View {
        ZStack(alignment:.topLeading) {
            LinearGradient(colors:[R.color(0x4d2586),R.color(0x6a3fa6),R.color(0x8459c3)],startPoint:.bottomLeading,endPoint:.topTrailing)
            Canvas { c,_ in
                var glow=c; glow.addFilter(.blur(radius:0.6))
                glow.stroke(R.shared.swirl,with:.color(R.color(0xd6beff,0.16)),style:StrokeStyle(lineWidth:24,lineCap:.round))
            }
            HStack(spacing:4) {
                Canvas { c,_ in
                    var bell=c; bell.translateBy(x:0,y:10.640625); bell.scaleBy(x:1,y:-1)
                    bell.fill(R.shared.bell,with:.color(.white))
                }.frame(width:12,height:12.3046875)
                Text("Announcement").font(.system(size:12.5,weight:.medium)).foregroundStyle(.white)
            }
            .padding(.leading,8).padding(.trailing,9).frame(height:23)
            .background(.white.opacity(0.13),in:Capsule()).offset(x:16,y:14)
            Text("Something big is coming 🎉")
                .font(.system(size:17)).tracking(-0.2).foregroundStyle(.white)
                .frame(width:width-32,height:22,alignment:.leading).offset(x:16,y:54)
            Text("Ember Rift drops October 24. Get ready to play, compete, and earn exclusive Loot.")
                .font(.system(size:14)).tracking(-0.1).lineSpacing(Self.bodyLeading)
                .foregroundStyle(R.color(0xf0e8ff,0.84)).fixedSize(horizontal:false,vertical:true)
                .padding(.vertical,Self.bodyLeading/2).frame(width:width-32,alignment:.topLeading).offset(x:16,y:81)
        }.frame(width:width,height:133,alignment:.topLeading).clipShape(RoundedRectangle(cornerRadius:20,style:.circular))
    }
}
