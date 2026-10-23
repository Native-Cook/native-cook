export type SetupSample = { name: string; mainThreadMs: number; elapsedMs: number };
export type SetupSlice = <T>(name: string, work: () => T | Promise<T>) => Promise<T>;

/** Await asynchronous native work separately from its synchronous JS entry cost. */
export function setupSlices(
  permitted: () => boolean,
  alive: () => boolean,
  record: (sample: SetupSample) => void = () => {},
  yieldFrame: () => Promise<void> = () => new Promise(resolve => requestAnimationFrame(() => resolve())),
): SetupSlice {
  return async (name, work) => {
    do {
      await yieldFrame();
      if (!alive()) throw new Error('Medal setup cancelled');
    } while (!permitted());
    const start = performance.now();
    const pending = work();
    const mainThreadMs = performance.now() - start;
    const result = await pending;
    record({ name, mainThreadMs, elapsedMs: performance.now() - start });
    // The caller must receive late native handles so its finally block can release them.
    return result;
  };
}
