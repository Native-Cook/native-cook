export type CardFinish =
  | 'rare-holo'
  | 'double-rare'
  | 'special-illustration'
  | 'hyper-rare';

export type CardStage = 'BASIC' | 'STAGE 1' | 'STAGE 2';

export type CardAttack = {
  name: string;
  damage: number;
  cost: number;
  description: string;
};

export type CardAbility = {
  name: string;
  description: string;
};

export type DemoCard = {
  id: string;
  number: string;
  name: string;
  hp: number;
  element: string;
  rarity: string;
  finish: CardFinish;
  description: string;
  accent: string;
  accentSoft: string;
  stage: CardStage;
  evolvesFrom?: string;
  category: string;
  height: string;
  weight: string;
  ability?: CardAbility;
  attacks: CardAttack[];
  weakness: string;
  resistance: string;
  retreat: number;
  flavorText: string;
  illustrator: string;
  setCode: string;
  elementSymbol: string;
};
