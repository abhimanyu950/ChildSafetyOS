// Firebase Configuration (from google-services.json)
const firebaseConfig = {
    apiKey: "AIzaSyCQ-Kd5_MjSLUK7aE43y_78aS70wXJX3gQ",
    authDomain: "childsafetyos.firebaseapp.com",
    projectId: "childsafetyos",
    storageBucket: "childsafetyos.firebasestorage.app",
    messagingSenderId: "1096942346174",
    appId: "1:1096942346174:android:2f02d917e9926de690e3ef"
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();

// Global state
let currentDeviceId = null;
let eventsListener = null;
let deviceListener = null;
let statsListener = null;
let alertsListener = null;

// DOM Elements
const deviceIdInput = document.getElementById('deviceId');
const connectBtn = document.getElementById('connectBtn');
const connectionStatus = document.getElementById('connectionStatus');
const eventsList = document.getElementById('eventsList');
const severityFilter = document.getElementById('severityFilter');
const typeFilter = document.getElementById('typeFilter');

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    // Load saved device ID
    const savedDeviceId = localStorage.getItem('lastDeviceId');
    if (savedDeviceId) {
        deviceIdInput.value = savedDeviceId;
    }

    connectBtn.addEventListener('click', connectToDevice);
    severityFilter.addEventListener('change', filterEvents);
    typeFilter.addEventListener('change', filterEvents);

    // Auto-connect if device ID exists
    if (savedDeviceId) {
        connectToDevice();
    }
});

// Connect to Device
function connectToDevice() {
    const deviceId = deviceIdInput.value.trim();

    if (!deviceId) {
        alert('Please enter a device ID');
        return;
    }

    // Disconnect from previous device
    if (currentDeviceId) {
        disconnectDevice();
    }

    currentDeviceId = deviceId;
    localStorage.setItem('lastDeviceId', deviceId);

    updateConnectionStatus('online', 'Connected');

    // Start listeners
    listenToDeviceStatus();
    listenToEvents();
    listenToDailyStats();
    listenToAlerts();
}

// Disconnect Device
function disconnectDevice() {
    if (eventsListener) eventsListener();
    if (deviceListener) deviceListener();
    if (statsListener) statsListener();
    if (alertsListener) alertsListener();

    currentDeviceId = null;
    updateConnectionStatus('offline', 'Disconnected');
    clearAllData();
}

// Update Connection Status
function updateConnectionStatus(status, text) {
    const statusDot = connectionStatus.querySelector('.status-dot');
    const statusText = connectionStatus.querySelector('.status-text');

    statusDot.className = `status-dot ${status}`;
    statusText.textContent = text;
}

// Listen to Device Status
function listenToDeviceStatus() {
    const deviceRef = db.collection('devices').doc(currentDeviceId);

    deviceListener = deviceRef.onSnapshot((doc) => {
        if (doc.exists) {
            const data = doc.data();
            updateDeviceInfo(data);
        } else {
            console.log('Device not found. Creating placeholder...');
            document.getElementById('deviceName').textContent = 'Device not initialized';
        }
    }, (error) => {
        console.error('Error listening to device:', error);
        updateConnectionStatus('offline', 'Error: ' + error.message);
    });
}

// Update Device Info UI
function updateDeviceInfo(data) {
    document.getElementById('deviceName').textContent = data.deviceName || 'Unknown';
    document.getElementById('childName').textContent = data.childName || 'Child';
    document.getElementById('lastSeen').textContent = data.lastSeen
        ? formatTimestamp(data.lastSeen.toDate())
        : 'Never';

    // Update badges
    updateBadge('vpnBadge', 'VPN', data.vpnEnabled);
    updateBadge('adminBadge', 'Admin', data.adminProtectionEnabled);
    updateBadge('lockBadge', 'Lock', data.settingsLockEnabled);
}

function updateBadge(elementId, label, isActive) {
    const badge = document.getElementById(elementId);
    badge.textContent = `${label}: ${isActive ? 'ON' : 'OFF'}`;
    badge.classList.toggle('active', isActive);
}

