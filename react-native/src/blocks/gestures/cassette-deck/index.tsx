import { StatusBar } from 'expo-status-bar';

import { CassetteDeck } from './CassetteDeck';

export default function CassetteDeckDemo() {
  return (
    <>
      <StatusBar style="light" />
      <CassetteDeck autoPlay />
    </>
  );
}
