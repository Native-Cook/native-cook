"""Bake Charizard's tail flame silhouette as a signed distance map, for the blaze in its move.

The blaze is the drawn flame scaled up about its base, so its outline has to stay a smooth curve when
it is blown up 2-3x; read from the art itself it shows the art's pixel steps and colour speckle.
This takes the same pixels CREATURE_LIFE's flameMask takes (flame colours inside the flame's ellipse,
above the cut at the tail tip), smooths them, fills the small holes where the body shows through,
and stores the signed distance to that outline (art px, positive inside), 4 texels per art px:
grey = 0.5 + d / (2 * RANGE), the same in R, G and B so a colour space conversion leaves it alone.

usage: python3 bake-flame.py <assets dir> <out dir>"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as nd

ART, OUT = sys.argv[1], sys.argv[2]
# HabitatRig's Charizard flame: ellipse centre and radii (art px).
AT, RADIUS = (451.0, 345.0), (38.0, 68.0)
# The map's box (art px) and resolution; HabitatRig's flame.sdf must match.
BOX = (408, 272, 96, 140)
SCALE = 4
RANGE = 6.0

im = np.asarray(Image.open(f'{ART}/charizard.png').convert('RGBA')).astype(np.float64) / 255
rgb, a = im[..., :3], im[..., 3]
x0, y0, w, h = BOX
ys, xs = np.mgrid[0:h * SCALE, 0:w * SCALE]
# Art pixel centres sampled at texel centres.
ax = x0 + (xs + 0.5) / SCALE - 0.5
ay = y0 + (ys + 0.5) / SCALE - 0.5
def up(c):
    return nd.map_coordinates(c, [ay, ax], order=3, mode='nearest')
r, b, al = up(rgb[..., 0]), up(rgb[..., 2]), np.clip(up(a), 0, 1)
px, py = ax, ay
e = ((px - AT[0]) / RADIUS[0]) ** 2 + ((py - AT[1]) / RADIUS[1]) ** 2
inside = (e < 0.905) & (py < AT[1] + RADIUS[1] * 0.86)
hot = (r - b) / np.maximum(al, 1e-3) > 0.08
mask = inside & hot & (al > 0.5)
# Smooth the outline (sigma ~0.8 art px), drop specks, fill holes smaller than ~6 art px across.
# The foot, where the art cuts the flame at the tail's tip, is smoothed harder: blown up it would show
# the cut's teeth.
foot = np.clip((ay - (AT[1] + RADIUS[1] * 0.70)) / 8.0, 0, 1)
soft = nd.gaussian_filter(mask.astype(float), 0.8 * SCALE) * (1 - foot) + nd.gaussian_filter(mask.astype(float), 2.4 * SCALE) * foot
mask = soft > 0.5
lab, n = nd.label(mask)
if n > 1:
    sizes = nd.sum(mask, lab, range(1, n + 1))
    mask = lab == (1 + int(np.argmax(sizes)))
holes = nd.binary_fill_holes(mask) & ~mask
lab, n = nd.label(holes)
for i in range(1, n + 1):
    if (lab == i).sum() < (6 * SCALE) ** 2:
        mask |= lab == i
d = (nd.distance_transform_edt(mask) - nd.distance_transform_edt(~mask)) / SCALE
d = nd.gaussian_filter(d, 0.6 * SCALE)
g = np.clip(0.5 + d / (2 * RANGE), 0, 1)
g8 = np.round(g * 255).astype(np.uint8)
Image.fromarray(np.dstack([g8, g8, g8, np.full_like(g8, 255)]), 'RGBA').save(f'{OUT}/charizard-flame.png')
print('saved', g8.shape, 'inside px', int(mask.sum()))
