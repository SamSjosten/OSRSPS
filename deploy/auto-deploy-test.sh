#!/usr/bin/env bash
# Tests auto-deploy.sh on a made-up main branch, with a stand-in for docker that only logs what it is asked to do:
# which merges rebuild and restart the server, which leave it running, and what the state files the server's /version
# reads say after each. Needs bash, git and flock, as the script does; CI runs it on every push.
#
#   deploy/auto-deploy-test.sh
set -euo pipefail

script=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auto-deploy.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1

# docker: logs each call with the OSRSPS_COMMIT it got and what .deploy/built said then. A build fails while
# $tmp/fail-build exists, and an up while $tmp/fail-up does; a fetch-cache writes the cache-source.txt the real one
# would.
mkdir "$tmp/bin"
cat >"$tmp/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "docker $* OSRSPS_COMMIT=${OSRSPS_COMMIT:-} built=$(cat .deploy/built 2>/dev/null)" >>"$DOCKER_LOG"
if [[ " $* " == *" build "* && -e $FAIL_BUILD ]]; then exit 1; fi
if [[ " $* " == *" up "* && -e $FAIL_UP ]]; then exit 1; fi
if [[ " $* " == *" fetch-cache "* ]]; then
	mkdir -p data
	echo "openrs2 runescape/1 build ${*: -1} 2026-01-01T00:00:00Z" >data/cache-source.txt
fi
EOF
chmod +x "$tmp/bin/docker"
export PATH=$tmp/bin:$PATH DOCKER_LOG=$tmp/docker.log FAIL_BUILD=$tmp/fail-build FAIL_UP=$tmp/fail-up

# main lives in a bare repo; changes are made in dev and pushed, and the script runs in vm, VM 214's checkout.
git init -q --bare -b main "$tmp/origin.git"
git init -q -b main "$tmp/dev"
git -C "$tmp/dev" remote add origin "$tmp/origin.git"
mkdir -p "$tmp/dev/deploy" "$tmp/dev/src/main"
cp "$script" "$tmp/dev/deploy/auto-deploy.sh"
printf '/data/\n/.deploy/\n' >"$tmp/dev/.gitignore"

# Writes each "path=content" into dev, commits and pushes it to main; the new commit is in $head.
merge() {
	local change
	for change in "$@"; do
		mkdir -p "$(dirname "$tmp/dev/${change%%=*}")"
		echo "${change#*=}" >"$tmp/dev/${change%%=*}"
	done
	git -C "$tmp/dev" add -A
	git -C "$tmp/dev" commit -q -m "change $*"
	git -C "$tmp/dev" push -q origin main
	head=$(git -C "$tmp/dev" rev-parse HEAD)
}

