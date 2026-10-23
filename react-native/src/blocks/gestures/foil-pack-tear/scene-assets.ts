// All native asset references live here so integration needs only one Metro asset reload.
export const SCENE_ASSETS = {
  packMaps: require('./assets/pack-maps.png'),
  packPrint: require('./assets/pack-print.png'),
  cardFace: require('./assets/card-face.png'),
  cardBack: require('./assets/card-back.png'),
  cardMaps: require('./assets/card-maps.png'),
  tabLabel: require('./assets/tab-label.png'),
  spark: require('./assets/spark.png'),
} as const;
