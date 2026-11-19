import { BlurStyle, Canvas, createPicture, Group, Path, Picture, Skia } from "@shopify/react-native-skia";
import type { SkPicture } from "@shopify/react-native-skia";
import { memo, useMemo } from "react";
import type { JSX } from "react";
import { StyleSheet } from "react-native";
import { useDerivedValue } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";

import { DOOR_X, HOME, HOME_INDEX, makeCityPicture, makeRoutePath, ROUTE_STEP, ROUTE_X, ROUTE_Y } from "./courierCity";
import { cityFrame } from "./courierDraw";
import { clamp01, routeAt, screenCam, smooth01, toScreen } from "./courierMotion";

export const ROUTE_BLUE = "#2F6BFF";
const TRAVELLED = "#A9B0BD";

export type CourierMapProps = {
  width: number;
  height: number;
  /** [width, height, focus x, focus y, framing width, framing height, panel top] (see courierMotion). */
  lay: number[];
  camX: SharedValue<number>;
  camY: SharedValue<number>;
  camZ: SharedValue<number>;
  camB: SharedValue<number>;
  /** Metres the van has driven, and how visible it is. */
  vanD: SharedValue<number>;
  vanA: SharedValue<number>;
  /** The greyed, travelled part of the route: [end fraction] and its visibility. */
  travEnd: SharedValue<number>;
  travA: SharedValue<number>;
  /** The door's arrival pulse (0 .. 1, 0 = none). */
  doorPulse: SharedValue<number>;
};

/** How much the van is drawn as a 3D model rather than the location dot, for a zoom. */
export function vanModelK(s: number): number {
  "worklet";
  return smooth01((s - 1.5) / 1.1);
}

/** A coordinate for an SVG path string: never in exponent notation (a sub-0.0001 pt value is 0). */
function svgNum(v: number): number {
  "worklet";
  return v > -1e-4 && v < 1e-4 ? 0 : v;
}

let cityPicture: SkPicture | null = null;
function getCityPicture(): SkPicture {
  if (!cityPicture) cityPicture = makeCityPicture();
  return cityPicture;
}

/** The map: the static city picture and the route under the camera's matrix, then the raised buildings and the van
 * (computed per frame in screen space), then the location dot. */
