// The headless script runs' helpers (.github/workflows/headless.yml): which runner scripts a pull request changed,
// which a nightly part plays, and the parts' summaries put together.
//
//   node .github/scripts/headless.js changed <base ref>          the changed scripts in test-runner/examples, by commas
//   node .github/scripts/headless.js part <examples dir> <i>/<n> part i of n of every script there, by commas
//   node .github/scripts/headless.js merge <dir> <out.json>      one summary.json from each one under <dir>
//
// `merge` prints what failed, writes it to the job's summary when GITHUB_STEP_SUMMARY is set, and exits 1 when a
// script failed or a part left no summary.

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const EXAMPLES = 'test-runner/examples/';

// A pair's other half (x-test.json and x-test2.json), or null for a script that plays alone. As HeadlessScripts.pairPartner.
function partner(name) {
  if (name.endsWith('-test.json')) return name.slice(0, -'-test.json'.length) + '-test2.json';
  if (name.endsWith('-test2.json')) return name.slice(0, -'-test2.json'.length) + '-test.json';
  return null;
}

// One name for each script or pair: the runner plays a pair whole from either half, so a second half whose first is
// listed too is dropped. Sorted, so every part of a run deals from the same order.
function units(names) {
  const all = new Set(names);
  return [...all].filter(name => !(name.endsWith('-test2.json') && all.has(partner(name)))).sort();
}

// The scripts among a pull request's changed paths that still exist (a deleted script has nothing to play).
function changed(paths, exists = file => fs.existsSync(file)) {
  const names = paths
    .filter(file => file.startsWith(EXAMPLES) && file.endsWith('.json') && !file.slice(EXAMPLES.length).includes('/'))
    .filter(file => exists(file))
    .map(file => file.slice(EXAMPLES.length));
  return units(names);
}

// Part `index` (from 1) of `count`: every count-th script or pair, so the parts are about as long as each other.
function part(names, index, count) {
  if (!(Number.isInteger(index) && Number.isInteger(count) && index >= 1 && index <= count)) {
    throw new Error(`part ${index}/${count} isn't one of 1/${count} to ${count}/${count}`);
  }
  return units(names).filter((_, i) => i % count === index - 1);
}

// The parts' summaries as one, in the nightly's format, with the scripts by name. `expected` is how many parts there
// were: one that left no summary (its job died) is a failure of the run.
function merge(summaries, expected = summaries.length) {
  const rows = summaries.flatMap(summary => summary.rows || []).sort((a, b) => a.script.localeCompare(b.script));
  const count = result => rows.filter(row => row.result === result).length;
  const first = summaries[0] || {};
  const started = summaries.map(summary => summary.started).filter(Boolean).sort()[0] || null;
  const finished = summaries.map(summary => summary.finished).filter(Boolean).sort().pop() || null;
  const failed = rows.filter(row => row.result !== 'pass' && row.result !== 'flaky');
  const missing = Math.max(0, expected - summaries.length);
  const lines = [`Headless scripts: ${count('pass')} passed, ${count('flaky')} flaky, ${failed.length} failed, of ${rows.length}`];
  if (missing > 0) lines.push('', `${missing} of ${expected} parts left no summary: their scripts aren't counted above.`);
  const table = (title, list) => {
    if (list.length === 0) return;
    lines.push('', `### ${title}`, '', '| Script | Steps | Why |', '| --- | --- | --- |');
    for (const row of list) lines.push(`| ${row.script} | ${row.steps} | ${cell(row.failure || row.rerun || '')} |`);
  };
  table('Failed', failed);
  table('Flaky (failed, then passed when played again)', rows.filter(row => row.result === 'flaky'));
  return {
    summary: {
      sha: first.sha || null,
      serverVersionStart: first.serverVersionStart || null,
      serverVersionEnd: first.serverVersionEnd || null,
      runner: first.runner || null,
      started,
      finished,
      rows,
    },
    markdown: lines.join('\n') + '\n',
    ok: failed.length === 0 && missing === 0 && rows.length > 0,
  };
}

// A failure's words in one table cell.
function cell(text) {
  return text.replace(/\|/g, '\\|').replace(/\s+/g, ' ').slice(0, 300);
}

function summaryFiles(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { recursive: true }).map(String).filter(file => path.basename(file) === 'summary.json').sort()
    .map(file => path.join(dir, file));
}

function main(args) {
  const [command, ...rest] = args;
  if (command === 'changed') {
    const out = execFileSync('git', ['diff', '--name-only', '--diff-filter=d', `${rest[0]}...HEAD`], { encoding: 'utf8' });
    console.log(changed(out.split('\n').filter(Boolean)).join(','));
  } else if (command === 'part') {
    const [index, count] = rest[1].split('/').map(Number);
    const names = fs.readdirSync(rest[0]).filter(name => name.endsWith('.json'));
    console.log(part(names, index, count).join(','));
  } else if (command === 'merge') {
    const summaries = summaryFiles(rest[0]).map(file => JSON.parse(fs.readFileSync(file, 'utf8')));
    const merged = merge(summaries, rest[2] ? Number(rest[2]) : summaries.length);
    fs.mkdirSync(path.dirname(rest[1]), { recursive: true });
    fs.writeFileSync(rest[1], JSON.stringify(merged.summary, null, 2) + '\n');
    console.log(merged.markdown);
    if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, merged.markdown);
    if (!merged.ok) process.exitCode = 1;
  } else {
    console.error('usage: headless.js changed <base> | part <dir> <i>/<n> | merge <dir> <out.json> [parts]');
    process.exitCode = 2;
  }
}

if (require.main === module) main(process.argv.slice(2));

module.exports = { partner, units, changed, part, merge };
