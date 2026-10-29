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

A pull request that only changes this file, top-level docs or `.github/` doesn't restart the server on VM 214 (the
deploy filter in `deploy/auto-deploy.sh`).
