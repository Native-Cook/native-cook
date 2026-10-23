import {
  Blur,
  Circle,
  Group,
  Image,
  Paint,
  Paragraph,
  Path,
  RadialGradient,
  Rect,
  RoundedRect,
  Shadow,
  Skia,
  type SkImage,
  type SkParagraph,
  TextAlign,
  vec,
} from '@shopify/react-native-skia';
import { Platform } from 'react-native';
import { type DerivedValue, useDerivedValue } from 'react-native-reanimated';

import {
  type Layout,
  NAME_LINE,
  NAME_SIZE,
  NAME_TRACKING,
  type Progress,
  YEAR_FOLDED_SIZE,
  YEAR_LINE,
  YEAR_SIZE,
  cardPose,
  headingPose,
  photoPose,
} from './motion';

// MARK: Colours

export const SCREEN = '#FFFFFF';
/**
 * The gradient: a near-white base with a blue light in the middle and a mint one low on the right;
 * folded into the card they make its blue-grey middle, as in the design.
 */
const BASE = '#F4F7FA';
const BLUE = '#B2C6DE';
const MINT = '#C3E0CF';
/** A photo still loading. */
const PLACEHOLDER = '#E6E9EE';
export const ICON = '#9AA0A8';
export const HEART = '#FF4D6A';
const WHITE = '#FFFFFF';
const YEAR = 'rgba(255,255,255,0.85)';
const HEADING_SHADOW = 'rgba(0,0,0,0.06)';
const HEADING_SHADOW_RADIUS = 12;
const PHOTO_SHADOW_ALPHA = 0.18;
const PHOTO_SHADOW_RADIUS = 18;
const PHOTO_SHADOW_Y = 8;
/** A photo in its cell keeps this share of its shadow. */
const CELL_SHADOW = 0.3;
const CARD_SHADOW_ALPHA = 0.16;
const CARD_SHADOW_RADIUS = 30;
const CARD_SHADOW_Y = 16;
/** The soft shadow under each icon, as in the design. */
export const ICON_SHADOW = 'rgba(0,0,0,0.14)';
export const ICON_SHADOW_RADIUS = 3;
export const ICON_SHADOW_Y = 4;
/** Skia blurs by a Gaussian's sigma; a shadow's or a blur's radius, as the Swift version gives it, is about twice that. */
export const SIGMA = 0.5;

/**
 * The two lights of the gradient, in its frame's coordinates (so they squash with it into the card):
 * centre and radii as shares of the frame's width and height. Each is full colour out to GLOW_CORE of
 * its radius, then fades out.
 */
const GLOWS = [
  { color: BLUE, x: 0.3, y: 0.45, rx: 0.85, ry: 0.34 },
  { color: MINT, x: 0.72, y: 0.64, rx: 0.6, ry: 0.28 },
];
const GLOW_CORE = 0.15;

const FONT = Platform.select({ ios: ['.AppleSystemUIFont'], default: ['sans-serif'] });

// MARK: Text

/** One line of text, left at 0, set in a line box of `line` points (or its natural height). */
function line(text: string, size: number, weight: number, color: string, tracking: number, lineHeight?: number) {
  const builder = Skia.ParagraphBuilder.Make({ textAlign: TextAlign.Left, maxLines: 1 });
  builder.pushStyle({
    color: Skia.Color(color),
    fontFamilies: FONT,
    fontSize: size,
    fontStyle: { weight },
    letterSpacing: tracking,
    ...(lineHeight ? { heightMultiplier: lineHeight / size, halfLeading: true } : {}),
    shadows: [{ color: Skia.Color(HEADING_SHADOW), blurRadius: HEADING_SHADOW_RADIUS * SIGMA, offset: { x: 0, y: 0 } }],
  });
  builder.addText(text);
  const paragraph = builder.build();
  paragraph.layout(10000);
  return paragraph;
}

export type HeadingText = {
  name: SkParagraph;
  yearTop: SkParagraph;
  yearCell: SkParagraph;
  /** The name's width at NAME_SIZE, for the folded scale. */
  nameWidth: number;
};

export function headingText(name: string, year: string): HeadingText {
  const title = line(name, NAME_SIZE, 800, WHITE, NAME_TRACKING, NAME_LINE);
  return {
    name: title,
    yearTop: line(year, YEAR_SIZE, 600, YEAR, 0, YEAR_LINE),
    yearCell: line(year, YEAR_FOLDED_SIZE, 600, YEAR, 0),
    nameWidth: title.getLongestLine(),
  };
}

// MARK: Photos

const cache = new Map<string, SkImage>();

export function cachedPhoto(address: string) {
  return cache.get(address) ?? null;
}