// Listen to Events (Real-time)
function listenToEvents() {
    const eventsRef = db.collection('devices')
        .doc(currentDeviceId)
        .collection('events')
        .orderBy('timestamp', 'desc')
        .limit(50);

    eventsListener = eventsRef.onSnapshot((snapshot) => {
        eventsList.innerHTML = '';

        if (snapshot.empty) {
            eventsList.innerHTML = '<p class="no-data">No events yet</p>';
            return;
        }

        snapshot.forEach((doc) => {
            const event = doc.data();
            const eventElement = createEventElement(event);
            eventsList.appendChild(eventElement);
        });

        filterEvents(); // Apply current filters
    }, (error) => {
        console.error('Error listening to events:', error);
    });
}

// Create Event Element
function createEventElement(event) {
    const div = document.createElement('div');
    div.className = `event-item severity-${event.severity}`;
    div.dataset.severity = event.severity;
    div.dataset.type = event.eventType;

    const timestamp = event.timestamp ? formatTimestamp(event.timestamp.toDate()) : 'Just now';

    div.innerHTML = `
        <div class="event-header">
            <span class="event-type">${getEventIcon(event.eventType)} ${formatEventType(event.eventType)}</span>
            <span class="event-time">${timestamp}</span>
        </div>
        <div class="event-reason">${event.reason || 'No reason provided'}</div>
        ${createEventDetails(event)}
    `;

    return div;
}

function createEventDetails(event) {
    const details = [];

    if (event.domain) details.push(`<span class="event-detail-badge">🌐 ${event.domain}</span>`);
    if (event.searchQuery) details.push(`<span class="event-detail-badge">🔍 "${event.searchQuery}"</span>`);
    if (event.appName) details.push(`<span class="event-detail-badge">📱 ${event.appName}</span>`);
    if (event.mlScores) {
        const maxScore = Math.max(...Object.values(event.mlScores)).toFixed(2);
        details.push(`<span class="event-detail-badge">🤖 ML: ${maxScore}</span>`);
    }

    if (details.length === 0) return '';

    return `<div class="event-details">${details.join('')}</div>`;
}

// Listen to Daily Stats
function listenToDailyStats() {
    const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
    const statsRef = db.collection('devices')
        .doc(currentDeviceId)
        .collection('daily_stats')
        .doc(today);

    statsListener = statsRef.onSnapshot((doc) => {
        if (doc.exists) {
            const stats = doc.data();
            updateTodayStats(stats);
            updateChartsData(stats);
        } else {
            // Daily_stats document doesn't exist - calculate from events as fallback
            console.warn('Daily stats not found. Calculating from events...');
            calculateStatsFromEvents();
        }
    });
}

// Fallback: Calculate stats from events when daily_stats doesn't exist
function calculateStatsFromEvents() {
    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);

    db.collection('devices')
        .doc(currentDeviceId)
        .collection('events')
        .where('timestamp', '>=', todayStart)
        .get()
        .then((snapshot) => {
            console.log(`Found ${snapshot.size} events for today`);

            const stats = {
                totalBlocks: 0,
                criticalBlocks: 0,
                imageBlocks: 0,
                urlBlocks: 0,
                searchBlocks: 0,
                pageBlocks: 0,
                topBlockedDomains: {},
                topReasons: {}
            };

            snapshot.forEach((doc) => {
                const event = doc.data();

                // Only count blocking/filtering events
                if (event.category === 'CONTENT_FILTER' || event.category === 'ACCESS_CONTROL') {
                    stats.totalBlocks++;

                    if (event.eventType === 'IMAGE_BLOCKED') stats.imageBlocks++;
                    if (event.eventType === 'URL_BLOCKED') stats.urlBlocks++;
                    if (event.eventType === 'SEARCH_BLOCKED') stats.searchBlocks++;
                    if (event.eventType === 'PAGE_BLOCKED') stats.pageBlocks++;

                    if (event.severity === 'CRITICAL') stats.criticalBlocks++;

                    if (event.domain) {
                        stats.topBlockedDomains[event.domain] = (stats.topBlockedDomains[event.domain] || 0) + 1;
                    }

                    if (event.reason) {
                        const key = event.reason.substring(0, 50);
                        stats.topReasons[key] = (stats.topReasons[key] || 0) + 1;
                    }
                }
            });

            console.log('Calculated stats:', stats);
            updateTodayStats(stats);
            updateChartsData(stats);
        })
        .catch((error) => {
            console.error('Error calculating stats:', error);
            updateTodayStats({});
        });
}

// Update Today's Stats
function updateTodayStats(stats) {
    document.getElementById('totalBlocks').textContent = stats.totalBlocks || 0;
    document.getElementById('criticalBlocks').textContent = stats.criticalBlocks || 0;
    document.getElementById('imageBlocks').textContent = stats.imageBlocks || 0;
    document.getElementById('urlBlocks').textContent = stats.urlBlocks || 0;
}

