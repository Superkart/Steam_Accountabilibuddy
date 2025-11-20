import { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../services/api';
import type { PriceAlert } from '../types/auth';
import './EmailSettings.css';

export const EmailSettings = () => {
  const { user, updateEmail, deleteEmail } = useAuth();
  const [email, setEmail] = useState('');
  const [originalEmail, setOriginalEmail] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState('');
  const [priceAlerts, setPriceAlerts] = useState<PriceAlert[]>([]);
  const [loadingAlerts, setLoadingAlerts] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isCheckingPrices, setIsCheckingPrices] = useState(false);
  const [checkMessage, setCheckMessage] = useState('');

  useEffect(() => {
    loadPriceAlerts();
  }, [user]);

  // Hydrate local email input whenever the authenticated user's email changes.
  // This ensures returning users see their saved address in the input and
  // lets us detect whether the field was actually modified (to avoid
  // unnecessary saves/duplicate entries).
  useEffect(() => {
    const newEmail = user?.email ?? '';
    setEmail(newEmail);
    setOriginalEmail(newEmail);
  }, [user?.email]);

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
      // Update originalEmail so the Save button stays disabled until the
      // user changes the field again.
      setOriginalEmail(email.trim());
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

  const handleDeleteEmail = async () => {
    if (!confirm('Are you sure you want to remove your email? This will also delete all your price alerts since they require email for notifications.')) {
      return;
    }

    try {
      setIsDeleting(true);
      setSaveMessage('');
      await deleteEmail();
      setEmail('');
      setOriginalEmail('');
      setPriceAlerts([]); // Clear price alerts since backend deletes them too
      setSaveMessage('Email and price alerts removed successfully!');
      setTimeout(() => setSaveMessage(''), 3000);
    } catch (error) {
      setSaveMessage(error instanceof Error ? error.message : 'Failed to remove email');
    } finally {
      setIsDeleting(false);
    }
  };

  const handleDeleteAllAlerts = async () => {
    if (!priceAlerts || priceAlerts.length === 0) {
      return;
    }

    if (!confirm(`Are you sure you want to delete all ${priceAlerts.length} price alerts? This action cannot be undone.`)) {
      return;
    }

    try {
      setIsDeleting(true);
      await authApi.deleteAllPriceAlerts();
      setPriceAlerts([]);
      alert('All price alerts deleted successfully!');
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to delete all price alerts');
    } finally {
      setIsDeleting(false);
    }
  };

  const handleTestEmailSystem = async () => {
    if (!user?.email) {
      setCheckMessage('Please set your email address first');
      setTimeout(() => setCheckMessage(''), 3000);
      return;
    }

    if (!priceAlerts || priceAlerts.length === 0) {
      setCheckMessage('Create at least one price alert to test the email system');
      setTimeout(() => setCheckMessage(''), 3000);
      return;
    }

    try {
      setIsCheckingPrices(true);
      setCheckMessage('');
      const result = await authApi.triggerPriceCheck();
      setCheckMessage(result.message || 'Price check completed! Check your email if any alerts were triggered.');
      // Reload alerts to see updated prices
      await loadPriceAlerts();
      setTimeout(() => setCheckMessage(''), 5000);
    } catch (error) {
      setCheckMessage(error instanceof Error ? error.message : 'Failed to trigger price check');
      setTimeout(() => setCheckMessage(''), 3000);
    } finally {
      setIsCheckingPrices(false);
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
            // Disable save while saving, when input is empty, or when value
            // hasn't changed from the hydrated original value to avoid
            // accidental duplicate saves.
            disabled={isSaving || email.trim() === '' || email.trim() === (originalEmail ?? '').trim()}
          >
            {isSaving ? 'Saving...' : 'Save Email'}
          </button>
        </form>

        {saveMessage && (
          <div className={`save-message ${saveMessage.includes('success') ? 'success' : 'error'}`}>
            {saveMessage}
          </div>
        )}

        {user?.email && (
          <div className="email-actions">
            <button
              onClick={handleDeleteEmail}
              className="delete-email-button"
              disabled={isDeleting}
            >
              {isDeleting ? 'Removing...' : 'Remove Email'}
            </button>
          </div>
        )}
      </div>

      <div className="settings-section">
        <div className="alerts-header">
          <h3>Active Price Alerts ({priceAlerts?.length || 0})</h3>
          <div className="alerts-actions">
            {priceAlerts && priceAlerts.length > 0 && (
              <>
                <button
                  onClick={handleTestEmailSystem}
                  className="test-email-button"
                  disabled={isCheckingPrices || !user?.email}
                  title="Test email system by checking current prices and sending notifications if any alerts are triggered"
                >
                  {isCheckingPrices ? 'Checking...' : '📧 Test Email System'}
                </button>
                <button
                  onClick={loadPriceAlerts}
                  className="refresh-button"
                  disabled={loadingAlerts}
                  title="Refresh last-checked timestamps"
                >
                  {loadingAlerts ? 'Refreshing...' : 'Refresh'}
                </button>
                <button
                  onClick={handleDeleteAllAlerts}
                  className="delete-all-button"
                  disabled={isDeleting}
                >
                  {isDeleting ? 'Deleting...' : 'Delete All Alerts'}
                </button>
              </>
            )}
          </div>
        </div>
        <p className="settings-description">
          {user?.email
            ? 'You will receive daily email notifications when these games drop below your target price.'
            : 'Set your email address above to activate price alerts.'
          }
        </p>

        {checkMessage && (
          <div className={`check-message ${checkMessage.includes('completed') || checkMessage.includes('Check your email') ? 'success' : 'info'}`}>
            {checkMessage}
          </div>
        )}

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
