import { router } from 'expo-router';
import { GlowDialReminder } from './GlowDialReminder';

export default function GlowDialReminderDemo() {
  return <GlowDialReminder autoPlay onBack={() => router.back()} />;
}
