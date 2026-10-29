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

## Installing without root: sam's crontab

What runs on 214 now. As `sam`, one line in `crontab -e` runs the same script every minute, logging to
`.deploy/log` (the lock keeps runs from overlapping):

```bash
* * * * * /srv/rsps/OSRSPS/deploy/auto-deploy.sh >>/srv/rsps/OSRSPS/.deploy/log 2>&1 # osrsps-auto-deploy
```

To stop it: `crontab -l | grep -v osrsps-auto-deploy | crontab -`. If the timer below is installed later, remove
this line.

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
