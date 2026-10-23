import { StatusBar } from 'expo-status-bar';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { HoloAgendaCarousel } from './HoloAgendaCarousel';
export default function HoloAgendaCarouselDemo() {
  return <GestureHandlerRootView style={{ flex: 1, backgroundColor: '#000000' }}>
    <StatusBar hidden />
    <HoloAgendaCarousel autoPlay />
  </GestureHandlerRootView>;
}
