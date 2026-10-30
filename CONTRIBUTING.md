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

A pull request that only changes this file, top-level docs, the friend page (`docs/`) or `.github/` doesn't restart
the server on VM 214 (the deploy filter in `deploy/auto-deploy.sh`).

## Auto-merge

A pull request merges by itself, with a merge commit, once auto-merge is on and branch protection on `main` is
satisfied: every `build` job passes, the `codex-review` check passes, and the branch is up to date with `main`. The
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

So a thread pulls its branch (`git pull`) before pushing, as the queue may have pushed a merge of `main` to it. A
failing check or a Codex finding takes a pull request out of the queue until it is fixed. To run the queue now:
Actions, **automerge**, **Run workflow**.

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
