/** Every colour, size and string of the block. Layout values are points measured on a 402 × 874 screen. */
import { Platform, type TextStyle } from 'react-native';

export const COLOR = {
  page: '#FFFFFF',
  pageClear: 'rgba(255,255,255,0)',
  ink: '#111216',
  inkSoft: '#5B5E68',
  rule: '#E7E7EC',
  panel: '#8E90A8',
  panelLow: '#3D4049',
  panelInk: '#FFFFFF',
  panelInkSoft: 'rgba(255,255,255,0.78)',
  pill: '#FFFFFF',
  pillInk: '#111216',
  toolbar: '#F1F1F4',
  toolbarInk: '#2A2C33',
  stackIcon: ['#FF9A3D', '#FF4F8B', '#3D63F2'],
  accent: '#3D63F2',
  // The gradient field (top-left cobalt, violet middle, magenta-pink right, cobalt bottom).
  fieldCobalt: '#2F45F0',
  fieldIndigo: '#4A35D8',
  fieldViolet: '#8A43D6',
  fieldMagenta: '#E0569A',
  fieldDeep: '#1E2BB8',
  // The rim's sweep: cold to warm, then back to cold so the seam is invisible.
  rim: ['#7FD4FF', '#7C8CFF', '#C77DFF', '#FF7EB6', '#FFC58A', '#7FD4FF'],
  summaryInk: '#FFFFFF',
  summaryInkSoft: 'rgba(255,255,255,0.8)',
  creditInk: 'rgba(255,255,255,0.94)',
  creditInkSoft: 'rgba(255,255,255,0.7)',
  iconDisc: '#3D63F2',
  iconGlyph: '#FFFFFF',
  closeFill: 'rgba(255,255,255,0.16)',
  sweep: ['rgba(255,255,255,0)', 'rgba(255,255,255,0.55)', 'rgba(255,255,255,0)'],
  // The card one step back in the stack: the page's white seen in the field's light, one shade down (opaque).
  stack: '#D8D9F4',
  pageShadow: '#120A40',
  cardGlow: '#B9A6FF',
  mote: '#FFFFFF',
  bloom: '#E9E2FF',
  // The reader's AI badge in the footer credit (the source shows a colourful icon there): a disc, concentric with the pill.
  creditIcon: ['#FF7A9C', '#FF4F8B', '#8E5BFF'],
  // The "Summarizing for you…" label: a dim base the shimmer runs through (≥ 3:1 on the pink field).
  thinkingBase: 'rgba(255,255,255,0.68)',
  closeInk: '#FFFFFF',
  finger: 'rgba(255,255,255,0.34)',
  fingerRing: 'rgba(255,255,255,0.75)',
  // The highlighter: a lavender marker in the AI's hue, so a marked sentence reads as "picked by the summary".
  marker: '#DCD4FF',
  // A flying sentence's backing, so its words read over anything they cross: the page's white while it is still ink,
  // then a pill of the field's indigo at 70 % once it has turned white.
  chipPaper: 'rgba(255,255,255,0.94)',
  chipPill: 'rgba(74,53,216,0.7)',
  // Non-key text washes out under this veil while pinching.
  veil: '#FFFFFF',
  // Entity chips, the words they light, and the review meter.
  entityFill: 'rgba(255,255,255,0.14)',
  entityBorder: 'rgba(255,255,255,0.32)',
  termFlash: 'rgba(255,226,150,0.42)',
  meterTrack: 'rgba(255,255,255,0.24)',
  meterFill: '#FFFFFF',
  sup: 'rgba(255,255,255,0.62)',
  // The depth label under the page: dark glass that reads on the white page and on the field alike.
  depthGlass: 'rgba(22,16,64,0.62)',
  depthBorder: 'rgba(255,255,255,0.18)',
  depthLens: 'rgba(255,255,255,0.94)',
  depthInk: 'rgba(255,255,255,0.72)',
  depthInkActive: '#17132A',
  // Reading progress on the thumbnail card.
  progressTrack: 'rgba(17,18,22,0.10)',
  progressFill: '#3D63F2',
  // The lifted share card and its actions.
  shareGlass: 'rgba(255,255,255,0.12)',
  shareBorder: 'rgba(255,255,255,0.3)',
  shareShadow: '#0B0630',
  actionFill: 'rgba(255,255,255,0.2)',
  // A summary point while pressed: a soft backing behind its lines.
  pointPress: 'rgba(255,255,255,0.1)',
  actionInk: '#FFFFFF',
} as const;

