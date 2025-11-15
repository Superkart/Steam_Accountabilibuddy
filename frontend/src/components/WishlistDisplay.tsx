import { useEffect, useState } from 'react';
import type { WishlistEntry, Game } from '../types/auth';
import { authApi } from '../services/api';
import './WishlistDisplay.css';

type SortOption = 'dateAdded' | 'priority' | 'priceAsc' | 'priceDesc';

export const WishlistDisplay = () => {
  const [wishlist, setWishlist] = useState<WishlistEntry[]>([]);
  const [library, setLibrary] = useState<Game[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedGameId, setExpandedGameId] = useState<number | null>(null);
  const [similarGamesMap, setSimilarGamesMap] = useState<Map<number, Game[]>>(new Map());
  const [sortBy, setSortBy] = useState<SortOption>('dateAdded');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      // Load both wishlist and library in parallel
      const [wishlistData, libraryData] = await Promise.all([
        authApi.getWishlist(),
        authApi.getLibrary()
      ]);
      setWishlist(wishlistData);
      setLibrary(libraryData);
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load data');
      }
    } finally {
      setLoading(false);
    }
  };

  const findSimilarGames = (wishlistGame: WishlistEntry): Game[] => {
    if (!wishlistGame.tags || wishlistGame.tags.length === 0) {
      return [];
    }

    const wishlistTags = new Set(wishlistGame.tags.map(tag => tag.toLowerCase()));

    // Find library games that share tags with the wishlist game
    const gamesWithScores = library.map(game => {
      const gameTags = new Set((game.tags || []).map(tag => tag.toLowerCase()));
      const commonTags = [...wishlistTags].filter(tag => gameTags.has(tag));
      return {
        game,
        score: commonTags.length,
        commonTags
      };
    }).filter(item => item.score > 0);

    // Sort by number of common tags (descending) and return top 5
    return gamesWithScores
      .sort((a, b) => b.score - a.score)
      .slice(0, 5)
      .map(item => item.game);
  };

  const toggleSimilarGames = (appId: number) => {
    if (expandedGameId === appId) {
      // Collapse if already expanded
      setExpandedGameId(null);
    } else {
      // Expand and compute similar games if not already computed
      setExpandedGameId(appId);
      if (!similarGamesMap.has(appId)) {
        const wishlistGame = wishlist.find(g => g.appId === appId);
        if (wishlistGame) {
          const similar = findSimilarGames(wishlistGame);
          setSimilarGamesMap(new Map(similarGamesMap.set(appId, similar)));
        }
      }
    }
  };

  const getSortedWishlist = (): WishlistEntry[] => {
    const sorted = [...wishlist];

    switch (sortBy) {
      case 'dateAdded':
        // Sort by date added (newest first)
        return sorted.sort((a, b) => b.addedAt - a.addedAt);

      case 'priority':
        // Sort by your rank (lower number = higher priority/rank)
        return sorted.sort((a, b) => {
          const aPriority = parseInt(a.priority) || Number.MAX_VALUE;
          const bPriority = parseInt(b.priority) || Number.MAX_VALUE;
          return aPriority - bPriority;
        });

      case 'priceAsc':
        // Sort by price (low to high)
        return sorted.sort((a, b) => {
          const aPrice = a.currentPrice ?? Number.MAX_VALUE;
          const bPrice = b.currentPrice ?? Number.MAX_VALUE;
          return aPrice - bPrice;
        });

      case 'priceDesc':
        // Sort by price (high to low)
        return sorted.sort((a, b) => {
          const aPrice = a.currentPrice ?? -1;
          const bPrice = b.currentPrice ?? -1;
          return bPrice - aPrice;
        });

      default:
        return sorted;
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
          <button onClick={loadData} className="retry-button">
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
        <div className="header-controls">
          <span className="game-count">{wishlist.length} items</span>
          <div className="sort-controls">
            <label htmlFor="sort-select" className="sort-label">Sort by:</label>
            <select
              id="sort-select"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as SortOption)}
              className="sort-dropdown"
            >
              <option value="dateAdded">Date Added</option>
              <option value="priority">Priority</option>
              <option value="priceAsc">Price: Low to High</option>
              <option value="priceDesc">Price: High to Low</option>
            </select>
          </div>
        </div>
      </div>
      <div className="wishlist-list">
        {getSortedWishlist().map((game) => (
          <div key={game.appId}>
            <div className="wishlist-item">
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
                <button
                  className="similar-games-button"
                  onClick={() => toggleSimilarGames(game.appId)}
                >
                  {expandedGameId === game.appId ? 'Hide' : 'Show'} Similar Games
                </button>
              </div>
            </div>

            {expandedGameId === game.appId && (
              <div className="similar-games-container">
                {similarGamesMap.get(game.appId)?.length ? (
                  <>
                    <h4 className="similar-games-title">Similar Games in Your Library:</h4>
                    <div className="similar-games-list">
                      {similarGamesMap.get(game.appId)?.map((libGame) => (
                        <div key={libGame.appId} className="similar-game-item">
                          {libGame.imgSmallUrl && (
                            <img
                              src={libGame.imgSmallUrl}
                              alt={libGame.name}
                              className="similar-game-img"
                            />
                          )}
                          <div className="similar-game-info">
                            <div className="similar-game-name">{libGame.name}</div>
                            {libGame.playtimeHours !== undefined && (
                              <div className="similar-game-playtime">
                                {libGame.playtimeHours.toFixed(1)} hours played
                              </div>
                            )}
                            {libGame.tags && libGame.tags.length > 0 && (
                              <div className="similar-game-tags">
                                {libGame.tags.slice(0, 3).map((tag, idx) => (
                                  <span key={idx} className="similar-tag">{tag}</span>
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </>
                ) : (
                  <div className="no-similar-games">
                    No similar games found in your library.
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
