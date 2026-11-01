// Auto-merge (see CONTRIBUTING.md), run by .github/workflows/automerge.yml through actions/github-script. Two parts:
//
// The `codex-review` check, which branch protection requires on main: it passes when Codex's latest review found
// nothing and covers the pull request's changes as they are now. Each Codex review leaves a receipt naming the commit
// it reviewed: a comment saying "Didn't find any major issues", or a review with suggestions. The latest receipt is
// the verdict. Codex's summary comment carries a marker, `<!-- codex-review-state:v1 {...} -->`, used for whether a
// review is running, and as the verdict only when there is no receipt: its `has_any_findings` isn't about the latest
// review alone (#212 has it true beside a clean receipt for the same commit).
//
// A review covers the head when it is the head, or when the reviewed commit merged with main gives the same tree as
// the head merged with main (`git merge-tree`): merging main in doesn't need Codex again, any other change does,
// a conflict resolution included, and a reviewed commit that conflicts with main covers nothing.
//
// The queue: branch protection wants a pull request up to date with main before it merges, so two green pull
// requests can't break main together, and GitHub's merge queue isn't offered on a personal account's private
// repository. So after each event this picks the oldest pull request with auto-merge on that is behind main, has no
// failing check and a passing `codex-review`, merges main into it (update-branch) and starts `build` on it. One at a
// time: while a pull request that is up to date still has checks running, it waits for that one (a batch of the others
// may still start, below). GitHub runs no
// workflow for a push made with the workflow's own token, hence the dispatch, and the `codex-review` check for the new
// head is written here.
//
// Batches: when two or more such pull requests are ready (behind main, their own checks done and green, `codex-review`
// passing), the queue merges them one after another onto main as commits on the `automerge/batch` branch, skipping any
// that conflicts with those before it, and starts `build` there: one CI run for all of them. The last merge's commit
// names the batch in its trailers (main's commit and each member's head). Green, that commit becomes the head of the
// last member's branch (a fast-forward: its head is the merge's second parent), with a `codex-review` that covers each
// member; it is up to date with main and its checks have run, so auto-merge merges it, and with it every other member,
// whose head is now in main, which GitHub marks merged. Red, the first half of the batch goes again, down to one pull
// request, which goes the one-at-a-time way above, so its own checks show the failure to the thread that owns it.

const { execFileSync } = require('node:child_process');

const CHECK = 'codex-review';
const CODEX = 'chatgpt-codex-connector[bot]';
const STATE = /<!-- codex-review-state:v1 (\{.*?\}) -->/s;
const RECEIPT = '<!-- codex-review-receipt:v1 -->';
const REVIEWED = /\*\*Reviewed commit:\*\*\s*`([0-9a-f]{7,40})`/;
const CLEAN = "Didn't find any major issues";
const BAD = ['failure', 'cancelled', 'timed_out', 'action_required', 'stale', 'startup_failure'];
const NOTICE = '<!-- automerge:review-again -->';
const APPROVE = '<!-- automerge:approve-runs';
// This workflow's own jobs: GitHub files them on the pull request's head too, and the queue mustn't wait on itself.
const OWN = ['automerge-gate', 'automerge-queue'];
const BUILD = 'build'; // build.yml's main job: no check of that name on a head means no CI ran on it
const BATCH = 'automerge/batch';
const BATCH_MAX = 6;
const BASE_TRAILER = 'Automerge-Base:';
const MEMBER_TRAILER = 'Automerge-Member:';
const BOT = ['-c', 'user.name=github-actions[bot]', '-c', 'user.email=41898282+github-actions[bot]@users.noreply.github.com'];

const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

// GitHub's API answers 5xx now and then (500s listing a commit's check runs, 14:21-14:25Z on 2026-10-30), and an
// error left unhandled kills the run, the queue's and the gate's alike, with a codex-review possibly half written.
// So each request is tried again on a 5xx, or no answer at all, after 2, 4 and 8 seconds.
const RETRIES = [2000, 4000, 8000];
const RETRYING = Symbol('automerge retries');

