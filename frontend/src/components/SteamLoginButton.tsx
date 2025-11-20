import React from 'react';
import { useAuth } from '../context/AuthContext';
import '../styles/SteamLoginButton.css';

export const SteamLoginButton: React.FC = () => {
  const { login } = useAuth();

  return (
    <button className="steam-login-button" onClick={login} aria-label="Sign in through Steam">
      <img
        src="https://community.cloudflare.steamstatic.com/public/images/signinthroughsteam/sits_01.png"
        alt="Sign in through Steam"
        className="steam-button-image"
      />
    </button>
  );
};
