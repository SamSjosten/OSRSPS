// node --test .github/scripts/*.test.js : the headless script runs' helpers on made-up script names and summaries.
const { test } = require('node:test');
const assert = require('node:assert');
const { changed, part, merge } = require('./headless.js');

test('a pull request plays the scripts it changed, a pair once, and nothing it deleted or that is no script', () => {
  const paths = [
    'src/main/kotlin/osrsps/game/World.kt',
    'test-runner/README.md',
    'test-runner/examples/hans-demo.json',
    'test-runner/examples/social-pm-test2.json',
    'test-runner/examples/social-trade-test.json',
    'test-runner/examples/social-trade-test2.json',
    'test-runner/examples/gone.json',
    'test-runner/examples/notes/readme.json',
  ];
  const names = changed(paths, file => !file.endsWith('gone.json'));
  assert.deepStrictEqual(names, ['hans-demo.json', 'social-pm-test2.json', 'social-trade-test.json']);
  assert.deepStrictEqual(changed(['build.gradle.kts'], () => true), []);
});

test('the parts of a nightly run hold every script or pair once between them', () => {
  const names = ['c.json', 'a.json', 'b-test2.json', 'b-test.json', 'd.json', 'e-test2.json', 'f.json'];
  const parts = [1, 2, 3].map(i => part(names, i, 3));
  assert.deepStrictEqual(parts, [['a.json', 'd.json'], ['b-test.json', 'e-test2.json'], ['c.json', 'f.json']]);
  assert.throws(() => part(names, 4, 3), /isn't one of/);
  assert.throws(() => part(names, 0, 3), /isn't one of/);
});

const row = (script, result, failure = null) => ({ script, result, steps: '3/4', seconds: 5, failure, rerun: null });

test('the parts\' summaries become one, and the run passes when nothing failed', () => {
  const one = { sha: 'abc', runner: 'headless 0.26.0', started: '2026-11-15T04:00:05Z', finished: '2026-11-15T04:20:00Z', rows: [row('b.json', 'pass'), row('d.json', 'flaky')] };
  const two = { sha: 'abc', runner: 'headless 0.26.0', started: '2026-11-15T04:00:01Z', finished: '2026-11-15T04:31:00Z', rows: [row('a.json', 'pass')] };
  const merged = merge([one, two]);
  assert.strictEqual(merged.ok, true);
  assert.deepStrictEqual(merged.summary.rows.map(r => r.script), ['a.json', 'b.json', 'd.json']);
  assert.strictEqual(merged.summary.sha, 'abc');
  assert.strictEqual(merged.summary.started, '2026-11-15T04:00:01Z');
  assert.strictEqual(merged.summary.finished, '2026-11-15T04:31:00Z');
  assert.match(merged.markdown, /2 passed, 1 flaky, 0 failed, of 3/);
  assert.match(merged.markdown, /### Flaky/);
  assert.doesNotMatch(merged.markdown, /### Failed/);
});

test('a failed script, a part with no summary and a run with no scripts each fail the run', () => {
  const failed = merge([{ rows: [row('a.json', 'pass'), row('b.json', 'fail', 'step 2 (x | y):\n timed out')] }]);
  assert.strictEqual(failed.ok, false);
  assert.match(failed.markdown, /\| b\.json \| 3\/4 \| step 2 \(x \\\| y\): timed out \|/);
  const missing = merge([{ rows: [row('a.json', 'pass')] }], 2);
  assert.strictEqual(missing.ok, false);
  assert.match(missing.markdown, /1 of 2 parts left no summary/);
  assert.strictEqual(merge([]).ok, false);
  assert.strictEqual(merge([{ rows: [row('a.json', 'error', 'no partner')] }]).ok, false);
});
