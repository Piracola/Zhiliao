/**
 * 从 Lucide 生成设置页图标（app/src/main/res/drawable/ic_*.xml）。
 *
 * 用法：node tools/update-icons.mjs [输出目录]
 * 依赖：只用到 Node 自带的 fetch / fs；图标来自 jsDelivr 上的 lucide-static。
 *
 * 图标来源：Lucide (https://lucide.dev) v1.47.0，ISC License。
 * ISC 许可要求随副本保留以下声明：
 *
 *   ISC License
 *   Copyright (c) 2026 Lucide Icons and Contributors
 *
 *   Permission to use, copy, modify, and/or distribute this software for any
 *   purpose with or without fee is hereby granted, provided that the above
 *   copyright notice and this permission notice appear in all copies.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *   WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *   MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *   ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *   WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *   ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *   OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
import fs from 'node:fs';
import path from 'node:path';

const icons = {"ic_ad_units":"megaphone","ic_add_circle":"circle-plus","ic_article":"file-text","ic_auto_delete":"eraser","ic_block":"ban","ic_check":"check","ic_circle_down":"circle-arrow-down","ic_close":"x","ic_code":"code","ic_color":"palette","ic_comment":"message-square","ic_cross_star":"sparkles","ic_delete":"trash-2","ic_emoji_objects":"lightbulb","ic_event":"calendar-days","ic_format_list":"list","ic_fullscreen":"maximize","ic_fullscreen_exit":"minimize","ic_group":"users","ic_help":"circle-help","ic_image":"image","ic_info":"info","ic_label":"tag","ic_layers":"layers","ic_link":"link","ic_local_mall":"store","ic_login":"log-in","ic_mark_chat_unread":"message-square-dot","ic_monetization":"circle-dollar-sign","ic_notes":"align-left","ic_notifications_off":"bell-off","ic_person":"user","ic_person_add_alt":"user-plus","ic_plagiarism":"filter","ic_play_circle":"circle-play","ic_refresh":"refresh-cw","ic_restart":"rotate-cw","ic_rss_feed":"rss","ic_scroll_text":"scroll-text","ic_search":"search","ic_search_off":"search-x","ic_settings":"settings","ic_share":"share-2","ic_table_rows":"rows-3","ic_toggle_on":"toggle-right","ic_tooltip":"message-square-off","ic_viewcard":"layout-panel-top","ic_vip":"crown","ic_vip_banner":"gem","ic_whatshot":"flame"};

const version = '1.47.0';
const base = 'https://cdn.jsdelivr.net/npm/lucide-static@' + version + '/icons/';
const outDir = process.argv[2] ?? 'app/src/main/res/drawable';
const nl = String.fromCharCode(10);

const attr = (tag, name) => {
  const match = new RegExp(name + '="([^"]*)"').exec(tag);
  return match ? match[1] : null;
};

const number = (value) => (Number.isFinite(Number(value)) ? String(Number(value)) : '0');

// Lucide 用 24 网格、2px 圆头描边；Android 的 VectorDrawable 直接支持同样的描边属性
const clean = (d) => d.replace(/(\d)-/g, '$1 -').replace(/(^|[^\d.])\.(\d)/g, '$1' + '0.$2').replace(/\s+/g, ' ').trim();

const toPathData = (tag, tagName) => {
  if (tagName === 'path') return clean(attr(tag, 'd') ?? '') || null;
  if (tagName === 'circle' || tagName === 'ellipse') {
    const rx = Number(attr(tag, tagName === 'circle' ? 'r' : 'rx'));
    const ry = Number(attr(tag, tagName === 'circle' ? 'r' : 'ry'));
    const cx = Number(attr(tag, 'cx')), cy = Number(attr(tag, 'cy'));
    return 'M' + number(cx - rx) + ',' + number(cy) + 'a' + number(rx) + ',' + number(ry) + ' 0 1,0 ' + number(2 * rx) + ',0a' + number(rx) + ',' + number(ry) + ' 0 1,0 ' + number(-2 * rx) + ',0';
  }
  if (tagName === 'line') {
    return 'M' + number(attr(tag, 'x1')) + ',' + number(attr(tag, 'y1')) + 'L' + number(attr(tag, 'x2')) + ',' + number(attr(tag, 'y2'));
  }
  if (tagName === 'polyline' || tagName === 'polygon') {
    const points = (attr(tag, 'points') ?? '').trim().split(/\s+/).map((point) => point.split(','));
    if (points.length === 0) return null;
    let d = 'M' + number(points[0][0]) + ',' + number(points[0][1]);
    for (let i = 1; i < points.length; i++) d += 'L' + number(points[i][0]) + ',' + number(points[i][1]);
    return tagName === 'polygon' ? d + 'Z' : d;
  }
  if (tagName === 'rect') {
    const x = Number(attr(tag, 'x') ?? 0), y = Number(attr(tag, 'y') ?? 0);
    const width = Number(attr(tag, 'width')), height = Number(attr(tag, 'height'));
    const rx = attr(tag, 'rx') === null ? 0 : Number(attr(tag, 'rx'));
    const ry = attr(tag, 'ry') === null ? rx : Number(attr(tag, 'ry'));
    if (rx <= 0 || ry <= 0) return 'M' + number(x) + ',' + number(y) + 'h' + number(width) + 'v' + number(height) + 'h' + number(-width) + 'Z';
    const arc = 'a' + number(rx) + ',' + number(ry) + ' 0 0 1 ';
    return 'M' + number(x + rx) + ',' + number(y) + 'h' + number(width - 2 * rx) + arc + number(rx) + ',' + number(ry) +
      'v' + number(height - 2 * ry) + arc + number(-rx) + ',' + number(ry) +
      'h' + number(-(width - 2 * rx)) + arc + number(-rx) + ',' + number(-ry) +
      'v' + number(-(height - 2 * ry)) + arc + number(rx) + ',' + number(-ry) + 'Z';
  }
  return null;
};

let written = 0;
const failed = [];
for (const [resource, icon] of Object.entries(icons)) {
  let svg;
  try {
    const response = await fetch(base + icon + '.svg');
    if (!response.ok) throw new Error('HTTP ' + response.status);
    svg = await response.text();
  } catch (error) {
    failed.push(resource + ' -> ' + icon + ' (' + error.message + ')');
    continue;
  }
  const defaultWidth = attr(svg, 'stroke-width') ?? '2';
  const paths = [];
  for (const match of svg.matchAll(/<(path|circle|ellipse|line|polyline|polygon|rect)\b([^>]*?)\/?>/g)) {
    const data = toPathData(match[0], match[1]);
    if (!data) continue;
    const strokeWidth = attr(match[0], 'stroke-width') ?? defaultWidth;
    const filled = (attr(match[0], 'fill') ?? 'none') !== 'none';
    paths.push('    <path' + nl + '        android:pathData="' + data + '"' + (filled ? nl + '        android:fillColor="@android:color/white"' : '') +
      nl + '        android:strokeColor="@android:color/white"' + nl + '        android:strokeWidth="' + strokeWidth + '"' +
      nl + '        android:strokeLineCap="round"' + nl + '        android:strokeLineJoin="round"/>');
  }
  if (paths.length === 0) {
    failed.push(resource + ' -> ' + icon + ' (no geometry)');
    continue;
  }
  const xml = '<?xml version="1.0" encoding="utf-8"?>' + nl +
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"' + nl +
    '    android:width="24dp"' + nl + '    android:height="24dp"' + nl +
    '    android:viewportWidth="24"' + nl + '    android:viewportHeight="24"' + nl +
    '    android:tint="#808080">' + nl + paths.join(nl) + nl + '</vector>' + nl;
  fs.writeFileSync(path.join(outDir, resource + '.xml'), xml, 'utf8');
  written++;
}
console.log('written=' + written + ' failed=' + failed.length);
for (const failure of failed) console.log('FAIL ' + failure);