function retrying(github, sleep) {
  if (!github.hook || github[RETRYING]) return;
  github[RETRYING] = true;
  github.hook.wrap('request', async (request, options) => {
    for (let attempt = 0; ; attempt++) {
      try {
        return await request(options);
      } catch (error) {
        const transient = error.status === undefined || error.status >= 500;
        if (!transient || attempt >= RETRIES.length) throw error;
        await sleep(RETRIES[attempt]);
      }
    }
  });
}

// Codex's latest verdict from a pull request's comments and reviews: { sha, clean }, { running: true }, { unsaid: true }
// or null.
function verdict(comments, reviews) {
  const codex = item => item.user && item.user.login === CODEX && item.body;
  let state = null;
  for (const comment of comments.filter(codex)) {
    const match = STATE.exec(comment.body);
    if (!match) continue;
    try { state = JSON.parse(match[1]).code_review; } catch { continue; }
    if (state && state.status) break;
    state = null;
  }
  if (state && state.status !== 'completed') return { running: true };
  const receipts = [
    ...comments.filter(codex).map(c => ({ at: c.created_at, body: c.body })),
    ...reviews.filter(codex).map(r => ({ at: r.submitted_at, body: r.body })),
  ].filter(r => r.body.includes(RECEIPT) && REVIEWED.test(r.body));
  if (receipts.length) {
    receipts.sort((a, b) => (a.at < b.at ? -1 : a.at > b.at ? 1 : 0));
    const last = receipts[receipts.length - 1];
    return { sha: REVIEWED.exec(last.body)[1], clean: last.body.includes(CLEAN) };
  }
  if (state && state.commit_sha) {
    // A finished review whose flags are both null said nothing (on #298 the receipt never came, during GitHub's 500s):
    // no verdict yet, not findings.
    if (state.has_any_findings == null && state.has_unresolved_findings == null) return { unsaid: true };
    return { sha: state.commit_sha, clean: state.has_any_findings === false && state.has_unresolved_findings === false };
  }
  return null;
}

