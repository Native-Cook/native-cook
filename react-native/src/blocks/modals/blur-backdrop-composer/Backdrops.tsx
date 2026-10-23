import { Image } from 'expo-image';
import { StyleSheet, View } from 'react-native';

export type Scene = 0 | 1 | 2;

type Props = {
  scene: Scene;
  width: number;
  height: number;
};

const photo = (id: string, w: number, h: number) =>
  `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&h=${h}&q=80`;

/** The photos behind the composer, one per opening: a neon street at night, a lagoon, peaks above the clouds. */
export const BACKDROPS: Record<Scene, string> = {
  0: photo('photo-1540959733332-eab4deabeeaf', 600, 1300),
  1: photo('photo-1573843981267-be1999ff37cd', 600, 1300),
  2: photo('photo-1506905925346-21bda4d32df4', 600, 1300),
};

/** Photos of the page behind: the welcome card, the collage and the avatar. */
export const PAGE_PHOTOS = {
  hero: photo('photo-1502602898657-3e91760cbb34', 900, 440),
  collage: [
    photo('photo-1570077188670-e3a8d69ac5ff', 360, 280),
    photo('photo-1641128324972-af3212f0f6bd', 360, 280),
    photo('photo-1524492412937-b28074a5d7da', 360, 280),
  ],
  avatar: photo('photo-1544005313-94ddf0286df2', 120, 120),
};

const BLUR = 28;
const SCENES: Scene[] = [0, 1, 2];

/**
 * The photo behind the composer, blurred as the material would blur it. All three stay mounted
 * and only the current one shows, so a new opening never waits for its photo to decode.
 */
export function Backdrop({ scene, width, height }: Props) {
  return (
    <View style={{ width, height }}>
      {SCENES.map((s) => (
        <Image
          key={s}
          source={BACKDROPS[s]}
          blurRadius={BLUR}
          cachePolicy="memory-disk"
          transition={0}
          contentFit="cover"
          style={[StyleSheet.absoluteFill, { opacity: s === scene ? 1 : 0 }]}
        />
      ))}
    </View>
  );
}
