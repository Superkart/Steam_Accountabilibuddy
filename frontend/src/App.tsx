import { useEffect } from 'react';
import { LoginPage } from './pages/LoginPage';
import { useAuth } from './context/AuthContext';
import './App.css';

function App() {
  const { user, loading, checkAuth } = useAuth();

  // Check for authentication after Steam redirect
  useEffect(() => {
    // If we're coming back from Steam, check auth status
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('openid.mode')) {
      // We're returning from Steam login, wait a moment then check auth
      setTimeout(() => {
        checkAuth();
      }, 1000);
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
      </header>
      <main>
        <p>Successfully logged in! More features coming soon...</p>
      </main>
    </div>
  );
}

export default App;