// Git in the workflow's checkout of main, for the tree comparison.
function shellGit(args, input) {
  return execFileSync('git', args, { input, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
}

// The tree a commit makes merged with main. merge-tree exits 1 on a conflict, which throws.
function treeWithMain(git, sha) {
  return git(['merge-tree', '--write-tree', 'origin/main', sha]).split('\n')[0].trim();
}

// Whether the review of `reviewed` still covers pull request `number` at `head`.
function covers(git, number, reviewed, head) {
  if (head.startsWith(reviewed) || reviewed.startsWith(head)) return true;
  try {
    git(['fetch', '--no-tags', '--quiet', 'origin', 'main', `+refs/pull/${number}/head:refs/remotes/pr/${number}`]);
    let full;
    try { full = git(['rev-parse', '--verify', `${reviewed}^{commit}`]).trim(); } catch {
      git(['fetch', '--no-tags', '--quiet', 'origin', reviewed]);
      full = git(['rev-parse', '--verify', `${reviewed}^{commit}`]).trim();
    }
    return treeWithMain(git, full) === treeWithMain(git, head);
  } catch {
    return false; // a commit that can't be fetched any more (force-pushed away) needs a new review
  }
}

// The `codex-review` result for a pull request as it is now: { status, conclusion?, title, summary, stale? }.
// A batch's head covers each member: the last member's branch holds it, and its check is Codex on each member's head.
// Anyone can write the trailers, so a head counts as a batch only when it is what the queue would build from them.
async function evaluate(github, git, repo, pr) {
  const batch = parseBatch((await github.rest.git.getCommit({ ...repo, commit_sha: pr.head.sha })).data.message);
  if (batch && batch.members[batch.members.length - 1].number === pr.number && isBatch(git, pr.number, batch, pr.head.sha)) {
    for (const member of batch.members) {
      const result = await evaluateAt(github, git, repo, member.number, member.sha);
      if (result.conclusion !== 'success') return { ...result, title: `#${member.number} in the batch: ${result.title}`, member, own: result };
    }
    const numbers = batch.members.map(member => `#${member.number}`).join(', ');
    return { status: 'completed', conclusion: 'success', title: `Codex found nothing in ${numbers}`, summary: 'Clean Codex reviews of each pull request in the batch.' };
  }
  return evaluateAt(github, git, repo, pr.number, pr.head.sha);
}

async function evaluateAt(github, git, repo, number, head) {
  const issue = { ...repo, issue_number: number };
  const comments = await github.paginate(github.rest.issues.listComments, { ...issue, per_page: 100 });
  const reviews = await github.paginate(github.rest.pulls.listReviews, { ...repo, pull_number: number, per_page: 100 });
  const found = verdict(comments, reviews);
  if (!found) {
    return { status: 'in_progress', title: 'Waiting for a Codex review', summary: 'Codex hasn\'t reviewed this pull request yet.' };
  }
  if (found.running) return { status: 'in_progress', title: 'Codex is reviewing', summary: 'A Codex review is running.' };
  if (found.unsaid) {
    return {
      status: 'in_progress',
      title: 'Waiting for a Codex receipt',
      summary: 'Codex\'s last review finished without saying whether it found anything: comment `@codex review`.',
    };
  }
  const short = found.sha.slice(0, 7);
  if (!covers(git, number, found.sha, head)) {
    return {
      status: 'in_progress',
      stale: true,
      title: `Codex reviewed ${short}, not the changes at ${head.slice(0, 7)}`,
      summary: 'The pull request\'s diff changed since Codex\'s last review: comment `@codex review`.',
    };
  }
  if (found.clean) {
    return { status: 'completed', conclusion: 'success', title: `Codex found nothing at ${short}`, summary: 'Clean Codex review.' };
  }
  return {
    status: 'completed',
    conclusion: 'failure',
    title: `Codex has findings at ${short}`,
    summary: 'Fix or answer the findings, then comment `@codex review`.',
  };
}

async function writeCheck(github, repo, sha, result) {
  await github.rest.checks.create({
    ...repo,
    name: CHECK,
    head_sha: sha,
    status: result.status,
    ...(result.conclusion ? { conclusion: result.conclusion } : {}),
    output: { title: result.title, summary: result.summary },
  });
}

// The latest run of each check on a commit, by name.
async function latestChecks(github, repo, sha) {
  const runs = await github.paginate(github.rest.checks.listForRef, { ...repo, ref: sha, per_page: 100 });
  const byName = new Map();
  for (const run of runs) {
    const seen = byName.get(run.name);
    if (!seen || run.id > seen.id) byName.set(run.name, run);
  }
  return [...byName.values()];
}

// `current` is the check already on the head, if any: an unchanged result isn't written again, so a queue run doesn't
// wake the thread with the same failing check each time. Gate runs aren't serialized (see automerge.yml), so a run
// whose evaluation started before the newest check on the head was written leaves that check alone; the queue looks
// again at any check that isn't passing, so a run that lost such a race to a stale one is put right there.
async function gate(github, git, repo, core, number, current, now = Date.now) {
  const started = now();
  const { data: pr } = await github.rest.pulls.get({ ...repo, pull_number: number });
  if (pr.state !== 'open') return;
  const result = await evaluate(github, git, repo, pr);
  core.info(`#${number}: ${CHECK} ${result.conclusion || result.status}: ${result.title}`);
  const latest = (await latestChecks(github, repo, pr.head.sha)).find(run => run.name === CHECK);
  if (latest && latest.started_at && Date.parse(latest.started_at) > started) {
    core.info(`#${number}: a newer ${CHECK} was written meanwhile; leaving it`);
    return;
  }
  const seen = current || latest;
  const same = seen && seen.status === result.status && (seen.conclusion || null) === (result.conclusion || null)
    && seen.output && seen.output.title === result.title;
  if (!same) await writeCheck(github, repo, pr.head.sha, result);
}

// The pull_request runs GitHub holds for "approval from a maintainer" on a head: a merge of main the queue pushed with
// the workflow token gets them, and until they run, the ruleset waits on their checks and the pull request stays
// blocked (a dispatched build doesn't stand in for them). Approves each; returns how many waited and how many ran.
async function approveWaiting(github, repo, core, sha) {
  const { data } = await github.rest.actions.listWorkflowRunsForRepo({ ...repo, head_sha: sha, status: 'action_required', per_page: 100 });
  const runs = data.workflow_runs || [];
  let approved = 0;
  for (const run of runs) {
    try {
      await github.rest.actions.approveWorkflowRun({ ...repo, run_id: run.id });
      approved++;
    } catch (error) {
      core.warning(`run ${run.id} on ${sha.slice(0, 7)}: approval refused (${error.status}): ${error.message}`);
    }
  }
  if (runs.length) core.info(`${sha.slice(0, 7)}: approved ${approved} of ${runs.length} waiting runs`);
  return { waiting: runs.length, approved };
}

// Once per head: the runs on it wait for a maintainer and the queue couldn't approve them.
async function askForApproval(github, repo, number, sha) {
  const marker = `${APPROVE} ${sha} -->`;
  const comments = await github.paginate(github.rest.issues.listComments, { ...repo, issue_number: number, per_page: 100 });
  if (comments.some(comment => (comment.body || '').includes(marker))) return;
  await github.rest.issues.createComment({
    ...repo,
    issue_number: number,
    body: `${marker}
**Runs waiting:** the CI runs on ${sha.slice(0, 7)} are waiting for a maintainer's approval, and auto-merge couldn't \
approve them. @SamSjosten: "Approve workflows to run" on this pull request lets it merge.`,
  });
}

// The batch a commit's message names: { base, members: [{ number, sha }] }, or null.
function parseBatch(message) {
  const lines = (message || '').split('\n').map(line => line.trim());
  const base = lines.find(line => line.startsWith(BASE_TRAILER));
  const members = lines.filter(line => line.startsWith(MEMBER_TRAILER)).map(line => {
    const [number, sha] = line.slice(MEMBER_TRAILER.length).trim().split(/\s+/);
    return { number: Number(number.replace('#', '')), sha };
  });
  if (!base || members.length < 2 || members.some(member => !member.number || !/^[0-9a-f]{40}$/.test(member.sha))) return null;
  return { base: base.slice(BASE_TRAILER.length).trim(), members };
}

function commitTree(git, tree, parents, message) {
  return git([...BOT, 'commit-tree', tree, ...parents.flatMap(parent => ['-p', parent]), '-F', '-'], message).trim();
}

// Merges each member's head in turn onto main, as commits made without a working tree, leaving out any that conflicts
// with main and the members before it. The last commit carries the batch's trailers. Returns { sha, base, members }.
function buildBatch(git, members) {
  git(['fetch', '--no-tags', '--quiet', 'origin', 'main', ...members.map(m => `+refs/pull/${m.number}/head:refs/remotes/pr/${m.number}`)]);
  const base = git(['rev-parse', 'origin/main']).trim();
  let head = base;
  let last = null;
  const merged = [];
  for (const member of members) {
    let tree;
    try {
      tree = git(['merge-tree', '--write-tree', head, member.sha]).split('\n')[0].trim();
    } catch {
      continue; // conflicts with the batch so far: it goes in a later one
    }
    last = { tree, onto: head };
    head = commitTree(git, tree, [head, member.sha], `Merge #${member.number} into the auto-merge batch`);
    merged.push(member);
  }
  if (merged.length < 2) return { sha: head, base, members: merged };
  const numbers = merged.map(member => `#${member.number}`).join(', ');
  const message = `Auto-merge batch: ${numbers}\n\n${BASE_TRAILER} ${base}\n${merged.map(m => `${MEMBER_TRAILER} #${m.number} ${m.sha}`).join('\n')}\n`;
  const sha = commitTree(git, last.tree, [last.onto, merged[merged.length - 1].sha], message);
  return { sha, base, members: merged };
}

// Whether `head` is the batch its trailers name, as buildBatch makes it: on main as it is now, with the last member as
// its second parent and the tree of every member merged onto main in turn. So its content is main and the members'
// changes, nothing more, whoever made the commit.
function isBatch(git, number, batch, head) {
  try {
    try { git(['fetch', '--no-tags', '--quiet', 'origin', `+refs/heads/${BATCH}:refs/remotes/origin/${BATCH}`]); } catch { /* gone: the pull request's head may still be it */ }
    git(['fetch', '--no-tags', '--quiet', 'origin', `+refs/pull/${number}/head:refs/remotes/pr/${number}`]);
    const built = buildBatch(git, batch.members);
    const last = batch.members[batch.members.length - 1].sha;
    return built.base === batch.base && built.members.length === batch.members.length
      && git(['rev-parse', `${head}^2`]).trim() === last
      && git(['rev-parse', `${head}^{tree}`]).trim() === git(['rev-parse', `${built.sha}^{tree}`]).trim();
  } catch {
    return false;
  }
}

// The batch branch as it is: { sha, at, base, members } or null.
async function readBatch(github, repo) {
  let data;
  try {
    ({ data } = await github.rest.repos.getBranch({ ...repo, branch: BATCH }));
  } catch (error) {
    if (error.status === 404) return null;
    throw error;
  }
  const batch = parseBatch(data.commit.commit.message);
  return batch && { ...batch, sha: data.commit.sha, at: Date.parse(data.commit.commit.committer.date) };
}

// Builds a batch of two or more and starts `build` on it; false when fewer than two merge together.
async function startBatch(github, git, repo, core, members) {
  const built = buildBatch(git, members.map(({ number, sha }) => ({ number, sha })));
  if (built.members.length < 2) return false;
  git(['push', '--force', '--quiet', 'origin', `${built.sha}:refs/heads/${BATCH}`]);
  await github.rest.actions.createWorkflowDispatch({ ...repo, workflow_id: 'build.yml', ref: BATCH });
  core.info(`batch ${built.sha.slice(0, 7)}: ${built.members.map(m => `#${m.number}`).join(', ')} on main ${built.base.slice(0, 7)}; build started`);
  return true;
}

// Merges main into one pull request and starts its checks: the one-at-a-time way. False when it couldn't.
async function updateOne(github, git, repo, core, sleep, pr) {
  const { data: fresh } = await github.rest.pulls.get({ ...repo, pull_number: pr.number });
  if (fresh.mergeable === false) return false; // conflicts: mergeable.yml tells the thread
  core.info(`#${pr.number}: merging main into ${pr.head.ref}`);
  try {
    await github.rest.pulls.updateBranch({ ...repo, pull_number: pr.number, expected_head_sha: pr.head.sha });
  } catch (error) {
    core.info(`#${pr.number}: update-branch refused (${error.status}): ${error.message}`);
    return false;
  }
  let head = pr.head.sha;
  for (let i = 0; i < 24 && head === pr.head.sha; i++) {
    await sleep(5000);
    ({ data: { head: { sha: head } } } = await github.rest.pulls.get({ ...repo, pull_number: pr.number }));
  }
  if (head === pr.head.sha) {
    core.warning(`#${pr.number}: the merge of main hasn't shown up yet; the next event picks it up`);
    return true;
  }
  // GitHub may file the merge's pull_request runs as waiting for approval; approved, they are the pull request's
  // checks. If none show up, the push started nothing and a dispatched build stands in.
  let held = { waiting: 0, approved: 0 };
  for (let i = 0; i < 6 && !held.waiting; i++) {
    await sleep(5000);
    held = await approveWaiting(github, repo, core, head);
  }
  if (!held.waiting) {
    await github.rest.actions.createWorkflowDispatch({ ...repo, workflow_id: 'build.yml', ref: pr.head.ref });
  } else if (held.approved < held.waiting) {
    await askForApproval(github, repo, pr.number, head);
  }
  const { data: updated } = await github.rest.pulls.get({ ...repo, pull_number: pr.number });
  const result = await evaluate(github, git, repo, updated);
  await writeCheck(github, repo, updated.head.sha, result);
  if (result.stale) {
    await github.rest.issues.createComment({
      ...repo,
      issue_number: pr.number,
      body: `${NOTICE}
**Main merged in:** auto-merge merged \`main\` into this branch at ${head.slice(0, 7)}, and that changed the pull \
request's diff, so Codex's last review no longer covers it. To the thread that opened this pull request: pull the \
branch, check the merge, and comment \`@codex review\`.`,
    });
  }
  return true;
}

// What to do about the batch on its branch, if it is still about main and its members: true when the queue is done
// for this run (the batch is building, was promoted or went again), false to go on as if there were none.
async function advanceBatch(github, git, repo, core, sleep, now, batch, mainSha, scanned, waiting) {
  if (!batch || batch.base !== mainSha) return false;
  const carrier = batch.members[batch.members.length - 1];
  const carrierState = scanned.get(carrier.number);
  if (carrierState && carrierState.pr.head.sha === batch.sha) {
    if (carrierState.failing) return false;
    core.info(`batch ${batch.sha.slice(0, 7)} is #${carrier.number}'s head: waiting for auto-merge`);
    return true;
  }
  if (!batch.members.every(member => scanned.has(member.number) && scanned.get(member.number).pr.head.sha === member.sha)) {
    core.info(`batch ${batch.sha.slice(0, 7)}: a member moved or left the queue; building again`);
    return false;
  }
  const checks = (await latestChecks(github, repo, batch.sha)).filter(run => !OWN.includes(run.name) && run.name !== CHECK);
  const failing = checks.some(run => run.status === 'completed' && BAD.includes(run.conclusion));
  const running = checks.some(run => run.status !== 'completed');
  if (!failing && !checks.some(run => run.name === BUILD)) {
    if (now() - batch.at > 10 * 60 * 1000) {
      core.info(`batch ${batch.sha.slice(0, 7)}: no build after ten minutes; starting one`);
      await github.rest.actions.createWorkflowDispatch({ ...repo, workflow_id: 'build.yml', ref: BATCH });
    }
    return true;
  }
  if (!failing && running) {
    core.info(`batch ${batch.sha.slice(0, 7)}: checks running`);
    return true;
  }
  if (!failing) {
    const result = await evaluate(github, git, repo, { number: carrier.number, head: { sha: batch.sha } });
    if (result.conclusion !== 'success') {
      // The member's own codex-review passed against an older main, and no longer does: it is written again on the
      // member's head and the member leaves the queue until Codex covers it, so the batch is built without it instead
      // of the same batch again and again (#296, 2026-10-30).
      if (result.member) {
        await writeCheck(github, repo, result.member.sha, result.own);
        const state = scanned.get(result.member.number);
        if (state) Object.assign(state, { ready: false, batchable: false });
      }
      core.info(`batch ${batch.sha.slice(0, 7)}: ${result.title}; building again without it`);
      return false;
    }
    core.info(`batch ${batch.sha.slice(0, 7)} is green: it becomes #${carrier.number}'s head`);
    await github.rest.git.updateRef({ ...repo, ref: `heads/${carrierState.pr.head.ref}`, sha: batch.sha, force: false });
    await writeCheck(github, repo, batch.sha, result);
    return true;
  }
  const half = batch.members.slice(0, Math.ceil(batch.members.length / 2));
  core.info(`batch ${batch.sha.slice(0, 7)} failed: ${half.map(m => `#${m.number}`).join(', ')} go again`);
  if (half.length >= 2 && await startBatch(github, git, repo, core, half)) return true;
  const first = scanned.get(half[0].number);
  if (waiting) return true; // the one-at-a-time way waits for it; the failed batch stays until then
  return first.ready ? updateOne(github, git, repo, core, sleep, first.pr) : false;
}

async function advance(github, git, repo, core, sleep, now) {
  const open = await github.paginate(github.rest.pulls.list, { ...repo, state: 'open', base: 'main', per_page: 100 });
  const queued = open.filter(pr => pr.auto_merge && !pr.draft).sort((a, b) => a.number - b.number);

  const scanned = new Map();
  let waiting = null; // an up-to-date pull request whose checks are still running: no other gets main merged in meanwhile
  for (const pr of queued) {
    const { data: compare } = await github.rest.repos.compareCommits({ ...repo, base: 'main', head: pr.head.sha });
    const behind = compare.behind_by > 0;
    if (compare.ahead_by === 0) {
      // Every commit already in main: a batch member GitHub hasn't marked merged yet.
      core.info(`#${pr.number}: its commits are all in main; leaving it`);
      continue;
    }
    // Held runs first: merging main in again would only push another head whose runs are held the same way.
    const held = await approveWaiting(github, repo, core, pr.head.sha);
    if (held.waiting) {
      if (held.approved === held.waiting) {
        core.info(`#${pr.number}: approved its waiting runs; waiting for them`);
        waiting = waiting || pr;
        continue;
      }
      await askForApproval(github, repo, pr.number, pr.head.sha);
      continue;
    }
    let checks = await latestChecks(github, repo, pr.head.sha);
    const current = checks.find(run => run.name === CHECK);
    // Missing (opened before this workflow, or an event was missed), waiting or failing: Codex may have answered
    // since, or a slower gate run may have written a stale result. Written again only when it changes.
    if (!current || current.conclusion !== 'success') {
      await gate(github, git, repo, core, pr.number, current, now);
      checks = await latestChecks(github, repo, pr.head.sha);
    }
    if (!behind && !checks.some(run => run.name === BUILD)) {
      // An up-to-date head that no build ever ran on: a merge of main whose dispatch was lost (the update showed up
      // after the wait below). Ten minutes, so a thread's fresh push gets its own pull_request build first.
      const { data: commit } = await github.rest.repos.getCommit({ ...repo, ref: pr.head.sha });
      if (now() - Date.parse(commit.commit.committer.date) > 10 * 60 * 1000) {
        core.info(`#${pr.number}: no build on ${pr.head.sha.slice(0, 7)}; starting one`);
        await github.rest.actions.createWorkflowDispatch({ ...repo, workflow_id: 'build.yml', ref: pr.head.ref });
        waiting = waiting || pr; // and the scan goes on, so a green batch isn't held back by it (#319, 2026-10-30)
        continue;
      }
    }
    const others = checks.filter(run => !OWN.includes(run.name));
    const failing = others.some(run => run.status === 'completed' && BAD.includes(run.conclusion));
    const running = others.some(run => run.name !== CHECK && run.status !== 'completed');
    const codex = checks.find(run => run.name === CHECK);
    const codexOk = codex && codex.conclusion === 'success';
    core.info(`#${pr.number}: ${behind ? 'behind main' : 'up to date'}, ${failing ? 'failing' : running ? 'checks running' : 'checks done'}, ${CHECK} ${codexOk ? 'passes' : 'not passing'}`);
    // Waiting for it holds back only the one-at-a-time way: the scan goes on, so a batch of the others can still start.
    // Stopping here instead starved every older ready pull request whenever any thread pushed (#292, #308, 2026-10-30).
    if (!behind && running && !failing) {
      core.info(`#${pr.number} is up to date with checks running`);
      waiting = waiting || pr;
      continue;
    }
    const ready = behind && !failing && codexOk;
    scanned.set(pr.number, { pr, failing, ready, batchable: ready && !running && checks.some(run => run.name === BUILD) });
  }

  const { data: main } = await github.rest.repos.getBranch({ ...repo, branch: 'main' });
  if (await advanceBatch(github, git, repo, core, sleep, now, await readBatch(github, repo), main.commit.sha, scanned, waiting)) return;

  const batchable = [...scanned.values()].filter(state => state.batchable).slice(0, BATCH_MAX);
  if (batchable.length >= 2 && await startBatch(github, git, repo, core, batchable.map(state => ({ number: state.pr.number, sha: state.pr.head.sha })))) return;

  if (waiting) {
    core.info(`waiting for #${waiting.number}`);
    return;
  }
  for (const state of scanned.values()) {
    if (state.ready && await updateOne(github, git, repo, core, sleep, state.pr)) return;
  }
  core.info('Nothing to merge main into');
}

// The `gate` job: the check for the pull request the event is about.
module.exports.gate = async function ({ github, context, core, git = shellGit, sleep = wait, now = Date.now }) {
  retrying(github, sleep);
  const payload = context.payload;
  const number = payload.pull_request ? payload.pull_request.number : payload.issue && payload.issue.number;
  if (number) await gate(github, git, context.repo, core, number, undefined, now);
};

// The `queue` job, after every event.
module.exports.queue = async function ({ github, context, core, git = shellGit, sleep = wait, now = Date.now }) {
  retrying(github, sleep);
  await advance(github, git, context.repo, core, sleep, now);
};

module.exports.CHECK = CHECK;
module.exports.CODEX = CODEX;
module.exports.NOTICE = NOTICE;
module.exports.APPROVE = APPROVE;
module.exports.OWN = OWN;
module.exports.verdict = verdict;
module.exports.covers = covers;
module.exports.retrying = retrying;
module.exports.BATCH = BATCH;
module.exports.parseBatch = parseBatch;
module.exports.buildBatch = buildBatch;
module.exports.isBatch = isBatch;
