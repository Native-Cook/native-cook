// The shop's pieces: sneakers, scents and jewellery, each with its Unsplash photo.
export type Piece = {
  id: string;
  name: string;
  kind: string;
  price: string;
  photo: string;
};

export const photo = (id: string, width: number) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=80`;

export const PIECES: Piece[] = [
  { id: 'fairline', name: 'Fairline Duo', kind: 'Runner', price: '€168', photo: 'photo-1560769629-975ec94e6a86' },
  { id: 'bordeaux', name: 'Bordeaux Garnet', kind: 'Ring', price: '€212', photo: 'photo-1735212214451-a8f43a015309' },
  { id: 'stairway', name: 'Stairway Hoops', kind: 'Earrings', price: '€86', photo: 'photo-1751552147823-e2a844bce504' },
  { id: 'airlace', name: 'Airlace Chain', kind: 'Necklace', price: '€74', photo: 'photo-1751552147774-c374ae8e9910' },
  { id: 'dune', name: 'Dune Slip Low', kind: 'Slip-on', price: '€148', photo: 'photo-1603808033192-082d6919d3e1' },
  { id: 'paired', name: 'Paired Crimson 4', kind: 'Court', price: '€189', photo: 'photo-1605523741177-cd660595c2cf' },
  { id: 'daisy', name: 'Daisy Cluster', kind: 'Ring', price: '€164', photo: 'photo-1751551683594-ab054a031696' },
  { id: 'rose', name: 'Rose Braid', kind: 'Bangle', price: '€128', photo: 'photo-1735905131335-a921150a017d' },
  { id: 'gilded', name: 'Gilded Air Extrait', kind: 'Extrait · 30 ml', price: '€138', photo: 'photo-1622618991746-fe6004db3a47' },
  { id: 'cabinet', name: 'Cabinet Brooch', kind: 'Brooch', price: '€236', photo: 'photo-1615957579676-d1db3be6effe' },
  { id: 'amber', name: 'Amber Heirloom', kind: 'Eau de parfum · 75 ml', price: '€118', photo: 'photo-1535683577427-740aaac4ec25' },
  { id: 'satin', name: 'Satin Petal', kind: 'Eau de parfum · 50 ml', price: '€104', photo: 'photo-1543422655-ac1c6ca993ed' },
];

export const RECENT = ['runner', 'amber', 'court', 'ring', 'extrait', 'parfum'];

export const POPULAR = ['Fairline Duo', 'Gilded Air Extrait', 'Daisy Cluster', 'Satin Petal'];

/** The pieces whose name holds the query, in the shop's order. */
export function search(query: string) {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  return PIECES.filter((p) => p.name.toLowerCase().includes(q) || p.kind.toLowerCase().includes(q));
}
