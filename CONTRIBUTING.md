# Contributing

Work comes in as one branch and one pull request against `main` per step. Several project threads open pull requests
at the same time, so a merge often leaves another open pull request conflicting with `main`.

## Merge conflicts

`.github/workflows/mergeable.yml` checks every open pull request after each push to `main`, and a pull request again
on each push to it. A pull request that no longer merges cleanly gets:

- the `merge-conflict` label,
- a failing `mergeable` check on its head commit,
- one comment starting **Merge conflict:**, posted when the conflict starts (a later conflict gets a new comment).

That comment is for the thread that opened the pull request, which watches its own pull request's activity. When it
arrives, that thread:

1. merges `origin/main` into its branch (`git fetch origin main && git merge origin/main`), never a rebase or force
   push, as reviews and approvals stay on the commits;
2. resolves the conflicts keeping both sides' intent, runs the tests the pull request touches, and pushes;
3. says nothing in the project chat unless the resolution changed what the pull request does.

The push runs the check again: the label comes off, the check passes and the comment is edited into a resolved note.
Nobody needs to report a conflict by hand. A conflict on a pull request whose thread has finished goes to the project
chat, which hands it to a thread.

To check every open pull request now (after changing this workflow, say): Actions, **mergeable**, **Run workflow**.

### Shared lists: one entry a line

Most conflicts come from lists every content thread adds to. Git merges two additions cleanly when they land on
different lines, so these lists hold one entry a line, with a trailing comma where the language allows one:

- `DialogueLines.ALL`: one `Lines` object a line, in alphabetical order. Add yours at its place, not at the end.
- `examines.json`: `npcs` and `locs` sorted by id, one entry a line, so a new id lands in the middle. `about` is a
  list: an area adds its own line instead of editing the others.
- `NpcSpawnFile.FILES`: one file a line, a new area at the end. Not sorted: the order sets NPCs' indexes.
- `World`'s `init` registrations and `commands`: one call a line, already. Add yours next to the related ones (the
  area's other registrations), not at the end of the block, as the order can matter.
- README tables (`::` commands, the runner's steps): add a row next to the related rows, not at the end of the table.

Never put several entries on one line, and never reflow such a list.

A pull request that only changes this file, top-level docs, the friend page (`docs/`) or `.github/` doesn't restart
the server on VM 214 (the deploy filter in `deploy/auto-deploy.sh`).

## Auto-merge

A pull request merges by itself, with a merge commit, once auto-merge is on and branch protection on `main` is
satisfied: every `build` job passes (jobs for parts a change doesn't touch, such as the launcher's installers, are
skipped, which counts as passing), the `codex-review` check passes, and the branch is up to date with `main`. The
thread that opens a pull request turns auto-merge on straight away; Sam stays its author and can still merge by hand
or turn it off.

`.github/workflows/automerge.yml` (`.github/scripts/automerge.js`) does two things:

- **`codex-review`** passes when Codex's latest review found nothing and covers the pull request as it is now: the
  reviewed commit is the head, or the reviewed commit merged with `main` makes the same tree as the head merged with
  `main` (`git merge-tree`), so merging `main` in needs no new review. It fails while Codex has findings, and waits
  (in progress) until Codex has reviewed, or when the pull request has changed since its last review: comment
  `@codex review` then. It's worked out from Codex's latest receipt (the comment or review naming the commit it
  reviewed), written again each time Codex comments, and on every push.
- **The queue**: "up to date with `main`" keeps two green pull requests from breaking `main` together, but GitHub's
  merge queue isn't offered on this repository, so after each push to `main`, `build` run and Codex comment the
  workflow takes the oldest pull request with auto-merge on that is behind `main`, has no failing check and a passing
  `codex-review`, merges `main` into it and starts `build` on it. One at a time: while an up-to-date pull request still
  has checks running, it waits for that one. If that merge of `main` changes the diff, it comments **Main merged in:**
  asking the thread for a new Codex review. GitHub may hold that merge's `pull_request` runs for "approval from a
  maintainer", which leaves the pull request blocked on checks that never start; the queue approves them (and only
  dispatches `build` when no runs show up). If GitHub refuses the approval, it comments **Runs waiting:** once, and
  Sam clicks "Approve workflows to run".
- **Batches**: when two or more such pull requests are ready, with their own checks finished and green, the queue
  merges up to six of them one after another onto `main` on the `automerge/batch` branch (leaving out any that
  conflicts with those before it) and runs `build` there once for all of them. Green, that commit becomes the head of
  the batch's last pull request, a fast-forward, with a `codex-review` that covers each member; auto-merge merges it,
  and since every other member's head is then in `main`, GitHub marks those merged too. One merge, one deploy. Red,
  the first half goes again, down to a single pull request, which goes the one-at-a-time way so its own checks show
  the failure. The batch branch is the queue's own: never push to it.

