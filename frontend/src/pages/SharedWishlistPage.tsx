import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

export const SharedWishlistPage: React.FC = () => {
  const { uuid } = useParams<{ uuid: string }>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<any>(null);

  useEffect(() => {
    if (!uuid) return;
    setLoading(true);
    setError(null);
    fetch(`/api/share/${uuid}`)
      .then(async (res) => {
        if (!res.ok) {
          const body = await res.json().catch(() => ({}));
          throw new Error(body?.error || 'Share link not found');
        }
        return res.json();
      })
      .then((d) => setData(d))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [uuid]);

  if (loading) return <div>Loading shared wishlist...</div>;
  if (error) return <div className="error-message">{error}</div>;
  if (!data) return <div>No shared data available.</div>;

  return (
    <div className="shared-wishlist-page">
      <h2>Shared Wishlist</h2>
      <p>Shared on: {new Date(data.createdAt).toLocaleString()}</p>
      <div className="wishlist-items">
        {Array.isArray(data.wishlist) && data.wishlist.length > 0 ? (
          data.wishlist.map((game: any) => (
            <div key={game.appId} className="shared-game-card">
              <img
                src={`https://cdn.akamai.steamstatic.com/steam/apps/${game.appId}/header.jpg`}
                alt={game.name}
                className="shared-game-image"
                onError={(e) => {
                  e.currentTarget.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="292" height="136"%3E%3Crect fill="%231b2838" width="292" height="136"/%3E%3Ctext fill="%23ffffff" font-family="Arial" font-size="14" x="50%25" y="50%25" text-anchor="middle" dominant-baseline="middle"%3ENo Image%3C/text%3E%3C/svg%3E';
                }}
              />
              <h3>{game.name}</h3>
              <p>Price: {game.currentPrice ? `$${game.currentPrice}` : 'Unavailable'}</p>
              {game.tags && (
                <div className="tags">
                  {game.tags.slice(0, 6).map((t: string) => (
                    <span key={t} className="tag">{t}</span>
                  ))}
                </div>
              )}
            </div>
          ))
        ) : (
          <div>No games in shared wishlist.</div>
        )}
      </div>
    </div>
  );
};

export default SharedWishlistPage;