// Update Charts
function updateChartsData(stats) {
    // Top Blocked Domains
    const domainsChart = document.getElementById('domainsChart');
    if (stats.topBlockedDomains && Object.keys(stats.topBlockedDomains).length > 0) {
        const sorted = Object.entries(stats.topBlockedDomains)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5);
        domainsChart.innerHTML = renderBarChart(sorted);
    } else {
        domainsChart.innerHTML = '<p class="no-data">No domains blocked yet</p>';
    }

    // Top Block Reasons
    const reasonsChart = document.getElementById('reasonsChart');
    if (stats.topReasons && Object.keys(stats.topReasons).length > 0) {
        const sorted = Object.entries(stats.topReasons)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5);
        reasonsChart.innerHTML = renderBarChart(sorted);
    } else {
        reasonsChart.innerHTML = '<p class="no-data">No blocks yet</p>';
    }
}

function renderBarChart(data) {
    const maxValue = Math.max(...data.map(d => d[1]));
    return data.map(([label, value]) => {
        const percentage = (value / maxValue) * 100;
        return `
            <div class="chart-bar">
                <div class="chart-label" title="${label}">${label}</div>
                <div class="chart-bar-fill" style="width: ${percentage}%">
                    <span class="chart-value">${value}</span>
                </div>
            </div>
        `;
    }).join('');
}

// Listen to Alerts
function listenToAlerts() {
    const alertsRef = db.collection('devices')
        .doc(currentDeviceId)
        .collection('alerts')
        .where('acknowledged', '==', false)
        .orderBy('timestamp', 'desc')
        .limit(5);

    alertsListener = alertsRef.onSnapshot((snapshot) => {
        const alertsList = document.getElementById('alertsList');

        if (snapshot.empty) {
            alertsList.innerHTML = '<p class="no-data">No alerts</p>';
            return;
        }

        alertsList.innerHTML = '';
        snapshot.forEach((doc) => {
            const alert = doc.data();
            const alertElement = createAlertElement(alert);
            alertsList.appendChild(alertElement);
        });
    });
}

function createAlertElement(alert) {
    const div = document.createElement('div');
    div.className = `alert-item ${alert.severity === 'CRITICAL' ? 'critical' : ''}`;

    const timestamp = alert.timestamp ? formatTimestamp(alert.timestamp.toDate()) : 'Just now';

    div.innerHTML = `
        <div>${alert.message}</div>
        <div class="alert-time">${timestamp}</div>
    `;

    return div;
}

// Filter Events
function filterEvents() {
    const severityValue = severityFilter.value;
    const typeValue = typeFilter.value;

    const events = eventsList.querySelectorAll('.event-item');
    events.forEach(event => {
        const matchSeverity = severityValue === 'all' || event.dataset.severity === severityValue;
        const matchType = typeValue === 'all' || event.dataset.type === typeValue;

        event.style.display = (matchSeverity && matchType) ? 'block' : 'none';
    });
}

// Utility Functions
function formatTimestamp(date) {
    const now = new Date();
    const diff = Math.floor((now - date) / 1000); // seconds

    if (diff < 60) return 'Just now';
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;

    return date.toLocaleString();
}

function formatEventType(type) {
    return type.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
}

function getEventIcon(type) {
    const icons = {
        'IMAGE_BLOCKED': '🖼️',
        'URL_BLOCKED': '🚫',
        'SEARCH_BLOCKED': '🔍',
        'PAGE_BLOCKED': '📄',
        'APP_LOCKED': '🔒',
        'DOMAIN_BLOCKED': '🌐',
        'VPN_STARTED': '✅',
        'VPN_STOPPED': '⏸️'
    };
    return icons[type] || '📊';
}

function clearAllData() {
    eventsList.innerHTML = '<p class="no-data">Connect to a device to see events</p>';
    document.getElementById('todayStats').querySelectorAll('.stat-value').forEach(el => el.textContent = '0');
    document.getElementById('domainsChart').innerHTML = '<p class="no-data">No data available</p>';
    document.getElementById('reasonsChart').innerHTML = '<p class="no-data">No data available</p>';
    document.getElementById('alertsList').innerHTML = '<p class="no-data">No alerts</p>';
}
