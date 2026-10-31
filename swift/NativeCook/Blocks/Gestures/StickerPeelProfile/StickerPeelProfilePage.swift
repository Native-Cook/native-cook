import UIKit

// RN Text's natural line boxes, in full-screen points. The page is rendered only on layout changes.
enum StickerPeelProfilePage {
    typealias R = StickerPeelProfileResources
    // Yoga rounds ordinary container origins to the nearest pixel, but floors Text origins.
    static func snap(_ value: Double) -> Double {
        let scale = UIScreen.main.scale
        return floor(value*scale+0.5+0.000001)/scale
    }
    static func height(_ size: Double, _ weight: String = "Medium",_ value: String = "Ag") -> Double {
        StickerPeelProfileTypography.label(value,size,weight).size.height
    }
    @discardableResult static func text(_ value: String,_ size: Double,_ weight: String,_ x: Double,_ y: Double,_ color: UInt32 = 0xffffff,_ alpha: Double = 1,_ center: Bool = false) -> Double {
        let label = StickerPeelProfileTypography.label(value,size,weight,color,alpha)
        let scale = UIScreen.main.scale
        let left = center ? x-label.size.width/2 : x
        label.draw(CGPoint(x:floor(left*scale+0.000001)/scale,y:floor(y*scale+0.000001)/scale))
        return label.size.width
    }
    static func round(_ c: CGContext,_ x: Double,_ y: Double,_ w: Double,_ h: Double,_ r: Double,_ color: UInt32) {
        c.addPath(CGPath(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerWidth:r,cornerHeight:r,transform:nil))
        c.setFillColor(R.ui(color).cgColor); c.fillPath()
    }
    static func grad(_ c: CGContext,_ rect: CGRect,_ radius: Double,_ colors: [UInt32],_ diagonal: Bool = false) {
        c.saveGState(); c.addPath(CGPath(roundedRect:rect,cornerWidth:radius,cornerHeight:radius,transform:nil)); c.clip()
        R.gradient(c,colors,rect.origin,CGPoint(x:diagonal ? rect.maxX : rect.minX,y:rect.maxY)); c.restoreGState()
    }
    static func draw(_ c: CGContext,_ size: CGSize,_ top: Double,_ bottom: Double) {
        let w = size.width, h = size.height, cx = w/2
        c.setFillColor(R.ui(0x151517).cgColor); c.fill(CGRect(origin:.zero,size:size))
        for (x,glyph) in [(w-112,"◌"),(w-60,"⋮")] {
            round(c,x,top+10,42,42,14,0x2a2a2e)
            text(glyph,18,"Bold",x+21,top+10+(42-height(18,"Bold",glyph))/2,0xffffff,1,true)
        }
        let nameY = top+214
        text("Rio Vega",24,"Bold",cx,nameY,0xffffff,1,true)
        let handleY = nameY+height(24,"Bold")+2
        text("@riovega",14,"Medium",cx,handleY,0xffffff,0.55,true)
        let friendsY = snap(handleY+height(14)+18)
        let tw = StickerPeelProfileTypography.label("1 friend",15,"SemiBold").size.width
        let fw = 28+26+10+tw
        let friendsX = snap(cx-fw/2)
        round(c,friendsX,friendsY,fw,44,14,0x242427)
        grad(c,CGRect(x:friendsX+14,y:friendsY+9,width:26,height:26),8,[0xffb36a,0xff5a8a])
        text("1 friend",15,"SemiBold",friendsX+50,friendsY+(44-height(15,"SemiBold"))/2)
        let by = top+318
        grad(c,CGRect(x:18,y:by,width:w-36,height:70),16,[0x3a1170,0x7a2fc9,0xb04cff],true)
        let titleY = by+(70-height(18,"Bold")-2-height(13))/2
        text("Music Compatibility",18,"Bold",34,titleY)
        text("Discover your score!",13,"Medium",34,titleY+height(18,"Bold")+2,0xffffff,0.75)
        c.saveGState(); c.translateBy(x:w-18-26-26,y:by+8+30); c.rotate(by:6 * .pi/180)
        round(c,-26,-30,52,60,10,0x5a1fa8)
        text("?",34,"Bold",0,-height(34,"Bold")/2,0x1a0838,1,true); c.restoreGState()
        let ly = by+92
        text("Recently played",15,"SemiBold",18,ly,0xffffff,0.6)
        let more = "See more"
        let mw = StickerPeelProfileTypography.label(more,15,"SemiBold").size.width
        text(more,15,"SemiBold",w-18-mw,ly)
        let songs: [(String,String,[UInt32])] = [("Glass Garden","Mira Sol",[0x2ad4c4,0x3a5bff]),("Night Bus Home","Ode Kim",[0xffcf6a,0xff5a8a]),("Fizz - Remix","Tavi, Rook",[0xff7ac8,0x7a3cff])]
        for (i,s) in songs.enumerated() {
            let y = ly+height(15,"SemiBold")+14+Double(i)*66
            grad(c,CGRect(x:18,y:y,width:52,height:52),6,s.2,true)
            c.setFillColor(R.ui(0xffffff,0.35).cgColor); c.fillEllipse(in:CGRect(x:31,y:y+13,width:26,height:26))
            let rowTextY = snap(y+(52-height(16)-2-height(14))/2)
            text(s.0,16,"Medium",84,rowTextY)
            text(s.1,14,"Medium",84,rowTextY+height(16)+2,0xffffff,0.55)
            let aw = StickerPeelProfileTypography.label("4 hours ago",12,"SemiBold").size.width+24
            round(c,w-18-aw,y+11,aw,30,15,0x242427)
            text("4 hours ago",12,"SemiBold",w-18-aw+12,y+11+(30-height(12,"SemiBold"))/2,0xffffff,0.7)
        }
        // Close is a real Button over the cached page.
        _ = h; _ = bottom
    }
}
