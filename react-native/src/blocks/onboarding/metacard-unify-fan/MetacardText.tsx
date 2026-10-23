import { Blur, FillType, Group, Paint, Paragraph, Path, Skia, TextAlign, type SkPath, type SkTypefaceFontProvider } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { useDerivedValue } from 'react-native-reanimated';
import { COPY, D, markCommands } from './metacard-layout';
import { INK } from './metacard-theme';
import { textState } from './metacard-timeline';
import type { MetacardValues } from './use-metacard-motion';

export function makeParagraphs(provider: SkTypefaceFontProvider) {
  return COPY.map((copy, i) => {
    const p = Skia.ParagraphBuilder.Make({ maxLines: 1, textAlign: TextAlign.Center, textStyle: {
      fontFamilies: [i > 4 ? 'MetacardInterRegular' : 'MetacardInter'],
      fontSize: i > 4 ? D.hintSize : i === 4 ? D.wordSize : D.titleSize,
      letterSpacing: i > 4 ? D.hintTracking : i === 4 ? D.wordTracking : D.titleTracking, color: Skia.Color(INK),
    } }, provider).addText(copy).build();
    p.layout(D.width);
    return p;
  });
}
function Hint({ index, values, paragraphs, reduced, path }: {
  index: 5 | 6; values: MetacardValues; paragraphs: ReturnType<typeof makeParagraphs>; reduced: boolean; path: SkPath;
}) {
  const { u } = values, paragraph = paragraphs[index], width = paragraph.getLongestLine();
  const left = (D.width - width - D.hintMark - D.hintGap) / 2;
  const state = useDerivedValue(() => textState(u.get(), reduced));
  const alpha = useDerivedValue(() => D.hintOpacity * (index === 5 ? state.get().hintFan : state.get().hintHero));
  const offset = useDerivedValue(() => [{ translateY: index === 5 ? state.get().hintFanY : state.get().hintHeroY }]);
  const blur = useDerivedValue(() => index === 5 ? state.get().hintFanBlur : state.get().hintHeroBlur);
  return <Group layer={<Paint opacity={alpha}><Blur blur={blur} /></Paint>} transform={offset}>
    <Group transform={[{ translateX: left + D.hintMark / 2 }, { translateY: D.hintY }, { scale: D.hintMark / 24 }]}>
      <Path path={path} color={INK} />
    </Group>
    <Paragraph paragraph={paragraph} x={left + D.hintMark + D.hintGap - (D.width - width) / 2}
      y={D.hintY - paragraph.getHeight() / 2} width={D.width} />
  </Group>;
}
function Line({ index, values, paragraphs, reduced }: {
  index: number; values: MetacardValues; paragraphs: ReturnType<typeof makeParagraphs>; reduced: boolean;
}) {
  const { u } = values, paragraph = paragraphs[index];
  const state = useDerivedValue(() => textState(u.get(), reduced));
  const alpha = useDerivedValue(() => index < 2 ? state.get().fan : state.get().hero);
  const offset = useDerivedValue(() => [{ translateY: index < 2 ? state.get().fanY : state.get().heroY }]);
  const blur = useDerivedValue(() => index < 2 ? state.get().fanBlur : state.get().heroBlur);
  const centre = index < 2 ? D.title1[index] : D.title2[index - 2];
  // A paragraph ignores Group opacity and a child Blur on the phone: fade and blur it as a layer.
  return <Group layer={<Paint opacity={alpha}><Blur blur={blur} /></Paint>} transform={offset}>
    <Paragraph paragraph={paragraph} x={0} y={centre - paragraph.getHeight() / 2} width={D.width} />
  </Group>;
}
export function MetacardText({ values, paragraphs, reduced }: {
  values: MetacardValues; paragraphs: ReturnType<typeof makeParagraphs>; reduced: boolean;
}) {
  const { u } = values;
  const word = paragraphs[4], wordWidth = word.getLongestLine();
  const left = (D.width - wordWidth - D.wordMark - D.wordGap) / 2;
  const path = useMemo(() => {
    const builder = Skia.PathBuilder.Make().setFillType(FillType.EvenOdd);
    for (const c of markCommands()) {
      if (c[0] === 0) builder.moveTo(c[1], c[2]);
      else if (c[0] === 1) builder.lineTo(c[1], c[2]);
      else if (c[0] === 2) builder.quadTo(c[1], c[2], c[3], c[4]);
      else builder.close();
    }
    return builder.build();
  }, []);
  const state = useDerivedValue(() => textState(u.get(), reduced));
  const alpha = useDerivedValue(() => state.get().word);
  const offset = useDerivedValue(() => [{ translateY: state.get().wordY }]);
  const blur = useDerivedValue(() => state.get().wordBlur);
  return <>
    {[0, 1, 2, 3].map(index => <Line key={index} index={index} values={values} paragraphs={paragraphs} reduced={reduced} />)}
    <Group layer={<Paint opacity={alpha}><Blur blur={blur} /></Paint>} transform={offset}>
      <Group transform={[{ translateX: left + D.wordMark / 2 }, { translateY: D.wordY + 1 }, { scale: D.wordMark / 24 }]}>
        <Path path={path} color={INK} />
      </Group>
      <Paragraph paragraph={word} x={left + D.wordMark + D.wordGap - (D.width - wordWidth) / 2}
        y={D.wordY - word.getHeight() / 2} width={D.width} />
    </Group>
    <Hint index={5} values={values} paragraphs={paragraphs} reduced={reduced} path={path} />
    <Hint index={6} values={values} paragraphs={paragraphs} reduced={reduced} path={path} />
  </>;
}
