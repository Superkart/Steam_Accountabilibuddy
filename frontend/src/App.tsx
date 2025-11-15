import { useEffect } from 'react';
import { LoginPage } from './pages/LoginPage';
import { useAuth } from './context/AuthContext';
import { WishlistDisplay } from './components/WishlistDisplay';
import './App.css';

function App() {
  const { user, loading, checkAuth, logout } = useAuth();

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
        <h1>Welcome, {user.username}!</h1>
        {user.profilePictureUrl && (
          <img
            src={user.profilePictureUrl}
            alt="Profile"
            style={{ width: 64, height: 64, borderRadius: '50%' }}
          />
        )}
        <p>Steam ID: {user.steamId}</p>
        <button
          onClick={logout}
          style={{
            marginTop: '20px',
            padding: '10px 20px',
            fontSize: '16px',
            backgroundColor: '#dc3545',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer',
          }}
        >
          Logout
        </button>
      </header>
      <main>
        <WishlistDisplay />
      </main>
    </div>
  );
}

export default App;
