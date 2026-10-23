"""Generate the original, offline Canopy Beat Hop artwork (Pillow, development only).

No source-game pixels are used. Run from any directory with Python 3 + Pillow.
The three platforms receive identical pre-rendered artwork to keep every frame cheap.
"""
from pathlib import Path
from math import sin, cos, pi, exp
import re
import shutil
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[6]
RN = Path(__file__).parent
SWIFT = ROOT / 'swift/NativeCook/Blocks/Playground/CanopyBeatHop/Assets'
KOTLIN = ROOT / 'kotlin/app/src/main/assets/canopybeathop'
S = 2
W, H = 393, 852
PALETTES = [
    ('day', '#9ED76E', '#FFFFAE', '#075C4A', '#0BB66B', '#126C37', '#77B83B', '#FFF20B', '#5EB19E'),
    ('night', '#357DDF', '#87FFFF', '#21125C', '#156EC8', '#27116A', '#4363CA', '#B7FFFF', '#4387CD'),
    ('violet', '#299DCB', '#A8F6FA', '#30104D', '#B007A3', '#451260', '#6549AC', '#FFFFFF', '#6B83C5'),
    ('amber', '#FFCA50', '#FFFFA3', '#71361C', '#D3790A', '#823C16', '#C7731D', '#FFF406', '#C89151'),
]

def rgb(c): return tuple(bytes.fromhex(c.lstrip('#')))
def mix(a,b,f): return tuple(round(x+(y-x)*f) for x,y in zip(a,b))
def gradient(w,h,stops):
    im=Image.new('RGBA',(w*S,h*S)); d=ImageDraw.Draw(im)
    for y in range(h*S):
        u=y/(h*S-1)
        for j in range(len(stops)-1):
            a,ca=stops[j]; b,cb=stops[j+1]
            if a<=u<=b:
                d.line([(0,y),(w*S,y)],fill=mix(rgb(ca),rgb(cb),(u-a)/(b-a))+(255,)); break
    return im

def path_points(data):
    tok=re.findall(r'[MLCQZ]|-?\d*\.?\d+',data); out=[]; i=0; p=(0,0); start=p
    while i<len(tok):
        op=tok[i]; i+=1
        if op=='Z': out.append(start); continue
        n={'M':2,'L':2,'Q':4,'C':6}[op]
        v=list(map(float,tok[i:i+n])); i+=n
        if op in ('M','L'):
            p=(v[0],v[1]); out.append(p)
            if op=='M': start=p
        else:
            x0,y0=p
            for k in range(1,25):
                t=k/24; q=1-t
                if op=='Q': x=q*q*x0+2*q*t*v[0]+t*t*v[2]; y=q*q*y0+2*q*t*v[1]+t*t*v[3]
                else: x=q**3*x0+3*q*q*t*v[0]+3*q*t*t*v[2]+t**3*v[4]; y=q**3*y0+3*q*q*t*v[1]+3*q*t*t*v[3]+t**3*v[5]
                out.append((x,y))
            p=out[-1]
    return out

def poly(im,points,color):
    target=Image.new('RGBA',im.size) if isinstance(color,tuple) and len(color)==4 and color[3]<255 else im
    ImageDraw.Draw(target).polygon([(round(x*S),round(y*S)) for x,y in points],fill=color)
    if target is not im: im.alpha_composite(target)
def path(im,data,color): poly(im,path_points(data),color)
def ellipse(im,box,color):
    target=Image.new('RGBA',im.size) if isinstance(color,tuple) and len(color)==4 and color[3]<255 else im
    ImageDraw.Draw(target).ellipse(tuple(round(v*S) for v in box),fill=color)
    if target is not im: im.alpha_composite(target)
