package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

/** GLSL ES 3 translations of the reference drum shader. Outputs premultiplied color. */
internal object RaffleDrumSpinGpuShaders {
    val drumVertex = """#version 300 es
        precision highp float;
        layout(location=0) in vec3 p;
        layout(location=1) in vec3 n;
        layout(location=2) in float mat;
        layout(location=3) in float spins;
        uniform vec4 viewport, drum, cam, misc, ghost;
        uniform float instanceOffset;
        out vec3 vn; out vec3 vv; flat out float material; out float alpha;
        vec3 rotX(vec3 v,float a) { float s=sin(a),c=cos(a); return vec3(v.x,c*v.y-s*v.z,s*v.y+c*v.z); }
        vec3 toView(vec3 p) { float x1=cam.x*p.x+cam.y*p.z; float z1=-cam.y*p.x+cam.x*p.z; return vec3(x1,cam.z*p.y-cam.w*z1,cam.w*p.y+cam.z*z1); }
        float depthOf(float z) { return clamp((z-2.8)/(9.5-2.8),0.0,1.0); }
        void main() {
          float g=float(gl_InstanceID)+instanceOffset;
          vec3 wp=p,wn=n;
          if(spins>0.5) { float a=drum.w-g*ghost.x; wp=rotX(p,a); wn=rotX(n,a); }
          vec3 vp=toView(wp); vn=toView(wn);
          float z=misc.x+vp.z; vec2 px=drum.xy+vec2(vp.x,-vp.y)*drum.z/z;
          vec2 ndc=vec2(px.x/viewport.x*2.0-1.0,1.0-px.y/viewport.y*2.0);
          gl_Position=vec4(ndc*z,(depthOf(z)*2.0-1.0)*z,z);
          vv=-vec3(vp.x,vp.y,z); material=mat;
          alpha=g>0.0 ? 0.55*(1.0-g/(ghost.y+1.0)) : 1.0;
          if(g>0.0 && spins<0.5) gl_Position=vec4(2.0,2.0,0.0,1.0);
        }
    """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam)
    val drumFragment = """#version 300 es
        precision highp float;
        uniform vec4 misc, ghost; uniform sampler2D envTex;
        in vec3 vn; in vec3 vv; flat in float material; in float alpha; out vec4 color;
        vec3 studio(vec3 r) {
          float up=r.y;
          vec3 c=mix(vec3(.08,.06,.14),vec3(.46,.42,.56),smoothstep(-.7,.25,up));
          c+=vec3(1.0,.98,1.02)*smoothstep(.3,.9,up)*1.25;
          float strip1=exp(-pow((r.x-.6)/.11,2.0))*smoothstep(-.4,.4,up);
          float strip2=exp(-pow((r.x+.66)/.09,2.0))*smoothstep(-.3,.5,up)*.8;
          c+=vec3(1.0,.98,1.0)*(strip1*1.5+strip2);
          c+=vec3(.5,.38,.85)*exp(-pow((r.z-.9)/.4,2.0))*.45;
          c-=vec3(.18)*exp(-pow((up+.05)/.07,2.0)); return max(c,vec3(0.0));
        }
        vec3 reflection(vec3 r) {
          if(ghost.z<.5) return studio(r);
          float u=atan(r.x,r.z)/6.2831853+.5, v=acos(clamp(r.y,-1.0,1.0))/3.14159265;
          vec3 e=textureLod(envTex,vec2(u,v),0.0).rgb;
          e=mix(vec3(dot(e,vec3(.3,.55,.15))),e,.35);
          vec3 fill=vec3(.78,.77,.84)*smoothstep(.15,-.75,r.z)*(.55+.45*smoothstep(-.6,.5,r.y));
          return e*1.3+pow(e,vec3(2.2))*2.4+studio(r)*.4+fill;
        }
        void main() {
          vec3 v=normalize(vv),n=normalize(vn); if(dot(n,v)<0.0) n=-n;
          float nv=max(dot(n,v),0.0); vec3 r=reflect(-v,n); vec3 env=reflection(r);
          vec3 tint=vec3(.93,.93,.98); float rough=0.0;
          if(material>.5 && material<1.5) { tint=vec3(.82,.82,.88); env=mix(env,reflection(normalize(n+vec3(0.0,.3,0.0))),.35); }
          if(material>1.5 && material<2.5) { tint=vec3(.34,.33,.4); env=mix(env,reflection(n),.35)*.8+vec3(.035,.03,.05); }
          if(material>2.5) { tint=vec3(.05,.045,.06); rough=1.0; }
          float fres=.55+.45*pow(1.0-nv,4.0); vec3 col=env*tint*fres;
          if(rough>.5) { vec3 l=normalize(vec3(-.4,.8,.5)); col=tint+vec3(1.0,.97,.94)*pow(max(dot(reflect(-l,n),v),0.0),48.0)*.9+vec3(.3,.18,.62)*pow(1.0-nv,2.5)*.8; }
          vec3 key=normalize(vec3(-.55,.7,.45));
          col+=vec3(1.0,.86,.62)*pow(max(dot(r,key),0.0),90.0)*.55*(1.0-rough);
          col+=vec3(.5,.3,1.0)*pow(1.0-nv,3.0)*.16*(1.0+misc.z);
          color=vec4(col*alpha,alpha);
        }
    """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam)
    val ballVertex = """#version 300 es
        precision highp float;
        uniform vec4 viewport; uniform vec4 ballPos[3], ballRot[3], ballFx[3];
        out vec2 q; flat out int id;
        const vec2 corners[6]=vec2[6](vec2(-1,-1),vec2(1,-1),vec2(1,1),vec2(-1,-1),vec2(1,1),vec2(-1,1));
        float depthOf(float z) { return clamp((z-2.8)/(9.5-2.8),0.0,1.0); }
        void main() {
          id=gl_InstanceID; vec4 pos=ballPos[id], rot=ballRot[id];
          q=corners[gl_VertexID]*(1.0+.9*ballFx[id].x); vec2 px=pos.xy+q*pos.z;
          vec2 ndc=vec2(px.x/viewport.x*2.0-1.0,1.0-px.y/viewport.y*2.0);
          gl_Position=vec4(ndc*pos.w,(depthOf(pos.w)*2.0-1.0)*pos.w,pos.w);
          if(rot.w<=.001) gl_Position=vec4(2,2,0,1);
        }
    """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam)
    val ballFragment = """#version 300 es
        precision highp float;
        uniform vec4 drum; uniform vec4 ballPos[3],ballRot[3],ballFx[3];
        in vec2 q; flat in int id; out vec4 color;
        float depthOf(float z) { return clamp((z-2.8)/(9.5-2.8),0.0,1.0); }
        vec3 rotX(vec3 v,float a) { float s=sin(a),c=cos(a); return vec3(v.x,c*v.y-s*v.z,s*v.y+c*v.z); }
        vec3 rotY(vec3 v,float a) { float s=sin(a),c=cos(a); return vec3(c*v.x+s*v.z,v.y,-s*v.x+c*v.z); }
        vec3 rotZ(vec3 v,float a) { float s=sin(a),c=cos(a); return vec3(c*v.x-s*v.y,s*v.x+c*v.y,v.z); }
        vec3 seam(float t) { float A=.78,B=.22; return vec3(A*cos(t)+B*cos(3.0*t),A*sin(t)-B*sin(3.0*t),2.0*sqrt(A*B)*sin(2.0*t)); }
        void main() {
          vec4 pos=ballPos[id],rot=ballRot[id]; float glow=ballFx[id].x,d2=dot(q,q);
          if(d2>1.0) {
            float rr=sqrt(d2),h=glow*exp(-(rr-1.0)*3.2)*(1.0-smoothstep(1.5,1.9,rr));
            if(h<.004) discard;
            vec3 hc=mix(vec3(1.0,.86,.55),vec3(.72,.55,1.0),smoothstep(1.0,1.6,rr));
            color=vec4(hc*h,h)*rot.w; gl_FragDepth=depthOf(pos.w); return;
          }
          vec3 n=vec3(q.x,-q.y,sqrt(1.0-d2)); vec3 o=rotZ(rotY(rotX(n,rot.x),rot.y),rot.z);
          float best=9.0,bt=0.0;
          for(int k=0;k<48;k++) { float t=float(k)*.1309,dd=distance(o,seam(t)); if(dd<best) { best=dd; bt=t; } }
          for(int k=0;k<6;k++) { float h=.065/float(k+1),da=distance(o,seam(bt-h)),db=distance(o,seam(bt+h)); if(da<best) { best=da; bt-=h; } if(db<best) { best=db; bt+=h; } }
          vec3 sp=seam(bt),tg=normalize(seam(bt+.01)-seam(bt-.01));
          float vv=abs(dot(o-sp,cross(sp,tg))),u=bt*24.0/6.2831853;
          float line=abs(fract(u*4.0+vv*9.0)-.5);
          float stitch=(1.0-smoothstep(.1,.2,line))*smoothstep(.018,.03,vv)*(1.0-smoothstep(.075,.09,vv));
          float groove=1.0-smoothstep(0.0,.016,vv); vec3 L=normalize(vec3(-.5,.6,.65));
          float diff=.5+.5*dot(n,L),spec=pow(max(0.0,dot(reflect(-L,n),vec3(0,0,1))),22.0),rim=pow(1.0-n.z,2.2);
          vec3 col=vec3(.97,.95,.91)*(.42+.62*diff);
          col=mix(col,col*.8,groove*.6); col=mix(col,vec3(.78,.14,.16),stitch);
          col+=vec3(.55,.35,1.0)*rim*.45; col+=vec3(spec*.35);
          col*=1.0-.35*smoothstep(.2,-.9,n.y); col+=vec3(1.0,.85,.5)*glow*rim*.8;
          float edge=1.0-smoothstep(1.0-2.0/pos.z,1.0,sqrt(d2)),a=edge*rot.w;
          color=vec4(col*a,a); float worldR=pos.z*pos.w/drum.z;
          gl_FragDepth=depthOf(pos.w-worldR*n.z);
        }
    """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam)
    val foilVertex = """#version 300 es
        precision highp float;
        uniform vec4 viewport,misc,burst;
        out vec3 n; out float a; out float hue; out vec2 uv; flat out float kind;
        const vec2 corners[6]=vec2[6](vec2(-1,-1),vec2(1,-1),vec2(1,1),vec2(-1,-1),vec2(1,1),vec2(-1,1));
        float h1(float i,float s) { return fract(sin(i*127.1+s*311.7)*43758.5453); }
        float cellScale(float cell) { if(cell==0.0||cell==1.0) return .9; if(cell==4.0||cell==10.0) return .62; if(cell==7.0) return .55; if(cell==11.0) return .7; return 1.0; }
        float depthOf(float z) { return clamp((z-2.8)/(9.5-2.8),0.0,1.0); }
        void main() {
          float i=float(gl_InstanceID),t=misc.w; n=vec3(0,0,1); a=0.0; hue=0.0; uv=vec2(0); kind=0.0;
          if(t<0.0) { gl_Position=vec4(2,2,0,1); return; }
          float pr=viewport.z,pick=h1(i,14.0),cell=floor(h1(i,15.0)*11.999);
          if(h1(i,16.0)<.35) cell=floor(h1(i,17.0)*3.999);
          if(pick>.86) { kind=1.0; cell=12.0+step(.5,h1(i,18.0)); }
          if(pick>.96) { kind=2.0; cell=14.0+step(.6,h1(i,18.0)); }
          float ang=h1(i,1.0)*6.2831853,up=h1(i,2.0),sp=(260.0+560.0*pow(h1(i,3.0),.8))*pr;
          if(kind==2.0) sp*=.45;
          float drag=3.2,travel=sp*(1.0-exp(-drag*t))/drag;
          vec2 dir=normalize(vec2(cos(ang)*1.25,sin(ang)*.75-.45*up));
          float fall=150.0*pr*t*t*.5+26.0*pr*t; if(kind==2.0) fall*=.3;
          float sway=sin(t*(3.0+3.0*h1(i,4.0))+i)*14.0*pr*smoothstep(.25,1.0,t);
          vec2 c=burst.xy+dir*(burst.z*.7+travel)+vec2(sway,fall);
          float halfSize=(8.0+6.0*pow(h1(i,5.0),1.6))*pr*cellScale(cell);
          vec3 rp; vec2 corner=corners[gl_VertexID];
          if(kind==0.0) {
            vec3 axis=normalize(vec3(h1(i,7.0)-.5,h1(i,8.0)-.5,h1(i,9.0)-.5)+vec3(0,0,.01));
            float spin=t*(5.0+8.0*h1(i,10.0))*exp(-t*.35)+h1(i,11.0)*6.28;
            vec3 local=vec3(corner*halfSize,0); float ca=cos(spin),sa=sin(spin);
            rp=local*ca+cross(axis,local)*sa+axis*dot(axis,local)*(1.0-ca);
            n=vec3(0,0,1)*ca+cross(axis,vec3(0,0,1))*sa+axis*axis.z*(1.0-ca);
          } else if(kind==1.0) {
            halfSize=(9.0+12.0*h1(i,5.0))*pr*(.55+.45*sin(t*(9.0+6.0*h1(i,19.0))+i*2.0));
            float r=h1(i,20.0)*.6;
            rp=vec3(vec2(corner.x*cos(r)-corner.y*sin(r),corner.x*sin(r)+corner.y*cos(r))*halfSize,0);
          } else { halfSize=(13.0+14.0*h1(i,5.0))*pr; rp=vec3(corner*halfSize,0); }
          vec2 px=c+rp.xy,ndc=vec2(px.x/viewport.x*2.0-1.0,1.0-px.y/viewport.y*2.0);
          gl_Position=vec4(ndc*3.0,(depthOf(3.0)*2.0-1.0)*3.0,3.0);
          float life=1.0-smoothstep(1.4+.6*h1(i,12.0),2.3,t); a=life*smoothstep(0.0,.05,t);
          if(kind==2.0) a*=.32*smoothstep(0.0,.3,t);
          hue=h1(i,13.0); float col=cell-4.0*floor(cell/4.0),row=floor(cell/4.0);
          uv=(vec2(col,row)+corner*.5+.5)/4.0;
        }
    """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam)
    val foilFragment = """#version 300 es
        precision highp float;
        uniform sampler2D confTex; uniform vec4 ghost;
        in vec3 n; in float a; in float hue; in vec2 uv; flat in float kind; out vec4 color;
        void main() {
          vec4 tex=texture(confTex,uv); float facing=abs(normalize(n).z);
          if(ghost.w<.5) {
            vec3 base=vec3(.98,.76,.36); if(hue>.6) base=vec3(.62,.42,1.0);
            if(kind>.5) { color=vec4(0); return; }
            vec3 col=base*(.3+.7*facing)+vec3(1.0,.97,.9)*pow(facing,30.0)*.8;
            color=vec4(col*a,a); return;
          }
          if(kind==1.0) { vec3 lc=min(tex.rgb*tex.a*a*1.4,vec3(1)); color=vec4(lc,max(lc.r,max(lc.g,lc.b))); return; }
          if(kind==2.0) { color=vec4(tex.rgb*tex.a*a,tex.a*a); return; }
          float lum=dot(tex.rgb,vec3(.3,.55,.15)); vec3 col=tex.rgb*(.38+.75*facing);
          col+=vec3(1.0,.96,.86)*pow(facing,18.0)*smoothstep(.35,.9,lum)*.9;
          float alpha=tex.a*a; color=vec4(col*alpha,alpha);
        }
    """.trimIndent().let(RaffleDrumSpinShaderCompile::unrollCoarseSeam)
}
