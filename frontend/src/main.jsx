import { StrictMode, useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const apiBase = import.meta.env.VITE_API_BASE_URL || ''

const initialOrder = {
  symbol: 'SBIN',
  side: 'BUY',
  entryPrice: '1130',
  stopPrice: '1128',
  quantity: '100'
}

const initialExecution = {
  symbol: 'SBIN', side: 'BUY', quantity: '1', orderType: 'LIMIT', price: '',
  triggerPrice: '', product: 'MIS', exchange: 'NSE', validity: 'DAY', stopPrice: ''
}

function money(value) {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency', currency: 'INR', maximumFractionDigits: 0
  }).format(Number(value || 0))
}


function LoginScreen({ onLogin, error, busy }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  function submit(event) {
    event.preventDefault()
    onLogin(username, password)
  }

  return <main className="login-shell">
    <section className="login-card">
      <div className="login-brand"><span className="brand-mark">S</span><div><strong>Signal<span>Desk</span></strong><small>PRIVATE TRADING WORKSPACE</small></div></div>
      <div className="login-copy">
        <p className="eyebrow">SECURE ACCESS</p>
        <h1>Welcome back</h1>
        <p>Sign in to continue to your private market research workspace.</p>
      </div>
      <form className="login-form" onSubmit={submit}>
        <label><span>Username</span><input autoFocus required value={username} onChange={event => setUsername(event.target.value)} autoComplete="username" /></label>
        <label><span>Password</span><input required type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" /></label>
        {error && <div className="login-error"><span>!</span>{error}</div>}
        <button className="primary full" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'} <span>→</span></button>
      </form>
      <div className="login-footnote"><span className="status-dot" /> Local session protected · broker execution remains gated</div>
    </section>
  </main>
}

function App() {
  const [profile, setProfile] = useState(null)
  const [draft, setDraft] = useState(null)
  const [order, setOrder] = useState(initialOrder)
  const [preview, setPreview] = useState(null)
  const [active, setActive] = useState('overview')
  const [marketSection, setMarketSection] = useState('connection')
  const [openMenu, setOpenMenu] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [checking, setChecking] = useState(false)
  const [notice, setNotice] = useState(null)
  const [kiteStatus, setKiteStatus] = useState(null)
  const [marketSession, setMarketSession] = useState(null)
  const [execution, setExecution] = useState(initialExecution)
  const [executionOrders, setExecutionOrders] = useState([])
  const [executionBusy, setExecutionBusy] = useState(false)
  const [authReady, setAuthReady] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)
  const [loginBusy, setLoginBusy] = useState(false)
  const [loginError, setLoginError] = useState(null)


  async function login(username, password) {
    setLoginBusy(true)
    setLoginError(null)
    try {
      await request('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      })
      setAuthenticated(true)
    } catch (error) {
      setLoginError(error.message || 'Unable to sign in')
    } finally {
      setLoginBusy(false)
    }
  }

  async function logout() {
    try { await request('/api/v1/auth/logout', { method: 'POST' }) } catch (_) {}
    setAuthenticated(false)
    setProfile(null)
    setDraft(null)
    setActive('overview')
  }

  const localRisk = useMemo(() => {
    const entry = Number(order.entryPrice)
    const stop = Number(order.stopPrice)
    const quantity = Number(order.quantity)
    return Number.isFinite(entry) && Number.isFinite(stop) && Number.isFinite(quantity)
      ? Math.abs(entry - stop) * quantity : 0
  }, [order])

  async function request(path, options = {}) {
    const response = await fetch(`${apiBase}${path}`, {
      ...options,
      credentials: 'include',
      headers: { ...(options.headers || {}) }
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      const error = new Error(data.message || data.error || 'Request failed')
      error.status = response.status
      throw error
    }
    return data
  }

  async function loadProfile() {
    setLoading(true)
    try {
      const current = await request('/api/v1/profile')
      setProfile(current)
      setDraft(current)
    } catch (error) {
      setNotice({ tone: 'error', text: `API unavailable: ${error.message}` })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let cancelled = false
    request('/api/v1/auth/me')
      .then(() => { if (!cancelled) setAuthenticated(true) })
      .catch(error => { if (!cancelled && error.status === 401) setAuthenticated(false) })
      .finally(() => { if (!cancelled) setAuthReady(true) })
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    const hash = window.location.hash
    if (!hash.startsWith('#integration')) return
    setActive('integration')
    const query = hash.includes('?') ? hash.substring(hash.indexOf('?') + 1) : ''
    const result = new URLSearchParams(query).get('kite')
    if (result === 'connected') setNotice({ tone: 'success', text: 'Kite connected. Today’s encrypted session is ready.' })
    if (result === 'error') setNotice({ tone: 'error', text: 'Kite login could not be completed. Connect again from this screen.' })
    window.history.replaceState(null, '', window.location.pathname + window.location.search)
  }, [])

  useEffect(() => {
    if (!notice) return undefined
    const timer = window.setTimeout(() => setNotice(null), 2000)
    return () => window.clearTimeout(timer)
  }, [notice])

  function handleButtonFeedback(event) {
    const button = event.target?.closest?.('button')
    if (!button || button.disabled) return
    button.classList.remove('button-clicked')
    window.requestAnimationFrame(() => {
      button.classList.add('button-clicked')
      window.setTimeout(() => button.classList.remove('button-clicked'), 240)
    })
  }

  async function loadKiteStatus() {
    try {
      setKiteStatus(await request('/api/v1/integrations/zerodha/status'))
    } catch (error) {
      setKiteStatus({ state: 'UNAVAILABLE', nextStep: error.message, executionState: 'DISABLED' })
    }
  }

  useEffect(() => {
    if (authenticated) loadKiteStatus()
  }, [authenticated])

  async function loadMarketSession() {
    try { setMarketSession(await request('/api/v1/market-session/status')) } catch (_) { setMarketSession(null) }
  }

  useEffect(() => {
    if (!authenticated) return undefined
    loadMarketSession()
    const timer = window.setInterval(loadMarketSession, 30000)
    return () => window.clearInterval(timer)
  }, [authenticated])

  async function saveProfile(event) {
    event.preventDefault()
    setSaving(true)
    try {
      const updated = await request('/api/v1/profile', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
          ...draft,
          allocatedCapital: Number(draft.allocatedCapital),
          maxLossPerTrade: Number(draft.maxLossPerTrade),
          maxDailyLoss: Number(draft.maxDailyLoss),
          maxTradesPerDay: Number(draft.maxTradesPerDay),
          maxOpenPositions: Number(draft.maxOpenPositions),
          cooldownSeconds: Number(draft.cooldownSeconds)
        })
      })
      setProfile(updated)
      setDraft(updated)
      setNotice({ tone: 'success', text: 'Paper-trading profile saved locally.' })
    } catch (error) {
      setNotice({ tone: 'error', text: error.message })
    } finally {
      setSaving(false)
    }
  }

  async function checkOrder(event) {
    event.preventDefault()
    setChecking(true)
    setPreview(null)
    try {
      const result = await request('/api/v1/execution/preview', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
          ...order,
          symbol: order.symbol.trim().toUpperCase(),
          entryPrice: Number(order.entryPrice),
          stopPrice: Number(order.stopPrice),
          quantity: Number(order.quantity)
        })
      })
      setPreview(result)
    } catch (error) {
      setNotice({ tone: 'error', text: error.message })
    } finally {
      setChecking(false)
    }
  }

  const changeDraft = (field, value) => setDraft(current => ({ ...current, [field]: value }))
  const changeOrder = (field, value) => setOrder(current => ({ ...current, [field]: value }))
  const changeExecution = (field, value) => setExecution(current => ({ ...current, [field]: value }))

  async function refreshExecution() {
    try { setExecutionOrders(await request('/api/v1/execution/orders')) } catch (error) { setNotice({ tone: 'error', text: error.message }) }
  }

  useEffect(() => { if (authenticated) loadProfile() }, [authenticated])
  useEffect(() => { if (active === 'execution' && authenticated) refreshExecution() }, [active, authenticated])

  if (!authReady) return <main className="login-shell"><section className="login-card login-loading"><span className="brand-mark">S</span><p>Checking secure session…</p></section></main>
  if (!authenticated) return <LoginScreen onLogin={login} error={loginError} busy={loginBusy} />

  return <div className="app-shell" onClick={handleButtonFeedback}>
    <aside className="sidebar">
      <a className="brand" href="#overview" onClick={() => { setActive('overview'); setOpenMenu(null) }}>
        <span className="brand-mark">S</span><span>Signal<span>Desk</span></span>
      </a>
      <p className="sidebar-label">WORKSPACE</p>
      <nav>
        <button className={active === 'overview' ? 'nav-link active' : 'nav-link'} onClick={() => { setActive('overview'); setOpenMenu(null) }}><span>▦</span> Overview</button>

        <div className="nav-group">
          <button className={openMenu === 'trading' ? 'nav-link active nav-parent' : 'nav-link nav-parent'} onClick={() => setOpenMenu(current => current === 'trading' ? null : 'trading')}><span>⇄</span><span>Trading</span><span className="nav-chevron">{openMenu === 'trading' ? '⌃' : '⌄'}</span></button>
          {openMenu === 'trading' && <div className="subnav">
            <button className={active === 'risk' ? 'subnav-link active' : 'subnav-link'} onClick={() => { setActive('risk'); setOpenMenu('trading') }}>Risk preview</button>
            <button className={active === 'execution' ? 'subnav-link active' : 'subnav-link'} onClick={() => { setActive('execution'); setOpenMenu('trading') }}>Execution</button>
          </div>}
        </div>

        <div className="nav-group">
          <button className={openMenu === 'records' ? 'nav-link active nav-parent' : 'nav-link nav-parent'} onClick={() => setOpenMenu(current => current === 'records' ? null : 'records')}><span>▤</span><span>Records</span><span className="nav-chevron">{openMenu === 'records' ? '⌃' : '⌄'}</span></button>
          {openMenu === 'records' && <div className="subnav">
            <button className={active === 'audit' ? 'subnav-link active' : 'subnav-link'} onClick={() => { setActive('audit'); setOpenMenu('records') }}>Audit log</button>
            <button className={active === 'journal' ? 'subnav-link active' : 'subnav-link'} onClick={() => { setActive('journal'); setOpenMenu('records') }}>Trade journal</button>
          </div>}
        </div>

        <div className="nav-group">
          <button className={openMenu === 'market-data' ? 'nav-link active nav-parent' : 'nav-link nav-parent'} onClick={() => setOpenMenu(current => current === 'market-data' ? null : 'market-data')}><span>◌</span><span>Market data</span><span className="nav-chevron">{openMenu === 'market-data' ? '⌃' : '⌄'}</span></button>
          {openMenu === 'market-data' && <div className="subnav">
            <button className={active === 'integration' && marketSection === 'connection' ? 'subnav-link active' : 'subnav-link'} onClick={() => { setActive('integration'); setMarketSection('connection'); setOpenMenu('market-data') }}>Connection</button>
            <button className={active === 'integration' && marketSection === 'symbols' ? 'subnav-link active' : 'subnav-link'} onClick={() => { setActive('integration'); setMarketSection('symbols'); setOpenMenu('market-data') }}>Symbols & candles</button>
            <button className={active === 'integration' && marketSection === 'monitor' ? 'subnav-link active' : 'subnav-link'} onClick={() => { setActive('integration'); setMarketSection('monitor'); setOpenMenu('market-data') }}>Live monitor</button>
            <button className={active === 'integration' && marketSection === 'strategy' ? 'subnav-link active' : 'subnav-link'} onClick={() => { setActive('integration'); setMarketSection('strategy'); setOpenMenu('market-data') }}>Scanner rules</button>
          </div>}
        </div>

        <div className="nav-group">
          <button className={openMenu === 'configuration' ? 'nav-link active nav-parent' : 'nav-link nav-parent'} onClick={() => setOpenMenu(current => current === 'configuration' ? null : 'configuration')}><span>⚙</span><span>Configuration</span><span className="nav-chevron">{openMenu === 'configuration' ? '⌃' : '⌄'}</span></button>
          {openMenu === 'configuration' && <div className="subnav">
            <button className={active === 'settings' ? 'subnav-link active' : 'subnav-link'} onClick={() => { setActive('settings'); setOpenMenu('configuration') }}>Trading profile</button>
          </div>}
        </div>
      </nav>
