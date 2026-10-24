import { Image, View, type ImageProps, type ImageSourcePropType, type StyleProp, type ViewStyle } from 'react-native';

type Artwork = { size: number[]; rect: number[] };
const ARTWORK: Record<string, Artwork> = require('./assets/artwork.json');

/** Keep the screen's existing frame while cropping only transparent source margins at runtime. */
export function BuzzStreaksArtwork({ name, source, width, height, style, onLoad }: {
  name: string;
  source: ImageSourcePropType;
  width: number;
  height: number;
  style?: StyleProp<ViewStyle>;
  onLoad?: ImageProps['onLoad'];
}) {
  const art = ARTWORK[name];
  if (!art) throw new Error(`Missing BuzzStreaks artwork metadata: ${name}`);
  const sx = width / art.rect[2], sy = height / art.rect[3];
  return <View pointerEvents="none" style={[style, { width, height, overflow: 'hidden' }]}>
    <Image source={source} onLoad={onLoad} resizeMode="stretch" style={{ position: 'absolute', left: -art.rect[0] * sx,
      top: -art.rect[1] * sy, width: art.size[0] * sx, height: art.size[1] * sy }}/>
  </View>;
}
