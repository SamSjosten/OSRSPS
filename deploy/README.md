# Deploying on VM 214 by itself

`auto-deploy.sh` brings the server on VM 214 up to date with `main`: every minute a line in `sam`'s crontab (or
`osrsps-deploy.timer`) runs it, and when `origin/main` has moved it fast-forwards `/srv/rsps/OSRSPS` and runs
`docker compose up -d --build`. Nobody needs to ssh in after a merge.

- The server answers `http://192.168.0.210:43595/version` with the commit of main it runs: the one it was built from
  (`OSRSPS_COMMIT`, set by the script; `unknown` after a plain `docker compose up`), or a newer one that left it
  running (next point). `test-runner/launch/start-client.ps1 -Commit <sha>` on the test VM waits for it, so a test run
  starts once the merge is live.
- When the merges since the running server's build change nothing it is built from, the script only fast-forwards
  the checkout, and the server keeps running with its players in. That is a merge whose files are all docs at the top
  of the repo, tests (`src/test/`), the RuneLite plugins (`test-runner/`, `tick-recorder/`), the launcher
  (`launcher/`), CI (`.github/`) or this folder. Any other file, or one moved out of the server, rebuilds it.
  `.deploy/built` keeps the running server's commit and `.deploy/deployed` the merge's, and compose mounts `.deploy/`
  read-only into the container, so `/version` answers with the merge's commit anyway and `/status` adds a
  `built from <commit>` line under it. `.deploy/built` is empty from a new build, or a restart, until the server is up,
  and stays empty if it doesn't start: the image may then be a build that never started, and the old server may be
  gone, so the next merge builds and restarts even when it leaves the server's files alone (a revert of the change that
  didn't start, say), and `/version` answers with the server's own commit. `--force` rebuilds and restarts all the
  same. `deploy/auto-deploy-test.sh` (run by CI) plays the script through these cases on a made-up `main` with a
  stand-in for docker.
