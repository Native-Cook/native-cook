export type Vec2 = [number, number];
export type Vec3 = [number, number, number];
export type Vertex = { point: Vec3; uv: Vec2 };
export type Triangle = { a: Vertex; b: Vertex; c: Vertex; cut: boolean };
export type MeshPiece = { id: number; center: Vec3; pivot: Vec3; finalCenter: Vec3; finalRotation: Vec3; delay: number; polygon: Vec2[]; triangles: Triangle[] };
export type Geometry = { hull: Vec2[]; pieces: MeshPiece[]; cracks: { id: number; piece: number; a: Vec3; b: Vec3 }[] };
// Exported directly from the frozen Swift geometry; no independent tessellation.
export const GEOMETRY: Geometry = require('./assets/geometry.json');
