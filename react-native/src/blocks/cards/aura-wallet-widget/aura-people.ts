import type { ImageSourcePropType } from "react-native";

// The wallet's owner and the five friends on the cushion's lip, left to right.
// The faces are ours, rendered for this block: each fills its square, on its
// own pastel ground, and is shown cut to a circle.

export type AuraPerson = { id: string; name: string; ground: string; image: ImageSourcePropType };

export const OWNER: AuraPerson = { id: "ana", name: "Ana", ground: "#BFE3F2", image: require("./assets/avatars/ana.png") };

export const FRIENDS: AuraPerson[] = [
  { id: "kofi", name: "Kofi", ground: "#A9E6BE", image: require("./assets/avatars/kofi.png") },
  { id: "maya", name: "Maya", ground: "#CDBDF6", image: require("./assets/avatars/maya.png") },
  { id: "theo", name: "Theo", ground: "#F5B8CB", image: require("./assets/avatars/theo.png") },
  { id: "raj", name: "Raj", ground: "#F1DBC6", image: require("./assets/avatars/raj.png") },
  { id: "skye", name: "Skye", ground: "#A9D8F7", image: require("./assets/avatars/skye.png") },
];

/** The last transaction. Its brand is invented, and so is its mark. */
export const LAST = { title: "Orbit Pro", date: "Jan 17", time: "20:12", amount: 60, balance: 52002.5 };
