import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../services/api';
import type { PriceAlert } from '../types/auth';
import './EmailSettings.css';

export const EmailSettings = () => {
  const { user, updateEmail } = useAuth();
  const [email, setEmail] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState('');
  const [priceAlerts, setPriceAlerts] = useState<PriceAlert[]>([]);
  const [loadingAlerts, setLoadingAlerts] = useState(false);

  useEffect(() => {
    loadPriceAlerts();
  }, [user]);

  const loadPriceAlerts = async () => {
    try {
      setLoadingAlerts(true);
      const alerts = await authApi.getPriceAlerts();
      setPriceAlerts(alerts || []);
    } catch (error) {
      console.error('Failed to load price alerts:', error);
      setPriceAlerts([]);
    } finally {
      setLoadingAlerts(false);
    }
  };

  const handleSaveEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) {
      setSaveMessage('Please enter an email address');
      return;
    }

    // Validate email format
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      setSaveMessage('Please enter a valid email address');
      return;
    }

    try {
      setIsSaving(true);
      setSaveMessage('');
      await updateEmail(email);
      setSaveMessage('Email saved successfully!');
      setTimeout(() => setSaveMessage(''), 3000);
    } catch (error) {
      setSaveMessage(error instanceof Error ? error.message : 'Failed to save email');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDeleteAlert = async (appId: number) => {
    if (!confirm('Are you sure you want to delete this price alert?')) {
      return;
    }

    try {
      await authApi.deletePriceAlert(appId);
      setPriceAlerts(priceAlerts.filter(alert => alert.appId !== appId));
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to delete price alert');
    }
  };

  return (
    <div className="email-settings-container">
      <div className="settings-section">
        <h3>Email Settings</h3>
        <p className="settings-description">
          Set your email address to receive price alerts when wishlist games go on sale.
          We'll email you once per day when games drop below your target price.
        </p>

        <form onSubmit={handleSaveEmail} className="email-form">
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Enter your email address"
            className="email-input"
            disabled={isSaving}
          />
          <button
            type="submit"
            className="save-button"
            disabled={isSaving}
          >
            {isSaving ? 'Saving...' : 'Save Email'}
          </button>
        </form>

        {saveMessage && (
          <div className={`save-message ${saveMessage.includes('success') ? 'success' : 'error'}`}>
            {saveMessage}
          </div>
        )}
      </div>

      <div className="settings-section">
        <h3>Active Price Alerts ({priceAlerts?.length || 0})</h3>
        <p className="settings-description">
          {user?.email
            ? 'You will receive daily email notifications when these games drop below your target price.'
            : 'Set your email address above to activate price alerts.'
          }
        </p>

        {loadingAlerts ? (
          <div className="loading">Loading price alerts...</div>
        ) : !priceAlerts || priceAlerts.length === 0 ? (
          <div className="no-alerts">
            No price alerts set. Click the bell icon on wishlist games to set alerts.
          </div>
        ) : (
          <div className="alerts-list">
            {priceAlerts.map((alert) => (
              <div key={alert.appId} className="alert-item">
                <div className="alert-info">
                  <div className="alert-game-name">{alert.gameName}</div>
                  <div className="alert-prices">
                    <span className="alert-target">
                      Target: ${parseFloat(alert.targetPrice).toFixed(2)}
                    </span>
                    {alert.currentPrice && (
                      <span className="alert-current">
                        Current: ${parseFloat(alert.currentPrice).toFixed(2)}
                      </span>
                    )}
                  </div>
                  {alert.lastChecked && (
                    <div className="alert-meta">
                      Last checked: {new Date(alert.lastChecked).toLocaleString()}
                    </div>
                  )}
                </div>
                <button
                  onClick={() => handleDeleteAlert(alert.appId)}
                  className="delete-alert-button"
                  title="Delete price alert"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
