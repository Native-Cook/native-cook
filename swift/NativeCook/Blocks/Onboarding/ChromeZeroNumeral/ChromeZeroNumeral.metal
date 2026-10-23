#include <metal_stdlib>
using namespace metal;
constant float chromeZeroCell = 0.125;
constant float chromeZeroBevel = 0.058;
constant float chromeZeroPi = 3.14159265359;

constexpr sampler chromeZeroSampler(coord::normalized, address::clamp_to_edge, filter::linear);

// Original distance fields, mercury union, camera and studio lighting.
struct ChromeZeroNumeralField {
    texture2d<float> uEnv;
    float2 uCentre;
    float uUnit;
    float uPixelRatio;
    float uCamera;
    float4 uTurn; // cos(yaw), sin(yaw), cos(pitch), sin(pitch)
    float3 uDock;
    float uScale;
    float uBuild;
    float uDrain;
    float uFuse;
    float uRipple;
    float2 uRippleOrigin;
    float uSweep;
    float uPercent;
    float uFlash;





    float sq(float v) { return v * v; }
    float3 toObject(float3 v) {
        float3 q = float3(v.x, v.y*uTurn.z + v.z*uTurn.w, -v.y*uTurn.w + v.z*uTurn.z);
        return float3(q.x*uTurn.x - q.z*uTurn.y, q.y, q.x*uTurn.y + q.z*uTurn.x);
    }
    float3 toWorld(float3 v) {
        float3 q = float3(v.x*uTurn.x + v.z*uTurn.y, v.y, -v.x*uTurn.y + v.z*uTurn.x);
        return float3(q.x, q.y*uTurn.z - q.z*uTurn.w, q.y*uTurn.w + q.z*uTurn.z);
    }
    float roundedRect(float2 p, float2 ext, float radius) {
        float2 q = abs(p) - ext + radius;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
    }
    float profile(float2 p) {
        float outer = roundedRect(p, float2(0.64, 1.0), 0.56);
        float counter = roundedRect(p, float2(0.23, 0.60), 0.23);
        return max(outer, -counter);
    }
    // Analytic profile gradient keeps pillow curvature outside the four field taps of the normal.
    float2 profileGradient(float2 p) {
        float outer = roundedRect(p,float2(0.64,1.0),0.56);
        float counter = roundedRect(p,float2(0.23,0.60),0.23);
        float2 q = outer > -counter ? max(abs(p)-float2(0.08,0.44),0.0)
            : max(abs(p)-float2(0.0,0.37),0.0);
        return q/max(length(q),0.00001)*sign(p)*(outer > -counter ? 1.0 : -1.0);
    }
    float extrude(float profileD, float z, float depth, float bevel) {
        float2 q = float2(profileD + bevel, abs(z) - depth + bevel);
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - bevel;
    }
    float solidZero(float3 p) { return extrude(profile(p.xy), p.z, 0.26, chromeZeroBevel); }
    float hashColumn(float2 col) {
        float3 h = fract(col.xyx*0.1031);
        h += dot(h,h.yzx+33.33);
        return fract((h.x+h.y)*h.z);
    }
    float breath() { return 1.0 + 0.10 * (1.0-uFuse); }
    float columnDrop(float2 col) {
        return uDrain>0.0 ? max(0.0, uDrain - 0.22 * hashColumn(col + 17.0)) * 9.0 : 0.0;
    }
    // A row starts at y=-1; arrival times increase with row, so the mould fills from its foot.
    float arrival(float row, float2 col) { return 0.10 + 0.08*hashColumn(col) + row*0.040; }
    // Polynomial smooth union has round liquid necks, never cell-shaped facets.
    float liquidUnion(float a, float b, float softness) {
        float h = max(softness-abs(a-b),0.0)/softness;
        return min(a,b)-h*h*softness*0.25;
    }
    float beadShape(float3 delta, float stretch) {
        // A tapered round capsule: no box distance, including during the falling phase.
        float taper = 1.0-0.16*smoothstep(-stretch,stretch+0.0001,delta.y)
            *smoothstep(0.0,0.03,stretch);
        delta.y = sign(delta.y)*max(abs(delta.y)-stretch,0.0);
        return length(delta)-0.47*chromeZeroCell*taper;
    }
    float2 beadColumn(float3 q) {
        // Four layers at z = ±.0625, ±.1875; the centre plane lies between layers.
        return clamp(floor(float2(q.x,q.z)/chromeZeroCell+float2(0.5,0.0)),
            float2(-5.0,-2.0),float2(5.0,1.0));
    }
    float oneBead(float3 p, float2 col, float row, float moving) {
        float3 home = float3(col.x*chromeZeroCell, -1.0+row*chromeZeroCell, (col.y+0.5)*chromeZeroCell);
        if (profile(home.xy) > chromeZeroCell*0.25 || row < 0.0 || row > 16.0) { return 0.24; }
        float until = max(arrival(row, col)-uBuild, 0.0);
        // A capsule contracts continuously into its sphere over the final .15 model units.
        float age = max(uBuild-arrival(row,col),0.0);
        float settle = (1.0-moving)*0.010*sin(age*115.0)*exp(-age*60.0);
        float rise = moving*until*36.0+settle;
        float streak = 0.0705*smoothstep(0.0, 0.15, rise);
        float3 delta = p-home-float3(0.0, rise, 0.0);
        return beadShape(delta, streak);
    }
    // Exact occupancy test for a bead centre, without square roots in the fusion kernel.
    float beadMask(float2 home) {
        float2 outerQ = max(abs(home)-float2(0.08,0.44),0.0);
        float2 innerQ = max(abs(home)-float2(0.0,0.37),0.0);
        return dot(outerQ,outerQ)<=sq(0.56+chromeZeroCell*0.25)
            && dot(innerQ,innerQ)>=sq(0.23-chromeZeroCell*0.25) ? 1.0 : 0.0;
    }
    float beads(float3 p) {
        if (uBuild <= 0.0 || uDrain >= 1.0) { return 10.0; }
        float expansion = breath();
        float3 q = p/expansion;
        float2 col = beadColumn(q);
        // The half-second exit keeps only the two camera-side depth layers.
        if (uDrain>0.0) { col.y=clamp(col.y,uTurn.x>=0.0 ? 0.0 : -2.0,uTurn.x>=0.0 ? 1.0 : -1.0); }
        q.y += columnDrop(col);
        if (uBuild >= 1.0) {
            float3 home = float3(col.x*chromeZeroCell,clamp(floor(q.y/chromeZeroCell+0.5),-8.0,8.0)*chromeZeroCell,(col.y+0.5)*chromeZeroCell);
            float d;
            // The core grows inside constant-radius balls; easing reveals liquid necks during hold.
            // Sinking the centres lets the round bumps submerge before the exact solid endpoint.
            float3 beadDelta = q-home;
            beadDelta.z += sign(home.z)*0.105*uFuse;
            d = profile(home.xy)>chromeZeroCell*0.25 ? 0.24 : beadShape(beadDelta,0.0);
            if (uFuse<=0.0 || uDrain>0.0) { return d*expansion; }
            float core = extrude(profile(q.xy),q.z,0.26,mix(0.045,chromeZeroBevel,smoothstep(0.65,1.0,uFuse)))+0.30*(1.0-sqrt(uFuse));
            // Sum neighbouring Gaussian sphere fields analytically on each lattice axis.
            // The repeated field and its gradient agree at cell boundaries: no tiled creases.
            float softness = mix(1200.0,420.0,smoothstep(0.0,0.5,uFuse));
            float3 dx = q.x-home.x+float3(chromeZeroCell,0.0,-chromeZeroCell);
            float3 dy = q.y-home.y+float3(chromeZeroCell,0.0,-chromeZeroCell);
            float3 gx = exp(-dx*dx*softness);
            float3 gy = exp(-dy*dy*softness);
            // Only the nine actual neighbouring centres contribute. This also makes the
            // field continuous where a valid centre meets an empty counter/perimeter cell.
            float gxy = gx.x*(gy.x*beadMask(home.xy+float2(-chromeZeroCell,-chromeZeroCell))
                +gy.y*beadMask(home.xy+float2(-chromeZeroCell,0.0))+gy.z*beadMask(home.xy+float2(-chromeZeroCell,chromeZeroCell)))
                +gx.y*(gy.x*beadMask(home.xy+float2(0.0,-chromeZeroCell))
                +gy.y*beadMask(home.xy)+gy.z*beadMask(home.xy+float2(0.0,chromeZeroCell)))
                +gx.z*(gy.x*beadMask(home.xy+float2(chromeZeroCell,-chromeZeroCell))
                +gy.y*beadMask(home.xy+float2(chromeZeroCell,0.0))+gy.z*beadMask(home.xy+float2(chromeZeroCell,chromeZeroCell)));
            float outerZ = 0.1875-0.105*uFuse;
            float innerZ = 0.0625-0.045*uFuse;
            float gz = exp(-sq(q.z-outerZ)*softness)+exp(-sq(q.z+outerZ)*softness)
                +exp(-sq(q.z-innerZ)*softness)+exp(-sq(q.z+innerZ)*softness);
            float sphereD = sqrt(max(-log(max(gxy*gz,0.00000001))/softness,0.0))-0.47*chromeZeroCell;
            float fluidBeads = mix(d,sphereD,smoothstep(0.0,0.15,uFuse));
            float merged = liquidUnion(fluidBeads,core,0.07);
            float mould = extrude(profile(q.xy),q.z,0.26,mix(0.045,chromeZeroBevel,smoothstep(0.65,1.0,uFuse)))-(1.0-uFuse)*0.10;
            float edgeSoftness = max(0.0001,0.035*sin(chromeZeroPi*uFuse));
            return -liquidUnion(-merged,-mould,edgeSoftness)*expansion;
        }
        float latest = min(16.0, floor((uBuild-arrival(0.0,col))/0.040));
        float row = clamp(floor((q.y+1.0)/chromeZeroCell+0.5), 0.0, max(0.0,latest));
        float landed = latest >= 0.0 ? oneBead(q,col,row,0.0) : 10.0;
        float falling = 10.0;
        if (uBuild < 1.0 && latest < 16.0) {
            // Invert the stretched lattice analytically: no loop over hundreds of individual beads.
            float spacing = chromeZeroCell+36.0*0.040;
            float movingRow = floor((q.y+1.0-36.0*(arrival(0.0,col)-uBuild))/spacing+0.5);
            movingRow = clamp(movingRow, max(0.0,latest+1.0),16.0);
            falling = oneBead(q,col,movingRow,1.0);
        }
        float d = min(landed,falling);
        return d*expansion;
    }
    float3 percentCentre() {
        // It starts behind the right stroke, where the bowl cannot show it.
        return mix(float3(0.44,0.0,-0.45),uDock,uPercent)
            + float3(0.15*sin(chromeZeroPi*uPercent),0.10*sin(chromeZeroPi*uPercent),0.0);
    }
    float3 percentLocal(float3 p) {
        float ang = -1.5*chromeZeroPi*(1.0-uPercent);
        float3 q = (p-percentCentre())/mix(0.55,1.0,uPercent);
        return float3(q.x*cos(ang)-q.z*sin(ang),q.y,q.x*sin(ang)+q.z*cos(ang));
    }
    float percentField(float3 p) {
        if (uPercent <= 0.0) { return 10.0; }
        float3 q = percentLocal(p);
        // Ring centres and radius keep the complete glyph at the contract's .62-unit height.
        float a = abs(length(q.xy-float2(-0.13,0.16))-0.1075)-0.0425;
        float b = abs(length(q.xy-float2(0.13,-0.16))-0.1075)-0.0425;
        float2 bar = float2(q.x*0.819152-q.y*0.573576,q.x*0.573576+q.y*0.819152);
        float c = roundedRect(bar,float2(0.0425,0.31),0.012);
        return extrude(min(min(a,b),c),q.z,0.07,0.014)*mix(0.55,1.0,uPercent);
    }
    float scene(float3 p, bool poured) {
        return min(poured ? beads(p) : solidZero(p),percentField(p));
    }
    // A slab test in model space rejects the empty canvas before touching the distance field.
    float2 boxInterval(float3 ro, float3 rd, float3 lo, float3 hi) {
        float3 safeDir = float3(rd.x>=0.0 ? max(rd.x,0.000001) : min(rd.x,-0.000001),
            rd.y>=0.0 ? max(rd.y,0.000001) : min(rd.y,-0.000001),
            rd.z>=0.0 ? max(rd.z,0.000001) : min(rd.z,-0.000001));
        float3 a = (lo-ro)/safeDir;
        float3 b = (hi-ro)/safeDir;
        float3 nearT = min(a,b);
        float3 farT = max(a,b);
        return float2(max(nearT.x,max(nearT.y,nearT.z)),min(farT.x,min(farT.y,farT.z)));
    }
    float2 sphereInterval(float3 ro, float3 rd, float3 at, float radius) {
        float3 q = ro-at;
        float b = dot(q,rd);
        float det = b*b-dot(q,q)+radius*radius;
        if (det < 0.0) { return float2(1.0,-1.0); }
        return float2(-b-sqrt(det),-b+sqrt(det));
    }
    float3 surfaceNormal(float3 p, bool poured) {
        const float e = 0.0007;
        const float2 k = float2(1.0,-1.0);
        return normalize(k.xyy*scene(p+k.xyy*e,poured)+k.yyx*scene(p+k.yyx*e,poured)
            +k.yxy*scene(p+k.yxy*e,poured)+k.xxx*scene(p+k.xxx*e,poured));
    }
    float3 environment(float3 rayDir, float solidStudio) {
        float2 uv = float2(fract(atan2(rayDir.x,rayDir.z)/(2.0*chromeZeroPi)+0.5),acos(clamp(rayDir.y,-1.0,1.0))/chromeZeroPi);
        // Both studio tiles contain wrap gutters and prefiltered colour. Endpoints take one tap.
        float2 at = float2(1.5+uv.x*1021.0,clamp(uv.y*512.0,0.5,511.5));
        if (solidStudio>=1.0) { return float3(uEnv.sample(chromeZeroSampler, at/float2(2048.0,512.0)).rgb); }
        if (solidStudio<=0.0) { return float3(uEnv.sample(chromeZeroSampler, (at+float2(1024.0,0.0))/float2(2048.0,512.0)).rgb); }
        return mix(float3(uEnv.sample(chromeZeroSampler, (at+float2(1024.0,0.0))/float2(2048.0,512.0)).rgb),
            float3(uEnv.sample(chromeZeroSampler, at/float2(2048.0,512.0)).rgb),solidStudio);
    }
    float lamp(float3 reflected, float3 axis, float exponent) {
        return pow(max(dot(reflected,normalize(axis)),0.0),exponent);
    }
    // A dim opposite softbox and horizon card, evaluated only on the turning solid face.
    // Keeping this fill separate from the atlas leaves REST and the bearing studio untouched.
    float3 turnStudio(float3 rayDir) {
        float longitude = atan2(rayDir.x,rayDir.z)/(2.0*chromeZeroPi)+0.5;
        float offset = fract(longitude-0.895+0.5)-0.5;
        float height = rayDir.y+0.35*offset;
        float softbox = exp(-sq(offset/0.36))*exp(-sq((height-0.10)/0.16));
        float horizon = exp(-sq((rayDir.y+0.08)/0.15));
        return float3(0.40,0.41,0.43)*softbox+float3(0.10,0.11,0.13)*horizon;
    }

