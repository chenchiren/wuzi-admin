/**
 * 内置 SVG 图标定义（24×24 线框风格，stroke=currentColor）
 * 每个图标：{ viewBox, parts: [{ tag, attrs }] }
 * 可通过 registry.registerIcon() 覆盖或新增
 */
export const builtinIcons = {
  // —— 导航分组 ——
  dashboard: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'rect', attrs: { x: '3', y: '3', width: '7', height: '9', rx: '1' } },
      { tag: 'rect', attrs: { x: '14', y: '3', width: '7', height: '5', rx: '1' } },
      { tag: 'rect', attrs: { x: '14', y: '12', width: '7', height: '9', rx: '1' } },
      { tag: 'rect', attrs: { x: '3', y: '16', width: '7', height: '5', rx: '1' } }
    ]
  },
  database: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'ellipse', attrs: { cx: '12', cy: '5', rx: '8', ry: '3' } },
      { tag: 'path', attrs: { d: 'M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5' } },
      { tag: 'path', attrs: { d: 'M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6' } }
    ]
  },
  cart: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M3 4h2l2.4 11.2a2 2 0 0 0 2 1.6h7.8a2 2 0 0 0 2-1.5L21 8H6' } },
      { tag: 'circle', attrs: { cx: '10', cy: '20', r: '1.2' } },
      { tag: 'circle', attrs: { cx: '17', cy: '20', r: '1.2' } }
    ]
  },
  warehouse: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M3 21V8l9-5 9 5v13' } },
      { tag: 'path', attrs: { d: 'M7 21v-8h10v8' } },
      { tag: 'path', attrs: { d: 'M7 17h10' } }
    ]
  },
  hand: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M8 11V6a1.5 1.5 0 0 1 3 0v5' } },
      { tag: 'path', attrs: { d: 'M11 11V5a1.5 1.5 0 0 1 3 0v6' } },
      { tag: 'path', attrs: { d: 'M14 11V7a1.5 1.5 0 0 1 3 0v7a7 7 0 0 1-7 7h-1a7 7 0 0 1-7-7v-2a1.5 1.5 0 0 1 3 0' } }
    ]
  },
  assets: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'rect', attrs: { x: '3', y: '8', width: '18', height: '12', rx: '2' } },
      { tag: 'path', attrs: { d: 'M3 12h18' } },
      { tag: 'path', attrs: { d: 'M12 8V6a2 2 0 0 1 2-2h1' } },
      { tag: 'circle', attrs: { cx: '8', cy: '16', r: '1.2', fill: 'currentColor', stroke: 'none' } }
    ]
  },
  'qr-code': {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'rect', attrs: { x: '3', y: '3', width: '7', height: '7', rx: '1' } },
      { tag: 'rect', attrs: { x: '14', y: '3', width: '7', height: '7', rx: '1' } },
      { tag: 'rect', attrs: { x: '3', y: '14', width: '7', height: '7', rx: '1' } },
      { tag: 'path', attrs: { d: 'M14 14h3v3h-3zM19 14h2M14 19h2M18 18h3v3h-3z' } }
    ]
  },
  chart: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M4 19V5' } },
      { tag: 'path', attrs: { d: 'M4 19h16' } },
      { tag: 'path', attrs: { d: 'M8 16v-5' } },
      { tag: 'path', attrs: { d: 'M12 16V8' } },
      { tag: 'path', attrs: { d: 'M16 16v-3' } }
    ]
  },
  settings: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'circle', attrs: { cx: '12', cy: '12', r: '3' } },
      {
        tag: 'path',
        attrs: {
          d: 'M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3h.1a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5h.1a1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8v.1a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z'
        }
      }
    ]
  },

  // —— 菜单项 ——
  package: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M12 3 20 7.5v9L12 21l-8-4.5v-9L12 3z' } },
      { tag: 'path', attrs: { d: 'M4 7.5 12 12l8-4.5' } },
      { tag: 'path', attrs: { d: 'M12 12v9' } }
    ]
  },
  folders: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M4 20h12a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-5l-2-2H4a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2z' } },
      { tag: 'path', attrs: { d: 'M8 7V5a2 2 0 0 1 2-2h5l2 2h3a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2' } }
    ]
  },
  truck: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M3 6h11v11H3z' } },
      { tag: 'path', attrs: { d: 'M14 10h4l3 3v4h-7' } },
      { tag: 'circle', attrs: { cx: '7.5', cy: '18.5', r: '1.5' } },
      { tag: 'circle', attrs: { cx: '17.5', cy: '18.5', r: '1.5' } }
    ]
  },
  percent: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M19 5 5 19' } },
      { tag: 'circle', attrs: { cx: '7.5', cy: '7.5', r: '2.5' } },
      { tag: 'circle', attrs: { cx: '16.5', cy: '16.5', r: '2.5' } }
    ]
  },
  'file-text': {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5z' } },
      { tag: 'path', attrs: { d: 'M14 3v5h5' } },
      { tag: 'path', attrs: { d: 'M9 13h6M9 17h4' } }
    ]
  },
  receipt: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M6 3h12v18l-2-1.5L14 21l-2-1.5L10 21l-2-1.5L6 21V3z' } },
      { tag: 'path', attrs: { d: 'M9 8h6M9 12h6' } }
    ]
  },
  stamp: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M5 21h14' } },
      { tag: 'path', attrs: { d: 'M8 17h8v-2a4 4 0 0 0-1.2-2.9A4 4 0 0 1 13 8.2V7a2 2 0 1 0-4 0v1.2a4 4 0 0 1-1.8 3.9A4 4 0 0 0 8 15v2z' } }
    ]
  },
  'package-check': {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M12 3 20 7.5v5' } },
      { tag: 'path', attrs: { d: 'M4 7.5 12 12l3-1.7' } },
      { tag: 'path', attrs: { d: 'M4 7.5v9L12 21l3-1.7' } },
      { tag: 'path', attrs: { d: 'M16 19l2 2 4-4' } }
    ]
  },
  'arrow-in': {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M12 3v12' } },
      { tag: 'path', attrs: { d: 'm7 10 5 5 5-5' } },
      { tag: 'path', attrs: { d: 'M4 21h16' } }
    ]
  },
  'arrow-out': {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M12 21V9' } },
      { tag: 'path', attrs: { d: 'm7 14 5-5 5 5' } },
      { tag: 'path', attrs: { d: 'M4 3h16' } }
    ]
  },
  boxes: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M3 8.5 7.5 6 12 8.5V14L7.5 16.5 3 14V8.5z' } },
      { tag: 'path', attrs: { d: 'M12 8.5 16.5 6 21 8.5V14L16.5 16.5 12 14' } },
      { tag: 'path', attrs: { d: 'M7.5 16.5V21L12 21 16.5 21v-4.5' } }
    ]
  },
  transfer: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M7 7h13' } },
      { tag: 'path', attrs: { d: 'm16 3 4 4-4 4' } },
      { tag: 'path', attrs: { d: 'M17 17H4' } },
      { tag: 'path', attrs: { d: 'm8 21-4-4 4-4' } }
    ]
  },
  clipboard: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'rect', attrs: { x: '5', y: '4', width: '14', height: '17', rx: '2' } },
      { tag: 'path', attrs: { d: 'M9 4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v1H9V4z' } },
      { tag: 'path', attrs: { d: 'm9 13 2 2 4-4' } }
    ]
  },
  clock: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'circle', attrs: { cx: '12', cy: '12', r: '9' } },
      { tag: 'path', attrs: { d: 'M12 7v5l3 2' } }
    ]
  },
  trash: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M4 7h16' } },
      { tag: 'path', attrs: { d: 'M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2' } },
      { tag: 'path', attrs: { d: 'M6 7l1 13a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-13' } },
      { tag: 'path', attrs: { d: 'M10 11v6M14 11v6' } }
    ]
  },
  alert: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M12 3 2.5 20h19L12 3z' } },
      { tag: 'path', attrs: { d: 'M12 10v4' } },
      { tag: 'circle', attrs: { cx: '12', cy: '17', r: '0.8', fill: 'currentColor', stroke: 'none' } }
    ]
  },
  scan: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M4 8V5a1 1 0 0 1 1-1h3' } },
      { tag: 'path', attrs: { d: 'M20 8V5a1 1 0 0 0-1-1h-3' } },
      { tag: 'path', attrs: { d: 'M4 16v3a1 1 0 0 0 1 1h3' } },
      { tag: 'path', attrs: { d: 'M20 16v3a1 1 0 0 1-1 1h-3' } },
      { tag: 'path', attrs: { d: 'M7 12h10' } }
    ]
  },
  printer: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M6 9V3h12v6' } },
      { tag: 'path', attrs: { d: 'M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2' } },
      { tag: 'rect', attrs: { x: '6', y: '14', width: '12', height: '7', rx: '1' } }
    ]
  },
  gauge: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M4 19a8 8 0 1 1 16 0' } },
      { tag: 'path', attrs: { d: 'm12 15 3.5-5' } },
      { tag: 'circle', attrs: { cx: '12', cy: '15', r: '1.2', fill: 'currentColor', stroke: 'none' } }
    ]
  },
  'file-chart': {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5z' } },
      { tag: 'path', attrs: { d: 'M14 3v5h5' } },
      { tag: 'path', attrs: { d: 'M9 17v-3M12 17v-5M15 17v-2' } }
    ]
  },
  user: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'circle', attrs: { cx: '12', cy: '8', r: '4' } },
      { tag: 'path', attrs: { d: 'M5 21a7 7 0 0 1 14 0' } }
    ]
  },
  building: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M4 21V4a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v17' } },
      { tag: 'path', attrs: { d: 'M14 9h5a1 1 0 0 1 1 1v11' } },
      { tag: 'path', attrs: { d: 'M8 7h2M8 11h2M8 15h2' } },
      { tag: 'path', attrs: { d: 'M3 21h18' } }
    ]
  },
  shield: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M12 3 5 6v5c0 4.5 2.8 8.2 7 10 4.2-1.8 7-5.5 7-10V6l-7-3z' } },
      { tag: 'path', attrs: { d: 'm9 12 2 2 4-4' } }
    ]
  },
  sheet: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5z' } },
      { tag: 'path', attrs: { d: 'M14 3v5h5' } },
      { tag: 'path', attrs: { d: 'M8 13h8M8 17h8M10 13v4M14 13v4' } }
    ]
  },
  history: {
    viewBox: '0 0 24 24',
    parts: [
      { tag: 'path', attrs: { d: 'M3 12a9 9 0 1 0 3-6.7' } },
      { tag: 'path', attrs: { d: 'M3 4v5h5' } },
      { tag: 'path', attrs: { d: 'M12 8v4l3 2' } }
    ]
  }
}
