import { StrictMode, useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Divider, LinearProgress, Paper, Stack, Step, StepLabel, Stepper, Switch, Tab, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs, TextField, ThemeProvider, Typography, createTheme } from '@mui/material'
import './styles.css'

const apiBase = import.meta.env.VITE_API_BASE_URL || ''

async function apiFetch(path, options = {}) {
  const { suppressAuthRedirect = false, ...fetchOptions } = options
  const response = await fetch(`${apiBase}${path}`, { credentials: 'include', ...fetchOptions })
  if (!suppressAuthRedirect && (response.status === 401 || response.status === 403) && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('quantpilot:auth-expired'))
  }
  return response
}

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
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: 'Inter, Manrope, Arial, sans-serif',
    h4: { fontWeight: 800, letterSpacing: '-.03em' },
    h5: { fontWeight: 800, letterSpacing: '-.025em' },
    h6: { fontWeight: 750, letterSpacing: '-.02em' }
  },
  components: {
    MuiCard: { styleOverrides: { root: { borderRadius: '14px', border: '1px solid rgba(108, 128, 158, .18)', boxShadow: '0 18px 45px rgba(43, 66, 99, .12)' } } },
    MuiPaper: { styleOverrides: { root: { borderRadius: '10px' } } },
    MuiAlert: { styleOverrides: { root: { borderRadius: '10px' } } },
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { borderRadius: 11, textTransform: 'none', fontWeight: 750, minHeight: 42 } } },
    MuiTextField: { defaultProps: { size: 'small', variant: 'outlined' } },
    MuiOutlinedInput: { styleOverrides: { root: { borderRadius: 13, minHeight: 48, '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderWidth: 2 } } } },
    MuiInputLabel: { styleOverrides: { root: { fontWeight: 600 } } },
    MuiCardContent: { styleOverrides: { root: { '&:last-child': { paddingBottom: 28 } } } },
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

// Google Material Symbols' query_stats mark is embedded locally so the logo
// remains available even when external font requests are blocked by CSP.
function QuantPilotMark({ size = 'small' }) {
  const dimension = size === 'large' ? 52 : size === 'medium' ? 42 : 34
  const letterSize = size === 'large' ? 23 : size === 'medium' ? 19 : 16
  return <Box component="span" aria-label="QuantPilotPlatform" sx={{ position: 'relative', display: 'grid', placeItems: 'center', flex: '0 0 auto', width: dimension, height: dimension, overflow: 'hidden', borderRadius: '10px', color: '#fff', background: 'linear-gradient(145deg, #13b8c1, #078f9f)', boxShadow: '5px 5px 10px #c3cedc, -5px -5px 10px #fff' }}>
    <svg viewBox="0 0 24 24" aria-hidden="true" style={{ position: 'absolute', inset: 3, width: 'calc(100% - 6px)', height: 'calc(100% - 6px)', opacity: .24 }}><path fill="currentColor" d="m2.225 13.7-1.225-.9 4.825-7.75 3.125 3.65 4.125-6.7 3.125 4.65 3.575-5.65 1.2.875-4.75 7.525-3.1-4.6-3.975 6.45-3.125-3.65L2.225 13.7Zm12.35 4.8q1.2 0 2.063-.837T17.5 15.625q0-1.225-.85-2.075t-2.075-.85q-1.2 0-2.037.863T11.7 15.625q0 1.2.838 2.038T14.575 18.5ZM19.925 22 17.125 19.2q-.55.375-1.187.588T14.575 20q-1.825 0-3.1-1.275t-1.275-3.1q0-1.825 1.275-3.125t3.1-1.3q1.85 0 3.138 1.288T19 15.625q0 .725-.212 1.363T18.175 18.175l2.8 2.775-1.05 1.05Z" /></svg>
    <Typography component="span" sx={{ position: 'relative', color: 'inherit', fontSize: letterSize, lineHeight: 1, fontWeight: 850, letterSpacing: '-.04em' }}>Q</Typography>
  </Box>
}

function QuantPilotWordmark({ login = false, withMark = false }) {
  return <Box component="span" sx={{ display: 'inline-flex', flexDirection: 'column', minWidth: 0, ml: withMark ? { xs: 1, sm: 1.5 } : 0 }}>
    <Typography component="span" sx={{ color: '#223451', fontSize: login ? '1.3rem' : '1.15rem', fontWeight: 850, letterSpacing: '-.045em', lineHeight: 1.05, whiteSpace: 'nowrap' }}>QuantPilot<span style={{ color: '#078f9f' }}>Platform</span></Typography>
    {login && <Typography component="span" sx={{ mt: .45, color: 'text.secondary', fontSize: '.62rem', fontWeight: 700, letterSpacing: '.13em', lineHeight: 1.1 }}>PRIVATE MARKET WORKSPACE</Typography>}
  </Box>
}

function App() {
  const [profile, setProfile] = useState(null)
  const [draft, setDraft] = useState(null)
  const [order, setOrder] = useState(initialOrder)
  const [preview, setPreview] = useState(null)
  const [active, setActive] = useState('overview')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [profileSavedAt, setProfileSavedAt] = useState(null)
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

  useEffect(() => {
    function handleSessionExpired() {
      setProfile(null)
      setDraft(null)
      setKiteStatus(null)
      setMarketSession(null)
      setNotice({ tone: 'error', text: 'Your session has expired. Please sign in again.' })
      setAuthError('Your session has expired. Please sign in again.')
      setAuthState('unauthenticated')
    }
    window.addEventListener('quantpilot:auth-expired', handleSessionExpired)
    return () => window.removeEventListener('quantpilot:auth-expired', handleSessionExpired)
  }, [])

  const localRisk = useMemo(() => {
    const entry = Number(order.entryPrice)
    const stop = Number(order.stopPrice)
    const quantity = Number(order.quantity)
    return Number.isFinite(entry) && Number.isFinite(stop) && Number.isFinite(quantity)
      ? Math.abs(entry - stop) * quantity : 0
  }, [order])

  async function request(path, options) {
    const response = await apiFetch(path, options)
    const data = await response.json().catch(() => ({}))
    if (!response.ok) {
      const error = new Error(data.message || data.error || 'Request failed')
      error.status = response.status
      throw error
    }
    return data
  }

  useEffect(() => {
    request('/api/v1/auth/me', { suppressAuthRedirect: true })
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
      setProfileSavedAt(new Date())
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

  if (authState === 'checking') return <ThemeProvider theme={marketTheme}><Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: '#edf2f8' }}><Stack alignItems="center" gap={2}><CircularProgress size={34} /><Typography color="text.secondary">Checking secure session…</Typography></Stack></Box></ThemeProvider>
  if (authState === 'unauthenticated') return <LoginPage message={authError} onSuccess={() => { setAuthError(''); setAuthState('authenticated'); loadProfile(); loadKiteStatus() }} />
  if (authState === 'unavailable') return <ThemeProvider theme={marketTheme}><Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3, bgcolor: '#edf2f8' }}><Card sx={{ ...pageCardSx, width: 'min(430px,100%)' }}><CardContent><Stack gap={2}><QuantPilotWordmark login /><Alert severity="error">Authentication service is unavailable.</Alert><Typography variant="caption" color="text.secondary">{authError}</Typography><Button variant="contained" onClick={() => window.location.reload()}>Retry</Button></Stack></CardContent></Card></Box></ThemeProvider>

  return <ThemeProvider theme={marketTheme}><Box className="app-shell">
    <aside className="sidebar">
      <a className="brand" href="#overview" onClick={() => setActive('overview')}>
        <QuantPilotMark /><QuantPilotWordmark withMark />
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

      {notice && <Alert severity={notice.tone === 'success' ? 'success' : 'error'} onClose={() => setNotice(null)} sx={{ mb: 2 }}>{notice.text}</Alert>}

      {!profile && loading ? <Card sx={{ p: 4 }}><LinearProgress sx={{ mb: 2 }} /><Typography color="text.secondary">Loading your local risk profile…</Typography></Card> : <>
        {active === 'overview' && <OverviewMUI profile={profile} onOpenRisk={() => setActive('risk')} />}

        {active === 'risk' && <RiskPreviewMUI profile={profile} order={order} preview={preview} localRisk={localRisk} checking={checking} changeOrder={changeOrder} checkOrder={checkOrder} />}

        {active === 'execution' && <ExecutionConsoleMUI profile={profile} status={kiteStatus} form={execution} change={changeExecution} orders={executionOrders} busy={executionBusy} setBusy={setExecutionBusy} refresh={refreshExecution} notify={setNotice} />}

        {active === 'audit' && <AuditLogMUI notify={setNotice} />}

        {active === 'journal' && <JournalMUI notify={setNotice} />}

        {active === 'integration' && <KiteIntegration status={kiteStatus} refresh={loadKiteStatus} notify={setNotice} />}

        {active === 'settings' && draft && <ProfileMUI draft={draft} changeDraft={changeDraft} saveProfile={saveProfile} saving={saving} savedAt={profileSavedAt} />}
      </>}
    </main>
  </Box></ThemeProvider>
}

const pageCardSx = { minWidth: 0, overflow: 'hidden', borderRadius: '14px', background: 'linear-gradient(145deg, #f8faff, #edf2f8)', border: '1px solid rgba(108,128,158,.16)', boxShadow: '10px 14px 30px rgba(90,108,137,.14), -8px -8px 22px rgba(255,255,255,.82)' }
const insetSx = { borderRadius: '10px', background: '#edf2f8', boxShadow: 'inset 4px 4px 9px #d2dae5, inset -4px -4px 9px #ffffff' }

function PageHeading({ eyebrow, title, description, action }) {
  return <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', sm: 'flex-start' }} gap={2} sx={{ minWidth: 0, flexWrap: 'wrap', mb: 4 }}>
    <Box sx={{ minWidth: 0, flex: '1 1 260px' }}><Typography variant="overline" color="text.secondary" letterSpacing=".14em">{eyebrow}</Typography><Typography variant="h5" sx={{ overflowWrap: 'anywhere' }}>{title}</Typography>{description && <Typography variant="body2" color="text.secondary" mt={.75} sx={{ maxWidth: '68ch', lineHeight: 1.6 }}>{description}</Typography>}</Box>
    {action && <Box sx={{ flex: '0 0 auto', alignSelf: { xs: 'flex-start', sm: 'flex-start' } }}>{action}</Box>}
  </Stack>
}

function OverviewMUI({ profile, onOpenRisk }) {
  const metrics = [
    ['Allocated capital', money(profile?.allocatedCapital), 'Configured paper capital', 'primary'],
    ['Risk per trade', money(profile?.maxLossPerTrade), 'Maximum loss at stop', 'warning'],
    ['Daily stop', money(profile?.maxDailyLoss), 'Stop trading after this loss', 'error'],
    ['Position limits', `${profile?.maxOpenPositions} open / ${profile?.maxTradesPerDay} trades`, 'Session guardrails', 'secondary']
  ]
  const checklist = ['Define entry and hard stop before checking quantity', 'Keep proposed loss inside the configured limit', 'Avoid recovery sizing or averaging down', 'Treat every signal as a hypothesis, not an instruction']
  return <Box sx={{ display: 'grid', gap: 2.5 }}>
    <Alert severity="info" variant="outlined" sx={{ borderRadius: '10px', bgcolor: 'rgba(232,248,251,.72)' }}><Typography fontWeight={800}>Execution is disabled.</Typography><Typography variant="body2">Approved previews are paper-only. Broker routing remains separately gated.</Typography></Alert>
    <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0,1fr))', lg: 'repeat(4, minmax(0,1fr))' }, gap: 2 }}>
      {metrics.map(([label, value, detail, color]) => <Card key={label} sx={{ ...pageCardSx, minHeight: 132 }}><CardContent sx={{ p: 2.5 }}><Typography variant="caption" color="text.secondary" fontWeight={700}>{label}</Typography><Typography variant="h5" color={`${color}.main`} mt={1}>{value}</Typography><Typography variant="caption" color="text.secondary">{detail}</Typography></CardContent></Card>)}
    </Box>
    <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'minmax(0,1.25fr) minmax(0,.9fr)' }, gap: 2.5 }}>
      <Card sx={pageCardSx}><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><PageHeading eyebrow="BEFORE EVERY PAPER TRADE" title="Risk checklist" action={<Chip label="Required" color="secondary" size="small" />} /><Stack divider={<Divider flexItem />}>{checklist.map(item => <Stack key={item} direction="row" gap={1.5} alignItems="center" py={1.2}><Chip label="✓" size="small" color="success" sx={{ minWidth: 28 }} /><Typography variant="body2">{item}</Typography></Stack>)}</Stack></CardContent></Card>
      <Card sx={{ ...pageCardSx, background: 'linear-gradient(145deg,#e5f7f7,#eaf0f8)' }}><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><Typography variant="overline" color="text.secondary" letterSpacing=".14em">NEXT STEP</Typography><Typography variant="h5" mb={1}>Validate a setup</Typography><Typography variant="body2" color="text.secondary" lineHeight={1.7} mb={3}>Check the maximum permitted quantity from the exact entry and stop distance.</Typography><Button variant="contained" fullWidth onClick={onOpenRisk}>Open risk preview →</Button></CardContent></Card>
    </Box>
  </Box>
}

