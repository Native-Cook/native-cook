/** Colours, layout (measured from the source, 402 pt wide) and the gifts on offer. */
/** Colours and layout measured from source 09 (screen 250 px → 402 pt, 1.61 pt/px). */
export const COLORS = {
  // Gate 2b (16 Oct): stage darkened to the source's own exposure (swatch dE <= 5).
  stage: '#605133',
  stageEdge: '#302816',
  floor: '#332A13',
  floorLow: '#2D2511',
  floorLine: 'rgba(255,240,190,0.08)',
  pocket: '#251902',
  /** The pocket's top fades in from the stage: the source shows no hard upper edge. */
  pocketFade: 'rgba(37,25,2,0)',
  pocketLow: '#4F3803',
  ink: '#FFFFFF',
  mute: '#B9B3A0',
  hint: '#CFCABB',
  /** The stage barely takes the centred card's hue: the source keeps it warm whichever card is chosen. */
  hueTint: 0.05,
  stageWarm: 0.3,
  stageWarmInk: '#E07A2A',
  pocketLight: 'rgba(255,224,60,1)',
  glowCore: '#F7B812',
  cardLift: 0.2,
  offerWarm: 'rgba(214,200,140,0.28)',
  /** The card art darkened (black) and multiplied by its own hue, to the source's exposure. */
  cardShade: [0.2, 0.25],
  cardShadeInk: '#8A4C00',
  yellow: '#FFD23F',
  yellowDeep: '#F2A900',
  outline: 'rgba(255,255,255,0.42)',
  button: '#FCD43C',
  buttonInk: '#2A2112',
  step: '#D8D3C4',
} as const;

/** Spacing scale (pt), from the source's own gaps: 4-pt steps up to the offer screen's 28-pt gutter. */
export const SPACE = { xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24, gutter: 28 } as const;

/** Measured on the rectified reference (ref/gift-slot-drop-{rest,a,b}.png, 1206 × 2622 = 3 px/pt). */
export const LAYOUT = {
  headerTop: 63,
  /** The offer screen's close and Help sit higher (source frame b). */
  offerHeaderTop: 25,
  headerLeft: 14,
  headerRight: 50,
  giftY: 144,
  /** The bow sits right on our box; the box's body is centred like the source's icon. */
  giftDx: -2.5,
  giftSize: 47,
  titleY: 214,
  titleSize: 25.5,
  titleLine: 34,
  cardY: 427,
  cardW: 158,
  cardH: 206,
  cardR: 12,
  spacing: 172,
  turnDeg: 42,
  sideScale: 1,
  chevronY: 605,
  /** The lit pocket under the card: a rounded opening that runs down to the floor. */
  pocketW: 180,
  pocketTop: 634,
  floorY: 669,
  slotY: 678,
  slotW: 170,
  slotH: 24,
  /** The glow sits low in the pocket, near its bottom (source rest frame). */
  glowDrop: 21,
  /** The pocket's inner glow, relative to the slot line: a tall bloom whose brightest part sits at the pocket's bottom. */
  glowTop: -14,
  glowH: 92,
  /** How far the pocket's rounded bottom sits below the floor line. */
  pocketDepth: 28,
  hintY: 764.5,
  offerY: 366,
  offerW: 292,
  offerH: 505,
  howTop: 669,
  stepTop: 716,
  buttonTop: 780,
  buttonH: 60,
  buttonW: 357,
} as const;

export type Gift = {
  id: string;
  /** Name typed in the card's wordmark and used in copy. */
  name: string;
  /** Wordmark text as printed on the card (cased like the brand). */
  wordmark: string;
  headline: string;
  line: string;
  cap: string;
  use: string;
  expires: string;
  ink: string;
  /** Background of the carousel while this card is centred. */
  glow: string;
};

/** Invented shops in the style of the source's four cards. Artwork lives in assets/ (generated from the frames). */
// Order as in the source (frames 001–013): a white card, the light-blue parcels card, the burger card, the red card.
export const GIFTS: Gift[] = [
  { id: 'white', name: 'Loomwell', wordmark: 'loomwell', headline: '50%', line: 'cashback', cap: 'up to $20', use: 'Use AERO Pay at Loomwell', expires: 'Expires on 27 Nov', ink: '#22201C', glow: '#E9E4DA' },
  { id: 'parcels', name: 'Nimbus', wordmark: 'nimbus', headline: '50%', line: 'cashback', cap: 'up to $25', use: 'Use AERO Pay to shop on Nimbus', expires: 'Expires on 27 Nov', ink: '#1A2433', glow: '#9CCADC' },
  { id: 'burger', name: 'Bunbox', wordmark: 'BUNBOX', headline: '50%', line: 'cashback', cap: 'up to $10', use: 'Order on Bunbox and pay with AERO Pay', expires: 'Expires on 27 Nov', ink: '#FFFFFF', glow: '#E8612A' },
  { id: 'red', name: 'Volta', wordmark: 'volta', headline: '50%', line: 'cashback on', cap: 'mobile recharge', use: 'Recharge your Volta number with AERO Pay', expires: 'Expires on 27 Nov', ink: '#FFFFFF', glow: '#D9141C' },
];

/** The source opens on the burger card. */
export const START_INDEX = 2;
