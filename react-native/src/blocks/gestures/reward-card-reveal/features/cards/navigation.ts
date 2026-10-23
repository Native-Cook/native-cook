import { createContext, useContext } from 'react';

// The original app had two expo-router routes, `index` (the gallery) and `card/[id]` (the detail).
// As a block it is one screen of the host app, so the open card is local state and reaches the
// two screens through this context instead of the router.
export type CardNavigation = {
  /** The `id` of the open card, as the `card/[id]` route received it. Undefined on the gallery. */
  cardId: string | undefined;
  /** What the gallery's link to `/card/[id]` did. */
  openCard: (id: string) => void;
  /** What `router.back()` did on the detail screen. */
  back: () => void;
};

export const CardNavigationContext = createContext<CardNavigation>({
  cardId: undefined,
  openCard: () => {},
  back: () => {},
});

export function useCardNavigation() {
  return useContext(CardNavigationContext);
}
