export interface User {
  steamId: string;
  username: string;
  profilePictureUrl: string;
  email?: string;
  authenticated: boolean;
}

export interface AuthResponse {
  steamId: string;
  username: string;
  profilePictureUrl: string;
  email?: string;
  authenticated: boolean;
  message?: string;
}

export interface Game {
  appId: number;
  name: string;
  playtimeHours?: number;
  imgSmallUrl?: string;
  tags: string[];
}

export interface WishlistEntry {
  appId: number;
  name: string;
  addedAt: number;
  priority: string;
  tags: string[];
  currentPrice: number | null;
  originalPrice: number | null;
  discountPercent: number | null;
}

export interface PriceAlert {
  appId: number;
  gameName: string;
  targetPrice: string;
  currentPrice: string | null;
  lastChecked: string | null;
  createdAt: string;
}