const serif = Platform.select({ ios: 'Georgia', default: 'serif' });

export const TYPE = {
  wordmark: { fontSize: 25, fontWeight: '900' as const, letterSpacing: 0.4, color: COLOR.ink },
  pill: { fontSize: 8, fontWeight: '800' as const, letterSpacing: 0.8, color: COLOR.pillInk },
  headline: { fontFamily: serif, fontSize: 32, lineHeight: 36, fontWeight: '700' as const, color: COLOR.panelInk, textAlign: 'center' as const },
  dek: { fontFamily: serif, fontSize: 15, lineHeight: 19, letterSpacing: -0.3, fontWeight: '700' as const, color: COLOR.panelInkSoft, textAlign: 'center' as const },
  byline: { fontSize: 8, fontWeight: '700' as const, letterSpacing: 0.8, color: COLOR.panelInkSoft, textAlign: 'center' as const },
  section: { fontSize: 13, fontWeight: '800' as const, letterSpacing: 1.1, color: COLOR.ink },
  dropCap: { fontFamily: serif, fontSize: 56, lineHeight: 56, fontWeight: '700' as const, color: COLOR.ink },
  body: { fontFamily: serif, fontSize: 17, lineHeight: 26, color: COLOR.ink },
  toolbarLabel: { fontSize: 13, fontWeight: '600' as const, color: COLOR.toolbarInk },
  cardTitle: { fontSize: 11, fontWeight: '600' as const, color: COLOR.summaryInk },
  // Summary scale: lead (19 semibold) > point title (17 semibold, Apple's Headline) > point body (15, Subheadline,
  // secondary white) > credit (11, Caption 2). Tracking follows SF's optical table at each size.
  lead: { fontSize: 19, lineHeight: 26, fontWeight: '600' as const, letterSpacing: -0.4, color: COLOR.summaryInk },
  itemTitle: { fontSize: 17, lineHeight: 22, fontWeight: '600' as const, letterSpacing: -0.4, color: COLOR.summaryInk },
  itemBody: { fontSize: 15, lineHeight: 20, fontWeight: '400' as const, letterSpacing: -0.2, color: COLOR.summaryInkSoft },
  // TL;DR: one bold sentence, a step above the lead.
  tldr: { fontSize: 24, lineHeight: 30, fontWeight: '700' as const, letterSpacing: -0.6, color: COLOR.summaryInk },
  // The source marker: the title's own size, so the font's superscript figure sits where a footnote mark should.
  sup: { fontWeight: '600' as const, color: COLOR.sup },
  entity: { fontSize: 13, lineHeight: 16, fontWeight: '600' as const, letterSpacing: -0.1, color: COLOR.summaryInk },
  // Footer credit at 12 pt (never smaller on this screen).
  creditTitle: { fontSize: 12, lineHeight: 15, fontWeight: '600' as const, letterSpacing: 0, color: COLOR.creditInk },
  creditSub: { fontSize: 12, lineHeight: 15, fontWeight: '400' as const, letterSpacing: 0, color: COLOR.creditInkSoft, fontVariant: ['tabular-nums'] as TextStyle['fontVariant'] },
  depth: { fontSize: 12, lineHeight: 15, fontWeight: '600' as const, letterSpacing: 0 },
  thinking: { fontSize: 15, lineHeight: 20, fontWeight: '500' as const, color: COLOR.thinkingBase },
  action: { fontSize: 15, lineHeight: 20, fontWeight: '600' as const, letterSpacing: -0.2, color: COLOR.actionInk },
} as const;

