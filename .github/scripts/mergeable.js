// Flags open pull requests that stop merging cleanly into their base branch (see CONTRIBUTING.md), run by
// .github/workflows/mergeable.yml through actions/github-script.
//
// A pull request that conflicts gets the `merge-conflict` label, a failing `mergeable` check on its head and one new
// comment: that comment is what wakes the thread that opened it. When it merges cleanly again the label goes, the
// check passes and the comment is edited into a resolved note. A conflict that comes back later is a new comment.
//
// A push to main writes a check only when a pull request's state changes, so the threads aren't woken by every merge;
// a pull request's own event (and a run by hand) writes it every time.

const LABEL = 'merge-conflict';
const CHECK = 'mergeable';
const OPEN = '<!-- mergeable-check:open -->';
const RESOLVED = '<!-- mergeable-check:resolved -->';

const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

// GitHub works mergeability out in the background after a push to either side: until it has, `mergeable` is null.
// Asking starts the work, so ask again a few seconds later. Null when it still doesn't know.
async function mergeState(github, repo, number, sleep, tries) {
  for (let i = 0; i < tries; i++) {
    const { data: pr } = await github.rest.pulls.get({ ...repo, pull_number: number });
    if (pr.state !== 'open') return null;
    if (pr.mergeable !== null && pr.mergeable_state !== 'unknown') return { pr, conflicted: pr.mergeable === false };
    await sleep(5000);
  }
  return null;
}

function conflictComment(pr, mainSha) {
  const moved = mainSha ? ` since \`${pr.base.ref}\` moved to ${mainSha.slice(0, 7)}` : '';
  return `${OPEN}
**Merge conflict:** this branch no longer merges cleanly into \`${pr.base.ref}\`${moved}.

To the thread that opened this pull request: merge \`origin/${pr.base.ref}\` into the branch, resolve the conflicts, \
run the tests and push. Merge, don't rebase or force-push. This comment turns into a resolved note, the \
\`${LABEL}\` label comes off and the \`${CHECK}\` check passes once the branch merges cleanly again.`;
}

function resolvedComment(pr) {
  return `${RESOLVED}
~~Merge conflict with \`${pr.base.ref}\`~~: resolved, merges cleanly again at ${pr.head.sha.slice(0, 7)}.`;
}

module.exports = async function run({ github, context, core, sleep = wait, tries = 12 }) {
  const repo = context.repo;
  const push = context.eventName === 'push';
  const mainSha = push ? context.payload.after : null;

  let numbers;
  if (context.payload.pull_request) {
    numbers = [context.payload.pull_request.number];
  } else {
    const open = await github.paginate(github.rest.pulls.list, { ...repo, state: 'open', per_page: 100 });
    numbers = open.map(pr => pr.number);
  }

  let labelMade = false;
  for (const number of numbers) {
    const state = await mergeState(github, repo, number, sleep, tries);
    if (!state) {
      core.info(`#${number}: closed, or GitHub doesn't know yet whether it merges; left as it is`);
      continue;
    }
    const { pr, conflicted } = state;
    const labelled = pr.labels.some(label => label.name === LABEL);
    core.info(`#${number}: ${conflicted ? 'conflicts' : 'merges cleanly'}${labelled ? ' (labelled)' : ''}`);

    if (conflicted && !labelled) {
      if (!labelMade) {
        await github.rest.issues
          .createLabel({ ...repo, name: LABEL, color: 'd93f0b', description: 'Needs its base branch merged in' })
          .catch(error => { if (error.status !== 422) throw error; }); // 422: it's there already
        labelMade = true;
      }
      await github.rest.issues.addLabels({ ...repo, issue_number: number, labels: [LABEL] });
      await github.rest.issues.createComment({ ...repo, issue_number: number, body: conflictComment(pr, mainSha) });
    }
    if (!conflicted && labelled) {
      await github.rest.issues
        .removeLabel({ ...repo, issue_number: number, name: LABEL })
        .catch(error => { if (error.status !== 404) throw error; });
      const comments = await github.paginate(github.rest.issues.listComments, { ...repo, issue_number: number, per_page: 100 });
      for (const comment of comments) {
        if (comment.user && comment.user.type === 'Bot' && comment.body.startsWith(OPEN)) {
          await github.rest.issues.updateComment({ ...repo, comment_id: comment.id, body: resolvedComment(pr) });
        }
      }
    }

    if (!push || conflicted !== labelled) {
      await github.rest.checks.create({
        ...repo,
        name: CHECK,
        head_sha: pr.head.sha,
        status: 'completed',
        conclusion: conflicted ? 'failure' : 'success',
        output: conflicted
          ? { title: `Conflicts with ${pr.base.ref}`, summary: `Merge \`origin/${pr.base.ref}\` into the branch and resolve the conflicts.` }
          : { title: `Merges cleanly into ${pr.base.ref}`, summary: 'No merge conflict.' },
      });
    }
  }
};

module.exports.LABEL = LABEL;
module.exports.CHECK = CHECK;
module.exports.OPEN = OPEN;
module.exports.RESOLVED = RESOLVED;
