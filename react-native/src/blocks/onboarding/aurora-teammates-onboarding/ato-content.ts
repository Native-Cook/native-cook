// Aurora Teammates Onboarding — the copy and the small artwork.
//
// The pin's copy is kept (two typos fixed: "speacialized", "task"). Its integration tiles show six
// real companies' names and marks; ours are invented names with marks drawn here in the same colour
// roles (a blue calendar, a blue camera, a warm multicolour chat mark, a mono notes mark, a white
// mail disc, an orange CRM disc), so page 3 reads the same without anyone's logo. The marks are
// images (assets/ato-logo-*.png), made by an image model to a brief and checked by hand.

/** Title tokens: a word that stays on screen between two pages keeps its token and glides. */
export const TOKENS = ['RUN', 'MEET', 'CONNECT', 'YOUR', 'BUSINESS', 'WITH', 'AI TEAMMATES', 'WORKSPACE'] as const;
export type Token = (typeof TOKENS)[number];
/** Which tokens are drawn with the rainbow gradient. */
export const GRADIENT_TOKENS: readonly Token[] = ['AI TEAMMATES', 'WORKSPACE'];

/** Per page: the title's lines as tokens, and the subtitle's lines. */
export const PAGE_COPY: {
  lines: Token[][];
  subtitle: string[];
  a11y: string;
}[] = [
  {
    lines: [['RUN', 'YOUR'], ['BUSINESS', 'WITH'], ['AI TEAMMATES']],
    subtitle: ['Projects, clients, meetings, tasks, & growth managed', 'from one AI-powered workspace.'],
    a11y: 'Run your business with AI teammates. Projects, clients, meetings, tasks and growth managed from one AI-powered workspace.',
  },
  {
    lines: [['MEET', 'YOUR'], ['AI TEAMMATES']],
    subtitle: ['A specialized AI for every part of your business.'],
    a11y: 'Meet your AI teammates. A specialized AI for every part of your business.',
  },
  {
    lines: [['CONNECT', 'YOUR'], ['WORKSPACE']],
    subtitle: ['Sync the tools your team already uses.'],
    a11y: 'Connect your workspace. Sync the tools your team already uses.',
  },
];

// ------------------------------------------------------------------ page 2: agents (24 px line icons)
const SPARK_TR = 'M18.5 2c.35 1.9 1.6 3.15 3.5 3.5-1.9.35-3.15 1.6-3.5 3.5-.35-1.9-1.6-3.15-3.5-3.5 1.9-.35 3.15-1.6 3.5-3.5z';
const SPARK_BR = 'M18.5 15c.35 1.9 1.6 3.15 3.5 3.5-1.9.35-3.15 1.6-3.5 3.5-.35-1.9-1.6-3.15-3.5-3.5 1.9-.35 3.15-1.6 3.5-3.5z';

export const AGENTS = [
  {
    title: 'AI Chat Assistant',
    sub: 'Chats, writes, plans',
    icon: 'M13 4.6A7.6 7.6 0 0 0 5.4 16.1L4.4 19.6l3.6-1A7.6 7.6 0 0 0 19.6 11.4 M9 12h.01 M12.5 12h.01',
    spark: SPARK_TR,
  },
  {
    title: 'AI Project Manager',
    sub: 'Plans sprints & tracks delivery',
    icon: 'M14 4.5H6.2A1.7 1.7 0 0 0 4.5 6.2v11.6a1.7 1.7 0 0 0 1.7 1.7h11.6a1.7 1.7 0 0 0 1.7-1.7V10.5 M12 8.4a2.4 2.4 0 1 1 0 4.8a2.4 2.4 0 1 1 0-4.8z M7.9 16.6c.9-1.7 2.3-2.5 4.1-2.5s3.2.8 4.1 2.5',
    spark: SPARK_TR,
  },
  {
    title: 'AI Meeting Assistant',
    sub: 'Qualifies leads & writes outreach',
    icon: 'M12.5 7H6.2A1.7 1.7 0 0 0 4.5 8.7v7.6A1.7 1.7 0 0 0 6.2 18h7.6a1.7 1.7 0 0 0 1.7-1.7V11 M15.5 12.2l4-2.4v6.4l-4-2.4',
    spark: SPARK_TR,
  },
  {
    title: 'AI Content Assistant',
    sub: 'Drafts proposals & reports',
    icon: 'M13.5 4.5H7.2A1.7 1.7 0 0 0 5.5 6.2v11.6a1.7 1.7 0 0 0 1.7 1.7h5.3 M13.5 4.5l5 5v3 M13.5 4.5v3.8a1.2 1.2 0 0 0 1.2 1.2h3.8',
    spark: SPARK_BR,
  },
  {
    title: 'AI Finance Manager',
    sub: 'Invoices, expenses & forecasts',
    icon: 'M14.5 6H6.2A1.7 1.7 0 0 0 4.5 7.7v8.6A1.7 1.7 0 0 0 6.2 18h6.3 M19.5 10.5v2 M4.9 7.3l7.1 5.2 3.6-2.6',
    spark: SPARK_BR,
  },
] as const;

// ------------------------------------------------------------------ page 3: integrations (invented)
export const INTEGRATIONS: {
  name: string;
  status: string;
  connected: boolean;
}[] = [
  {
    name: 'Daybook',
    status: 'Connected',
    connected: true,
  },
  {
    name: 'Huddle',
    status: 'Connect',
    connected: false,
  },
  {
    name: 'Parley',
    status: 'Connect',
    connected: false,
  },
  {
    name: 'Inkwell',
    status: 'Connect',
    connected: false,
  },
  {
    name: 'Postbox',
    status: 'Connected',
    connected: true,
  },
  {
    name: 'Leadflow',
    status: 'Connect',
    connected: false,
  },
];