def shaded_shape(im,points,top,bottom):
    mask=Image.new('L',im.size); ImageDraw.Draw(mask).polygon([(round(x*S),round(y*S)) for x,y in points],fill=255)
    lo=min(y for x,y in points); hi=max(y for x,y in points)
    g=Image.new('RGBA',im.size); d=ImageDraw.Draw(g)
    for y in range(max(0,int(lo*S)),min(im.height,int(hi*S)+1)):
        f=max(0,min(1,(y/S-lo)/max(1,hi-lo))); d.line([(0,y),(im.width,y)],fill=mix(rgb(top),rgb(bottom),f)+(255,))
    im.alpha_composite(Image.composite(g,Image.new('RGBA',im.size),mask))

def radial(im,cx,cy,rx,ry,color,alpha):
    layer=Image.new('RGBA',im.size); d=ImageDraw.Draw(layer)
    for k in range(60,0,-1):
        f=k/60
        d.ellipse(((cx-rx*f)*S,(cy-ry*f)*S,(cx+rx*f)*S,(cy+ry*f)*S),fill=rgb(color)+(round(alpha*(1-f)**1.8),))
    im.alpha_composite(layer)

def leaf(im,cx,cy,dx,dy,width,c1,c2):
    length=max(1,(dx*dx+dy*dy)**.5); nx=-dy/length; ny=dx/length
    sides=[]
    for sign in [1,-1]:
        pts=[]
        for k in range(25):
            t=k/24; bulge=sin(pi*t)**.85*width*sign
            pts.append((cx+dx*t+nx*bulge,cy+dy*t+ny*bulge-5*sin(pi*t)))
        sides.append(pts)
    shaded_shape(im,sides[0]+list(reversed(sides[1])),c1,c2)
    ridge=[(cx+dx*k/24,cy+dy*k/24-5*sin(pi*k/24)) for k in range(25)]
    shaded_shape(im,sides[0]+list(reversed(ridge)),c1,c2)
    # A narrow specular ridge and a dark underside make a folded leaf.
    ImageDraw.Draw(im).line([(x*S,y*S) for x,y in ridge], fill=rgb(c1)+(150,),width=S)

def shrub(im,x,y,scale,p):
    _,_,_,_,_,dark,light,*_=p
    ellipse(im,(x-28*scale,y-3*scale,x+29*scale,y+8*scale),rgb(dark)+(90,))
    for j in range(10):
        a=(-170+j*19)*pi/180; le=(19+(j*7)%17)*scale
        leaf(im,x,y,cos(a)*le,-abs(sin(a))*le,(4+(j%3))*scale,light,dark)
    leaf(im,x,y,3*scale,-30*scale,5*scale,'#9ACB43' if p[0]=='day' else light,dark)