/** Downloads a photo once and keeps it decoded. */
export async function loadPhoto(address: string): Promise<SkImage | null> {
  const hit = cache.get(address);
  if (hit) return hit;
  try {
    const data = await Skia.Data.fromURI(address);
    const encoded = Skia.Image.MakeImageFromEncoded(data);
    if (!encoded) return null;
    // Decoded now, so the first frame that draws it does not stall.
    const image = encoded.makeNonTextureImage() ?? encoded;
    cache.set(address, image);
    return image;
  } catch {
    return null;
  }
}

// MARK: The scene: the white screen, the gradient in its moving frame, the heading and the photos

type SceneProps = {
  layout: Layout;
  progress: DerivedValue<Progress>;
  text: HeadingText;
  images: (SkImage | null)[];
  order: number[];
};

/** Everything the album draws, from one set of progress values: the backdrop, the heading, the photos. */
export function Scene({ layout, progress, text, images, order }: SceneProps) {
  return (
    <Group>
      <Rect x={0} y={0} width={layout.width} height={layout.height} color={SCREEN} />
      <Backdrop layout={layout} progress={progress} />
      <Heading layout={layout} progress={progress} text={text} />
      {order.map((i) => (
        <Photo key={i} index={i} layout={layout} progress={progress} image={images[i]} />
      ))}
    </Group>
  );
}

function Backdrop({ layout, progress }: { layout: Layout; progress: DerivedValue<Progress> }) {
  const pose = useDerivedValue(() => cardPose(progress.get().card, layout));
  const shape = useDerivedValue(() => {
    const p = pose.get();
    return { rect: p.rect, rx: p.radius, ry: p.radius };
  });
  const shadowColor = useDerivedValue(() => `rgba(0,0,0,${CARD_SHADOW_ALPHA * pose.get().shadow})`);
  const feather = useDerivedValue(() => pose.get().feather);
  return (
    <Group>
      <RoundedRect rect={shape} color={BASE}>
        <Shadow dx={0} dy={CARD_SHADOW_Y} blur={CARD_SHADOW_RADIUS * SIGMA} color={shadowColor} />
      </RoundedRect>
      <Group
        layer={
          <Paint>
            <Blur blur={feather} mode="decal" />
          </Paint>
        }
      >
        <Group clip={shape}>
          <RoundedRect rect={shape} color={BASE} />
          {GLOWS.map((glow) => (
            <Glow key={glow.color} glow={glow} pose={pose} />
          ))}
        </Group>
      </Group>
    </Group>
  );
}

/** A light: a circle of radius rx·width, squashed to ry·height, full colour in its core, fading out. */
function Glow({ glow, pose }: { glow: (typeof GLOWS)[number]; pose: DerivedValue<ReturnType<typeof cardPose>> }) {
  const transform = useDerivedValue(() => {
    const r = pose.get().rect;
    const rx = glow.rx * r.width;
    const ry = glow.ry * r.height;
    return [{ translateX: r.x + glow.x * r.width }, { translateY: r.y + glow.y * r.height }, { scaleY: ry / Math.max(1, rx) }];
  });
  const radius = useDerivedValue(() => Math.max(1, glow.rx * pose.get().rect.width));
  return (
    <Group transform={transform}>
      <Circle cx={0} cy={0} r={radius}>
        <RadialGradient
          c={vec(0, 0)}
          r={radius}
          colors={[glow.color, glow.color, `${glow.color}00`]}
          positions={[0, GLOW_CORE, 1]}
        />
      </Circle>
    </Group>
  );
}

/** The name, the year over it (open) and the year under it (folded), sliding and shrinking together. */
function Heading({ layout, progress, text }: { layout: Layout; progress: DerivedValue<Progress>; text: HeadingText }) {
  const pose = useDerivedValue(() => headingPose(progress.get().card, layout));
  const nameTransform = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.name.x }, { translateY: p.name.y }, { scale: p.scale }];
  });
  const yearTopTransform = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.yearTop.x }, { translateY: p.yearTop.y }, { scale: p.scale }];
  });
  const yearTopOpacity = useDerivedValue(() => pose.get().yearTopOpacity);
  const yearCellTransform = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.yearCell.x }, { translateY: p.yearCell.y }];
  });
  const yearCellOpacity = useDerivedValue(() => pose.get().yearCellOpacity);
  const yearTopWidth = text.yearTop.getLongestLine();
  const yearCellWidth = text.yearCell.getLongestLine();
  const yearCellHeight = text.yearCell.getHeight();
  return (
    <Group>
      {/* A paragraph paints with its own paint, so its fade is a layer's. */}
      <Group transform={yearTopTransform} layer={<Paint opacity={yearTopOpacity} />}>
        <Paragraph paragraph={text.yearTop} x={-yearTopWidth / 2} y={-YEAR_LINE / 2} width={10000} />
      </Group>
      <Group transform={nameTransform}>
        <Paragraph paragraph={text.name} x={-text.nameWidth / 2} y={-NAME_LINE / 2} width={10000} />
      </Group>
      <Group transform={yearCellTransform} layer={<Paint opacity={yearCellOpacity} />}>
        <Paragraph paragraph={text.yearCell} x={-yearCellWidth / 2} y={-yearCellHeight / 2} width={10000} />
      </Group>
    </Group>
  );
}

