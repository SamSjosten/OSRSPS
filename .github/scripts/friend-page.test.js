// node --test .github/scripts/*.test.js : the forwarding page checks on made-up pages, then on the real docs/.
const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { check, forwardFor, MOVED_TO } = require('./friend-page.js');

function page(to, { refresh = to, links = [to], words = 'The download page has moved.' } = {}) {
  return `<html><head>${refresh === null ? '' : `<meta http-equiv="refresh" content="0; url=${refresh}">`}</head>`
    + `<body><p>${words}</p>${links.map(l => `<a href="${l}">${l}</a>`).join('')}</body></html>`;
}

test('each page forwards to the page of the same name there, the index to the folder', () => {
  assert.strictEqual(forwardFor('index.html'), MOVED_TO);
  assert.strictEqual(forwardFor('mac.html'), MOVED_TO + 'mac.html');
});

test('forwarding pages pass', () => {
  assert.deepStrictEqual(check({
    'index.html': page(MOVED_TO),
    'mac.html': page(MOVED_TO + 'mac.html'),
  }), []);
});

test('a page that forwards nowhere, or elsewhere, or doesn\'t link where it forwards is a problem', () => {
  assert.deepStrictEqual(check({ 'index.html': page(MOVED_TO, { refresh: null }) }),
    ['index.html: doesn\'t forward at once']);
  assert.deepStrictEqual(check({ 'index.html': page(MOVED_TO), 'mac.html': page(MOVED_TO + 'mac.html', { refresh: MOVED_TO }) }),
    [`mac.html: forwards to "${MOVED_TO}", not ${MOVED_TO}mac.html`]);
  assert.deepStrictEqual(check({ 'index.html': page(MOVED_TO, { links: [] }) }),
    [`index.html: doesn't link ${MOVED_TO}`]);
});

test('a page links only where it forwards: no installer or other host here any more', () => {
  const installer = 'https://github.com/SamSjosten/OdinsGate/releases/latest/download/OdinsGate-Setup.exe';
  assert.deepStrictEqual(check({ 'index.html': page(MOVED_TO, { links: [MOVED_TO, installer] }) }),
    [`index.html: links "${installer}", not only ${MOVED_TO}`]);
});

test('a banned word in the text is a problem, but not inside a link', () => {
  assert.deepStrictEqual(check({ 'index.html': page(MOVED_TO, { words: 'Made for the old school scape game' }) }),
    ['index.html: holds the banned word "scape"']);
  assert.deepStrictEqual(check({ 'index.html': page(MOVED_TO, { words: 'Moved from samsjosten.github.io/OSRSPS' }) }),
    ['index.html: holds the banned word "OSRS"']);
});

test('only forwarding pages belong, and index.html must be there', () => {
  assert.deepStrictEqual(check({ 'index.html': page(MOVED_TO), 'style.css': 'body {}' }),
    ['style.css: only forwarding pages belong here']);
  assert.deepStrictEqual(check({ 'index.html': page(MOVED_TO), '.nojekyll': '' }), []);
  assert.deepStrictEqual(check({ 'mac.html': page(MOVED_TO + 'mac.html') }), ['docs/ has no index.html']);
});

test('the real docs/ forward every page the old page had', () => {
  const docs = path.join(__dirname, '..', '..', 'docs');
  const files = {};
  for (const entry of fs.readdirSync(docs, { withFileTypes: true })) {
    if (entry.isFile()) files[entry.name] = fs.readFileSync(path.join(docs, entry.name), 'utf8');
  }
  assert.deepStrictEqual(Object.keys(files).sort(), ['.nojekyll', 'index.html', 'linux.html', 'mac.html', 'windows.html']);
  assert.deepStrictEqual(check(files), []);
});
