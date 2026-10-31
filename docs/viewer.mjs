/** Mirrors ProfileService's v1 fragment format. Never treat profile content as HTML. */
export function parseProfile(hash) {
  if (!hash || hash === '#') return null;
  if (hash.length > 4096) throw new Error('Profile link is too large.');
  const params = new URLSearchParams(hash.replace(/^#/, ''));
  const single = (key, required, limit) => {
    const values = params.getAll(key);
    if (values.length > 1) throw new Error('Invalid profile field.');
    const value = (values[0] ?? '').trim();
    if ((required && !value) || value.length > limit) throw new Error('Invalid profile field.');
    return value;
  };
  if (single('v', true, 1) !== '1') throw new Error('Unsupported profile version.');
  const name = single('n', true, 100);
  const bio = single('b', false, 200);
  const labels = params.getAll('l');
  const urls = params.getAll('u');
  if (!labels.length || labels.length > 10 || labels.length !== urls.length)
    throw new Error('Invalid profile links.');
  const links = labels.map((label, index) => {
    if (!label.trim() || label.trim().length > 60) throw new Error('Invalid link label.');
    // Require an absolute HTTPS URL; reject userinfo and backslashes before browser normalization.
    const raw = urls[index];
    if (!/^https:\/\//i.test(raw) || /[\\\u0000-\u0020]/.test(raw) || raw.length > 2048)
      throw new Error('Invalid link URL.');
    const url = new URL(raw);
    if (url.protocol !== 'https:' || !url.hostname || url.username || url.password)
      throw new Error('Invalid link URL.');
    return { label: label.trim(), url: url.href };
  });
  return { name, bio, links };
}

export function renderProfile(root, hash) {
  const element = (tag, text, className) => {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) node.className = className;
    return node;
  };
  root.replaceChildren(element('p', 'SOCIALCARD', 'eyebrow'));
  document.title = 'SocialCard';
  try {
    const profile = parseProfile(hash);
    if (!profile) {
      root.append(element('h1', 'Your profile, one tap away.'),
        element('p', 'Open the full link from a SocialCard to see its profile.'));
      return;
    }
    root.append(element('h1', profile.name));
    if (profile.bio) root.append(element('p', profile.bio));
    const nav = document.createElement('nav');
    nav.setAttribute('aria-label', 'Profile links');
    for (const link of profile.links) {
      const anchor = element('a', link.label);
      anchor.href = link.url;
      anchor.target = '_blank';
      anchor.rel = 'noopener noreferrer';
      nav.append(anchor);
    }
    root.append(nav);
    root.append(element('p', 'Profile content is supplied by the card owner. Check link destinations before opening.'));
  } catch {
    root.append(element('h1', 'This profile could not be opened.'),
      element('p', 'The link may be incomplete or use an unsupported format. Ask the card owner to rewrite it.'));
  }
}

if (typeof document !== 'undefined') {
  const root = document.getElementById('profile');
  const update = () => renderProfile(root, window.location.hash);
  update();
  window.addEventListener('hashchange', update);
}
