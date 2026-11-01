// Pop-up Quest Card — the four quests' paper illustrations, each a stack of flat layers drawn once.
//
// Every scene lives in a 1000 × 700 box (y down). Layer 0 is the backdrop (it stands at the back of
// the floor when the book opens), layer 1 is the floor itself (it tilts back), and the rest are cut-outs
// that stand up on the floor along their bottom edge, `hinge` (a y in the box).
import {
  createPicture,
  PaintStyle,
  Skia,
  StrokeCap,
  type SkCanvas,
  type SkPicture,
} from "@shopify/react-native-skia";

const p = (c: string, a = 1) => {
  const q = Skia.Paint();
  q.setAntiAlias(true);
  q.setColor(Skia.Color(c));
  q.setAlphaf(a);
  return q;
};
const s = (c: string, w: number) => {
  const q = p(c);
  q.setStyle(PaintStyle.Stroke);
  q.setStrokeWidth(w);
  q.setStrokeCap(StrokeCap.Round);
  return q;
};
const poly = (cv: SkCanvas, pts: number[], c: string) => {
  const b = Skia.PathBuilder.Make();
  b.moveTo(pts[0], pts[1]);
  for (let i = 2; i < pts.length; i += 2) b.lineTo(pts[i], pts[i + 1]);
  b.close();
  cv.drawPath(b.detach(), p(c));
};
const cloud = (cv: SkCanvas, x: number, y: number, k: number) => {
  cv.drawCircle(x, y, 34 * k, p("#FFFFFF"));
  cv.drawCircle(x + 40 * k, y + 8 * k, 28 * k, p("#FFFFFF"));
  cv.drawCircle(x - 38 * k, y + 10 * k, 24 * k, p("#FFFFFF"));
  cv.drawRRect(
    Skia.RRectXY(
      Skia.XYWHRect(x - 62 * k, y + 6 * k, 130 * k, 30 * k),
      15 * k,
      15 * k,
    ),
    p("#FFFFFF"),
  );
};
const pine = (cv: SkCanvas, x: number, y: number, k: number, c: string) => {
  poly(cv, [x, y - 120 * k, x + 46 * k, y - 30 * k, x - 46 * k, y - 30 * k], c);
  poly(cv, [x, y - 80 * k, x + 56 * k, y + 4 * k, x - 56 * k, y + 4 * k], c);
  cv.drawRect(Skia.XYWHRect(x - 8 * k, y, 16 * k, 22 * k), p("#8A5A3C"));
};
const pic = (draw: (cv: SkCanvas) => void): SkPicture =>
  createPicture(draw, Skia.XYWHRect(-100, -100, 1200, 900));

export type Layer = { pic: SkPicture; hinge: number };
export type Quest = {
  title: string;
  price: string;
  bg: string;
  tint: string;
  cats: [string, string][];
  acts: { icon: string; title: string; sub: string }[];
  layers: Layer[];
};

