// Auto-merge (see CONTRIBUTING.md), run by .github/workflows/automerge.yml through actions/github-script. Two parts:
//
// The `codex-review` check, which branch protection requires on main: it passes when Codex's latest review found
// nothing and covers the pull request's changes as they are now. Codex keeps a summary comment on each pull request
// with its state in a marker, `<!-- codex-review-state:v1 {...} -->`: the commit it last reviewed and whether that
// review had findings. Older pull requests only have its receipts ("Didn't find any major issues" or suggestions,
// each naming the reviewed commit), so the latest receipt is the fallback. Inferred: the marker's `has_any_findings`
// is about the latest review only (on #207 it is false after earlier reviews that had findings).
//
// A review covers the head when it is the head, or when the reviewed commit merged with main gives the same tree as
// the head merged with main (`git merge-tree`): merging main in doesn't need Codex again, any other change does,
// a conflict resolution included, and a reviewed commit that conflicts with main covers nothing.
//
// The queue: branch protection wants a pull request up to date with main before it merges, so two green pull
// requests can't break main together, and GitHub's merge queue isn't offered on a personal account's private
// repository. So after each event this picks the oldest pull request with auto-merge on that is behind main, has no
// failing check and a passing `codex-review`, merges main into it (update-branch) and starts `build` on it. One at a
// time: while a pull request that is up to date still has checks running, it waits for that one. GitHub runs no
// workflow for a push made with the workflow's own token, hence the dispatch, and the `codex-review` check for the new
// head is written here.

const { execFileSync } = require('node:child_process');

const CHECK = 'codex-review';
const CODEX = 'chatgpt-codex-connector[bot]';
const STATE = /<!-- codex-review-state:v1 (\{.*?\}) -->/s;
const RECEIPT = '<!-- codex-review-receipt:v1 -->';
const REVIEWED = /\*\*Reviewed commit:\*\*\s*`([0-9a-f]{7,40})`/;
const CLEAN = "Didn't find any major issues";
const BAD = ['failure', 'cancelled', 'timed_out', 'action_required', 'stale', 'startup_failure'];
const NOTICE = '<!-- automerge:review-again -->';
const BUILD = 'build'; // build.yml's main job: no check of that name on a head means no CI ran on it

const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

