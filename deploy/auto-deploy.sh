#!/usr/bin/env bash
# Redeploys the server on VM 214 when origin/main has moved: fast-forwards the checkout and runs
# `docker compose up -d --build` with OSRSPS_COMMIT set, so the server's /version answers with that commit.
# When the merges since the running server's build change nothing it is built from (docs, tests, the RuneLite
# plugins, the launcher, CI, this folder), it only fast-forwards: the server keeps running and its players stay in,
# and /version answers with the new commit anyway, from the state files below.
# It also swaps the cache when deploy/cache-build.txt names another OpenRS2 build than data/cache-source.txt.
# Run every minute (osrsps-deploy.timer, or sam's crontab); by hand it deploys main now if main is new.
#
#   deploy/auto-deploy.sh            deploy origin/main if it isn't deployed yet (and didn't fail last time)
#   deploy/auto-deploy.sh --force    rebuild and restart origin/main even if it is
#
# State lives in .deploy/ (git-ignored), which compose.yaml mounts read-only into the server: `deployed` holds the
# commit of main that is up and `built` the one the running server was built from (older after merges that left it
# running), `failed` the last commit whose deploy failed, which is not retried every minute; a newer commit on main, or
# --force, tries again. `built` is empty from a new build, or a restart, until the server is up, and stays empty if it
# doesn't start: the image and the running server are then no longer known to be one build, so the next merge builds,
# even one that leaves the server's files alone, and the server answers /version with its own commit. `cache-wait`
# holds a cache build that couldn't be fetched yet (OpenRS2 doesn't list it as newest, or the fetch failed): it is
# tried again 30 minutes after the last try.
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
	local target deployed failed built
	target=$(git rev-parse origin/main)
	deployed=$(cat .deploy/deployed 2>/dev/null || true)
	failed=$(cat .deploy/failed 2>/dev/null || true)
	# Before `built` was written, every deploy built the commit it deployed.
	built=$(cat .deploy/built 2>/dev/null || echo "$deployed")
	local code=1
	if ((!force)) && [[ $target == "$deployed" || $target == "$failed" ]]; then
		code=0
	fi
	# With no new code the checkout already holds the cache build wanted; else it is read after the fast-forward.
	if ((!code)) && ! cache_due; then
		return 0
	fi

	local commit=$built
	if ((code && !force)) && [[ -n $built ]] && ! server_changed "$built" "$target"; then
		# A restart would kick every player for nothing: the checkout moves on, and the server's /version reads the new
		# commit from .deploy/deployed, as `built` still names its own.
		if ! git merge --ff-only --quiet origin/main; then
			echo "$target" >.deploy/failed
			echo "Deploy of $target FAILED: the checkout can't fast-forward; it is retried when main moves on or with --force"
			return 1
		fi
		put built "$built"
		put deployed "$target"
		rm -f .deploy/failed
		echo "Deployed $target without a restart: nothing the server is built from changed since $built"
		code=0
	elif ((code)); then
		echo "Deploying $target (up now: ${deployed:-unknown})"
		# The build's whole output goes to .deploy/build.log; on a failure its end is logged, where Gradle's error is.
		if ! git merge --ff-only --quiet origin/main ||
			! OSRSPS_COMMIT=$target BUILDKIT_PROGRESS=plain docker compose build >.deploy/build.log 2>&1; then
			[[ -s .deploy/build.log ]] && tail -n 60 .deploy/build.log
			echo "$target" >.deploy/failed
			echo "Deploy of $target FAILED; it is retried when main moves on or with --force"
			return 1
		fi
		# The image is the new build from here on, not the running server's.
		put built ""
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
	# The restart replaces the running server: which build runs is known again once the new one is up.
	put built ""
	if OSRSPS_COMMIT=${commit:-unknown} docker compose up -d "${recreate[@]}"; then
		if ((code)); then
			# deployed first: until built names the new server's commit, its /version answers with its own.
			put deployed "$target"
			rm -f .deploy/failed
		fi
		put built "$commit"
		echo "Deployed ${commit:-unknown}"
	else
		((code)) && echo "$target" >.deploy/failed
		echo "Starting ${commit:-unknown} FAILED"
		return 1
	fi
}

# Whether the commits from $1 to $2 change anything the server is built from or runs with: any file but the docs at the
# top, the tests, the RuneLite plugins, the launcher, CI and this folder (whose cache-build.txt has a check of its own,
# cache_due). A file moved out of the server counts, as both of its paths do. When git can't tell (a commit it doesn't
# have), yes.
server_changed() {
	local files
	files=$(git diff --no-renames --name-only "$1" "$2" --) || return 0
	[[ -n $files ]] || return 1
	grep -qvE '^((test-runner|tick-recorder|launcher|deploy|docs|\.github)/|src/test/|[^/]+\.md$)' <<<"$files"
}

# Writes .deploy/$1 whole, as the server reads `built` and `deployed` for its /version while this runs.
put() {
	printf '%s\n' "$2" >".deploy/$1.new"
	mv -f ".deploy/$1.new" ".deploy/$1"
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