function RiskPreviewMUI({ profile, order, preview, localRisk, checking, changeOrder, checkOrder }) {
  return <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'minmax(0,1.2fr) minmax(0,.9fr)' }, gap: 2.5 }}>
    <Card sx={pageCardSx}><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><PageHeading eyebrow="PAPER-TRADE GATE" title="Order risk preview" description="Define the entry, hard stop, and quantity before any paper-trade intent is considered." action={<Chip label="No order routing" color="info" size="small" />} /><Box component="form" onSubmit={checkOrder} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
      <TextField label="NSE symbol" value={order.symbol} inputProps={{ maxLength: 32 }} onChange={e => changeOrder('symbol', e.target.value.toUpperCase())} />
      <TextField select SelectProps={{ native: true }} label="Side" value={order.side} onChange={e => changeOrder('side', e.target.value)}><option>BUY</option><option>SELL</option></TextField>
      <TextField label="Entry price (₹)" type="number" inputProps={{ min: 0, step: .05 }} value={order.entryPrice} onChange={e => changeOrder('entryPrice', e.target.value)} />
      <TextField label="Stop price (₹)" type="number" inputProps={{ min: 0, step: .05 }} value={order.stopPrice} onChange={e => changeOrder('stopPrice', e.target.value)} />
      <TextField label="Quantity" type="number" inputProps={{ min: 1, step: 1 }} value={order.quantity} onChange={e => changeOrder('quantity', e.target.value)} />
      <Paper sx={{ ...insetSx, p: 1.6, gridColumn: { sm: '1 / -1' }, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}><Typography variant="body2" color="text.secondary">Calculated risk before API check</Typography><Typography variant="h6" color={localRisk > Number(profile?.maxLossPerTrade) ? 'error.main' : 'success.main'}>{money(localRisk)}</Typography></Paper>
      <Button type="submit" variant="contained" fullWidth disabled={checking} sx={{ gridColumn: { sm: '1 / -1' }, minHeight: 48 }}>{checking ? 'Checking limits…' : 'Check paper-trade limits →'}</Button>
    </Box></CardContent></Card>
    <Card sx={pageCardSx}><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><PageHeading eyebrow="RISK DECISION" title={preview ? (preview.allowed ? 'Within limits' : 'Blocked by guardrails') : 'Waiting for a setup'} action={preview && <Chip label={preview.allowed ? 'ALLOWED' : 'BLOCKED'} color={preview.allowed ? 'success' : 'error'} size="small" />} />{!preview ? <Typography variant="body2" color="text.secondary" lineHeight={1.8}>Enter the proposed entry, stop, and quantity. The API will calculate permitted quantity using your saved risk profile.</Typography> : <Stack gap={2}><Stack direction="row" justifyContent="space-between" sx={insetSx} p={1.8}><Typography color="text.secondary">Proposed risk</Typography><Typography fontWeight={800}>{money(preview.proposedRisk)}</Typography></Stack><Stack direction="row" justifyContent="space-between" sx={insetSx} p={1.8}><Typography color="text.secondary">Maximum quantity</Typography><Typography fontWeight={800}>{preview.maxAllowedQuantity}</Typography></Stack><Alert severity={preview.allowed ? 'success' : 'error'}>{preview.reasons?.map(reason => <div key={reason}>{reason}</div>)}</Alert><Typography variant="caption" color="text.secondary">Mode: <b>{preview.executionMode}</b>. No broker request has been sent.</Typography></Stack>}</CardContent></Card>
  </Box>
}