def scene(p):
    name,sky,horizon,ground,bottom,dark,light,sun,mountain=p
    im=gradient(W,H,[(0,sky),(.26,horizon),(.27,ground),(.52,ground),(1,bottom)])
    radial(im,202,137,198,130,horizon,90)
    if name in ('day','amber'):
        radial(im,207,153,84,99,sun,75)
        disk=[(207+55*cos(j*pi/64),153+66*sin(j*pi/64)) for j in range(128)]
        mask=Image.new('L',im.size); ImageDraw.Draw(mask).polygon([(x*S,y*S) for x,y in disk],fill=255)
        disk_light=gradient(W,H,[(0,'#FFB21B'),(.103,'#FFB21B'),(.18,sun),(.258,horizon),(1,horizon)])
        im.alpha_composite(Image.composite(disk_light,Image.new('RGBA',im.size),mask))
        radial(im,207,200,78,52,horizon,180)
    else:
        radial(im,215,16,83,96,'#B1FFFF',95)
        moon_mask=Image.new('L',im.size); m=ImageDraw.Draw(moon_mask)
        m.ellipse((161*S,-50*S,269*S,82*S),fill=255)
        if name=='violet': m.ellipse((177*S,-60*S,275*S,60*S),fill=0)
        moon=Image.new('RGBA',im.size,'#BCFFFF' if name=='night' else '#FFFFFF')
        im.alpha_composite(Image.composite(moon,Image.new('RGBA',im.size),moon_mask))
        for j in range(31):
            x=(j*113+23)%393; y=(j*47+13)%210
            ellipse(im,(x,y,x+1.4+(j%3)*.5,y+1.4+(j%3)*.5),(203,255,255,170))
        for x,y in [(105,198),(218,151),(320,57)]:
            path(im,f'M {x-13} {y+9} L {x+2} {y-3} L {x} {y+2} Z',(190,255,249,125))
    cloud=Image.new('RGBA',im.size)
    for x,y,l in [(71,12,193),(255,68,116),(9,146,128)]:
        path(cloud,f'M {x} {y} Q {x+l*.3} {y-4} {x+l*.57} {y-1} Q {x+l*.73} {y-7} {x+l} {y+1} L {x} {y+3} Z',(243,255,224,42))
    im.alpha_composite(cloud)
    # The source's small triangular mountains sit below the branch.
    peaks=[(-35,225,5,117,65,228),(26,227,52,144,119,228),(71,227,89,161,126,228),(104,229,151,185,181,229),(155,230,184,201,204,230),(245,231,270,190,305,231),(290,232,349,155,421,234)]
    for x0,y0,x1,y1,x2,y2 in peaks:
        shaded_shape(im,[(x0,y0),(x1,y1),(x2,y2)],horizon,mountain)
        poly(im,[(x1,y1),(x1-9,y1+41),(x2,y2)],rgb(mountain)+(255,))
    path(im,'M -30 222 Q 50 218 105 232 Q 226 231 316 222 Q 368 218 426 237 L 426 290 L -30 290 Z',rgb(light)+(230,))
    # A curved hill, then an uninterrupted broad floor with subtle side facets.
    hill=path_points('M -35 257 Q 140 201 301 232 Q 371 238 427 266 L 427 852 L -35 852 Z')
    shaded_shape(im,hill,ground,bottom)
    poly(im,[(37,245),(151,359),(60,852),(-40,852),(-40,285)],rgb(ground)+(23,))
    poly(im,[(304,239),(239,370),(335,852),(430,852),(423,281)],rgb(dark)+(16,))
    radial(im,203,841,254,515,bottom,90)
    # Two smoothly shaded umbrella crowns, with a dark convex bottom rim.
    for x,y,z in [(278,239,1),(315,244,.60)]:
        trunk=path_points(f'M {x-5*z} {y} Q {x+3*z} {y-24*z} {x-11*z} {y-45*z} Q {x-35*z} {y-49*z} {x-38*z} {y-66*z} L {x-33*z} {y-68*z} Q {x-30*z} {y-51*z} {x-11*z} {y-52*z} Q {x-10*z} {y-67*z} {x-15*z} {y-77*z} L {x-10*z} {y-79*z} Q {x} {y-58*z} {x+6*z} {y-50*z} Q {x+14*z} {y-66*z} {x+12*z} {y-77*z} L {x+16*z} {y-76*z} Q {x+19*z} {y-60*z} {x+10*z} {y-44*z} Q {x+5*z} {y-18*z} {x+12*z} {y} Z')
        shaded_shape(im,trunk,light,dark)
        cap=path_points(f'M {x-56*z} {y-64*z} C {x-65*z} {y-150*z} {x+21*z} {y-154*z} {x+25*z} {y-80*z} Q {x-9*z} {y-66*z} {x-56*z} {y-64*z} Z')
        shaded_shape(im,cap,light,dark)
        # Clipped radial highlight gives the canopy a spherical surface.
        mask=Image.new('L',im.size); ImageDraw.Draw(mask).polygon([(x*S,y*S) for x,y in cap],fill=255)
        glow=Image.new('RGBA',im.size); radial(glow,x-30*z,y-125*z,58*z,43*z,light,200)
        im.alpha_composite(Image.composite(glow,Image.new('RGBA',im.size),mask))
        path(im,f'M {x-56*z} {y-66*z} Q {x-8*z} {y-72*z} {x+25*z} {y-82*z} L {x+25*z} {y-77*z} Q {x-9*z} {y-63*z} {x-56*z} {y-62*z} Z',rgb(dark)+(235,))
    # A broad sweeping right trunk, not a striped flat pole.
    trunk=path_points('M 332 253 Q 322 213 337 154 Q 353 83 390 -5 L 429 -14 Q 393 68 377 124 Q 356 194 360 249 L 382 264 Q 354 257 332 253 Z')
    shaded_shape(im,trunk,dark,ground)
    path(im,'M 370 155 Q 371 115 397 91 L 419 64 L 426 64 Q 412 95 388 114 L 378 160 Z',rgb(dark)+(255,))
    # Branch, leaves and a plump singing bird.
    branch=path_points('M -22 137 Q 58 131 122 105 Q 149 94 169 82 Q 147 105 117 115 Q 63 139 -22 147 Z')
    shaded_shape(im,branch,light,dark)
    path(im,'M 97 121 Q 122 125 140 143 Q 126 126 106 115 Z',rgb(dark)+(255,))
    for x,y,dx,dy in [(19,132,-4,-29),(31,131,23,-16),(53,127,12,-13),(126,105,23,-9),(130,102,-7,-34),(145,100,20,1),(139,136,11,19),(66,163,2,21),(63,161,11,-12),(31,176,-21,-27),(346,20,-26,-13),(330,17,-24,1),(315,13,7,-21)]:
        leaf(im,x,y,dx,dy,4.2,light,dark)
    bird=path_points('M 68 99 Q 85 104 92 72 Q 91 38 104 34 Q 121 28 130 50 L 137 53 L 130 56 Q 137 82 124 97 Q 111 113 84 109 L 77 106 Q 90 107 94 101 Q 76 103 68 99 Z')
    shaded_shape(im,bird,light,dark)
    path(im,'M 106 58 Q 93 60 95 75 Q 91 85 83 89 Q 103 94 114 81 Q 119 68 106 58 Z',rgb(light)+(190,))
    for j in range(18):
        x=107+(j*11)%21; y=67+(j*7)%31
        ellipse(im,(x,y,x+1.7,y+2.5),rgb(light)+(210,))
    ellipse(im,(114,48,118,53),rgb(horizon)+(230,))
    for x,y,dx,dy in [(108,36,-1,-9),(111,36,2,-10),(113,36,3,-7)]:
        ImageDraw.Draw(im).line([(x*S,y*S),((x+dx)*S,(y+dy)*S)],fill=rgb(dark)+(255,),width=S)
    for x,y in [(119,38),(131,35),(137,43)]:
        path(im,f'M {x} {y} L {x+5} {y-6} L {x+2} {y+1} Z',rgb(dark)+(240,))
    ImageDraw.Draw(im).line([(109*S,107*S),(108*S,115*S),(116*S,106*S),(117*S,113*S)],fill=rgb(dark)+(255,),width=S)
    for x,y,dx,dy,w in [(-15,-16,20,102,15),(3,-12,34,51,20),(18,-20,45,51,16),(9,-9,8,83,10),(386,-18,-25,32,14),(403,-6,-20,57,12)]:
        leaf(im,x,y,dx,dy,w,dark,dark)
    for x,y,z in [(9,255,.60),(42,267,.68),(66,287,.74),(49,328,1.15),(-12,306,1.05),(148,228,.83),(272,247,.62),(317,285,.76),(392,265,1.1),(405,365,1.4)]: shrub(im,x,y,z,p)
    return im.convert('RGB')

