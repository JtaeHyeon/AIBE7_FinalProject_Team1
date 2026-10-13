// 로그인·회원가입 화면 연동. 화면 HTML은 pages.js가 나중에 채워 넣으므로 document에 이벤트를 위임한다.
// Access Token은 메모리에만 둔다(새로고침 시 HttpOnly refresh 쿠키로 복구). Refresh Token은 JS에서 접근할 수 없다.
const auth = (() => {
  const EMAIL_KEY = 'savedEmail';
  let accessToken = null;
  let user = null;

  const safeStorage = {
    get: (key) => { try { return localStorage.getItem(key); } catch { return null; } },
    set: (key, value) => { try { localStorage.setItem(key, value); } catch { /* 저장소를 못 쓰는 환경 */ } },
    remove: (key) => { try { localStorage.removeItem(key); } catch { /* 저장소를 못 쓰는 환경 */ } },
  };

  const goto = (page) => document.querySelector(`[data-page="${page}"]`)?.click();

  const request = async (path, options = {}) => {
    const response = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      body: options.body ? JSON.stringify(options.body) : undefined,
    });
    if (response.status === 204) return null;
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error(payload?.message || '요청을 처리하지 못했어요.');
      error.status = response.status;
      error.fieldErrors = payload?.fieldErrors || [];
      throw error;
    }
    return payload?.data ?? null;
  };

  const renderHeader = () => {
    const box = document.querySelector('.landing-account');
    if (!box) return;
    box.replaceChildren();
    if (user) {
      const name = document.createElement('span');
      name.textContent = `${user.nickname}님`;
      const logout = document.createElement('button');
      logout.type = 'button';
      logout.dataset.authLogout = '';
      logout.textContent = '로그아웃';
      box.append(name, document.createTextNode(' '), logout);
    } else {
      const login = document.createElement('button');
      login.dataset.page = 'auth';
      login.textContent = '로그인';
      const bar = document.createElement('span');
      bar.textContent = '|';
      const signup = document.createElement('button');
      signup.dataset.page = 'signup';
      signup.textContent = '회원가입';
      box.append(login, bar, signup);
    }
  };

  const setSession = (data) => {
    accessToken = data.accessToken;
    user = data.user;
    renderHeader();
  };

  const clearSession = () => {
    accessToken = null;
    user = null;
    renderHeader();
  };

  const message = (form, text, kind = 'error') => {
    const box = form.querySelector('.au-msg');
    if (!box) return;
    box.textContent = text || '';
    box.hidden = !text;
    box.dataset.kind = kind;
  };

  const setBusy = (form, busy) => {
    const button = form.querySelector('button[type="submit"]');
    if (button) button.disabled = busy;
  };

  const passwordRules = (value) => ({
    length: value.length >= 8 && value.length <= 64,
    letter: /[A-Za-z]/.test(value),
    digit: /\d/.test(value),
    special: /[^A-Za-z\d]/.test(value) && /^[\x21-\x7E]+$/.test(value),
  });

  const handleLogin = async (form) => {
    const email = form.elements.email.value.trim();
    const password = form.elements.password.value;
    if (!email || !password) return message(form, '이메일과 비밀번호를 입력해 주세요.');
    setBusy(form, true);
    try {
      const data = await request('/api/auth/login', { body: { email, password } });
      if (form.elements.saveEmail.checked) safeStorage.set(EMAIL_KEY, email);
      else safeStorage.remove(EMAIL_KEY);
      form.elements.password.value = '';
      message(form, '');
      setSession(data);
      goto('home');
    } catch (error) {
      form.elements.password.value = '';
      message(form, error.message);
    } finally {
      setBusy(form, false);
    }
  };

  const handleSignup = async (form) => {
    const email = form.elements.email.value.trim();
    const password = form.elements.password.value;
    const nickname = form.elements.nickname.value.trim();
    if (!email || !password || !nickname) return message(form, '필수 항목을 모두 입력해 주세요.');
    if (!Object.values(passwordRules(password)).every(Boolean)) {
      return message(form, '비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 모두 포함해야 해요.');
    }
    if (password !== form.elements.passwordConfirm.value) return message(form, '비밀번호 확인이 일치하지 않아요.');
    if (![...form.querySelectorAll('[data-agree-required]')].every((box) => box.checked)) {
      return message(form, '필수 약관에 동의해 주세요.');
    }
    setBusy(form, true);
    try {
      await request('/api/auth/signup', { body: { email, password, nickname } });
      form.reset();
      message(form, '');
      const loginForm = document.querySelector('[data-auth-form="login"]');
      if (loginForm) {
        loginForm.elements.email.value = email;
        message(loginForm, '가입이 완료됐어요. 로그인해 주세요.', 'info');
      }
      goto('auth');
    } catch (error) {
      const detail = error.fieldErrors?.[0]?.message;
      message(form, detail || error.message);
    } finally {
      setBusy(form, false);
    }
  };

  document.addEventListener('submit', (event) => {
    const form = event.target.closest?.('[data-auth-form]');
    if (!form) return;
    event.preventDefault();
    if (form.dataset.authForm === 'login') handleLogin(form);
    else if (form.dataset.authForm === 'signup') handleSignup(form);
  });

  document.addEventListener('input', (event) => {
    const input = event.target;
    const form = input.closest?.('[data-auth-form="signup"]');
    if (!form || input.name !== 'password') return;
    const rules = passwordRules(input.value);
    form.querySelectorAll('[data-rule]').forEach((item) => item.classList.toggle('ok', rules[item.dataset.rule]));
  });

  document.addEventListener('change', (event) => {
    const input = event.target;
    if (!input.matches?.('[data-agree-all]')) return;
    input.closest('form').querySelectorAll('[data-agree-required]').forEach((box) => { box.checked = input.checked; });
  });

  document.addEventListener('click', async (event) => {
    if (!event.target.closest?.('[data-auth-logout]')) return;
    try {
      await request('/api/auth/logout');
    } catch { /* 서버 응답과 관계없이 화면에서는 로그아웃 처리 */ }
    clearSession();
    goto('landing');
  });

  // 로그인 화면이 로드되면 저장된 이메일을 채운다.
  const restoreEmail = new MutationObserver(() => {
    const form = document.querySelector('[data-auth-form="login"]');
    if (!form) return;
    const saved = safeStorage.get(EMAIL_KEY);
    if (saved && !form.elements.email.value) {
      form.elements.email.value = saved;
      form.elements.saveEmail.checked = true;
    }
    restoreEmail.disconnect();
  });
  restoreEmail.observe(document.body, { childList: true, subtree: true });

  // 새로고침 후 HttpOnly refresh 쿠키가 남아 있으면 세션을 복구한다.
  request('/api/auth/refresh').then(setSession).catch(() => {});

  return { get accessToken() { return accessToken; }, get user() { return user; } };
})();
