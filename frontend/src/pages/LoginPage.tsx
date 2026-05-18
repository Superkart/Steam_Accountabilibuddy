import React from 'react';
import { SteamLoginButton } from '../components/SteamLoginButton';
import '../styles/LoginPage.css';

export const LoginPage: React.FC = () => {
  return (
    <div className="login-page">
      <div className="login-container">
        <div className="login-content">
          <h1 className="app-title">SteamLens</h1>
          <p className="app-description">
            Track your unplayed Steam games and get personalized recommendations from your wishlist
          </p>

          <div className="login-section">
            <SteamLoginButton />
          </div>

          <div className="features">
            <div className="feature">
              <div className="feature-icon">📚</div>
              <h3>Track Your Library</h3>
              <p>See games with 2 hours or less playtime</p>
            </div>
            <div className="feature">
              <div className="feature-icon">🎯</div>
              <h3>Smart Recommendations</h3>
              <p>Get suggestions based on your wishlist tags</p>
            </div>
            <div className="feature">
              <div className="feature-icon">💰</div>
              <h3>Price Alerts</h3>
              <p>Get notified when wishlist games go on sale</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