function ProfileMUI({ draft, changeDraft, saveProfile, saving, savedAt }) {
  const fields = [['broker', 'Broker label', 'text'], ['exchange', 'Exchange', 'text'], ['segment', 'Segment', 'text'], ['product', 'Product', 'text'], ['allocatedCapital', 'Allocated capital (₹)', 'number'], ['maxLossPerTrade', 'Maximum loss per trade (₹)', 'number'], ['maxDailyLoss', 'Maximum daily loss (₹)', 'number'], ['maxTradesPerDay', 'Maximum trades per day', 'number'], ['maxOpenPositions', 'Maximum open positions', 'number'], ['cooldownSeconds', 'Cooldown between intents (seconds)', 'number']]
  return <Card sx={{ ...pageCardSx, maxWidth: 980 }}><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><PageHeading eyebrow="LOCAL CONFIGURATION" title="Paper-trading guardrails" description="These limits are stored in your local PostgreSQL database." action={<Chip label="PAPER / DRY-RUN only" color="secondary" size="small" />} />{savedAt && <Alert severity="success" variant="outlined" sx={{ mb: 2.5, borderRadius: '10px' }}>Profile refreshed and saved at {savedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}.</Alert>}<Box component="form" onSubmit={saveProfile} sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))' }, gap: 2 }}>
      <TextField select SelectProps={{ native: true }} label="Mode" value={draft.mode} onChange={e => changeDraft('mode', e.target.value)}><option>PAPER</option><option>DRY_RUN</option><option>LIVE</option></TextField>
      {fields.map(([key, label, type]) => <TextField key={key} label={label} type={type} value={draft[key] ?? ''} inputProps={type === 'number' ? { min: 0 } : undefined} onChange={e => changeDraft(key, e.target.value)} />)}
      <Paper sx={{ ...insetSx, p: 1.6, gridColumn: { sm: '1 / -1' }, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}><Box><Typography fontWeight={800}>Enable kill switch</Typography><Typography variant="caption" color="text.secondary">Blocks every paper-trade preview until disabled.</Typography></Box><Switch checked={Boolean(draft.killSwitchEnabled)} onChange={e => changeDraft('killSwitchEnabled', e.target.checked)} /></Paper>
      <Button type="submit" variant="contained" disabled={saving} sx={{ gridColumn: { sm: '1 / -1' }, minHeight: 48 }}>{saving ? 'Saving…' : 'Save local profile →'}</Button>
    </Box></CardContent></Card>
}

function AuditLogMUI({ notify }) {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(false)
  async function refresh() { setLoading(true); try { const response = await apiFetch('/api/v1/audit/events?limit=100'); const data = await response.json().catch(() => []); if (!response.ok) throw new Error(data.message || 'Audit events could not be loaded'); setEvents(data) } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setLoading(false) } }
  useEffect(() => { refresh() }, [])
  return <Card sx={pageCardSx}><CardContent sx={{ p: { xs: 2, md: 3 } }}><PageHeading eyebrow="DURABLE SAFETY TRAIL" title="Recent events" description="Order attempts, safeguards, kill-switch changes, and reconciliation events are stored in PostgreSQL." action={<Button variant="outlined" onClick={refresh} disabled={loading}>↻ Refresh</Button>} />{loading && <LinearProgress sx={{ mb: 2 }} />}{events.length === 0 ? <Alert severity="info">No audit events recorded.</Alert> : <TableContainer sx={{ maxHeight: 560, borderRadius: 2, ...insetSx }}><Table stickyHeader size="small"><TableHead><TableRow>{['Time', 'Event', 'Aggregate', 'Details'].map(label => <TableCell key={label}>{label}</TableCell>)}</TableRow></TableHead><TableBody>{events.map((event, index) => <TableRow hover key={`${event.occurredAt}-${index}`}><TableCell sx={{ whiteSpace: 'nowrap' }}>{event.occurredAt ? new Date(event.occurredAt).toLocaleString() : '—'}</TableCell><TableCell><Typography fontWeight={800}>{event.eventType}</Typography></TableCell><TableCell>{event.aggregateType}<Typography variant="caption" display="block" color="text.secondary">{event.aggregateId}</Typography></TableCell><TableCell sx={{ minWidth: 260, whiteSpace: 'normal', wordBreak: 'break-word' }}>{event.details}</TableCell></TableRow>)}</TableBody></Table></TableContainer>}</CardContent></Card>
}