n>⚙</span> Trading profile</button>
      </nav>
      <div className="sidebar-footer">
        <div className="paper-chip"><span className="dot" /> PAPER MODE</div>
        <p>Your browser talks only to the local API.</p>
        <button className="logout-button" onClick={logout}>Sign out</button>
      </div>
    </aside>

    <main className="main-content">
      <header className="topbar">
        <div>
          <p className="eyebrow">LOCAL RESEARCH WORKSPACE</p>
          <h1>{active === 'settings' ? 'Trading profile' : active === 'risk' ? 'Risk preview' : active === 'execution' ? 'Order execution' : active === 'audit' ? 'Audit log' : active === 'journal' ? 'Trade journal' : active === 'integration' ? 'Market-data connection' : 'Paper trading overview'}</h1>
        </div>
        <div className="topbar-status">
          <div className="connection"><span className={loading ? 'status-dot amber' : 'status-dot'} /> {loading ? 'Connecting to API' : 'Local API connected'}</div>
          {marketSession && <div className={`session-chip ${marketSession.open ? 'open' : 'closed'}`}>
            <span className="status-dot" /> {marketSession.open ? 'NSE session open' : 'NSE session closed'}
          </div>}
        </div>
      </header>

      {notice && <div className={`notice ${notice.tone}`}><span>{notice.tone === 'success' ? '✓' : '!'}</span>{notice.text}<button onClick={() => setNotice(null)}>×</button></div>}

      {!profile && loading ? <section className="loading-card">Loading your local risk profile…</section> : <>
        {active === 'overview' && <>
          <section className="safety-banner"><div className="safety-icon">⌁</div><div><strong>Execution is disabled.</strong><p>Approved previews are paper-only. This frontend cannot send a broker order.</p></div></section>
          <section className="metrics-grid">
            <Metric label="Allocated capital" value={money(profile?.allocatedCapital)} detail="Configured paper capital" />
            <Metric label="Risk per trade" value={money(profile?.maxLossPerTrade)} detail="Maximum loss at stop" accent="orange" />
            <Metric label="Daily stop" value={money(profile?.maxDailyLoss)} detail="Stop trading after this loss" accent="red" />
            <Metric label="Position limits" value={`${profile?.maxOpenPositions} open / ${profile?.maxTradesPerDay} trades`} detail="Session guardrails" />
          </section>
          <section className="two-column">
            <div className="panel checklist"><div className="panel-heading"><div><p className="eyebrow">BEFORE EVERY PAPER TRADE</p><h2>Risk checklist</h2></div><span className="badge">Required</span></div>
              <Checklist text="Define entry and hard stop before checking quantity" />
              <Checklist text="Keep proposed loss inside the configured limit" />
              <Checklist text="Avoid recovery sizing or averaging down" />
              <Checklist text="Treat every signal as a hypothesis, not an instruction" />
            </div>
            <div className="panel quick-action"><p className="eyebrow">NEXT STEP</p><h2>Validate a setup</h2><p>Check the maximum permitted quantity from the exact entry and stop distance.</p><button className="primary" onClick={() => setActive('risk')}>Open risk preview <span>→</span></button></div>
          </section>
        </>}

        {active === 'risk' && <section className="risk-layout">
          <form className="panel order-form" onSubmit={checkOrder}>
            <div className="panel-heading"><div><p className="eyebrow">PAPER-TRADE GATE</p><h2>Order risk preview</h2></div><span className="badge blue">No order routing</span></div>
            <div className="field-grid">
              <Field label="NSE symbol"><input value={order.symbol} maxLength="32" onChange={e => changeOrder('symbol', e.target.value)} /></Field>
              <Field label="Side"><select value={order.side} onChange={e => changeOrder('side', e.target.value)}><option>BUY</option><option>SELL</option></select></Field>
              <Field label="Entry price (₹)"><input type="number" min="0" step="0.05" value={order.entryPrice} onChange={e => changeOrder('entryPrice', e.target.value)} /></Field>
              <Field label="Stop price (₹)"><input type="number" min="0" step="0.05" value={order.stopPrice} onChange={e => changeOrder('stopPrice', e.target.value)} /></Field>
              <Field label="Quantity"><input type="number" min="1" step="1" value={order.quantity} onChange={e => changeOrder('quantity', e.target.value)} /></Field>
            </div>
            <div className="calculated-risk"><span>Calculated risk before API check</span><strong className={localRisk > Number(profile?.maxLossPerTrade) ? 'danger-text' : ''}>{money(localRisk)}</strong></div>
            <button className="primary full" disabled={checking}>{checking ? 'Checking limits…' : 'Check paper-trade limits'} <span>→</span></button>
          </form>

          <aside className={`panel decision ${preview ? (preview.allowed ? 'allowed' : 'rejected') : ''}`}>
            <p className="eyebrow">RISK DECISION</p>
            {!preview ? <><h2>Waiting for a setup</h2><p>Enter the proposed entry, stop and quantity. The API calculates permitted quantity using your saved risk profile.</p></> : <>
              <div className="decision-title"><span>{preview.allowed ? '✓' : '×'}</span><h2>{preview.allowed ? 'Within limits' : 'Blocked by guardrails'}</h2></div>
              <div className="result-stat"><span>Proposed risk</span><strong>{money(preview.proposedRisk)}</strong></div>
              <div className="result-stat"><span>Maximum quantity</span><strong>{preview.maxAllowedQuantity}</strong></div>
              <ul>{preview.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul>
              <p className="execution-note">Mode: <strong>{preview.executionMode}</strong>. No broker request has been sent.</p>
            </>}
          </aside>
        </section>}

        {active === 'execution' && <ExecutionConsole profile={profile} status={kiteStatus} form={execution} change={changeExecution} orders={executionOrders} busy={executionBusy} setBusy={setExecutionBusy} refresh={refreshExecution} notify={setNotice} />}

        {active === 'audit' && <AuditLog notify={setNotice} />}

        {active === 'journal' && <Journal notify={setNotice} />}

        {active === 'integration' && <KiteIntegration status={kiteStatus} refresh={loadKiteStatus} notify={setNotice} section={marketSection} setSection={setMarketSection} />}

        {active === 'settings' && draft && <form className="panel settings-panel" onSubmit={saveProfile}>
          <div className="panel-heading"><div><p className="eyebrow">LOCAL CONFIGURATION</p><h2>Paper-trading guardrails</h2><p>These limits are stored in your local PostgreSQL database.</p></div><span className="badge">PAPER / DRY-RUN only</span></div>
          <div className="setting-grid">
            <Field label="Mode"><select value={draft.mode} onChange={e => changeDraft('mode', e.target.value)}><option>PAPER</option><option>DRY_RUN</option></select></Field>
            <Field label="Broker label"><input value={draft.broker} onChange={e => changeDraft('broker', e.target.value)} /></Field>
            <Field label="Exchange"><input value={draft.exchange} onChange={e => changeDraft('exchange', e.target.value)} /></Field>
            <Field label="Segment"><input value={draft.segment} onChange={e => changeDraft('segment', e.target.value)} /></Field>
            <Field label="Product"><input value={draft.product} onChange={e => changeDraft('product', e.target.value)} /></Field>
            <Field label="Allocated capital (₹)"><input type="number" min="1" value={draft.allocatedCapital} onChange={e => changeDraft('allocatedCapital', e.target.value)} /></Field>
            <Field label="Maximum loss per trade (₹)"><input type="number" min="1" value={draft.maxLossPerTrade} onChange={e => changeDraft('maxLossPerTrade', e.target.value)} /></Field>
            <Field label="Maximum daily loss (₹)"><input type="number" min="1" value={draft.maxDailyLoss} onChange={e => changeDraft('maxDailyLoss', e.target.value)} /></Field>
            <Field label="Maximum trades per day"><input type="number" min="1" value={draft.maxTradesPerDay} onChange={e => changeDraft('maxTradesPerDay', e.target.value)} /></Field>
            <Field label="Maximum open positions"><input type="number" min="1" value={draft.maxOpenPositions} onChange={e => changeDraft('maxOpenPositions', e.target.value)} /></Field>
            <Field label="Cooldown between intents (seconds)"><input type="number" min="0" value={draft.cooldownSeconds ?? 60} onChange={e => changeDraft('cooldownSeconds', e.target.value)} /></Field>
          </div>
          <label className="kill-switch"><input type="checkbox" checked={draft.killSwitchEnabled} onChange={e => changeDraft('killSwitchEnabled', e.target.checked)} /><span><strong>Enable kill switch</strong><small>Blocks every paper-trade preview until you turn it off.</small></span></label>
          <button className="primary save" disabled={saving}>{saving ? 'Saving…' : 'Save local profile'} <span>→</span></button>
        </form>}
      </>}
    </main>
  </div>
}

