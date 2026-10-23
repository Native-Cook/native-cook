import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { BeamCheckoutStepper } from './BeamCheckoutStepper';

/** Opens the full-screen checkout demonstration on a black stage. */
export default function BeamCheckoutStepperDemo() {
  return <View style={{ flex: 1, backgroundColor: '#000000' }}><StatusBar hidden /><BeamCheckoutStepper autoPlay /></View>;
}
