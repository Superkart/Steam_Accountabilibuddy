import { useEffect, useState } from 'react';
import type { WishlistEntry } from '../types/auth';
import { authApi } from '../services/api';
import './WishlistDisplay.css';

export const WishlistDisplay = () => {
  const [wishlist, setWishlist] = useState<WishlistEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadWishlist();
  }, []);

  const loadWishlist = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await authApi.getWishlist();
      setWishlist(data);
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load wishlist');
      }
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="wishlist-container">
        <h2>Your Wishlist</h2>
        <div className="loading">Loading wishlist...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="wishlist-container">
        <h2>Your Wishlist</h2>
        <div className="error-message">
          <h3>⚠️ Unable to load wishlist</h3>
          <p>{error}</p>
          <div className="help-text">
            <strong>If your wishlist is private:</strong>
            <ol>
              <li>Go to your Steam profile</li>
              <li>Click "Edit Profile"</li>
              <li>Click "Privacy Settings"</li>
              <li>Set "Game details" to "Public"</li>
              <li>Refresh this page</li>
            </ol>
          </div>
          <button onClick={loadWishlist} className="retry-button">
            Try Again
          </button>
        </div>
      </div>
    );
  }

  if (wishlist.length === 0) {
    return (
      <div className="wishlist-container">
        <h2>Your Wishlist</h2>
        <div className="empty-state">
          <p>Your wishlist is empty!</p>
          <p>Add some games on Steam to see them here.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="wishlist-container">
      <div className="wishlist-header">
        <h2>Your Wishlist</h2>
        <span className="game-count">{wishlist.length} items</span>
      </div>
      <div className="wishlist-list">
        {wishlist.map((game) => (
          <div key={game.appId} className="wishlist-item">
            <div className="item-left">
              <img
                src={`https://cdn.akamai.steamstatic.com/steam/apps/${game.appId}/header.jpg`}
                alt={game.name}
                className="game-capsule"
                onError={(e) => {
                  e.currentTarget.src = 'https://via.placeholder.com/292x136/1b2838/ffffff?text=No+Image';
                }}
              />
            </div>
            <div className="item-center">
              <h3 className="game-title">{game.name}</h3>
              {game.tags && game.tags.length > 0 && (
                <div className="game-tags">
                  {game.tags.slice(0, 5).map((tag, index) => (
                    <span key={index} className="tag">
                      {tag}
                    </span>
                  ))}
                </div>
              )}
              <div className="game-meta">
                Added on {new Date((game.addedAt || 0) * 1000).toLocaleDateString()}
              </div>
            </div>
            <div className="item-right">
              {game.currentPrice !== undefined && game.currentPrice !== null ? (
                <div className="price-container">
                  {game.currentPrice > 0 ? (
                    <div className="price-box">
                      <span className="price">${Number(game.currentPrice).toFixed(2)}</span>
                    </div>
                  ) : (
                    <div className="free-box">
                      <span className="free-text">Free To Play</span>
                    </div>
                  )}
                </div>
              ) : (
                <div className="price-container">
                  <span className="price-unavailable">Price unavailable</span>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
