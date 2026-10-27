/** Local fixtures only. No wallet, orders, networking, billing or persisted entitlement. */

export const STEPS = ['Wallet', 'Choose bots', 'Setup', 'Allocation', 'Results', 'Pro'] as const;
export const PRO = 5;

export const RISKS = ['2.0%', '0.5%', '0.25%'] as const;
export type Risk = (typeof RISKS)[number];

export type Flow = {
  step: number;
  selectedBot: number;
  proFocus: number;
  allocation: number;
  risk: Risk;
  autoTrading: boolean;
  maxDailyLoss: boolean;
  /** Seconds (performance clock) of the last accepted CTA press. */
  lastAdvance: number;
};

export function initialFlow(start = 0): Flow {
  return {
    step: Math.min(PRO, Math.max(0, start)),
    selectedBot: 1,
    proFocus: 0,
    allocation: 53,
    risk: '2.0%',
    autoTrading: true,
    maxDailyLoss: true,
    lastAdvance: -Infinity,
  };
}

const clamp = (value: number, low: number, high: number) => Math.min(high, Math.max(low, value));

/**
 * Coalesce duplicate CTA input during the exit/entry handoff (480 ms). Back/Skip can
 * interrupt at any time and reset this gate. Returns null when the press is ignored.
 */
export function advance(flow: Flow, time: number): Flow | null {
  if (time - flow.lastAdvance < 0.48 || flow.step === PRO) return null;
  const step = flow.step + 1;
  return { ...flow, step, lastAdvance: time, proFocus: step === PRO ? 0 : flow.proFocus };
}

export function back(flow: Flow): Flow {
  return { ...flow, step: Math.max(0, flow.step - 1), lastAdvance: -Infinity };
}

export function show(flow: Flow, step: number): Flow {
  return { ...flow, step, proFocus: step === PRO ? 0 : flow.proFocus, lastAdvance: -Infinity };
}

export const chooseBot = (flow: Flow, index: number): Flow => ({ ...flow, selectedBot: clamp(index, 0, 2) });
export const focusPro = (flow: Flow, index: number): Flow => ({ ...flow, proFocus: clamp(index, 0, 2) });
export const allocate = (flow: Flow, amount: number): Flow => ({ ...flow, allocation: clamp(amount, 0, 100) });
