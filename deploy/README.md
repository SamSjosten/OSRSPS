# Deploying on VM 214 by itself

`auto-deploy.sh` brings the server on VM 214 up to date with `main`: every minute `osrsps-deploy.timer` runs it, and
when `origin/main` has moved it fast-forwards `/srv/rsps/OSRSPS` and runs `docker compose up -d --build`. Nobody needs
to ssh in after a merge.

- The server answers `http://192.168.0.210:43595/version` with the commit it was built from (`OSRSPS_COMMIT`, set by
  the script; `unknown` after a plain `docker compose up`). `test-runner/launch/start-client.ps1 -Commit <sha>` on the
  test VM waits for it, so a test run starts once the merge is live.
- A deploy that fails (build error, or a checkout that can't fast-forward) is written to `.deploy/failed` and is not
  retried every minute. The next commit on main tries again, or `deploy/auto-deploy.sh --force` by hand.
- Log: `journalctl -u osrsps-deploy -n 50`. State: `.deploy/deployed` and `.deploy/failed` in the checkout.
- By hand, as `sam` in `/srv/rsps/OSRSPS`: `deploy/auto-deploy.sh` (deploys main if new) or `--force` (rebuilds now).

## Installing (once, as root on VM 214)

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
