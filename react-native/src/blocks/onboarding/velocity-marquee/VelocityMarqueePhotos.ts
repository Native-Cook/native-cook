/** One photo on a row: an Unsplash id and its orientation, which sets the tile's width. */
export type MarqueePhoto = {
  id: string;
  shape: 'portrait' | 'landscape';
};

/** Photo URL at a width in pixels, cropped to the photo's own aspect. */
export function photoUrl(id: string, width: number) {
  return `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=80`;
}

const p = (id: string): MarqueePhoto => ({ id: `photo-${id}`, shape: 'portrait' });
const l = (id: string): MarqueePhoto => ({ id: `photo-${id}`, shape: 'landscape' });

/**
 * Three rows of twelve, top to bottom. Chrome-and-pastel snapshots, pastel still lifes and soft
 * 3D renders are dealt across the rows so neighbours never share a palette.
 */
export const ROWS: MarqueePhoto[][] = [
  [
    p('1568191009535-7d8b407d389a'),
    l('1690627931183-991bd45dc2f4'),
    l('1622737133809-d95047b9e673'),
    p('1619922800069-098b291b8f17'),
    l('1693225113452-8ab5cf5f6e7f'),
    p('1619045119136-349759036541'),
    l('1551499779-ee50f1aa4d25'),
    l('1622547748225-3fc4abd2cca0'),
    p('1724606854879-9321f9340a89'),
    l('1690627932216-3843a4b4764c'),
    p('1653481351422-91ceed9c94bd'),
    l('1499796683658-b659bc751db1'),
  ],
  [
    l('1627637819794-fba32f82be16'),
    p('1563291074-2bf8677ac0e5'),
    l('1702285566327-2df5bc5b4e67'),
    p('1712917841321-a491f1924d09'),
    l('1672080070762-764c74ee1227'),
    p('1562048048-86d659689440'),
    l('1544194215-541c2d3561a4'),
    l('1702285566373-bb7744adcbe7'),
    p('1521133573892-e44906baee46'),
    l('1651611243377-2c15b94ad613'),
    p('1743428287676-c3ea53dabf6d'),
    l('1650943574955-ac02c65cfc71'),
  ],
  [
    p('1579546928686-286c9fbde1ec'),
    l('1671519821564-ced7e41ee7ae'),
    l('1702285566188-00af76abde38'),
    p('1609414558965-3bec66c06de7'),
    l('1458682625221-3a45f8a844c7'),
    l('1617791160505-6f00504e3519'),
    p('1653549892798-c8596ad08ee6'),
    l('1671509429700-fe3137bb9ab9'),
    p('1629786270823-3440bd45d1d5'),
    l('1618172193763-c511deb635ca'),
    l('1680779487014-2b587dba40c6'),
    l('1757167301187-5777536c564c'),
  ],
];