- It also swaps the cache: when `deploy/cache-build.txt` (e.g. `241.4`) names another OpenRS2 build than
  `data/cache-source.txt`, it runs `fetch-cache <build>` with the new image, then recreates the container. The fetch
  keeps the old cache in `data/cache.previous`. While OpenRS2 doesn't list that build as the newest, `fetch-cache`
  changes nothing and the script tries again every 30 minutes (`.deploy/cache-wait`). Rolling back is by hand (see the
  top README's "Get the cache"); put the old build in `cache-build.txt` too, or the next run fetches the new one again.
  Write the build as OpenRS2's caches.json lists it, which is what `data/cache-source.txt` records: it lists the
  2026-10-20 cache (the one the game calls 241.4) as plain `241`, with no minor build.
- `http://192.168.0.210:43595/status` answers with the commit, the cache's source line and the game frame check
  (`Game frame: ...` lines), so the test VM can check a swap without a shell here. Its last line, `js5 connections N`,
  counts the cache (JS5) connections let in since the server started; only a client of the server's revision gets
  that far.
- The image is built on 214 next to the live server, and Proxmox can balloon the VM down to 2 GB. On 2026-10-28 a
  build with Gradle's defaults ran it out of memory: the kernel OOM-killed the running server four times in 16 minutes
  and spared the build (build steps seem to keep the Docker daemon's low OOM score). So the Dockerfile keeps the build
  to one small JVM and raises that step's OOM score to the top, and compose fixes the server's heap at 512 MB and
  lowers its score to -500: if memory runs out anyway, the kernel kills the build first and every process with the
  default score before the server.
- A deploy that fails (build error, a server that doesn't start, or a checkout that can't fast-forward) is written to
  `.deploy/failed` and is not retried every minute. The next commit on main tries again, or
  `deploy/auto-deploy.sh --force` by hand.
- Log: `journalctl -u osrsps-deploy -n 50` (a failed build also logs the last 60 lines of its output); the last build's
  whole output is in `.deploy/build.log`. State: `.deploy/deployed`, `.deploy/built`,
  `.deploy/failed` and `.deploy/cache-wait` in the checkout. On a checkout where `docker compose up` ran before the
  script ever did, Docker made `.deploy/` for its mount as root: `sudo chown sam .deploy` before the first run.
- By hand, as `sam` in `/srv/rsps/OSRSPS`: `deploy/auto-deploy.sh` (deploys main if new) or `--force` (rebuilds now).
- Other work on the checkout or the container (a cache swap, `fetch-cache`, a hand-made `git pull`) should hold the
  same lock so a timed deploy can't start in the middle: `flock .deploy/lock docker compose run --rm osrsps fetch-cache`,
  or stop the crontab line / `sudo systemctl stop osrsps-deploy.timer` first and start it after.

## The staging server: any branch, next to the live one

`staging.sh` runs a second server on VM 214, in the compose project `osrsps-staging`, from any branch or commit, so a
change can be tried on the test VMs before it merges. It listens on 44594 (game) and 44595 (HTTP), which the Proxmox
host forwards from `192.168.0.210` for the LAN only (the relay and the internet never see it). As `sam` in
`/srv/rsps/OSRSPS`:

```bash
deploy/staging.sh up claude/some-branch   # fetch the branch's head from origin, build it and start, or restart, staging
deploy/staging.sh up 3c1b194              # a commit, or main
deploy/staging.sh up claude/some-branch --force   # build and restart even when that commit is up already
deploy/staging.sh status                  # the ref and commit up, the container, what /version answers
deploy/staging.sh down                    # stop and remove it; the data folder and the image stay for the next up
deploy/staging.sh auto                    # what the crontab line below runs: staging follows origin's `staging` branch
```

- Without a shell on the VM, staging follows the branch named `staging` on origin: `git push origin my-branch:staging`
  and within a minute `auto` (the crontab line below) builds and runs its head; push again and it follows; delete the
  branch (`git push origin --delete staging`) and staging goes down. A commit whose build or start failed is written
  to `.staging/failed` and not tried every minute; the next commit on the branch is. While the branch exists, `auto`
  brings staging back up after a `down` by hand, so the branch is what to delete; staging brought up by hand on
  another ref is left alone until the branch appears, and then follows it. `auto` says nothing when there is nothing
  to do, so `.deploy/staging.log` holds only the ups and downs.
- Everything staging is under `.staging/` in the checkout (git-ignored). `src/` is a git worktree at the staged
  commit, which the image is built from; the compose file is the checkout's `deploy/compose.staging.yaml`, never the
  staged commit's, so a branch can't change the staging server's ports or data folder. `deployed` and `ref` say what
  is up, `build.log` holds the last build's output, and `data/` is the server's data folder.
- `data/` is made at the first `up` from the live `data/`: the cache, the RSA key and every other file at the top of
  the folder (the words, the prices) are copied, the saves and accounts are not, so every player starts fresh there
  (the test accounts included: the runner's first login makes them). The key is the live one, so the launcher
  reaches staging with the signed manifest's key (`--server`, below). To start the data over, `down`,
  `sudo rm -rf .staging/data` (the container made its files as root) and `up` again. A staged commit whose `deploy/cache-build.txt`
  names another OpenRS2 build than `.staging/data/cache-source.txt` gets that build fetched into the staging data
  before the start, with the image just built; a fetch that fails (OpenRS2 doesn't list it as newest yet) is said and
  the server starts on the cache it has.
- The build takes the deploy lock (`.deploy/lock`) and waits up to 15 minutes for a live deploy's build to finish, so
  the VM never builds two images at once; a timed deploy that comes due during a staging build sees the lock and tries
  again a minute later. The staging server runs with the same 512 MB heap as the live one. When memory runs out, the
  kernel kills a build first (the Dockerfile raises its OOM score), the staging server next (the default score), and
  the live server last (-500). `down` when staging isn't needed, as it keeps running across reboots like the live one.
- `http://192.168.0.210:44595/version` answers with the staged commit, so `start-client.ps1 -Commit <sha> -GamePort
  44594` on a test VM waits for it. `/status`, `/status.json` and the client files answer there too, with the host the
  client asked by and the staging ports, since the server listens on 44594 itself (`serve 44594`).
- The test VMs reach staging through the launcher only: `start-client.ps1 -Restart -Launcher -GamePort 44594`
  (`test-runner/launch/README.md`) points it there with `--server`. RSProx's client goes wherever its `OSRSPS` target
  says, the live server, so the script refuses another server without `-Launcher`.
- `deploy/staging-test.sh` (run by CI) plays the script through these cases on a made-up origin with a stand-in for
  docker, as `auto-deploy-test.sh` does for the deploy.

## Installing without root: sam's crontab

What runs on 214 now. As `sam`, one line in `crontab -e` runs the same script every minute, logging to
`.deploy/log` (the lock keeps runs from overlapping):

```bash
* * * * * /srv/rsps/OSRSPS/deploy/auto-deploy.sh >>/srv/rsps/OSRSPS/.deploy/log 2>&1 # osrsps-auto-deploy
```

To stop it: `crontab -l | grep -v osrsps-auto-deploy | crontab -`. If the timer below is installed later, remove
this line.

A second line, the same way, keeps the staging server on origin's `staging` branch (above):

```bash
* * * * * /srv/rsps/OSRSPS/deploy/staging.sh auto >>/srv/rsps/OSRSPS/.deploy/staging.log 2>&1 # osrsps-auto-staging
```

## Installing the timer instead (once, as root on VM 214)

The checkout must already hold this folder, and `sam` must be able to `git fetch` without typing anything
(`sudo -u sam git -C /srv/rsps/OSRSPS fetch origin main` returns quietly).

```bash
cp /srv/rsps/OSRSPS/deploy/osrsps-deploy.service /srv/rsps/OSRSPS/deploy/osrsps-deploy.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now osrsps-deploy.timer
systemctl list-timers osrsps-deploy.timer     # next run within a minute
journalctl -u osrsps-deploy -n 20             # after it: "Deploying <sha>" and "Deployed <sha>"
curl -s http://127.0.0.1:43595/version        # that sha
```

The first run rebuilds whatever main is, since nothing is recorded as deployed yet. To stop deploying by itself:
`systemctl disable --now osrsps-deploy.timer`.
