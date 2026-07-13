# SteamLens Deployment Runbook (GCP VM + Netlify)

This document is the production deployment path for this repository:
- Backend + PostgreSQL on a Google Cloud VM using Docker Compose
- Frontend on Netlify
- Steam OAuth URLs aligned with deployed backend/frontend URLs

---

## 1) Validate repository deployment configuration

Review these files before deployment:

- `/home/runner/work/SteamLens/SteamLens/docker-compose.yml`
- `/home/runner/work/SteamLens/SteamLens/.env.production`
- `/home/runner/work/SteamLens/SteamLens/steam-lens/src/main/resources/application.properties`
- `/home/runner/work/SteamLens/SteamLens/frontend/netlify.toml`
- `/home/runner/work/SteamLens/SteamLens/netlify.toml`

### What to verify

- `docker-compose.yml` passes these backend env vars into the container:
  - `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
  - `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
  - `STEAM_API_KEY`
  - `APP_BASE_URL`, `APP_FRONTEND_URL`
- `.env.production` contains placeholders only (no secrets committed).
- `application.properties` reads runtime values from env vars (`APP_BASE_URL`, `APP_FRONTEND_URL`, DB, mail, steam key).
- Netlify redirects proxy `/api/*` to your GCP backend static IP.

Why: Steam OAuth + session flow depends on correct backend/frontend URL and environment alignment.

---

## 2) Prepare GCP infrastructure

### 2.1 Create VM

1. Open Google Cloud Console → **Compute Engine** → **VM instances**
2. Click **Create instance**
3. Suggested settings:
   - Name: `steamlens-vm`
   - Region: closest to users
   - Machine type: `e2-medium`
   - OS: Ubuntu 22.04/24.04 LTS
   - Disk: 20 GB+
4. Enable HTTP/HTTPS traffic

### 2.2 Reserve static external IP

1. Go to **VPC network** → **IP addresses**
2. Reserve external static IP in same region as VM
3. Attach it to `steamlens-vm`
4. Record it as `<GCP_STATIC_IP>`

### 2.3 Firewall rules

Allow inbound TCP:
- `80` (backend/API access via Netlify redirect)
- `443` (for future HTTPS termination)

Why: static IP prevents OAuth breakage across VM restarts; firewall exposes only required ports.

---

## 3) Install runtime on VM

SSH into VM and run:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release git

sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker $USER
newgrp docker

docker --version
docker compose version
git --version
```

Why: deployment is Docker Compose based.

---

## 4) Pull code onto VM

```bash
cd ~
git clone https://github.com/Superkart/SteamLens.git
cd SteamLens
```

If repo already exists:

```bash
cd ~/SteamLens
git pull
```

Why: VM needs `docker-compose.yml`, backend Dockerfile, and env template from repo.

---

## 5) Create production `.env` on VM

```bash
cd ~/SteamLens
cp .env.production .env
nano .env
```

Set real values:

```bash
DATABASE_PASSWORD=<strong-random-password>
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<your-email>
MAIL_PASSWORD=<gmail-app-password>
STEAM_API_KEY=<steam-web-api-key>
APP_BASE_URL=http://<GCP_STATIC_IP>
APP_FRONTEND_URL=https://<your-netlify-site>.netlify.app
```

Why: backend reads runtime config from env vars; OAuth redirect targets depend on these values.

Security:
- Never commit `.env`
- Rotate credentials immediately if they were previously exposed

---

## 6) Register/verify Steam API key for production

1. Visit https://steamcommunity.com/dev/apikey
2. Use `http://<GCP_STATIC_IP>` (or your production backend domain) as domain
3. Save key and place it in `.env` (`STEAM_API_KEY`)

Why: Steam OpenID/API behavior can fail if production domain/realm is misaligned.

---

## 7) Start backend stack

From `~/SteamLens` on VM:

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

Why: starts PostgreSQL and Spring Boot backend with production env vars.

---

## 8) Verify backend health before frontend cutover

```bash
curl http://localhost/api/auth/steam/me
```

Expected: unauthenticated response (for example `{"error":"Not authenticated"}`)

Why: confirms container networking and backend routing are working.

---

## 9) Deploy frontend to Netlify

On local machine:

```bash
cd /home/runner/work/SteamLens/SteamLens/frontend
npm install
npm run build
```

Deploy `frontend/dist` to Netlify (manual deploy) or connect repository in Netlify.

Why: frontend is static and independently hosted.

---

## 10) Point Netlify API proxy to GCP backend

Update these files before deploying frontend:

- `/home/runner/work/SteamLens/SteamLens/frontend/netlify.toml`
- `/home/runner/work/SteamLens/SteamLens/netlify.toml` (if using repo-root Netlify config)

Replace:

```toml
to = "http://YOUR_GCP_STATIC_IP/api/:splat"
```

with your real static IP.

Rebuild/redeploy frontend after the change.

Why: frontend calls `/api/*`; Netlify must rewrite those requests to backend VM.

---

## 11) Final OAuth alignment pass

After Netlify URL is final, confirm VM `.env` contains exact frontend URL:

```bash
APP_FRONTEND_URL=https://<your-netlify-site>.netlify.app
```

Then restart backend:

```bash
cd ~/SteamLens
docker compose restart backend
```

Why: Steam login redirect and session return flow require exact frontend URL alignment.

---

## 12) Post-deploy verification checklist

- Steam login succeeds end-to-end
- Library loads
- Wishlist loads
- Price alert create/delete works
- Test email notification path
- Share wishlist link works

Use logs during validation:

```bash
docker compose logs -f backend
docker compose logs -f postgres
```

---

## 13) Immediate hardening tasks (recommended)

- Add HTTPS termination (Nginx/Caddy/LB + certificate)
- Restrict SSH ingress to trusted IPs only
- Configure automated DB backups
- Add uptime/health monitoring + alerting

Why: production reliability and security.

---

## Useful operational commands

```bash
# status
docker compose ps

# restart all services
docker compose restart

# stop stack
docker compose down

# rebuild after pulling changes
git pull
docker compose up -d --build

# inspect backend logs
docker compose logs -f backend

# inspect database logs
docker compose logs -f postgres
```