failures=0
step=
# Runs the script in vm with these arguments; its exit code is in $status.
deploy() {
	: >"$DOCKER_LOG"
	status=0
	(cd "$tmp/vm" && deploy/auto-deploy.sh "$@") >"$tmp/out" 2>&1 || status=$?
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
expect_state() {
	local have
	have=$(cat "$tmp/vm/.deploy/$1" 2>/dev/null || echo none)
	[[ $have == "$2" ]] || fail ".deploy/$1 is $have, not $2"
}
expect_status() { [[ $status == "$1" ]] || fail "exit code $status, not $1"; }
expect_checkout() { [[ $(git -C "$tmp/vm" rev-parse HEAD) == "$1" ]] || fail "the checkout isn't at $1"; }

step="first deploy"
merge src/main/Server.kt=1
git clone -q "$tmp/origin.git" "$tmp/vm"
deploy
first=$head
expect_status 0
expect_calls "compose build OSRSPS_COMMIT=$first" 1
# The server is restarted with built empty: it is known again once the new server is up.
expect_calls "compose up -d OSRSPS_COMMIT=$first built=$" 1
expect_state deployed "$first"
expect_state built "$first"

step="nothing new"
deploy
expect_status 0
expect_no_calls

step="docs, the friend page, tests, plugins, launcher, CI and deploy files only, on a checkout from before .deploy/built"
rm "$tmp/vm/.deploy/built"
merge README.md=1 docs/index.html=1 src/test/ServerTest.kt=1 test-runner/Plugin.java=1 tick-recorder/Plugin.java=1 \
	launcher/Main.java=1 .github/workflows/build.yml=1 deploy/README.md=1
deploy
expect_status 0
expect_no_calls
expect_state deployed "$head"
expect_state built "$first"
expect_checkout "$head"
grep -q "without a restart" "$tmp/out" || fail "the output doesn't say the server kept running"

step="a second merge like that"
merge test-runner/Plugin.java=2
deploy
expect_status 0
expect_no_calls
expect_state deployed "$head"
expect_state built "$first"

step="a server change"
merge src/main/Server.kt=2
deploy
expect_status 0
expect_calls "compose build OSRSPS_COMMIT=$head" 1
expect_calls "compose up -d OSRSPS_COMMIT=$head" 1
expect_state deployed "$head"
expect_state built "$head"

step="a doc in src/main is part of the server"
merge src/main/resources/README.md=1
deploy
expect_calls "compose build OSRSPS_COMMIT=$head" 1
expect_state built "$head"

step="a file moved out of the server"
git -C "$tmp/dev" mv src/main/Server.kt test-runner/Server.kt
merge test-runner/Plugin.java=3
deploy
expect_calls "compose build OSRSPS_COMMIT=$head" 1
expect_state built "$head"
good=$head

step="a server change whose build fails"
touch "$FAIL_BUILD"
merge src/main/World.kt=1
broken=$head
deploy
expect_status 1
expect_calls "compose up" 0
expect_state failed "$broken"
expect_state deployed "$good"
expect_state built "$good"

step="a docs-only merge after it still builds the failed change"
rm "$FAIL_BUILD"
merge README.md=2
deploy
expect_status 0
expect_calls "compose build OSRSPS_COMMIT=$head" 1
expect_calls "compose up -d OSRSPS_COMMIT=$head" 1
expect_state deployed "$head"
expect_state built "$head"
expect_state failed none
built=$head

step="a merge with no changes at all"
git -C "$tmp/dev" commit -q --allow-empty -m "no changes"
git -C "$tmp/dev" push -q origin main
head=$(git -C "$tmp/dev" rev-parse HEAD)
deploy
expect_status 0
expect_no_calls
expect_state deployed "$head"
expect_state built "$built"

step="a new cache build, with no server change"
merge deploy/cache-build.txt=999
deploy
expect_status 0
expect_calls "compose build" 0
expect_calls "compose run --rm osrsps fetch-cache 999" 1
# The server is restarted on the new cache, still as the build it is, so /version keeps reading the newer commit.
expect_calls "compose up -d --force-recreate OSRSPS_COMMIT=$built" 1
expect_state deployed "$head"
expect_state built "$built"

step="a server change whose server doesn't start"
touch "$FAIL_UP"
up=$head
merge src/main/World.kt=2
deploy
expect_status 1
expect_calls "compose build OSRSPS_COMMIT=$head" 1
expect_calls "compose up -d OSRSPS_COMMIT=$head" 1
expect_state failed "$head"
expect_state deployed "$up"
# The image is the build that didn't start, and the old server may be gone with it.
expect_state built ""

step="its revert builds and restarts the server, though the server's files are back to the running build's"
rm "$FAIL_UP"
git -C "$tmp/dev" revert --no-edit HEAD >/dev/null
git -C "$tmp/dev" push -q origin main
head=$(git -C "$tmp/dev" rev-parse HEAD)
deploy
expect_status 0
expect_calls "compose build OSRSPS_COMMIT=$head" 1
expect_calls "compose up -d OSRSPS_COMMIT=$head" 1
expect_state deployed "$head"
expect_state built "$head"
expect_state failed none
built=$head

step="a new cache build whose server doesn't start"
touch "$FAIL_UP"
merge deploy/cache-build.txt=1000
deploy
expect_status 1
expect_calls "compose build" 0
expect_calls "compose up -d --force-recreate OSRSPS_COMMIT=$built built=$" 1
expect_state built ""

step="a docs merge after it builds and restarts the server"
rm "$FAIL_UP"
merge README.md=3
deploy
expect_status 0
expect_calls "compose build OSRSPS_COMMIT=$head" 1
expect_calls "compose up -d OSRSPS_COMMIT=$head" 1
expect_state built "$head"

step="a server change with a new cache: built is empty from the build on"
merge src/main/World.kt=3 deploy/cache-build.txt=1001
deploy
expect_status 0
expect_calls "fetch-cache 1001 OSRSPS_COMMIT= built=$" 1
expect_calls "compose up -d --force-recreate OSRSPS_COMMIT=$head built=$" 1
expect_state built "$head"

step="--force rebuilds a docs-only merge"
merge README.md=4
deploy --force
expect_status 0
expect_calls "compose build OSRSPS_COMMIT=$head" 1
expect_calls "compose up -d OSRSPS_COMMIT=$head" 1
expect_state built "$head"

if ((failures)); then
	echo "$failures check(s) failed"
	exit 1
fi
echo "auto-deploy.sh: all checks passed"
