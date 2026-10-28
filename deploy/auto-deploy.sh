#!/usr/bin/env bash
# Redeploys the server on VM 214 when origin/main has moved: fast-forwards the checkout and runs
# `docker compose up -d --build` with OSRSPS_COMMIT set, so the server's /version answers with that commit.
# osrsps-deploy.timer runs it every minute; by hand it deploys main now if main is new.
#
#   deploy/auto-deploy.sh            deploy origin/main if it isn't deployed yet (and didn't fail last time)
#   deploy/auto-deploy.sh --force    rebuild and restart origin/main even if it is
#
# State lives in .deploy/ (git-ignored): `deployed` holds the commit that is up, `failed` the last commit whose
# deploy failed, which is not retried every minute; a newer commit on main, or --force, tries again.
# The whole script is one function so that a fast-forward that changes this file can't change it mid-run.
set -euo pipefail

main() {
	local force=0
	[[ ${1:-} == --force ]] && force=1
	local repo
	repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
	cd "$repo"
	mkdir -p .deploy

	exec 9>.deploy/lock
	if ! flock -n 9; then
		echo "Another deploy is running"
		return 0
	fi

	git fetch --quiet origin main
	local target deployed failed
	target=$(git rev-parse origin/main)
	deployed=$(cat .deploy/deployed 2>/dev/null || true)
	failed=$(cat .deploy/failed 2>/dev/null || true)
	if ((!force)) && [[ $target == "$deployed" || $target == "$failed" ]]; then
		return 0
	fi

	echo "Deploying $target (up now: ${deployed:-unknown})"
	if git merge --ff-only --quiet origin/main && OSRSPS_COMMIT=$target docker compose up -d --build; then
		echo "$target" >.deploy/deployed
		rm -f .deploy/failed
		echo "Deployed $target"
	else
		echo "$target" >.deploy/failed
		echo "Deploy of $target FAILED; it is retried when main moves on or with --force"
		return 1
	fi
}

main "$@"
