// Measured from the pin (a phone filmed in a hand; screen scaled to 402 × 874 pt, colours
// lifted from the filmed screen, which the camera dulls).

export const BG = {
  // sampled on the pin's rectified frame: dark green, then deep blue from the lower quarter
  top: '#112f26',
  mid: '#123a30',
  bottom: '#0e5bc7',
  stops: [0, 0.7, 1] as const,
};

// The camera the pin's post is seen through: a slight downward look and a long lens.
export const BOARD = {
  pitch: 16, // degrees: top faces show
  focal: 930, // perspective distance (pt)
};

export const SHADE = {
  face: 1,
  top: 1.12,
  bottom: 0.62,
  end: 0.8, // tail end and arrow facets, before the light from the left
  side: 0.1, // extra light on facets turned to the left
};

export const TYPE = {
  name: 28,
  km: 9,
  kmInset: 28, // from the arrow's shoulder
};
