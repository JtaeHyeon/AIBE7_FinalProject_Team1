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
      method: options.method || 'POST',
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
      const name = document.createElement('button');
      name.type = 'button';
      name.dataset.page = 'mypage';
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

  // 마이페이지: 로그인 여부에 따라 안내/계정 정보를 보여준다. 화면 HTML이 늦게 로드될 수 있어 둘 다에서 호출한다.
  const renderMyPage = () => {
    const root = document.querySelector('#mypage');
    if (!root) return;
    const guest = root.querySelector('[data-mp="guest"]');
    const member = root.querySelector('[data-mp="member"]');
    if (!guest || !member) return;
    guest.hidden = !!user;
    member.hidden = !user;
    if (!user) return;
    const fields = {
      initial: [...user.nickname][0] || '',
      nickname: user.nickname,
      email: user.email,
    };
    Object.entries(fields).forEach(([key, value]) => {
      const el = member.querySelector(`[data-mp-field="${key}"]`);
      if (el) el.textContent = value;
    });
  };

  const setSession = (data) => {
    accessToken = data?.accessToken ?? null;
    user = data?.user ?? null;
    renderHeader();
    renderMyPage();
  };

  const clearSession = () => setSession(null);

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

  // Refresh Token은 1회용이라 동시에 두 번 갱신하면 한쪽이 실패한다. 진행 중인 갱신이 있으면 그 결과를 같이 기다린다.
  let refreshing = null;
  const refresh = () => {
    refreshing ??= request('/api/auth/refresh')
      .then((data) => { setSession(data); return data; })
      .finally(() => { refreshing = null; });
    return refreshing;
  };

  // 다른 화면(판매글 작성 등)이 fetch 대신 쓰는 공통 요청 함수. Authorization 헤더를 붙이고, 401이면 한 번 갱신해 다시 보낸다.
  const authFetch = async (url, options = {}) => {
    const send = () => {
      const headers = new Headers(options.headers || {});
      if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
      return fetch(url, { ...options, headers });
    };
    // 새로고침 직후 세션 복구가 진행 중이면 먼저 끝나기를 기다린다.
    if (!accessToken && refreshing) await refreshing.catch(() => {});
    const response = await send();
    if (response.status !== 401) return response;
    try {
      await refresh();
    } catch {
      return response; // 세션이 끝났으면 원래 401을 그대로 돌려준다.
    }
    return send();
  };
  window.PastportApi = { fetch: authFetch };

  // 이메일 인증: 메일의 링크를 눌러 인증을 마친 이메일만 가입할 수 있다(서버도 다시 확인한다).
  // 링크는 다른 탭에서 열리므로, 보낸 뒤에는 인증 여부를 주기적으로 물어본다. 이메일을 바꾸면 처음부터 다시 한다.
  let verifiedEmail = null;
  let pollTimer = null;

  const emailStatus = (form, text, ok = false) => {
    const box = form.querySelector('[data-email-status]');
    box.textContent = text;
    box.classList.toggle('ok', ok);
  };

  const resetEmailVerification = (form) => {
    clearInterval(pollTimer);
    verifiedEmail = null;
    emailStatus(form, '');
  };

  const pollEmailVerified = (form, email) => {
    clearInterval(pollTimer);
    const deadline = Date.now() + 30 * 60 * 1000;
    pollTimer = setInterval(async () => {
      if (Date.now() > deadline) return clearInterval(pollTimer);
      try {
        const data = await request(`/api/auth/email/status?email=${encodeURIComponent(email)}`, { method: 'GET' });
        if (!data?.verified) return;
        clearInterval(pollTimer);
        verifiedEmail = email.toLowerCase();
        emailStatus(form, '이메일 인증이 완료됐어요.', true);
      } catch { /* 다음 주기에 다시 확인 */ }
    }, 5000);
  };

  const handleEmailSend = async (form) => {
    const email = form.elements.email.value.trim();
    if (!email) return message(form, '이메일을 입력해 주세요.');
    message(form, '');
    try {
      await request('/api/auth/email/send', { body: { email } });
      emailStatus(form, '인증 메일을 보냈어요. 메일의 링크를 누르면 인증이 완료돼요.');
      pollEmailVerified(form, email);
    } catch (error) {
      message(form, error.fieldErrors?.[0]?.message || error.message);
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
    if (verifiedEmail !== email.toLowerCase()) return message(form, '이메일 인증을 완료해 주세요.');
    if (![...form.querySelectorAll('[data-agree-required]')].every((box) => box.checked)) {
      return message(form, '필수 약관에 동의해 주세요.');
    }
    setBusy(form, true);
    try {
      await request('/api/auth/signup', { body: { email, password, nickname } });
      form.reset();
      resetEmailVerification(form);
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
    if (form && input.name === 'email') resetEmailVerification(form);
    if (!form || input.name !== 'password') return;
    const rules = passwordRules(input.value);
    form.querySelectorAll('[data-rule]').forEach((item) => item.classList.toggle('ok', rules[item.dataset.rule]));
  });

  document.addEventListener('change', (event) => {
    const input = event.target;
    if (!input.matches?.('[data-agree-all]')) return;
    input.closest('form').querySelectorAll('[data-agree-required]').forEach((box) => { box.checked = input.checked; });
  });

  document.addEventListener('click', (event) => {
    const button = event.target.closest?.('[data-email-send]');
    if (button) handleEmailSend(button.closest('form'));
  });

  document.addEventListener('click', async (event) => {
    if (!event.target.closest?.('[data-auth-logout]')) return;
    try {
      await request('/api/auth/logout');
    } catch { /* 서버 응답과 관계없이 화면에서는 로그아웃 처리 */ }
    clearSession();
    goto('landing');
  });

  // 로그인·회원가입 화면이 같이 쓰는 일러스트는 한 파일에서 가져와 자리표시자를 바꿔 넣는다.
  const artHtml = fetch('/pages/auth-art.html').then((response) => response.text());
  document.addEventListener('pastport:page-loaded', async (event) => {
    const id = event.detail?.id;
    if (id === 'mypage') renderMyPage();
    const slot = ['auth', 'signup'].includes(id) && document.querySelector(`#${id} [data-auth-art]`);
    if (slot) slot.outerHTML = await artHtml.catch(() => '');
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
  refresh().catch(() => {});

  return { get accessToken() { return accessToken; }, get user() { return user; } };
})();
