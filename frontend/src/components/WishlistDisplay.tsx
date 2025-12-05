import { useCallback, useEffect, useState } from 'react';
import type { WishlistEntry, Game, PriceAlert } from '../types/auth';
import { authApi } from '../services/api';
import { PriceAlertButton } from './PriceAlertButton';
import './WishlistDisplay.css';

type SortOption = 'dateAdded' | 'priority' | 'priceAsc' | 'priceDesc';
type SimilarGameWithScore = { game: Game; score: number; commonTags: string[] };
type WishlistMatch = { entry: WishlistEntry; score: number; commonTags: string[] };
type BacklogRecommendation = {
  game: Game;
  matches: WishlistMatch[];
  totalScore: number;
};

const GENERIC_TAGS = new Set([
  'action',
  'adventure',
  'indie',
  'casual',
  'rpg',
  'strategy',
  'simulation',
  'multiplayer',
  'singleplayer',
  'co-op',
  'coop',
  'online co-op',
  'local co-op',
  'controller',
  'family friendly',
  'sports',
  'free to play',
  'fantasy',
  'pixel graphics'
]);

const MIN_COMMON_TAGS = 2;
const MIN_MATCH_SCORE = 1.3;
const MAX_RECOMMENDATIONS = 5;

const normalizeTags = (tags?: string[]) => {
  const map = new Map<string, string>();
  if (!tags) {
    return map;
  }

  tags.forEach(tag => {
    if (!tag) return;
    const normalized = tag.toLowerCase().trim();
    if (!normalized || GENERIC_TAGS.has(normalized)) {
      return;
    }
    if (!map.has(normalized)) {
      map.set(normalized, tag);
    }
  });

  return map;
};

const buildTagFrequency = (wishlistData: WishlistEntry[]) => {
  const frequency = new Map<string, number>();

  wishlistData.forEach(entry => {
    const tagMap = normalizeTags(entry.tags);
    tagMap.forEach((_original, normalized) => {
      frequency.set(normalized, (frequency.get(normalized) || 0) + 1);
    });
  });

  return frequency;
};

const getTagWeight = (normalizedTag: string, frequencyMap: Map<string, number>) => {
  const freq = frequencyMap.get(normalizedTag) ?? 1;
  return 1 / Math.log2(freq + 1.5);
};

const computeBacklogRecommendations = (wishlistData: WishlistEntry[], libraryData: Game[]): BacklogRecommendation[] => {
  if (!wishlistData.length || !libraryData.length) {
    return [];
  }

  const tagFrequency = buildTagFrequency(wishlistData);
  const recommendationsList: BacklogRecommendation[] = [];

  libraryData.forEach((game) => {
    if (!game.appId) {
      return;
    }

    const gameTagMap = normalizeTags(game.tags);
    if (!gameTagMap.size) {
      return;
    }

    const matches: WishlistMatch[] = [];

    wishlistData.forEach(entry => {
      const entryTagMap = normalizeTags(entry.tags);
      if (!entryTagMap.size) {
        return;
      }

      const overlappingTags: string[] = [];
      gameTagMap.forEach((_value, normalizedTag) => {
        if (entryTagMap.has(normalizedTag)) {
          overlappingTags.push(normalizedTag);
        }
      });

      if (overlappingTags.length < MIN_COMMON_TAGS) {
        return;
      }

      const score = overlappingTags.reduce((sum, normalizedTag) => {
        return sum + getTagWeight(normalizedTag, tagFrequency);
      }, 0);

      if (score < MIN_MATCH_SCORE) {
        return;
      }

      const commonTags = overlappingTags.map(tag => entryTagMap.get(tag) ?? gameTagMap.get(tag) ?? tag);

      matches.push({
        entry,
        score,
        commonTags
      });
    });

    if (matches.length) {
      matches.sort((a, b) => b.score - a.score);
      const totalScore = matches.reduce((sum, match) => sum + match.score, 0);
      recommendationsList.push({
        game,
        matches,
        totalScore
      });
    }
  });

  return recommendationsList
    .sort((a, b) => {
      if (b.matches.length !== a.matches.length) {
        return b.matches.length - a.matches.length;
      }
      if (b.totalScore !== a.totalScore) {
        return b.totalScore - a.totalScore;
      }
      return (a.game.name || '').localeCompare(b.game.name || '');
    })
    .slice(0, MAX_RECOMMENDATIONS);
};

