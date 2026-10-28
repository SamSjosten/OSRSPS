# Deploying on VM 214 by itself

`auto-deploy.sh` brings the server on VM 214 up to date with `main`: every minute a line in `sam`'s crontab (or
`osrsps-deploy.timer`) runs it, and when `origin/main` has moved it fast-forwards `/srv/rsps/OSRSPS` and runs
`docker compose up -d --build`. Nobody needs to ssh in after a merge.

- The server answers `http://192.168.0.210:43595/version` with the commit it was built from (`OSRSPS_COMMIT`, set by
  the script; `unknown` after a plain `docker compose up`). `test-runner/launch/start-client.ps1 -Commit <sha>` on the
  test VM waits for it, so a test run starts once the merge is live.
- It also swaps the cache: when `deploy/cache-build.txt` (e.g. `241.4`) names another OpenRS2 build than
  `data/cache-source.txt`, it runs `fetch-cache <build>` with the new image, then recreates the container. The fetch
  keeps the old cache in `data/cache.previous`. While OpenRS2 doesn't list that build as the newest, `fetch-cache`
  changes nothing and the script tries again every 30 minutes (`.deploy/cache-wait`). Rolling back is by hand (see the
  top README's "Get the cache"); put the old build in `cache-build.txt` too, or the next run fetches the new one again.
  Write the build as OpenRS2's caches.json lists it, which is what `data/cache-source.txt` records: it lists the
  2026-10-20 cache (the one the game calls 241.4) as plain `241`, with no minor build.
- `http://192.168.0.210:43595/status` answers with the commit, the cache's source line and the game frame check
  (`Game frame: ...` lines), so the test VM can check a swap without a shell here.
- A deploy that fails (build error, or a checkout that can't fast-forward) is written to `.deploy/failed` and is not
  retried every minute. The next commit on main tries again, or `deploy/auto-deploy.sh --force` by hand.
- Log: `.deploy/log` with the crontab line, `journalctl -u osrsps-deploy -n 50` with the timer. State: `.deploy/deployed`,
  `.deploy/failed` and `.deploy/cache-wait` in the checkout.
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
