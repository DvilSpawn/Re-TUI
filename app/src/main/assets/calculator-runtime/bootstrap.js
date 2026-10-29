/* APK-owned host: the only Android connection is a calculator result MessagePort. */
(function () {
  'use strict';
  let port, worker;
  const send = value => port.postMessage(JSON.stringify(value));
  window.onmessage = async event => {
    // Android's native postWebMessage sender may have an empty/opaque origin.
    // The private page cannot navigate or frame external content; native targets this exact origin.
    if (![location.origin, '', 'null'].includes(event.origin) || event.data !== 'connect' || port || event.ports.length !== 1) return;
    port = event.ports[0];
    port.onmessage = event => {
      try {
        const request = JSON.parse(event.data);
        if (request.type !== 'evaluate' || typeof request.id !== 'string' ||
            typeof request.expression !== 'string' || request.expression.length > 1024 || !worker) throw new Error('Invalid request');
        worker.postMessage(request);
      } catch (_) { send({type: 'unavailable'}); }
    };
    try {
      if (typeof WebAssembly !== 'object' || typeof Worker !== 'function') throw new Error('WebAssembly unavailable');
      const load = async (name, binary) => {
        const response = await fetch('/' + name, {cache: 'no-store', credentials: 'omit'});
        if (!response.ok) throw new Error('Engine file unavailable');
        return binary ? response.arrayBuffer() : response.text();
      };
      const [glue, adapter, binary] = await Promise.all([load('engine.js'), load('worker.js'), load('engine.wasm', true)]);
      const url = URL.createObjectURL(new Blob([glue, '\n', adapter], {type: 'application/javascript'}));
      worker = new Worker(url);
      URL.revokeObjectURL(url);
      worker.onmessage = event => {
        if (typeof event.data === 'object' && event.data !== null) send(event.data);
      };
      worker.onerror = () => { worker.terminate(); worker = null; send({type: 'unavailable'}); };
      worker.postMessage({type: 'initialize', binary}, [binary]);
    } catch (_) { send({type: 'unavailable'}); }
  };
})();