export const LAYOUT = {
  gutter: 16,
  mastheadHeight: 42,
  panelInset: 8,
  panelPadTop: 27,
  panelPadX: 20,
  pillHeight: 16,
  pillPadX: 8,
  // Measured on the rectified source: 262 pt tall across the 386 pt panel.
  photoAspect: 262 / 386,
  pillGap: 21,
  dekGap: 12,
  photoGap: 17,
  dotGap: 46,
  bylineGap: 12,
  panelPadBottom: 42,
  panelRadius: 12,
  // White space under the panel before the body (the source shows none of the body at rest).
  bodyGap: 96,
  // Toolbar centre sits 39 pt above the bottom edge, as in the source.
  toolbarCenterFromBottom: 45,
  toolbarPill: { width: 56, height: 30 },
  plusGlyph: 20,
  toolbarRound: 30,
  photoInset: 12,
  toolbarHeight: 52,
  toolbarFade: 28,
  // Collapsed card: share of the screen width, height in points, corner radius.
  // Measured on the rectified source: 0.55 W × 222 pt while thinking, 0.64 W × 270 pt with the summary.
  thinkWidthRatio: 0.55,
  thinkHeight: 222,
  cardWidthRatio: 0.64,
  cardHeight: 270,
  cardRadius: 18,
  // The page's own top margin trimmed off in the card, so the masthead sits near the card's top edge.
  cardTopTrim: 6,
  thinkTop: 134,
  summaryTop: 125,
  // The page-title row sits 8 pt above the stacked card's top edge (card top − stackPeek − 8 − its 16 pt height).
  titleRowGap: 34,
  titleRowHeight: 16,
  shadowBlur: 18,
  glowBlur: 30,
  glowSpread: 22,
  glowRest: 0.35,
  glowThink: 0.35,
  shadowAlpha: 0.35,
  shadowDrop: 14,
  // Stacked card behind the page card: scale about the card's centre line, how far its top edge shows above the
  // card, the progress p at which it starts sliding out, and the p span of its fade.
  stackScale: 0.92,
  stackPeek: 10,
  stackFrom: 0.85,
  stackFade: 0.08,
  // The footer rides 10 pt into the home-indicator inset (never closer than 16 pt to the screen edge).
  footerLift: 10,
  footerMin: 16,
  // The credit pill: 44 pt like the close button beside it, a 20 pt disc on a 12 pt inset (concentric: 22 − 10 = 12).
  creditPill: { height: 44, padLeft: 12, padRight: 16, gap: 10, icon: 20, glyph: 11 },
  sweepWidth: 90,
  summaryGap: 24,
  textInset: 16,
  itemIndent: 34,
  iconDisc: 24,
  iconGlyph: 13,
  itemGap: 20,
  leadGap: 28,
  // Air kept between the last line and the footer; a summary longer than its room scrolls, fading whole lines.
  rowsBreath: 16,
  footerHeight: 44,
  closeSize: 44,
  closeGlyph: 18,
  deviceRadius: 48,
  rimWidth: 2.5,
  rimGlow: 10,
  fingerRadius: 22,
  // The page's own status-bar band: opaque over the clock and the Dynamic Island, with a soft edge below.
  safeFade: 18,
  // Marker band around a line: 2 pt past the words, the lower two thirds of the 26 pt line (a highlighter, not a box).
  markerPadX: 2,
  markerTop: 7,
  markerBottom: 2,
  markerRadius: 4,
  // The flying sentence's indigo pill: one rounded band per line, 6 pt past the words, 1 pt inside the 26 pt line.
  chipPadX: 6,
  chipInsetY: 1,
  chipRadius: 9,
  // The autoplay's read scroll stops with the first key sentence this far under the status bar.
  readTopGap: 72,
  // Entity chips under the lead.
  entityGap: 12,
  entityHeight: 28,
  entityPadX: 12,
  entityGapX: 8,
  // Review meter: five slim segments after the title.
  meterSegW: 12,
  meterSegH: 4,
  meterGap: 3,
  meterLeft: 10,
  // Depth label and "Summarizing for you…": centred this far above the bottom inset.
  depthHeight: 32,
  depthPad: 3,
  depthSegPadX: 12,
  depthLift: 92,
  // Reading progress bar inside the thumbnail card (screen points).
  progressHeight: 3,
  progressInset: 14,
  progressBottom: 10,
  // Share card.
  shareRadius: 22,
  sharePad: 12,
  actionHeight: 44,
  actionPadX: 18,
  actionGap: 10,
  // A pressed point's backing: past its lines by 8 pt each side, 4 pt above and below.
  pointPressPadX: 8,
  pointPressPadY: 4,
  pointPressRadius: 12,
} as const;

export type Paragraph = { leadIn?: string; key?: string; rest: string };
export type PointCopy = { icon: 'star' | 'body' | 'pricetag'; title: string; body: string; source: number; meter?: number };
export type LevelCopy = { name: string; lead: string; tldr: boolean; points: number[]; chips: number[] };

