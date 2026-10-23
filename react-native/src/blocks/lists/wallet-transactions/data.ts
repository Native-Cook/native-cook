export type RowIcon = 'puff' | 'dots' | 'sphere' | 'word' | 'initials';

export type Row = {
  name: string;
  meta: string;
  date?: string; // recurring payments show the loop mark and the next date
  amount: string; // whole part with sign, e.g. '-$109'
  cents: string; // '.99'
  icon: RowIcon;
  word?: string;
  tint?: string;
};

// The pin's wordmark, row brands and its repeated filler name are replaced by invented ones of the
// same length; amounts, dates and the order are the pin's.
export const BRAND = 'VEN';

export const BALANCE_COPY = { whole: '25 431', cents: '.30' } as const;

export const ALL: Row[] = [
  { name: 'Aven', meta: 'Online', date: 'Nov 12, ‘26', amount: '-$59', cents: '.99', icon: 'puff' },
  { name: 'Aven', meta: 'Online', date: 'Nov 14, ‘26', amount: '-$109', cents: '.99', icon: 'dots' },
  { name: 'Studio Sphere', meta: 'Salary', amount: '$4,950', cents: '.00', icon: 'sphere' },
  { name: 'Rivo Drive', meta: 'Online', amount: '-$24', cents: '.23', icon: 'word', word: 'Rivo' },
];

export const FRIENDS: Row[] = [
  { name: 'Nadia Rahman', meta: 'Friends', amount: '+$45', cents: '.00', icon: 'initials', word: 'NR', tint: '#E8F1E4' },
  { name: 'Jonas Weber', meta: 'Friends', amount: '-$18', cents: '.50', icon: 'initials', word: 'JW', tint: '#E9ECFB' },
  { name: 'Aiko Tanaka', meta: 'Friends', amount: '-$64', cents: '.00', icon: 'initials', word: 'AT', tint: '#FCEDE4' },
];

export const FILTERS = ['All', 'Recurring', 'Friends'] as const;

export const TILE_COPY = {
  invest: { label: 'Invest', whole: '70 850', cents: '' },
  shared: { label: 'Shared', whole: '1 200', cents: '.50' },
  loans: { label: 'Loans' },
} as const;

export const ACTION_COPY = ['Send', 'Receive', 'Add'] as const;
