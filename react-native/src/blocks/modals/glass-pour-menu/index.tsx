import { GlassPourMenu } from './GlassPourMenu';
import { COPY } from './gpm-theme';
export { GlassPourMenu, type GlassPourMenuProps, type GlassPourMenuItem } from './GlassPourMenu';

export default function GlassPourMenuDemo() {
  return <GlassPourMenu items={[...COPY.rows]} onSelect={() => {}} autoPlay />;
}
