export const PRISM = {
  width: 402, height: 874,
  headline: { top: 196, height: 88, size: 36, line: 44, tracking: -0.6 },
  sphere: { x: 201, y: 472, radius: 124, touchRadius: 136 },
  caption: { top: 660, height: 24, size: 17, line: 24, tracking: -0.2 },
  page: '#000000', text: '#FFFFFF', captionAlpha: 0.92,
  headlineLines: ['We are creating', 'your digital art.'],
  captions: ['Please stand by…', 'Shaping the light…'],
  moteColours: ['#EDE4FF', '#C4B0FF', '#9F7BFF', '#F0ABFC'],
} as const;

export function prismLayout(width: number, height: number) {
  const k = Math.min(width / PRISM.width, height / PRISM.height);
  const left = (width - PRISM.width * k) / 2;
  const top = (height - PRISM.height * k) / 2;
  const halfPoint = (value: number) => Math.round(value * k * 2) / 2;
  return {
    k, left, top, frameWidth: PRISM.width * k, frameHeight: PRISM.height * k,
    cx: left + PRISM.sphere.x * k, cy: top + PRISM.sphere.y * k,
    radius: PRISM.sphere.radius * k, touchRadius: PRISM.sphere.touchRadius * k,
    headlineTop: top + PRISM.headline.top * k,
    headlineHeight: halfPoint(PRISM.headline.line) * 2,
    headlineSize: halfPoint(PRISM.headline.size), headlineLine: halfPoint(PRISM.headline.line),
    captionTop: top + PRISM.caption.top * k,
    captionSize: halfPoint(PRISM.caption.size), captionLine: halfPoint(PRISM.caption.line),
  };
}