const pirate: Layer[] = [
  {
    hinge: 430,
    pic: pic((cv) => {
      const g = Skia.Paint();
      g.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: 0, y: 0 },
          { x: 0, y: 430 },
          [Skia.Color("#9ED8F0"), Skia.Color("#E3F4FA")],
          null,
          0,
        ),
      );
      cv.drawRect(Skia.XYWHRect(0, 0, 1000, 440), g);
      cv.drawCircle(800, 110, 58, p("#FFE07A"));
      cloud(cv, 220, 120, 1);
      cloud(cv, 640, 70, 0.7);
      cloud(cv, 980, 190, 0.8);
    }),
  },
  {
    hinge: 700,
    pic: pic((cv) => {
      cv.drawRect(Skia.XYWHRect(0, 430, 1000, 270), p("#4CC3D9"));
      for (let i = 0; i < 6; i++) {
        const y = 470 + i * 40;
        for (let x = 40; x < 960; x += 120)
          cv.drawLine(
            x + (i % 2) * 60,
            y,
            x + 40 + (i % 2) * 60,
            y,
            s("#BFEFF7", 5),
          );
      }
      cv.drawOval(Skia.XYWHRect(120, 540, 760, 160), p("#F6D79C"));
    }),
  },
  {
    hinge: 520,
    pic: pic((cv) => {
      cv.drawOval(Skia.XYWHRect(330, 330, 420, 260), p("#F0C985"));
      poly(
        cv,
        [
          420, 520, 420, 330, 470, 330, 470, 300, 500, 300, 500, 330, 560, 330,
          560, 260, 600, 230, 640, 260, 640, 520,
        ],
        "#E7A07A",
      );
      cv.drawRect(Skia.XYWHRect(445, 270, 50, 250), p("#D98A68"));
      poly(cv, [440, 270, 470, 210, 500, 270], "#E25A4E");
      poly(cv, [600, 230, 640, 160, 680, 230], "#E25A4E");
      cv.drawRect(Skia.XYWHRect(560, 400, 40, 60), p("#7A4A3A"));
      cv.drawLine(640, 160, 640, 110, s("#3A2A20", 5));
      poly(cv, [640, 110, 700, 125, 640, 140], "#2A2A35");
    }),
  },
  {
    hinge: 610,
    pic: pic((cv) => {
      poly(cv, [140, 520, 420, 520, 380, 610, 180, 610], "#8A5232");
      cv.drawRect(Skia.XYWHRect(150, 540, 260, 10), p("#6B3C22"));
      cv.drawLine(280, 520, 280, 300, s("#5A3A26", 9));
      poly(cv, [285, 310, 400, 330, 395, 470, 285, 480], "#FFFFFF");
      poly(cv, [275, 330, 190, 470, 275, 470], "#FFF4E2");
      poly(cv, [280, 300, 340, 280, 280, 260], "#2A2A35");
      cv.drawCircle(310, 280, 6, p("#FFFFFF"));
    }),
  },
  {
    hinge: 700,
    pic: pic((cv) => {
      cv.drawLine(840, 700, 870, 420, s("#9A6A44", 18));
      for (let i = 0; i < 5; i++) {
        const a = -0.6 + i * 0.55;
        const pt = Skia.PathBuilder.Make();
        pt.moveTo(870, 420);
        pt.quadTo(
          870 + Math.cos(a) * 90,
          400 + Math.sin(a) * 30 - 60,
          870 + Math.cos(a) * 170,
          430 + Math.sin(a) * 60,
        );
        cv.drawPath(pt.detach(), s("#3FAE5A", 28));
      }
      cv.drawRRect(
        Skia.RRectXY(Skia.XYWHRect(60, 620, 130, 80), 14, 14),
        p("#B5652F"),
      );
      cv.drawRRect(
        Skia.RRectXY(Skia.XYWHRect(60, 590, 130, 50), 24, 24),
        p("#C9773A"),
      );
      cv.drawRect(Skia.XYWHRect(112, 600, 26, 40), p("#F2C14E"));
      for (let x = 30; x < 1000; x += 90)
        cv.drawCircle(x, 712, 34, p("#7FD6E4"));
    }),
  },
];

const rocket: Layer[] = [
  {
    hinge: 440,
    pic: pic((cv) => {
      const g = Skia.Paint();
      g.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: 0, y: 0 },
          { x: 0, y: 440 },
          [Skia.Color("#BFE6C8"), Skia.Color("#EEF8E6")],
          null,
          0,
        ),
      );
      cv.drawRect(Skia.XYWHRect(0, 0, 1000, 450), g);
      cloud(cv, 760, 120, 0.9);
      cloud(cv, 200, 200, 0.6);
      cv.drawCircle(300, 90, 26, p("#FFFFFF", 0.9));
    }),
  },
  {
    hinge: 700,
    pic: pic((cv) => {
      cv.drawRect(Skia.XYWHRect(0, 440, 1000, 260), p("#8BCB5B"));
      cv.drawOval(Skia.XYWHRect(380, 560, 260, 70), p("#6FB047"));
    }),
  },
  {
    hinge: 520,
    pic: pic((cv) => {
      cv.drawOval(Skia.XYWHRect(-200, 380, 700, 300), p("#A6D876"));
      cv.drawOval(Skia.XYWHRect(560, 360, 760, 340), p("#9AD06A"));
      poly(cv, [150, 500, 210, 420, 270, 500], "#FF8A4C");
      poly(cv, [760, 470, 820, 390, 880, 470], "#4C8DFF");
    }),
  },
  {
    hinge: 600,
    pic: pic((cv) => {
      cv.drawOval(Skia.XYWHRect(430, 540, 160, 70), p("#FFFFFF", 0.9));
      cv.drawCircle(470, 560, 40, p("#FFFFFF"));
      cv.drawCircle(560, 565, 36, p("#FFFFFF"));
      poly(cv, [470, 560, 450, 600, 510, 560], "#FF8A4C");
      cv.drawRRect(
        Skia.RRectXY(Skia.XYWHRect(470, 260, 80, 280), 40, 40),
        p("#F4F6FA"),
      );
      poly(cv, [470, 330, 510, 200, 550, 330], "#FF5A4E");
      cv.drawCircle(510, 360, 22, p("#5EC3E8"));
      cv.drawCircle(510, 360, 22, s("#C9D3E0", 6));
      poly(cv, [470, 470, 420, 540, 470, 530], "#FF5A4E");
      poly(cv, [550, 470, 600, 540, 550, 530], "#FF5A4E");
      poly(cv, [490, 540, 510, 600, 530, 540], "#FFC23D");
    }),
  },
  {
    hinge: 700,
    pic: pic((cv) => {
      for (let i = 0; i < 4; i++)
        cv.drawCircle(120 + i * 70, 690, 58, p(i % 2 ? "#4FA34A" : "#5CB352"));
      for (let i = 0; i < 4; i++)
        cv.drawCircle(700 + i * 80, 700, 62, p(i % 2 ? "#4FA34A" : "#5CB352"));
      cv.drawLine(860, 620, 860, 470, s("#5A3A26", 6));
      poly(cv, [860, 470, 930, 495, 860, 520], "#FFC23D");
    }),
  },
];

