import { ScrubNumberField } from './ScrubNumberField';

// The block has the whole screen: the Adjust page of an invented camera app, Parlour. Three number
// fields are scrubbed like in a design tool (press and drag sideways) and edit the photo live through
// one Skia shader. In the 9 s film a ghost finger scrubs Exposure slowly, flicks Warmth, scrubs
// Vignette and double taps the photo to roll everything back. Touch a field to take over (the film
// returns 2 s after the last touch).
export default function ScrubNumberFieldDemo() {
  return <ScrubNumberField autoPlay />;
}
