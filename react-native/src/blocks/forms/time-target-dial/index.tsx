import { StyleSheet, View } from 'react-native';
import Animated, { FadeInUp, useSharedValue } from 'react-native-reanimated';
import { BottomTab } from './components/bottom-tab';
import { CircleTime } from './components/circle-time';
import { Footer } from "./components/footer";
import { Header } from "./components/header";
import { Queued } from "./components/queued";
import { TimeRange } from './components/time-range';

export default function TimeTargetDialDemo() {
  const selectedDuration = useSharedValue(5);
  return (
    <View style={[styles.fill]}>
      <Header />
      <Queued />
      <Animated.View entering={FadeInUp.duration(600)} style={styles.container}>
        <CircleTime selectedDuration={selectedDuration}/>
        <TimeRange selectedDuration={selectedDuration} />
      </Animated.View>
      <Footer />
      <BottomTab />
    </View>
  );
};


const styles = StyleSheet.create({
  fill: {
    flex: 1,
    backgroundColor: '#000',
  },
  button: {
    alignItems: 'center',
    aspectRatio: 1,
    backgroundColor: 'white',
    borderRadius: 32,
    height: 64,
    justifyContent: 'center',
  },
  buttonsContainer: {
    bottom: 48,
    flexDirection: 'row',
    gap: 8,
    position: 'absolute',
    right: 32,
  },
  container: {
    flexDirection: "row",
    alignItems: 'center',
    flex: 1,
  },

});
