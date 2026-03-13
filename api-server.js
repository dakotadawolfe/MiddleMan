/**
 * MiddleMan API proxy: listens on 8765 and forwards to the agent on 8766.
 * Edit this file to add custom endpoints or transform responses, then restart:
 *   node api-server.js
 * No need to restart RuneLite when you change the API.
 */
const http = require('http');

const PROXY_PORT = 8765;
const AGENT_HOST = '127.0.0.1';
const AGENT_PORT = 8766;

const server = http.createServer((clientReq, clientRes) => {
  const path = clientReq.url;
  const opts = {
    hostname: AGENT_HOST,
    port: AGENT_PORT,
    path: path,
    method: clientReq.method,
    headers: clientReq.headers
  };
  const proxy = http.request(opts, (agentRes) => {
    clientRes.writeHead(agentRes.statusCode, agentRes.headers);
    agentRes.pipe(clientRes);
  });
  proxy.on('error', (err) => {
    clientRes.writeHead(502, { 'Content-Type': 'application/json' });
    clientRes.end(JSON.stringify({ error: 'Agent unreachable (is RuneLite running with MiddleMan?)', detail: err.message }));
  });
  clientReq.pipe(proxy);
});

server.listen(PROXY_PORT, '127.0.0.1', () => {
  console.log('MiddleMan API proxy: http://127.0.0.1:' + PROXY_PORT + ' -> agent :' + AGENT_PORT);
  console.log('Edit api-server.js and restart this process to change the API; RuneLite can stay running.');
});
