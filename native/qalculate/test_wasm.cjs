// SPDX-License-Identifier: GPL-3.0-or-later
// Run the actual current-version binary, not a mock of the calculator protocol.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const engine = path.resolve(__dirname, '../../app/build/qalculate/wasm/dist');
const create = require(path.join(engine, 'engine.js'));
(async () => {
  const module = await create({ wasmBinary: fs.readFileSync(path.join(engine, 'engine.wasm')) });
  const evaluate = module.cwrap('retui_evaluate', 'string', ['string', 'number', 'number', 'number']);
  function calc(expression, degrees = 0, exact = 0, preview = 0) {
    const result = JSON.parse(evaluate(expression, degrees, exact, preview));
    assert.equal(typeof result.text, 'string');
    assert.equal(typeof result.parsed, 'string');
    return result;
  }
  assert.equal(calc('2+2').text, '4');
  assert.equal(calc('1/3', 0, 1).text.replaceAll(' ', ''), '1/3');
  assert.equal(calc('sin(30)', 1, 1).text.replaceAll(' ', ''), '1/2');
  const approximateSin = calc('sin(30)', 1);
  assert.equal(approximateSin.status, 2);
  assert.ok(approximateSin.text === '0.5' || /^interval\(0\.4999[0-9]+, 0\.5000[0-9]+\)$/.test(approximateSin.text));
  assert.equal(calc('2 m to cm').text.replaceAll(' ', ''), '200cm');
  assert.equal(calc('2×3', 0, 1).text, '6');
  assert.equal(calc('6÷2', 0, 1).text, '3');
  assert.equal(calc('4−2', 0, 1).text, '2');
  assert.equal(calc('5 ft + 10 in', 0, 1).text, '5 ft + 10 in');
  assert.equal(calc('retuivar:=7', 0, 1).text, '7');
  assert.equal(calc('retuivar+1', 0, 1).text, '8');
  assert.equal(calc('2+*3').status, 1);
  assert.equal(calc('1/0').status, 1);
  assert.equal(calc('1/0', 0, 1).status, 1);
  const preview = calc('2+2', 0, 0, 1);
  assert.equal(preview.text, '4');
  assert.ok(preview.parsed.length);
  assert.equal(calc('x=2', 0, 0, 1).status, 3);
  assert.equal(calc('factorial(200000)', 0, 0, 1).status, 3);
  assert.equal(calc('', 0, 0).status, 1);
  console.log('PASS: actual Qalculate5.8.2 WASM arithmetic, fractions, degrees, units, Unicode operators, variables, errors, safe preview and input bounds');
})().catch(error => { console.error(error); process.exitCode = 1; });
