#!/usr/bin/env bash
# Tests staging.sh on a made-up origin, with a stand-in for docker that only logs what it is asked to do: which ref
# resolves to what, what is built and started with which commit, what the staging data folder is made from, and what
# the state files say after each command. Needs bash, git and flock, as the script does; CI runs it on every push.
#
#   deploy/staging-test.sh
set -euo pipefail

script=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/staging.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com
export GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1

# docker: logs each call with the OSRSPS_COMMIT it got. A build fails while $tmp/fail-build exists, and writes the
# "build output" the script shows on a failure; a fetch-cache writes the cache-source.txt the real one would, into the
# data folder under the --project-directory it was given.
mkdir "$tmp/bin"
cat >"$tmp/bin/docker" <<'EOF2'
#!/usr/bin/env bash
echo "docker $* OSRSPS_COMMIT=${OSRSPS_COMMIT:-}" >>"$DOCKER_LOG"
if [[ " $* " == *" build "* ]]; then
	echo "step 1/9: FROM eclipse-temurin"
	if [[ -e $FAIL_BUILD ]]; then echo "Compilation error: Unresolved reference"; exit 1; fi
fi
if [[ " $* " == *" fetch-cache "* ]]; then
	project=
	for ((i = 1; i <= $#; i++)); do [[ ${!i} == --project-directory ]] && project=${@:i+1:1}; done
	mkdir -p "$project/data"
	echo "openrs2 runescape/1 build ${*: -1} 2026-01-01T00:00:00Z" >"$project/data/cache-source.txt"
fi
EOF2
chmod +x "$tmp/bin/docker"
export PATH=$tmp/bin:$PATH DOCKER_LOG=$tmp/docker.log FAIL_BUILD=$tmp/fail-build

# origin is a bare repo; changes are made in dev and pushed, and the script runs in vm, VM 214's live checkout.
git init -q --bare -b main "$tmp/origin.git"
git init -q -b main "$tmp/dev"
git -C "$tmp/dev" remote add origin "$tmp/origin.git"
mkdir -p "$tmp/dev/deploy" "$tmp/dev/src/main"
cp "$script" "$tmp/dev/deploy/staging.sh"
printf '/data/\n/.deploy/\n/.staging/\n' >"$tmp/dev/.gitignore"

# Writes each "path=content" into dev, commits and pushes it to the branch dev is on; the new commit is in $head.
push() {
	local change
	for change in "$@"; do
		mkdir -p "$(dirname "$tmp/dev/${change%%=*}")"
		echo "${change#*=}" >"$tmp/dev/${change%%=*}"
	done
	git -C "$tmp/dev" add -A
	git -C "$tmp/dev" commit -q -m "change $*"
	git -C "$tmp/dev" push -q origin HEAD
	head=$(git -C "$tmp/dev" rev-parse HEAD)
}

failures=0
step=
# Runs the script in vm with these arguments; its exit code is in $status and its output in $tmp/out.
staging() {
	: >"$DOCKER_LOG"
	status=0
	(cd "$tmp/vm" && deploy/staging.sh "$@") >"$tmp/out" 2>&1 || status=$?
}
fail() {
	echo "FAIL ($step): $*"
	sed 's/^/  script: /' "$tmp/out"
	sed 's/^/  /' "$DOCKER_LOG"
	failures=$((failures + 1))
}
calls() { grep -c -- "$1" "$DOCKER_LOG" || true; }
expect_calls() { [[ $(calls "$1") == "$2" ]] || fail "expected $2 docker call(s) with '$1', got $(calls "$1")"; }
expect_no_calls() { [[ ! -s $DOCKER_LOG ]] || fail "expected no docker calls"; }
expect_status() { [[ $status == "$1" ]] || fail "exit code $status, not $1"; }
expect_output() { grep -q -- "$1" "$tmp/out" || fail "the output doesn't say '$1'"; }
expect_state() {
	local have
	have=$(cat "$tmp/vm/.staging/$1" 2>/dev/null || echo none)
	[[ $have == "$2" ]] || fail ".staging/$1 is $have, not $2"
}
expect_worktree() {
	[[ $(git -C "$tmp/vm/.staging/src" rev-parse HEAD 2>/dev/null) == "$1" ]] || fail ".staging/src isn't at $1"
}
compose="compose -p osrsps-staging --project-directory .staging -f deploy/compose.staging.yaml"

step="no ref"
push src/main/Server.kt=1
main1=$head
git clone -q "$tmp/origin.git" "$tmp/vm"
staging up
expect_status 64
expect_no_calls
staging
expect_status 64

step="a ref origin doesn't have"
staging up nosuch
expect_status 1
expect_output "No branch or commit named nosuch"
expect_no_calls
expect_state deployed none

step="a branch, before the live checkout has a cache"
git -C "$tmp/dev" checkout -q -b feature
push src/main/Server.kt=feature1
feature1=$head
staging up feature
expect_status 1
expect_output "No cache in data/cache"
expect_no_calls

step="a branch"
mkdir -p "$tmp/vm/data/cache" "$tmp/vm/data/saves" "$tmp/vm/data/accounts"
echo cache >"$tmp/vm/data/cache/main_file_cache.dat2"
echo key >"$tmp/vm/data/rsa.key"
echo words >"$tmp/vm/data/dialogue.txt"
echo "openrs2 runescape/1 build 241 2026-01-01T00:00:00Z" >"$tmp/vm/data/cache-source.txt"
echo player >"$tmp/vm/data/saves/test.json"
echo account >"$tmp/vm/data/accounts/test.json"
staging up feature
expect_status 0
expect_calls "docker $compose build OSRSPS_COMMIT=$feature1" 1
expect_calls "docker $compose up -d --force-recreate OSRSPS_COMMIT=$feature1" 1
expect_calls "fetch-cache" 0
expect_worktree "$feature1"
expect_state deployed "$feature1"
expect_state ref feature
expect_output "Staging runs feature ($feature1) at 192.168.0.210:44594"
[[ -s $tmp/vm/.staging/build.log ]] || fail "the build's output isn't in .staging/build.log"
[[ $(cat "$tmp/vm/.staging/data/cache/main_file_cache.dat2") == cache ]] || fail "the cache wasn't copied"
[[ $(cat "$tmp/vm/.staging/data/rsa.key") == key ]] || fail "the key wasn't copied"
[[ $(cat "$tmp/vm/.staging/data/dialogue.txt") == words ]] || fail "the words weren't copied"
[[ ! -e $tmp/vm/.staging/data/saves ]] || fail "the live saves were copied"
[[ ! -e $tmp/vm/.staging/data/accounts ]] || fail "the live accounts were copied"
[[ ! -e $tmp/vm/.staging/data/cache.new ]] || fail "cache.new was left behind"
[[ -e $tmp/vm/data/saves/test.json && ! -e $tmp/vm/data/.staging ]] || fail "the live data folder changed"
[[ $(git -C "$tmp/vm" rev-parse HEAD) == "$main1" ]] || fail "the live checkout moved"

step="the same branch again"
staging up feature
expect_status 0
expect_no_calls
expect_output "already"
expect_state deployed "$feature1"

step="--force"
staging up feature --force
expect_status 0
expect_calls "build OSRSPS_COMMIT=$feature1" 1
expect_calls "up -d --force-recreate OSRSPS_COMMIT=$feature1" 1

step="the branch moved"
push src/main/Server.kt=feature2
feature2=$head
staging up feature
expect_status 0
expect_calls "build OSRSPS_COMMIT=$feature2" 1
expect_worktree "$feature2"
expect_state deployed "$feature2"
expect_output "up now: $feature1"
[[ $(cat "$tmp/vm/.staging/data/cache/main_file_cache.dat2") == cache ]] || fail "the staging data was remade"

step="a commit by its hash"
staging up "$feature1"
expect_status 0
expect_calls "build OSRSPS_COMMIT=$feature1" 1
expect_worktree "$feature1"
expect_state ref "$feature1"

step="main"
staging up main
expect_status 0
expect_calls "build OSRSPS_COMMIT=$main1" 1
expect_worktree "$main1"

step="status"
staging status
expect_status 0
expect_output "brought up on main ($main1)"
expect_calls "$compose ps" 1

step="a branch wanting another cache build"
push deploy/cache-build.txt=999
cache=$head
staging up feature
expect_status 0
expect_calls "$compose run --rm osrsps fetch-cache 999 OSRSPS_COMMIT=$" 1
expect_calls "up -d --force-recreate OSRSPS_COMMIT=$cache" 1
expect_output "Fetching cache build 999 into the staging data (have: 241)"
grep -q "build 999" "$tmp/vm/.staging/data/cache-source.txt" || fail "the staging cache wasn't fetched"
grep -q "build 241" "$tmp/vm/data/cache-source.txt" || fail "the live cache-source.txt changed"

step="the cache build it has already"
staging up feature --force
expect_status 0
expect_calls "fetch-cache" 0

step="a build that fails"
touch "$FAIL_BUILD"
push src/main/Server.kt=broken
staging up feature
expect_status 1
expect_calls "up -d" 0
expect_output "Unresolved reference"
expect_output "FAILED; the whole output is in .staging/build.log"
expect_state deployed "$cache"
rm "$FAIL_BUILD"

step="the deploy lock held by a live build"
flock -x "$tmp/vm/.deploy/lock" sleep 3 &
sleep 0.5
STAGING_LOCK_WAIT=1 staging up feature
wait
expect_status 1
expect_output "try again"
expect_no_calls
expect_state deployed "$cache"

step="down"
staging down
expect_status 0
expect_calls "$compose down" 1
expect_state deployed none
expect_state ref none
[[ -e $tmp/vm/.staging/data/cache ]] || fail "the staging data went with it"
staging status
expect_output "Staging is down"

step="up after down"
staging up feature
expect_status 0
expect_calls "build OSRSPS_COMMIT=$head" 1
expect_state deployed "$head"

step="auto with no staging branch and staging up by hand"
byhand=$head
staging auto
expect_status 0
expect_no_calls
expect_state deployed "$byhand"
[[ ! -s $tmp/out ]] || fail "auto said something with nothing to do"

step="auto when the staging branch appears"
git -C "$tmp/dev" checkout -q -b staging
push src/main/Server.kt=staging1
staging1=$head
staging auto
expect_status 0
expect_calls "build OSRSPS_COMMIT=$staging1" 1
expect_calls "up -d --force-recreate OSRSPS_COMMIT=$staging1" 1
expect_worktree "$staging1"
expect_state deployed "$staging1"
expect_state ref staging

step="auto with nothing new"
staging auto
expect_status 0
expect_no_calls
[[ ! -s $tmp/out ]] || fail "auto said something with nothing to do"

step="auto when the staging branch moves"
push src/main/Server.kt=staging2
staging auto
expect_status 0
expect_calls "build OSRSPS_COMMIT=$head" 1
expect_state deployed "$head"
staging2=$head

step="auto when the build fails: tried once, until the branch moves on"
touch "$FAIL_BUILD"
push src/main/Server.kt=staging3
staging auto
expect_status 1
expect_output "FAILED; it is tried again when the branch moves on"
expect_state failed "$head"
expect_state deployed "$staging2"
staging auto
expect_status 0
expect_no_calls
rm "$FAIL_BUILD"
push src/main/Server.kt=staging4
staging auto
expect_status 0
expect_calls "build OSRSPS_COMMIT=$head" 1
expect_state deployed "$head"
expect_state failed none

step="auto brings staging back up after a down by hand while the branch is there"
staging down
staging auto
expect_status 0
expect_calls "build OSRSPS_COMMIT=$head" 1
expect_state deployed "$head"

step="auto leaves staging brought up by hand on another ref while the branch is there"
staging up feature
expect_state ref feature
staging auto
expect_status 0
expect_calls "build OSRSPS_COMMIT=$head" 1
expect_state ref staging

step="auto takes staging down when the branch is deleted"
git -C "$tmp/dev" checkout -q feature
git -C "$tmp/dev" push -q origin --delete staging
staging auto
expect_status 0
expect_output "branch is gone"
expect_calls "$compose down" 1
expect_state deployed none
staging auto
expect_status 0
expect_no_calls

step="auto leaves staging brought up by hand alone when the branch is gone"
staging up feature
staging auto
expect_status 0
expect_no_calls
expect_state ref feature

step="auto while another auto runs"
flock -x "$tmp/vm/.staging/lock" sleep 2 &
sleep 0.5
staging auto
wait
expect_status 0
expect_output "Another staging run is going"
expect_no_calls

if ((failures)); then
	echo "$failures check(s) failed"
	exit 1
fi
echo "staging.sh: all checks passed"
