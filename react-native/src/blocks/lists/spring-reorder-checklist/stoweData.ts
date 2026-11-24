import type { ChecklistItem } from './SpringReorderChecklist';

/** The packing list of Stowe, an invented travel app. */
export const STOWE_ITEMS: readonly ChecklistItem[] = [
  { id: 'passport', title: 'Passport', note: 'Front pocket', packed: true },
  { id: 'jacket', title: 'Rain jacket', note: 'Carry-on · 420 g' },
  { id: 'charger', title: 'Charger and cable', note: 'Carry-on · 90 g', packed: true },
  { id: 'shoes', title: 'Walking shoes', note: 'Suitcase · 1 pair' },
  { id: 'shirts', title: 'Linen shirts', note: 'Suitcase · 3' },
  { id: 'adapter', title: 'Plug adapter', note: 'Carry-on · Type F', packed: true },
  { id: 'toiletries', title: 'Toiletries bag', note: 'Suitcase · under 100 ml' },
  { id: 'sunscreen', title: 'Sunscreen', note: 'Suitcase · SPF 50' },
  { id: 'book', title: 'Paperback', note: 'Carry-on · for the flight' },
  { id: 'bottle', title: 'Water bottle', note: 'Carry-on · empty at security' },
  { id: 'socks', title: 'Socks', note: 'Suitcase · 5 pairs', packed: true },
  { id: 'hat', title: 'Sun hat', note: 'Suitcase · packable' },
  { id: 'meds', title: 'Travel meds', note: 'Carry-on · 1 pouch', packed: true },
  { id: 'tote', title: 'Folding tote', note: 'Suitcase · for the market' },
];
