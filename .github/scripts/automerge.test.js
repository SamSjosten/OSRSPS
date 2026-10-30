// node --test .github/scripts/*.test.js : plays automerge.js through its cases against a stand-in for GitHub's API and
// for git.
const test = require('node:test');
const assert = require('node:assert');
const automerge = require('./automerge.js');
const { CHECK, CODEX, NOTICE, verdict, covers } = automerge;

const summary = (sha, findings, status = 'completed') => ({
  user: { login: CODEX },
  created_at: '2026-10-29T22:00:00Z',
  body: `<!-- codex-pull-request-review-summary -->\n<!-- codex-review-state:v1 {"code_review":{"commit_sha":"${sha}","has_any_findings":${findings},"has_unresolved_findings":false,"status":"${status}"},"security_review":{"commit_sha":null}} -->\n## Codex Review Summary`,
});
const receipt = (sha, clean, at) => ({
  user: { login: CODEX },
  created_at: at,
  submitted_at: at,
  body: `<!-- codex-review-receipt:v1 -->\n\n${clean ? "Codex Review: Didn't find any major issues. Hooray!" : '### 💡 Codex Review\n\nHere are some automated review suggestions'}\n\n**Reviewed commit:** \`${sha.slice(0, 10)}\``,
});

test('the summary marker gives the reviewed commit and whether it had findings', () => {
  assert.deepStrictEqual(verdict([summary('a'.repeat(40), false)], []), { sha: 'a'.repeat(40), clean: true });
  assert.deepStrictEqual(verdict([summary('a'.repeat(40), true)], []), { sha: 'a'.repeat(40), clean: false });
  assert.deepStrictEqual(verdict([summary('a'.repeat(40), false, 'in_progress')], []), { running: true });
});

test('without a summary the latest receipt decides, comment or review', () => {
  const clean = receipt('b'.repeat(40), true, '2026-10-29T22:00:00Z');
  const findings = receipt('c'.repeat(40), false, '2026-10-29T23:00:00Z');
  assert.deepStrictEqual(verdict([clean], [findings]), { sha: 'c'.repeat(10), clean: false });
  assert.deepStrictEqual(verdict([findings], [{ ...clean, submitted_at: '2026-10-30T00:00:00Z' }]), { sha: 'b'.repeat(10), clean: true });
});

test('only Codex counts, and no review is null', () => {
  const forged = { ...summary('a'.repeat(40), false), user: { login: 'SamSjosten' } };
  assert.strictEqual(verdict([forged], []), null);
  assert.strictEqual(verdict([], []), null);
});

// A git that knows each commit's patch-id against main.
function fakeGit(patches) {
  return (args, input) => {
    if (args[0] === 'fetch') return '';
    if (args[0] === 'rev-parse') {
      const sha = Object.keys(patches).find(s => s.startsWith(args[2].replace('^{commit}', '')));
      if (!sha) throw new Error('unknown commit');
      return sha;
    }
    if (args[0] === 'merge-base') return 'base';
    if (args[0] === 'diff') return `diff of ${args[2]}`;
    if (args[0] === 'patch-id') return `${patches[input.replace('diff of ', '')]} x\n`;
    throw new Error(`git ${args.join(' ')}`);
  };
}

test('a review covers the head when it is the head or the same patch', () => {
  const git = fakeGit({ ['1'.repeat(40)]: 'p1', ['2'.repeat(40)]: 'p1', ['3'.repeat(40)]: 'p2' });
  assert.ok(covers(git, 5, '1'.repeat(10), '1'.repeat(40)));
  assert.ok(covers(git, 5, '1'.repeat(10), '2'.repeat(40)), 'main merged in, same diff');
  assert.ok(!covers(git, 5, '1'.repeat(10), '3'.repeat(40)), 'the diff changed');
  assert.ok(!covers(git, 5, '9'.repeat(10), '3'.repeat(40)), 'a reviewed commit that is gone');
});

