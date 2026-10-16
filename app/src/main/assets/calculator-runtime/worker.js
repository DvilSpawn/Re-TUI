/* APK-owned adapter appended after the verified Emscripten glue in a dedicated worker. */
(function () {
  'use strict';
  let evaluate;
  self.fetch = () => Promise.reject(new Error('Calculator network access disabled'));
  self.importScripts = () => { throw new Error('Calculator imports disabled'); };
  self.XMLHttpRequest = undefined;
  self.WebSocket = undefined;
  self.onmessage = async event => {
    const request = event.data;
    try {
      if (request.type === 'initialize' && !evaluate) {
        const module = await createQalculateModule({
          wasmBinary: request.binary, noInitialRun: true,
          locateFile: () => '/blocked', print: () => {}, printErr: () => {}
        });
        evaluate = module.cwrap('retui_evaluate', 'string', ['string', 'number', 'number', 'number']);
        self.postMessage({type: 'ready'});
      } else if (request.type === 'evaluate' && evaluate) {
        const result = JSON.parse(evaluate(request.expression, request.degrees ? 1 : 0,
          request.exact ? 1 : 0, request.preview ? 1 : 0));
        self.postMessage({type: 'result', id: request.id, result});
      } else throw new Error('Calculator protocol error');
    } catch (_) { self.postMessage({type: 'unavailable'}); }
  };
})();
