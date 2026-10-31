import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseProfile } from './viewer.mjs';

test('decodes the Java v1 format including Unicode and reserved characters', () => {
  const profile = parseProfile('#v=1&n=Rithush%20%26%20Co&b=Hi%20%2B%20%F0%9F%91%8B&l=GitHub&u=https%3A%2F%2Fgithub.com%2Frvi');
  assert.equal(profile.name, 'Rithush & Co');
  assert.equal(profile.bio, 'Hi + 👋');
  assert.deepEqual(profile.links, [{ label: 'GitHub', url: 'https://github.com/rvi' }]);
});

test('preserves multiple links and their order', () => {
  const profile = parseProfile('#v=1&n=R&l=One&u=https%3A%2F%2Fa.ch&l=Two&u=https%3A%2F%2Fb.ch');
  assert.deepEqual(profile.links.map(link => link.label), ['One', 'Two']);
});

test('rejects incomplete, unsafe and unsupported data', () => {
  for (const hash of ['#v=2&n=R', '#v=1&n=R&l=X', '#v=1&n=R&n=S&l=X&u=https://a.ch',
    ...['javascript:alert(1)', 'http://a.ch', 'https://user:pass@a.ch', '//a.ch',
      'https://a.ch/\\evil'].map(url => '#v=1&n=R&l=X&u=' + encodeURIComponent(url))]) {
    assert.throws(() => parseProfile(hash));
  }
  assert.throws(() => parseProfile('#' + 'x'.repeat(4097)));
  assert.equal(parseProfile(''), null);
});

test('keeps HTML-looking content as plain data', () => {
  assert.equal(parseProfile('#v=1&n=%3Cscript%3E&l=%3Cb%3EX%3C%2Fb%3E&u=https://a.ch').name, '<script>');
});
