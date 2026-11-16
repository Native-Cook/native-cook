import { COPY, TEXT_ITEMS, type Metric } from './tgt-theme';
import { timeText, type RunSample } from './tgt-data';
export { TEXT_ITEMS } from './tgt-theme';
export type { Metric } from './tgt-theme';
export function metricText(id: Metric, sample: RunSample, units: 'km' | 'mi') {
  'worklet';
  switch (id) {
    case 'run':
      return COPY.run;
    case 'distance':
      return (sample.distance * (units === 'mi' ? 0.621371 : 1)).toFixed(2);
    case 'unit':
      return units === 'mi' ? COPY.mi : COPY.km;
    case 'elapsed':
      return timeText(sample.elapsed);
    case 'bpm':
      return `${Math.round(sample.bpm)} bpm`;
    case 'pace':
      return `${timeText(sample.pace * (units === 'mi' ? 1.609344 : 1))} /${units}`;
    case 'contactLabel':
      return COPY.contact;
    case 'contact':
      return `${Math.round(sample.contact)}`;
    case 'ms':
      return COPY.ms;
    case 'verticalLabel':
      return COPY.vertical;
    case 'oscillation':
      return sample.oscillation.toFixed(1);
    case 'cm':
      return COPY.cm;
  }
}

export function textItemsAt(fontScale: number) {
  return TEXT_ITEMS.map((item) => (item.id === 'unit' && fontScale > 1.2 ? { ...item, x: 79, y: 478 } : item));
}
