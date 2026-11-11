"""Bake each creature's lid map (512x1024): top half = skin painted over the eyes (Laplace fill from
the surrounding skin, so the lid keeps the face's shading), bottom half = eye map
(R = v, 0 at the eye's top contour .. 1 at its bottom, per column; G = inside the eye; B = column height / 128)."""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as nd

ART = sys.argv[1]
OUT = sys.argv[2]
EYES = {
    'charizard': [dict(box=(226, 128, 258, 146), seed=(245, 139))],
    'blastoise': [dict(box=(296, 80, 345, 108), seed=(318, 92))],
    'bulbasaur': [dict(box=(66, 266, 114, 330), seed=(76, 300)), dict(box=(208, 276, 294, 339), seed=(240, 315))],
}

def bake(name):
    im = np.asarray(Image.open(f'{ART}/{name}.png').convert('RGBA')).astype(np.float64) / 255
    rgb, a = im[..., :3], im[..., 3]
    H, W = a.shape
    eye_all = np.zeros((H, W), bool)
    vmap = np.zeros((H, W)); hmap = np.zeros((H, W))
    nonskin = np.zeros((H, W), bool)
    for e in EYES[name]:
        x0, y0, x1, y1 = e['box']
        sub = rgb[y0:y1, x0:x1]; sa = a[y0:y1, x0:x1]
        # Skin: the median of the box's border ring.
        ring = np.concatenate([sub[0], sub[-1], sub[:, 0], sub[:, -1]])
        skin = np.median(ring, axis=0)
        dist = np.linalg.norm(sub - skin, axis=-1)
        cand = (dist > 0.15) & (sa > 0.5)
        lab, _ = nd.label(cand)
        sx, sy = e['seed'][0] - x0, e['seed'][1] - y0
        assert lab[sy, sx] > 0, (name, e, sub[sy, sx], skin)
        m = lab == lab[sy, sx]
        m = nd.binary_dilation(nd.binary_fill_holes(nd.binary_closing(m, iterations=2)), iterations=1)
        mask = np.zeros((H, W), bool); mask[y0:y1, x0:x1] = m
        # Strokes and their soft edges near the eye (brows, lashes) take no part in the fill.
        X0, Y0, X1, Y1 = max(x0 - 10, 0), max(y0 - 10, 0), x1 + 10, y1 + 10
        nonskin[Y0:Y1, X0:X1] |= (np.linalg.norm(rgb[Y0:Y1, X0:X1] - skin, axis=-1) > 0.1) & (a[Y0:Y1, X0:X1] > 0.5)
        eye_all |= mask
        # Contours per column, smoothed along x so the lid's edge is a curve, not a staircase.
        cols = [x for x in range(x0, x1) if mask[:, x].any()]
        T = np.array([np.nonzero(mask[:, x])[0].min() for x in cols], float)
        B = np.array([np.nonzero(mask[:, x])[0].max() + 1 for x in cols], float)
        k = np.exp(-np.arange(-3, 4) ** 2 / (2 * 1.3 ** 2)); k /= k.sum()
        pad = lambda a: np.concatenate([[a[0]] * 3, a, [a[-1]] * 3])
        Ts = np.convolve(pad(T), k, 'valid'); Bs = np.convolve(pad(B), k, 'valid')
        # Never pull the contour inside the drawn eye.
        Ts = np.minimum(Ts, T + 0.5); Bs = np.maximum(Bs, B - 0.5)
        for x, t, b in zip(cols, Ts, Bs):
            h = b - t
            for y in range(int(np.floor(t)), int(np.ceil(b))):
                if not mask[y, x]: continue
                vmap[y, x] = np.clip((y + 0.5 - t) / h, 0, 1)
                hmap[y, x] = h
        print(name, e['box'], 'skin', np.round(skin * 255), 'pixels', mask.sum())
    # Around each eye the lid feathers out over 4.5 px (G), so the fill meets the art with no edge:
    # above an eye it is the top contour (v 0), below it the bottom one (v 1).
    dist = nd.distance_transform_edt(~eye_all)
    cover = np.clip(1 - dist / 4.5, 0, 1) * (a > 0.01)
    # Strokes drawn beside an eye (a lash flick, a brow) stay drawn: the lid only feathers over skin.
    cover[nonskin & (dist > 2)] = 0
    cy = {}
    for x in range(W):
        ys = np.nonzero(eye_all[:, x])[0]
        if len(ys): cy[x] = (ys.min() + ys.max()) / 2
    for y, x in zip(*np.nonzero((cover > 0) & ~eye_all)):
        nx = min(cy, key=lambda c: abs(c - x))
        vmap[y, x] = 0.0 if y < cy[nx] else 1.0
        hmap[y, x] = hmap[:, nx].max()
    # Laplace fill: unknown = eyes grown by 6 px plus nearby dark strokes; transparent pixels take no part.
    grown = nd.binary_dilation(eye_all, iterations=6)
    dark = (rgb.sum(-1) < 1.5) | nonskin
    near = nd.binary_dilation(eye_all, iterations=8)
    unknown = (grown | (dark & near)) & (a > 0.01)
    known = (a > 0.5) & ~unknown
    fill = rgb.copy()
    fill[unknown] = rgb[known].mean(0)
    # Strokes further out are not skin either: they neither take part nor feed the fill.
    opaque = ((a > 0.5) & ~dark) | unknown
    for _ in range(4000):
        acc = np.zeros_like(fill); n = np.zeros((H, W))
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ok = np.roll(opaque, (dy, dx), (0, 1))
            acc += np.roll(fill, (dy, dx), (0, 1)) * ok[..., None]; n += ok
        new = acc / np.maximum(n, 1)[..., None]
        fill[unknown] = new[unknown]
    top = np.dstack([fill, np.ones((H, W))])
    bottom = np.dstack([vmap, cover, np.minimum(hmap / 128, 1), np.ones((H, W))])
    out = np.concatenate([top, bottom], 0)
    Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA').save(f'{OUT}/{name}-lids.png', optimize=True)

for name in EYES: bake(name)
