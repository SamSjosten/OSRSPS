#!/usr/bin/env bash
# A staging server on VM 214 next to the live one: a second compose project, osrsps-staging, that builds any branch or
# commit and serves it on the LAN-only ports 44594 (game) and 44595 (HTTP), with a data folder of its own. The live
# server, its checkout and its data are left alone, with one exception: a staging build waits for a live deploy's build
# and holds the deploy lock while it builds, so VM 214 never builds two images at once (two would run it out of memory;
# a deploy that comes due meanwhile tries again a minute later).
#
#   deploy/staging.sh up <branch|commit>   fetch that commit from origin, build it and start, or restart, staging on it
#   deploy/staging.sh up <ref> --force     build and restart even when that commit is up already
#   deploy/staging.sh down                 stop and remove the staging server; its data folder and image stay
#   deploy/staging.sh status               what is up: the ref and commit, the container, /version
#   deploy/staging.sh auto                 every minute from cron: staging follows the branch named staging on origin
#
# `auto` is how staging is used without a shell on VM 214: push the commit to try as the branch `staging` on origin
# (`git push origin my-branch:staging`), and within a minute staging builds and runs it; push again and it follows;
# delete the branch and staging goes down. A commit whose build or start failed is not tried every minute
# (.staging/failed); the next commit on the branch is. While the branch exists, `auto` brings staging back up after a
# `down` by hand; staging brought up by hand on another ref is left alone until the branch appears.
#
# Everything staging is under .staging/ in this checkout (git-ignored): src/ is a git worktree at the staged commit,
# whose Dockerfile builds the image (the compose file is this checkout's deploy/compose.staging.yaml, never the staged
# commit's); data/ is the staging server's data folder, made at the first `up` from the live data/: the cache, the RSA
# key and every other file at the top of data/ (the words, the prices) are copied, the saves and accounts are not, so
# a staging player starts fresh; `deployed` names the commit up and `ref` what it was asked as; build.log holds the
# last build's output. When the staged commit's deploy/cache-build.txt names another OpenRS2 build than the staging
# data has, that build is fetched into the staging data before the start, the way auto-deploy.sh does for the live one.
set -euo pipefail

AUTO_BRANCH=staging

main() {
	local repo
	repo=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
	cd "$repo"
	case ${1:-} in
		up) shift; up "$@" ;;
		down) down ;;
		status) status ;;
		auto) auto ;;
		*) echo "usage: deploy/staging.sh up <branch|commit> [--force] | down | status | auto" >&2; return 64 ;;
	esac
}

compose() {
	docker compose -p osrsps-staging --project-directory .staging -f deploy/compose.staging.yaml "$@"
}

up() {
	local ref=${1:-} force=0
	if [[ -z $ref ]]; then
		echo "up wants a branch or commit: deploy/staging.sh up <branch|commit> [--force]" >&2
		return 64
	fi
	[[ ${2:-} == --force ]] && force=1
	mkdir -p .staging
	git fetch --quiet origin
	local commit
	if ! commit=$(resolve "$ref"); then
		echo "No branch or commit named $ref on origin"
		return 1
	fi
	local running
	running=$(cat .staging/deployed 2>/dev/null || true)
	if ((!force)) && [[ $commit == "$running" ]]; then
		echo "Staging runs $ref ($commit) already; --force rebuilds and restarts it"
		return 0
	fi
	checkout "$commit"
	prepare_data

	echo "Building $ref ($commit) for staging (up now: ${running:-nothing})"
	mkdir -p .deploy
	exec 9>.deploy/lock
	if ! flock -w "${STAGING_LOCK_WAIT:-900}" 9; then
		echo "A live deploy has been building for a while; try again when it is done"
		return 1
	fi
	if ! OSRSPS_COMMIT=$commit BUILDKIT_PROGRESS=plain compose build >.staging/build.log 2>&1; then
		[[ -s .staging/build.log ]] && tail -n 60 .staging/build.log
		echo "Building $ref ($commit) for staging FAILED; the whole output is in .staging/build.log"
		return 1
	fi
	exec 9>&-

	local wanted
	wanted=$(tr -d '[:space:]' <.staging/src/deploy/cache-build.txt 2>/dev/null || true)
	if [[ -n $wanted && $wanted != "$(cache_have || true)" ]]; then
		echo "Fetching cache build $wanted into the staging data (have: $(cache_have || echo none))"
		if ! compose run --rm osrsps fetch-cache "$wanted"; then
			echo "Fetching cache build $wanted FAILED; staging starts on the cache it has"
		fi
	fi

	if ! OSRSPS_COMMIT=$commit compose up -d --force-recreate; then
		echo "Starting $ref ($commit) on staging FAILED"
		return 1
	fi
	printf '%s\n' "$commit" >.staging/deployed
	printf '%s\n' "$ref" >.staging/ref
	rm -f .staging/failed
	echo "Staging runs $ref ($commit) at 192.168.0.210:44594 (HTTP 44595)"
}

