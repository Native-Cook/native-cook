import Foundation

struct PotionVialUploadFrame {
  var t: Double
  var p: Double
  var front: Double
  var bulge: Double
  var flow: Double
  var glow: Double
  var gone: Double
  var liquidOn: Double
  var mist: Double
  var streak: Double
  var splash: Double
  var burst: Double
  var ghostX: Double
  var ghostY: Double
  var ghostScale: Double
  var ghostAlpha: Double
  var ghostRot: Double
  var slotEmpty: Double
  var hot: Double
  var gemX: Double
  var gemY: Double
  var gemRot: Double
  var gemVis: Double
  var spX: Double
  var spY: Double
  var spHead: Double
  var spWalk: Double
  var spPhase: Double
  var spIdle: Double
  var spHop: Double
  var restA: Double
  var readyA: Double
  var counterA: Double
  var doneA: Double
  var cardState: Int
  var cardDoneA: Double
  var shake: Double
  var pixOut: Double
  var pixX: Double
  var pixY: Double
  var pixIn: Double
  var dp: Double
  var silkY: Double
  var shine: Double
  var trail: Double
  var cloud: Double
  var cloudA: Double
  var fade: Double
}

extension PotionVialUploadMotion {
  typealias V = PotionVialUploadTheme.V
  static let SPIDER_REST = PotionVialUploadPoint(x: 39, y: -18)
  static let NECK_TOP = PotionVialUploadPoint(x: 296, y: -18)
  struct PotionVialUploadLink { var x, y, a: Double }
  static func chainLinks(_ ax: Double, _ ay: Double, _ bx: Double, _ by: Double, _ length: Double) -> [PotionVialUploadLink] {
    let d = max(0.01,hypot(bx-ax,by-ay)), slack = max(0,length-d), depth = sqrt(3*d*slack/8)
    let pts: [PotionVialUploadPoint] = (0...28).map { i in
      let s = Double(i)/28
      return .init(x: mix(ax,bx,s),y: mix(ay,by,s)+depth*4*s*(1-s))
    }
    var links: [PotionVialUploadLink] = [], acc = 0.0, next = 2.2
    for i in 1..<pts.count {
      let sx = pts[i].x-pts[i-1].x, sy = pts[i].y-pts[i-1].y, l = hypot(sx,sy)
      while acc+l >= next {
        let k = (next-acc)/l
        links.append(.init(x:pts[i-1].x+sx*k,y:pts[i-1].y+sy*k,a:atan2(sy,sx))); next += 4.4
      }
      acc += l
    }
    return links
  }
  static func spiderPath(_ t: Double) -> PotionVialUploadPoint {
    if t < 2.85 { return SPIDER_REST }
    let k = smooth(seg(t,2.85,3.33)), wander = sin(2 * .pi*(t-2.73)/2.4)*11
    let fx = min(mix(18.6,313,progressAt(t))-3,286)
    let tx = mix(39,max(fx,39),k), ty = mix(-18,wander,k), c = easeInOut(seg(t,7.6,8.15))
    return .init(x:mix(tx,296,c),y:mix(ty,-18,c))
  }
  static func ghostPath(_ k: Double, _ cardX: Double, _ cardY: Double) -> PotionVialUploadPoint {
    let u = 1-k
    return .init(x:u*u*cardX+2*u*k*372+k*k*357,y:u*u*cardY+2*u*k*(cardY-110)+k*k*(-50))
  }
  static func cardIcon(_ g: PotionVialUploadGeo) -> PotionVialUploadPoint { g.icon }
  static func frameAt(_ t: Double, _ d: PotionVialUploadDriver, _ now: Double, _ g: PotionVialUploadGeo, _ still: Bool) -> PotionVialUploadFrame {
  let p = progressAt(t);
  let filling = t >= T.splash;
  let front = mix(V.x0 + V.wall - 6, V.neck + 2, p);
  let dp = (progressAt(t + 0.05) - progressAt(t - 0.05)) * 10;
  let bulge = 5 + min(dp * 9, 4);
  
  let glow = filling ? 0.12 + 0.88 * smooth(seg(p, 0.35, 1)) : 0;

  let icon = cardIcon(g);
  let fingerFile = d.mode == 1 && !d.fileX.isNaN;
  var ghostX = icon.x;
  var ghostY = icon.y;
  var ghostScale = 1.0;
  var ghostAlpha = 0.0;
  var ghostRot = 0.0;
  var slotEmpty = 0.0;
  var hot = 0.0;
  var mist = 0.0;
  if (fingerFile) {
    ghostX = d.fileX;
    ghostY = d.fileY;
    ghostAlpha = 1;
    slotEmpty = 1;
    let dist = hypot(ghostX - MOUTH.x, ghostY - MOUTH.y);
    hot = 1 - smooth((dist - NEAR_R * 0.55) / (NEAR_R * 0.6));
    mist = d.gem.corked == 0 ? 0.35 + 0.65 * hot : 0;
    ghostRot = -0.12;
  } else if (d.fileBack < 1) {
    let k = easeOutBack(d.fileBack);
    ghostX = mix(d.fileFromX, icon.x, k);
    ghostY = mix(d.fileFromY, icon.y, k);
    ghostAlpha = 1;
    slotEmpty = 1 - smooth(seg(d.fileBack, 0.75, 1));
  } else if (t >= T.lift && t < T.drop + 0.14) {
    
    let fly = easeInOut(seg(t, T.lift, T.near + 0.15));
    let pos = ghostPath(fly, icon.x, icon.y);
    let hover = seg(t, T.near + 0.15, T.drop);
    ghostX = pos.x + sin(hover * Double.pi) * 6;
    ghostY = pos.y - sin(hover * Double.pi * 2) * 3;
    ghostAlpha = 1;
    ghostRot = -0.14 * smooth(seg(t, T.lift, T.lift + 0.3));
    slotEmpty = smooth(seg(t, T.lift, T.lift + 0.15));
    hot = smooth(seg(t, T.near - 0.2, T.near));
    mist = smooth(seg(t, T.near, T.near + 0.2));
    if (t >= T.drop) {
      
      let k = easeIn(seg(t, T.drop, T.drop + 0.13));
      let sx = !d.dropX.isNaN ? d.dropX : ghostX;
      let sy = d.dropY == d.dropY ? d.dropY : ghostY;
      ghostX = mix(sx, MOUTH.x + 8, k);
      ghostY = mix(sy, MOUTH.y - 4, k);
      ghostScale = mix(1, 0.45, k);
      ghostAlpha = 1 - smooth(seg(t, T.drop + 0.07, T.drop + 0.13));
    }
  } else if (t >= T.drop + 0.14 && !d.dropX.isNaN && t < T.drop + 0.3) {
    ghostAlpha = 0;
  }
  
  if (!fingerFile && !d.dropX.isNaN && t >= T.drop && t < T.drop + 0.14) {
    let k = easeIn(seg(t, T.drop, T.drop + 0.13));
    ghostX = mix(d.dropX, MOUTH.x + 8, k);
    ghostY = mix(d.dropY, MOUTH.y - 4, k);
    ghostScale = mix(1, 0.45, k);
    ghostAlpha = 1 - smooth(seg(t, T.drop + 0.07, T.drop + 0.13));
    ghostRot = -0.12;
    slotEmpty = 1;
  }
  
  if !fingerFile { mist = mist * (1 - smooth(seg(t, T.drop + 0.13, T.drop + 0.22))) }
  if t >= T.done && t < T.done + 1.2 { mist = max(mist, 0.4 * (1 - smooth(seg(t, T.done + 0.1, T.done + 1.1)))) }
  if t >= T.drop + 0.1 && !fingerFile && d.fileBack >= 1 { slotEmpty = t < T.fill ? 1 : 1 - smooth(seg(t, T.fill, T.fill + 0.3)) }

  
  let gm = d.gem;
  let gemX = ANCHOR.x + gm.x;
  let gemY = ANCHOR.y + gm.y;
  var sp: PotionVialUploadPoint = spiderPath(t);
  var sp2: PotionVialUploadPoint = spiderPath(max(0, t - 0.06));
  var head = 1.75;
  var walk = 0.0;
  if (t >= T.climb[0] && gm.corked == 0) {
    
    let bx = gemX + sin(gm.rot) * 16;
    let by = gemY - cos(gm.rot) * 16;
    let links = chainLinks(ANCHOR.x, ANCHOR.y, bx, by, V.chain - 16);
    let k = easeInOut(seg(t, T.climb[0], T.climb[1]));
    let n = links.count;
    if (t < T.climb[1] && n > 1) {
      let fi = k * Double(n - 1);
      let a = links[Int(floor(fi))];
      let b = links[min(n - 1, Int(floor(fi)) + 1)];
      let e = fi - floor(fi);
      let k0 = smooth(seg(t, T.climb[0], T.climb[0] + 0.12));
      sp = PotionVialUploadPoint(x: mix(NECK_TOP.x, mix(a.x, b.x, e), k0), y: mix(NECK_TOP.y, mix(a.y, b.y, e), k0));
      head = atan2(b.y - a.y, b.x - a.x);
      walk = 1;
    } else {
      sp = PotionVialUploadPoint(x: bx - sin(gm.rot) * 4, y: by + cos(gm.rot) * 4);
      head = Double.pi / 2 + gm.rot;
    }
    sp2 = sp;
  } else {
    let vx = (sp.x - sp2.x) / 0.06;
    let vy = (sp.y - sp2.y) / 0.06;
    let speed = hypot(vx, vy);
    walk = smooth((speed - 4) / 22);
    if t >= T.splash && t < 7.6 { head = 0.05 }
    else if t >= 7.6 { head = mix(0.05, Double.pi * 0.75, smooth(seg(t, 8.0, 8.3))) }
    let velHead = atan2(vy, vx);
    var dh = velHead - head;
    dh -= roundJS(dh / (Double.pi * 2)) * Double.pi * 2;
    head += dh * walk;
  }
  
  let ka = now - d.kickAt;
  let kick = ka >= 0 && ka < 1.2 ? sin(min(ka / 0.35, 1) * Double.pi * 0.5) * exp(-max(0, ka - 0.35) * 4) : 0;
  let hopK = ka >= 0 && ka < 0.4 ? sin((ka / 0.4) * Double.pi) : 0;

  
  let restA = still ? 0 : 1 - smooth(seg(t, T.near - 0.1, T.near));
  var readyA = still ? 0 : smooth(seg(t, T.near - 0.1, T.near)) * (1 - smooth(seg(t, T.near + 0.6, T.near + 0.7)));
  let dropA = still ? 0 : smooth(seg(t, T.near + 0.6, T.near + 0.7)) * (1 - smooth(seg(t, 2.07, 2.2)));
  if fingerFile { readyA = smooth((hot - 0.4) / 0.4) }
  let counterA = still ? 1 : smooth(seg(t, 2.07, 2.2));
  let doneA = still ? 1 : smooth(seg(t, T.fill, T.fill + 0.1));
  let cardState = t >= T.fill ? 2 : t >= T.drop ? 1 : 0;
  let cardDoneA = cardState == 2 ? smooth(seg(t, T.fill, T.fill + 0.3)) : 0;

  
  let sinceHome = now - d.gem.lastHome - d.gem.homeDur;
  let shake = sinceHome >= 0 && sinceHome < 0.4 ? sin(sinceHome * 60) * exp(-sinceHome * 11) * 1.2 : 0;
  let fade = still
    ? 0
    : t < T.fadeIn[1]
      ? 1 - smooth(seg(t, T.fadeIn[0], T.fadeIn[1]))
      : smooth(seg(t, T.fade[0], T.fade[1]));

  return PotionVialUploadFrame(
    t: t,
    p: p,
    front: front,
    bulge: bulge,
    flow: p * 120,
    glow: glow,
    gone: 0,
    liquidOn: filling ? 1 : 0,
    mist: (restA + readyA + dropA >= 0 ? 1 : 0) * mist,
    streak: -1,
    splash: -1,
    burst: t >= T.fill && t < T.fill + 1.4 ? t - T.fill : -1,
    ghostX: ghostX,
    ghostY: ghostY,
    ghostScale: ghostScale,
    ghostAlpha: ghostAlpha,
    ghostRot: ghostRot,
    slotEmpty: slotEmpty,
    hot: hot,
    gemX: gemX,
    gemY: gemY,
    gemRot: gm.rot,
    gemVis: mix(-atan2(gm.x, gm.y) * 0.9, -0.35, gm.corked == 1 ? (gm.homeAt < 0 ? 1 : clamp01((now - gm.homeAt) / gm.homeDur)) : 0),
    spX: sp.x + kick * 22 * d.kickDir,
    spY: sp.y,
    spHead: head,
    spWalk: max(walk, kick > 0.02 && ka < 0.5 ? 1 : 0),
    spPhase: (sp.x + sp.y * 0.6) / 9 + kick * 4.5,
    spIdle: t,
    spHop: hopK,
    restA: max(restA, dropA),
    readyA: readyA,
    counterA: counterA,
    doneA: doneA,
    cardState: cardState,
    cardDoneA: cardDoneA,
    shake: shake,
    pixOut: -1,
    pixX: 0,
    pixY: 0,
    pixIn: -1,
    dp: max(0, dp),
    silkY: Double.nan,
    shine: -1,
    trail: 0,
    cloud: t >= T.streak[0] && t < T.streak[1] + 0.4 ? seg(t, T.streak[0], T.streak[1]) : -1,
    cloudA: 1 - smooth(seg(t, T.streak[1] - 0.05, T.streak[1] + 0.4)),
    fade: fade
  )
  }
}