export const WishlistDisplay = () => {
  const [wishlist, setWishlist] = useState<WishlistEntry[]>([]);
  const [library, setLibrary] = useState<Game[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedGameId, setExpandedGameId] = useState<number | null>(null);
  const [similarGamesMap, setSimilarGamesMap] = useState<Map<number, SimilarGameWithScore[]>>(new Map());
  const [sortBy, setSortBy] = useState<SortOption>('dateAdded');
  const [priceAlerts, setPriceAlerts] = useState<PriceAlert[]>([]);
  const [recommendations, setRecommendations] = useState<BacklogRecommendation[]>([]);
  const [expandedRecommendationId, setExpandedRecommendationId] = useState<number | null>(null);
  // Share modal state
  const [shareModalVisible, setShareModalVisible] = useState(false);
  const [generatedLink, setGeneratedLink] = useState<string | null>(null);
  const [sharing, setSharing] = useState(false);
  const [shareError, setShareError] = useState<string | null>(null);

  const loadPriceAlerts = useCallback(async () => {
    try {
      const alerts = await authApi.getPriceAlerts();
      setPriceAlerts(alerts || []);
    } catch {
      // User might not have email set, which is fine - just use empty array
      setPriceAlerts([]);
    }
  }, []);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      // Load both wishlist and library in parallel
      const [wishlistData, libraryData] = await Promise.all([
        authApi.getWishlist(),
        authApi.getLibrary()
      ]);
      console.log('Wishlist data:', wishlistData);
      setWishlist(wishlistData);
      setLibrary(libraryData);
      setRecommendations(computeBacklogRecommendations(wishlistData, libraryData));
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load data');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
    loadPriceAlerts().catch(err => {
      console.log('Price alerts not available:', err);
    });
  }, [loadData, loadPriceAlerts]);

  const findSimilarGames = (wishlistGame: WishlistEntry): Array<{ game: Game; score: number; commonTags: string[] }> => {
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
    }).filter(item => item.score >= 4); // Require at least 4 common tags for meaningful similarity

    // Sort by number of common tags (descending) and return top 5
    return gamesWithScores
      .sort((a, b) => b.score - a.score)
      .slice(0, 5);
  };

  const toggleSimilarGames = (appId: number) => {
    if (expandedGameId === appId) {
      // Collapse if already expanded
      setExpandedGameId(null);
    } else {
      // Expand and compute similar games if not already computed
      setExpandedGameId(appId);
      setSimilarGamesMap(prev => {
        if (prev.has(appId)) {
          return prev;
        }
        const wishlistGame = wishlist.find(g => g.appId === appId);
        if (!wishlistGame) {
          return prev;
        }
        const similar = findSimilarGames(wishlistGame);
        const next = new Map(prev);
        next.set(appId, similar);
        return next;
      });
    }
  };

  const toggleRecommendation = (appId: number) => {
    setExpandedRecommendationId(prev => (prev === appId ? null : appId));
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
      {recommendations.length > 0 && (
        <div className="backlog-recommendations">
          <div className="backlog-header">
            <h3>Backlog Recommendations</h3>
            <p>These unplayed library games closely match your wishlist interests.</p>
          </div>
          <div className="backlog-list">
            {recommendations.map((recommendation) => (
              <div key={recommendation.game.appId} className="backlog-card">
                <div className="backlog-card-main">
                  <div className="backlog-card-left">
                    {recommendation.game.appId && (
                      <img
                        src={`https://cdn.akamai.steamstatic.com/steam/apps/${recommendation.game.appId}/header.jpg`}
                        alt={recommendation.game.name}
                        className="backlog-card-img"
                        onError={(e) => {
                          e.currentTarget.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="292" height="136"%3E%3Crect fill="%231b2838" width="292" height="136"/%3E%3Ctext fill="%23ffffff" font-family="Arial" font-size="14" x="50%25" y="50%25" text-anchor="middle" dominant-baseline="middle"%3ENo Image%3C/text%3E%3C/svg%3E';
                        }}
                      />
                    )}
                  </div>
                  <div className="backlog-card-content">
                    <div className="backlog-card-header">
                      <div>
                        <h4>{recommendation.game.name}</h4>
                        <span className="backlog-match-count">
                          Matches {recommendation.matches.length} wishlist game{recommendation.matches.length > 1 ? 's' : ''}
                        </span>
                      </div>
                      {recommendation.game.playtimeHours !== undefined && (
                        <span className="backlog-playtime">
                          {recommendation.game.playtimeHours.toFixed(1)} hrs played
                        </span>
                      )}
                    </div>
                    {recommendation.game.tags && recommendation.game.tags.length > 0 && (
                      <div className="backlog-tags">
                        {recommendation.game.tags.slice(0, 6).map((tag, idx) => (
                          <span key={idx} className="tag">
                            {tag}
                          </span>
                        ))}
                      </div>
                    )}
                    <button
                      className="matching-wishlist-button"
                      onClick={() => toggleRecommendation(recommendation.game.appId!)}
                    >
                      {expandedRecommendationId === recommendation.game.appId
                        ? 'Hide'
                        : 'Show'}{' '}
                      Matching Wishlist Games
                    </button>
                  </div>
                </div>
                {expandedRecommendationId === recommendation.game.appId && (
                  <div className="matching-wishlist-container">
                    {recommendation.matches.map(match => (
                      <div key={match.entry.appId} className="matching-wishlist-item">
                        <div className="matching-wishlist-info">
                          <div className="matching-wishlist-title">
                            {match.entry.name}
                            <span className="similarity-score"> ({match.commonTags.length} shared tags)</span>
                          </div>
                          <div className="matching-tags">
                            <strong>Common tags:</strong> {match.commonTags.join(', ')}
                          </div>
                        </div>
                        <PriceAlertButton
                          appId={match.entry.appId}
                          gameName={match.entry.name}
                          currentPrice={match.entry.currentPrice}
                          originalPrice={match.entry.originalPrice}
                          hasAlert={priceAlerts.some(alert => alert.appId === match.entry.appId)}
                          onAlertChange={loadPriceAlerts}
                        />
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
      <div className="wishlist-header">
        <h2>Your Wishlist</h2>
        <div className="header-controls">
          <span className="game-count">{wishlist.length} items</span>
          <button
            className="share-wishlist-button"
            onClick={async () => {
              setSharing(true);
              setShareError(null);
              try {
                const resp = await authApi.createShareLink();
                setGeneratedLink(resp.shareUrl);
                setShareModalVisible(true);
                try {
                  await navigator.clipboard.writeText(resp.shareUrl);
                } catch {
                  // ignore clipboard failures
                }
              } catch (err) {
                setShareError(err instanceof Error ? err.message : 'Failed to create share link');
              } finally {
                setSharing(false);
              }
            }}
            disabled={sharing}
          >
            {sharing ? 'Sharing...' : 'Share Wishlist'}
          </button>
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
      {/* Share modal */}
      {shareModalVisible && (
        <div className="share-modal-overlay">
          <div className="share-modal">
            <h3>Share Your Wishlist</h3>
            {shareError && <div className="error-message">{shareError}</div>}
            <p>Anyone with this link can view a snapshot of your wishlist.</p>
            <input
              className="share-link-input"
              readOnly
              value={generatedLink ?? ''}
              onClick={(e) => (e.currentTarget as HTMLInputElement).select()}
            />
            <div className="share-modal-actions">
              <button
                onClick={() => {
                  if (generatedLink) {
                    navigator.clipboard.writeText(generatedLink).catch(() => {});
                  }
                }}
              >
                Copy Link
              </button>
              <button onClick={() => setShareModalVisible(false)}>Close</button>
            </div>
          </div>
        </div>
      )}
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
                    e.currentTarget.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="292" height="136"%3E%3Crect fill="%231b2838" width="292" height="136"/%3E%3Ctext fill="%23ffffff" font-family="Arial" font-size="14" x="50%25" y="50%25" text-anchor="middle" dominant-baseline="middle"%3ENo Image%3C/text%3E%3C/svg%3E';
                  }}
                />
              </div>
              <div className="item-center">
                <h3 className="game-title">{game.name}</h3>
                {game.tags && game.tags.length > 0 && (
                  <div className="game-tags">
                    {game.tags.slice(0, 7).map((tag, index) => (
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
                        {game.discountPercent && game.discountPercent > 0 ? (
                          <div className="sale-price-container">
                            <div className="discount-badge">-{game.discountPercent}%</div>
                            <div className="price-details">
                              <span className="original-price">
                                ${Number(game.originalPrice).toFixed(2)}
                              </span>
                              <span className="sale-price">
                                ${Number(game.currentPrice).toFixed(2)}
                              </span>
                            </div>
                          </div>
                        ) : (
                          <span className="price">${Number(game.currentPrice).toFixed(2)}</span>
                        )}
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
                <div className="item-actions">
                  <PriceAlertButton
                    appId={game.appId}
                    gameName={game.name}
                    currentPrice={game.currentPrice}
                    originalPrice={game.originalPrice}
                    hasAlert={priceAlerts.some(alert => alert.appId === game.appId)}
                    onAlertChange={loadPriceAlerts}
                  />
                  <button
                    className="buy-now-button"
                    onClick={() => {
                      if (game.appId) {
                        window.open(`https://store.steampowered.com/app/${game.appId}/`, '_blank');
                      }
                    }}
                  >
                    🛒 Buy Now
                  </button>
                  <button
                    className="similar-games-button"
                    onClick={() => toggleSimilarGames(game.appId)}
                  >
                    {expandedGameId === game.appId ? 'Hide' : 'Show'} Similar Games
                  </button>
                </div>
              </div>
            </div>

            {expandedGameId === game.appId && (
              <div className="similar-games-container">
                {similarGamesMap.get(game.appId)?.length ? (
                  <>
                    <h4 className="similar-games-title">Similar Games in Your Library:</h4>
                    <div className="similar-games-list">
                      {similarGamesMap.get(game.appId)?.map((similarItem) => (
                        <div key={similarItem.game.appId} className="similar-game-item">
                          {similarItem.game.appId && (
                            <img
                              src={`https://cdn.akamai.steamstatic.com/steam/apps/${similarItem.game.appId}/capsule_231x87.jpg`}
                              alt={similarItem.game.name}
                              className="similar-game-img"
                              onError={(e) => {
                                e.currentTarget.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="231" height="87"%3E%3Crect fill="%231b2838" width="231" height="87"/%3E%3Ctext fill="%23ffffff" font-family="Arial" font-size="12" x="50%25" y="50%25" text-anchor="middle" dominant-baseline="middle"%3ENo Image%3C/text%3E%3C/svg%3E';
                              }}
                            />
                          )}
                          <div className="similar-game-info">
                            <div className="similar-game-name">
                              {similarItem.game.name}
                              <span className="similarity-score"> ({similarItem.score} tags in common)</span>
                            </div>
                            {similarItem.game.playtimeHours !== undefined && (
                              <div className="similar-game-playtime">
                                {similarItem.game.playtimeHours.toFixed(1)} hours played
                              </div>
                            )}
                            <div className="similar-game-common-tags">
                              <strong>Common tags:</strong> {similarItem.commonTags.join(', ')}
                            </div>
                            {similarItem.game.tags && similarItem.game.tags.length > 0 && (
                              <div className="similar-game-tags">
                                {similarItem.game.tags.slice(0, 7).map((tag, idx) => (
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
                    No similar games found in your library (need at least 4 common tags).
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