function Metric({ label, value, detail, accent = '' }) { return <article className={`metric-card ${accent}`}><p>{label}</p><strong>{value}</strong><span>{detail}</span></article> }
function Checklist({ text }) { return <div className="check-item"><span>✓</span>{text}</div> }
function Field({ label, children }) { return <label className="field"><span>{label}</span>{children}</label> }

function AuditLog({ notify }) {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(false)
  async function refresh() {
    setLoading(true)
    try {
      const response = await fetch(`${apiBase}/api/v1/audit/events?limit=100`)
      const data = await response.json().catch(() => [])
      if (!response.ok) throw new Error(data.message || 'Audit events could not be loaded')
      setEvents(data)
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setLoading(false) }
  }
  useEffect(() => { refresh() }, [])
  return <section className="audit-page"><section className="panel table-panel"><div className="panel-heading"><div><p className="eyebrow">DURABLE SAFETY TRAIL</p><h2>Recent events</h2><p>Order attempts, safeguards, kill-switch changes, and reconciliation events are stored in PostgreSQL.</p></div><button className="ghost" onClick={refresh} disabled={loading}>↻ Refresh</button></div>
    {events.length === 0 ? <p className="empty">No audit events recorded.</p> : <div className="trade-table-wrap"><table><thead><tr><th>Time</th><th>Event</th><th>Aggregate</th><th>Details</th></tr></thead><tbody>{events.map((event, index) => <tr key={`${event.occurredAt}-${index}`}><td>{event.occurredAt ? new Date(event.occurredAt).toLocaleString() : '—'}</td><td><strong>{event.eventType}</strong></td><td>{event.aggregateType}<small>{event.aggregateId}</small></td><td className="audit-details">{event.details}</td></tr>)}</tbody></table></div>}
  </section></section>
}

