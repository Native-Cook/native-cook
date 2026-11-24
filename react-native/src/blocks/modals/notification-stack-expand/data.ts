// Relay, a team inbox for the (invented) Fernway team. Names, channels and numbers are made up.

export type Notice = {
  id: string;
  /** Who it is from; their initials draw the avatar. */
  from: string;
  body: string;
  time: string;
  /** Three stops for the avatar disc, top-left to bottom-right. */
  tint: [string, string, string];
  /** A small glyph on the avatar's corner: a channel (#) or an app squircle. */
  badge?: 'channel' | 'file';
  /** From an app rather than a person: a squircle with the app's glyph instead of initials. */
  app?: 'calendar' | 'files';
};

export type NoticeGroup = {
  title: string;
  channel: string;
  notices: Notice[];
};

/** The hero group: grouped messages from one channel. The stack shows the first four. */
export const LAUNCH: NoticeGroup = {
  title: 'Launch review',
  channel: '#launch-review',
  notices: [
    {
      id: 'l1',
      from: 'Priya Nair',
      body: 'Signed off on the onboarding copy. Ship it with the new empty states?',
      time: 'now',
      tint: ['#F2B48A', '#E07A5F', '#B5525C'],
      badge: 'channel',
    },
    {
      id: 'l2',
      from: 'Tomás Reyes',
      body: 'Build 4.12 is on TestFlight. Crash rate is down to 0.3% since Monday.',
      time: '4m',
      tint: ['#9CC9B8', '#4E9A86', '#2F6F66'],
      badge: 'channel',
    },
    {
      id: 'l3',
      from: 'Ines Kowal',
      body: 'Moved the review to 3:30 so design can join from the Lisbon office.',
      time: '12m',
      tint: ['#B9B4E8', '#7F79C9', '#5A5497'],
      badge: 'channel',
    },
    {
      id: 'l4',
      from: 'Dev Shah',
      body: 'The pricing page still shows last year’s annual price. Fixing it now.',
      time: '18m',
      tint: ['#F1D58E', '#D9A74A', '#A8772F'],
      badge: 'channel',
    },
  ],
};

/** A single notice under the group. */
export const STANDUP: Notice = {
  id: 's1',
  from: 'Calendar',
  body: 'Standup in 10 min · Larch room, second floor',
  time: '9:50',
  tint: ['#F5A3A0', '#E5605B', '#C23F44'],
  app: 'calendar',
};

/** A second, smaller group that stays collapsed. */
export const CRIT: NoticeGroup = {
  title: 'Design crit',
  channel: '#design-crit',
  notices: [
    {
      id: 'c1',
      from: 'Noor Amari',
      body: 'Uploaded 6 frames of Checkout v3. Comments welcome before Thursday.',
      time: '9:12',
      tint: ['#A7C7EE', '#5C8FD6', '#3A64A8'],
      badge: 'file',
    },
    {
      id: 'c2',
      from: 'Leo Brandt',
      body: 'Left notes on the receipt screen.',
      time: '8:47',
      tint: ['#C9D8A2', '#93AE5B', '#64803A'],
      badge: 'channel',
    },
  ],
};

export const YESTERDAY: Notice[] = [
  {
    id: 'y1',
    from: 'Hana Ito',
    body: 'Thanks for covering the release notes. The changelog reads great.',
    time: 'Mon',
    tint: ['#F0B6CF', '#D86F9A', '#A84A74'],
    badge: 'channel',
  },
  {
    id: 'y2',
    from: 'Files',
    body: 'Q4 roadmap.key was shared with you by Ines Kowal.',
    time: 'Mon',
    tint: ['#B7C2CC', '#7D8B98', '#56636F'],
    app: 'files',
  },
];

export const FILTERS = ['All', 'Mentions', 'Threads'] as const;
