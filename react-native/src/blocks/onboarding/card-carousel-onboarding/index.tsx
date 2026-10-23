import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import React from 'react';

import { OnboardingScreen } from './screens/onboarding-screen';

const CardCarouselOnboardingDemo = () => {
  const [fontsLoaded] = useFonts({
    'CaveatBrush-Regular': require("./assets/fonts/CaveatBrush-Regular.ttf"),
  });

  if (!fontsLoaded) {
    return null; // Or return a loading spinner
  }

  return (
    <>
      {/* The original app left the status bar to the system, which draws dark icons over this light screen. */}
      <StatusBar style="dark" />
      <OnboardingScreen />
    </>
  );
};

export default CardCarouselOnboardingDemo;