# Staging on origin's `staging` branch: up on its head when that isn't up (or the commit that failed last), down when
# the branch is gone and staging was up on it; nothing, said quietly, otherwise. One run at a time (.staging/lock).
auto() {
	mkdir -p .staging
	exec 8>.staging/lock
	if ! flock -n 8; then
		echo "Another staging run is going"
		return 0
	fi
	git fetch --quiet --prune origin
	local target running
	target=$(git rev-parse --verify --quiet "origin/$AUTO_BRANCH^{commit}" || true)
	running=$(cat .staging/deployed 2>/dev/null || true)
	if [[ -n $target ]]; then
		if [[ $target == "$running" || $target == "$(cat .staging/failed 2>/dev/null || true)" ]]; then
			return 0
		fi
		if ! up "$AUTO_BRANCH"; then
			printf '%s\n' "$target" >.staging/failed
			echo "Staging of $AUTO_BRANCH ($target) FAILED; it is tried again when the branch moves on"
			return 1
		fi
	elif [[ -n $running && $(cat .staging/ref 2>/dev/null || true) == "$AUTO_BRANCH" ]]; then
		echo "The $AUTO_BRANCH branch is gone from origin; taking staging down"
		down
	fi
}

down() {
	compose down
	rm -f .staging/deployed .staging/ref
	echo "Staging is down; .staging/data and the image stay for the next up"
}

status() {
	local commit
	commit=$(cat .staging/deployed 2>/dev/null || true)
	if [[ -z $commit ]]; then
		echo "Staging is down"
	else
		echo "Staging was brought up on $(cat .staging/ref 2>/dev/null || echo '?') ($commit)"
	fi
	compose ps
	echo "/version answers: $(curl -s --max-time 5 http://127.0.0.1:44595/version || echo 'nothing')"
}

# The commit $1 names on origin: a branch there first, then anything git resolves (a commit, a tag).
resolve() {
	git rev-parse --verify --quiet "origin/$1^{commit}" 2>/dev/null && return 0
	git rev-parse --verify --quiet "$1^{commit}" 2>/dev/null
}

# .staging/src at commit $1: a worktree of this checkout, made at the first up and moved after.
checkout() {
	if [[ ! -e .staging/src/.git ]]; then
		rm -rf .staging/src
		git worktree prune
		git worktree add --quiet --detach .staging/src "$1"
	else
		git -C .staging/src checkout --quiet --detach "$1"
	fi
}

# The staging data folder, made once from the live one: the cache and the files at the top of data/, nothing from
# its folders (saves, accounts). Copied without their owner, as the live files are the container's (root's).
prepare_data() {
	[[ -e .staging/data/cache ]] && return 0
	if [[ ! -d data/cache ]]; then
		echo "No cache in data/cache to make the staging data folder from (the top README says how to get one)"
		return 1
	fi
	echo "Making .staging/data from the live data folder: the cache and its files, no saves or accounts"
	rm -rf .staging/data/cache.new
	mkdir -p .staging/data
	cp -a --no-preserve=ownership data/cache .staging/data/cache.new
	find data -maxdepth 1 -type f -exec cp -a --no-preserve=ownership {} .staging/data/ \;
	mv .staging/data/cache.new .staging/data/cache
}

# The build the staging data's cache-source.txt records ("openrs2 runescape/2740 build 241.3 2026-..."); fails without.
cache_have() {
	local have
	have=$(sed -n 's/.* build \([^ ]*\).*/\1/p' .staging/data/cache-source.txt 2>/dev/null)
	[[ -n $have ]] && echo "$have"
}

main "$@"; exit
