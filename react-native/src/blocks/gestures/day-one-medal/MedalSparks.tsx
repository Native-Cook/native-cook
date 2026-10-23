import { BlendMode, BlurStyle, Canvas, PaintStyle, Picture, Skia, StrokeCap } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue, useFrameCallback, useSharedValue } from 'react-native-reanimated';

const LIFE = 1.25;
const RING = 0.75;
// Air drag, as a rate: a spark has lost most of its speed within a third of a second.
const DRAG = 3.4;
const GRAVITY = 520;

type Spark = { angle: number; speed: number; life: number; width: number; late: number };

// Fixed, so the strike looks the same on every run and in every build of the block.
function sparks(count: number): Spark[] {
  let seed = 7919;
  const next = () => {
    seed = (Math.imul(seed, 1664525) + 1013904223) | 0;
    return (seed >>> 0) / 4294967296;
  };
  return Array.from({ length: count }, () => ({
    // All the way round, with more of them thrown upwards.
    angle: next() * Math.PI * 2 - (next() < 0.45 ? Math.PI / 2 - (next() - 0.5) * 1.6 : 0),
    speed: 420 + 980 * next() * next(),
    life: 0.45 + 0.8 * next(),
    width: 1 + 1.5 * next(),
    late: 0.06 * next(),
  }));
}

const SPARKS = sparks(72);

const line = Skia.Paint();
line.setAntiAlias(true);
line.setStyle(PaintStyle.Stroke);
line.setStrokeCap(StrokeCap.Round);
line.setBlendMode(BlendMode.Plus);

const ring = Skia.Paint();
ring.setAntiAlias(true);
ring.setStyle(PaintStyle.Stroke);
ring.setBlendMode(BlendMode.Plus);
ring.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 5, true));

const WHITE_HOT = Skia.Color('#FFF3D6');
const ORANGE = Skia.Color('#FF8A2A');
const EMBER = Skia.Color('#C2410C');

type Props = {
  /** The GPU supplies the HDR strike; continuous forging/engraving sparks stay above it. */
  burst?: boolean;
  width: number;
  height: number;
  /** Where the numeral is, in this canvas. */
  centre: SharedValue<{ x: number; y: number }>;
  spitting: SharedValue<number>;
  spot: SharedValue<{ x: number; y: number; on: number }>;
  scale: number;
  /** The clock the strike was timed on, and its reading at the strike. */
  clock: SharedValue<number>;
  struck: SharedValue<number>;
};

/** The strike's debris: a ring of light running out from the medal and sparks thrown off the numeral. */
type Fleck = { born: number; x: number; y: number; vx: number; vy: number; life: number };

export function MedalSparks({ width, height, centre, scale, clock, struck, spitting, spot, burst = true }: Props) {
  const debris = useSharedValue<Fleck[]>([]);
  const last = useSharedValue(-1);
  const budget = useSharedValue(0);
  const serial = useSharedValue(0);
  useFrameCallback(() => {
    'worklet';
    const now = clock.get();
    const dt = last.get() < 0 ? 0 : Math.min(0.05, now - last.get());
    last.set(now);
    if (dt <= 0) return;
    const source = spot.get();
    const strength = Math.min(spitting.get(), source.on);
    const before = debris.get();
    const live: Fleck[] = [];
    for (let i = 0; i < before.length; i++) {
      if (now >= before[i].born && now - before[i].born < before[i].life) live.push(before[i]);
    }
    let owed = strength > 0.01 ? budget.get() + dt * (3 + 19 * strength) : 0;
    while (owed >= 1 && live.length < 14) {
      owed--;
      const n = serial.get() + 1;
      serial.set(n);
      const seed = Math.sin(n * 127.1) * 43758.5453;
      const r = seed - Math.floor(seed);
      const angle = -Math.PI * (0.12 + 0.76 * r);
      const speed = (45 + r * 95) * scale;
      live.push({ born: now, x: source.x, y: source.y, vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed, life: 0.22 + r * 0.18 });
    }
    budget.set(owed);
    debris.set(live);
  });
  const picture = useDerivedValue(() => {
    'worklet';
    const now = clock.get();
    const age = now - struck.get();
    const origin = centre.get();
    const particles = debris.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
        for (let i = 0; i < particles.length; i++) {
          const particle = particles[i];
          const elapsed = now - particle.born;
          const remaining = 1 - elapsed / particle.life;
          const x = particle.x + particle.vx * elapsed;
          const y = particle.y + particle.vy * elapsed + 180 * scale * elapsed * elapsed;
          line.setColor(remaining > 0.65 ? WHITE_HOT : ORANGE);
          line.setAlphaf(remaining * remaining * 0.85);
          line.setStrokeWidth(0.85 * scale);
          canvas.drawLine(x, y, x - particle.vx * 0.018, y - particle.vy * 0.018, line);
        }
        if (!burst || age < 0 || age > LIFE) return recorder.finishRecordingAsPicture();

        if (age < RING) {
          const k = age / RING;
          const out = 1 - Math.pow(1 - k, 3);
          ring.setColor(WHITE_HOT);
          ring.setAlphaf(0.55 * (1 - k) * (1 - k));
          ring.setStrokeWidth((10 - 7 * k) * scale);
          canvas.drawCircle(origin.x, origin.y, (70 + 250 * out) * scale, ring);
        }

        for (let i = 0; i < SPARKS.length; i++) {
          const s = SPARKS[i];
          const t = age - s.late;
          if (t <= 0 || t > s.life) continue;
          const k = t / s.life;
          // Distance run under drag, and what is left of the speed for the streak's length.
          const run = (s.speed * (1 - Math.exp(-DRAG * t))) / DRAG;
          const left = s.speed * Math.exp(-DRAG * t);
          const cos = Math.cos(s.angle);
          const sin = Math.sin(s.angle);
          const x = origin.x + cos * run * scale;
          const y = origin.y + (sin * run + 0.5 * GRAVITY * t * t) * scale;
          const vx = cos * left;
          const vy = sin * left + GRAVITY * t;
          const tail = 0.028 * scale;
          line.setColor(k < 0.25 ? WHITE_HOT : k < 0.6 ? ORANGE : EMBER);
          line.setAlphaf((1 - k) * (1 - k));
          line.setStrokeWidth(s.width * scale * (1 - 0.5 * k));
          canvas.drawLine(x, y, x - vx * tail, y - vy * tail, line);
        }
    return recorder.finishRecordingAsPicture();
  });

  return (
    <Canvas style={{ position: 'absolute', left: 0, top: 0, width, height }} pointerEvents="none">
      <Picture picture={picture} />
    </Canvas>
  );
}
