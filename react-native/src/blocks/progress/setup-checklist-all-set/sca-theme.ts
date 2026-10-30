// Setup Checklist All Set — layout, colour and copy, measured on the pin's flow (iPhone screens,
// 1179 px across: one point of the 402-point screen is 1179 / 402 px).

export const REF_W = 402;
export const REF_H = 874;

export const C = {
  page: '#F3F3F1',
  ink: '#121212',
  ink2: '#2B2B2B',
  grey: '#6E6E6A',
  mute: '#A8A8A4',
  dim: '#000000',
  sheet: '#FFFFFF',
  purple: '#7C41FF',
  badge: '#8022FE',
  barFrom: '#2865DA',
  barTo: '#7943FD',
  track: '#E0E1D9',
  green: '#0C7324',
  ring: '#DFE0DB',
  divider: '#EFEFEC',
  close: '#EFEFED',
  chip: '#E9E9E6',
  lilac: '#EDC2ED',
  popper: '#1F1A6B',
  cta: '#8129D9',
  pro: '#5B2BD1',
  confetti: ['#8022FE', '#EDC2ED', '#FFC83D', '#2865DA', '#0C7324', '#FF6FA8'],
};

export const COPY = {
  handle: 'alexsmith',
  url: 'tapline.me/alexsmith',
  pro: 'Try Pro for free',
  chips: ['Products', 'Design', 'Settings'],
  title: 'Your setup checklist',
  allSet: "You're all set!",
  allSetBody: ['Now that you’ve shared your Tapline,', 'keep that momentum going by sharing it in', 'even more places.'],
  cta: 'How to keep growing your traffic',
  later: 'Maybe later',
  skip: 'Skip',
  ring: ['17%', 'set up'],
};

export const ITEMS = [
  { title: 'Add your name and bio', body: ['This will be the first thing visitors see when they', 'land on your Tapline.'], action: 'Add name and bio' },
  { title: 'Add your profile image', body: ['A friendly face tells visitors they have found', 'the right page.'], action: 'Add profile image' },
  { title: 'Add your socials', body: ['Link the places people already follow you,', 'all in one row.'], action: 'Add socials' },
  { title: 'Add your other important links', body: ['Your shop, your newsletter, your latest video:', 'one tap away.'], action: 'Add links' },
  { title: 'Customize your design', body: ['Pick a look and feel that perfectly reflects who you', 'are and how you want to appear to the world.'], action: 'Customize design' },
  { title: 'Share your Tapline', body: ['Put your Tapline in your bio so every visitor', 'finds the rest of you.'], action: 'Copy link' },
];

export const UI = {
  sheet: { y: 299, r: 34 },
  title: { x: 17, y: 338 },
  badge: { x: 179, y: 328, w: 37, h: 20 },
  close: { x: 371, y: 338, r: 14 },
  bar: { x: 16.5, y: 369, w: 369.5, h: 11.4 },
  rows: { y0: 396.5, h: 54.3, open: 115.5, check: { x: 28.3, r: 11 }, textX: 49.4, chevronX: 377 },
  desc: { dy: [39.5, 57.8] }, // from the row's centre
  action: { dy: 100.5, h: 34, padX: 13 },
  skipDx: 22, // the pin's gap from the button to Skip
  // the page behind
  page: { avatar: { x: 37.5, y: 97, r: 20 }, pro: { x: 76, y: 77, w: 134, h: 40 }, share: { x: 316, y: 97 }, bell: { x: 373, y: 97 }, handle: { x: 17, y: 150 }, url: { x: 17, y: 192 }, chips: { y: 249, x: [17, 145, 259], w: [121, 107, 117], h: 40 }, cards: { y: 296, h: 207 }, ring: { x: 41, y: 572, r: 31 } },
  // the all-set sheet
  allSet: { y: 68, icon: { x: 201, y: 390, r: 51 }, title: 486.5, body: [528, 552, 576], cta: { x: 16, y: 689, w: 370, h: 51 }, later: 775.5 },
};