export const COPY = {
  wordmark: 'MERIDIAN',
  kicker: 'REVIEW',
  headline: 'My Week Living Inside the Halo One Visor',
  dek: 'Mara Okafor gets hands-on — and then some — in this in-depth Halo One review.',
  byline: 'BY MARA OKAFOR',
  date: 'FEB 2, 2026',
  section: 'GEAR REVIEWS',
  // Each of the first four paragraphs opens with a key sentence (its topic sentence): the summary is made from them.
  body: [
    { leadIn: 'THE HALO ONE', key: ' is a computer you wear: a private cinema and an office in one visor, and the closest anyone has come to that old promise.', rest: ' I spent a week working, watching and travelling with it, and mostly forgot it was there.' },
    { key: 'It has the best screen I have worn: text is as sharp as on a good laptop, and a film in a dark room beats my television.', rest: ' I never reached for a controller, because my hands were enough.' },
    { key: 'The split strap spreads the weight, but by the second hour I wanted a break.', rest: ' The battery pack in a pocket is a cable you never quite forget.' },
    { key: 'It starts at $1,899, and the travel case you will want is extra.', rest: ' That is laptop money for something most people will not wear all day yet.' },
    { rest: 'Apps are the open question. The ones made for the visor are lovely; the rest float in flat windows, and you notice the seams within a day.' },
    { rest: 'Calls are the surprise. People see a soft rendering of my face, which is uncanny for a minute and then simply useful, like a good webcam.' },
    { rest: 'Travel is where it earns its keep. On a five-hour flight the cabin disappeared, and the battery lasted one film and most of a second.' },
    { rest: 'Setup takes ten minutes, most of it fitting the light seal. Get that wrong and the edges glow; get it right and the room falls away.' },
    { rest: 'Battery life is the honest weak spot: a little over two hours of mixed use, and the pack grows warm in a pocket by the end of a film.' },
    { rest: 'Sound is better than it has any right to be. The speakers sit just off the ears, and in a quiet room nobody beside you hears much at all.' },
    { rest: 'Typing works with any keyboard, and the visor floats a larger screen above it; for writing I preferred that to anything else on the trip.' },
    { rest: 'Friends asked the same thing every time: does it feel lonely? Less than I feared, though handing it over to a guest takes a fiddly minute.' },
    { rest: 'Would I buy one? Not this year. But I have stopped thinking of headsets as gadgets, and that is the real change.' },
  ] as Paragraph[],
  summarize: 'Summarize',
  pageTitle: 'Halo One review: my week inside the visor',
  thinking: 'Summarizing for you…',
  credit: 'Summarized for you',
  // Pieces of one line around the rolling numbers; the edge spaces are non-breaking so layout keeps them.
  creditRead: ['You read\u00A0', '%\u00A0·\u00A014\u00A0min\u00A0→\u00A0', '\u00A0s'],
  // What the summary saves: the article reads in 14 min, the summary in 40 s.
  readSeconds: 40,
  // The three points, each from one key sentence (`source` is that sentence's index).
  points: [
    { icon: 'star', title: 'Review sentiment', body: 'The best screen Mara has worn: laptop-sharp text and films that beat her TV.', source: 1, meter: 4 },
    { icon: 'body', title: 'Comfort and fit', body: 'The split strap spreads the weight, but she wanted a break after 2 hours.', source: 2 },
    { icon: 'pricetag', title: 'Price', body: 'Starts at $1,899; the travel case is extra.', source: 3 },
  ] as PointCopy[],
  // Summary depth follows the pinch. `chips` maps each key sentence to the group it becomes (0 = the lead, n = the
  // n-th shown point, −1 = left out: it dissolves where it is).
  levels: [
    { name: 'Key points', tldr: false, points: [0, 1, 2], chips: [0, 1, 2, 3],
      lead: 'The Halo One is a computer you wear: a private cinema and an office in one visor, the closest yet to that old promise.' },
    { name: 'Brief', tldr: false, points: [1], chips: [0, -1, 1, 0],
      lead: 'The Halo One is a $1,899 computer you wear: a private cinema and an office in one visor, the closest yet to its promise.' },
    { name: 'TL;DR', tldr: true, points: [], chips: [0, 0, 0, 0],
      lead: 'The Halo One: the best wearable screen yet, at $1,899, though you will want a break after 2 hours.' },
  ] as LevelCopy[],
  // Entity chips under the lead, and the words each lights in the summary.
  entities: [
    { label: 'Halo One', term: 'Halo One' },
    { label: '$1,899', term: '$1,899' },
    { label: '2-hour comfort', term: '2 hours' },
  ],
  price: { term: '$1,899', value: 1899 },
  sup: ['¹', '²', '³'],
  share: ['Copy summary', 'Share'],
} as const;