function ExecutionConsoleMUI({ profile, status, form, change, orders, busy, setBusy, refresh, notify }) {
  const [automationStatus, setAutomationStatus] = useState(null)
  const liveGateReady = profile?.mode === 'LIVE' && status?.executionState === 'CONFIGURED_BUT_GATED' && status?.accessTokenConfigured
  useEffect(() => { apiFetch('/api/v1/automation/status').then(response => response.ok ? response.json() : null).then(setAutomationStatus).catch(() => setAutomationStatus(null)) }, [])
  async function api(path, options) { const response = await apiFetch(path, options); const data = await response.json().catch(() => ({})); if (!response.ok) throw new Error(data.message || 'Execution request failed'); return data }
  async function submit(event) { event.preventDefault(); if (!liveGateReady) { notify({ tone: 'error', text: 'Execution is locked. Enable the explicit routing flag, use LIVE profile mode, and complete Kite login first.' }); return } if (!window.confirm(`Submit ${form.side} ${form.quantity} ${form.symbol}? This can place a real broker order.`)) return; setBusy(true); try { const key = window.crypto?.randomUUID ? window.crypto.randomUUID() : `${Date.now()}-${Math.random()}`; await api('/api/v1/execution/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key }, body: JSON.stringify({ ...form, symbol: form.symbol.trim().toUpperCase(), quantity: Number(form.quantity), price: form.price ? Number(form.price) : null, triggerPrice: form.triggerPrice ? Number(form.triggerPrice) : null, stopPrice: form.stopPrice ? Number(form.stopPrice) : null }) }); notify({ tone: 'success', text: 'Order submission accepted by the execution gateway.' }); await refresh() } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) } }
  async function reconcile() { setBusy(true); try { await api('/api/v1/execution/orders/reconcile', { method: 'POST' }); await refresh(); notify({ tone: 'success', text: 'Tracked order statuses reconciled.' }) } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) } }
  async function cancel(orderId) { if (!window.confirm(`Cancel broker order ${orderId}?`)) return; setBusy(true); try { await api(`/api/v1/execution/orders/${encodeURIComponent(orderId)}`, { method: 'DELETE' }); await refresh(); notify({ tone: 'success', text: 'Cancellation request submitted.' }) } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setBusy(false) } }
  const fields = [['symbol', 'NSE symbol', 'text'], ['quantity', 'Quantity', 'number'], ['price', 'Expected entry (₹)', 'number'], ['stopPrice', 'Hard stop (₹)', 'number'], ['triggerPrice', 'Trigger price (optional)', 'number']]
  return <Box sx={{ display: 'grid', gap: 2.5 }}><Alert severity={liveGateReady ? 'success' : 'warning'} variant="outlined" sx={{ borderRadius: '10px' }}><Typography fontWeight={800}>{liveGateReady ? 'Explicit execution gates are open.' : 'Execution is locked.'}</Typography><Typography variant="body2">Every order requires LIVE profile mode, routing enabled, today’s Kite session, valid risk controls, and confirmation.</Typography><Typography variant="caption" display="block" mt={1}>Automatic signal routing: <b>{automationStatus?.enabled ? 'ENABLED' : 'DISABLED'}</b> · max signal age: {automationStatus?.maxSignalAgeSeconds ?? '—'}s · quantity cap: {automationStatus?.maxOrderQuantity ?? '—'} · broker state: <b>{automationStatus?.uncertainBrokerState ? 'RECONCILIATION REQUIRED' : 'READY'}</b></Typography></Alert>
    <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'minmax(0,1.2fr) minmax(0,.9fr)' }, gap: 2.5 }}><Card sx={pageCardSx}><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><PageHeading eyebrow="CONTROLLED BROKER ROUTING" title="Submit Kite order" action={<Chip label={liveGateReady ? 'GATED LIVE' : 'LOCKED'} color={liveGateReady ? 'success' : 'default'} size="small" />} /><Box component="form" onSubmit={submit} sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))' }, gap: 2 }}>
      <TextField label="NSE symbol" required value={form.symbol} onChange={e => change('symbol', e.target.value.toUpperCase())} />
      <TextField select SelectProps={{ native: true }} label="Side" value={form.side} onChange={e => change('side', e.target.value)}><option>BUY</option><option>SELL</option></TextField>
      <TextField select SelectProps={{ native: true }} label="Order type" value={form.orderType} onChange={e => change('orderType', e.target.value)}><option>LIMIT</option><option>MARKET</option><option>SL</option><option>SL-M</option></TextField>
      {fields.slice(1).map(([key, label, type]) => <TextField key={key} label={label} type={type} required={key === 'quantity' || key === 'price' || key === 'stopPrice'} value={form[key]} onChange={e => change(key, e.target.value)} />)}
      <TextField select SelectProps={{ native: true }} label="Product" value={form.product} onChange={e => change('product', e.target.value)}><option>MIS</option><option>CNC</option></TextField>
      <Button type="submit" variant="contained" disabled={busy} sx={{ gridColumn: { sm: '1 / -1' }, minHeight: 48 }}>{busy ? 'Processing…' : 'Submit controlled order'}</Button>
    </Box></CardContent></Card><Card sx={pageCardSx}><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><PageHeading eyebrow="EXECUTION LIFECYCLE" title="Reconciliation is mandatory" /><Typography variant="body2" color="text.secondary" lineHeight={1.8} mb={3}>The broker response is only an acknowledgement. Tracked orders are reconciled into OPEN, PARTIALLY_FILLED, COMPLETE, REJECTED, and CANCELLED states.</Typography><Button variant="outlined" onClick={reconcile} disabled={busy}>↻ Reconcile tracked orders</Button></CardContent></Card></Box>
    <Card sx={pageCardSx}><CardContent sx={{ p: { xs: 2, md: 3 } }}><PageHeading eyebrow="TRACKED ORDERS" title="Execution history" action={<Button variant="outlined" onClick={refresh} disabled={busy}>↻ Refresh</Button>} />{orders.length === 0 ? <Alert severity="info">No broker execution requests are recorded.</Alert> : <TableContainer sx={{ maxHeight: 520, borderRadius: 2, ...insetSx }}><Table stickyHeader size="small"><TableHead><TableRow>{['Order', 'Symbol', 'Side', 'Quantity', 'Filled', 'Status', ''].map(label => <TableCell key={label}>{label}</TableCell>)}</TableRow></TableHead><TableBody>{orders.map(order => <TableRow hover key={order.orderId}><TableCell><Typography fontWeight={800}>{order.orderId}</Typography><Typography variant="caption" color="text.secondary">{order.observedAt ? new Date(order.observedAt).toLocaleString() : ''}</Typography></TableCell><TableCell>{order.symbol || '—'}</TableCell><TableCell><Chip label={order.side || '—'} size="small" color={order.side === 'BUY' ? 'success' : 'error'} variant="outlined" /></TableCell><TableCell>{order.quantity}</TableCell><TableCell>{order.filledQuantity}{order.averagePrice ? ` @ ₹${order.averagePrice}` : ''}</TableCell><TableCell><Chip label={order.status} size="small" /></TableCell><TableCell>{['PENDING', 'OPEN', 'PARTIALLY_FILLED'].includes(order.status) && <Button size="small" color="error" onClick={() => cancel(order.orderId)} disabled={busy}>Cancel</Button>}</TableCell></TableRow>)}</TableBody></Table></TableContainer>}</CardContent></Card>
  </Box>
}

