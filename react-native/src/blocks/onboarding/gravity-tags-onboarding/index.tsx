import { StatusBar } from 'expo-status-bar';
import React, { useState } from 'react';
import { View } from 'react-native';

import { AnimatedSplashOverlay } from './components/animated-icon';
import TagsScreen from './components/TagsScreen';
import WelcomeScreen from './components/WelcomeScreen';

export default function GravityTagsOnboardingDemo() {
  const [onboardingStep, setOnboardingStep] = useState(0); // 0: Welcome, 1: Tags

  const handleWelcomeContinue = () => {
    setOnboardingStep(1);
  };

  return (
    <View style={{ flex: 1 }}>
      {/* The original app left the status bar to the system, which draws dark icons over these white screens. */}
      <StatusBar style="dark" />
      <AnimatedSplashOverlay />

      {onboardingStep === 0 && (
        <WelcomeScreen onContinue={handleWelcomeContinue} />
      )}

      {onboardingStep === 1 && (
        <TagsScreen onContinue={() => {

        }} />
      )}

    </View>
  );
}
