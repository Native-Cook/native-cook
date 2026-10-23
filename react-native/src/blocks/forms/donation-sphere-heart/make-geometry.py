"""Regenerate point tables from the Swift reference. Generated PNG textures are authored assets."""
import json
import math
from pathlib import Path
import re
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
SWIFT = ROOT / 'swift/NativeCook/Blocks/Forms/DonationSphereHeart/DonationSphereHeart.swift'
def hash_value(i):
    v = math.sin(i * 12.9898 + 7.233) * 43758.5453
    return v - math.floor(v)
def geometry():
    source = SWIFT.read_text()
    def table(label, seed):
        text = source.split(f'static let {label}:')[1].split('let data:')[1].split(']')[1]
        rows = re.findall(r'\((-?\d+),(-?\d+),(\d+),(\d+)\)', text)
        return [dict(x=float(x), y=float(y), z=(hash_value(i + seed) - 0.5) * (74 if label == 'medium' else 56), radius=float(r), material=int(m))
                for i, (x, y, r, m) in enumerate(rows)]
    medium = table('medium', 61)
    sparse = table('sparse', 122)
    outline = []
    for i in range(160):
        a = i * 2 * math.pi / 160
        outline.append((16 * math.sin(a) ** 3 * 9, -(13 * math.cos(a) - 5 * math.cos(2*a) - 2 * math.cos(3*a) - math.cos(4*a)) * 7.5 + 1))
    def inside(x, y):
        result = False
        j = len(outline) - 1
        for i, a in enumerate(outline):
            b = outline[j]
            if (a[1] > y) != (b[1] > y) and x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]:
                result = not result
            j = i
        return result
    dense, candidate = [], 0
    while len(dense) < 120:
        x, y = hash_value(candidate * 3 + 1001) * 288 - 144, hash_value(candidate * 3 + 1002) * 232 - 103
        candidate += 1
        if not inside(x, y) or any(math.hypot(q['x'] - x, (q['y'] + 17) / 1.25 - y) < 13 for q in dense):
            continue
        i = len(dense)
        material = medium[i]['material'] if i < len(medium) else [2,6,3,6,2,5,3,0,1,7][i % 10]
        radius = (7 + hash_value(i + 173) * 10) * (0.66 if material == 4 else 1)
        dense.append(dict(x=x, y=y * 1.25 - 17, z=(hash_value(i + 823) - 0.5) * 92, radius=radius, material=material))
    assert len(medium) == 48 and len(sparse) == 12
    return json.dumps(dict(medium=medium, dense=dense, sparse=sparse), separators=(',', ':')).encode()


if __name__ == '__main__':
    data = geometry()
    for path in [HERE / 'assets/donation-spheres.json', ROOT / 'kotlin/app/src/main/assets/donationsphereheart/donationsphereheart_spheres.json']:
        path.write_bytes(data)
    print('Wrote identical Swift sphere tables to both ports.')