    half4 render(float2 xy) {
        if (uScale <= 0.001) { return half4(0.0); }
        bool poured = uFuse < 1.0 || uBuild < 1.0 || uDrain > 0.0;
        float2 screen = (xy-uCentre)/uUnit;
        float3 ray = normalize(float3(screen.x,-screen.y,-uCamera));
        float3 ro = toObject(float3(0.0,0.0,uCamera))/uScale;
        float3 rd = toObject(ray);
        float expandBy = poured ? breath() : 1.0;
        float top = uBuild < 1.0 ? 7.5 : 1.10;
        float bottom = -1.10;
        if (uDrain > 0.0) {
            // Every column has fallen at least 9*(drain - 0.22) and at most 9*drain: march only that band.
            top = 1.10 - 9.0*max(0.0,uDrain-0.22);
            bottom = -1.10 - 9.0*uDrain;
        }
        // Match the drain box to its two remaining depth layers, not the old four-layer slab.
        float backZ = uDrain>0.0 ? (uTurn.x>=0.0 ? 0.003 : -0.247) : -0.34;
        float frontZ = uDrain>0.0 ? (uTurn.x>=0.0 ? 0.247 : -0.003) : 0.34;
        float2 zeroRange = boxInterval(ro,rd,float3(-0.74,bottom,backZ)*expandBy,float3(0.74,top,frontZ)*expandBy);
        if (uBuild <= 0.0 || uDrain >= 1.0) { zeroRange = float2(1.0,-1.0); }
        float2 pctRange = uPercent > 0.0 ? sphereInterval(ro,rd,percentCentre(),0.46*mix(0.55,1.0,uPercent)) : float2(1.0,-1.0);
        bool zeroPossible = zeroRange.y >= max(zeroRange.x,0.0);
        bool pctPossible = pctRange.y >= max(pctRange.x,0.0);
        if (!zeroPossible && !pctPossible) { return half4(0.0); }
        float nearT = zeroPossible ? zeroRange.x : pctRange.x;
        float farT = zeroPossible ? zeroRange.y : pctRange.y;
        if (pctPossible) {
            nearT = min(nearT, pctRange.x);
            farT = max(farT, pctRange.y);
        }
        float t = max(0.0,nearT);
        float closest = 10.0;
        float bestT = t;
        float pixel = 1.0/(uUnit*uScale*uPixelRatio);
        float threshold = pixel*(uDrain>0.0 ? 0.65 : 0.16);
        bool hit = false;
        if (poured) {
            for (int i=0;i<72;i++) {
                // Keep the original budget for rays that can hit the docked percent.
                if (uDrain>0.0 && !pctPossible && i>=28) { break; }
                float3 p = ro+rd*t;
                float d = scene(p,true);
                if (d < closest) {
                    closest = d;
                    bestT = t;
                }
                if (d < threshold) {
                    hit = true;
                    break;
                }
                // Column state changes at cell boundaries. Do not jump across a neighbouring column.
                float2 grid = p.xz/expandBy/chromeZeroCell-float2(0.0,0.5);
                float2 cellEdge = (0.5-fract(grid+0.5))*chromeZeroCell*expandBy;
                float2 boundary = (cellEdge+sign(rd.xz)*0.5*chromeZeroCell*expandBy)/
                    (rd.xz+float2(0.0000001));
                float crossing = max(0.0008,min(boundary.x,boundary.y)+0.0005);
                t += min(max(d*0.84,threshold*0.6),crossing);
                if (t>farT) { break; }
            }
        } else {
            for (int i=0;i<48;i++) {
                float d = scene(ro+rd*t,false);
                if (d < closest) {
                    closest = d;
                    bestT = t;
                }
                if (d < threshold) {
                    hit = true;
                    break;
                }
                t += max(d*0.9,threshold*0.6);
                if (t>farT) { break; }
            }
        }
        float alpha = hit ? 1.0 : 1.0-smoothstep(threshold,pixel*1.1,closest);
        if (alpha<=0.0) { return half4(0.0); }
        float3 p = ro+rd*bestT;
        bool isPercent = percentField(p) < (poured ? beads(p) : solidZero(p));
        float3 n;
        if (uDrain>0.0 && !isPercent) {
            // Exact sphere normal avoids four scene evaluations for every falling bearing.
            float3 q=p/expandBy;
            float2 col=beadColumn(q);
            col.y=clamp(col.y,uTurn.x>=0.0 ? 0.0 : -2.0,uTurn.x>=0.0 ? 1.0 : -1.0);
            q.y+=columnDrop(col);
            float3 home=float3(col.x*chromeZeroCell,clamp(floor(q.y/chromeZeroCell+0.5),-8.0,8.0)*chromeZeroCell,(col.y+0.5)*chromeZeroCell);
            n=normalize(q-home+float3(0.0,0.0,sign(home.z)*0.105*uFuse));
        } else { n=surfaceNormal(p,poured); }
        float facing = smoothstep(0.82,0.995,abs(n.z));
        float wave = 0.0;
        if (!isPercent && (!poured || uFuse>0.65)) {
            // Very shallow pillow curvature. Outer and inner profile gradients both bend the reflection.
            float edgeD = profile(p.xy);
            float2 grad = profileGradient(p.xy);
            float rimBend = 0.13*sq(1.0-smoothstep(0.0,0.15,-edgeD));
            n = normalize(n+float3(grad*rimBend+float2(p.x*0.035,p.y*0.045),0.0)*facing*(poured ? smoothstep(0.65,1.0,uFuse) : 1.0));
        }
        if (uDrain<=0.0 && !isPercent && uRipple>0.0 && uRipple<1.0) {
            float2 radial = p.xy-uRippleOrigin;
            float radius = length(radial);
            float ringD = radius-uRipple*2.5;
            // A compact packet contains at most two broad crests, .35 model units apart.
            float packet = 1.0-smoothstep(0.26,0.35,abs(ringD));
            float envelope = exp(-sq(ringD/0.22))*packet*sin(chromeZeroPi*uRipple)/(1.0+radius*0.85);
            wave = envelope*cos(ringD*(2.0*chromeZeroPi/0.35));
            n = normalize(n+float3(radial/max(radius,0.001),0.0)*wave*0.10);
        }
        if (isPercent) {
            float3 pct = percentLocal(p);
            float2 ringA = pct.xy-float2(-0.13,0.16);
            float2 ringB = pct.xy-float2(0.13,-0.16);
            float da = abs(length(ringA)-0.1075);
            float db = abs(length(ringB)-0.1075);
            float barX = pct.x*0.819152-pct.y*0.573576;
            float2 curve = float2(0.819152,-0.573576)*barX*9.0;
            if (min(da,db)<abs(barX)) {
                float2 ringVec = da<db ? ringA : ringB;
                float ringR = length(ringVec);
                curve = ringVec/max(ringR,0.001)*(ringR-0.1075)*9.0;
            }
            float ang = -1.5*chromeZeroPi*(1.0-uPercent);
            float3 bend = float3(curve.x*cos(ang),curve.y,-curve.x*sin(ang));
            n = normalize(n+bend*facing);
        }
        float3 nw = toWorld(n);
        float3 reflection = reflect(ray,nw);
        float nv = clamp(dot(nw,-ray),0.0,1.0);
        float silverBevel = smoothstep(0.03,0.40,abs(n.z));
        float3 f0 = mix(float3(0.07,0.15,0.95),float3(0.95,0.96,0.98),silverBevel);
        if (poured) {
            float3 bearingF0 = mix(float3(0.76,0.83,1.0),float3(0.95,0.96,0.98),smoothstep(-0.85,-0.25,n.y));
            f0 = mix(bearingF0,f0,smoothstep(0.65,1.0,uFuse));
        }
        if (isPercent) { f0=float3(0.24,0.27,0.38); }
        float3 fresnel = f0+(float3(1.0)-f0)*pow(1.0-nv,5.0);
        // The face's studio follows 87.5% of its reflected yaw; the remaining
        // motion slides the flag across the face without losing it during idle or early drag.
        float3 lookup = reflection;
        float faceWeight = isPercent ? 0.0 : facing*(poured ? smoothstep(0.65,1.0,uFuse) : 1.0);
        float turnDelta = atan2(uTurn.y,uTurn.x)+0.33;
        float follow = -1.75*sin(turnDelta)*faceWeight;
        lookup = float3(reflection.x*cos(follow)+reflection.z*sin(follow),reflection.y,
            -reflection.x*sin(follow)+reflection.z*cos(follow));
        float3 env = environment(lookup,isPercent || !poured ? 1.0 : smoothstep(0.65,1.0,uFuse));
        // Idle stays in the original flagged key; beyond .45 radians the dim turn studio is complete.
        float turnFill = smoothstep(0.04,0.10,1.0-cos(turnDelta))*faceWeight;
        if (turnFill>0.0) { env += turnStudio(lookup)*turnFill; }
        float ao = 1.0;
        if (!isPercent && !poured) {
            float counterWall = (1.0-facing)*(1.0-smoothstep(0.26,0.40,abs(p.x)));
            ao -= counterWall*0.24;
        }
        if (poured && !isPercent) { ao=0.88+0.12*smoothstep(0.0,0.65,nv); }
        float3 colour = env*fresnel*ao*1.32;
        // Anodised cobalt has a body colour even when the wall reflects the black studio flag.
        // The mask excludes planar silver, and preserves every accepted loose/liquid state.
        float solidFinish = poured ? smoothstep(0.65,1.0,uFuse) : 1.0;
        float wallMask = isPercent ? 0.0 : (1.0-silverBevel)*solidFinish;
        if (wallMask>0.0) {
            float wallSheen = 0.5+0.5*dot(nw,normalize(float3(-0.3,0.65,0.7)));
            colour += mix(float3(0.055,0.105,0.65),float3(0.095,0.17,1.10),wallSheen)*wallMask;
            float wallStrip = exp(-sq((reflection.y+0.04-reflection.x*0.12)/0.045));
            colour += float3(0.30,0.42,0.95)*wallStrip*wallMask;
        }
        float hot = uDrain>0.0 ? 0.0 : lamp(reflection,float3(-0.68,0.55,0.48),180.0)*2.6
            + lamp(reflection,float3(0.74,0.28,-0.46),260.0)*3.0
            + lamp(reflection,float3(-0.2,-0.65,0.72),320.0)*1.8;
        colour += fresnel*hot;
        // Polished chamfers pick up a broad white overhead card, with moving hot lamps.
        float bevelFinish = poured && !isPercent ? smoothstep(0.65,1.0,uFuse) : 1.0;
        float chamfer = smoothstep(0.08,0.30,abs(n.z))*
            (1.0-smoothstep(mix(0.88,0.96,bevelFinish),mix(0.99,0.999,bevelFinish),abs(n.z)));
        float rimLight = pow(1.0-nv,4.0);
        float edgeLight = isPercent ? 0.42 : (poured ? mix(0.10,0.95,smoothstep(0.65,1.0,uFuse)) : 0.95);
        float3 oldEdge = float3(0.78,0.86,1.0)*(chamfer*edgeLight+rimLight*(isPercent ? 0.15 : 0.10));
        if (isPercent || solidFinish>0.0) {
            float cardLight = 0.22+0.30*lamp(reflection,float3(-0.3,0.7,0.5),3.0);
            float3 polishedEdge = mix(float3(0.97,0.98,1.0),float3(0.80,0.87,1.0),rimLight*0.25)
                *(chamfer*cardLight+rimLight*0.045);
            colour += mix(oldEdge,polishedEdge,isPercent ? 1.0 : solidFinish*(1.0-facing));
        } else { colour += oldEdge; }

        float band = exp(-sq((p.x-uSweep*1.15+0.1*p.y)/0.075))
            * (1.0-smoothstep(0.80,1.0,abs(uSweep)));
        // On the loose balls the band catches each crown in turn: a glitter running across the numeral.
        float crown = max(n.z,0.0);
        crown = crown * crown;
        crown = crown * crown;
        crown = crown * crown;
        float catchLight = (poured && uFuse<0.65) ? 1.5*crown : facing*0.60;
        colour += float3(0.85,0.91,1.0)*band*catchLight;
        colour += float3(0.40,0.52,1.0)*wave*0.18;
        if (poured && uBuild<1.0 && !isPercent) {
            float3 homeP = p/breath();
            float2 col = beadColumn(homeP);
            float row = floor((homeP.y+1.0)/chromeZeroCell+0.5);
            float age = uBuild-arrival(row,col);
            float landedFlash = age>=0.0 ? exp(-age*90.0) : 0.0;
            colour += float3(0.42,0.46,0.55)*landedFlash;
        }
        // One bounded highlight lift, including bloom: never brighten the dark studio panel.
        float specularLift = smoothstep(0.65,1.10,max(colour.r,max(colour.g,colour.b)));
        float flashMask = clamp(max(chamfer,max(rimLight,specularLift)),0.0,1.0);
        colour *= 1.0+0.30*clamp(uFlash,0.0,1.0)*flashMask;
        // Soft shoulder preserves deep blacks; source pictures are already in display colour space.
        colour = colour/(1.0+colour*0.18);
        // Roll off only the solid silver face. Retain the accepted ball, wall and bevel exposure.
        float faceShoulder = isPercent ? 0.0 : facing*(poured ? smoothstep(0.65,1.0,uFuse) : 1.0);
        float3 excess = max(colour-0.85,0.0);
        float3 rolled = min(colour,0.85)+excess/(1.0+excess/0.15);
        colour = mix(colour,rolled,faceShoulder);
        // Away from the key, silver stays below 60% display brightness; the bevel keeps its glints.
        colour = mix(colour,min(colour,float3(0.60)),turnFill);
        float grain = fract(sin(dot(xy,float2(12.9898,78.233)))*43758.5453)-0.5;
        colour = clamp(colour+grain/510.0,0.0,1.0);
        return half4(half3(colour*alpha),half(alpha));
    }
};