// A repository whose pull requests are { number: { head, ref, behind, checks: [{ name, status, conclusion }],
// comments, autoMerge, mergeable } }.
function fakeGitHub(prs) {
  const calls = [];
  let nextId = 1000;
  const prData = n => {
    const pr = prs[n];
    return {
      number: n,
      state: 'open',
      draft: false,
      auto_merge: pr.autoMerge === false ? null : { merge_method: 'merge' },
      mergeable: pr.mergeable !== false,
      head: { sha: pr.head, ref: pr.ref || `branch-${n}` },
      base: { ref: 'main' },
    };
  };
  const rest = {
    pulls: {
      list: 'pulls.list',
      listReviews: 'pulls.listReviews',
      get: async ({ pull_number }) => ({ data: prData(pull_number) }),
      updateBranch: async ({ pull_number }) => {
        calls.push(['updateBranch', pull_number]);
        const pr = prs[pull_number];
        pr.head = pr.mergedHead;
        pr.behind = 0;
        pr.checks = [];
      },
    },
    issues: {
      listComments: 'issues.listComments',
      createComment: async args => { calls.push(['comment', args.issue_number, args.body]); },
    },
    repos: {
      getCommit: async ({ ref }) => {
        const pr = Object.values(prs).find(p => p.head === ref);
        return { data: { commit: { committer: { date: pr.committed || '2026-10-30T00:00:00Z' } } } };
      },
      compareCommits: async ({ head }) => {
        const pr = Object.values(prs).find(p => p.head === head);
        return { data: { behind_by: pr.behind || 0 } };
      },
    },
    checks: {
      listForRef: 'checks.listForRef',
      create: async args => {
        calls.push(['check', args.head_sha, args.conclusion || args.status]);
        const pr = Object.values(prs).find(p => p.head === args.head_sha);
        pr.checks.push({ id: nextId++, name: args.name, status: args.status, conclusion: args.conclusion || null, output: args.output });
      },
    },
    actions: {
      createWorkflowDispatch: async args => { calls.push(['dispatch', args.workflow_id, args.ref]); },
    },
  };
  const paginate = async (what, args) => {
    if (what === 'pulls.list') return Object.keys(prs).map(Number).map(prData);
    if (what === 'pulls.listReviews') return [];
    if (what === 'issues.listComments') return prs[args.issue_number].comments || [];
    if (what === 'checks.listForRef') {
      const pr = Object.values(prs).find(p => p.head === args.ref);
      return pr.checks.map((run, i) => ({ id: run.id || i + 1, ...run }));
    }
    throw new Error(`paginate ${what}`);
  };
  return { github: { rest, paginate }, calls };
}

const core = { info: () => {}, warning: () => {} };
const repo = { owner: 'SamSjosten', repo: 'OSRSPS' };
const H = c => c.repeat(40);
const green = [{ name: 'build', status: 'completed', conclusion: 'success' }];
const codexPass = { name: CHECK, status: 'completed', conclusion: 'success' };
const NOW = Date.parse('2026-10-30T01:00:00Z');
const queue = (github, git) => automerge.queue({ github, context: { repo, payload: {} }, core, git, sleep: async () => {}, now: () => NOW });
const gate = (github, git, number) => automerge.gate({ github, context: { repo, payload: { pull_request: { number } } }, core, git });

test('the gate passes a clean review of the head, fails findings and waits without one', async () => {
  const { github, calls } = fakeGitHub({
    1: { head: H('1'), checks: [], comments: [summary(H('1'), false)] },
    2: { head: H('2'), checks: [], comments: [summary(H('2'), true)] },
    3: { head: H('3'), checks: [], comments: [] },
  });
  const git = fakeGit({});
  await gate(github, git, 1);
  await gate(github, git, 2);
  await gate(github, git, 3);
  assert.deepStrictEqual(calls, [['check', H('1'), 'success'], ['check', H('2'), 'failure'], ['check', H('3'), 'in_progress']]);
});

test('the queue merges main into the oldest ready pull request only, and starts build on it', async () => {
  const { github, calls } = fakeGitHub({
    7: { head: H('7'), mergedHead: H('8'), behind: 2, checks: [...green, codexPass], comments: [summary(H('7'), false)] },
    9: { head: H('9'), behind: 1, checks: [...green, codexPass], comments: [summary(H('9'), false)] },
  });
  await queue(github, fakeGit({ [H('7')]: 'p', [H('8')]: 'p' }));
  assert.deepStrictEqual(calls, [['updateBranch', 7], ['dispatch', 'build.yml', 'branch-7'], ['check', H('8'), 'success']]);
});

test('the queue waits while an up-to-date pull request still has checks running', async () => {
  const { github, calls } = fakeGitHub({
    3: { head: H('3'), behind: 0, checks: [{ name: 'build', status: 'in_progress' }, codexPass] },
    4: { head: H('4'), behind: 1, checks: [...green, codexPass] },
  });
  await queue(github, fakeGit({}));
  assert.deepStrictEqual(calls, []);
});

test('the queue skips failing, conflicting, not-reviewed and not-queued pull requests', async () => {
  const { github, calls } = fakeGitHub({
    1: { head: H('1'), behind: 1, checks: [{ name: 'build', status: 'completed', conclusion: 'failure' }, codexPass] },
    2: { head: H('2'), behind: 1, mergeable: false, checks: [...green, codexPass] },
    3: { head: H('3'), behind: 1, checks: [...green, { name: CHECK, status: 'completed', conclusion: 'failure' }] },
    4: { head: H('4'), behind: 1, autoMerge: false, checks: [...green, codexPass] },
  });
  await queue(github, fakeGit({}));
  assert.deepStrictEqual(calls, []);
});

