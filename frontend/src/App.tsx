import { useEffect, useState } from 'react';
import { LoginPage } from './pages/LoginPage';
import { useAuth } from './context/AuthContext';
import { WishlistDisplay } from './components/WishlistDisplay';
import { EmailSettings } from './components/EmailSettings';
import './App.css';

type Tab = 'wishlist' | 'settings';

function App() {
  const { user, loading, checkAuth, logout } = useAuth();
  const [activeTab, setActiveTab] = useState<Tab>('wishlist');
  const [showFullId, setShowFullId] = useState(false);
  const [copied, setCopied] = useState(false);

  // Check for authentication after Steam redirect
  useEffect(() => {
    // If we're coming back from Steam, check auth status
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('auth') === 'success') {
      // We're returning from Steam login, check auth immediately
      checkAuth();
      // Clean up URL parameters
      window.history.replaceState({}, document.title, window.location.pathname);
    }
  }, [checkAuth]);

  if (loading) {
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        fontSize: '24px',
      }}>
        Loading...
      </div>
    );
  }

  if (!user) {
    return <LoginPage />;
  }


  const maskSteamId = (id?: string) => {
    if (!id) return '';
    // show first 6 and last 4, mask the rest
    if (id.length <= 10) return id;
    return `${id.slice(0, 6)}••••${id.slice(-4)}`;
  };

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (e) {
      console.error('Clipboard write failed', e);
    }
  };

  return (
    <div className="App">
      <header className="app-header">
        <div className="header-info">
          <h1>Welcome, {user.username}!</h1>
          {user.profilePictureUrl && (
            <img
              src={user.profilePictureUrl}
              alt="Profile"
              className="profile-picture"
            />
          )}
          <div className="steam-id-container">
            <span className="steam-id-label">Steam ID:</span>
            <span className="steam-id">{showFullId ? user.steamId : maskSteamId(user.steamId)}</span>
            <div className="steam-id-controls">
              <button
                className="small-button"
                title={showFullId ? 'Hide Steam ID' : 'Show Steam ID'}
                onClick={() => setShowFullId(s => !s)}
              >
                {showFullId ? 'Hide' : 'Show'}
              </button>
              <button
                className="small-button"
                title="Copy Steam ID"
                onClick={() => user.steamId && copyToClipboard(user.steamId)}
              >
                {copied ? 'Copied' : 'Copy'}
              </button>
            </div>
          </div>
          {user.email && <p className="user-email">Email: {user.email}</p>}
        </div>
        <button onClick={logout} className="logout-button">
          Logout
        </button>
      </header>

      <nav className="tab-navigation">
        <button
          className={`tab-button ${activeTab === 'wishlist' ? 'active' : ''}`}
          onClick={() => setActiveTab('wishlist')}
        >
          Wishlist
        </button>
        <button
          className={`tab-button ${activeTab === 'settings' ? 'active' : ''}`}
          onClick={() => setActiveTab('settings')}
        >
          Settings
        </button>
      </nav>

      <main>
        {activeTab === 'wishlist' ? <WishlistDisplay /> : <EmailSettings />}
      </main>
    </div>
  );
}

export default App;