struct ChromeZeroNumeralBackdrop {
    float2 resolution;
    float drift;
    float flash;
    float dim;
    half4 render(float2 xy) {
        float2 uv = xy / resolution;
        float2 p = uv - float2(0.5, 0.43);
        float centre = exp(-dot(p * float2(1.5, 1.0), p * float2(1.5, 1.0)) * 3.0);
        float3 ink = mix(float3(0.00784, 0.01176, 0.05882), float3(0.02745, 0.04314, 0.2), centre);
        // Parallel 30-degree ribbons in point space: positive y falls down the screen.
        // A narrow blue-white core sits within a periwinkle shoulder and electric-blue skirt.
        float2 axis = float2(0.8660254, 0.5);
        float2 normal = float2(-0.5, 0.8660254);
        float2 topDelta = (xy - float2(resolution.x * 0.64 + drift * 4.0,
            resolution.x * 0.015 + drift * 3.0)) / resolution.x;
        // Compact controls sit closer to the edge: lower the bottom ribbon with them.
        float bottomInset = resolution.y < 700.0 ? 0.04 : 0.12;
        float2 bottomDelta = (xy - float2(-resolution.x * 0.10 + drift * 3.0,
            resolution.y - resolution.x * bottomInset + drift * 4.0)) / resolution.x;
        float topAlong = dot(topDelta, axis);
        float bottomAlong = dot(bottomDelta, axis);
        float topAcross = dot(topDelta, normal);
        float bottomAcross = dot(bottomDelta, normal);
        float topWidth = 0.075 + 0.055 * smoothstep(-0.3, 0.5, topAlong);
        float topEnvelope = exp(-topAlong * topAlong / 0.30);
        float bottomEnvelope = exp(-bottomAlong * bottomAlong / 0.38);
        float core = exp(-topAcross * topAcross / (topWidth * topWidth)) * topEnvelope * 0.55
            + exp(-bottomAcross * bottomAcross / 0.0081) * bottomEnvelope * 0.62;
        float shoulder = exp(-topAcross * topAcross / 0.0361) * topEnvelope * 0.28
            + exp(-bottomAcross * bottomAcross / 0.0256) * bottomEnvelope * 0.32;
        float skirt = exp(-topAcross * topAcross / 0.09) * topEnvelope * 0.20
            + exp(-bottomAcross * bottomAcross / 0.0625) * bottomEnvelope * 0.22;
        float2 hazePoint = (xy - resolution * float2(0.36, 0.25)) / (resolution.x * float2(0.50, 0.65));
        float haze = exp(-dot(hazePoint, hazePoint)) * 0.035;
        // Rain retains 55% of the light; the fuse lifts its structure without bleaching it.
        float power = (1.0 + 0.035 * drift + 0.40 * flash) * (1.0 - 0.45 * dim);
        ink += power * (float3(0.788, 0.824, 1.0) * core
            + float3(0.557, 0.616, 1.0) * shoulder
            + float3(0.239, 0.294, 1.0) * skirt
            + float3(0.557, 0.616, 1.0) * haze);
        float vignette = 1.0 - 0.26 * smoothstep(0.24, 0.79, length(uv - 0.5));
        float noise = fract(sin(dot(xy, float2(12.9898, 78.233))) * 43758.5453) - 0.5;
        return half4(half3(clamp(ink * vignette + noise / 255.0, 0.0, 1.0)), 1.0);
    }
};

