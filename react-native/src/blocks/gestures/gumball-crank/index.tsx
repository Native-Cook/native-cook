import { StatusBar } from 'expo-status-bar';

import { GumballCrank } from './GumballCrank';

export default function GumballCrankDemo() {
  return (
    <>
      <StatusBar style="dark" />
      <GumballCrank autoPlay />
    </>
  );
}
