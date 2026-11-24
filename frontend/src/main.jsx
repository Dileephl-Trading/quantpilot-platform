import { StrictMode, useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Alert, Box, Button, Card, CardContent, Chip, Divider, Paper, Stack, Step, StepLabel, Stepper, Switch, Tab, Tabs, TextField, ThemeProvider, Typography, createTheme } from '@mui/material'
import './styles.css'

const apiBase = import.meta.env.VITE_API_BASE_URL || ''

const marketTheme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#078f9f' },
    secondary: { main: '#6750a4' },
    success: { main: '#16866f' },
    warning: { main: '#b56b00' },
    background: { default: '#edf2f8', paper: '#f8faff' },
    text: { primary: '#223451', secondary: '#667793' }
  },
  shape: { borderRadius: 16 },
  typography: {
    fontFamily: 'Inter, Manrope, Arial, sans-serif',
    h4: { fontWeight: 800, letterSpacing: '-.03em' },
    h5: { fontWeight: 800, letterSpacing: '-.025em' },
    h6: { fontWeight: 750, letterSpacing: '-.02em' }
  },
  components: {
    MuiCard: { styleOverrides: { root: { border: '1px solid rgba(108, 128, 158, .18)', boxShadow: '0 18px 45px rgba(43, 66, 99, .12)' } } },
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { borderRadius: 11, textTransform: 'none', fontWeight: 750, minHeight: 42 } } },
    MuiTextField: { defaultProps: { size: 'small' } },
    MuiChip: { styleOverrides: { root: { fontWeight: 700 } } }
  }
})

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