struct ChromeZeroNumeralCard {
    texture2d<float> cardImage;
    float2 dimensions;
    float sweep;
    float yaw;
    float strength;
    float fuse;
    float4 render(float2 xy) {
        float2 uv = xy / dimensions;
        float4 card = cardImage.sample(chromeZeroSampler, uv);
        float2 shadowPoint = (uv - float2(0.635 - yaw * 0.045, 0.65)) / float2(0.23, 0.12);
        float contact = exp(-dot(shadowPoint, shadowPoint) * 1.8) * 0.48 * strength;
        float stripe = uv.x + uv.y * 0.37 - (sweep * 0.96 + 0.6);
        float sheen = exp(-stripe * stripe / 0.0028) * 0.26;
        sheen *= step(-0.999, sweep) * (1.0 - step(0.999, sweep));
        // One crisp boundary and a soft tail, about 10% of the card width. Yaw moves the light.
        float reflectedBand = uv.x + uv.y * 0.28 - (0.57 + yaw * 0.42);
        float hardLight = smoothstep(-0.004, 0.003, reflectedBand)
            * exp(-max(reflectedBand, 0.0) * max(reflectedBand, 0.0) / 0.0064) * 0.24;
        // A flattened, softly defocused zero below its foot; bead gaps reappear while held.
        float2 reflectedPoint = (uv - float2(0.645 - yaw * 0.045, 0.73)) / float2(0.25, 0.075);
        float oval = length(reflectedPoint);
        float rim = oval - 0.82;
        float smear = exp(-rim * rim / 0.15) * exp(-abs(reflectedPoint.y) * 0.5);
        float fragments = 0.18 + 0.82 * smoothstep(0.15, 0.85,
            0.5 + 0.5 * cos(reflectedPoint.x * 31.0 + reflectedPoint.y * 9.0));
        float reflection = smear * mix(fragments, 1.0, fuse) * strength * 0.13;
        float3 lightColour = float3(0.71, 0.79, 1.0);
        float3 lit = card.rgb * (1.0 - contact)
            + lightColour * (sheen + hardLight + reflection) * card.a;
        return float4(min(lit, float3(card.a)), card.a);
    }
};

