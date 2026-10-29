// The odinsgate probe, run every 15 minutes by .github/workflows/probe.yml: is the server reachable the way a friend
// reaches it, through the relay at odinsgate.net? /status.json on the HTTP port must answer our own JSON with
// "up": true, and the game port must accept a connection. Both are tried again a few times, half a minute apart,
// before the server counts as down, so the restart after a deploy (a minute or two) raises no alarm. Prints what it
// saw on each try; exits 1 when still down after the last. With GITHUB_OUTPUT set it also writes `up` and `detail`
// for the alert step (.github/scripts/alert.js).
//
//   node .github/scripts/probe.js http://odinsgate.net:43595/status.json odinsgate.net:43594 [tries] [delayMs]

const net = require('node:net');
const fs = require('node:fs');

const DEFAULT_TRIES = 8;
const DEFAULT_DELAY_MS = 30000;
const DEFAULT_TIMEOUT_MS = 10000;

const wait = ms => new Promise(resolve => setTimeout(resolve, ms));

// GET the status page: { ok, detail }. A page that isn't ours (a proxy's error page, say) is no answer.
async function status(url, timeoutMs) {
  let response;
  try {
    response = await fetch(url, { signal: AbortSignal.timeout(timeoutMs), redirect: 'manual' });
  } catch (error) {
    return { ok: false, detail: `${url}: ${error.cause ? error.cause.message : error.message}` };
  }
  if (response.status !== 200) return { ok: false, detail: `${url} answered ${response.status}` };
  let body;
  try {
    body = await response.json();
  } catch {
    return { ok: false, detail: `${url} didn't answer with our status JSON` };
  }
  if (!body || typeof body !== 'object' || typeof body.up !== 'boolean') {
    return { ok: false, detail: `${url} didn't answer with our status JSON` };
  }
  if (!body.up) return { ok: false, detail: `the server says it is not up (loading its cache, or restarting)` };
  const players = typeof body.players === 'number' ? `${body.players} player(s)` : 'players unknown';
  const logins = body.acceptingLogins === false ? ', not accepting logins (full)' : '';
  return { ok: true, detail: `the server is up at revision ${body.revision}, ${players}${logins}` };
}

// Connect to host:port and hang up: { ok, detail }.
function connect(hostPort, timeoutMs) {
  const at = hostPort.lastIndexOf(':');
  const host = hostPort.slice(0, at);
  const port = Number(hostPort.slice(at + 1));
  return new Promise(resolve => {
    const socket = net.connect({ host, port });
    const done = (ok, detail) => { socket.destroy(); resolve({ ok, detail }); };
    socket.setTimeout(timeoutMs, () => done(false, `${hostPort}: no connection within ${timeoutMs} ms`));
    socket.once('connect', () => done(true, `${hostPort} accepts connections`));
    socket.once('error', error => done(false, `${hostPort}: ${error.message}`));
  });
}

async function probe({ statusUrl, game, tries = DEFAULT_TRIES, delayMs = DEFAULT_DELAY_MS,
  timeoutMs = DEFAULT_TIMEOUT_MS, log = () => {}, sleep = wait }) {
  let detail = '';
  for (let attempt = 1; attempt <= tries; attempt++) {
    const [page, port] = await Promise.all([status(statusUrl, timeoutMs), connect(game, timeoutMs)]);
    detail = `${page.detail}; ${port.detail}`;
    log(`try ${attempt}/${tries}: ${detail}`);
    if (page.ok && port.ok) return { up: true, detail, tries: attempt };
    if (attempt < tries) await sleep(delayMs);
  }
  return { up: false, detail: `down after ${tries} tries: ${detail}`, tries };
}

module.exports = probe;
module.exports.status = status;
module.exports.connect = connect;

if (require.main === module) {
  const [statusUrl, game, tries, delayMs] = process.argv.slice(2);
  if (!statusUrl || !game) {
    console.error('usage: node probe.js <status url> <game host:port> [tries] [delay ms]');
    process.exit(2);
  }
  probe({
    statusUrl, game,
    tries: tries ? Number(tries) : DEFAULT_TRIES,
    delayMs: delayMs ? Number(delayMs) : DEFAULT_DELAY_MS,
    log: line => console.log(line),
  }).then(result => {
    console.log(result.up ? `up: ${result.detail}` : result.detail);
    if (process.env.GITHUB_OUTPUT) {
      fs.appendFileSync(process.env.GITHUB_OUTPUT, `up=${result.up}\ndetail=${result.detail.replace(/\r?\n/g, ' ')}\n`);
    }
    process.exit(result.up ? 0 : 1);
  });
}
