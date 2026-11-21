# QuantPilot security baseline

The application keeps broker execution disabled by default. Security and execution are separate switches.

## Local development

Leave these values in the local, uncommitted `.env` while building:

``
env
QUANTPILOT_SECURITY_ENABLED=false
KITE_ORDER_ROUTING_ENABLED=false
AUTOMATED_EXECUTION_ENABLED=false
```

## Private deployment

Before exposing the application through a VPN or HTTPS reverse proxy:

1. Set `QUANTPILOT_SECURITY_ENABLED=true`.
2. Set a unique username.
3. Prefer `QUANTPILOT_SECURITY_PASSWORD_HASH` with a bcrypt hash. A plaintext password is supported only for local development.
4. Set `QUANTPILOT_ALLOWED_ORIGIN` to the exact private application origin.
5. Set `SESSION_COOKIE_SECURE=true` when HTTPS is enabled.
6. Keep the database port private and do not publish PostgreSQL to the internet.
7. Keep `KITE_ORDER_ROUTING_ENABLED=false` and `AUTOMATED_EXECUTION_ENABLED=false` until execution tests are signed off.

To generate a bcrypt value on a machine with Apache `htpasswd`:

```bash
htpasswd -bnBC 12 "" 'replace-with-a-long-password' | tr -d ':\\n'
```

Store the resulting hash in `QUANTPILOT_SECURITY_PASSWORD_HASH`, never in Git or frontend code.

## Implemented controls

- HTTP Basic authentication for API routes when enabled
- Stateless API security and protected actuator exposure
- CSRF disabled for the stateless API; CORS is restricted to one configured origin
- Security headers at both the backend and Nginx edge
- In-memory API rate limiting with `429` and `Retry-After`
- HttpOnly and SameSite session-cookie defaults
- No secrets in browser storage or tracked repository files
- Existing audit and kill-switch paths remain protected by the API gate
