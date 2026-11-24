export type ToneVersion = { label: string; text: string };

export const DOCUMENT = 'Note to the team';

export const ORIGINAL =
  'Hi everyone, I wanted to let you know that the launch of the new onboarding has been moved to next Thursday because we still need a little more time to finish testing the payment screens. Please send me anything that is still open by Tuesday.';

export const VERSIONS: ToneVersion[] = [
  {
    label: 'Shorter',
    text: 'Hi all, the new onboarding now launches next Thursday. We need more time to test the payment screens. Please send open items by Tuesday.',
  },
  {
    label: 'Warmer',
    text: 'Hi everyone, thank you for your patience. We are moving the new onboarding to next Thursday so the payment screens get the testing they deserve. If anything is still open, send it my way by Tuesday.',
  },
  {
    label: 'Clearer',
    text: 'The new onboarding launch moves to next Thursday. Testing of the payment screens is not finished. Send me every open item by Tuesday.',
  },
];
