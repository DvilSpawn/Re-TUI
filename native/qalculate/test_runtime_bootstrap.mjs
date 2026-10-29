#!/usr/bin/env node
// Protocol and trust-boundary checks for the APK-owned portable calculator host.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const assets = path.join(root, 'app/src/main/assets/calculator-runtime');
const read = name => fs.readFileSync(path.join(assets, name), 'utf8');

const replies = [], calls = [];
const worker = {postMessage: value => replies.push(value)};
const binary = new ArrayBuffer(8);
const workerContext = vm.createContext({self: worker, createQalculateModule: async config => {
  assert.equal(config.wasmBinary, binary);
  assert.equal(config.noInitialRun, true);
  return {cwrap: (name, output, inputs) => {
    assert.equal(name, 'retui_evaluate');
    assert.equal(output, 'string');
    assert.equal(inputs.join(','), 'string,number,number,number');
    return (...args) => { calls.push(args); return '{"status":2,"text":"0.5","parsed":"1 / 2"}'; };
  }};
}});
vm.runInContext(read('worker.js'), workerContext);
await worker.onmessage({data: {type: 'initialize', binary}});
assert.equal(replies.pop().type, 'ready');
await worker.onmessage({data: {type: 'evaluate', id: 'request', expression: '1/2', degrees: false, exact: true, preview: true}});
assert.deepEqual(calls.pop(), ['1/2', 0, 1, 1]);
assert.equal(replies[0].id, 'request');
assert.equal(replies[0].result.status, 2);
await assert.rejects(worker.fetch('https://example.com'));
assert.throws(() => worker.importScripts('https://example.com'));
assert.equal(worker.XMLHttpRequest, undefined);
assert.equal(worker.WebSocket, undefined);

const fetches = [], sent = [], workers = [];
class FakeWorker {
  constructor() { this.messages = []; workers.push(this); }
  postMessage(value) { this.messages.push(value); }
  terminate() { this.stopped = true; }
}
const page = {};
const port = {postMessage: value => sent.push(JSON.parse(value))};
const hostContext = vm.createContext({window: page, location: {origin: 'https://retui-calculator.invalid'},
  WebAssembly: {}, Worker: FakeWorker, Blob, URL: {createObjectURL: () => 'blob:calculator', revokeObjectURL: () => {}},
  fetch: async (url, options) => {
    assert.equal(options.credentials, 'omit');
    fetches.push(url);
    return {ok: true, text: async () => '', arrayBuffer: async () => new ArrayBuffer(8)};
  }});
vm.runInContext(read('bootstrap.js'), hostContext);
await page.onmessage({origin: 'https://untrusted.example', data: 'connect', ports: [port]});
assert.equal(fetches.length, 0, 'external sender must not initialize the runtime');
await page.onmessage({origin: '', data: 'connect', ports: [port]});
assert.deepEqual(fetches.sort(), ['/engine.js', '/engine.wasm', '/worker.js']);
assert.equal(workers.length, 1);
assert.equal(workers[0].messages[0].type, 'initialize');
port.onmessage({data: JSON.stringify({type: 'evaluate', id: 'bounded', expression: '2+2'})});
assert.equal(workers[0].messages[1].id, 'bounded');
port.onmessage({data: JSON.stringify({type: 'evaluate', id: 'oversized', expression: '2'.repeat(1025)})});
assert.equal(workers[0].messages.length, 2, 'oversized requests must not reach WASM');
assert.equal(sent.pop().type, 'unavailable');
workers[0].onerror();
assert.equal(workers[0].stopped, true);
assert.equal(sent.pop().type, 'unavailable');
console.log('Portable calculator bootstrap: protocol, bounded input, native port and network isolation passed.');