test('a pull request without the check gets one first', async () => {
  const { github, calls } = fakeGitHub({
    5: { head: H('5'), behind: 0, checks: [...green], comments: [summary(H('5'), false)] },
  });
  await queue(github, fakeGit({}));
  assert.deepStrictEqual(calls, [['check', H('5'), 'success']]);
});

test('when merging main changes the diff, the thread is asked for a new Codex review', async () => {
  const { github, calls } = fakeGitHub({
    6: { head: H('6'), mergedHead: H('a'), behind: 1, checks: [...green, codexPass], comments: [summary(H('6'), false)] },
  });
  await queue(github, fakeGit({ [H('6')]: 'p', [H('a')]: 'q' }));
  assert.deepStrictEqual(calls.slice(0, 3), [['updateBranch', 6], ['dispatch', 'build.yml', 'branch-6'], ['check', H('a'), 'in_progress']]);
  assert.strictEqual(calls[3][0], 'comment');
  assert.ok(calls[3][2].startsWith(NOTICE));
});

test('a waiting check is looked at again on each queue run, and written only when it changes', async () => {
  const waiting = { name: CHECK, status: 'in_progress', conclusion: null, output: { title: 'Waiting for a Codex review' } };
  const { github, calls } = fakeGitHub({
    1: { head: H('1'), behind: 0, checks: [...green, { ...waiting }], comments: [] },
    2: { head: H('2'), behind: 0, checks: [...green, { ...waiting }], comments: [summary(H('2'), false)] },
  });
  await queue(github, fakeGit({}));
  assert.deepStrictEqual(calls, [['check', H('2'), 'success']]);
});

test('an up-to-date head with no build gets one, once it is older than a fresh push', async () => {
  const { github, calls } = fakeGitHub({
    1: { head: H('1'), ref: 'b1', behind: 0, committed: '2026-10-30T00:30:00Z', checks: [codexPass] },
  });
  await queue(github, fakeGit({}));
  assert.deepStrictEqual(calls, [['dispatch', 'build.yml', 'b1']]);

  const fresh = fakeGitHub({ 1: { head: H('1'), behind: 0, committed: '2026-10-30T00:55:00Z', checks: [codexPass] } });
  await queue(fresh.github, fakeGit({}));
  assert.deepStrictEqual(fresh.calls, []);
});

test('the real git tells a trailing space apart, and a merge of main from the same patch', () => {
  const { execFileSync } = require('node:child_process');
  const fs = require('node:fs');
  const os = require('node:os');
  const path = require('node:path');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'automerge-'));
  const sh = (...args) => execFileSync('git', ['-C', dir, ...args], { encoding: 'utf8' }).trim();
  const write = text => fs.writeFileSync(path.join(dir, 'f'), text);
  sh('init', '-q', '-b', 'main');
  sh('config', 'user.email', 'a@b');
  sh('config', 'user.name', 'a');
  write('a\nb\nc\nd\ne\nf\ng\nh\n');
  fs.writeFileSync(path.join(dir, 'other'), 'x\n');
  sh('add', '.');
  sh('commit', '-qm', 'base');
  sh('checkout', '-qb', 'pr');
  write('a\nb\nc\nd\ne\nf\ng\nh\nhello world\n');
  sh('commit', '-qam', 'pr');
  const reviewed = sh('rev-parse', 'HEAD');
  sh('checkout', '-q', 'main');
  fs.writeFileSync(path.join(dir, 'other'), 'x\ny\n');
  sh('commit', '-qam', 'main');
  sh('update-ref', 'refs/remotes/origin/main', 'main');
  sh('checkout', '-q', 'pr');
  sh('merge', '-q', 'main', '-m', 'merge main');
  const merged = sh('rev-parse', 'HEAD');
  write('a\nb\nc\nd\ne\nf\ng\nh\nhello world \n');
  sh('commit', '-qam', 'trailing space');
  const spaced = sh('rev-parse', 'HEAD');
  const git = (args, input) => execFileSync('git', ['-C', dir, ...args], { input, encoding: 'utf8' });
  const noFetch = (args, input) => (args[0] === 'fetch' ? '' : git(args, input));
  assert.ok(covers(noFetch, 1, reviewed, merged), 'main merged in, same patch');
  assert.ok(!covers(noFetch, 1, merged, spaced), 'a trailing space added');
  fs.rmSync(dir, { recursive: true, force: true });
});
