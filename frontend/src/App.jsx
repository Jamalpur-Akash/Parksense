import { useState, useEffect, useRef, useCallback } from 'react';
import './App.css';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

function formatDistance(meters) {
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`;
}

function getReporterId() {
  let id = localStorage.getItem('parksense_reporter_id');
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem('parksense_reporter_id', id);
  }
  return id;
}

function App() {
  const [mode, setMode] = useState('live'); // 'live' | 'nearby' | 'admin'
  const [liveCoords, setLiveCoords] = useState(null);
  const [status, setStatus] = useState(null);
  const [displayCoords, setDisplayCoords] = useState(null);

  const [nearbyParking, setNearbyParking] = useState([]);
  const [nearbyLoading, setNearbyLoading] = useState(false);
  const [nearbyError, setNearbyError] = useState(null);

  const [showPhotoForm, setShowPhotoForm] = useState(false);
  const [photoZoneName, setPhotoZoneName] = useState('');
  const [photoFile, setPhotoFile] = useState(null);
  const [photoSubmitting, setPhotoSubmitting] = useState(false);
  const [photoResult, setPhotoResult] = useState(null);

  const [adminKey, setAdminKey] = useState(null);
  const [pendingZones, setPendingZones] = useState([]);
  const [pendingLoading, setPendingLoading] = useState(false);
  const [pendingError, setPendingError] = useState(null);

  const watchIdRef = useRef(null);

  const checkLocation = useCallback(async (lat, lng) => {
    try {
      const res = await fetch(`${API_URL}/check-location`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ lat, lng }),
      });
      const data = await res.json();
      setStatus(data);
    } catch (err) {
      setStatus({ error: 'Could not reach backend. Is Spring Boot running?' });
    }
  }, []);

  const fetchNearbyParking = useCallback(async (lat, lng) => {
    setNearbyLoading(true);
    setNearbyError(null);
    setNearbyParking([]);
    try {
      const res = await fetch(`${API_URL}/parking/nearby?lat=${lat}&lng=${lng}`);
      if (!res.ok) throw new Error('Backend error');
      const data = await res.json();
      setNearbyParking(data);
      if (data.length === 0) {
        setNearbyError('No mapped parking found nearby — map coverage varies by area.');
      }
    } catch (err) {
      setNearbyError('Could not fetch nearby parking. Backend running? Internet connected?');
    }
    setNearbyLoading(false);
  }, []);

  const fetchPendingZones = useCallback(async (key) => {
    setPendingLoading(true);
    setPendingError(null);
    try {
      const res = await fetch(`${API_URL}/zones/pending?adminKey=${encodeURIComponent(key)}`);
      if (res.status === 403) {
        setPendingError('Invalid admin key.');
        setPendingZones([]);
      } else {
        const data = await res.json();
        setPendingZones(data);
      }
    } catch (err) {
      setPendingError('Could not reach backend. Is Spring Boot running?');
    }
    setPendingLoading(false);
  }, []);

  // Live GPS mode: continuous watch, immediate no-parking check on every fix
  useEffect(() => {
    if (mode !== 'live') {
      if (watchIdRef.current !== null) {
        navigator.geolocation.clearWatch(watchIdRef.current);
        watchIdRef.current = null;
      }
      return;
    }

    if (!navigator.geolocation) {
      setStatus({ error: 'Geolocation not supported by this browser.' });
      return;
    }

    watchIdRef.current = navigator.geolocation.watchPosition(
      (position) => {
        const { latitude, longitude } = position.coords;
        setLiveCoords({ lat: latitude, lng: longitude });
        setDisplayCoords({ lat: latitude, lng: longitude });
        checkLocation(latitude, longitude);
      },
      () => setStatus({ error: 'Location permission denied or unavailable.' }),
      { enableHighAccuracy: true, maximumAge: 0, timeout: 10000 }
    );

    return () => {
      if (watchIdRef.current !== null) {
        navigator.geolocation.clearWatch(watchIdRef.current);
        watchIdRef.current = null;
      }
    };
  }, [mode, checkLocation]);

  // Find Parking Nearby mode: one-time location fix, then fetch real nearby parking
  useEffect(() => {
    if (mode !== 'nearby') return;

    if (liveCoords) {
      fetchNearbyParking(liveCoords.lat, liveCoords.lng);
      return;
    }

    if (!navigator.geolocation) {
      setNearbyError('Geolocation not supported by this browser.');
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude } = position.coords;
        setDisplayCoords({ lat: latitude, lng: longitude });
        fetchNearbyParking(latitude, longitude);
      },
      () => setNearbyError('Location permission denied or unavailable.'),
      { enableHighAccuracy: true, maximumAge: 0, timeout: 10000 }
    );
  }, [mode, liveCoords, fetchNearbyParking]);

  // Admin mode: ask for the key once, then load pending zones
  useEffect(() => {
    if (mode !== 'admin') return;

    if (adminKey) {
      fetchPendingZones(adminKey);
      return;
    }

    const key = window.prompt('Enter admin key:');
    if (!key) {
      setMode('live');
      return;
    }
    setAdminKey(key);
  }, [mode, adminKey, fetchPendingZones]);

  const handleOpenPhotoForm = () => {
    if (!liveCoords) {
      alert('Live GPS location needed to report a zone. Switch to Live GPS mode first.');
      return;
    }
    setPhotoZoneName('');
    setPhotoFile(null);
    setPhotoResult(null);
    setShowPhotoForm(true);
  };

  const handlePhotoSubmit = async () => {
    if (!photoZoneName || !photoFile || !liveCoords) return;

    setPhotoSubmitting(true);
    setPhotoResult(null);

    try {
      const formData = new FormData();
      formData.append('photo', photoFile);
      formData.append('name', photoZoneName);
      formData.append('lat', liveCoords.lat);
      formData.append('lng', liveCoords.lng);
      formData.append('radiusMeters', 15);
      formData.append('reporterId', getReporterId());

      const res = await fetch(`${API_URL}/zones/report-with-photo`, {
        method: 'POST',
        body: formData,
      });
      const data = await res.json();
      setPhotoResult(data);
    } catch (err) {
      setPhotoResult({ status: 'error', message: 'Could not reach backend. Is Spring Boot running?' });
    }

    setPhotoSubmitting(false);
  };

  const handleAdminAction = async (id, action) => {
    try {
      await fetch(`${API_URL}/zones/${id}/${action}?adminKey=${encodeURIComponent(adminKey)}`, {
        method: 'POST',
      });
      fetchPendingZones(adminKey);
    } catch (err) {
      alert('Could not reach backend.');
    }
  };

  return (
    <div className="dashboard">
      <h1>ParkSense Dashboard</h1>

      <div className="mode-toggle">
        <button
          className={mode === 'live' ? 'mode-btn active' : 'mode-btn'}
          onClick={() => { setMode('live'); setStatus(null); }}
        >
          Live GPS
        </button>
        <button
          className={mode === 'nearby' ? 'mode-btn active' : 'mode-btn'}
          onClick={() => setMode('nearby')}
        >
          Find Parking Nearby
        </button>
        <button
          className={mode === 'admin' ? 'mode-btn active' : 'mode-btn'}
          onClick={() => setMode('admin')}
        >
          Admin
        </button>
      </div>

      {mode === 'live' && (
        <>
          {displayCoords && (
            <p className="coords-line">
              Current location: {displayCoords.lat.toFixed(6)}, {displayCoords.lng.toFixed(6)}
            </p>
          )}

          {status && !status.error && (
            <div className={`alert ${status.inZone ? 'warning' : 'safe'}`}>
              {status.inZone
                ? (status.verified
                    ? `⚠️ No Parking Zone: ${status.zoneName}`
                    : `🕓 Unverified report nearby: ${status.zoneName} (pending review)`)
                : '✅ Proceed'}
            </div>
          )}

          {status?.error && <div className="alert warning">{status.error}</div>}

          <button className="report-btn" onClick={handleOpenPhotoForm}>
            📷 Report this location as No Parking (AI Verified)
          </button>

          {showPhotoForm && (
            <div style={{
              marginTop: '15px',
              padding: '15px',
              border: '1px solid #444',
              borderRadius: '10px',
              background: '#1e1e1e',
              textAlign: 'left'
            }}>
              <div style={{ marginBottom: '10px' }}>
                <label style={{ display: 'block', marginBottom: '5px', color: '#ccc' }}>
                  Zone name
                </label>
                <input
                  type="text"
                  value={photoZoneName}
                  onChange={(e) => setPhotoZoneName(e.target.value)}
                  placeholder='e.g. "Outside XYZ Store"'
                  style={{ width: '100%', padding: '8px', borderRadius: '6px', border: '1px solid #555' }}
                />
              </div>

              <div style={{ marginBottom: '10px' }}>
                <label style={{ display: 'block', marginBottom: '5px', color: '#ccc' }}>
                  Photo of no-parking sign
                </label>
                <input
                  type="file"
                  accept="image/*"
                  capture="environment"
                  onChange={(e) => setPhotoFile(e.target.files[0])}
                />
              </div>

              <button
                className="report-btn"
                disabled={photoSubmitting || !photoZoneName || !photoFile}
                onClick={handlePhotoSubmit}
              >
                {photoSubmitting ? 'Analyzing photo…' : 'Submit for AI Verification'}
              </button>

              <button
                className="report-btn"
                style={{ marginLeft: '10px', background: '#555' }}
                onClick={() => setShowPhotoForm(false)}
              >
                Cancel
              </button>

              {photoResult && (
                <div style={{ marginTop: '10px', color: '#eee' }}>
                  {photoResult.status === 'error' && (
                    <p style={{ color: '#f87171' }}>❌ {photoResult.message}</p>
                  )}

                  {photoResult.status === 'pending' && (
                    <>
                      <p style={{ color: '#facc15' }}>
                        🕓 Sign detected ({photoResult.detections} object(s) found). Zone added as "pending review".
                      </p>
                      {!photoResult.reporterCounted && (
                        <p style={{ color: '#9ca3af', fontSize: '13px' }}>
                          You've already reported this exact location before — this submission wasn't
                          counted again toward verification.
                        </p>
                      )}
                      {photoResult.reporterCounted && (
                        <p style={{ color: '#9ca3af', fontSize: '13px' }}>
                          {photoResult.totalReporters} independent report(s) so far for this location.
                        </p>
                      )}
                    </>
                  )}

                  {photoResult.status === 'approved' && (
                    <p style={{ color: '#4ade80' }}>
                      {photoResult.autoApproved
                        ? `✅ Auto-approved — ${photoResult.totalReporters} independent reports confirmed this location.`
                        : '✅ This location is already a verified no-parking zone.'}
                    </p>
                  )}

                  {photoResult.status === 'rejected' && (
                    <p style={{ color: '#f87171' }}>
                      ❌ No sign detected in photo. Report rejected.
                    </p>
                  )}
                </div>
              )}
            </div>
          )}
        </>
      )}

      {mode === 'nearby' && (
        <div className="parking-list-section">
          {displayCoords && (
            <p className="coords-line">
              Searching near: {displayCoords.lat.toFixed(6)}, {displayCoords.lng.toFixed(6)}
            </p>
          )}

          {nearbyLoading && <div className="status-card">Searching for parking nearby…</div>}

          {nearbyError && <div className="alert warning">{nearbyError}</div>}

          {nearbyParking.length > 0 && (
            <ul className="parking-list">
              {nearbyParking.map((spot, idx) => (
                <li key={idx} className="parking-item">
                  <div>
                    <strong>{spot.name}</strong>
                    <div className="parking-distance">{formatDistance(spot.distanceMeters)} away</div>
                  </div>
                  <a  href={`https://www.google.com/maps?q=${spot.lat},${spot.lng}`}
                    target="_blank"
                    rel="noreferrer"
                    className="map-link"
                  >
                    Open in Maps
                  </a>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {mode === 'admin' && (
        <div className="admin-section">
          {pendingLoading && <div className="status-card">Loading pending zones…</div>}
          {pendingError && <div className="alert warning">{pendingError}</div>}

          {!pendingLoading && !pendingError && pendingZones.length === 0 && (
            <div className="status-card">No zones awaiting review.</div>
          )}

          {pendingZones.map((zone) => (
            <div key={zone.id} className="admin-zone-card">
              {zone.photoUrl && (
                <img
                  src={`${API_URL}${zone.photoUrl}`}
                  alt={zone.name}
                  className="admin-zone-img"
                />
              )}
              <div className="admin-zone-info">
                <strong>{zone.name}</strong>
                <p className="coords-line">
                  {zone.reportCount} independent report(s) · confidence {zone.confidence}
                </p>
              </div>
              <div className="admin-actions">
                <button className="approve-btn" onClick={() => handleAdminAction(zone.id, 'approve')}>
                  ✅ Approve
                </button>
                <button className="reject-btn" onClick={() => handleAdminAction(zone.id, 'reject')}>
                  ❌ Reject
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default App;