function JournalMUI({ notify }) {
  const [trades, setTrades] = useState([]); const [summary, setSummary] = useState(null); const [report, setReport] = useState(null); const [loading, setLoading] = useState(true); const [creating, setCreating] = useState(false); const [action, setAction] = useState(null); const [replay, setReplay] = useState(null); const [actionPrice, setActionPrice] = useState(''); const [actionQuantity, setActionQuantity] = useState(''); const [brokerage, setBrokerage] = useState('0'); const [exitReason, setExitReason] = useState('MANUAL'); const [form, setForm] = useState({ symbol: 'SBIN', side: 'BUY', intendedEntryPrice: '', stopPrice: '', intendedQuantity: '', strategy: '', notes: '' })
  async function api(path, options) { const response = await apiFetch(path, options); const data = await response.json().catch(() => ({})); if (!response.ok) throw new Error(data.message || 'Request failed'); return data }
  async function reload() { setLoading(true); try { const [nextTrades, nextSummary, nextReport] = await Promise.all([api('/api/v1/paper-trades'), api('/api/v1/paper-trades/summary'), api('/api/v1/paper-trades/report')]); setTrades(nextTrades); setSummary(nextSummary); setReport(nextReport) } catch (error) { notify({ tone: 'error', text: `Unable to load journal: ${error.message}` }) } finally { setLoading(false) } }
  useEffect(() => { reload() }, [])
  async function createIntent(event) { event.preventDefault(); setCreating(true); try { await api('/api/v1/paper-trades', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...form, symbol: form.symbol.trim().toUpperCase(), intendedEntryPrice: Number(form.intendedEntryPrice), stopPrice: Number(form.stopPrice), intendedQuantity: Number(form.intendedQuantity) }) }); setForm(current => ({ ...current, intendedEntryPrice: '', stopPrice: '', intendedQuantity: '', notes: '' })); notify({ tone: 'success', text: 'Intent recorded. It is not treated as a filled trade.' }); await reload() } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setCreating(false) } }
  function startAction(type, trade) { setAction({ type, trade }); setActionPrice(type === 'fill' ? trade.intendedEntryPrice : ''); setActionQuantity(type === 'fill' ? trade.intendedQuantity : ''); setBrokerage('0'); setExitReason('MANUAL') }
  async function submitAction(event) { event.preventDefault(); try { const { type, trade } = action; if (type === 'fill') await api(`/api/v1/paper-trades/${trade.id}/fill`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ actualEntryPrice: Number(actionPrice), filledQuantity: Number(actionQuantity) }) }); if (type === 'close') await api(`/api/v1/paper-trades/${trade.id}/close`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ actualExitPrice: Number(actionPrice), brokerage: Number(brokerage), exitReason }) }); if (type === 'cancel') await api(`/api/v1/paper-trades/${trade.id}/cancel`, { method: 'POST' }); notify({ tone: 'success', text: type === 'fill' ? 'Actual fill recorded.' : type === 'close' ? 'Exit and realised P&L recorded.' : 'Intent cancelled.' }); setAction(null); await reload() } catch (error) { notify({ tone: 'error', text: error.message }) } }
  async function openReplay(trade) { try { setReplay({ trade, events: await api(`/api/v1/paper-trades/${trade.id}/replay`) }) } catch (error) { notify({ tone: 'error', text: error.message }) } }
  const metrics = [['Today’s realised P&L', money(summary?.realisedPnl), 'After recorded charges'], ['Active risk', money(summary?.activeRisk), 'Intended + open trade risk'], ['Remaining daily capacity', money(summary?.remainingDailyRiskCapacity), `Of ${money(summary?.maxDailyLoss)} daily limit`], ['Session activity', `${summary?.initiatedTrades || 0} intents / ${summary?.openPositions || 0} open`, 'Today’s journal'], ['Win rate', `${report?.winRate || 0}%`, `${report?.winningTrades || 0} wins / ${report?.closedTrades || 0} closed`], ['Entry slippage', money(report?.totalSlippage), 'Adverse slippage is positive']]
  const setField = (key, value) => setForm(current => ({ ...current, [key]: value }))
  return <Box sx={{ minWidth: 0, display: 'grid', gap: 2.5 }}><Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0,1fr))', lg: 'repeat(6, minmax(0,1fr))' }, gap: 1.5 }}>{metrics.map(([label, value, detail]) => <Card key={label} sx={{ ...pageCardSx, minHeight: 116 }}><CardContent sx={{ p: 1.8 }}><Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{label}</Typography><Typography variant="h6" mt={.7} sx={{ overflowWrap: 'anywhere' }}>{value}</Typography><Typography variant="caption" color="text.secondary">{detail}</Typography></CardContent></Card>)}</Box>
    <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'minmax(0,1.2fr) minmax(0,.8fr)' }, gap: 2.5, alignItems: 'start' }}><Card sx={pageCardSx}><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><PageHeading eyebrow="RECORD DECISION" title="New trade intent" description="This captures the planned trade. It does not create a fill." /><Box component="form" onSubmit={createIntent} sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2, minmax(0,1fr))' }, gap: 2 }}><TextField label="NSE symbol" value={form.symbol} onChange={e => setField('symbol', e.target.value.toUpperCase())} /><TextField select SelectProps={{ native: true }} label="Side" value={form.side} onChange={e => setField('side', e.target.value)}><option>BUY</option><option>SELL</option></TextField><TextField required label="Intended entry (₹)" type="number" value={form.intendedEntryPrice} onChange={e => setField('intendedEntryPrice', e.target.value)} /><TextField required label="Hard stop (₹)" type="number" value={form.stopPrice} onChange={e => setField('stopPrice', e.target.value)} /><TextField required label="Intended quantity" type="number" value={form.intendedQuantity} onChange={e => setField('intendedQuantity', e.target.value)} /><TextField label="Strategy label" placeholder="Opening-range breakout" value={form.strategy} onChange={e => setField('strategy', e.target.value)} /><TextField multiline minRows={3} label="Notes" placeholder="Why this setup qualified…" value={form.notes} onChange={e => setField('notes', e.target.value)} sx={{ gridColumn: { sm: '1 / -1' } }} /><Button type="submit" variant="contained" disabled={creating} sx={{ gridColumn: { sm: '1 / -1' }, minHeight: 48 }}>{creating ? 'Saving…' : 'Record intended trade →'}</Button></Box></CardContent></Card><Card sx={{ ...pageCardSx, background: 'linear-gradient(145deg,#e5f7f7,#eaf0f8)' }}><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><PageHeading eyebrow="LIFECYCLE" title="Intent ≠ fill" /><Stack gap={2}><Typography variant="body2"><b>INTENDED</b> — approved plan and proposed risk.</Typography><Typography variant="body2"><b>OPEN</b> — record actual fill price and quantity.</Typography><Typography variant="body2"><b>CLOSED</b> — record actual exit, charges, and P&amp;L.</Typography></Stack><Typography variant="body2" color="text.secondary" mt={3}>There is no assumed fill price in this journal.</Typography></CardContent></Card></Box>
    <Card sx={pageCardSx}><CardContent sx={{ p: { xs: 2, md: 3 } }}><PageHeading eyebrow="TODAY" title="Trade history" action={<Stack direction="row" gap={1}><Button variant="outlined" onClick={reload}>↻ Refresh</Button><Button variant="outlined" onClick={() => window.open(`${apiBase}/api/v1/paper-trades/export`, '_blank')}>↓ Export CSV</Button></Stack>} />{loading ? <LinearProgress /> : trades.length === 0 ? <Alert severity="info">No intents recorded today. Start by recording a risk-checked setup.</Alert> : <TableContainer sx={{ maxHeight: 560, borderRadius: 2, ...insetSx }}><Table stickyHeader size="small"><TableHead><TableRow>{['Symbol', 'Side', 'Status', 'Intended', 'Actual', 'Slippage', 'Net P&L', ''].map(label => <TableCell key={label}>{label}</TableCell>)}</TableRow></TableHead><TableBody>{trades.map(trade => <TableRow hover key={trade.id}><TableCell><Typography fontWeight={800}>{trade.symbol}</Typography><Typography variant="caption" color="text.secondary">{trade.strategy || 'No strategy label'}</Typography></TableCell><TableCell><Chip label={trade.side} size="small" color={trade.side === 'BUY' ? 'success' : 'error'} variant="outlined" /></TableCell><TableCell><Chip label={trade.status} size="small" /></TableCell><TableCell>₹{trade.intendedEntryPrice} × {trade.intendedQuantity}<Typography variant="caption" display="block">Stop ₹{trade.stopPrice}</Typography></TableCell><TableCell>{trade.actualEntryPrice ? <>₹{trade.actualEntryPrice} × {trade.filledQuantity}<Typography variant="caption" display="block">{trade.actualExitPrice ? `Exit ₹${trade.actualExitPrice}` : 'Open position'}</Typography></> : '—'}</TableCell><TableCell>{trade.slippageAmount == null ? '—' : money(trade.slippageAmount)}<Typography variant="caption" display="block">{trade.slippagePerUnit == null ? '' : `₹${trade.slippagePerUnit}/unit`}</Typography></TableCell><TableCell color={Number(trade.netPnl) < 0 ? 'error' : 'success'}>{trade.netPnl == null ? '—' : money(trade.netPnl)}</TableCell><TableCell><Stack direction="row" gap={.5} flexWrap="wrap"><Button size="small" onClick={() => openReplay(trade)}>Replay</Button>{trade.status === 'INTENDED' && <><Button size="small" onClick={() => startAction('fill', trade)}>Fill</Button><Button size="small" color="error" onClick={() => { if (window.confirm(`Cancel ${trade.symbol} intent?`)) startAction('cancel', trade) }}>Cancel</Button></>}{trade.status === 'OPEN' && <Button size="small" onClick={() => startAction('close', trade)}>Close</Button>}</Stack></TableCell></TableRow>)}</TableBody></Table></TableContainer>}</CardContent></Card>
    <Dialog open={Boolean(action)} onClose={() => setAction(null)} fullWidth maxWidth="xs"><Box component="form" onSubmit={submitAction}><DialogTitle>{action?.type === 'fill' ? 'Record actual fill' : action?.type === 'close' ? 'Record actual exit' : 'Cancel trade intent'}{action && <Typography variant="caption" display="block" color="text.secondary">{action.trade.symbol}</Typography>}</DialogTitle><DialogContent>{action?.type === 'cancel' ? <Typography color="text.secondary">Confirm that no fill occurred. This action cannot be reversed.</Typography> : <Stack gap={2} pt={1}><TextField autoFocus required label={action?.type === 'fill' ? 'Actual entry price (₹)' : 'Actual exit price (₹)'} type="number" value={actionPrice} onChange={e => setActionPrice(e.target.value)} />{action?.type === 'fill' && <TextField required label="Actual filled quantity" type="number" inputProps={{ min: 1, max: action.trade.intendedQuantity }} value={actionQuantity} onChange={e => setActionQuantity(e.target.value)} />}{action?.type === 'close' && <><TextField required label="Brokerage and charges (₹)" type="number" value={brokerage} onChange={e => setBrokerage(e.target.value)} /><TextField select SelectProps={{ native: true }} label="Exit reason" value={exitReason} onChange={e => setExitReason(e.target.value)}><option>STOP_LOSS</option><option>TARGET</option><option>MANUAL</option><option>END_OF_DAY</option></TextField></>}</Stack>}</DialogContent><DialogActions><Button onClick={() => setAction(null)}>Close</Button><Button type="submit" variant="contained" color={action?.type === 'cancel' ? 'error' : 'primary'}>{action?.type === 'fill' ? 'Confirm actual fill' : action?.type === 'close' ? 'Record exit and P&L' : 'Confirm cancellation'}</Button></DialogActions></Box></Dialog>
    <Dialog open={Boolean(replay)} onClose={() => setReplay(null)} fullWidth maxWidth="sm"><DialogTitle>Recorded lifecycle<Typography variant="caption" display="block" color="text.secondary">{replay?.trade.symbol} · replay is read-only</Typography></DialogTitle><DialogContent><Stack gap={1.5}>{replay?.events.map((event, index) => <Paper key={`${event.event}-${index}`} sx={{ ...insetSx, p: 1.5 }}><Typography fontWeight={800}>{index + 1}. {event.event.replaceAll('_', ' ')}</Typography><Typography variant="caption" color="text.secondary">{event.occurredAt ? new Date(event.occurredAt).toLocaleString() : '—'} · {event.details}</Typography>{event.netPnl != null && <Typography variant="caption" display="block">Gross {money(event.grossPnl)} · Charges {money(event.charges)} · Net {money(event.netPnl)}</Typography>}</Paper>)}</Stack></DialogContent><DialogActions><Button onClick={() => setReplay(null)}>Close</Button></DialogActions></Dialog>
  </Box>
}