struct ChromeZeroNumeralVertex {
    float4 position [[position]];
    float2 uv;
};

vertex ChromeZeroNumeralVertex chromeZeroNumeralVertex(uint id [[vertex_id]]) {
    float2 positions[3] = {float2(-1.0,-1.0), float2(3.0,-1.0), float2(-1.0,3.0)};
    float2 p = positions[id];
    return {float4(p,0.0,1.0),float2((p.x+1.0)*0.5,(1.0-p.y)*0.5)};
}

static float4 chromeZeroOver(float4 foreground, float4 background) {
    return foreground + background * (1.0 - foreground.a);
}

static float4 chromeZeroSprite(texture2d<float> image, float2 point, float2 centre,
                               float size, float angle = 0.0, float blur = 0.0) {
    if (size <= 0.001) { return float4(0.0); }
    float2 p = point - centre;
    p = float2(p.x*cos(angle)+p.y*sin(angle),-p.x*sin(angle)+p.y*cos(angle));
    float2 uv = p / size + 0.5;
    float bleed = 3.0 * blur / size;
    if (any(uv < -bleed) || any(uv > 1.0 + bleed)) { return float4(0.0); }
    constexpr sampler spriteSampler(coord::normalized, address::clamp_to_zero, filter::linear);
    if (blur <= 0.0) { return image.sample(spriteSampler,uv); }
    // Sample the same point-space Gaussian used by Skia's sprite blur.
    float4 colour = float4(0.0);
    float weightSum = 0.0;
    for (int y = -3; y <= 3; y++) {
        for (int x = -3; x <= 3; x++) {
            float weight = exp(-0.5 * float(x*x + y*y));
            colour += image.sample(spriteSampler, uv + float2(x,y)*blur/size) * weight;
            weightSum += weight;
        }
    }
    colour /= weightSum;
    return colour;
}

