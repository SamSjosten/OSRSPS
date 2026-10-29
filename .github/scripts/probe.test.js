// node --test .github/scripts/*.test.js : the odinsgate probe against a status page and a game port on loopback.
const { test, before } = require('node:test');
const assert = require('node:assert');
const http = require('node:http');
const net = require('node:net');
const probe = require('./probe.js');

const UP = '{"up":true,"revision":241,"commit":"abc","players":3,"acceptingLogins":true,"js5Connections":7}';

// A status page whose body (and code) the test changes as it goes, and a game port that accepts.
async function fakeServer() {
  let body = UP;
  let code = 200;
  const web = http.createServer((request, response) => {
    response.writeHead(code, { 'content-type': 'application/json' });
    response.end(body);
  });
  await new Promise(resolve => web.listen(0, '127.0.0.1', resolve));
  const game = net.createServer(socket => socket.end());
  await new Promise(resolve => game.listen(0, '127.0.0.1', resolve));
  return {
    statusUrl: `http://127.0.0.1:${web.address().port}/status.json`,
    game: `127.0.0.1:${game.address().port}`,
    set(newBody, newCode = 200) { body = newBody; code = newCode; },
    async close() {
      await new Promise(resolve => web.close(resolve));
      await new Promise(resolve => game.close(resolve));
    },
  };
}

// A port nothing listens on: taken, then given back.
async function deadPort() {
  const taken = net.createServer();
  await new Promise(resolve => taken.listen(0, '127.0.0.1', resolve));
  const port = taken.address().port;
  await new Promise(resolve => taken.close(resolve));
  return port;
}

// The first fetch in a process loads its implementation, which took seconds on a cold CI runner once and ate the
// first try's timeout (the try failed, and a test that expects one try saw two). Loaded here, before any test.
before(async () => {
  const server = await fakeServer();
  try {
    await fetch(server.statusUrl);
  } finally {
    await server.close();
  }
});

// A timeout only a fault could reach, so no test depends on how fast the runner is; the one test about the
// timeout passes its own short one.
function run(server, more = {}) {
  const log = [];
  return probe({ statusUrl: server.statusUrl, game: server.game, tries: 3, delayMs: 0,
    timeoutMs: 10000, log: line => log.push(line), sleep: async () => {}, ...more })
    .then(result => ({ ...result, log }));
}

test('a server that is up and accepting connections is up on the first try', async () => {
  const server = await fakeServer();
  try {
    const result = await run(server);
    assert.strictEqual(result.up, true);
    assert.strictEqual(result.tries, 1);
    assert.match(result.detail, /the server is up at revision 241, 3 player\(s\); 127\.0\.0\.1:\d+ accepts connections/);
    assert.strictEqual(result.log.length, 1);
  } finally {
    await server.close();
  }
});

test('a full server is still up', async () => {
  const server = await fakeServer();
  try {
    server.set(UP.replace('"acceptingLogins":true', '"acceptingLogins":false'));
    const result = await run(server);
    assert.strictEqual(result.up, true);
    assert.match(result.detail, /not accepting logins \(full\)/);
  } finally {
    await server.close();
  }
});

test('a server that says it is not up is tried again and counts as down after the last try', async () => {
  const server = await fakeServer();
  try {
    server.set(UP.replace('"up":true', '"up":false'));
    const slept = [];
    const result = await run(server, { sleep: async ms => slept.push(ms), delayMs: 123 });
    assert.strictEqual(result.up, false);
    assert.strictEqual(result.tries, 3);
    assert.match(result.detail, /^down after 3 tries: the server says it is not up/);
    assert.deepStrictEqual(slept, [123, 123], 'a pause between tries, none after the last');
    assert.strictEqual(result.log.length, 3);
  } finally {
    await server.close();
  }
});

test('a server that comes back during the tries is up', async () => {
  const server = await fakeServer();
  try {
    server.set('', 503);
    let sleeps = 0;
    const result = await run(server, { sleep: async () => { if (++sleeps === 1) server.set(UP); } });
    assert.strictEqual(result.up, true);
    assert.strictEqual(result.tries, 2);
    assert.match(result.log[0], /answered 503/);
  } finally {
    await server.close();
  }
});

test('a page that is not our status JSON is down', async () => {
  const server = await fakeServer();
  try {
    server.set('<html>a proxy error page</html>');
    let result = await run(server);
    assert.strictEqual(result.up, false);
    assert.match(result.detail, /didn't answer with our status JSON/);
    server.set('{"something":"else"}');
    result = await run(server);
    assert.strictEqual(result.up, false);
    assert.match(result.detail, /didn't answer with our status JSON/);
  } finally {
    await server.close();
  }
});

test('a game port that refuses connections is down even with the status page up', async () => {
  const server = await fakeServer();
  try {
    const result = await run(server, { game: `127.0.0.1:${await deadPort()}` });
    assert.strictEqual(result.up, false);
    assert.match(result.detail, /the server is up at revision 241.*; 127\.0\.0\.1:\d+: connect ECONNREFUSED/);
  } finally {
    await server.close();
  }
});

test('a relay that is gone is down with the reason', async () => {
  const server = await fakeServer();
  try {
    const result = await run(server, { statusUrl: `http://127.0.0.1:${await deadPort()}/status.json` });
    assert.strictEqual(result.up, false);
    assert.match(result.detail, /status\.json: .*ECONNREFUSED/);
  } finally {
    await server.close();
  }
});

test('a port that accepts but never answers the status request is down after the timeout', async () => {
  const held = new Set();
  const silent = net.createServer(socket => held.add(socket));
  await new Promise(resolve => silent.listen(0, '127.0.0.1', resolve));
  const server = await fakeServer();
  try {
    const result = await run(server, { statusUrl: `http://127.0.0.1:${silent.address().port}/status.json`, tries: 1,
      timeoutMs: 500 });
    assert.strictEqual(result.up, false);
    assert.match(result.detail, /status\.json: .*(timeout|aborted)/i);
  } finally {
    await server.close();
    for (const socket of held) socket.destroy();
    await new Promise(resolve => silent.close(resolve));
  }
});
