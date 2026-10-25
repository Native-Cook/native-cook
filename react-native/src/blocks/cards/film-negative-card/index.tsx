import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FilmNegativeCard } from './FilmNegativeCard';

export default function FilmNegativeCardDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#16110F' }}>
      <StatusBar hidden />
      <FilmNegativeCard autoPlay />
    </View>
  );
}
