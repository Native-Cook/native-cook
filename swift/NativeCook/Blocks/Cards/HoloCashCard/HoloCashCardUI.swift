import SwiftUI

// Native Text preserves the source's type, fixed point sizes and accessibility reading order.
struct HoloCashCardText: View {
    let value: String
    var size = 14.0
    var weight = "Bold"
    var color: UInt32 = 0x111111
    var line: Double? = nil
    var tracking = 0.0
    var tabular = false
    var body: some View {
        let name = weight == "Anton" ? "Anton-Regular" : (weight == "Bold" ? "Inter24pt-Bold" : "Inter-\(weight)")
        let nativeText = Text(value).font(.custom(name,fixedSize:size))
        (tracking == 0 ? nativeText : nativeText.kerning(tracking))
            .holoCashCardMonospacedDigitIf(tabular)
            .foregroundStyle(HoloCashCardResources.color(color))
            .fixedSize(horizontal:true,vertical:true)
            .frame(height:line.map { CGFloat($0) })
            .offset(y:baselineOffset)
    }
    // Match React Native's fixed line-height baseline placement; keep the layout boxes intact.
    private var baselineOffset: Double {
        if size == 38 && line == 40 { return -8.0/3 }
        if value == "," && size == 38 { return 3 }
        if weight == "Anton" && line == 20 { return -10.0/3 }
        if weight == "Anton" && size == 20 { return -1.0/3 }
        if size == 7 { return 1.0/3 }
        if weight == "Medium" && line == 16 { return 1.0/3 }
        if weight == "Bold" && size == 12 && line == 18 { return 1.0/3 }
        return 0
    }
}
extension View {
    @ViewBuilder fileprivate func holoCashCardMonospacedDigitIf(_ enabled:Bool)->some View {
        if enabled { self.monospacedDigit() } else { self }
    }
}
struct HoloCashCardIcon: View {
    let name:String
    var size=22.0
    var color:UInt32=0x111111
    var width=1.9
    var body:some View {
        Canvas { c,_ in HoloCashCardResources.shared.drawIcon(c,name,0,0,size,color,width) }
            .frame(width:size,height:size).accessibilityHidden(true)
    }
}
struct HoloCashCardBar: View {
    let l:HoloCashCardLayout
    let hidden:Bool
    let hide:()->Void
    var body:some View {
        HStack {
            HoloCashCardText(value:"LC",size:11,color:0xffffff)
                .frame(width:30,height:30).background(HoloCashCardResources.color(0x111111),in:RoundedRectangle(cornerRadius:8))
                .rotationEffect(.degrees(-6))
            Spacer()
            HStack(spacing:6) {
                Button(action:hide) { HoloCashCardIcon(name:"eye").frame(width:40,height:40).background(.white,in:RoundedRectangle(cornerRadius:12)).contentShape(Rectangle().inset(by:-8)) }
                    .accessibilityLabel(hidden ? "Show balance":"Hide balance")
                Button(action:{}) { HoloCashCardIcon(name:"chat").frame(width:40,height:40).background(.white,in:RoundedRectangle(cornerRadius:12)).contentShape(Rectangle().inset(by:-8)) }
                    .accessibilityLabel("Support chat")
            }.buttonStyle(.plain)
        }.frame(width:l.cw,height:40).offset(x:18,y:l.top)
    }
}
struct HoloCashCardBalance: View {
    let l:HoloCashCardLayout
    let s:HoloCashCardMotion.State
    let boost:()->Void
    var body:some View {
        VStack(spacing:0) {
            HStack {
                HStack(spacing:6) {
                    HoloCashCardText(value:"Balance")
                    HoloCashCardIcon(name:"info",size:15,color:0x8a8a8f,width:2)
                }
                Spacer(minLength:0)
                HStack(spacing:6) {
                    HoloCashCardText(value:"Rewards of",size:13,weight:"Medium")
                    HStack(spacing:0) {
                        VStack(spacing:0) {
                            HoloCashCardText(value:"3",size:12,color:0xffffff,line:18)
                            HoloCashCardText(value:"8",size:12,color:0xffffff,line:18)
                        }
                        .offset(y:-((s.apy-3)/5)*18)
                        .frame(height:18,alignment:.top).clipped()
                        HoloCashCardText(value:"%",size:12,color:0xffffff,line:18)
                    }.padding(.horizontal,4).background(HoloCashCardResources.color(0xe93ad8),in:RoundedRectangle(cornerRadius:4))
                    HoloCashCardText(value:"APY",size:13,weight:"Medium")
                }
            }
            .frame(height:18)
            ZStack(alignment:.bottom) {
                HStack(alignment:.bottom,spacing:0) {
                    HoloCashCardText(value:"$",size:20,line:30).padding(.trailing,2)
                    digit(0,38,40)
                    HoloCashCardText(value:",",size:38,tracking:-1,tabular:true)
                        // Match RCTText's punctuation ink bounds without changing its advance.
                        .scaleEffect(x:0.95,y:0.875,anchor:.bottomTrailing)
                    digit(1,38,40); digit(2,38,40); digit(3,38,40)
                    HStack(spacing:0) {
                        HoloCashCardText(value:".",size:20,line:24,tabular:true)
                        digit(4,20,24); digit(5,20,24)
                        HStack(spacing:0) {
                            digit(6,20,24,true); digit(7,20,24,true); digit(8,20,24,true)
                        }.offset(y:s.dip*6).opacity((1-s.dip)*(1-s.hide))
                    }.padding(.bottom,4)
                }.frame(height:42,alignment:.bottom).opacity(1-s.hide)
                HoloCashCardText(value:"$ • • • • •",size:30,tracking:2)
                    .padding(.bottom,4).opacity(s.hide)
            }
            .frame(maxWidth:.infinity).frame(height:42).padding(.top,12)
            .accessibilityElement(children:.ignore)
            .accessibilityLabel(s.hideTarget==1 ? "Balance hidden":"Balance 5,020 dollars and 20 cents, accruing")
            HoloCashCardText(value:"Total rewarded: $100",size:12,weight:"Medium",color:0x8a8a8f,line:16)
            HoloCashCardText(value:"Balance is held in full in dUSD",size:12,weight:"Medium",color:0x8a8a8f,line:16)
            Spacer(minLength:0)
        }
        .padding(.horizontal,14).padding(.top,12)
        .frame(width:l.cw,height:126)
        .background(.white,in:RoundedRectangle(cornerRadius:16))
        .overlay(alignment:.topTrailing) {
            Button(action:boost) {
                VStack(spacing:0) {
                    HoloCashCardText(value:s.boost ? "BOOSTED":"BOOST TO",size:7)
                    HoloCashCardText(value:s.boost ? "ON":"8%",size:18,weight:"Anton",line:20)
                }.padding(.horizontal,7).padding(.vertical,4)
                    .background(HoloCashCardResources.color(0xf5ec3d),in:RoundedRectangle(cornerRadius:3))
                    .contentShape(Rectangle().inset(by:-10))
            }
            .buttonStyle(.plain).rotationEffect(.degrees(12-s.boostK*6)).scaleEffect(s.stickerScale)
            .accessibilityLabel("Boost rewards to 8 percent").accessibilityValue(s.boost ? "On":"Off").accessibilityAddTraits(.isToggle)
            .offset(x:-18,y:42)
        }
        .offset(x:18,y:l.balanceY)
    }
    private func digit(_ k:Int,_ size:Double,_ lh:Double,_ grey:Bool=false)->some View {
        let d=s.odo.disp[k], m=(d.truncatingRemainder(dividingBy:10)+10).truncatingRemainder(dividingBy:10)
        return VStack(spacing:0) {
            ForEach(0..<11,id:\.self) { n in
                HoloCashCardText(value:"\(n%10)",size:size,color:grey ? 0xa5a5aa:0x111111,line:lh,tracking:size==38 ? -1:0,tabular:true)
            }
        }.offset(y:-m*lh).frame(height:lh,alignment:.top).clipped()
    }
}
struct HoloCashCardLoyalty: View {
    let l:HoloCashCardLayout
    var body:some View {
        VStack(alignment:.leading,spacing:8) {
            HStack {
                HoloCashCardText(value:"Loyalty program").offset(y:-1.0/3)
                Spacer(minLength:0)
                HStack(spacing:6) {
                    HoloCashCardText(value:"1%",size:12,color:0xffffff,line:18)
                        .padding(.horizontal,4).background(HoloCashCardResources.color(0xe93ad8),in:RoundedRectangle(cornerRadius:4))
                    HoloCashCardText(value:"Cashback",size:13,weight:"Medium")
                }
            }
            HStack(spacing:6) {
                HoloCashCardText(value:"2,000",size:20,tabular:true).offset(y:1.0/3)
                Circle().fill(HoloCashCardResources.color(0x2d6bff))
                    .overlay(Circle().strokeBorder(HoloCashCardResources.color(0xbcd0ff),lineWidth:2)).frame(width:12,height:12).offset(y:1.0/3)
            }
            Spacer(minLength:0)
        }.padding(.horizontal,14).padding(.top,12).frame(width:l.cw,height:76)
            .background(.white,in:RoundedRectangle(cornerRadius:16)).offset(x:18,y:l.loyalY)
    }
}
struct HoloCashCardTabs: View {
    let l:HoloCashCardLayout
    var body:some View {
        let gap=(l.cw-64-88)/5
        ZStack(alignment:.topLeading) {
            RoundedRectangle(cornerRadius:22).fill(.white).frame(width:l.cw,height:64)
            HoloCashCardIcon(name:"wallet").offset(x:gap/2,y:21)
            HoloCashCardIcon(name:"grid",color:0x9a9aa0).offset(x:gap*1.5+22,y:21)
            HoloCashCardText(value:"SEND",size:20,weight:"Anton",tracking:0.5)
                .frame(width:66,height:66)
                .background(HoloCashCardResources.color(0xf04fd8),in:Circle())
                .overlay(Circle().strokeBorder(.white,lineWidth:3))
                .rotationEffect(.degrees(-10)).offset(x:l.cw/2-33,y:-14)
            HoloCashCardIcon(name:"ring",color:0x9a9aa0).offset(x:gap*3.5+108,y:21)
            HoloCashCardIcon(name:"check",color:0x9a9aa0).offset(x:gap*4.5+130,y:21)
        }
        .frame(width:l.cw,height:64,alignment:.topLeading)
        .overlay(alignment:.topTrailing) {
            VStack(spacing:0) {
                HoloCashCardText(value:"REFER",size:8,line:9)
                HoloCashCardText(value:"AND EARN",size:8,line:9)
            }.padding(.horizontal,6).padding(.vertical,4)
                .background(HoloCashCardResources.color(0x5ec8f2))
                .rotationEffect(.degrees(14)).offset(x:6,y:-26).allowsHitTesting(false)
        }
        .offset(x:18,y:l.tabsY)
    }
}