const space: Layer[] = [
  {
    hinge: 450,
    pic: pic((cv) => {
      const g = Skia.Paint();
      g.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: 0, y: 0 },
          { x: 0, y: 450 },
          [Skia.Color("#6E7FD8"), Skia.Color("#B9C6F5")],
          null,
          0,
        ),
      );
      cv.drawRect(Skia.XYWHRect(0, 0, 1000, 460), g);
      for (let i = 0; i < 22; i++)
        cv.drawCircle(
          40 + ((i * 173) % 920),
          30 + ((i * 97) % 360),
          4 + (i % 3) * 2,
          p("#FFFFFF", 0.9),
        );
      cv.drawCircle(760, 140, 80, p("#FFB86B"));
      cv.drawOval(Skia.XYWHRect(650, 120, 220, 44), s("#FFE2B8", 10));
    }),
  },
  {
    hinge: 700,
    pic: pic((cv) => {
      cv.drawRect(Skia.XYWHRect(0, 450, 1000, 250), p("#D9DDF2"));
      cv.drawOval(Skia.XYWHRect(160, 520, 140, 40), p("#C3C8E6"));
      cv.drawOval(Skia.XYWHRect(640, 600, 180, 50), p("#C3C8E6"));
    }),
  },
  {
    hinge: 540,
    pic: pic((cv) => {
      cv.drawOval(Skia.XYWHRect(80, 400, 360, 220), p("#E8EAF8"));
      cv.drawOval(Skia.XYWHRect(560, 380, 420, 240), p("#E2E5F6"));
      cv.drawCircle(220, 470, 20, p("#CDD2EC"));
      cv.drawCircle(760, 450, 26, p("#CDD2EC"));
    }),
  },
  {
    hinge: 560,
    pic: pic((cv) => {
      cv.drawOval(Skia.XYWHRect(380, 300, 260, 80), p("#9AA6C9"));
      cv.drawCircle(510, 300, 60, p("#9EE6F0", 0.95));
      cv.drawOval(Skia.XYWHRect(330, 330, 360, 70), p("#C7CFE6"));
      for (let i = 0; i < 5; i++)
        cv.drawCircle(370 + i * 70, 365, 8, p("#FFD36B"));
      poly(cv, [430, 400, 590, 400, 650, 560, 370, 560], "#FFF6C9");
    }),
  },
  {
    hinge: 700,
    pic: pic((cv) => {
      cv.drawOval(Skia.XYWHRect(40, 610, 220, 110), p("#B7BEDF"));
      cv.drawOval(Skia.XYWHRect(760, 630, 200, 90), p("#B7BEDF"));
      cv.drawLine(180, 620, 180, 480, s("#5A5F7A", 6));
      poly(cv, [180, 480, 250, 505, 180, 530], "#FF5A7A");
    }),
  },
];

