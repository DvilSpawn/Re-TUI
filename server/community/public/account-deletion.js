'use strict';
const form = document.getElementById('deletion');
const status = document.getElementById('status');
form.addEventListener('submit', async event => {
  event.preventDefault();
  const handle = form.elements.username.value.toLowerCase();
  const password = form.elements.password.value;
  form.elements.password.value = '';
  const button = form.querySelector('button'); button.disabled = true;
  async function post(path, body, token = '') {
    const response = await fetch('/community/api/' + path, {method:'POST', credentials:'omit', cache:'no-store', redirect:'error',
      headers:{'Content-Type':'application/json', ...(token ? {Authorization:token} : {})}, body:JSON.stringify(body)});
    if (!response.ok) throw new Error(response.status === 429 ? 'Rate limited. Wait and try again.' : 'Request failed. Check your credentials.');
    return response.json();
  }
  try {
    status.textContent = 'Verifying account…';
    const result = await post('community/account-delete', {password,confirm:handle});
    if (!result.deleted) throw new Error('Deletion was not confirmed.');
    status.textContent = 'Account deleted. Its authored content was removed. Moderation evidence and recovery-copy retention remain as described above.';
    form.reset(); form.hidden = true;
  } catch (error) {
    status.textContent = error instanceof TypeError ? 'Connection interrupted. Deletion may have completed; check by signing in before retrying.' : error.message;
  } finally { button.disabled = false; }
});
