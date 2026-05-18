# Deployment Guide for Google Cloud VM + Netlify

## Part 1: Set Up Google Cloud VM

### 1. Create a VM Instance

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Navigate to **Compute Engine** > **VM Instances**
3. Click **Create Instance**
4. Configure:
   - **Name**: `steamlens-vm`
   - **Region**: Choose closest to your users (e.g., `us-central1`)
   - **Machine type**: `e2-medium` (2 vCPU, 4 GB memory) - good starting point
   - **Boot disk**:
     - Click "Change"
     - Select **Ubuntu 22.04 LTS** // can change to 24.04 for longer lasting support
     - Size: **20 GB** minimum
   - **Firewall**:
     - ✅ Allow HTTP traffic
     - ✅ Allow HTTPS traffic

5. Click **Create**

### 2. Reserve a Static IP Address

1. In Google Cloud Console, go to **VPC Network** > **IP Addresses**
2. Click **Reserve External Static Address**
3. Configure:
   - **Name**: `steamlens-ip`
   - **Region**: Same as your VM
   - **Attached to**: Select your `steamlens-vm`
4. Click **Reserve**
5. **Note down this IP address** - you'll need it for Netlify

### 3. Configure Firewall Rules

1. Go to **VPC Network** > **Firewall**
2. Click **Create Firewall Rule**
3. Configure:
   - **Name**: `allow-http-80`
   - **Direction**: Ingress
   - **Targets**: All instances in the network
   - **Source IP ranges**: `0.0.0.0/0`
   - **Protocols and ports**:
     - ✅ TCP: `80`
4. Click **Create**

### 4. SSH into Your VM

1. In **VM Instances**, click **SSH** next to your VM
2. A terminal window will open

### 5. Install Docker and Docker Compose
// find good instructions online on how to install on ubunto. (docker.com)
Run these commands in the SSH terminal:

```bash
# Update package list
sudo apt-get update

# Install prerequisites
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# Add Docker's official GPG key
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up Docker repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Add your user to docker group (so you don't need sudo)
sudo usermod -aG docker $USER

# Apply group changes (or logout/login)
newgrp docker

# Verify installation
docker --version
docker compose version
```

### 6. Install Git

```bash
sudo apt-get install -y git
```

### 7. Clone Your Repository

```bash
# Clone your repo
git clone https://github.com/Superkart/SteamLens.git

# Navigate to the project
cd SteamLens
```

### 8. Register Steam API Key (IMPORTANT)

Before configuring your environment, you need to register a Steam API key for production:

1. Go to https://steamcommunity.com/dev/apikey
2. Log in with your Steam account
3. **Domain Name**: Enter `http://YOUR_VM_IP_ADDRESS` (replace with your actual VM IP from step 2)
   - Example: `http://34.123.45.67`
4. Agree to terms and click **Register**
5. **Copy your API key** - you'll need it in the next step

**Note:** While your localhost API key might work, it's best practice to register a new one with your production IP for Steam's OAuth authentication.

### 9. Set Up Environment Variables

```bash
# Copy the production env template
cp .env.production .env

# Edit the .env file with your actual values
nano .env
```

**Update these values in the `.env` file:**

1. **DATABASE_PASSWORD**: Choose a strong password (e.g., use `openssl rand -base64 32`)
2. **STEAM_API_KEY**: Paste the API key you just registered in step 8
3. **APP_BASE_URL**: Set to `http://YOUR_VM_IP_ADDRESS` (replace with your actual VM IP)
   - Example: `APP_BASE_URL=http://34.123.45.67`
   - ⚠️ **CRITICAL**: This must match your VM IP for Steam OAuth to work
4. **APP_FRONTEND_URL**: You'll update this after deploying to Netlify (step 13)
   - Leave as placeholder for now, or set to `https://your-site-name.netlify.app`
5. **Email settings**: Already filled in (steamlenssalealerts@gmail.com)

**Your `.env` file should look like this:**
```bash
DATABASE_PASSWORD=YourStrongPasswordHere123!
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=steamlenssalealerts@gmail.com
MAIL_PASSWORD=znvifhzqmjvaqaks
STEAM_API_KEY=ABC123DEF456GHI789JKL012MNO345PQ  # Your actual key
APP_BASE_URL=http://34.123.45.67  # Your actual VM IP
APP_FRONTEND_URL=https://your-site-name.netlify.app  # Update after Netlify deployment
```

Press `Ctrl+X`, then `Y`, then `Enter` to save.

### 9. Build and Run with Docker Compose

```bash
# Build and start the services
docker compose up -d --build

# Check if services are running
docker compose ps

# View logs (optional)
docker compose logs -f backend
```

**Your backend is now running on port 80!**

