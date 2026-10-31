import SwiftUI

// Native text retains the RN fonts, line boxes, tracking, and accessibility roles.
struct PixelTrophyUnlockCopy: View {
    let size: CGSize
    let top: Double
    let bottom: Double
    let earned: Bool
    var rowY: Double { top+186 }
    var gridY: Double { top+444 }
    var body: some View {
        ZStack(alignment:.topLeading) {
            Text("Achievements")
                .font(.custom("LilitaOne",fixedSize:32)).tracking(0.3)
                .foregroundStyle(.white).frame(width:size.width)
                .offset(y:top+26).accessibilityAddTraits(.isHeader)
            VStack(spacing:12) {
                HStack {
                    Text(earned ? "5 / 67 earned" : "4 / 67 earned").monospacedDigit()
                    Spacer(minLength:0)
                    Text(earned ? "7%" : "5%").monospacedDigit()
                }.font(.custom("Inter-Medium",fixedSize:17)).foregroundStyle(.white.opacity(0.82))
                    .offset(y:1.0/3) // Match RN's pixel-rounded native text baseline.
                ZStack(alignment:.leading) {
                    Capsule().fill(Color(red:36/255,green:51/255,blue:28/255))
                    Capsule().fill(Color(red:110/255,green:224/255,blue:58/255)).frame(width:(size.width-48)*Double(earned ? 5 : 4)/67)
                }.frame(height:16)
            }.frame(width:size.width-48).offset(x:24,y:top+84)
            Text("Recently earned").font(.custom("Inter-SemiBold",fixedSize:16)).offset(y:-1.0/3)
                .foregroundStyle(.white.opacity(0.85)).padding(.horizontal,18).frame(height:36)
                .background(.white.opacity(0.12),in:Capsule()).frame(width:size.width).offset(y:rowY-44)
            HStack(spacing:14) {
                Capsule().fill(Color(red:1,green:106/255,blue:58/255)).frame(height:3)
                Text("COMMON").font(.custom("LilitaOne",fixedSize:16)).tracking(1).foregroundStyle(.white).fixedSize().offset(y:1.0/3)
                Capsule().fill(Color(red:1,green:194/255,blue:58/255)).frame(height:3)
            }.frame(width:size.width-48).offset(x:24,y:gridY-46)
            ForEach(0..<8,id:\.self) { i in
                let p = CGPoint(x:24+Double(i%4)*(80+(size.width-368)/3),y:gridY+Double(i/4)*156)
                let lit = i == 0 || i == 1 && earned
                Text(PixelTrophyUnlockTheme.names[i]).font(.custom("Inter-SemiBold",fixedSize:13))
                    .lineSpacing(16-15.7294921875).multilineTextAlignment(.center)
                    .foregroundStyle(.white.opacity(lit ? 1 : 0.4))
                    .frame(width:100,alignment:.top).fixedSize(horizontal:false,vertical:true)
                    .offset(x:p.x-10,y:p.y+106+1.0/3)
                    .accessibilityLabel("\(PixelTrophyUnlockTheme.names[i]), \(lit ? "earned" : "locked")")
                    .accessibilityAddTraits(.isButton)
            }
            Text("Close").font(.custom("Inter-SemiBold",fixedSize:15)).foregroundStyle(.white).offset(y:-1.0/3)
                .padding(.horizontal,24).frame(height:40)
                .background(Color(red:60/255,green:60/255,blue:66/255).opacity(0.92),in:Capsule())
                .frame(width:size.width).offset(y:size.height-bottom-54)
                .accessibilityLabel("Close").accessibilityAddTraits(.isButton)
        }
        .frame(width:size.width,height:size.height,alignment:.topLeading)
        .allowsHitTesting(false)
    }
}
