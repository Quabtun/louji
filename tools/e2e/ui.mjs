import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export const ADB = process.env.ADB || 'D:/android/sdk/platform-tools/adb.exe';
export const PACKAGE = process.env.LOUJI_PACKAGE || 'com.quabtun.louji.debug';
export const ACTIVITY = `${PACKAGE}/com.quabtun.louji.MainActivity`;

export function adb(args, options = {}) {
  return execFileSync(ADB, args, {
    encoding: options.encoding ?? 'utf8',
    stdio: options.stdio ?? ['ignore', 'pipe', 'pipe'],
    maxBuffer: 32 * 1024 * 1024,
  });
}

export function dumpUi() {
  adb(['shell', 'uiautomator', 'dump', '/sdcard/louji-ui.xml']);
  return adb(['exec-out', 'cat', '/sdcard/louji-ui.xml']);
}

export function parseNodes(xml) {
  const nodes = [];
  const nodeRe = /<node\b([^>]*?)\/?>/g;
  const attrRe = /([\w:-]+)="([^"]*)"/g;
  let match;
  while ((match = nodeRe.exec(xml)) !== null) {
    const attrs = {};
    let attr;
    attrRe.lastIndex = 0;
    while ((attr = attrRe.exec(match[1])) !== null) attrs[attr[1]] = attr[2];
    const bounds = /\[(\d+),(\d+)\]\[(\d+),(\d+)\]/.exec(attrs.bounds || '');
    if (!bounds) continue;
    const [x1, y1, x2, y2] = bounds.slice(1).map(Number);
    nodes.push({
      ...attrs,
      x1, y1, x2, y2,
      cx: Math.round((x1 + x2) / 2),
      cy: Math.round((y1 + y2) / 2),
    });
  }
  return nodes;
}

export function nodes() {
  return parseNodes(dumpUi());
}

export function findById(id, list = null) {
  return (list || nodes()).find((node) => node['resource-id'] === id);
}

export function findByText(text, list = null) {
  return (list || nodes()).find(
    (node) => node.text === text || node['content-desc'] === text,
  );
}

export function tapPoint(x, y) {
  adb(['shell', 'input', 'tap', String(x), String(y)]);
}

export function tapId(id) {
  const node = findById(id);
  if (!node) throw new Error(`UI node not found: ${id}`);
  tapPoint(node.cx, node.cy);
  return node;
}

export function tapText(text) {
  const node = findByText(text);
  if (!node) throw new Error(`UI text not found: ${text}`);
  tapPoint(node.cx, node.cy);
  return node;
}

export function typeInto(id, text) {
  const node = findById(id);
  if (!node) throw new Error(`UI input not found: ${id}`);
  tapPoint(node.cx, node.cy);
  adb(['shell', 'input', 'text', text.replaceAll(' ', '%s')]);
}

export function pressBack() {
  adb(['shell', 'input', 'keyevent', 'KEYCODE_BACK']);
}

export function launch() {
  adb(['shell', 'am', 'force-stop', PACKAGE]);
  adb(['shell', 'am', 'start', '-n', ACTIVITY]);
}

export function forceStop() {
  adb(['shell', 'am', 'force-stop', PACKAGE]);
}

export function screenshot(file) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const buffer = execFileSync(ADB, ['exec-out', 'screencap', '-p'], {
    maxBuffer: 32 * 1024 * 1024,
  });
  fs.writeFileSync(file, buffer);
  return file;
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const [command, ...args] = process.argv.slice(2);
  const commands = {
    dump: () => console.log(JSON.stringify(nodes(), null, 2)),
    ids: () => console.log([...new Set(nodes().map((n) => n['resource-id']).filter(Boolean))].join('\n')),
    tap: () => console.log(tapId(args[0])),
    text: () => console.log(tapText(args[0])),
    type: () => typeInto(args[0], args.slice(1).join(' ')),
    shot: () => console.log(screenshot(args[0])),
    launch: () => launch(),
    back: () => pressBack(),
  };
  if (!commands[command]) {
    console.error(`usage: node ui.mjs <${Object.keys(commands).join('|')}> ...`);
    process.exit(1);
  }
  commands[command]();
}