def courier():
    im=Image.new('RGBA',(100*S,132*S))
    # One small leaf behind the cap, as in the gameplay silhouette.
    leaf(im,61,15,29,-8,2.5,'#92DB48','#1D8F38')
    # A compact arched body with a cyan saddle and two golden feet.
    body=path_points('M 31 85 Q 50 80 69 87 L 72 125 L 61 126 L 60 113 Q 50 105 40 113 L 39 126 L 28 125 Z')
    shaded_shape(im,body,'#ECFF43','#A0DA20')
    ellipse(im,(36,110,65,118),'#28AE6E')
    path(im,'M 37 116 Q 51 110 65 116 L 63 122 L 37 122 Z','#15665E')
    for x in [28,61]:
        shaded_shape(im,[(x,117),(x+11,117),(x+11,130),(x,130)],'#FFEC46','#EFB618')
        ImageDraw.Draw(im).line([(x*S,129*S),((x+11)*S,129*S)],fill='#FFFF84',width=S)
    # A physically shaded sphere: upper warm light and lower emissive rim.
    px=im.load()
    for y in range(7*S,100*S):
        for x in range(6*S,95*S):
            nx=(x/S-50)/44; ny=(y/S-53)/46
            r=nx*nx+ny*ny
            if r>1: continue
            z=(1-r)**.5
            light=max(0,-.22*nx-.57*ny+.78*z)
            c=mix(rgb('#065E32'),rgb('#39C94D'),min(1,.2+.72*light))
            rim=(1-z)**2 *(.45+max(0,ny)*.55)
            c=mix(c,rgb('#CAFF4B'),min(.9,rim))
            glow=max(0,ny-.45)*.55
            c=mix(c,rgb('#70FF53'),glow)
            px[x,y]=c+(255,)
    ellipse(im,(29,8,71,24),'#E9B538')
    ellipse(im,(33,8,68,19),'#FFD656')
    # Gold belt follows the curvature of the spherical back.
    pts=path_points('M 6 40 Q 50 49 94 40 L 94 51 Q 50 64 6 51 Z')
    shaded_shape(im,pts,'#FFCE30','#EAB325')
    pts=path_points('M 5 49 Q 50 63 95 49 L 95 59 Q 50 76 5 59 Z')
    shaded_shape(im,pts,'#FFFF64','#EBD523')
    path(im,'M 5 50 Q 50 65 95 50 L 95 52 Q 50 67 5 52 Z','#FFFFB3')
    for j in range(17):
        x=7+j*5.15; y=55+11*(1-((x-50)/45)**2)
        poly(im,[(x,y-2.5),(x+3.4,y-2.1),(x+3.4,y+2),(x,y+2.2)],'#297E35')
        ImageDraw.Draw(im).line([(x*S,(y+2)*S),((x+3.4)*S,(y+2.1)*S)],fill='#B6E938',width=S)
    for x in [4,94]: ellipse(im,(x-1,44,x+3,50),'#FFED4D')
    # Rectangular reflected lights on the sides of the sphere.
    path(im,'M 7 64 L 12 66 L 15 75 L 11 74 Z','#EDFFEB')
    path(im,'M 8 78 L 14 80 L 16 83 L 12 82 Z','#B0FFD0')
    path(im,'M 88 65 L 94 63 L 91 73 L 86 74 Z','#EDFFEB')
    path(im,'M 85 81 L 91 79 L 89 83 L 83 85 Z','#D4FFDA')
    return im

if __name__=='__main__':
    SWIFT.mkdir(parents=True,exist_ok=True); KOTLIN.mkdir(parents=True,exist_ok=True)
    items={f'{p[0]}.png':scene(p) for p in PALETTES}; items['courier.png']=courier()
    for name,im in items.items():
        file=RN/name; im.save(file,optimize=True)
        shutil.copyfile(file,SWIFT/f'CanopyBeatHop-{name}')
        shutil.copyfile(file,KOTLIN/f'canopybeathop_{name}')
        print(f'{name}: {file.stat().st_size:,} bytes')
