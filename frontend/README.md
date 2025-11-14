# Steam Accountabilibuddy - Frontend

React + TypeScript frontend for Steam Accountabilibuddy, built with Vite.

## Features

- 🎮 Steam OpenID authentication
- 📚 View your Steam library (games with ≤2 hours playtime)
- 🎯 Browse your wishlist with current prices
- 💰 Price alerts for wishlist games
- 🏷️ Smart recommendations based on game tags

## Tech Stack

- **React 18** - UI library
- **TypeScript** - Type safety
- **Vite** - Build tool and dev server
- **Axios** - HTTP client
- **React Router** (coming soon) - Routing

## Project Structure

```
src/
├── components/       # Reusable UI components
│   └── SteamLoginButton.tsx
├── context/         # React context providers
│   └── AuthContext.tsx
├── pages/           # Page components
│   └── LoginPage.tsx
├── services/        # API services
│   └── api.ts
├── styles/          # CSS files
│   ├── LoginPage.css
│   └── SteamLoginButton.css
├── types/           # TypeScript type definitions
│   └── auth.ts
├── App.tsx          # Main app component
└── main.tsx         # App entry point
```

## Getting Started

### Prerequisites

- Node.js 18+ installed
- Backend server running on `http://localhost:8080`

### Installation

```bash
# Install dependencies
npm install
```

### Development

```bash
# Start dev server (runs on http://localhost:3000)
npm run dev
```

The dev server includes a proxy that forwards `/auth/*` and `/api/*` requests to the backend at `http://localhost:8080`.

### Build for Production

```bash
# Build for production
npm run build

# Preview production build
npm run preview
```

## API Integration

The frontend communicates with the Spring Boot backend via these endpoints:

- `GET /auth/steam/login` - Redirects to Steam login
- `GET /auth/steam/return` - Handles Steam OAuth callback
- `GET /auth/steam/library` - Fetches user's Steam library
- `GET /auth/steam/wishlist` - Fetches user's wishlist with prices

All requests use `withCredentials: true` to maintain session cookies.

## Authentication Flow

1. User clicks "Sign in through Steam" button
2. App redirects to `/auth/steam/login`
3. Backend redirects to Steam OpenID
4. User authenticates with Steam
5. Steam redirects back to `/auth/steam/return`
6. Backend verifies authentication and creates session
7. Frontend detects successful auth and loads user data

## Environment Variables

The Vite dev server is configured to proxy requests to `localhost:8080` by default. To change this, update `vite.config.ts`.

## TODO

- [ ] Add routing with React Router
- [ ] Create dashboard page
- [ ] Display library and wishlist
- [ ] Add price alert management UI
- [ ] Show game recommendations
- [ ] Add user profile page
- [ ] Implement logout functionality