// Codex's latest verdict from a pull request's comments and reviews: { sha, clean }, { running: true } or null.
function verdict(comments, reviews) {
  const codex = item => item.user && item.user.login === CODEX && item.body;
  for (const comment of comments.filter(codex)) {
    const match = STATE.exec(comment.body);
    if (!match) continue;
    let review;
    try { review = JSON.parse(match[1]).code_review; } catch { continue; }
    if (!review || !review.status) continue;
    if (review.status !== 'completed') return { running: true };
    if (review.commit_sha) return { sha: review.commit_sha, clean: review.has_any_findings === false };
  }
  const receipts = [
    ...comments.filter(codex).map(c => ({ at: c.created_at, body: c.body })),
    ...reviews.filter(codex).map(r => ({ at: r.submitted_at, body: r.body })),
  ].filter(r => r.body.includes(RECEIPT) && REVIEWED.test(r.body));
  if (!receipts.length) return null;
  receipts.sort((a, b) => (a.at < b.at ? -1 : a.at > b.at ? 1 : 0));
  const last = receipts[receipts.length - 1];
  return { sha: REVIEWED.exec(last.body)[1], clean: last.body.includes(CLEAN) };
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
async function evaluate(github, git, repo, pr) {
  const issue = { ...repo, issue_number: pr.number };
  const comments = await github.paginate(github.rest.issues.listComments, { ...issue, per_page: 100 });
  const reviews = await github.paginate(github.rest.pulls.listReviews, { ...repo, pull_number: pr.number, per_page: 100 });
  const found = verdict(comments, reviews);
  const head = pr.head.sha;
  if (!found) {
    return { status: 'in_progress', title: 'Waiting for a Codex review', summary: 'Codex hasn\'t reviewed this pull request yet.' };
  }
  if (found.running) return { status: 'in_progress', title: 'Codex is reviewing', summary: 'A Codex review is running.' };
  const short = found.sha.slice(0, 7);
  if (!covers(git, pr.number, found.sha, head)) {
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
// wake the thread with the same failing check each time.
async function gate(github, git, repo, core, number, current) {
  const { data: pr } = await github.rest.pulls.get({ ...repo, pull_number: number });
  if (pr.state !== 'open') return;
  const result = await evaluate(github, git, repo, pr);
  core.info(`#${number}: ${CHECK} ${result.conclusion || result.status}: ${result.title}`);
  const same = current && current.status === result.status && (current.conclusion || null) === (result.conclusion || null)
    && current.output && current.output.title === result.title;
  if (!same) await writeCheck(github, repo, pr.head.sha, result);
}

async function advance(github, git, repo, core, sleep, now) {
  const open = await github.paginate(github.rest.pulls.list, { ...repo, state: 'open', base: 'main', per_page: 100 });
  const queued = open.filter(pr => pr.auto_merge && !pr.draft).sort((a, b) => a.number - b.number);

  const ready = [];
  for (const pr of queued) {
    const { data: compare } = await github.rest.repos.compareCommits({ ...repo, base: 'main', head: pr.head.sha });
    const behind = compare.behind_by > 0;
    let checks = await latestChecks(github, repo, pr.head.sha);
    const current = checks.find(run => run.name === CHECK);
    // Missing (opened before this workflow, or an event was missed) or still waiting: Codex may have answered since.
    if (!current || current.status !== 'completed') {
      await gate(github, git, repo, core, pr.number, current);
      checks = await latestChecks(github, repo, pr.head.sha);
    }
    if (!behind && !checks.some(run => run.name === BUILD)) {
      // An up-to-date head that no build ever ran on: a merge of main whose dispatch was lost (the update showed up
      // after the wait below). Ten minutes, so a thread's fresh push gets its own pull_request build first.
      const { data: commit } = await github.rest.repos.getCommit({ ...repo, ref: pr.head.sha });
      if (now() - Date.parse(commit.commit.committer.date) > 10 * 60 * 1000) {
        core.info(`#${pr.number}: no build on ${pr.head.sha.slice(0, 7)}; starting one`);
        await github.rest.actions.createWorkflowDispatch({ ...repo, workflow_id: 'build.yml', ref: pr.head.ref });
        return;
      }
    }
    const failing = checks.some(run => run.status === 'completed' && BAD.includes(run.conclusion));
    const running = checks.some(run => run.name !== CHECK && run.status !== 'completed');
    const codex = checks.find(run => run.name === CHECK);
    const codexOk = codex && codex.conclusion === 'success';
    core.info(`#${pr.number}: ${behind ? 'behind main' : 'up to date'}, ${failing ? 'failing' : running ? 'checks running' : 'checks done'}, ${CHECK} ${codexOk ? 'passes' : 'not passing'}`);
    if (!behind && running && !failing) {
      core.info(`#${pr.number} is up to date with checks running: waiting for it`);
      return;
    }
    if (behind && !failing && codexOk) ready.push(pr);
  }

  for (const pr of ready) {
    const { data: fresh } = await github.rest.pulls.get({ ...repo, pull_number: pr.number });
    if (fresh.mergeable === false) continue; // conflicts: mergeable.yml tells the thread
    core.info(`#${pr.number}: merging main into ${pr.head.ref}`);
    try {
      await github.rest.pulls.updateBranch({ ...repo, pull_number: pr.number, expected_head_sha: pr.head.sha });
    } catch (error) {
      core.info(`#${pr.number}: update-branch refused (${error.status}): ${error.message}`);
      continue;
    }
    let head = pr.head.sha;
    for (let i = 0; i < 24 && head === pr.head.sha; i++) {
      await sleep(5000);
      ({ data: { head: { sha: head } } } = await github.rest.pulls.get({ ...repo, pull_number: pr.number }));
    }
    if (head === pr.head.sha) {
      core.warning(`#${pr.number}: the merge of main hasn't shown up yet; the next event picks it up`);
      return;
    }
    await github.rest.actions.createWorkflowDispatch({ ...repo, workflow_id: 'build.yml', ref: pr.head.ref });
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
    return;
  }
  core.info('Nothing to merge main into');
}

// The `gate` job: the check for the pull request the event is about.
module.exports.gate = async function ({ github, context, core, git = shellGit }) {
  const payload = context.payload;
  const number = payload.pull_request ? payload.pull_request.number : payload.issue && payload.issue.number;
  if (number) await gate(github, git, context.repo, core, number);
};

// The `queue` job, after every event.
module.exports.queue = async function ({ github, context, core, git = shellGit, sleep = wait, now = Date.now }) {
  await advance(github, git, context.repo, core, sleep, now);
};

module.exports.CHECK = CHECK;
module.exports.CODEX = CODEX;
module.exports.NOTICE = NOTICE;
module.exports.verdict = verdict;
module.exports.covers = covers;
