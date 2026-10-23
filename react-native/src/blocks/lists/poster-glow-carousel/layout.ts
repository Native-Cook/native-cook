import type { Section } from './content';
import { HOME } from './theme';

export const LABEL_LINE = 21; // Poppins 14 line box; labels are placed by their centre

export function sectionHeight(section: Section): number {
  if (section.kind === 'posters') return HOME.poster.h;
  if (section.kind === 'wide') return HOME.wide.image + HOME.wide.info;
  return HOME.tall.h;
}

export type FeedLayout = { labels: number[]; tops: number[]; bottom: number };

/** Label centres and row tops in content coordinates, from the pin's measured gaps. */
export function feedLayout(sections: Section[]): FeedLayout {
  const labels: number[] = [];
  const tops: number[] = [];
  let label: number = HOME.firstLabelY;
  let bottom = 0;
  sections.forEach((section, i) => {
    if (i > 0) label = bottom + section.gap;
    labels.push(label);
    const top = label + (section.toContent ?? HOME.labelToContent);
    tops.push(top);
    bottom = top + sectionHeight(section);
  });
  return { labels, tops, bottom };
}
