import { MeshGradientWeather } from './MeshGradientWeather';

// The block has the whole screen: a weather app whose background is a living mesh gradient. A 16 s
// film scrubs the hourly row from dawn through a rainy afternoon to night and back to Now; scroll or
// tap the row to scrub it yourself (the film returns 2 s after the last touch).
// Liquid Glass cards need iOS 26 (expo-glass-effect); older systems get a blur.
export default function MeshGradientWeatherDemo() {
  return <MeshGradientWeather autoPlay />;
}
