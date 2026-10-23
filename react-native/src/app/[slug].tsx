import { useLocalSearchParams } from 'expo-router';

import { DemoScreen } from '@/gallery/demo-screen';
import { blocks } from '@/gallery/registry';

export function generateStaticParams() {
  return blocks.map((block) => ({ slug: block.slug }));
}

export default function DemoRoute() {
  const { slug } = useLocalSearchParams<{ slug: string }>();
  return <DemoScreen slug={slug} />;
}