function App() {
  const [profile, setProfile] = useState(null)
  const [draft, setDraft] = useState(null)
  const [order, setOrder] = useState(initialOrder)
  const [preview, setPreview] = useState(null)
  const [active, setActive] = useState('overview')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [checking, setChecking] = useState(false)
  const [notice, setNotice] = useState(null)
  const [kiteStatus, setKiteStatus] = useState(null)
  const [marketSession, setMarketSession] = useState(null)
  const [execution, setExecution] = useState(initialExecution)
  const [executionOrders, setExecutionOrders] = useState([])
  const [executionBusy, setExecutionBusy] = useState(false)
  const [openGroups, setOpenGroups] = useState({ trading: true, records: true, market: true, configuration: true })
  const [authState, setAuthState] = useState('checking')
  const [authError, setAuthError] = useState('')

  const localRisk = useMemo(() => {
    const entry = Number(order.entryPrice)
    const stop = Number(order.stopPrice)
    const quantity = Number(order.quantity)
    return Number.isFinite(entry) && Number.isFinite(stop) && Number.isFinite(quantity)
      ? Math.abs(entry - stop) * quantity : 0
  }, [order])

  async function request(path, options) {
    const response = await fetch(`${apiBase}${path}`, options)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      const error = new Error(data.message || data.error || 'Request failed')
      error.status = response.status
      throw error
    }
    return data
  }

  useEffect(() => {
    request('/api/v1/auth/me')
      .then(() => setAuthState('authenticated'))
      .catch(error => {
        if (error.status === 401 || error.status === 403) setAuthState('unauthenticated')
        else { setAuthError(error.message); setAuthState('unavailable') }
      })
  }, [])

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
    if (authState === 'authenticated') loadProfile()
  }, [authState])

  async function loadKiteStatus() {
    try {
      setKiteStatus(await request('/api/v1/integrations/zerodha/status'))
    } catch (error) {
      setKiteStatus({ state: 'UNAVAILABLE', nextStep: error.message, executionState: 'DISABLED' })
    }
  }

  useEffect(() => {
    if (authState === 'authenticated') loadKiteStatus()
  }, [authState])

  async function loadMarketSession() {
    try { setMarketSession(await request('/api/v1/market-session/status')) } catch (_) { setMarketSession(null) }
  }

  useEffect(() => {
    if (authState !== 'authenticated') return undefined
    loadMarketSession()
    const timer = window.setInterval(loadMarketSession, 30000)
    return () => window.clearInterval(timer)
  }, [authState])

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
  const toggleGroup = (group) => setOpenGroups(current => ({ ...current, [group]: !current[group] }))

  async function refreshExecution() {
    try { setExecutionOrders(await request('/api/v1/execution/orders')) } catch (error) { setNotice({ tone: 'error', text: error.message }) }
  }

  useEffect(() => { if (active === 'execution' && authState === 'authenticated') refreshExecution() }, [active, authState])

  if (authState === 'checking') return <div className="auth-loading">Checking secure session…</div>
  if (authState === 'unauthenticated') return <LoginPage onSuccess={() => { setAuthError(''); setAuthState('authenticated'); loadProfile(); loadKiteStatus() }} />
  if (authState === 'unavailable') return <div className="auth-loading"><div className="login-card"><h1>Signal<span>Desk</span></h1><p>Authentication service is unavailable.</p><small>{authError}</small><button className="primary" onClick={() => window.location.reload()}>Retry</button></div></div>

  return <div className="app-shell">
    <aside className="sidebar">
      <a className="brand" href="#overview" onClick={() => setActive('overview')}>
        <span className="brand-mark">S</span><span>Signal<span>Desk</span></span>
      </a>
      <p className="sidebar-label">WORKSPACE</p>
      <nav className="workspace-nav">
        <button className={active === 'overview' ? 'nav-link active' : 'nav-link'} onClick={() => setActive('overview')}><span>▦</span> Overview</button>
        <MenuGroup label="Trading" icon="⇄" open={openGroups.trading} active={active === 'risk' || active === 'execution'} onToggle={() => toggleGroup('trading')}>
          <MenuItem active={active === 'risk'} onClick={() => setActive('risk')} icon="◈">Risk preview</MenuItem>
          <MenuItem active={active === 'execution'} onClick={() => setActive('execution')} icon="⇄">Execution</MenuItem>
        </MenuGroup>
        <MenuGroup label="Records" icon="▤" open={openGroups.records} active={active === 'audit' || active === 'journal'} onToggle={() => toggleGroup('records')}>
          <MenuItem active={active === 'audit'} onClick={() => setActive('audit')} icon="▥">Audit log</MenuItem>
          <MenuItem active={active === 'journal'} onClick={() => setActive('journal')} icon="▤">Trade journal</MenuItem>
        </MenuGroup>
        <MenuGroup label="Market data" icon="◌" open={openGroups.market} active={active === 'integration'} onToggle={() => toggleGroup('market')}>
          <MenuItem active={active === 'integration'} onClick={() => setActive('integration')} icon="◌">Connection & data</MenuItem>
        </MenuGroup>
        <MenuGroup label="Configuration" icon="⚙" open={openGroups.configuration} active={active === 'settings'} onToggle={() => toggleGroup('configuration')}>
          <MenuItem active={active === 'settings'} onClick={() => setActive('settings')} icon="⚙">Trading profile</MenuItem>
        </MenuGroup>
      </nav>
      <div className="sidebar-footer">
        <div className="paper-chip"><span className="dot" /> PAPER MODE</div>
        <p>Your browser talks only to the local API.</p>
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

        {active === 'integration' && <KiteIntegration status={kiteStatus} refresh={loadKiteStatus} notify={setNotice} />}

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
function LoginPage({ onSuccess }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const response = await fetch(`${apiBase}/api/v1/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include', body: JSON.stringify({ username, password }) })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.message || data.error || 'Invalid username or password')
      onSuccess()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  return <main className="auth-loading"><form className="login-card" onSubmit={submit}>
    <div className="login-brand"><span className="brand-mark">S</span><strong>Signal<span>Desk</span></strong></div>
    <p className="eyebrow">SECURE ACCESS</p><h1>Welcome back</h1><p className="login-copy">Sign in to continue to your private market research workspace.</p>
    {error && <div className="login-error">{error}</div>}
    <label className="field"><span>Username</span><input autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} required /></label>
    <label className="field"><span>Password</span><input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required /></label>
    <button className="primary full" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'} <span>→</span></button>
    <small className="login-foot">Private session protected · broker execution remains gated</small>
  </form></main>
}
function MenuGroup({ label, icon, open, active, onToggle, children }) {
  return <div className={`menu-group ${active ? 'group-active' : ''}`}>
    <button className="menu-group-toggle" onClick={onToggle} aria-expanded={open}>
      <span className="menu-group-icon">{icon}</span><span>{label}</span><span className="menu-chevron">{open ? '⌄' : '›'}</span>
    </button>
    {open && <div className="submenu">{children}</div>}
  </div>
}
function MenuItem({ active, onClick, icon, children }) {
  return <button className={active ? 'nav-link submenu-link active' : 'nav-link submenu-link'} onClick={onClick}><span>{icon}</span>{children}</button>
}

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

function KiteIntegration({ status, refresh, notify }) {
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
  const [marketTab, setMarketTab] = useState('connection')
  const [hasSyncedSymbols, setHasSyncedSymbols] = useState(null)
  const [symbolMessage, setSymbolMessage] = useState('')
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

  useEffect(() => {
    if (!marketDataReady) { setHasSyncedSymbols(false); return }
    fetch(`${apiBase}/api/v1/market-data/instruments?query=`)
      .then(response => response.ok ? response.json() : [])
      .then(items => setHasSyncedSymbols(Array.isArray(items) && items.length > 0))
      .catch(() => setHasSyncedSymbols(false))
  }, [marketDataReady])

  async function syncInstruments() {
    setSyncing(true)
    try {
      const response = await fetch(`${apiBase}/api/v1/market-data/instruments/sync`, { method: 'POST' })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.message || 'Instrument sync failed')
      setSyncResult(data)
      setHasSyncedSymbols(true)
      notify({ tone: 'success', text: `${data.savedInstruments} NSE cash symbols synced locally.` })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setSyncing(false) }
  }

  async function searchInstruments(event) {
    event.preventDefault()
    if (!marketDataReady) { setSymbolMessage('Connect Kite first. Symbol search uses the synced Kite instrument catalogue.'); return }
    if (hasSyncedSymbols === false) { setSymbolMessage('Please sync NSE symbols first, then search by symbol or sector.'); return }
    if (hasSyncedSymbols === null) { setSymbolMessage('Checking the local symbol catalogue…'); return }
    setSymbolMessage('')
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

  async function addSelectedToMonitor() {
    if (!selected) return
    try {
      const response = await fetch(`${apiBase}/api/v1/live-market/watchlist`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ instrumentToken: selected.instrumentToken })
      })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.message || 'Could not add symbol to live monitor')
      notify({ tone: 'success', text: `${selected.tradingsymbol} added to Live monitor.` })
    } catch (error) { notify({ tone: 'error', text: error.message }) }
  }

  const configured = status?.state === 'READY_FOR_MARKET_DATA'
  const marketTabs = [['connection', 'Connection'], ['symbols', 'Symbols & candles'], ['live', 'Live monitor'], ['rules', 'Scanner rules']]
  return <ThemeProvider theme={marketTheme}>
    <Box className="integration-page" sx={{ minWidth: 0, gap: 2.5 }}>
      <Tabs value={marketTab} onChange={(_, value) => setMarketTab(value)} variant="scrollable" scrollButtons="auto" allowScrollButtonsMobile sx={{ bgcolor: 'background.paper', borderRadius: 3, px: 1, boxShadow: '0 10px 28px rgba(43,66,99,.10)' }}>
        {marketTabs.map(([key, label]) => <Tab key={key} value={key} label={label} />)}
      </Tabs>
      <Alert severity="info" variant="outlined" sx={{ borderRadius: 3, bgcolor: 'rgba(232,248,251,.75)' }}>
        <Typography variant="subtitle2" fontWeight={800}>Market data and broker routing are separately gated.</Typography>
        <Typography variant="body2">Order placement, modification, and cancellation are available only from the Execution screen after every explicit safety gate passes.</Typography>
      </Alert>

      {marketTab === 'connection' && <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1.25fr .9fr' }, gap: 2.5 }}>
        <Card><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={2} mb={2.5}>
            <Box><Typography variant="overline" color="text.secondary" letterSpacing=".14em">ZERODHA KITE CONNECT</Typography><Typography variant="h5">{status?.state?.replaceAll('_', ' ') || 'Checking configuration'}</Typography></Box>
            <Chip label={status?.executionState || 'DISABLED'} color={configured ? 'success' : 'default'} size="small" />
          </Stack>
          <Stack divider={<Divider flexItem />}>
            <IntegrationRow label="API key" value={status?.apiKeyConfigured ? 'Configured locally' : 'Not configured'} ok={status?.apiKeyConfigured} />
            <IntegrationRow label="API secret" value={status?.apiSecretConfigured ? 'Configured locally' : 'Not configured'} ok={status?.apiSecretConfigured} />
            <IntegrationRow label="Session token" value={status?.accessTokenConfigured ? 'Connected for current run' : 'Not connected for today'} ok={status?.accessTokenConfigured} />
            <IntegrationRow label="Live market data" value={status?.marketDataEnabled ? 'Explicitly enabled' : 'Disabled'} ok={status?.marketDataEnabled} />
          </Stack>
          <Alert severity={status?.nextStep ? 'warning' : 'info'} sx={{ mt: 2.5, borderRadius: 2 }}>{status?.nextStep || 'Loading local connection state…'}</Alert>
          <Stack direction="row" gap={1.5} mt={2.5} flexWrap="wrap"><Button variant="outlined" onClick={refresh}>Refresh state</Button><Button variant="contained" onClick={connectKite} disabled={loadingUrl || !status?.apiKeyConfigured || !status?.apiSecretConfigured}>{loadingUrl ? 'Opening Kite…' : 'Connect Kite'}</Button></Stack>
        </CardContent></Card>
        <Card><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><Typography variant="overline" color="text.secondary" letterSpacing=".14em">SAFE ACTIVATION PATH</Typography><Typography variant="h5" mb={2}>What happens after purchase</Typography><Stepper orientation="vertical" activeStep={-1}>{['Add credentials only to your uncommitted local .env file.', 'Use Kite login to create that day’s session token.', 'Verify instrument import and a historical-candle request.', 'Enable live WebSocket data only after both tests pass.'].map(step => <Step key={step}><StepLabel>{step}</StepLabel></Step>)}</Stepper><Typography variant="body2" color="text.secondary" mt={2}>API secret and access token never appear in this page or in browser storage.</Typography></CardContent></Card>
      </Box>}

      {marketTab === 'symbols' && <Card><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2} mb={2.5}><Box><Typography variant="overline" color="text.secondary" letterSpacing=".14em">READ-ONLY DATA VERIFICATION</Typography><Typography variant="h5">Sync symbols and inspect completed candles</Typography><Typography variant="body2" color="text.secondary">Only synced NSE cash equities are available for search, evaluation, and live monitoring.</Typography></Box><Chip label={marketDataReady ? 'Ready to verify' : 'Kite connection required'} color={marketDataReady ? 'success' : 'warning'} /></Stack>
        {!marketDataReady && <Alert severity="warning" sx={{ mb: 2 }}>Connect Kite first. Symbol synchronization uses Kite’s instrument master.</Alert>}
        <Button variant="contained" onClick={syncInstruments} disabled={!marketDataReady || syncing}>{syncing ? 'Syncing symbols…' : 'Sync NSE cash symbols'}</Button>{syncResult && <Typography component="span" variant="body2" color="text.secondary" ml={2}>{syncResult.savedInstruments} symbols synced</Typography>}
        {symbolMessage && <Alert severity={hasSyncedSymbols === false ? 'info' : 'warning'} sx={{ mt: 2 }} action={hasSyncedSymbols === false && <Button color="inherit" size="small" onClick={syncInstruments} disabled={syncing}>{syncing ? 'Syncing…' : 'Sync now'}</Button>}>{symbolMessage}</Alert>}
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '280px 1fr' }, gap: 2.5, mt: 3, pt: 3, borderTop: '1px solid rgba(108,128,158,.18)' }}>
          <Box component="form" onSubmit={searchInstruments} sx={{ display: 'grid', gap: 1.5, alignContent: 'start' }}><TextField label="Search synced NSE symbol" value={symbolQuery} placeholder="SBIN" onChange={event => setSymbolQuery(event.target.value.toUpperCase())} /><TextField select SelectProps={{ native: true }} label="Sector filter" value={sectorFilter} onChange={event => setSectorFilter(event.target.value)}><option value="">All sectors</option><option value="BANKING">Banking</option><option value="IT">IT</option><option value="AUTO">Auto</option><option value="ENERGY">Energy</option><option value="PHARMA">Pharma</option><option value="FMCG">FMCG</option><option value="OTHER">Other</option></TextField><Button type="submit" variant="outlined" disabled={!marketDataReady}>Search symbols</Button></Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2,1fr)' }, gap: 1.25, alignContent: 'start', maxHeight: 330, overflowY: 'auto', pr: .5 }}>{instruments.length === 0 ? <Alert severity="info">Sync the NSE symbols first, then search here.</Alert> : instruments.map(item => <Paper key={item.instrumentToken} component="button" type="button" onClick={() => setSelected(item)} sx={{ textAlign: 'left', p: 1.5, cursor: 'pointer', border: selected?.instrumentToken === item.instrumentToken ? '2px solid #078f9f' : '1px solid rgba(108,128,158,.18)', bgcolor: 'background.paper', borderRadius: 2 }}><Typography fontWeight={800}>{item.tradingsymbol}</Typography><Typography variant="caption" color="text.secondary">{item.sector || 'OTHER'} · {item.name || item.segment}</Typography></Paper>)}</Box>
        </Box>
        {selected && <Paper sx={{ mt: 3, p: 2, display: 'flex', alignItems: { xs: 'stretch', md: 'flex-end' }, gap: 1.5, flexWrap: 'wrap', bgcolor: '#f2f8fb' }}><Box sx={{ mr: 'auto' }}><Typography variant="overline" color="text.secondary">SELECTED SYMBOL</Typography><Typography fontWeight={800}>{selected.tradingsymbol}</Typography><Typography variant="body2" color="text.secondary">{selected.sector || 'OTHER'} · ready for monitoring</Typography></Box><Button variant="outlined" onClick={addSelectedToMonitor}>Add to Live monitor</Button><TextField type="date" label="Trading date" InputLabelProps={{ shrink: true }} value={tradeDate} onChange={event => setTradeDate(event.target.value)} /><Button variant="contained" onClick={verifyHistorical} disabled={verifying || !marketDataReady}>{verifying ? 'Fetching…' : 'Fetch and evaluate'}</Button></Paper>}
        {verification && <Alert severity="success" sx={{ mt: 2 }}>Completed candles: <b>{verification.count}</b> · Result: <b>{verification.signal.type}</b> · Reference: <b>{money(verification.signal.referencePrice)}</b><br />{verification.signal.reasons?.join(' · ')}</Alert>}
      </CardContent></Card>}
      {marketTab === 'live' && <LiveStreamMUI selected={selected} marketDataReady={marketDataReady} notify={notify} onNavigate={setMarketTab} />}
      {marketTab === 'rules' && <ScannerSettings marketDataReady={marketDataReady} notify={notify} />}
      {marketTab === 'connection' && <Card><CardContent><Typography variant="overline" color="text.secondary" letterSpacing=".14em">IMPLEMENTED CONTRACTS</Typography><Typography variant="h6" mb={2}>Ready for credential-backed verification</Typography><Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3,1fr)' }, gap: 1.5 }}>{[['GET /instruments', 'Downloads the published instrument CSV.'], ['Historical candles', 'Retrieves provider-neutral completed candles.'], ['Kite OAuth session flow', 'Creates the daily encrypted session token.']].map(([title, detail]) => <Paper key={title} sx={{ p: 2, bgcolor: 'background.default' }}><Typography fontWeight={800}>{title}</Typography><Typography variant="body2" color="text.secondary" mt={.5}>{detail}</Typography></Paper>)}</Box></CardContent></Card>}
    </Box>
  </ThemeProvider>
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
  return <Card><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}>
    <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={2} mb={3}><Box><Typography variant="overline" color="text.secondary" letterSpacing=".14em">STRATEGY CONFIGURATION</Typography><Typography variant="h5">Scanner rules</Typography><Typography variant="body2" color="text.secondary">These values control signal evaluation only; they never place broker orders.</Typography></Box><Chip label={config.enabled ? 'ENABLED' : 'DISABLED'} color={config.enabled ? 'success' : 'default'} /></Stack>
    <Box component="form" onSubmit={save} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)' }, gap: 2 }}>
      <Paper sx={{ p: 1.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gridColumn: { sm: '1 / -1' }, bgcolor: '#f4f7fb' }}><Box><Typography fontWeight={800}>Strategy enabled</Typography><Typography variant="caption" color="text.secondary">Allow completed candles to be evaluated against these rules.</Typography></Box><Switch checked={config.enabled} onChange={event => setConfig({ ...config, enabled: event.target.checked })} /></Paper>
      <TextField label="Opening-range candles" type="number" inputProps={{ min: 1 }} value={config.openingRangeCandles} onChange={event => setConfig({ ...config, openingRangeCandles: event.target.value })} />
      <TextField label="Volume lookback" type="number" inputProps={{ min: 1 }} value={config.volumeLookback} onChange={event => setConfig({ ...config, volumeLookback: event.target.value })} />
      <TextField label="Volume multiplier" type="number" inputProps={{ min: 0.1, step: 0.05 }} value={config.volumeMultiplier} onChange={event => setConfig({ ...config, volumeMultiplier: event.target.value })} />
      <TextField label="Minimum candles" type="number" inputProps={{ min: 3 }} value={config.minimumCandles} onChange={event => setConfig({ ...config, minimumCandles: event.target.value })} />
      <Button type="submit" variant="contained" disabled={saving || !marketDataReady} sx={{ minHeight: 48, gridColumn: { sm: '1 / -1' } }}>{saving ? 'Saving scanner settings…' : 'Save scanner settings'}</Button>
    </Box>
  </CardContent></Card>
}

function LiveStream({ selected, marketDataReady, notify, onNavigate }) {
  const [status, setStatus] = useState(null)
  const [watchlist, setWatchlist] = useState([])
  const [signals, setSignals] = useState([])
  const [candles, setCandles] = useState([])
  const [busy, setBusy] = useState(false)
  const [groups, setGroups] = useState([])
  const [history, setHistory] = useState({ signals: [], candles: [] })
  const [liveSearch, setLiveSearch] = useState('')
  const [liveResults, setLiveResults] = useState([])
  const [liveSearching, setLiveSearching] = useState(false)
  const [hasSyncedSymbols, setHasSyncedSymbols] = useState(null)
  const [dependencyMessage, setDependencyMessage] = useState('')

  async function api(path, options) {
    const response = await fetch(`${apiBase}${path}`, options)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.message || 'Live market-data request failed')
    return data
  }
  async function refresh() {
    try {
      const [nextStatus, nextWatchlist, nextSignals, nextCandles, nextHistory] = await Promise.all([api('/api/v1/live-market/status'), api('/api/v1/live-market/watchlist'), api('/api/v1/live-market/signals'), api('/api/v1/live-market/candles'), api('/api/v1/live-market/history?days=7')])
      setStatus(nextStatus); setWatchlist(nextWatchlist); setSignals(nextSignals); setCandles(nextCandles); setHistory(nextHistory)
    } catch (error) { notify({ tone: 'error', text: error.message }) }
  }
  useEffect(() => { refresh() }, [])
  useEffect(() => {
    if (!marketDataReady) return
    setGroups(['banking', 'bank-nifty', 'it', 'energy', 'auto', 'pharma', 'fmcg'])
  }, [marketDataReady])
  useEffect(() => {
    if (status?.state !== 'CONNECTED') return undefined
    const timer = window.setInterval(refresh, 5000)
    return () => window.clearInterval(timer)
  }, [status?.state])
  async function searchLiveSymbols(event) {
    event.preventDefault()
    const query = liveSearch.trim().toUpperCase()
    if (!marketDataReady) { setDependencyMessage('Connect Kite first. Live monitoring requires an active Kite session.'); return }
    if (hasSyncedSymbols === false) { setDependencyMessage('Sync NSE symbols first, then search here.'); return }
    if (!query) return
    setDependencyMessage('')
    setLiveSearching(true)
    try {
      const results = await api('/api/v1/market-data/instruments?query=' + encodeURIComponent(query))
      setLiveResults(results)
      setDependencyMessage(results.length === 0 ? 'No synced symbol matched this search.' : '')
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setLiveSearching(false) }
  }

  async function addLiveSymbol(instrument) {
    setBusy(true)
    try {
      await api('/api/v1/live-market/watchlist', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ instrumentToken: instrument.instrumentToken }) })
      await refresh()
      setLiveResults([])
      notify({ tone: 'success', text: instrument.tradingsymbol + ' added to the live monitor.' })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }

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
    <div className="dependency-chain"><strong>Live-monitor setup</strong><span className={marketDataReady ? 'ready' : 'missing'}>1. Kite connected</span><span className={hasSyncedSymbols ? 'ready' : 'missing'}>2. NSE symbols synced</span><span>3. Search and add</span><span>4. Start monitor</span>{!marketDataReady && <button className="link-button" onClick={() => onNavigate?.('connection')}>Go to Connection</button>}{marketDataReady && hasSyncedSymbols === false && <button className="link-button" onClick={() => onNavigate?.('symbols')}>Go to Symbols &amp; candles</button>}</div>
    <form className="live-search" onSubmit={searchLiveSymbols}><input aria-label="Search synced NSE symbol for live monitoring" value={liveSearch} placeholder="Search synced NSE symbol (e.g. SBIN, HDFCBANK)" onChange={event => setLiveSearch(event.target.value.toUpperCase())} /><button className="ghost" type="submit" disabled={!liveSearch.trim() || liveSearching}>{liveSearching ? 'Searching…' : 'Search and add'}</button></form>
    {dependencyMessage && <p className="verification-warning">{dependencyMessage} {hasSyncedSymbols === false && marketDataReady && <button className="link-button" onClick={() => onNavigate?.('symbols')}>Sync symbols first</button>}</p>}
    {liveResults.length > 0 && <div className="live-search-results">{liveResults.map(item => <div className="live-search-result" key={item.instrumentToken}><div><strong>{item.tradingsymbol}</strong><small>{item.sector || 'OTHER'} · {item.name || item.segment}</small></div><button className="primary" onClick={() => addLiveSymbol(item)} disabled={busy}>Add</button></div>)}</div>}
    <div className="live-actions"><button className="ghost" onClick={refresh}>↻ Refresh</button><button className="ghost" onClick={addSelected} disabled={!marketDataReady || !selected || busy}>{selected ? `Add ${selected.tradingsymbol}` : 'Select a symbol above'}</button>{groups.map(group => <button key={group} className="ghost group-button" onClick={() => addGroup(group)} disabled={!marketDataReady || busy}>{group === 'bank-nifty' ? 'Add Bank Nifty' : `Add ${group.toUpperCase()}`}</button>)}{connected ? <button className="danger-button" onClick={() => toggleStream('/api/v1/live-market/stop')} disabled={busy}>Stop monitor</button> : <button className="primary" onClick={() => toggleStream('/api/v1/live-market/start')} disabled={!marketDataReady || watchlist.length === 0 || busy}>{busy ? 'Working…' : 'Start monitor'}</button>}</div>
    <div className="live-grid"><div><span>Watchlist and latest ticks · {watchlist.length} symbols</span>{watchlist.length === 0 ? <p>No selected symbols yet.</p> : <ul>{watchlist.map(item => <li key={item.instrumentToken}><div><strong>{item.symbol}</strong><small>{status?.latestPrices?.[item.instrumentToken] ? `${money(status.latestPrices[item.instrumentToken])} · tick ${status.lastTickAt ? new Date(status.lastTickAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : 'pending'}` : 'Waiting for first tick'}</small></div><button className="watchlist-remove" onClick={() => removeSymbol(item.instrumentToken, item.symbol)} disabled={busy || connected}>Remove</button></li>)}</ul>}{connected && watchlist.length > 0 && <small className="watchlist-note">Stop the monitor before removing symbols.</small>}</div><div><span>Signal history · actionable only</span>{signals.length === 0 ? <p>{connected ? 'No actionable signal yet. Completed NO_TRADE evaluations are shown below.' : 'Start the monitor after adding symbols.'}</p> : <ul>{signals.slice(0, 20).map(signal => <li key={`${signal.instrumentToken}-${signal.candleStart}`} className={signal.type === 'WATCH_LONG' ? 'long-signal' : 'short-signal'}><strong>{signal.symbol} · {signal.type}</strong><small>{money(signal.referencePrice)} · {new Date(signal.candleStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</small></li>)}</ul>}</div></div>
    <div className="candle-panel"><div className="candle-heading"><span>Completed live candles</span><small>{candles.length ? 'Latest candles are shown first' : 'No completed live candle received in this run'}</small></div>{candles.length === 0 ? <p className="empty-candles">The first row appears after the next five-minute boundary receives a tick.</p> : <div className="candle-table-wrap"><table className="candle-table"><thead><tr><th>Symbol / time</th><th>OHLC</th><th>Volume</th><th>Evaluation</th><th>VWAP</th></tr></thead><tbody>{candles.slice(0, 20).map(candle => <tr key={`${candle.instrumentToken}-${candle.candleStart}`}><td><strong>{candle.symbol}</strong><small>{new Date(candle.candleStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</small></td><td>{money(candle.open)} / {money(candle.high)} / {money(candle.low)} / {money(candle.close)}</td><td>{candle.volume.toLocaleString()}</td><td><span className={`candle-evaluation ${candle.evaluationType === 'WATCH_LONG' ? 'long-signal' : candle.evaluationType === 'WATCH_SHORT' ? 'short-signal' : ''}`}>{candle.evaluationType}</span></td><td>{candle.vwap ? money(candle.vwap) : '—'}</td></tr>)}</tbody></table></div>}</div>
    {status?.lastError && <p className="live-error">{status.lastError}</p>}
    <details className="monitor-history"><summary>Last 7 days · persisted monitor history ({history.signals?.length || 0} signals, {history.candles?.length || 0} candles)</summary><div className="history-grid"><div><strong>Signals</strong>{history.signals?.length ? <ul>{history.signals.slice(0, 30).map(item => <li key={item.instrumentToken + '-' + item.candleStart}><b>{item.symbol} · {item.type}</b><small>{new Date(item.candleStart).toLocaleString()}</small></li>)}</ul> : <p>No signal history.</p>}</div><div><strong>Completed candles</strong><p>{history.candles?.length || 0} persisted candle evaluations are retained for review. Older records remain stored and are outside this seven-day view.</p></div></div></details>
  </section>
}

function LiveStreamMUI({ selected, marketDataReady, notify, onNavigate }) {
  const [status, setStatus] = useState(null)
  const [watchlist, setWatchlist] = useState([])
  const [signals, setSignals] = useState([])
  const [candles, setCandles] = useState([])
  const [history, setHistory] = useState({ signals: [], candles: [] })
  const [groups] = useState(['banking', 'bank-nifty', 'it', 'energy', 'auto', 'pharma', 'fmcg'])
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [hasSyncedSymbols, setHasSyncedSymbols] = useState(null)
  const [busy, setBusy] = useState(false)
  const [searching, setSearching] = useState(false)
  const [message, setMessage] = useState('')

  async function api(path, options) {
    const response = await fetch(`${apiBase}${path}`, options)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.message || data.error || 'Live market-data request failed')
    return data
  }

  async function refresh() {
    try {
      const [nextStatus, nextWatchlist, nextSignals, nextCandles, nextHistory] = await Promise.all([
        api('/api/v1/live-market/status'), api('/api/v1/live-market/watchlist'),
        api('/api/v1/live-market/signals'), api('/api/v1/live-market/candles'),
        api('/api/v1/live-market/history?days=7')
      ])
      setStatus(nextStatus); setWatchlist(nextWatchlist); setSignals(nextSignals); setCandles(nextCandles); setHistory(nextHistory)
    } catch (error) { notify({ tone: 'error', text: error.message }) }
  }

  useEffect(() => { refresh() }, [])
  useEffect(() => {
    if (!marketDataReady) { setHasSyncedSymbols(false); return }
    api('/api/v1/market-data/instruments?query=')
      .then(items => setHasSyncedSymbols(Array.isArray(items) && items.length > 0))
      .catch(() => setHasSyncedSymbols(false))
  }, [marketDataReady])
  useEffect(() => {
    if (status?.state !== 'CONNECTED') return undefined
    const timer = window.setInterval(refresh, 5000)
    return () => window.clearInterval(timer)
  }, [status?.state])

  async function search(event) {
    event.preventDefault()
    if (!marketDataReady) { setMessage('Connect Kite first.'); return }
    if (hasSyncedSymbols === false) { setMessage('Sync NSE symbols first.'); return }
    if (!query.trim()) return
    setSearching(true); setMessage('')
    try {
      const next = await api('/api/v1/market-data/instruments?query=' + encodeURIComponent(query.trim().toUpperCase()))
      setResults(next); if (!next.length) setMessage('No synced NSE symbol matched this search.')
    } catch (error) { setMessage(error.message) } finally { setSearching(false) }
  }

  async function addSymbol(instrument) {
    setBusy(true)
    try { await api('/api/v1/live-market/watchlist', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ instrumentToken: instrument.instrumentToken }) }); await refresh(); setResults([]); notify({ tone: 'success', text: `${instrument.tradingsymbol} added to the live monitor.` }) }
    catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  async function addSelected() { if (selected) await addSymbol(selected) }
  async function removeSymbol(token, symbol) {
    setBusy(true)
    try { await api(`/api/v1/live-market/watchlist/${token}`, { method: 'DELETE' }); await refresh(); notify({ tone: 'success', text: `${symbol} removed from the watchlist.` }) }
    catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  async function addGroup(group) {
    setBusy(true)
    try { await api(`/api/v1/live-market/watchlist/groups/${group}`, { method: 'POST' }); await refresh(); notify({ tone: 'success', text: `${group} group added to the watchlist.` }) }
    catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }
  async function toggle(path) {
    setBusy(true)
    try { await api(path, { method: 'POST' }); await refresh() } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) }
  }

  const connected = status?.state === 'CONNECTED'
  return <ThemeProvider theme={marketTheme}>
    <Card className="live-card"><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={2} mb={2}><Box><Typography variant="overline" color="text.secondary" letterSpacing=".14em">LIVE WATCHLIST · READ ONLY</Typography><Typography variant="h5">Completed-candle signal monitor</Typography><Typography variant="body2" color="text.secondary">Signals are generated only after a five-minute candle closes. They are alerts, never orders.</Typography></Box><Chip label={status?.state || 'STOPPED'} color={connected ? 'success' : 'default'} /></Stack>
      <Paper sx={{ p: 2, mb: 2.5, bgcolor: '#f4f7fb' }}><Typography variant="subtitle2" fontWeight={800} mb={1.5}>Live-monitor setup</Typography><Stepper activeStep={!marketDataReady ? 0 : hasSyncedSymbols ? 2 : 1} alternativeLabel sx={{ '& .MuiStepLabel-label': { fontSize: { xs: 10, sm: 12 } } }}><Step><StepLabel>Kite connected</StepLabel></Step><Step><StepLabel>NSE symbols synced</StepLabel></Step><Step><StepLabel>Search and add</StepLabel></Step><Step><StepLabel>Start monitor</StepLabel></Step></Stepper>{!marketDataReady && <Alert severity="warning" action={<Button size="small" onClick={() => onNavigate?.('connection')}>Go to Connection</Button>} sx={{ mt: 2 }}>Connect Kite first. Live monitoring requires an active daily session.</Alert>}{marketDataReady && hasSyncedSymbols === false && <Alert severity="info" action={<Button size="small" onClick={() => onNavigate?.('symbols')}>Sync symbols</Button>} sx={{ mt: 2 }}>Sync NSE symbols before searching and adding a stock.</Alert>}{message && <Alert severity="warning" sx={{ mt: 2 }}>{message}</Alert>}</Paper>
      <Box component="form" onSubmit={search} sx={{ display: 'flex', gap: 1.5, mb: 2, flexWrap: 'wrap' }}><TextField fullWidth sx={{ flex: '1 1 340px' }} label="Search synced NSE symbol" placeholder="SBIN, HDFCBANK, INFY" value={query} onChange={event => setQuery(event.target.value.toUpperCase())} /><Button type="submit" variant="contained" disabled={!query.trim() || searching}>{searching ? 'Searching…' : 'Search and add'}</Button></Box>
      {results.length > 0 && <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2,1fr)' }, gap: 1.25, mb: 2, maxHeight: 260, overflowY: 'auto' }}>{results.map(item => <Paper key={item.instrumentToken} sx={{ p: 1.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}><Box><Typography fontWeight={800}>{item.tradingsymbol}</Typography><Typography variant="caption" color="text.secondary">{item.sector || 'OTHER'} · {item.name || item.segment}</Typography></Box><Button size="small" variant="contained" onClick={() => addSymbol(item)} disabled={busy}>Add</Button></Paper>)}</Box>}
      <Stack direction="row" gap={1} flexWrap="wrap" mb={2.5}><Button variant="outlined" onClick={refresh}>Refresh</Button><Button variant="outlined" onClick={addSelected} disabled={!marketDataReady || !selected || busy}>{selected ? `Add ${selected.tradingsymbol}` : 'Select a symbol above'}</Button>{groups.map(group => <Button key={group} variant="outlined" size="small" onClick={() => addGroup(group)} disabled={!marketDataReady || busy}>{group === 'bank-nifty' ? 'Bank Nifty' : group.toUpperCase()}</Button>)}{connected ? <Button color="error" variant="contained" onClick={() => toggle('/api/v1/live-market/stop')} disabled={busy}>Stop monitor</Button> : <Button variant="contained" onClick={() => toggle('/api/v1/live-market/start')} disabled={!marketDataReady || watchlist.length === 0 || busy}>{busy ? 'Working…' : 'Start monitor'}</Button>}</Stack>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2, mb: 2.5 }}><Paper sx={{ p: 2, maxHeight: 300, overflowY: 'auto' }}><Typography variant="overline" color="text.secondary">WATCHLIST · {watchlist.length} SYMBOLS</Typography>{watchlist.length === 0 ? <Typography variant="body2" color="text.secondary" mt={1}>No selected symbols yet.</Typography> : <Stack divider={<Divider flexItem />} mt={1}>{watchlist.map(item => <Stack key={item.instrumentToken} direction="row" justifyContent="space-between" alignItems="center" py={1}><Box><Typography fontWeight={750}>{item.symbol}</Typography><Typography variant="caption" color="text.secondary">{status?.latestPrices?.[item.instrumentToken] ? `${money(status.latestPrices[item.instrumentToken])} · ${status.lastTickAt ? new Date(status.lastTickAt).toLocaleTimeString() : 'tick'}` : 'Waiting for first tick'}</Typography></Box><Button size="small" color="error" onClick={() => removeSymbol(item.instrumentToken, item.symbol)} disabled={busy || connected}>Remove</Button></Stack>)}</Stack>}</Paper><Paper sx={{ p: 2, maxHeight: 300, overflowY: 'auto' }}><Typography variant="overline" color="text.secondary">SIGNAL HISTORY · ACTIONABLE ONLY</Typography>{signals.length === 0 ? <Typography variant="body2" color="text.secondary" mt={1}>{connected ? 'No actionable signal yet.' : 'Start the monitor after adding symbols.'}</Typography> : <Stack divider={<Divider flexItem />} mt={1}>{signals.slice(0, 20).map(signal => <Box key={`${signal.instrumentToken}-${signal.candleStart}`} py={1}><Typography fontWeight={750} color={signal.type === 'WATCH_LONG' ? 'success.main' : 'error.main'}>{signal.symbol} · {signal.type}</Typography><Typography variant="caption" color="text.secondary">{money(signal.referencePrice)} · {new Date(signal.candleStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Typography></Box>)}</Stack>}</Paper></Box>
      <Paper sx={{ p: 2, mb: 2.5 }}><Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}><Typography variant="overline" color="text.secondary">COMPLETED LIVE CANDLES</Typography><Typography variant="caption" color="text.secondary">{candles.length ? 'Latest candles shown first' : 'No completed candle received in this run'}</Typography></Stack>{candles.length === 0 ? <Typography variant="body2" color="text.secondary">The first row appears after the next five-minute boundary receives a tick.</Typography> : <Box sx={{ overflow: 'auto', maxHeight: 350 }}><table className="candle-table"><thead><tr><th>Symbol / time</th><th>OHLC</th><th>Volume</th><th>Evaluation</th><th>VWAP</th></tr></thead><tbody>{candles.slice(0, 30).map(candle => <tr key={`${candle.instrumentToken}-${candle.candleStart}`}><td><strong>{candle.symbol}</strong><small>{new Date(candle.candleStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</small></td><td>{money(candle.open)} / {money(candle.high)} / {money(candle.low)} / {money(candle.close)}</td><td>{candle.volume.toLocaleString()}</td><td>{candle.evaluationType}</td><td>{candle.vwap ? money(candle.vwap) : '—'}</td></tr>)}</tbody></table></Box>}</Paper>
      {status?.lastError && <Alert severity="error" sx={{ mb: 2 }}>{status.lastError}</Alert>}
      <Paper component="details" sx={{ p: 2 }}><Typography component="summary" fontWeight={750} sx={{ cursor: 'pointer' }}>Last 7 days · persisted monitor history ({history.signals?.length || 0} signals, {history.candles?.length || 0} candles)</Typography><Box sx={{ mt: 2, maxHeight: 260, overflowY: 'auto' }}>{history.signals?.length ? history.signals.slice(0, 30).map(item => <Box key={item.instrumentToken + '-' + item.candleStart} py={.75}><Typography variant="body2" fontWeight={700}>{item.symbol} · {item.type}</Typography><Typography variant="caption" color="text.secondary">{new Date(item.candleStart).toLocaleString()}</Typography></Box>) : <Typography variant="body2" color="text.secondary" mt={1}>No signal history.</Typography>}</Box></Paper>
    </CardContent></Card>
  </ThemeProvider>
}

function Journal({ notify }) {
  const [trades, setTrades] = useState([])
  const [summary, setSummary] = useState(null)
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [action, setAction] = useState(null)
  const [replay, setReplay] = useState(null)
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

  async function openReplay(trade) {
    try {
      const events = await api(`/api/v1/paper-trades/${trade.id}/replay`)
      setReplay({ trade, events })
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
      {loading ? <p className="empty">Loading journal…</p> : trades.length === 0 ? <p className="empty">No intents recorded today. Start by recording a risk-checked setup.</p> : <div className="trade-table-wrap"><table><thead><tr><th>Symbol</th><th>Side</th><th>Status</th><th>Intended</th><th>Actual</th><th>Slippage</th><th>Net P&L</th><th /></tr></thead><tbody>{trades.map(trade => <tr key={trade.id}><td><strong>{trade.symbol}</strong><small>{trade.strategy || 'No strategy label'}</small></td><td className={trade.side === 'BUY' ? 'buy' : 'sell'}>{trade.side}</td><td><span className={`status ${trade.status.toLowerCase()}`}>{trade.status}</span></td><td>₹{trade.intendedEntryPrice} × {trade.intendedQuantity}<small>Stop ₹{trade.stopPrice}</small></td><td>{trade.actualEntryPrice ? <>₹{trade.actualEntryPrice} × {trade.filledQuantity}<small>{trade.actualExitPrice ? `Exit ₹${trade.actualExitPrice}` : 'Open position'}</small></> : '—'}</td><td>{trade.slippageAmount == null ? '—' : money(trade.slippageAmount)}<small>{trade.slippagePerUnit == null ? '' : `₹${trade.slippagePerUnit}/unit`}</small></td><td className={Number(trade.netPnl) < 0 ? 'loss' : 'profit'}>{trade.netPnl == null ? '—' : money(trade.netPnl)}</td><td className="row-actions"><button onClick={() => openReplay(trade)}>Replay</button>{trade.status === 'INTENDED' && <><button onClick={() => startAction('fill', trade)}>Fill</button><button onClick={() => { if (window.confirm(`Cancel ${trade.symbol} intent?`)) startAction('cancel', trade) }}>Cancel</button></>}{trade.status === 'OPEN' && <button onClick={() => startAction('close', trade)}>Close</button>}</td></tr>)}</tbody></table></div>}
    </section>
    {action && <div className="modal-backdrop"><form className="modal" onSubmit={submitAction}><button type="button" className="modal-close" onClick={() => setAction(null)}>×</button><p className="eyebrow">{action.type.toUpperCase()} {action.trade.symbol}</p><h2>{action.type === 'fill' ? 'Record actual fill' : action.type === 'close' ? 'Record actual exit' : 'Cancel trade intent'}</h2>{action.type === 'cancel' ? <p>Confirm that no fill occurred. This action cannot be reversed.</p> : <div className="modal-fields"><Field label={action.type === 'fill' ? 'Actual entry price (₹)' : 'Actual exit price (₹)'}><input required autoFocus type="number" min="0" step="0.05" value={actionPrice} onChange={e => setActionPrice(e.target.value)} /></Field>{action.type === 'fill' && <Field label="Actual filled quantity"><input required type="number" min="1" max={action.trade.intendedQuantity} value={actionQuantity} onChange={e => setActionQuantity(e.target.value)} /></Field>}{action.type === 'close' && <><Field label="Brokerage and charges (₹)"><input required type="number" min="0" step="0.01" value={brokerage} onChange={e => setBrokerage(e.target.value)} /></Field><Field label="Exit reason"><select value={exitReason} onChange={e => setExitReason(e.target.value)}><option>STOP_LOSS</option><option>TARGET</option><option>MANUAL</option><option>END_OF_DAY</option></select></Field></>}</div>}<button className={action.type === 'cancel' ? 'danger-button full' : 'primary full'}>{action.type === 'fill' ? 'Confirm actual fill' : action.type === 'close' ? 'Record exit and P&L' : 'Confirm cancellation'}</button></form></div>}
    {replay && <div className="modal-backdrop"><div className="modal replay-modal"><button type="button" className="modal-close" onClick={() => setReplay(null)}>×</button><p className="eyebrow">TRADE REPLAY · {replay.trade.symbol}</p><h2>Recorded lifecycle</h2><p>Replay is derived from persisted journal values; it never changes the trade.</p><div className="replay-timeline">{replay.events.map((event, index) => <div className="replay-event" key={`${event.event}-${index}`}><span className="replay-marker">{index + 1}</span><div><strong>{event.event.replaceAll('_', ' ')}</strong><small>{event.occurredAt ? new Date(event.occurredAt).toLocaleString() : '—'} · {event.details}</small>{event.netPnl != null && <small>Gross {money(event.grossPnl)} · Charges {money(event.charges)} · Net {money(event.netPnl)}</small>}</div></div>)}</div></div></div>}
  </section>
}

createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>)
