import SwiftUI

/// Rasterise the fixed tiles and controls only when the screen geometry changes.
@MainActor
final class DiceBoardRewardsStaticArt: ObservableObject {
  struct Tile {
    let image: Image
    let origin: CGPoint
  }
  @Published private(set) var geometry: DiceBoardRewardsGeometry?
  private(set) var tiles: [Tile] = []
  private(set) var controls: [Image] = []
  func prepare(_ g: DiceBoardRewardsGeometry) {
    guard geometry != g else { return }
    let art = DiceBoardRewardsArt(g:g)
    tiles = (0..<12).compactMap { i in
      let a = DiceBoardRewardsMotion.path[i], p = g.iso(a.0,a.1)
      let x = floor((p.x-g.tw*0.46-2)*3)/3
      let y = floor((p.y+g.th*0.5-g.th*0.46-2)*3)/3
      let size = CGSize(width:ceil((g.tw*0.92+4)*3)/3,height:ceil((g.th*0.92+16)*3)/3)
      let view = Canvas { c,_ in
        var c = c; c.translateBy(x:-x,y:-y)
        art.tile(c,i,0)
      }.frame(width:size.width,height:size.height)
      let renderer = ImageRenderer(content:view); renderer.scale = 3
      guard let cg = renderer.cgImage else { return nil }
      return Tile(image:Image(decorative:cg,scale:3),origin:CGPoint(x:x,y:y))
    }
    controls = (0..<8).compactMap { i in
      let view = Canvas { c,_ in art.controls(c,i/2,i%2 == 1) }.frame(width:g.width,height:g.height)
      let renderer = ImageRenderer(content:view); renderer.scale = 3
      return renderer.cgImage.map { Image(decorative:$0,scale:3) }
    }
    geometry = g
  }
}
