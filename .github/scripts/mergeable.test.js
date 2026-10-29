// node --test .github/scripts/ : plays mergeable.js through its cases against a stand-in for GitHub's API.
const test = require('node:test');
const assert = require('node:assert');
const run = require('./mergeable.js');
const { LABEL, OPEN, RESOLVED } = run;

// A repository whose pull requests are { number: { mergeable, labels, comments, head } }; `nulls` is how many times
// each answers mergeable: null first, as GitHub does while it works it out.
function fakeGitHub(prs, nulls = 0) {
  const calls = [];
  const asked = {};
  const rest = {
    pulls: {
      list: 'pulls.list',
      get: async ({ pull_number }) => {
        const pr = prs[pull_number];
        asked[pull_number] = (asked[pull_number] || 0) + 1;
        const known = asked[pull_number] > nulls;
        return {
          data: {
            number: pull_number,
            state: pr.state || 'open',
            mergeable: known ? pr.mergeable : null,
            mergeable_state: known ? (pr.mergeable ? 'clean' : 'dirty') : 'unknown',
            labels: pr.labels.map(name => ({ name })),
            head: { sha: pr.head },
            base: { ref: 'main' },
          },
        };
      },
    },
    issues: {
      listComments: 'issues.listComments',
      createLabel: async args => { calls.push(['createLabel', args.name]); },
      addLabels: async args => { calls.push(['addLabels', args.issue_number, args.labels]); prs[args.issue_number].labels.push(...args.labels); },
      removeLabel: async args => { calls.push(['removeLabel', args.issue_number, args.name]); },
      createComment: async args => { calls.push(['createComment', args.issue_number, args.body]); },
      updateComment: async args => { calls.push(['updateComment', args.comment_id, args.body]); },
    },
    checks: {
      create: async args => { calls.push(['check', args.head_sha, args.conclusion]); },
    },
  };
  const paginate = async (what, args) => {
    if (what === 'pulls.list') return Object.keys(prs).map(Number).filter(n => (prs[n].state || 'open') === 'open').map(number => ({ number }));
    if (what === 'issues.listComments') return prs[args.issue_number].comments || [];
    throw new Error(`paginate ${what}`);
  };
  return { github: { rest, paginate }, calls };
}

const core = { info: () => {} };
const repo = { owner: 'SamSjosten', repo: 'OSRSPS' };
const pushToMain = { eventName: 'push', repo, payload: { after: 'abcdef1234567890' } };
const prEvent = number => ({ eventName: 'pull_request_target', repo, payload: { pull_request: { number } } });
const go = (github, context) => run({ github, context, core, sleep: async () => {}, tries: 5 });

test('a push to main flags a pull request that starts to conflict, and only that one', async () => {
  const { github, calls } = fakeGitHub({
    1: { mergeable: true, labels: [], head: 'h1' },
    2: { mergeable: false, labels: [], head: 'h2' },
  });
  await go(github, pushToMain);
  assert.deepStrictEqual(calls.map(c => c.slice(0, 2)), [
    ['createLabel', LABEL],
    ['addLabels', 2],
    ['createComment', 2],
    ['check', 'h2'],
  ]);
  assert.strictEqual(calls[3][2], 'failure');
  assert.ok(calls[2][2].startsWith(OPEN));
  assert.match(calls[2][2], /moved to abcdef1/);
});

test('a push to main leaves alone a pull request that was flagged already', async () => {
  const { github, calls } = fakeGitHub({ 2: { mergeable: false, labels: [LABEL], head: 'h2' } });
  await go(github, pushToMain);
  assert.deepStrictEqual(calls, []);
});

test('a pull request that merges cleanly again loses the label, its comment is resolved and its check passes', async () => {
  const { github, calls } = fakeGitHub({
    3: {
      mergeable: true,
      labels: [LABEL],
      head: 'h3',
      comments: [
        { id: 30, user: { type: 'User' }, body: `${OPEN} quoted by a person` },
        { id: 31, user: { type: 'Bot' }, body: `${OPEN}\nconflict` },
        { id: 32, user: { type: 'Bot' }, body: `${RESOLVED}\nan older one` },
      ],
    },
  });
  await go(github, prEvent(3));
  assert.deepStrictEqual(calls.map(c => c.slice(0, 2)), [['removeLabel', 3], ['updateComment', 31], ['check', 'h3']]);
  assert.ok(calls[1][2].startsWith(RESOLVED));
  assert.strictEqual(calls[2][2], 'success');
});

test("a pull request's own event writes its check every time, with no comment when nothing changed", async () => {
  const clean = fakeGitHub({ 4: { mergeable: true, labels: [], head: 'h4' } });
  await go(clean.github, prEvent(4));
  assert.deepStrictEqual(clean.calls, [['check', 'h4', 'success']]);

  const still = fakeGitHub({ 5: { mergeable: false, labels: [LABEL], head: 'h5b' } });
  await go(still.github, prEvent(5));
  assert.deepStrictEqual(still.calls, [['check', 'h5b', 'failure']]);
});

test('it waits for GitHub to work mergeability out, and writes nothing while it never does', async () => {
  const late = fakeGitHub({ 6: { mergeable: false, labels: [], head: 'h6' } }, 3);
  await go(late.github, prEvent(6));
  assert.deepStrictEqual(late.calls.map(c => c[0]), ['createLabel', 'addLabels', 'createComment', 'check']);

  const never = fakeGitHub({ 7: { mergeable: false, labels: [], head: 'h7' } }, 99);
  await go(never.github, prEvent(7));
  assert.deepStrictEqual(never.calls, []);
});

test('the label is made once a run, and one that exists already is fine', async () => {
  const { github, calls } = fakeGitHub({
    8: { mergeable: false, labels: [], head: 'h8' },
    9: { mergeable: false, labels: [], head: 'h9' },
  });
  github.rest.issues.createLabel = async () => { calls.push(['createLabel']); throw Object.assign(new Error('exists'), { status: 422 }); };
  await go(github, pushToMain);
  assert.strictEqual(calls.filter(c => c[0] === 'createLabel').length, 1);
  assert.strictEqual(calls.filter(c => c[0] === 'createComment').length, 2);
});
