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
          <p>Steam ID: {user.steamId}</p>
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
