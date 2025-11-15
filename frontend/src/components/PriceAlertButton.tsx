import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../services/api';
import './PriceAlertButton.css';

interface PriceAlertButtonProps {
  appId: number;
  gameName: string;
  currentPrice: number | null;
  hasAlert: boolean;
  onAlertChange: () => void;
}

export const PriceAlertButton = ({ appId, gameName, currentPrice, hasAlert, onAlertChange }: PriceAlertButtonProps) => {
  const { user } = useAuth();
  const [showModal, setShowModal] = useState(false);
  const [targetPrice, setTargetPrice] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState('');

  const handleOpenModal = () => {
    if (!user?.email) {
      alert('Please set your email address in settings before creating price alerts.');
      return;
    }

    if (currentPrice !== null && currentPrice !== undefined) {
      // Default to 10% off current price
      setTargetPrice((currentPrice * 0.9).toFixed(2));
    }
    setShowModal(true);
    setError('');
  };

  const handleCreateAlert = async (e: React.FormEvent) => {
    e.preventDefault();

    const price = parseFloat(targetPrice);
    if (isNaN(price) || price <= 0) {
      setError('Please enter a valid price');
      return;
    }

    if (currentPrice !== null && price >= currentPrice) {
      setError('Target price should be lower than current price');
      return;
    }

    try {
      setIsProcessing(true);
      setError('');
      await authApi.createPriceAlert(appId, gameName, price);
      setShowModal(false);
      onAlertChange();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create price alert');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleDeleteAlert = async () => {
    if (!confirm('Remove price alert for this game?')) {
      return;
    }

    try {
      setIsProcessing(true);
      await authApi.deletePriceAlert(appId);
      onAlertChange();
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to delete price alert');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <>
      <button
        className={`price-alert-btn ${hasAlert ? 'active' : ''}`}
        onClick={hasAlert ? handleDeleteAlert : handleOpenModal}
        disabled={isProcessing}
        title={hasAlert ? 'Remove price alert' : 'Set price alert'}
      >
        {isProcessing ? '...' : hasAlert ? '🔔' : '🔕'}
      </button>

      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h3>Set Price Alert</h3>
            <p className="modal-game-name">{gameName}</p>

            {currentPrice !== null && currentPrice !== undefined && (
              <p className="current-price-display">
                Current price: ${currentPrice.toFixed(2)}
              </p>
            )}

            <form onSubmit={handleCreateAlert}>
              <div className="form-group">
                <label htmlFor="targetPrice">Alert me when price drops to:</label>
                <div className="price-input-group">
                  <span className="currency-symbol">$</span>
                  <input
                    id="targetPrice"
                    type="number"
                    step="0.01"
                    min="0.01"
                    value={targetPrice}
                    onChange={(e) => setTargetPrice(e.target.value)}
                    placeholder="0.00"
                    autoFocus
                    required
                  />
                </div>
              </div>

              {error && <div className="modal-error">{error}</div>}

              <div className="modal-actions">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="modal-btn modal-btn-cancel"
                  disabled={isProcessing}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="modal-btn modal-btn-confirm"
                  disabled={isProcessing}
                >
                  {isProcessing ? 'Creating...' : 'Create Alert'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
};
