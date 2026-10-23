/** For You Insights — the authored, local copy (the pin's own words where the pin shows them). */

export type ArticleKey = 'morning' | 'food' | 'run';

export type Article = Readonly<{
  key: ArticleKey;
  title: string;
  summary: string;
  readTime: string;
  sections: readonly Readonly<{ heading: string; body: string }>[];
}>;

export const LABEL = 'BASED ON YOUR HEALTH';

export const TIP =
  'Try breaking your morning run into intervals, alternating two minutes of hard effort with one minute of walking. You will get a great cardio boost in just 25 minutes.';

export const ARTICLES: readonly Article[] = [
  {
    key: 'morning',
    title: 'Good Sleep Habits Start as Soon as You Wake Up',
    summary: 'Your daily routine can make or break your nightly rest.',
    readTime: '5m Read',
    sections: [
      { heading: 'Begin with light', body: 'Spend a few quiet minutes near a bright window or outdoors after you wake. Morning light gives the day a clear opening cue.' },
      { heading: 'Keep a daytime rhythm', body: 'Movement, meals and breaks at roughly familiar times make it easier to notice when your energy rises and falls.' },
      { heading: 'Choose one wind-down cue', body: 'Dim a lamp, put away one screen or make a warm drink. A small repeatable cue is easier to keep than a whole routine.' },
    ],
  },
  {
    key: 'food',
    title: '5 Tricks That Unlock All the Best Stuff in Your Food',
    summary: 'How you pair and prepare your ingredients matters.',
    readTime: '3m Read',
    sections: [
      { heading: 'Balance the plate', body: 'Pair something crisp with something soft, and something fresh with something warm. Contrast makes simple food easier to enjoy.' },
      { heading: 'Use colour as a prompt', body: 'Two or three natural colours are a practical reminder to vary ingredients without turning lunch into a calculation.' },
      { heading: 'Prepare one flexible base', body: 'Cook a grain, wash greens or make a dressing once. A useful base lowers the effort of the next meal.' },
    ],
  },
  {
    key: 'run',
    title: '10 Minute Intervals That Build Real Stamina',
    summary: 'Short efforts with easy walks make a run easier to repeat.',
    readTime: '4m Read',
    sections: [
      { heading: 'Warm up gradually', body: 'Start with easy walking, then a relaxed jog. The first interval should feel like an invitation, not a test.' },
      { heading: 'Keep the pattern simple', body: 'Two minutes of steady running, one minute of walking. Repeat only while your form stays comfortable.' },
      { heading: 'Finish with room left', body: 'Walk until your breathing settles. A session you can repeat is worth more than one that empties the tank.' },
    ],
  },
] as const;

export const HABITS = [
  { icon: 'sunny', color: '#E2A031', title: 'Begin with light', body: 'Step outside or sit by a bright window for a quiet start to the day.' },
  { icon: 'leaf', color: '#47A76D', title: 'Balance the plate', body: 'Pair a fresh ingredient with something warm, and crisp textures with soft ones.' },
  { icon: 'walk', color: '#378DDD', title: 'Warm up gradually', body: 'Begin with easy walking, then a relaxed jog before your first interval.' },
] as const;