export const CourierMap = memo(function CourierMap(p: CourierMapProps): JSX.Element {
  const { width: w, height: h, lay } = p;
  const city = useMemo(getCityPicture, []);
  const route = useMemo(makeRoutePath, []);

  const matrix = useDerivedValue(() => {
    const cam = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    screenCam(p.camX.get(), p.camY.get(), p.camZ.get(), p.camB.get(), lay, cam);
    const m = Skia.Matrix();
    m.translate(cam[5], cam[6]);
    m.scale(1, cam[7]);
    m.rotate(-p.camB.get());
    m.scale(cam[2], cam[2]);
    m.translate(-cam[0], -cam[1]);
    return m;
  });
  const zoom = useDerivedValue(() => Math.pow(2, p.camZ.get()));
  const casingW = useDerivedValue(() => 10 / zoom.get());
  const lineW = useDerivedValue(() => 6.5 / zoom.get());
  const travOpacity = useDerivedValue(() => clamp01(p.travA.get()));
  // a ring on the pavement at the door, shown once close, and one wide pulse on arrival
  const doorRing = useMemo(() => {
    const path = Skia.PathBuilder.Make();
    path.addCircle(DOOR_X, HOME.y1 + 2.2, 3.2);
    return path.build();
  }, []);
  const ringOpacity = useDerivedValue(() => smooth01((zoom.get() - 1.6) / 1.2));
  const pulseOpacity = useDerivedValue(() => {
    const k = p.doorPulse.get();
    return k > 0 ? 1 - k : 0;
  });
  const pulsePath = useDerivedValue(() => {
    const path = Skia.PathBuilder.Make();
    const k = p.doorPulse.get();
    if (k > 0) path.addCircle(DOOR_X, HOME.y1 + 2.2, 3.2 + 10 * k);
    return path.build();
  });
  const ringStroke = useDerivedValue(() => 1.6 / zoom.get());

  const layer = useDerivedValue(() => {
    const cam = [0, 0, 0, 0, 0, 0, 0, 0, 0];
    screenCam(p.camX.get(), p.camY.get(), p.camZ.get(), p.camB.get(), lay, cam);
    const r = [0, 0, 0, 0];
    routeAt(p.vanD.get(), ROUTE_X, ROUTE_Y, ROUTE_STEP, r);
    const va = clamp01(p.vanA.get());
    const model = vanModelK(cam[2]);
    const vs = [r[0], r[1], r[2], r[3], va, model];
    const list: number[] = [];
    cityFrame(cam, lay, HOME_INDEX, vs, list);
    const pos = [0, 0];
    toScreen(r[0], r[1], cam, pos, 0);
    return createPicture((canvas) => {
      const paint = Skia.Paint();
      paint.setAntiAlias(true);
      // the van's soft shadow, under everything raised
      if (va * model > 0.01) {
        paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, Math.max(1.5, 0.9 * cam[2]), true));
        paint.setColor(Skia.Color(`rgba(40,44,52,${0.28 * va * model})`));
        const sh = Skia.PathBuilder.Make();
        const hx = r[2];
        const hy = r[3];
        const c = [0, 0, 0, 0, 0, 0, 0, 0];
        const al = [-3.3, 3.2, 3.2, -3.3];
        const ac = [-1.25, -1.25, 1.25, 1.25];
        for (let k = 0; k < 4; k++) toScreen(r[0] + hx * al[k] - hy * ac[k] + 0.5, r[1] + hy * al[k] + hx * ac[k] + 0.4, cam, c, k * 2);
        sh.moveTo(c[0], c[1]);
        sh.lineTo(c[2], c[3]);
        sh.lineTo(c[4], c[5]);
        sh.lineTo(c[6], c[7]);
        sh.close();
        canvas.drawPath(sh.build(), paint);
        paint.setMaskFilter(null);
      }
      // consecutive polygons of one colour (a wall's windows) go into one path and one draw. The path is handed to
      // Skia as one SVG string: a moveTo / lineTo call per point was up to 2,000 calls into native code a frame
      let k = 0;
      while (k < list.length) {
        const color = list[k];
        let d = "";
        while (k < list.length && list[k] === color) {
          const n = list[k + 1];
          d += "M" + svgNum(list[k + 2]) + " " + svgNum(list[k + 3]);
          for (let q = 1; q < n; q++) d += "L" + svgNum(list[k + 2 + q * 2]) + " " + svgNum(list[k + 3 + q * 2]);
          d += "Z";
          k += 2 + n * 2;
        }
        const path = Skia.Path.MakeFromSVGString(d);
        if (path == null) continue;
        paint.setColor(Skia.Color(color));
        canvas.drawPath(path, paint);
      }
      // the location dot while the van is far: a white disc with a blue core and a heading tip
      const dot = va * (1 - model);
      if (dot > 0.01) {
        const x = pos[0];
        const y = pos[1];
        paint.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 3, true));
        paint.setColor(Skia.Color(`rgba(20,30,60,${0.25 * dot})`));
        canvas.drawCircle(x, y + 1.5, 12, paint);
        paint.setMaskFilter(null);
        paint.setColor(Skia.Color(`rgba(255,255,255,${dot})`));
        canvas.drawCircle(x, y, 11.5, paint);
        paint.setColor(Skia.Color(`rgba(47,107,255,${dot})`));
        canvas.drawCircle(x, y, 8, paint);
        // heading: the route's direction on screen
        const tip = [0, 0];
        toScreen(r[0] + r[2] * 50, r[1] + r[3] * 50, cam, tip, 0);
        const dx = tip[0] - x;
        const dy = tip[1] - y;
        const dl = Math.hypot(dx, dy) || 1;
        const ux = dx / dl;
        const uy = dy / dl;
        const tri = Skia.PathBuilder.Make();
        tri.moveTo(x + ux * 6, y + uy * 6);
        tri.lineTo(x - ux * 3 - uy * 4, y - uy * 3 + ux * 4);
        tri.lineTo(x - ux * 1.2, y - uy * 1.2);
        tri.lineTo(x - ux * 3 + uy * 4, y - uy * 3 - ux * 4);
        tri.close();
        paint.setColor(Skia.Color(`rgba(255,255,255,${dot})`));
        canvas.drawPath(tri.build(), paint);
      }
    });
  });

  return (
    <Canvas pointerEvents="none" style={[StyleSheet.absoluteFill, { width: w, height: h }]}>
      <Group matrix={matrix}>
        <Picture picture={city} />
        <Path color="#FFFFFF" path={route} strokeCap="round" strokeJoin="round" strokeWidth={casingW} style="stroke" />
        <Path color={ROUTE_BLUE} path={route} strokeCap="round" strokeJoin="round" strokeWidth={lineW} style="stroke" />
        <Path
          color={TRAVELLED}
          end={p.travEnd}
          opacity={travOpacity}
          path={route}
          start={0}
          strokeCap="round"
          strokeJoin="round"
          strokeWidth={lineW}
          style="stroke"
        />
        <Group opacity={ringOpacity}>
          <Path color={ROUTE_BLUE} path={doorRing} strokeWidth={ringStroke} style="stroke" />
        </Group>
        <Path color={ROUTE_BLUE} opacity={pulseOpacity} path={pulsePath} strokeWidth={ringStroke} style="stroke" />
      </Group>
      <Picture picture={layer} />
    </Canvas>
  );
});