To verify it's working:
```bash
# Test the API endpoint
curl http://localhost/api/auth/steam/me
# You should get an "Not authenticated" response, which means the backend is running
```

### 10. Useful Docker Commands

```bash
# View running containers
docker compose ps

# View logs
docker compose logs -f backend
docker compose logs -f postgres

# Restart services
docker compose restart

# Stop services
docker compose down

# Stop and remove volumes (⚠️ deletes database data)
docker compose down -v

# Rebuild after code changes
git pull
docker compose up -d --build

# Access database directly
docker compose exec postgres psql -U postgres -d steamlens
```

---

## Part 2: Deploy Frontend to Netlify

### 1. Build Your Frontend

On your local machine:

```bash
cd frontend
npm install
npm run build
```

This creates a `dist` folder with your production-ready frontend.

### 2. Deploy to Netlify

1. Go to [Netlify](https://app.netlify.com/)
2. Sign up or log in
3. Click **Add new site** > **Deploy manually**
4. Drag and drop your `frontend/dist` folder
5. Wait for deployment to complete

### 3. Configure API Proxy

After deployment:

1. In your Netlify site, go to **Site settings** > **Build & deploy** > **Post processing**
2. Click **Edit settings** for **Asset optimization**
3. Go to **Site configuration** > **Redirects and rewrites**
4. Click **Add redirect rule**

**Note:** A `netlify.toml` file already exists in your `frontend` folder. You just need to update it:

1. Open `frontend/netlify.toml`
2. Replace `YOUR_VM_IP_ADDRESS` with your reserved static IP from Google Cloud

The file should look like this:
```toml
# Proxy all API requests to backend
[[redirects]]
  from = "/api/*"
  to = "http://YOUR_VM_IP_ADDRESS:80/api/:splat"
  status = 200
  force = true

# SPA fallback - all other routes go to index.html
[[redirects]]
  from = "/*"
  to = "/index.html"
  status = 200
```

**Replace `YOUR_VM_IP_ADDRESS` with your reserved static IP from Google Cloud.**

Then redeploy:
```bash
npm run build
# Drag and drop the new dist folder to Netlify
```

### 4. Update Backend with Netlify URL (CRITICAL)

Now that you have your Netlify site URL, you need to update the backend configuration:

1. **Copy your Netlify site URL** (e.g., `https://steamlens-app.netlify.app`)
2. **SSH back into your VM**
3. **Update the `.env` file:**

```bash
cd ~/SteamLens
nano .env
```

4. **Find and update the `APP_FRONTEND_URL` line:**
```bash
APP_FRONTEND_URL=https://your-actual-site-name.netlify.app
```

5. **Restart the backend** to apply changes:
```bash
docker compose restart backend
```

⚠️ **This is REQUIRED** - Without this, Steam OAuth won't redirect users back to your frontend after login!

---

## Part 3: Verify Everything Works

1. **Visit your Netlify site** (e.g., `https://your-site-name.netlify.app`)
2. **Test login** with Steam OAuth
3. **Check wishlist** loads properly
4. **Create a price alert** to test the full flow

---

## Troubleshooting

### Backend not starting?

```bash
# Check logs
docker compose logs backend

# Check if database is ready
docker compose logs postgres

# Restart everything
docker compose down
docker compose up -d
```

### Can't connect from frontend?

1. Verify your VM's static IP is correct in `netlify.toml`
2. Check firewall allows port 80
3. Test backend directly: `http://YOUR_VM_IP/api/auth/steam/me`
   - You should see: `{"error":"Not authenticated"}` which means it's working

### Database errors?

```bash
# Access database
docker compose exec postgres psql -U postgres -d steamlens

# View tables
\dt

# Exit
\q
```

### Need to update code?

```bash
# On VM
cd ~/SteamLens
git pull
docker compose up -d --build
```

---

## Costs Estimate

- **Google Cloud VM** (e2-medium): ~$25-30/month
- **Static IP**: $0 (free while attached to running VM)
- **Netlify**: Free tier (100GB bandwidth/month)

**Total**: ~$25-30/month

---

## Security Notes

1. ✅ Your `.env` file is gitignored (contains secrets)
2. ✅ Database is only accessible within Docker network
3. ✅ Gmail App Password is used (not your actual password)
4. ⚠️ Consider setting up HTTPS with Let's Encrypt (optional but recommended)
5. ⚠️ Consider restricting SSH access to your IP only

---

## Optional: Set Up HTTPS

If you want to use your own domain with HTTPS:

1. Buy a domain (e.g., from Google Domains, Namecheap)
2. Point domain's A record to your VM's static IP
3. Install Certbot on VM for free SSL certificate
4. Update Netlify redirects to use `https://yourdomain.com`

Let me know if you want help with this step!