fragment float4 chromeZeroNumeralFragment(ChromeZeroNumeralVertex input [[stage_in]],
    constant float *u [[buffer(0)]], texture2d<float> environment [[texture(0)]],
    texture2d<float> cardImage [[texture(1)]], texture2d<float> sparkle [[texture(2)]],
    texture2d<float> bokeh [[texture(3)]]) {
    float2 resolution = float2(u[0],u[1]);
    float2 xy = input.uv * resolution;
    float k = u[3];
    float offset = u[4];
    float yaw = u[8];
    float pitch = u[9];
    float scale = u[10];
    float build = u[11];
    float drain = u[12];
    float fuse = u[13];
    float sweep = u[17];
    float dim = u[20];
    float drift = u[21];
    float card = u[22];
    float reduced = u[25];
    float visible = u[27];
    float parallax = reduced > 0.5 ? 0.0 : sin(yaw + 0.33);
    ChromeZeroNumeralBackdrop backdrop = {resolution,drift*visible,u[19]*visible,1.0-visible*(1.0-dim)};
    float4 colour = float4(backdrop.render(xy));
    const float3 farDust[5] = {float3(62,164,6),float3(310,112,10),float3(358,390,8),float3(40,452,14),float3(272,518,8)};
    const float farAlpha[5] = {0.14,0.10,0.18,0.12,0.25};
    for (int i=0;i<5;i++) {
        float2 position = float2(offset,0.0)+farDust[i].xy*k;
        position += float2(-4.0*parallax+(reduced>0.5 ? 0.0 : drift*(i+2)),reduced>0.5 ? 0.0 : drift*3.0)*k;
        colour = chromeZeroOver(chromeZeroSprite(bokeh,xy,position,farDust[i].z*k,0.0,0.3*k)*farAlpha[i]*(1.0-dim)*visible,colour);
    }
    float2 cardSize = float2(324,216)*k;
    float2 cardCentre = float2(offset+170*k,404*k);
    cardCentre += float2(-40*(1-card)-6*parallax,64*(1-card)+6*u[24]+2*parallax)*k;
    float angle = (-10*(1-card)-1.5*parallax)*M_PI_F/180.0;
    float2 cardPoint = xy-cardCentre;
    cardPoint = float2(cardPoint.x*cos(angle)+cardPoint.y*sin(angle),-cardPoint.x*sin(angle)+cardPoint.y*cos(angle));
    cardPoint = cardPoint/(0.92+0.08*card)+cardSize*0.5;
    if (all(cardPoint>=0.0) && all(cardPoint<=cardSize)) {
        ChromeZeroNumeralCard cardShader = {cardImage,cardSize,sweep,parallax,build*(1-drain),fuse};
        colour = chromeZeroOver(cardShader.render(cardPoint)*u[23]*visible,colour);
    }
    ChromeZeroNumeralField field = {environment,float2(u[5],u[6]),u[7],u[2],7.0,
        float4(cos(yaw),sin(yaw),cos(pitch),sin(pitch)),float3(0.76,-0.1,0.3),
        scale,build,drain,fuse,u[14],float2(u[15],u[16]),sweep,u[18],u[19]};
    colour = chromeZeroOver(float4(field.render(xy))*visible,colour);
    const float3 stars[3] = {float3(254,494,56),float3(56,232,28),float3(360,172,18)};
    for (int i=0;i<3;i++) {
        float2 position = float2(offset,0.0)+stars[i].xy*k;
        position += float2(parallax*(i==0 ? 4.0 : -3.0),reduced>0.5 ? 0.0 : drift*(i+1))*k;
        float size = stars[i].z*k*u[28+i*4]*u[31+i*4];
        float rotation = reduced>0.5 ? 0.0 : u[29+i*4];
        colour = chromeZeroOver(chromeZeroSprite(sparkle,xy,position,size,rotation)*u[30+i*4]*visible,colour);
    }
    if (u[26]>0.0) {
        float3 pin = field.toWorld(float3(-0.42,0.86,0.26)*scale);
        float2 position = float2(u[5],u[6])+float2(pin.x,-pin.y)*u[7]*7.0/(7.0-pin.z);
        float size = (0.7+0.5*u[26])*k;
        colour.rgb += (chromeZeroSprite(sparkle,xy,position,48*size,0.0,3*k).rgb
            +chromeZeroSprite(sparkle,xy,position,32*size).rgb)*u[26]*visible;
    }
    const float3 nearDust[3] = {float3(46,344,14),float3(350,494,22),float3(288,216,10)};
    const float nearAlpha[3] = {0.12,0.10,0.18};
    for (int i=0;i<3;i++) {
        float2 position = float2(offset,0.0)+nearDust[i].xy*k;
        position += float2(-12.0*parallax+(reduced>0.5 ? 0.0 : drift*(i+2)),reduced>0.5 ? 0.0 : drift*6.0)*k;
        colour = chromeZeroOver(chromeZeroSprite(bokeh,xy,position,nearDust[i].z*k,0.0,2*k)*nearAlpha[i]*(1.0-dim)*visible,colour);
    }
    return float4(colour.rgb,1.0);
}
