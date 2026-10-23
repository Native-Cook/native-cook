/** Keep the generated dust field stable for a given capture. */
export function dispersionSeed(value: string): number {
  let hash = 2_166_136_261;

  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16_777_619);
  }

  return ((hash >>> 0) % 10_000) / 97 + 0.137;
}
