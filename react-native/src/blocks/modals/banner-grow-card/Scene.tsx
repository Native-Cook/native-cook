import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, View } from 'react-native';

export const SCENE_WIDTH = 252;
export const SCENE_HEIGHT = 380;

/** Peaks above a sea of cloud at first light: the calm the practice promises. */
const PHOTO = 'https://images.unsplash.com/photo-1506905925346-21bda4d32df4?auto=format&fit=crop&w=600&h=820&q=80';

/** The photo of the card, warmed to the app's amber and darkened at the foot for its caption. */
export function Scene() {
  return (
    <View style={styles.scene}>
      <Image source={PHOTO} style={StyleSheet.absoluteFill} contentFit="cover" contentPosition="center" transition={0} cachePolicy="memory-disk" />
      <LinearGradient
        colors={['rgba(232,140,62,0.34)', 'rgba(178,96,40,0.18)', 'rgba(36,16,6,0.78)']}
        locations={[0, 0.45, 1]}
        style={StyleSheet.absoluteFill}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  scene: { width: SCENE_WIDTH, height: SCENE_HEIGHT, backgroundColor: '#b26b3a' },
});