/** One photo: aspect-filled in its frame, rounded, with its shadow; grey while it loads. */
function Photo({
  index,
  layout,
  progress,
  image,
}: {
  index: number;
  layout: Layout;
  progress: DerivedValue<Progress>;
  image: SkImage | null;
}) {
  const pose = useDerivedValue(() => {
    const p = progress.get();
    return photoPose(index, p.photos[index], layout, p.driftT);
  });
  const transform = useDerivedValue(() => {
    const p = pose.get();
    return [{ translateX: p.x }, { translateY: p.y }, { rotate: (p.tilt * Math.PI) / 180 }];
  });
  const shape = useDerivedValue(() => {
    const p = pose.get();
    return { rect: { x: -p.width / 2, y: -p.height / 2, width: p.width, height: p.height }, rx: p.radius, ry: p.radius };
  });
  const frame = useDerivedValue(() => shape.get().rect);
  const lift = useDerivedValue(() => CELL_SHADOW + (1 - CELL_SHADOW) * pose.get().lift);
  const shadowColor = useDerivedValue(() => `rgba(0,0,0,${PHOTO_SHADOW_ALPHA * lift.get()})`);
  const shadowBlur = useDerivedValue(() => PHOTO_SHADOW_RADIUS * SIGMA * lift.get());
  const shadowY = useDerivedValue(() => PHOTO_SHADOW_Y * lift.get());
  return (
    <Group transform={transform}>
      <RoundedRect rect={shape} color={PLACEHOLDER}>
        <Shadow dx={0} dy={shadowY} blur={shadowBlur} color={shadowColor} />
      </RoundedRect>
      {image ? (
        <Group clip={shape}>
          <Image image={image} rect={frame} fit="cover" />
        </Group>
      ) : null}
    </Group>
  );
}

// MARK: Icons (20 × 20 boxes, stroked at 1.6 points once drawn at ICON_SIZE)

/** Share: an open box with an arrow up out of it. */
export const SHARE =
  'M7.2 7.6L5.8 7.6Q4.4 7.6 4.4 9L4.4 16.6Q4.4 18 5.8 18L14.2 18Q15.6 18 15.6 16.6L15.6 9Q15.6 7.6 14.2 7.6L12.8 7.6' +
  'M10 12.4L10 2.2M7.1 5L10 2.1L12.9 5';
/** Heart, also filled when liked. */
export const HEART_PATH =
  'M10 17C6.4 14.6 2.6 11.4 2.6 7.7C2.6 5.2 4.4 3.4 6.6 3.4C8.1 3.4 9.3 4.2 10 5.4' +
  'C10.7 4.2 11.9 3.4 13.4 3.4C15.6 3.4 17.4 5.2 17.4 7.7C17.4 11.4 13.6 14.6 10 17Z';
/** Info: a circle with an "i" (its dot is INFO_DOT, filled). */
export const INFO = 'M2.2 10A7.8 7.8 0 1 0 17.8 10A7.8 7.8 0 1 0 2.2 10ZM8.7 9L10 9L10 14M8.6 14L11.4 14';
export const INFO_DOT = { x: 10, y: 6.5, r: 0.95 };
/** Trash: a lid with a handle, a tapered can with two ribs. */
export const TRASH =
  'M3.2 5L16.8 5M7.6 5L7.6 3.6Q7.6 2.6 8.6 2.6L11.4 2.6Q12.4 2.6 12.4 3.6L12.4 5' +
  'M4.9 5L5.8 16.6Q5.9 18 7.2 18L12.8 18Q14.1 18 14.2 16.6L15.1 5M8.4 8.2L8.4 14.8M11.6 8.2L11.6 14.8';

/** A stroked glyph with its soft shadow, in the 20-point box drawn `unit` points a box point. */
export function Glyph({ d, color, stroke, unit }: { d: string; color: string; stroke: number; unit: number }) {
  return (
    <Path path={d} color={color} style="stroke" strokeWidth={stroke / unit} strokeCap="round" strokeJoin="round">
      <Shadow dx={0} dy={ICON_SHADOW_Y / unit} blur={(ICON_SHADOW_RADIUS * SIGMA) / unit} color={ICON_SHADOW} />
    </Path>
  );
}
