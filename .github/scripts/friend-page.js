// The old friend download page (docs/, served by GitHub Pages; README.md, "Letting friends test"), checked by the
// friend-page job in .github/workflows/build.yml. The launcher and its page moved to the public repository OdinsGate
// with launcher 1.0.2, but every launcher installed from here (1.0.0, 1.0.1) still sends its user to this page for
// "Please download the new installer", so it stays as a forward to the same page there, until this repository goes
// private and Pages here ends:
//   - every page forwards at once (a meta refresh) to the page of the same name under OdinsGate's page, and links
//     that one, and only that one, for a browser that doesn't follow the refresh;
//   - no page carries a banned word (the launcher's Names.BANNED) outside its links;
//   - nothing but such pages is there (and .nojekyll, which serves the files as they are), and index.html is.
//
//   node .github/scripts/friend-page.js [docs dir]
//
// The checks are check() below, on plain data; the command line reads the files and prints each problem.

const fs = require('fs');
const path = require('path');

const DOCS = 'docs';
const MOVED_TO = 'https://samsjosten.github.io/OdinsGate/';
// The launcher's Names.BANNED (OdinsGate, launcher/src/main/java/net/odinsgate/launcher/Names.java).
const BANNED = /runescape|osrs|jagex|scape|rsps/i;

// Where a page of this name forwards to: OdinsGate's page of the same name, its index as the folder itself.
function forwardFor(file) {
  return MOVED_TO + (file === 'index.html' ? '' : file);
}

// The problems with the pages: `files` maps each file name under docs/ to its text.
function check(files) {
  const problems = [];
  for (const [file, text] of Object.entries(files)) {
    if (file === '.nojekyll') continue;
    if (!file.endsWith('.html')) {
      problems.push(`${file}: only forwarding pages belong here`);
      continue;
    }
    const banned = text.replace(/\b(?:href|src|content)="[^"]*"/g, '').match(BANNED);
    if (banned) problems.push(`${file}: holds the banned word "${banned[0]}"`);
    const to = forwardFor(file);
    const refresh = text.match(/<meta http-equiv="refresh" content="0; url=([^"]*)">/);
    if (!refresh) problems.push(`${file}: doesn't forward at once`);
    else if (refresh[1] !== to) problems.push(`${file}: forwards to "${refresh[1]}", not ${to}`);
    const links = [...text.matchAll(/<a\b[^>]*\bhref="([^"]*)"/g)].map(m => m[1]);
    if (!links.includes(to)) problems.push(`${file}: doesn't link ${to}`);
    for (const link of links) {
      if (link !== to) problems.push(`${file}: links "${link}", not only ${to}`);
    }
  }
  if (!('index.html' in files)) problems.push('docs/ has no index.html');
  return problems;
}

function main(argv) {
  const docs = argv[0] || DOCS;
  const files = {};
  for (const entry of fs.readdirSync(docs, { withFileTypes: true })) {
    if (entry.isFile()) files[entry.name] = fs.readFileSync(path.join(docs, entry.name), 'utf8');
  }
  const problems = check(files);
  for (const problem of problems) console.log(problem);
  if (problems.length > 0) {
    console.log(`${problems.length} problem(s) with the friend page`);
    return 1;
  }
  console.log(`friend page: ${Object.keys(files).filter(f => f.endsWith(".html")).length} page(s) in ${docs} forward to ${MOVED_TO}`);
  return 0;
}

module.exports = { check, forwardFor, MOVED_TO };

if (require.main === module) process.exit(main(process.argv.slice(2)));
