// The seven friends in the row. Invented names and mascots; `color` is the
// flat background of each portrait, which tints the lens and leads the light
// under the card. `aura` is the two colors that light is mixed with: one
// beside it on the wheel and a deeper one, chosen by eye for each friend.

export type Friend = {
  id: string;
  name: string;
  color: string;
  aura: [string, string];
  // The friend's own lamp, when the portrait's color would not do: a yellow
  // dimmed to the strength of a lamp turns olive, an amber stays warm.
  lamp?: string;
  image: number;
};

export const FRIENDS: Friend[] = [
  { id: "mika", name: "Mika", color: "#FF8A3D", aura: ["#FF2E7E", "#5B2EFF"], image: require("./assets/avatars/mika.png") },
  { id: "juno", name: "Juno", color: "#7BE0B0", aura: ["#19B8FF", "#2E5BFF"], image: require("./assets/avatars/juno.png") },
  { id: "otto", name: "Otto", color: "#8B9CFF", aura: ["#B44DFF", "#1E9BFF"], image: require("./assets/avatars/otto.png") },
  { id: "nori", name: "Nori", color: "#FF8FC7", aura: ["#FF6A3D", "#8A3DFF"], image: require("./assets/avatars/nori.png") },
  { id: "pim", name: "Pim", color: "#6EC6FF", aura: ["#5B5BFF", "#19D9C0"], image: require("./assets/avatars/pim.png") },
  { id: "zuzu", name: "Zuzu", color: "#FFD966", lamp: "#FFAE2B", aura: ["#FF7A1F", "#FF2E7E"], image: require("./assets/avatars/zuzu.png") },
  { id: "rafa", name: "Rafa", color: "#C7A6FF", aura: ["#FF5CC8", "#3D6BFF"], image: require("./assets/avatars/rafa.png") },
];