function Metric({ label, value, detail, accent = '' }) { return <article className={`metric-card ${accent}`}><p>{label}</p><strong>{value}</strong><span>{detail}</span></article> }
function Checklist({ text }) { return <div className="check-item"><span>✓</span>{text}</div> }
function Field({ label, children }) { return <label className="field"><span>{label}</span>{children}</label> }
function LoginPage({ onSuccess, message = '' }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(message)
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const response = await fetch(`${apiBase}/api/v1/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, credentials: 'include', body: JSON.stringify({ username, password }) })
      const data = await response.json().catch(() => ({}))
      if (!response.ok) throw new Error(data.message || data.error || 'Invalid username or password')
      onSuccess()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  return <ThemeProvider theme={marketTheme}>
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: { xs: 2, sm: 3 }, bgcolor: '#edf2f8' }}>
      <Card sx={{ ...pageCardSx, width: 'min(500px, 100%)' }}>
        <CardContent sx={{ p: { xs: 3, sm: 4.5 } }}>
          <Stack gap={{ xs: 2.5, sm: 3 }}>
            <Stack direction="row" gap={{ xs: 1.75, sm: 2 }} alignItems="center">
              <QuantPilotMark size="large" />
              <QuantPilotWordmark login withMark />
            </Stack>
            <Box>
              <Typography variant="overline" color="text.secondary" letterSpacing=".16em">SECURE ACCESS</Typography>
              <Typography variant="h3" sx={{ mt: .75, fontSize: { xs: '2rem', sm: '2.45rem' }, lineHeight: 1.1 }}>Welcome back</Typography>
              <Typography variant="body1" color="text.secondary" sx={{ mt: 1.25, maxWidth: 420, lineHeight: 1.65 }}>Sign in to continue to your private market research workspace.</Typography>
            </Box>
            {error && <Alert severity="error" variant="outlined">{error}</Alert>}
            <Box component="form" onSubmit={submit} sx={{ display: 'grid', gap: 2.5, mt: .5 }}>
              <TextField fullWidth label="Username" autoFocus autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} required />
              <TextField fullWidth label="Password" type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required />
              <Button type="submit" variant="contained" disabled={busy} sx={{ minHeight: 50, mt: .5, fontSize: '1rem' }}>{busy ? 'Signing in…' : 'Sign in →'}</Button>
            </Box>
            <Divider sx={{ mt: .5 }} />
            <Typography variant="body2" textAlign="center" color="text.secondary" sx={{ pt: .25, lineHeight: 1.65 }}>Private session protected. Broker execution remains separately gated.</Typography>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  </ThemeProvider>
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
      const response = await apiFetch('/api/v1/audit/events?limit=100')
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
    apiFetch('/api/v1/automation/status').then(response => response.ok ? response.json() : null)
      .then(setAutomationStatus).catch(() => setAutomationStatus(null))
  }, [])
  async function api(path, options) {
    const response = await apiFetch(path, options)
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
      const response = await apiFetch('/api/v1/integrations/zerodha/login-url')
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
    apiFetch('/api/v1/market-data/instruments?query=')
      .then(response => response.ok ? response.json() : [])
      .then(items => setHasSyncedSymbols(Array.isArray(items) && items.length > 0))
      .catch(() => setHasSyncedSymbols(false))
  }, [marketDataReady])

  async function syncInstruments() {
    setSyncing(true)
    try {
      const response = await apiFetch('/api/v1/market-data/instruments/sync', { method: 'POST' })
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
      const response = await apiFetch(`/api/v1/market-data/instruments?query=${encodeURIComponent(symbolQuery.trim())}&sector=${encodeURIComponent(sectorFilter)}`)
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
      const configResponse = await apiFetch('/api/v1/scanner/config')
      const scannerConfig = await configResponse.json().catch(() => ({}))
      if (!configResponse.ok) throw new Error(scannerConfig.message || 'Scanner configuration could not be loaded')
      const from = `${tradeDate}T09:15:00+05:30`
      const to = `${tradeDate}T15:30:00+05:30`
      const interval = scannerConfig.candleInterval || 'FIVE_MINUTE'
      const candleResponse = await apiFetch(`/api/v1/market-data/historical-candles?instrumentToken=${selected.instrumentToken}&interval=${interval}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
      const candles = await candleResponse.json().catch(() => [])
      if (!candleResponse.ok) throw new Error(candles.message || 'Historical candle request failed')
      const signalResponse = await apiFetch('/api/v1/signals/evaluate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ symbol: selected.tradingsymbol, candles }) })
      const signal = await signalResponse.json().catch(() => ({}))
      if (!signalResponse.ok) throw new Error(signal.message || 'Signal evaluation failed')
      setVerification({ count: candles.length, signal })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setVerifying(false) }
  }

  async function addSelectedToMonitor() {
    if (!selected) return
    try {
      const response = await apiFetch('/api/v1/live-market/watchlist', {
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
      <Tabs value={marketTab} onChange={(_, value) => setMarketTab(value)} variant="scrollable" scrollButtons="auto" allowScrollButtonsMobile sx={{ bgcolor: 'background.paper', borderRadius: '10px', px: 1, boxShadow: '0 10px 28px rgba(43,66,99,.10)' }}>
        {marketTabs.map(([key, label]) => <Tab key={key} value={key} label={label} />)}
      </Tabs>
      <Alert severity="info" variant="outlined" sx={{ borderRadius: '10px', bgcolor: 'rgba(232,248,251,.75)' }}>
        <Typography variant="subtitle2" fontWeight={800}>Market data and broker routing are separately gated.</Typography>
        <Typography variant="body2">Order placement, modification, and cancellation are available only from the Execution screen after every explicit safety gate passes.</Typography>
      </Alert>

      {marketTab === 'connection' && <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'minmax(0,1.25fr) minmax(0,.9fr)' }, gap: 2.5 }}>
        <Card><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', sm: 'flex-start' }} gap={2} mb={2.5} sx={{ minWidth: 0 }}>
            <Box sx={{ minWidth: 0 }}><Typography variant="overline" color="text.secondary" letterSpacing=".14em">ZERODHA KITE CONNECT</Typography><Typography variant="h5" sx={{ overflowWrap: 'anywhere' }}>{status?.state?.replaceAll('_', ' ') || 'Checking configuration'}</Typography></Box>
            <Chip label={status?.executionState || 'DISABLED'} color={configured ? 'success' : 'default'} size="small" />
          </Stack>
          <Stack divider={<Divider flexItem />}>
            <IntegrationRow label="API key" value={status?.apiKeyConfigured ? 'Configured locally' : 'Not configured'} ok={status?.apiKeyConfigured} />
            <IntegrationRow label="API secret" value={status?.apiSecretConfigured ? 'Configured locally' : 'Not configured'} ok={status?.apiSecretConfigured} />
            <IntegrationRow label="Session token" value={status?.accessTokenConfigured ? 'Connected for current run' : 'Not connected for today'} ok={status?.accessTokenConfigured} />
            <IntegrationRow label="Live market data" value={status?.marketDataEnabled ? 'Explicitly enabled' : 'Disabled'} ok={status?.marketDataEnabled} />
          </Stack>
          <Alert severity={status?.nextStep ? 'warning' : 'info'} sx={{ mt: 2.5, borderRadius: '10px' }}>{status?.nextStep || 'Loading local connection state…'}</Alert>
          <Stack direction="row" gap={1.5} mt={2.5} flexWrap="wrap"><Button variant="outlined" onClick={refresh}>Refresh state</Button><Button variant="contained" onClick={connectKite} disabled={loadingUrl || !status?.apiKeyConfigured || !status?.apiSecretConfigured}>{loadingUrl ? 'Opening Kite…' : 'Connect Kite'}</Button></Stack>
        </CardContent></Card>
        <Card><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}><Typography variant="overline" color="text.secondary" letterSpacing=".14em">SAFE ACTIVATION PATH</Typography><Typography variant="h5" mb={2}>What happens after purchase</Typography><Stepper orientation="vertical" activeStep={-1}>{['Add credentials only to your uncommitted local .env file.', 'Use Kite login to create that day’s session token.', 'Verify instrument import and a historical-candle request.', 'Enable live WebSocket data only after both tests pass.'].map(step => <Step key={step}><StepLabel>{step}</StepLabel></Step>)}</Stepper><Typography variant="body2" color="text.secondary" mt={2}>API secret and access token never appear in this page or in browser storage.</Typography></CardContent></Card>
      </Box>}

      {marketTab === 'symbols' && <Card><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={2} mb={2.5}><Box><Typography variant="overline" color="text.secondary" letterSpacing=".14em">READ-ONLY DATA VERIFICATION</Typography><Typography variant="h5">Sync symbols and inspect completed candles</Typography><Typography variant="body2" color="text.secondary">Only synced NSE cash equities are available for search, evaluation, and live monitoring.</Typography></Box><Chip label={marketDataReady ? 'Ready to verify' : 'Kite connection required'} color={marketDataReady ? 'success' : 'warning'} /></Stack>
        {!marketDataReady && <Alert severity="warning" sx={{ mb: 2 }}>Connect Kite first. Symbol synchronization uses Kite’s instrument master.</Alert>}
        <Button variant="contained" onClick={syncInstruments} disabled={!marketDataReady || syncing}>{syncing ? 'Syncing symbols…' : 'Sync NSE cash symbols'}</Button>{syncResult && <Typography component="span" variant="body2" color="text.secondary" ml={2}>{syncResult.savedInstruments} symbols synced</Typography>}
        {symbolMessage && <Alert severity={hasSyncedSymbols === false ? 'info' : 'warning'} sx={{ mt: 2 }} action={hasSyncedSymbols === false && <Button color="inherit" size="small" onClick={syncInstruments} disabled={syncing}>{syncing ? 'Syncing…' : 'Sync now'}</Button>}>{symbolMessage}</Alert>}
        <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: '280px minmax(0,1fr)' }, gap: 2.5, mt: 3, pt: 3, borderTop: '1px solid rgba(108,128,158,.18)' }}>
          <Box component="form" onSubmit={searchInstruments} sx={{ display: 'grid', gap: 1.5, alignContent: 'start' }}><TextField label="Search synced NSE symbol" value={symbolQuery} placeholder="SBIN" onChange={event => setSymbolQuery(event.target.value.toUpperCase())} /><TextField select SelectProps={{ native: true }} label="Sector filter" value={sectorFilter} onChange={event => setSectorFilter(event.target.value)}><option value="">All sectors</option><option value="BANKING">Banking</option><option value="IT">IT</option><option value="AUTO">Auto</option><option value="ENERGY">Energy</option><option value="PHARMA">Pharma</option><option value="FMCG">FMCG</option><option value="OTHER">Other</option></TextField><Button type="submit" variant="outlined" disabled={!marketDataReady}>Search symbols</Button></Box>
          <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2,minmax(0,1fr))' }, gap: 1.25, alignContent: 'start', maxHeight: 330, overflowY: 'auto', pr: .5 }}>{instruments.length === 0 ? <Alert severity="info">Sync the NSE symbols first, then search here.</Alert> : instruments.map(item => <Paper key={item.instrumentToken} component="button" type="button" onClick={() => setSelected(item)} sx={{ minWidth: 0, textAlign: 'left', p: 1.5, cursor: 'pointer', border: selected?.instrumentToken === item.instrumentToken ? '2px solid #078f9f' : '1px solid rgba(108,128,158,.18)', bgcolor: 'background.paper', borderRadius: '10px' }}><Typography fontWeight={800} sx={{ overflowWrap: 'anywhere' }}>{item.tradingsymbol}</Typography><Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{item.sector || 'OTHER'} · {item.name || item.segment}</Typography></Paper>)}</Box>
        </Box>
        {selected && <Paper sx={{ mt: 3, p: 2, display: 'flex', alignItems: { xs: 'stretch', md: 'flex-end' }, gap: 1.5, flexWrap: 'wrap', bgcolor: '#f2f8fb' }}><Box sx={{ minWidth: 0, mr: 'auto', flex: '1 1 220px' }}><Typography variant="overline" color="text.secondary">SELECTED SYMBOL</Typography><Typography fontWeight={800} sx={{ overflowWrap: 'anywhere' }}>{selected.tradingsymbol}</Typography><Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{selected.sector || 'OTHER'} · ready for monitoring</Typography></Box><Button variant="outlined" onClick={addSelectedToMonitor}>Add to Live monitor</Button><TextField sx={{ flex: '1 1 170px' }} type="date" label="Trading date" InputLabelProps={{ shrink: true }} value={tradeDate} onChange={event => setTradeDate(event.target.value)} /><Button variant="contained" onClick={verifyHistorical} disabled={verifying || !marketDataReady}>{verifying ? 'Fetching…' : 'Fetch and evaluate'}</Button></Paper>}
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
    try { const response = await apiFetch('/api/v1/scanner/config'); const data = await response.json(); if (!response.ok) throw new Error(data.message || 'Scanner configuration could not be loaded'); setConfig(data) } catch (error) { notify({ tone: 'error', text: error.message }) }
  }
  useEffect(() => { load() }, [])
  async function save(event) {
    event.preventDefault(); setSaving(true)
    try {
      const response = await apiFetch('/api/v1/scanner/config', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: config.enabled, candleInterval: config.candleInterval, openingRangeCandles: Number(config.openingRangeCandles), volumeLookback: Number(config.volumeLookback), volumeMultiplier: Number(config.volumeMultiplier), minimumCandles: Number(config.minimumCandles) }) })
      const data = await response.json().catch(() => ({})); if (!response.ok) throw new Error(data.message || 'Scanner configuration update failed'); setConfig(data); notify({ tone: 'success', text: 'Scanner strategy configuration updated for this application run.' })
    } catch (error) { notify({ tone: 'error', text: error.message }) } finally { setSaving(false) }
  }
  if (!config) return null
  return <Card><CardContent sx={{ p: { xs: 2.5, md: 3.5 } }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', sm: 'flex-start' }} gap={2} mb={3} sx={{ minWidth: 0 }}><Box sx={{ minWidth: 0 }}><Typography variant="overline" color="text.secondary" letterSpacing=".14em">STRATEGY CONFIGURATION</Typography><Typography variant="h5" sx={{ overflowWrap: 'anywhere' }}>Scanner rules</Typography><Typography variant="body2" color="text.secondary" sx={{ maxWidth: '68ch', lineHeight: 1.6 }}>These values control signal evaluation only; they never place broker orders.</Typography></Box><Chip label={config.enabled ? 'ENABLED' : 'DISABLED'} color={config.enabled ? 'success' : 'default'} /></Stack>
    <Box component="form" onSubmit={save} sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2,minmax(0,1fr))' }, gap: 2 }}>
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
    const response = await apiFetch(path, options)
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
    const response = await apiFetch(path, options)
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
    <Card className="live-card" sx={pageCardSx}><CardContent sx={{ minWidth: 0, p: { xs: 2.5, md: 3.5 } }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', sm: 'flex-start' }} gap={2} mb={2} sx={{ minWidth: 0 }}><Box sx={{ minWidth: 0 }}><Typography variant="overline" color="text.secondary" letterSpacing=".14em">LIVE WATCHLIST · READ ONLY</Typography><Typography variant="h5" sx={{ overflowWrap: 'anywhere' }}>Completed-candle signal monitor</Typography><Typography variant="body2" color="text.secondary" sx={{ maxWidth: '68ch', lineHeight: 1.6 }}>Signals are generated only after a five-minute candle closes. They are alerts, never orders.</Typography></Box><Chip label={status?.state || 'STOPPED'} color={connected ? 'success' : 'default'} /> </Stack>
      <Paper sx={{ minWidth: 0, p: 2, mb: 2.5, bgcolor: '#f4f7fb', overflowX: 'auto' }}><Typography variant="subtitle2" fontWeight={800} mb={1.5}>Live-monitor setup</Typography><Stepper activeStep={!marketDataReady ? 0 : hasSyncedSymbols ? 2 : 1} alternativeLabel sx={{ minWidth: { xs: 520, sm: 0 }, '& .MuiStepLabel-label': { fontSize: { xs: 10, sm: 12 }, whiteSpace: 'normal' } }}><Step><StepLabel>Kite connected</StepLabel></Step><Step><StepLabel>NSE symbols synced</StepLabel></Step><Step><StepLabel>Search and add</StepLabel></Step><Step><StepLabel>Start monitor</StepLabel></Step></Stepper>{!marketDataReady && <Alert severity="warning" action={<Button size="small" onClick={() => onNavigate?.('connection')}>Go to Connection</Button>} sx={{ mt: 2 }}>Connect Kite first. Live monitoring requires an active daily session.</Alert>}{marketDataReady && hasSyncedSymbols === false && <Alert severity="info" action={<Button size="small" onClick={() => onNavigate?.('symbols')}>Sync symbols</Button>} sx={{ mt: 2 }}>Sync NSE symbols before searching and adding a stock.</Alert>}{message && <Alert severity="warning" sx={{ mt: 2 }}>{message}</Alert>}</Paper>
      <Box component="form" onSubmit={search} sx={{ display: 'flex', gap: 1.5, mb: 2, flexWrap: 'wrap' }}><TextField fullWidth sx={{ flex: '1 1 340px' }} label="Search synced NSE symbol" placeholder="SBIN, HDFCBANK, INFY" value={query} onChange={event => setQuery(event.target.value.toUpperCase())} /><Button type="submit" variant="contained" disabled={!query.trim() || searching}>{searching ? 'Searching…' : 'Search and add'}</Button></Box>
      {results.length > 0 && <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', sm: 'repeat(2,minmax(0,1fr))' }, gap: 1.25, mb: 2, maxHeight: 260, overflowY: 'auto' }}>{results.map(item => <Paper key={item.instrumentToken} sx={{ minWidth: 0, p: 1.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}><Box sx={{ minWidth: 0 }}><Typography fontWeight={800} sx={{ overflowWrap: 'anywhere' }}>{item.tradingsymbol}</Typography><Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{item.sector || 'OTHER'} · {item.name || item.segment}</Typography></Box><Button size="small" variant="contained" onClick={() => addSymbol(item)} disabled={busy}>Add</Button></Paper>)}</Box>}
      <Stack direction="row" gap={1} flexWrap="wrap" mb={2.5}><Button variant="outlined" onClick={refresh}>Refresh</Button><Button variant="outlined" onClick={addSelected} disabled={!marketDataReady || !selected || busy}>{selected ? `Add ${selected.tradingsymbol}` : 'Select a symbol above'}</Button>{groups.map(group => <Button key={group} variant="outlined" size="small" onClick={() => addGroup(group)} disabled={!marketDataReady || busy}>{group === 'bank-nifty' ? 'Bank Nifty' : group.toUpperCase()}</Button>)}{connected ? <Button color="error" variant="contained" onClick={() => toggle('/api/v1/live-market/stop')} disabled={busy}>Stop monitor</Button> : <Button variant="contained" onClick={() => toggle('/api/v1/live-market/start')} disabled={!marketDataReady || watchlist.length === 0 || busy}>{busy ? 'Working…' : 'Start monitor'}</Button>}</Stack>
      <Box sx={{ minWidth: 0, display: 'grid', gridTemplateColumns: { xs: 'minmax(0,1fr)', md: 'repeat(2,minmax(0,1fr))' }, gap: 2, mb: 2.5 }}><Paper sx={{ minWidth: 0, p: 2, maxHeight: 300, overflowY: 'auto' }}><Typography variant="overline" color="text.secondary">WATCHLIST · {watchlist.length} SYMBOLS</Typography>{watchlist.length === 0 ? <Typography variant="body2" color="text.secondary" mt={1}>No selected symbols yet.</Typography> : <Stack divider={<Divider flexItem />} mt={1}>{watchlist.map(item => <Stack key={item.instrumentToken} direction="row" justifyContent="space-between" alignItems="center" py={1} gap={1}><Box sx={{ minWidth: 0 }}><Typography fontWeight={750} sx={{ overflowWrap: 'anywhere' }}>{item.symbol}</Typography><Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{status?.latestPrices?.[item.instrumentToken] ? `${money(status.latestPrices[item.instrumentToken])} · ${status.lastTickAt ? new Date(status.lastTickAt).toLocaleTimeString() : 'tick'}` : 'Waiting for first tick'}</Typography></Box><Button size="small" color="error" onClick={() => removeSymbol(item.instrumentToken, item.symbol)} disabled={busy || connected}>Remove</Button></Stack>)}</Stack>}</Paper><Paper sx={{ minWidth: 0, p: 2, maxHeight: 300, overflowY: 'auto' }}><Typography variant="overline" color="text.secondary">SIGNAL HISTORY · ACTIONABLE ONLY</Typography>{signals.length === 0 ? <Typography variant="body2" color="text.secondary" mt={1}>{connected ? 'No actionable signal yet.' : 'Start the monitor after adding symbols.'}</Typography> : <Stack divider={<Divider flexItem />} mt={1}>{signals.slice(0, 20).map(signal => <Box key={`${signal.instrumentToken}-${signal.candleStart}`} py={1}><Typography fontWeight={750} sx={{ overflowWrap: 'anywhere' }} color={signal.type === 'WATCH_LONG' ? 'success.main' : 'error.main'}>{signal.symbol} · {signal.type}</Typography><Typography variant="caption" color="text.secondary">{money(signal.referencePrice)} · {new Date(signal.candleStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</Typography></Box>)}</Stack>}</Paper></Box>
      <Paper sx={{ minWidth: 0, p: 2, mb: 2.5 }}><Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} gap={.75} mb={1}><Typography variant="overline" color="text.secondary">COMPLETED LIVE CANDLES</Typography><Typography variant="caption" color="text.secondary">{candles.length ? 'Latest candles shown first' : 'No completed candle received in this run'}</Typography></Stack>{candles.length === 0 ? <Typography variant="body2" color="text.secondary">The first row appears after the next five-minute boundary receives a tick.</Typography> : <Box sx={{ overflow: 'auto', maxHeight: 350 }}><table className="candle-table"><thead><tr><th>Symbol / time</th><th>OHLC</th><th>Volume</th><th>Evaluation</th><th>VWAP</th></tr></thead><tbody>{candles.slice(0, 30).map(candle => <tr key={`${candle.instrumentToken}-${candle.candleStart}`}><td><strong>{candle.symbol}</strong><small>{new Date(candle.candleStart).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</small></td><td>{money(candle.open)} / {money(candle.high)} / {money(candle.low)} / {money(candle.close)}</td><td>{candle.volume.toLocaleString()}</td><td>{candle.evaluationType}</td><td>{candle.vwap ? money(candle.vwap) : '—'}</td></tr>)}</tbody></table></Box>}</Paper>
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
    const response = await apiFetch(path, options)
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
