// Standalone catalog screen; all visible content belongs to the canvas.
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { SuperchargeCard } from './SuperchargeCard';
export default function SuperchargeCardDemo() {
  return <View style={{ flex: 1, backgroundColor: '#050506' }}><StatusBar hidden /><SuperchargeCard autoPlay /></View>;
}
