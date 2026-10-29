#!/usr/bin/env bash
# Redeploys the server on VM 214 when origin/main has moved: fast-forwards the checkout and runs
# `docker compose up -d --build` with OSRSPS_COMMIT set, so the server's /version answers with that commit.
# It also swaps the cache when deploy/cache-build.txt names another OpenRS2 build than data/cache-source.txt.
# Run every minute (osrsps-deploy.timer, or sam's crontab); by hand it deploys main now if main is new.
#
#   deploy/auto-deploy.sh            deploy origin/main if it isn't deployed yet (and didn't fail last time)
#   deploy/auto-deploy.sh --force    rebuild and restart origin/main even if it is
#
# State lives in .deploy/ (git-ignored): `deployed` holds the commit that is up, `failed` the last commit whose
# deploy failed, which is not retried every minute; a newer commit on main, or --force, tries again. `cache-wait` holds
# a cache build that couldn't be fetched yet (OpenRS2 doesn't list it as newest, or the fetch failed): it is tried
# again 30 minutes after the last try.
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
	local code=1
	if ((!force)) && [[ $target == "$deployed" || $target == "$failed" ]]; then
		code=0
	fi
	# With no new code the checkout already holds the cache build wanted; else it is read after the fast-forward.
	if ((!code)) && ! cache_due; then
		return 0
	fi

	local commit=$deployed
	if ((code)); then
		echo "Deploying $target (up now: ${deployed:-unknown})"
		# The build's whole output goes to .deploy/build.log; on a failure its end is logged, where Gradle's error is.
		if ! git merge --ff-only --quiet origin/main ||
			! OSRSPS_COMMIT=$target BUILDKIT_PROGRESS=plain docker compose build >.deploy/build.log 2>&1; then
			[[ -s .deploy/build.log ]] && tail -n 60 .deploy/build.log
			echo "$target" >.deploy/failed
			echo "Deploy of $target FAILED; it is retried when main moves on or with --force"
			return 1
		fi
		commit=$target
	fi

	# The cache is fetched by the image just built, while the old server still runs; the restart below picks it up.
	local recreate=()
	if cache_due; then
		local wanted
		wanted=$(cache_wanted)
		echo "Fetching cache build $wanted (have: $(cache_have || echo none))"
		local status=0
		docker compose run --rm osrsps fetch-cache "$wanted" || status=$?
		if ((status == 0)); then
			rm -f .deploy/cache-wait
			recreate=(--force-recreate)
			echo "Fetched cache build $wanted; the old cache is in data/cache.previous"
		else
			echo "$wanted" >.deploy/cache-wait
			if ((status == 3)); then
				echo "OpenRS2 doesn't list build $wanted as newest yet; trying again in 30 minutes"
			else
				echo "Fetching cache build $wanted FAILED (exit $status); trying again in 30 minutes"
			fi
		fi
	fi

	if ((!code)) && ((${#recreate[@]} == 0)); then
		return 0
	fi
	if OSRSPS_COMMIT=${commit:-unknown} docker compose up -d "${recreate[@]}"; then
		if ((code)); then
			echo "$target" >.deploy/deployed
			rm -f .deploy/failed
		fi
		echo "Deployed ${commit:-unknown}"
	else
		((code)) && echo "$target" >.deploy/failed
		echo "Starting ${commit:-unknown} FAILED"
		return 1
	fi
}

# The OpenRS2 build deploy/cache-build.txt asks for, e.g. 241.4; nothing when the file is missing or empty.
cache_wanted() {
	tr -d '[:space:]' <deploy/cache-build.txt 2>/dev/null || true
}

# The build in data/cache-source.txt ("openrs2 runescape/2740 build 241.3 2026-..."); fails without one.
cache_have() {
	local have
	have=$(sed -n 's/.* build \([^ ]*\).*/\1/p' data/cache-source.txt 2>/dev/null)
	[[ -n $have ]] && echo "$have"
}

# Whether the cache should be fetched now: another build is wanted, and it wasn't tried in the last 30 minutes.
cache_due() {
	local wanted
	wanted=$(cache_wanted)
	[[ -n $wanted && $wanted != "$(cache_have || true)" ]] || return 1
	[[ $(cat .deploy/cache-wait 2>/dev/null || true) != "$wanted" ]] && return 0
	[[ -z $(find .deploy/cache-wait -mmin -30 2>/dev/null) ]]
}

main "$@"; exit
