import { MentionAutocomplete } from './MentionAutocomplete';

/**
 Mention Autocomplete: a team chat composer. Typing "@" opens a popover of people that grows out of
 the caret and re-ranks as you type, with the matched letters in bold; picking someone turns the
 typed letters into a token chip, and Backspace on a chip breaks it back into text.
 Component: MentionAutocomplete.tsx (screen), MentionPopover.tsx (the reusable popover).
 */
export default function MentionAutocompleteDemo() {
  return <MentionAutocomplete autoPlay />;
}