- **Merging itself**: with no merge rules on `main` (a private repository without GitHub Pro has none, and GitHub then
  enforces no checks and offers no auto-merge), the queue enforces them: it takes pull requests with the `automerge`
  label as well as those with auto-merge on, and merges one that is up to date with `main`, whose `build` passed, with
  every other check done and none failing, and a passing `codex-review`, with a merge commit. It moves `main` only as a
  fast-forward from the `main` it checked, so two queue runs can't merge two pull requests that were each up to date
  only with the old `main`; a green batch becomes `main` the same way. A merge made with the workflow's token starts no
  workflows, so the queue starts `build`, `mergeable` and itself on `main` afterwards. Then a thread labels its pull
  request `automerge` when it opens it, instead of turning auto-merge on.

So a thread pulls its branch (`git pull`) before pushing, as the queue may have pushed a merge of `main` (or a whole
batch) to it. A pull request that merged as part of a batch shows as merged with the batch's last pull request. A
failing check or a Codex finding takes a pull request out of the queue until it is fixed. To run the queue now:
Actions, **automerge**, **Run workflow**.

## Where CI runs

The Linux jobs (`build.yml`, `automerge.yml`, `mergeable.yml`, `probe.yml`) run on GitHub's `ubuntu-latest` while the
repository is public, and on the self-hosted runner VM on pve (`ci-runner`, Ubuntu 24.04, labels `self-hosted` and
`linux`) once it is private, as a private repository's 2,000 hosted minutes a month last a few days here. The
repository variable `RUNNER` (Settings, Secrets and variables, Actions, Variables) overrides both: `["self-hosted",
"linux"]` to try the VM early, `"ubuntu-latest"` to go back to GitHub's. The Windows and macOS installer jobs stay on
GitHub's runners and leave with the launcher.

The VM needs, besides the runner service: `git`, `curl`, `unzip`, Node.js 20 or newer (`node` on the path: the
script checks call it directly), PowerShell 7 (`pwsh`, for `launch-scripts`); Java comes from `setup-java`. Two runner
instances on it let two jobs run at once; more than one Gradle job a vCPU pair makes the tick-timing tests flaky.

## The staging server

A branch can run on the staging server next to the live one before it merges (`deploy/README.md`, "The staging
server: any branch, next to the live one"): push it as origin's `staging` branch (`git push origin my-branch:staging`)
and VM 214's timer builds and runs its head within a minute on the LAN-only ports 44594 and 44595. Threads push it
themselves (Sam, 2026-10-30), one at a time:

1. `git ls-remote --heads origin staging` first: a branch there is another thread's staging run. Wait for it to go,
   and never push over it.
2. Push, test, and push again as the branch moves: staging follows it.
3. Delete it when done (`git push origin --delete staging`), which takes staging down. A run left up holds the slot,
   and VM 214's memory, for everyone.

## Scheduled checks

Two workflows run on a schedule and, when they fail, open an issue assigned to the repository's owner, so the failure
arrives as an issue email (`.github/scripts/alert.js`; GitHub mails a failed scheduled run only to whoever last edited
its cron line, and a line Claude wrote reaches no one). The issue's body is brought up to date while the check keeps
failing, with no new notification however long that lasts, and it is closed with a comment when the check passes
again. Each run stays red while the check fails.

- `.github/workflows/probe.yml`, every 15 minutes: the server as a friend reaches it, through the relay at
  odinsgate.net (`.github/scripts/probe.js`). `/status.json` on port 43595 must answer with `"up": true` and port
  43594 must accept a connection, tried eight times half a minute apart before it counts as down, so the restart
  after a deploy raises no alarm. A full server is up. The issue is **odinsgate.net is down**.
- `.github/workflows/pin-health.yml`, Mondays, and on a pull request that touches the pin table or the check: every
  pin in `src/main/resources/runelite-pins.json` still holds on RuneLite's servers (`./gradlew -p launcher pinHealth`,
  `launcher/src/main/java/net/odinsgate/launcher/PinHealth.java`). The bootstrap carries the pinned SHA-256 and
  RuneLite's signature, every jar it names for any system answers a HEAD request with its size, and RuneLite's runtime
  config doesn't list the pinned client version as outdated. HEAD only: no RuneLite jar is downloaded or cached in
  Actions. The issue is **A RuneLite pin is no longer healthy**; on a pull request a failure is just a failing check.

To run either now: Actions, the workflow, **Run workflow**. The scripts' own tests run in `build`
(`node --test .github/scripts/*.test.js`).
