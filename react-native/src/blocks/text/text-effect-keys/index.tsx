import { TextEffectKeys } from './TextEffectKeys';

/** A chat composer whose eight keycaps perform their own text effect; press Explode and the sent word is thrown across the thread onto the keys. */
export default function TextEffectKeysDemo() {
  return <TextEffectKeys autoPlay />;
}
