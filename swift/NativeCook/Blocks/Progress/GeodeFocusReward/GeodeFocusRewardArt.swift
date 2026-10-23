import SwiftUI
import simd

/// A small projected solid mesh. Artwork is decoded once; no paths/noise/blur are generated into bitmaps per frame.
enum GeodeFocusRewardArt {
    private typealias M = GeodeFocusRewardMath
    private typealias G = GeodeFocusRewardGeometry
    private struct Face {
        var world: [SIMD3<Double>]
        var uv: [CGPoint]
        var cut: Bool
        var alpha: Double
        var depth: Double
        var shade: Double
    }
    static func draw(_ context: inout GraphicsContext, pose: GeodeFocusRewardHeroPose) {
        let resources = GeodeFocusRewardResources.shared
        let floorLayer = context
        floorLayer.draw(resources.floorLight, in: CGRect(x:46.5,y:544,width:300,height:100))
        floorLayer.draw(resources.contact, in: CGRect(x:144.5,y:577,width:104,height:36))

        var ctx = context
        // Floor/crescent are outside this debris clip; the parent also clips the complete card.
        ctx.clip(to: Path(CGRect(x:16,y:250,width:361,height:350)))
        ctx.translateBy(x:196.5,y:446+pose.globalY)
        ctx.scaleBy(x:pose.globalScale,y:pose.globalScale)
        ctx.translateBy(x:-196.5,y:-446)

        var glow = ctx
        glow.opacity = pose.glowGain
        glow.draw(resources.glow,in:CGRect(x:76.5,y:283,width:240,height:310))

        let geometry=G.shared
        let order=(0..<12).sorted { lhs,rhs in
            let a=M.rotate(pose.pieces[lhs].center,SIMD3(pose.manualYaw,0,0))
            let b=M.rotate(pose.pieces[rhs].center,SIMD3(pose.manualYaw,0,0))
            return a.z<b.z
        }
        var drewCore=false
        for i in order {
            let depth=M.rotate(pose.pieces[i].center,SIMD3(pose.manualYaw,0,0)).z
            if !drewCore && depth >= -16 { drawCore(&ctx,pose:pose,resources:resources);drewCore=true }
            if pose.pieces[i].alpha>0.0001 {drawPiece(&ctx,index:i,pose:pose,resources:resources,geometry:geometry)}
        }
        if !drewCore {drawCore(&ctx,pose:pose,resources:resources)}
        if pose.crumbAmount>0.0001 {drawCrumbs(&ctx,pose:pose)}
    }
    private static func drawCore(_ context:inout GraphicsContext,pose:GeodeFocusRewardHeroPose,resources:GeodeFocusRewardResources) {
        let yaw=M.clamp(pose.coreYaw+pose.manualYaw,-26,26),index=(yaw+26)/4
        let a=min(13,max(0,Int(floor(index)))),b=min(13,a+1),q=index-Double(a)
        var ctx=context
        ctx.translateBy(x:196.5,y:438);ctx.scaleBy(x:pose.coreScale,y:pose.coreScale)
        // Weighted premultiplied frames add inside an isolated layer, including their silhouette coverage.
        ctx.drawLayer { layer in
            if b == a || q <= 0 {layer.draw(resources.cores[a],in:resources.coreRect)}
            else {
                var first=layer;first.opacity=1-q;first.draw(resources.cores[a],in:resources.coreRect)
                var second=layer;second.blendMode = .plusLighter;second.opacity=q
                second.draw(resources.cores[b],in:resources.coreRect)
            }
        }
    }
    private static func transformed(_ point:SIMD3<Double>,piece:GeodeFocusRewardMeshPiece,state:GeodeFocusRewardPiecePose,yaw:Double)->SIMD3<Double> {
        M.rotate(state.center+M.rotate(point-piece.center,state.rotation),SIMD3(yaw,0,0))
    }
    private static func project(_ v:SIMD3<Double>)->CGPoint {
        let k=760/(760-v.z);return CGPoint(x:196.5+v.x*k,y:446+v.y*k)
    }
    private static func drawPiece(_ context:inout GraphicsContext,index:Int,pose:GeodeFocusRewardHeroPose,resources:GeodeFocusRewardResources,geometry:GeodeFocusRewardGeometry) {
        let piece=geometry.pieces[index],state=pose.pieces[index],light=simd_normalize(SIMD3(-0.5,-0.7,1.0))
        var faces:[Face]=[]
        for triangle in piece.triangles {
            let vertices=[triangle.a,triangle.b,triangle.c]
            let world=vertices.map {transformed($0.point,piece:piece,state:state,yaw:pose.manualYaw)}
            let normal=simd_normalize(simd_cross(world[1]-world[0],world[2]-world[0]))
            let center=(world[0]+world[1]+world[2])/3
            if simd_dot(normal,SIMD3(-center.x,-center.y,760-center.z))<=0 {continue}
            let diffuse=max(0,simd_dot(normal,light))
            faces.append(.init(world:world,uv:vertices.map(\.uv),cut:triangle.cut,alpha:state.alpha,depth:center.z,
                shade:triangle.cut ? 0.63+0.36*diffuse : 0.78+0.22*diffuse))
        }
        faces.sort {$0.depth<$1.depth}
        // Composite the solid before applying its late fade, preventing transparent seams between its faces.
        var group=context;group.opacity=state.alpha
        group.drawLayer { layer in
            for face in faces {paintTriangle(&layer,face:face,resources:resources,glow:pose.glowGain)}
            if pose.crack>0.00001 {paintCracks(&layer,index:index,pose:pose,geometry:geometry)}
        }
    }
    private static func paintTriangle(_ context:inout GraphicsContext,face:Face,resources:GeodeFocusRewardResources,glow:Double) {
        let points=face.world.map(project)
        guard let transform=affine(face.uv,points) else{return}
        // Subpixel overlap closes GPU clip coverage cracks without moving the shared mesh vertices.
        let centroid=CGPoint(x:(points[0].x+points[1].x+points[2].x)/3,y:(points[0].y+points[1].y+points[2].y)/3)
        let coverage=points.map {p->CGPoint in
            let dx=p.x-centroid.x,dy=p.y-centroid.y,length=max(0.001,hypot(dx,dy))
            return CGPoint(x:p.x+dx/length*0.32,y:p.y+dy/length*0.32)
        }
        var triangle=Path();triangle.move(to:coverage[0]);triangle.addLine(to:coverage[1]);triangle.addLine(to:coverage[2]);triangle.closeSubpath()
        var ctx=context
        ctx.clip(to:triangle,style:FillStyle(antialiased:false))
        ctx.concatenate(transform)
        ctx.draw(face.cut ? resources.cut:resources.rock,in:face.cut ? resources.cutRect:resources.rockRect)
        // A scalar broad-light layer uses the actual face normal without CPU relighting the cached bitmap.
        var shade=context;shade.clip(to:triangle,style:FillStyle(antialiased:false))
        shade.fill(triangle,with:.color(Color.black.opacity(1-face.shade)))
        if face.cut {
            shade.blendMode = .plusLighter
            shade.fill(triangle,with:.color(Color(.sRGB,red:0.88,green:0.44,blue:0.08).opacity(0.06*glow)))
        }
    }
    private static func affine(_ source:[CGPoint],_ target:[CGPoint])->CGAffineTransform? {
        let s0=source[0],s1=source[1],s2=source[2],d0=target[0],d1=target[1],d2=target[2]
        let ux=s1.x-s0.x,uy=s1.y-s0.y,vx=s2.x-s0.x,vy=s2.y-s0.y,det=ux*vy-vx*uy
        if abs(det)<0.00001 {return nil}
        let dx=d1.x-d0.x,dy=d1.y-d0.y,ex=d2.x-d0.x,ey=d2.y-d0.y
        let a=(dx*vy-ex*uy)/det,c=(ex*ux-dx*vx)/det,b=(dy*vy-ey*uy)/det,d=(ey*ux-dy*vx)/det
        return CGAffineTransform(a:a,b:b,c:c,d:d,tx:d0.x-a*s0.x-c*s0.y,ty:d0.y-b*s0.x-d*s0.y)
    }
    private static func paintCracks(_ context:inout GraphicsContext,index:Int,pose:GeodeFocusRewardHeroPose,geometry:GeodeFocusRewardGeometry) {
        let state=pose.pieces[index],piece=geometry.pieces[index]
        for (branch,crack) in geometry.cracks.enumerated() where crack.piece==index {
            let amount=M.clamp(pose.crack*4-Double(branch))
            if amount<=0 {continue}
            let a=transformed(crack.a,piece:piece,state:state,yaw:pose.manualYaw)
            let b=transformed(crack.a+(crack.b-crack.a)*amount,piece:piece,state:state,yaw:pose.manualYaw)
            let pa=project(a),pb=project(b)
            var path=Path();path.move(to:pa);path.addLine(to:pb)
            let width=0.6+2.4*pose.crack
            context.stroke(path,with:.color(Color(.sRGB,red:0.025,green:0.05,blue:0.032)),style:StrokeStyle(lineWidth:width+0.6,lineCap:.round))
            var emission=context;emission.blendMode = .plusLighter
            emission.stroke(path,with:.linearGradient(Gradient(colors:[Color(red:0.91,green:0.39,blue:0.08),Color(red:0.45,green:0.72,blue:0.16)]),startPoint:pa,endPoint:pb),style:StrokeStyle(lineWidth:max(0.4,width*0.45),lineCap:.round))
        }
    }
    private static func drawCrumbs(_ context:inout GraphicsContext,pose:GeodeFocusRewardHeroPose) {
        for i in 0..<12 {
            let state=pose.pieces[i],h=G.hash(i+5513),angle=h*2*Double.pi,size=1.5+2.5*G.hash(i+5669)
            let travel=pose.crumbAmount*(8+18*G.hash(i+5781))
            let p=M.rotate(state.center+SIMD3(cos(angle)*travel,sin(angle)*travel,4),SIMD3(pose.manualYaw,0,0)),point=project(p)
            var path=Path();path.move(to:CGPoint(x:point.x-size*0.6,y:point.y-size*0.4));path.addLine(to:CGPoint(x:point.x+size*0.6,y:point.y-size*0.25));path.addLine(to:CGPoint(x:point.x+size*0.1,y:point.y+size*0.6));path.closeSubpath()
            context.fill(path,with:.color(Color(red:0.24,green:0.28,blue:0.25).opacity(pose.crumbAmount*state.alpha*0.65)))
        }
    }
}