function ExecutionConsole({ profile, status, form, change, orders, busy, setBusy, refresh, notify }) {
  const [automationStatus, setAutomationStatus] = useState(null)
  const liveGateReady = profile?.mode === 'LIVE' && status?.executionState === 'CONFIGURED_BUT_GATED' && status?.accessTokenConfigured
  useEffect(() => {
    fetch(`${apiBase}/api/v1/automation/status`).then(response => response.ok ? response.json() : null)
      .then(setAutomationStatus).catch(() => setAutomationStatus(null))
  }, [])
  async function api(path, options) {
    const response = await fetch(`${apiBase}${path}`, options)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.message || 'Execution request failed')
    return data
  }
  async function submit(event) {
    event.preventDefault()
    if (!liveGateReady) { notify({ tone: 'error', text: 'Execution is locked. Enable the explicit routing flag, use LIVE profile mode, and complete Kite login first.' }); return }
    if (!window.confirm(`Submit ${form.side} ${form.quantity} ${form.symbol}? This can place a real broker order.`)) return
    setBusy(true)
    try {
      const key = window.crypto?.randomUUID ? window.crypto.randomUUID() : `${Date.now()}-${Math.random()}`
      await api('/api/v1/execution/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key }, body: JSON.stringify({
        ...form, symbol: form.symbol.trim().toUpperCase(), quantity: Number(form.quantity), price: form.price ? Number(form.price) : null,
        triggerPrice: form.triggerPrice ? Number(form.triggerPrice) : null, stopPrice: form.stopPrice ? Number(form.stopPrice) : null
      }) })
      notify({ tone: 'success', text: 'Order submission accepted by the execution gateway.' })
      await refresh()
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  async function reconcile() {
    setBusy(true)
    try { await api('/api/v1/execution/orders/reconcile', { method: 'POST' }); await refresh(); notify({ tone: 'success', text: 'Tracked order statuses reconciled.' }) }
    catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  async function cancel(orderId) {
    if (!window.confirm(`Cancel broker order ${orderId}?`)) return
    setBusy(true)
    try { await api(`/api/v1/execution/orders/${encodeURIComponent(orderId)}`, { method: 'DELETE' }); await refresh(); notify({ tone: 'success', text: 'Cancellation request submitted.' }) }
    catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  return <section className="execution-page">
    <section className="safety-banner"><div className="safety-icon">⌁</div><div><strong>{liveGateReady ? 'Explicit execution gates are open.' : 'Execution is locked.'}</strong><p>Every order requires LIVE profile mode, the routing flag, today’s Kite session, a risk-valid entry/stop pair, and confirmation.</p><p className="automation-state">Automatic signal routing: <b>{automationStatus?.enabled ? 'ENABLED' : 'DISABLED'}</b> · max signal age: {automationStatus?.maxSignalAgeSeconds ?? '—'}s · automatic quantity cap: {automationStatus?.maxOrderQuantity ?? '—'} · state: <b>{automationStatus?.uncertainBrokerState ? 'MANUAL RECONCILIATION REQUIRED' : 'READY'}</b></p></div></section>
    <div className="two-column">
      <form className="panel order-form" onSubmit={submit}>
        <div className="panel-heading"><div><p className="eyebrow">CONTROLLED BROKER ROUTING</p><h2>Submit Kite order</h2></div><span className={`badge ${liveGateReady ? 'green' : 'blue'}`}>{liveGateReady ? 'GATED LIVE' : 'LOCKED'}</span></div>
        <div className="field-grid">
          <Field label="NSE symbol"><input required value={form.symbol} onChange={e => change('symbol', e.target.value)} /></Field>
          <Field label="Side"><select value={form.side} onChange={e => change('side', e.target.value)}><option>BUY</option><option>SELL</option></select></Field>
          <Field label="Order type"><select value={form.orderType} onChange={e => change('orderType', e.target.value)}><option>LIMIT</option><option>MARKET</option><option>SL</option><option>SL-M</option></select></Field>
          <Field label="Quantity"><input required type="number" min="1" value={form.quantity} onChange={e => change('quantity', e.target.value)} /></Field>
          <Field label="Expected entry (₹)"><input required type="number" min="0" step="0.05" value={form.price} onChange={e => change('price', e.target.value)} /></Field>
          <Field label="Hard stop (₹)"><input required type="number" min="0" step="0.05" value={form.stopPrice} onChange={e => change('stopPrice', e.target.value)} /></Field>
          <Field label="Trigger price (optional)"><input type="number" min="0" step="0.05" value={form.triggerPrice} onChange={e => change('triggerPrice', e.target.value)} /></Field>
          <Field label="Product"><select value={form.product} onChange={e => change('product', e.target.value)}><option>MIS</option><option>CNC</option></select></Field>
        </div>
        <button className="primary full" disabled={busy}>{busy ? 'Processing…' : 'Submit controlled order'} <span>→</span></button>
      </form>
      <div className="panel journal-help"><p className="eyebrow">EXECUTION LIFECYCLE</p><h2>Reconciliation is mandatory</h2><p>The broker response is only an acknowledgement. The system polls tracked orders and persists `OPEN`, `PARTIALLY_FILLED`, `COMPLETE`, `REJECTED`, and `CANCELLED` states with observed fill quantity and average price.</p><button className="ghost" onClick={reconcile} disabled={busy}>↻ Reconcile tracked orders</button></div>
    </div>
    <section className="panel table-panel"><div className="panel-heading"><div><p className="eyebrow">TRACKED ORDERS</p><h2>Execution history</h2></div><button className="ghost" onClick={refresh} disabled={busy}>↻ Refresh</button></div>
      {orders.length === 0 ? <p className="empty">No broker execution requests are recorded.</p> : <div className="trade-table-wrap"><table><thead><tr><th>Order</th><th>Symbol</th><th>Side</th><th>Quantity</th><th>Filled</th><th>Status</th><th /></tr></thead><tbody>{orders.map(order => <tr key={order.orderId}><td><strong>{order.orderId}</strong><small>{order.observedAt ? new Date(order.observedAt).toLocaleString() : ''}</small></td><td>{order.symbol || '—'}</td><td className={order.side === 'BUY' ? 'buy' : 'sell'}>{order.side || '—'}</td><td>{order.quantity}</td><td>{order.filledQuantity}{order.averagePrice ? ` @ ₹${order.averagePrice}` : ''}</td><td><span className={`status ${String(order.status || '').toLowerCase()}`}>{order.status}</span></td><td>{['PENDING', 'OPEN', 'PARTIALLY_FILLED'].includes(order.status) && <button className="watchlist-remove" onClick={() => cancel(order.orderId)} disabled={busy}>Cancel</button>}</td></tr>)}</tbody></table></div>}
    </section>
  </section>
}

function KiteIntegration({ status, refresh, notify, section, setSection }) {
  const [loadingUrl, setLoadingUrl] = useState(false)
  const [syncing, setSyncing] = useState(false)
  const [syncResult, setSyncResult] = useState(null)
  const [symbolQuery, setSymbolQuery] = useState('SBIN')
  const [sectorFilter, setSectorFilter] = useState('')
  const [instruments, setInstruments] = useState([])
  const [selected, setSelected] = useState(null)
  const [tradeDate, setTradeDate] = useState(() => new Date().toLocaleDateString('en-CA'))
  const [verifying, setVerifying] = useState(false)
  const [verification, setVerification] = useState(null)
  async function connectKite() {
    setLoadingUrl(true)
    try {
      const response = await fetch(`${apiBase}/api/v1/integrations/zerodha/login-url`)
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.message || 'Kite API key has not been configured')
      window.location.assign(data.loginUrl)
    } catch (error) {
      notify({ tone: 'error', text: error.message })
    } finally { setLoadingUrl(false) }
  }

  const marketDataReady = status?.marketDataEnabled && status?.accessTokenConfigured

  async function syncInstruments() {
    setSyncing(true)
    try {
      const response = await fetch(`${apiBase}/api/v1/market-data/instruments/sync`, { method: 'POST' })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.message || 'Instrument sync failed')
      setSyncResult(data)
      notify({ tone: 'success', text: `${data.savedInstruments} NSE cash symbols synced locally.` })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setSyncing(false) }
  }

  async function searchInstruments(event) {
    event.preventDefault()
    try {
      const response = await fetch(`${apiBase}/api/v1/market-data/instruments?query=${encodeURIComponent(symbolQuery.trim())}&sector=${encodeURIComponent(sectorFilter)}`)
      const data = await response.json().catch(() => [])
      if (!response.ok) throw new Error(data.message || 'Symbol search failed')
      setInstruments(data)
      setSelected(null)
    } catch (error) { notify({ tone: 'error', text: error.message }) }
  }

  async function verifyHistorical() {
    if (!selected) return
    setVerifying(true)
    setVerification(null)
    try {
      const configResponse = await fetch(`${apiBase}/api/v1/scanner/config`)
      const scannerConfig = await configResponse.json().catch(() => ({}))
      if (!configResponse.ok) throw new Error(scannerConfig.message || 'Scanner configuration could not be loaded')
      const from = `${tradeDate}T09:15:00+05:30`
      const to = `${tradeDate}T15:30:00+05:30`
      const interval = scannerConfig.candleInterval || 'FIVE_MINUTE'
      const candleResponse = await fetch(`${apiBase}/api/v1/market-data/historical-candles?instrumentToken=${selected.instrumentToken}&interval=${interval}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
      const candles = await candleResponse.json().catch(() => [])
      if (!candleResponse.ok) throw new Error(candles.message || 'Historical candle request failed')
      const signalResponse = await fetch(`${apiBase}/api/v1/signals/evaluate`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ symbol: selected.tradingsymbol, candles }) })
      const signal = await signalResponse.json().catch(() => ({}))
      if (!signalResponse.ok) throw new Error(signal.message || 'Signal evaluation failed')
      setVerification({ count: candles.length, signal })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setVerifying(false) }
  }

  const configured = status?.state === 'READY_FOR_MARKET_DATA'
  return <section className="integration-page">
    <section className="safety-banner"><div className="safety-icon">◌</div><div><strong>Market data and broker routing are separately gated.</strong><p>Order placement, modification, and cancellation are available only from the Execution screen after every explicit safety gate passes.</p></div></section>
    <div className="market-data-tabs" role="tablist" aria-label="Market data sections">
      <button className={section === 'connection' ? 'market-tab active' : 'market-tab'} onClick={() => setSection('connection')}>Connection</button>
      <button className={section === 'symbols' ? 'market-tab active' : 'market-tab'} onClick={() => setSection('symbols')}>Symbols & candles</button>
      <button className={section === 'monitor' ? 'market-tab active' : 'market-tab'} onClick={() => setSection('monitor')}>Live monitor</button>
      <button className={section === 'strategy' ? 'market-tab active' : 'market-tab'} onClick={() => setSection('strategy')}>Scanner rules</button>
    </div>

    <div className={section === 'connection' ? 'market-section active' : 'market-section'}>
    <div className="integration-grid">
      <section className="panel integration-card">
        <div className="panel-heading"><div><p className="eyebrow">ZERODHA KITE CONNECT</p><h2>{status?.state?.replaceAll('_', ' ') || 'Checking configuration'}</h2></div><span className={`badge ${configured ? 'green' : 'blue'}`}>{status?.executionState || 'DISABLED'}</span></div>
        <div className="integration-rows">
          <IntegrationRow label="API key" value={status?.apiKeyConfigured ? 'Configured locally' : 'Not configured'} ok={status?.apiKeyConfigured} />
          <IntegrationRow label="API secret" value={status?.apiSecretConfigured ? 'Configured locally' : 'Not configured'} ok={status?.apiSecretConfigured} />
          <IntegrationRow label="Session token" value={status?.accessTokenConfigured ? 'Connected and encrypted locally' : 'Not connected for today'} ok={status?.accessTokenConfigured} />
          {status?.sessionExpiresAt && <IntegrationRow label="Session expiry" value={new Date(status.sessionExpiresAt).toLocaleString()} ok={status.accessTokenConfigured} />}
          <IntegrationRow label="Live market data" value={status?.marketDataEnabled ? 'Explicitly enabled' : 'Disabled'} ok={status?.marketDataEnabled} />
        </div>
        <div className="integration-next"><span>Next step</span><strong>{status?.nextStep || 'Loading local connection state…'}</strong></div>
        <div className="integration-actions"><button className="ghost" onClick={refresh}>↻ Refresh state</button><button className="primary" onClick={connectKite} disabled={loadingUrl || !status?.apiKeyConfigured || !status?.apiSecretConfigured}>{loadingUrl ? 'Opening Kite…' : 'Connect Kite'}</button></div>
      </section>
      <aside className="panel journal-help"><p className="eyebrow">SAFE ACTIVATION PATH</p><h2>What happens after purchase</h2><ol><li>Add credentials only to your uncommitted local <b>.env</b> file.</li><li>Use Kite login to create that day’s session token.</li><li>Verify instrument import and a historical-candle request.</li><li>Enable live WebSocket data only after both tests pass.</li></ol><p>API secret and access token never appear in this page or in browser storage.</p></aside>
    </div>
    </div>
    <div className={section === 'symbols' ? 'market-section active' : 'market-section'}>
    <section className="panel verification-card">
      <div className="panel-heading"><div><p className="eyebrow">READ-ONLY DATA VERIFICATION</p><h2>Sync symbols and inspect completed candles</h2><p>Only NSE cash equities are stored locally in this phase. No order can be created from this screen.</p></div><span className={`badge ${marketDataReady ? 'green' : 'blue'}`}>{marketDataReady ? 'Ready to verify' : 'Data disabled'}</span></div>
      {!marketDataReady && <p className="verification-warning">Enable <code>KITE_MARKET_DATA_ENABLED=true</code> in your local <code>.env</code>, restart, then connect Kite again before using these controls.</p>}
      <div className="verification-actions"><button className="primary" onClick={syncInstruments} disabled={!marketDataReady || syncing}>{syncing ? 'Syncing symbols…' : 'Sync NSE cash symbols'}</button>{syncResult && <span className="sync-result">Last sync: {syncResult.savedInstruments} symbols</span>}</div>
      <div className="symbol-workspace">
        <form className="symbol-search" onSubmit={searchInstruments}><Field label="Search synced NSE symbol"><input value={symbolQuery} placeholder="SBIN" onChange={event => setSymbolQuery(event.target.value.toUpperCase())} /></Field><Field label="Sector filter"><select value={sectorFilter} onChange={event => setSectorFilter(event.target.value)}><option value="">All sectors</option><option value="BANKING">Banking</option><option value="IT">IT</option><option value="AUTO">Auto</option><option value="ENERGY">Energy</option><option value="PHARMA">Pharma</option><option value="FMCG">FMCG</option><option value="OTHER">Other</option></select></Field><button className="ghost" disabled={!marketDataReady}>Search</button></form>
        <div className="instrument-results">{instruments.length === 0 ? <span>Sync first, then search by symbol.</span> : instruments.map(item => <button key={item.instrumentToken} className={selected?.instrumentToken === item.instrumentToken ? 'instrument-option selected' : 'instrument-option'} onClick={() => setSelected(item)}><strong>{item.tradingsymbol}</strong><span>{item.sector || 'OTHER'} · {item.name || item.segment} · token {item.instrumentToken}</span></button>)}</div>
      </div>
      {selected && <div className="historical-check"><div><p className="eyebrow">HISTORICAL CHECK</p><strong>{selected.tradingsymbol}</strong><span> · 5-minute completed candles</span></div><label className="date-field">Trading date<input type="date" value={tradeDate} onChange={event => setTradeDate(event.target.value)} /></label><button className="primary" onClick={verifyHistorical} disabled={verifying}>{verifying ? 'Fetching…' : 'Fetch and evaluate'}</button></div>}
      {verification && <div className="verification-result"><div><span>Completed candles</span><strong>{verification.count}</strong></div><div><span>Scanner result</span><strong>{verification.signal.type}</strong></div><div><span>Reference price</span><strong>{money(verification.signal.referencePrice)}</strong></div><p>{verification.signal.reasons?.join(' · ')}</p></div>}
    </section>
    </div>
    <div className={section === 'monitor' ? 'market-section active' : 'market-section'}>
    <LiveStream selected={selected} marketDataReady={marketDataReady} notify={notify} />
    </div>
    <div className={section === 'strategy' ? 'market-section active' : 'market-section'}>
    <ScannerSettings marketDataReady={marketDataReady} notify={notify} />
    </div>
    <div className={section === 'connection' ? 'market-section active' : 'market-section'}><section className="panel contract-card"><p className="eyebrow">IMPLEMENTED CONTRACTS</p><h2>Ready for credential-backed verification</h2><div className="contract-grid"><div><strong>GET /instruments</strong><span>Downloads the published instrument CSV.</span></div><div><strong>GET /instruments/historical/:token/:interval</strong><span>Retrieves historical candles into provider-neutral candle objects.</span></div><div><strong>Kite OAuth session flow</strong><span>Builds the login URL and SHA-256 session checksum.</span></div></div></section>
    </div>
  </section>
}

function IntegrationRow({ label, value, ok }) { return <div className="integration-row"><span>{label}</span><strong className={ok ? 'row-ok' : 'row-muted'}>{ok ? '● ' : '○ '}{value}</strong></div> }

function ScannerSettings({ marketDataReady, notify }) {
  const [config, setConfig] = useState(null)
  const [saving, setSaving] = useState(false)
  async function load() {
    try { const response = await fetch(`${apiBase}/api/v1/scanner/config`); const data = await response.json(); if (!response.ok) throw new Error(data.message || 'Scanner configuration could not be loaded'); setConfig(data) } catch (error) { notify({ tone: 'error', text: error.message }) }
  }
  useEffect(() => { load() }, [])
  async function save(event) {
    event.preventDefault(); setSaving(true)
    try {
      const response = await fetch(`${apiBase}/api/v1/scanner/config`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: config.enabled, candleInterval: config.candleInterval, openingRangeCandles: Number(config.openingRangeCandles), volumeLookback: Number(config.volumeLookback), volumeMultiplier: Number(config.volumeMultiplier), minimumCandles: Number(config.minimumCandles) }) })
      const data = await response.json().catch(() => ({})); if (!response.ok) throw new Error(data.message || 'Scanner configuration update failed'); setConfig(data); notify({ tone: 'success', text: 'Scanner strategy configuration updated for this application run.' })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setSaving(false) }
  }
  if (!config) return null
  return <section className="panel scanner-settings"><div className="panel-heading"><div><p className="eyebrow">STRATEGY CONFIGURATION</p><h2>Scanner rules</h2><p>Changes apply at runtime and are not broker orders.</p></div><span className={`badge ${config.enabled ? 'green' : 'blue'}`}>{config.enabled ? 'ENABLED' : 'DISABLED'}</span></div><form className="scanner-config-form" onSubmit={save}><label><span>Strategy enabled</span><input type="checkbox" checked={config.enabled} onChange={event => setConfig({ ...config, enabled: event.target.checked })} /></label><label><span>Opening-range candles</span><input type="number" min="1" value={config.openingRangeCandles} onChange={event => setConfig({ ...config, openingRangeCandles: event.target.value })} /></label><label><span>Volume lookback</span><input type="number" min="1" value={config.volumeLookback} onChange={event => setConfig({ ...config, volumeLookback: event.target.value })} /></label><label><span>Volume multiplier</span><input type="number" min="0.1" step="0.05" value={config.volumeMultiplier} onChange={event => setConfig({ ...config, volumeMultiplier: event.target.value })} /></label><label><span>Minimum candles</span><input type="number" min="3" value={config.minimumCandles} onChange={event => setConfig({ ...config, minimumCandles: event.target.value })} /></label><button className="primary" disabled={saving || !marketDataReady}>{saving ? 'Saving…' : 'Save scanner settings'}</button></form></section>
}

function LiveStream({ selected, marketDataReady, notify }) {
  const [status, setStatus] = useState(null)
  const [watchlist, setWatchlist] = useState([])
  const [signals, setSignals] = useState([])
  const [candles, setCandles] = useState([])
  const [busy, setBusy] = useState(false)
  const [groups, setGroups] = useState([])

  async function api(path, options) {
    const response = await fetch(`${apiBase}${path}`, options)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.message || 'Live market-data request failed')
    return data
  }
  async function refresh() {
    try {
      const [nextStatus, nextWatchlist, nextSignals, nextCandles] = await Promise.all([api('/api/v1/live-market/status'), api('/api/v1/live-market/watchlist'), api('/api/v1/live-market/signals'), api('/api/v1/live-market/candles')])
      setStatus(nextStatus); setWatchlist(nextWatchlist); setSignals(nextSignals); setCandles(nextCandles)
    } catch (error) { notify({ tone: 'error', text: error.message }) }
  }
  useEffect(() => { refresh() }, [])
  useEffect(() => {
    if (!marketDataReady) return
    fetch(`${apiBase}/api/v1/market-data/groups`).then(response => response.json()).then(data => setGroups(Object.keys(data).filter(group => group !== 'banking'))).catch(() => setGroups([]))
  }, [marketDataReady])
  useEffect(() => {
    if (status?.state !== 'CONNECTED') return undefined
    const timer = window.setInterval(refresh, 5000)
    return () => window.clearInterval(timer)
  }, [status?.state])
  async function addSelected() {
    if (!selected) return
    setBusy(true)
    try {
      await api('/api/v1/live-market/watchlist', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ instrumentToken: selected.instrumentToken }) })
      await refresh(); notify({ tone: 'success', text: `${selected.tradingsymbol} added to the local live watchlist.` })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  async function removeSymbol(instrumentToken, symbol) {
    setBusy(true)
    try {
      await api(`/api/v1/live-market/watchlist/${instrumentToken}`, { method: 'DELETE' })
      await refresh()
      notify({ tone: 'success', text: `${symbol} removed from the local watchlist. Restart the monitor to refresh its subscription.` })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  async function addGroup(group) {
    setBusy(true)
    try {
      await api(`/api/v1/live-market/watchlist/groups/${group}`, { method: 'POST' })
      await refresh()
      notify({ tone: 'success', text: `${group} group added to the local watchlist. Restart the monitor if it is currently stopped.` })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  async function toggleStream(path) {
    setBusy(true)
    try { await api(path, { method: 'POST' }); await refresh() } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  const connected = status?.state === 'CONNECTED'
  return <section className="panel live-card">
    <div className="panel-heading"><div><p className="eyebrow">LIVE WATCHLIST — READ ONLY</p><h2>Completed-candle signal monitor</h2><p>Signals are generated only after a five-minute candle closes. They are alerts, never orders.</p></div><span className={`badge ${connected ? 'green' : 'blue'}`}>{status?.state || 'STOPPED'}</span></div>
    <div className="live-actions"><button className="ghost" onClick={refresh}>↻ Refresh</button><button className="ghost" onClick={addSelected} disabled={!marketDataReady || !selected || busy}>{selected ? `Add ${selected.tradingsymbol}` : 'Select a symbol above'}</button>{groups.map(group => <button key={group} className="ghost group-button" onClick={() => addGroup(group)} disabled={!marketDataReady || busy}>{group === 'bank-nifty' ? 'Add Bank Nifty' : `Add ${group.toUpperCase()}`}</button>)}{connected ? <button className="danger-button" onClick={() => toggleStream('/api/v1/live-market/stop')} disabled={busy}>Stop monitor</button> : <button className="primary" onClick={() => toggleStream('/api/v1/live-market/start')} disabled={!marketDataReady || watchlist.length === 0 || busy}>{busy ? 'Working…' : 'Start monitor'}</button>}</div>
    <div className="live-grid"><div><span>Watchlist and latest ticks · {watchlist.length} symbols</span>{watchlist.length === 0 ? <p>No selected symbols yet.</p> : <ul>{watchlist.map(item => <li key={item.instrumentToken}><div><strong>{item.symbol}</strong><small>{status?.latestPrices?.[item.instrumentToken] ? `${money(status.latestPrices[item.instrumentToken])} · tick ${status.lastTickAt ? new Date(status.lastTickAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : 'pending'}` : 'Waiting for first tick'}</small></div><button className="watchlist-remove" onClick={() => removeSymbol(item.instrumentToken, item.symbol)} disabled={busy || connected}>Remove</button></li>)}</ul>}{connected && watchlist.length > 0 && <small className="watchlist-note">Stop the monitor before removing symbols.</small>}</div><div><span>Signal history · actionable only</span>{signals.length === 0 ? <p>{connected ? 'No actionable signal yet. Completed NO_TRADE evaluations are shown below.' : 'Start the monitor after adding symbols.'}</p> : <ul>{signals.slice(0, 20).map(signal => <li key={`${signal.instrumentToken}-${signal.candleStart}`} className={signal.type === 'WATCH_LONG' ? 'long-signal' : 'short-signal'}><strong>{signal.symbol} · {signal.type}</strong><small>{money(signal.referencePrice)} · {new Date(signal.candleStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</small></li>)}</ul>}</div></div>
    <div className="candle-panel"><div className="candle-heading"><span>Completed live candles</span><small>{candles.length ? 'Latest candles are shown first' : 'No completed live candle received in this run'}</small></div>{candles.length === 0 ? <p className="empty-candles">The first row appears after the next five-minute boundary receives a tick.</p> : <div className="candle-table-wrap"><table className="candle-table"><thead><tr><th>Symbol / time</th><th>OHLC</th><th>Volume</th><th>Evaluation</th><th>VWAP</th></tr></thead><tbody>{candles.slice(0, 20).map(candle => <tr key={`${candle.instrumentToken}-${candle.candleStart}`}><td><strong>{candle.symbol}</strong><small>{new Date(candle.candleStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</small></td><td>{money(candle.open)} / {money(candle.high)} / {money(candle.low)} / {money(candle.close)}</td><td>{candle.volume.toLocaleString()}</td><td><span className={`candle-evaluation ${candle.evaluationType === 'WATCH_LONG' ? 'long-signal' : candle.evaluationType === 'WATCH_SHORT' ? 'short-signal' : ''}`}>{candle.evaluationType}</span></td><td>{candle.vwap ? money(candle.vwap) : '—'}</td></tr>)}</tbody></table></div>}</div>
    {status?.lastError && <p className="live-error">{status.lastError}</p>}
  </section>
}

function Journal({ notify }) {
  const [trades, setTrades] = useState([])
  const [summary, setSummary] = useState(null)
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [action, setAction] = useState(null)
  const [actionPrice, setActionPrice] = useState('')
  const [actionQuantity, setActionQuantity] = useState('')
  const [brokerage, setBrokerage] = useState('0')
  const [exitReason, setExitReason] = useState('MANUAL')
  const [form, setForm] = useState({ symbol: 'SBIN', side: 'BUY', intendedEntryPrice: '', stopPrice: '', intendedQuantity: '', strategy: '', notes: '' })

  async function api(path, options) {
    const response = await fetch(`${apiBase}${path}`, options)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.message || 'Request failed')
    return data
  }

  async function reload() {
    setLoading(true)
    try {
      const [nextTrades, nextSummary, nextReport] = await Promise.all([api('/api/v1/paper-trades'), api('/api/v1/paper-trades/summary'), api('/api/v1/paper-trades/report')])
      setTrades(nextTrades)
      setSummary(nextSummary)
      setReport(nextReport)
    } catch (error) {
      notify({ tone: 'error', text: `Unable to load journal: ${error.message}` })
    } finally { setLoading(false) }
  }

  useEffect(() => { reload() }, [])

  async function createIntent(event) {
    event.preventDefault()
    setCreating(true)
    try {
      await api('/api/v1/paper-trades', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
        ...form, symbol: form.symbol.trim().toUpperCase(), intendedEntryPrice: Number(form.intendedEntryPrice),
        stopPrice: Number(form.stopPrice), intendedQuantity: Number(form.intendedQuantity)
      }) })
      setForm(current => ({ ...current, intendedEntryPrice: '', stopPrice: '', intendedQuantity: '', notes: '' }))
      notify({ tone: 'success', text: 'Intent recorded. It is not treated as a filled trade.' })
      await reload()
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setCreating(false) }
  }

  function startAction(type, trade) {
    setAction({ type, trade })
    setActionPrice(type === 'fill' ? trade.intendedEntryPrice : '')
    setActionQuantity(type === 'fill' ? trade.intendedQuantity : '')
    setBrokerage('0')
    setExitReason('MANUAL')
  }

  async function submitAction(event) {
    event.preventDefault()
    try {
      const { type, trade } = action
      if (type === 'fill') await api(`/api/v1/paper-trades/${trade.id}/fill`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ actualEntryPrice: Number(actionPrice), filledQuantity: Number(actionQuantity) }) })
      if (type === 'close') await api(`/api/v1/paper-trades/${trade.id}/close`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ actualExitPrice: Number(actionPrice), brokerage: Number(brokerage), exitReason }) })
      if (type === 'cancel') await api(`/api/v1/paper-trades/${trade.id}/cancel`, { method: 'POST' })
      notify({ tone: 'success', text: type === 'fill' ? 'Actual fill recorded.' : type === 'close' ? 'Exit and realised P&L recorded.' : 'Intent cancelled.' })
      setAction(null)
      await reload()
    } catch (error) { notify({ tone: 'error', text: error.message }) }
  }

  return <section className="journal-page">
    <div className="journal-metrics">
      <Metric label="Today’s realised P&L" value={money(summary?.realisedPnl)} detail="After recorded charges" accent={Number(summary?.realisedPnl) < 0 ? 'red' : ''} />
      <Metric label="Active risk" value={money(summary?.activeRisk)} detail="Intended + open trade risk" accent="orange" />
      <Metric label="Remaining daily capacity" value={money(summary?.remainingDailyRiskCapacity)} detail={`Of ${money(summary?.maxDailyLoss)} daily limit`} />
      <Metric label="Session activity" value={`${summary?.initiatedTrades || 0} intents / ${summary?.openPositions || 0} open`} detail="Today’s journal" />
      <Metric label="Win rate" value={`${report?.winRate || 0}%`} detail={`${report?.winningTrades || 0} wins / ${report?.closedTrades || 0} closed`} />
      <Metric label="Entry slippage" value={money(report?.totalSlippage)} detail="Adverse slippage is positive" accent={Number(report?.totalSlippage) > 0 ? 'orange' : ''} />
    </div>
    <div className="journal-grid">
      <form className="panel intent-form" onSubmit={createIntent}>
        <div className="panel-heading"><div><p className="eyebrow">RECORD DECISION</p><h2>New trade intent</h2><p>This captures the planned trade. It does not create a fill.</p></div></div>
        <div className="field-grid">
          <Field label="NSE symbol"><input value={form.symbol} maxLength="32" onChange={e => setForm(c => ({ ...c, symbol: e.target.value }))} /></Field>
          <Field label="Side"><select value={form.side} onChange={e => setForm(c => ({ ...c, side: e.target.value }))}><option>BUY</option><option>SELL</option></select></Field>
          <Field label="Intended entry (₹)"><input required type="number" min="0" step="0.05" value={form.intendedEntryPrice} onChange={e => setForm(c => ({ ...c, intendedEntryPrice: e.target.value }))} /></Field>
          <Field label="Hard stop (₹)"><input required type="number" min="0" step="0.05" value={form.stopPrice} onChange={e => setForm(c => ({ ...c, stopPrice: e.target.value }))} /></Field>
          <Field label="Intended quantity"><input required type="number" min="1" step="1" value={form.intendedQuantity} onChange={e => setForm(c => ({ ...c, intendedQuantity: e.target.value }))} /></Field>
          <Field label="Strategy label"><input maxLength="80" placeholder="Opening-range breakout" value={form.strategy} onChange={e => setForm(c => ({ ...c, strategy: e.target.value }))} /></Field>
        </div>
        <Field label="Notes"><textarea maxLength="500" placeholder="Why this setup qualified…" value={form.notes} onChange={e => setForm(c => ({ ...c, notes: e.target.value }))} /></Field>
        <button className="primary full" disabled={creating}>{creating ? 'Saving…' : 'Record intended trade'} <span>→</span></button>
      </form>
      <div className="panel journal-help"><p className="eyebrow">LIFECYCLE</p><h2>Intent ≠ fill</h2><ol><li><b>INTENDED</b> — approved plan and proposed risk.</li><li><b>OPEN</b> — you record actual fill price and quantity.</li><li><b>CLOSED</b> — you record actual exit, charges and P&L.</li></ol><p>There is no assumed fill price in this journal.</p></div>
    </div>
    <section className="panel table-panel"><div className="panel-heading"><div><p className="eyebrow">TODAY</p><h2>Trade history</h2></div><div className="row-actions"><button className="ghost" onClick={reload}>↻ Refresh</button><button className="ghost" onClick={() => window.open(`${apiBase}/api/v1/paper-trades/export`, '_blank')}>↓ Export CSV</button></div></div>
      {loading ? <p className="empty">Loading journal…</p> : trades.length === 0 ? <p className="empty">No intents recorded today. Start by recording a risk-checked setup.</p> : <div className="trade-table-wrap"><table><thead><tr><th>Symbol</th><th>Side</th><th>Status</th><th>Intended</th><th>Actual</th><th>Slippage</th><th>Net P&L</th><th /></tr></thead><tbody>{trades.map(trade => <tr key={trade.id}><td><strong>{trade.symbol}</strong><small>{trade.strategy || 'No strategy label'}</small></td><td className={trade.side === 'BUY' ? 'buy' : 'sell'}>{trade.side}</td><td><span className={`status ${trade.status.toLowerCase()}`}>{trade.status}</span></td><td>₹{trade.intendedEntryPrice} × {trade.intendedQuantity}<small>Stop ₹{trade.stopPrice}</small></td><td>{trade.actualEntryPrice ? <>₹{trade.actualEntryPrice} × {trade.filledQuantity}<small>{trade.actualExitPrice ? `Exit ₹${trade.actualExitPrice}` : 'Open position'}</small></> : '—'}</td><td>{trade.slippageAmount == null ? '—' : money(trade.slippageAmount)}<small>{trade.slippagePerUnit == null ? '' : `₹${trade.slippagePerUnit}/unit`}</small></td><td className={Number(trade.netPnl) < 0 ? 'loss' : 'profit'}>{trade.netPnl == null ? '—' : money(trade.netPnl)}</td><td className="row-actions">{trade.status === 'INTENDED' && <><button onClick={() => startAction('fill', trade)}>Fill</button><button onClick={() => { if (window.confirm(`Cancel ${trade.symbol} intent?`)) startAction('cancel', trade) }}>Cancel</button></>}{trade.status === 'OPEN' && <button onClick={() => startAction('close', trade)}>Close</button>}</td></tr>)}</tbody></table></div>}
    </section>
    {action && <div className="modal-backdrop"><form className="modal" onSubmit={submitAction}><button type="button" className="modal-close" onClick={() => setAction(null)}>×</button><p className="eyebrow">{action.type.toUpperCase()} {action.trade.symbol}</p><h2>{action.type === 'fill' ? 'Record actual fill' : action.type === 'close' ? 'Record actual exit' : 'Cancel trade intent'}</h2>{action.type === 'cancel' ? <p>Confirm that no fill occurred. This action cannot be reversed.</p> : <div className="modal-fields"><Field label={action.type === 'fill' ? 'Actual entry price (₹)' : 'Actual exit price (₹)'}><input required autoFocus type="number" min="0" step="0.05" value={actionPrice} onChange={e => setActionPrice(e.target.value)} /></Field>{action.type === 'fill' && <Field label="Actual filled quantity"><input required type="number" min="1" max={action.trade.intendedQuantity} value={actionQuantity} onChange={e => setActionQuantity(e.target.value)} /></Field>}{action.type === 'close' && <><Field label="Brokerage and charges (₹)"><input required type="number" min="0" step="0.01" value={brokerage} onChange={e => setBrokerage(e.target.value)} /></Field><Field label="Exit reason"><select value={exitReason} onChange={e => setExitReason(e.target.value)}><option>STOP_LOSS</option><option>TARGET</option><option>MANUAL</option><option>END_OF_DAY</option></select></Field></>}</div>}<button className={action.type === 'cancel' ? 'danger-button full' : 'primary full'}>{action.type === 'fill' ? 'Confirm actual fill' : action.type === 'close' ? 'Record exit and P&L' : 'Confirm cancellation'}</button></form></div>}
  </section>
}

createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>)