const castle: Layer[] = [
  {
    hinge: 430,
    pic: pic((cv) => {
      const g = Skia.Paint();
      g.setShader(
        Skia.Shader.MakeLinearGradient(
          { x: 0, y: 0 },
          { x: 0, y: 430 },
          [Skia.Color("#F7C9A6"), Skia.Color("#FCEBDD")],
          null,
          0,
        ),
      );
      cv.drawRect(Skia.XYWHRect(0, 0, 1000, 440), g);
      cv.drawCircle(220, 120, 50, p("#FFE9A8"));
      cloud(cv, 700, 110, 0.8);
    }),
  },
  {
    hinge: 700,
    pic: pic((cv) => {
      cv.drawRect(Skia.XYWHRect(0, 430, 1000, 270), p("#A9D27C"));
      poly(cv, [470, 700, 530, 700, 560, 430, 520, 430], "#E9D4A6");
    }),
  },
  {
    hinge: 500,
    pic: pic((cv) => {
      cv.drawOval(Skia.XYWHRect(-150, 360, 560, 260), p("#8FC56A"));
      cv.drawOval(Skia.XYWHRect(600, 340, 640, 280), p("#86BE60"));
    }),
  },
  {
    hinge: 560,
    pic: pic((cv) => {
      cv.drawRect(Skia.XYWHRect(370, 320, 280, 240), p("#C9B8E8"));
      for (let i = 0; i < 5; i++)
        cv.drawRect(Skia.XYWHRect(370 + i * 60, 300, 40, 24), p("#C9B8E8"));
      cv.drawRect(Skia.XYWHRect(330, 240, 80, 320), p("#B6A3DE"));
      cv.drawRect(Skia.XYWHRect(610, 240, 80, 320), p("#B6A3DE"));
      poly(cv, [320, 245, 370, 160, 420, 245], "#7B5CC9");
      poly(cv, [600, 245, 650, 160, 700, 245], "#7B5CC9");
      cv.drawRRect(
        Skia.RRectXY(Skia.XYWHRect(475, 440, 70, 120), 35, 35),
        p("#6B4A3A"),
      );
      cv.drawLine(370, 160, 370, 110, s("#3A2A20", 5));
      poly(cv, [370, 110, 420, 122, 370, 136], "#FF5A4E");
      cv.drawRRect(
        Skia.RRectXY(Skia.XYWHRect(355, 300, 24, 36), 12, 12),
        p("#5C4A8A"),
      );
      cv.drawRRect(
        Skia.RRectXY(Skia.XYWHRect(637, 300, 24, 36), 12, 12),
        p("#5C4A8A"),
      );
    }),
  },
  {
    hinge: 700,
    pic: pic((cv) => {
      pine(cv, 120, 640, 1.2, "#3E8E5A");
      pine(cv, 240, 680, 0.9, "#4A9E62");
      pine(cv, 860, 650, 1.1, "#3E8E5A");
      for (let x = 30; x < 1000; x += 80)
        cv.drawCircle(x, 716, 30, p("#7DB859"));
    }),
  },
];

export const QUESTS: Quest[] = [
  {
    title: "Pirate Isle Quest",
    price: "From $50/day",
    bg: "#DDF1F8",
    tint: "#2E9FC0",
    cats: [
      ["cube-outline", "Treasure"],
      ["boat-outline", "Sailing"],
      ["map-outline", "Maps"],
      ["musical-notes-outline", "Shanties"],
    ],
    acts: [
      {
        icon: "compass-outline",
        title: "Dig for the island's treasure",
        sub: "9 AM · Sea shanty songs",
      },
      {
        icon: "restaurant-outline",
        title: "10 AM Island lunch",
        sub: "3 beach games",
      },
    ],
    layers: pirate,
  },
  {
    title: "Rocket Camp",
    price: "From $42/day",
    bg: "#E6F4DA",
    tint: "#4C9A3B",
    cats: [
      ["rocket-outline", "Rockets"],
      ["flask-outline", "Science"],
      ["bonfire-outline", "Campfire"],
      ["star-outline", "Stars"],
    ],
    acts: [
      {
        icon: "construct-outline",
        title: "Build a water rocket",
        sub: "10 AM · teams of four",
      },
      {
        icon: "telescope-outline",
        title: "Night sky tour",
        sub: "8 PM · with telescopes",
      },
    ],
    layers: rocket,
  },
  {
    title: "Space Explorers",
    price: "From $58/day",
    bg: "#E3E7FA",
    tint: "#5A6BD0",
    cats: [
      ["planet-outline", "Planets"],
      ["rocket-outline", "Missions"],
      ["sparkles-outline", "Stars"],
      ["hardware-chip-outline", "Robots"],
    ],
    acts: [
      {
        icon: "planet-outline",
        title: "Moon base workshop",
        sub: "11 AM · build your rover",
      },
      {
        icon: "sparkles-outline",
        title: "Planetarium show",
        sub: "3 PM · 45 minutes",
      },
    ],
    layers: space,
  },
  {
    title: "Castle Knights",
    price: "From $46/day",
    bg: "#FBEADB",
    tint: "#8A63CF",
    cats: [
      ["shield-outline", "Shields"],
      ["flag-outline", "Banners"],
      ["leaf-outline", "Forest"],
      ["trophy-outline", "Tourney"],
    ],
    acts: [
      {
        icon: "shield-outline",
        title: "Shield painting",
        sub: "10 AM · take it home",
      },
      {
        icon: "trophy-outline",
        title: "Little knights tourney",
        sub: "2 PM · prizes for all",
      },
    ],
    layers: castle,
  },
];
