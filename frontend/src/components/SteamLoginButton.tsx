import React from 'react';
import { useAuth } from '../context/AuthContext';
import '../styles/SteamLoginButton.css';

export const SteamLoginButton: React.FC = () => {
  const { login } = useAuth();

  return (
    <button className="steam-login-button" onClick={login}>
      <svg
        className="steam-icon"
        viewBox="0 0 24 24"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path d="M12 2a10 10 0 0 1 10 10 10 10 0 0 1-10 10c-4.6 0-8.45-3.08-9.64-7.27l3.83 1.58a2.84 2.84 0 0 0 2.78 2.27c1.56 0 2.83-1.27 2.83-2.83v-.13l3.4-2.43h.08c2.08 0 3.77-1.69 3.77-3.77s-1.69-3.77-3.77-3.77-3.78 1.69-3.78 3.77v.05l-2.37 3.46-.16-.01c-.59 0-1.14.18-1.59.49L2 11.2C2.43 6.05 6.73 2 12 2M8.28 17.17c.8.33 1.72-.04 2.05-.84.33-.8-.05-1.71-.83-2.04l-1.28-.53c.49-.18 1.04-.19 1.56.03.53.21.94.62 1.15 1.15.22.52.22 1.1 0 1.62-.43 1.08-1.7 1.6-2.78 1.15l1.13.46M18.42 9.42c0 1.38-1.12 2.5-2.5 2.5s-2.5-1.12-2.5-2.5 1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5m-3.97 0c0 .82.66 1.47 1.47 1.47s1.47-.66 1.47-1.47-.66-1.47-1.47-1.47-1.47.66-1.47 1.47z" />
      </svg>
      Sign in through Steam
    </button>
  );
};
