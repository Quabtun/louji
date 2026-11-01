import sharp from 'sharp';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../..');
const iconDir = path.join(root, 'design', 'icon');
const resDir = path.join(root, 'app', 'src', 'main', 'res');

const master = path.join(iconDir, 'louji-master.svg');
const round = path.join(iconDir, 'louji-round-1024.svg');
const foreground = path.join(iconDir, 'louji-foreground-108.svg');
const monochrome = path.join(iconDir, 'louji-monochrome-108.svg');

const densities = {
  mdpi: 1,
  hdpi: 1.5,
  xhdpi: 2,
  xxhdpi: 3,
  xxxhdpi: 4,
};

async function render(source, size, target, { background = null } = {}) {
  await fs.mkdir(path.dirname(target), { recursive: true });
  let pipeline = sharp(source, { density: 384 }).resize(size, size, { fit: 'contain' });
  if (background) {
    pipeline = pipeline.flatten({ background });
  }
  await pipeline.png({ compressionLevel: 9 }).toFile(target);
}

for (const [density, scale] of Object.entries(densities)) {
  const dir = path.join(resDir, `mipmap-${density}`);
  await render(master, Math.round(48 * scale), path.join(dir, 'ic_launcher.png'));
  await render(round, Math.round(48 * scale), path.join(dir, 'ic_launcher_round.png'));
  await render(foreground, Math.round(108 * scale), path.join(dir, 'ic_launcher_foreground.png'));
  await render(monochrome, Math.round(108 * scale), path.join(dir, 'ic_launcher_monochrome.png'));
}

await render(master, 192, path.join(iconDir, 'preview-192.png'));
await render(master, 48, path.join(iconDir, 'preview-48.png'));
await render(master, 512, path.join(iconDir, 'louji-512.png'));

const previewSize = 512;
const bg = await sharp({
  create: {
    width: previewSize,
    height: previewSize,
    channels: 4,
    background: '#121517',
  },
}).png().toBuffer();
const fg = await sharp(foreground, { density: 384 })
  .resize(previewSize, previewSize, { fit: 'contain' })
  .png()
  .toBuffer();
await sharp(bg)
  .composite([{ input: fg, blend: 'over' }])
  .png()
  .toFile(path.join(iconDir, 'adaptive-preview.png'));

console.log('Rendered launcher icons into', resDir);
