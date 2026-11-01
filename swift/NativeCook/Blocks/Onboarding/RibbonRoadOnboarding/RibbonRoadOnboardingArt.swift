import SwiftUI
import UIKit
import CoreText

final class RibbonRoadOnboardingArt {
  private weak var cachedDriver: RibbonRoadOnboardingDriver?
  private var revision = -1
  private var contours: [RibbonRoadOnboardingContour] = []
  private var paths: [CGPath?] = [nil,nil]
  private var bounds: [(Double,Double)] = [(-1,-1),(-1,-1)]
  typealias R = RibbonRoadOnboardingResources
  static func icon(_ c: CGContext,_ kind: Int) {
    c.setLineCap(.round); c.setLineJoin(.round)
    c.setStrokeColor((kind==1 ? R.color(0x17171a) : UIColor.white).cgColor)
    c.setLineWidth(kind==0 ? 2.4 : kind==1 ? 2.2 : 2)
    c.beginPath()
    if kind==0 {
      c.move(to:CGPoint(x:5,y:11)); c.addLine(to:CGPoint(x:17,y:11))
      c.move(to:CGPoint(x:12,y:6)); c.addLine(to:CGPoint(x:17,y:11)); c.addLine(to:CGPoint(x:12,y:16))
    } else if kind==1 {
      c.move(to:CGPoint(x:4,y:14)); c.addLine(to:CGPoint(x:14,y:4))
      c.move(to:CGPoint(x:6,y:4)); c.addLine(to:CGPoint(x:14,y:4)); c.addLine(to:CGPoint(x:14,y:12))
    } else {
      c.addEllipse(in:CGRect(x:3.5,y:7.5,width:9,height:9))
      c.move(to:CGPoint(x:12.5,y:12)); c.addLine(to:CGPoint(x:20,y:12))
      c.move(to:CGPoint(x:17,y:12)); c.addLine(to:CGPoint(x:17,y:15.5))
      c.move(to:CGPoint(x:20,y:12)); c.addLine(to:CGPoint(x:20,y:15))
    }
    c.strokePath()
  }
  func ribbons(_ c: CGContext,_ driver: RibbonRoadOnboardingDriver,_ reduced: Bool) {
    typealias M = RibbonRoadOnboardingMotion
    let head=reduced ? 1 : M.easeInOut(M.seg(driver.e,0,1.1))
    let k=M.seg(driver.z,0.25,1.45)
    let tail=driver.z>=0 && !reduced ? k*k*k : 0
    if head<=tail+0.001 { return }
    let resources=R.shared
    if cachedDriver !== driver || revision != driver.field.revision {
      cachedDriver=driver; revision=driver.field.revision
      contours=(0..<2).map { RibbonRoadOnboardingContour(driver.lines[$0],driver.field,$0*170) }
      paths=[nil,nil]
    }
    for ribbon in 0..<2 {
      let contour=contours[ribbon]
      let a=tail*contour.length, b=head*contour.length
      if paths[ribbon]==nil || bounds[ribbon].0 != a || bounds[ribbon].1 != b {
        paths[ribbon]=contour.part(a,b); bounds[ribbon]=(a,b)
      }
      let path=paths[ribbon]!
      c.saveGState()
      c.setLineCap(.round); c.setLineJoin(.round)
      if ribbon==1 {
        // Skia's mask blur has sigma 9. CG's shadow blur is a diameter-like radius.
        c.saveGState()
        // Draw the source outside the clip and translate only its shadow back into place.
        c.setShadow(offset:CGSize(width:0,height:10006),blur:18,color:UIColor.black.withAlphaComponent(0.45).cgColor)
        c.translateBy(x:0,y:-10000)
        c.setStrokeColor(UIColor.black.cgColor)
        c.setLineWidth(50); c.addPath(path); c.strokePath()
        c.restoreGState()
      }
      c.setStrokeColor(R.color(ribbon==0 ? 0x3d34ec : 0xf3f3f1).cgColor)
      c.setLineWidth(46); c.addPath(path); c.strokePath()
      c.setFillColor((ribbon==0 ? UIColor.white : R.color(0x17171a)).cgColor)
      c.textMatrix = .identity
      let L=resources.patternLength, dir=ribbon==0 ? 1.0 : -1.0
      let ph=((dir*driver.phase).truncatingRemainder(dividingBy:L)+L).truncatingRemainder(dividingBy:L)
      var s=ph-L, j=0
      while s<b {
        let adv=resources.advances[j], mid=s+adv/2
        if mid>a+14 && mid<b-14 {
          let (p,t)=contour.position(mid)
          let ox=p.x-t.x*adv/2-t.y*(21*0.7/2)
          let oy=p.y-t.y*adv/2+t.x*(21*0.7/2)
          c.saveGState()
          c.concatenate(CGAffineTransform(a:t.x,b:t.y,c:t.y,d:-t.x,tx:ox,ty:oy))
          var glyph=resources.glyphs[j], point=CGPoint.zero
          CTFontDrawGlyphs(resources.font,&glyph,&point,1,c)
          c.restoreGState()
        }
        s += adv; j=(j+1)%resources.glyphs.count
      }
      c.restoreGState()
    }
  }
}
