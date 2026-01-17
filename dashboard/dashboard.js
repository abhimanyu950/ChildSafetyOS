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

// App Categories (Mirroring Android Logic)
const APP_CATEGORIES = {
    GAMES: [
        "com.supercell.clashofclans", "com.supercell.clashroyale",
        "com.mojang.minecraftpe", "com.kiloo.subwaysurf",
        "com.imangi.templerun2", "com.pubg.krmobile",
        "com.activision.callofduty.shooter", "com.dts.freefireth",
        "com.mobile.legends", "com.riotgames.league.wildrift",
        "com.roblox.client"
    ],
    SOCIAL: [
        "com.instagram.android", "com.zhiliaoapp.musically", // TikTok
        "com.snapchat.android", "com.twitter.android",
        "com.facebook.katana", "com.whatsapp",
        "com.discord"
    ],
    VIDEO: [
        "com.google.android.youtube", "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient", "in.startv.hotstar",
        "com.jio.media.jiobeats"
    ],
    BROWSER: [
        "com.android.chrome", "org.mozilla.firefox",
        "com.microsoft.emmx", "com.opera.browser"
    ]
};

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
    // Use local date format (YYYY-MM-DD) to match Android app's SimpleDateFormat
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    console.log('Looking for daily_stats with date:', today);
    const statsRef = db.collection('devices')
        .doc(currentDeviceId)
        .collection('daily_stats')
        .doc(today);

    statsListener = statsRef.onSnapshot((doc) => {
        if (doc.exists) {
            const stats = doc.data();
            console.log('Daily stats received:', stats);
            console.log('Video blocks from daily_stats:', stats.videoBlocks);
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
                videoBlocks: 0,
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
                    if (event.eventType === 'VIDEO_BLOCKED') stats.videoBlocks++;
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
    document.getElementById('videoBlocks').textContent = stats.videoBlocks || 0;
    document.getElementById('urlBlocks').textContent = stats.urlBlocks || 0;

    // New: Calculate and display Wellbeing Insights
    updateWellbeingInsights(stats);
}

function updateWellbeingInsights(stats) {
    // 1. Calculate Score
    const scoreData = calculateWellbeingScore(stats);

    // Update Score UI
    const scoreElement = document.getElementById('wellbeingScore');
    if (scoreElement) {
        scoreElement.textContent = scoreData.score;

        // Color coding
        scoreElement.className = 'wellbeing-score-value'; // Reset
        if (scoreData.score >= 80) scoreElement.classList.add('score-good');
        else if (scoreData.score >= 50) scoreElement.classList.add('score-average');
        else scoreElement.classList.add('score-bad');
    }

    // Update Score Label/Context
    const scoreContext = document.getElementById('wellbeingContext');
    if (scoreContext) {
        scoreContext.textContent = scoreData.context;
    }

    // 2. Generate Recommendations
    const recommendations = generateRecommendations(stats, scoreData);
    const recList = document.getElementById('recommendationsList');

    if (recList) {
        recList.innerHTML = '';
        if (recommendations.length === 0) {
            recList.innerHTML = '<p class="no-data">Great job! Everything looks good.</p>';
        } else {
            recommendations.forEach(rec => {
                const div = document.createElement('div');
                div.className = `recommendation-item ${rec.type.toLowerCase()}`;
                div.innerHTML = `
                    <div class="rec-icon">${rec.icon}</div>
                    <div class="rec-content">
                        <strong>${rec.title}</strong>
                        <p>${rec.message}</p>
                    </div>
                `;
                recList.appendChild(div);
            });
        }
    }
}

function calculateWellbeingScore(stats) {
    let score = 100;
    const deductions = [];

    // Deduct for critical blocks
    const criticals = stats.criticalBlocks || 0;
    if (criticals > 0) {
        const penalty = Math.min(criticals * 10, 40); // Max 40 pts deduction
        score -= penalty;
        deductions.push("Safety Incidents");
    }

    // Deduct for excessive screen time (if available)
    // Heuristic: Sum app usage
    let totalMinutes = 0;
    if (stats.appUsage) {
        totalMinutes = Object.values(stats.appUsage).reduce((a, b) => a + b, 0);
    }

    if (totalMinutes > 120) { // > 2 hours
        const extraHours = Math.ceil((totalMinutes - 120) / 60);
        const penalty = Math.min(extraHours * 5, 30); // 5 pts per extra hour, max 30
        score -= penalty;
        deductions.push("High Screen Time");
    }

    // Deduct for high category usage (e.g. Social > 1h)
    // This requires iterating appUsage and mapping to categories...
    // We'll trust the totalMinutes for now to keep it simple, 
    // but could refine this if we want specific penalties for "Doomscrolling".

    return {
        score: Math.max(0, score),
        context: deductions.length > 0
            ? `Impacted by: ${deductions.join(", ")}`
            : "Healthy digital habits detected."
    };
}

function generateRecommendations(stats, scoreData) {
    const recs = [];

    // 1. Critical Blocks
    if ((stats.criticalBlocks || 0) > 0) {
        recs.push({
            type: 'URGENT',
            icon: '🛡️',
            title: 'Review Blocked Content',
            message: 'Critical content was blocked today. Check the "Live Activity Feed" to see specifically what was accessed and consider talking to your child.'
        });
    }

    // 2. High Screen Time
    let totalMinutes = 0;
    const categoryUsage = { GAMES: 0, SOCIAL: 0, VIDEO: 0, OTHER: 0 };

    if (stats.appUsage) {
        Object.entries(stats.appUsage).forEach(([pkg, minutes]) => {
            totalMinutes += minutes;
            const cat = getAppCategory(pkg);
            categoryUsage[cat] += minutes;
        });
    }

    if (totalMinutes > 180) { // > 3h
        recs.push({
            type: 'WARNING',
            icon: '⏱️',
            title: 'Reduce Screen Time',
            message: `Total screen time is high (${Math.floor(totalMinutes / 60)}h ${totalMinutes % 60}m). Consider setting a daily limit/downtime.`
        });
    }

    // 3. Category Specific
    if (categoryUsage.SOCIAL > 60) {
        recs.push({
            type: 'INFO',
            icon: '💬',
            title: 'Social Media Limit',
            message: 'Social media usage is over 1 hour. Verify if this aligns with your family rules.'
        });
    }

    if (categoryUsage.GAMES > 60) {
        recs.push({
            type: 'INFO',
            icon: '🎮',
            title: 'Gaming Balance',
            message: 'Gaming usage is high today. Encourage outdoor activities or reading.'
        });
    }

    return recs;
}

function getAppCategory(packageName) {
    if (APP_CATEGORIES.GAMES.some(p => packageName.includes(p))) return 'GAMES';
    if (APP_CATEGORIES.SOCIAL.some(p => packageName.includes(p))) return 'SOCIAL';
    if (APP_CATEGORIES.VIDEO.some(p => packageName.includes(p))) return 'VIDEO';
    if (APP_CATEGORIES.BROWSER.some(p => packageName.includes(p)) || packageName.includes('browser') || packageName.includes('chrome')) return 'BROWSER';
    return 'OTHER';
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

    // App Usage Chart
    const usageChart = document.getElementById('usageChart');
    if (stats.appUsage && Object.keys(stats.appUsage).length > 0) {
        const sorted = Object.entries(stats.appUsage)
            .sort((a, b) => b[1] - a[1]) // Sort by duration desc
            .slice(0, 10); // Limit to top 10
        usageChart.innerHTML = renderUsageChart(sorted);

        // Render Category Chart as well
        renderCategoryChart(stats.appUsage);
    } else {
        usageChart.innerHTML = '<p class="no-data">No usage data yet</p>';
        document.getElementById('categoryChart').innerHTML = '<p class="no-data">No usage data</p>';
    }
}

function renderCategoryChart(appUsage) {
    const categoryUsage = { GAMES: 0, SOCIAL: 0, VIDEO: 0, BROWSER: 0, OTHER: 0 };
    let total = 0;

    Object.entries(appUsage).forEach(([pkg, minutes]) => {
        const cat = getAppCategory(pkg);
        categoryUsage[cat] = (categoryUsage[cat] || 0) + minutes;
        total += minutes;
    });

    const categoryChart = document.getElementById('categoryChart');

    // Sort logic
    const sortedCats = Object.entries(categoryUsage)
        .sort((a, b) => b[1] - a[1])
        .filter(x => x[1] > 0); // Only show used categories

    if (sortedCats.length === 0) {
        categoryChart.innerHTML = '<p class="no-data">No usage data</p>';
        return;
    }

    const html = sortedCats.map(([cat, minutes]) => {
        const percentage = total > 0 ? (minutes / total) * 100 : 0;
        const colorClass = `cat-${cat.toLowerCase()}`;
        return `
            <div class="chart-bar">
                <div class="chart-label">${cat}</div>
                <div class="chart-bar-fill ${colorClass}-bg" style="width: ${percentage}%">
                    <span class="chart-value">${minutes}m</span>
                </div>
            </div>
        `;
    }).join('');

    categoryChart.innerHTML = html;
}

function renderBarChart(data) {
    const maxValue = Math.max(...data.map(d => d[1]));
    return data.map(([label, value]) => {
        // Restore dots for display (pornhub_com -> pornhub.com)
        const displayLabel = label.replace(/_/g, '.');
        const percentage = (value / maxValue) * 100;
        return `
            <div class="chart-bar">
                <div class="chart-label" title="${displayLabel}">${displayLabel}</div>
                <div class="chart-bar-fill" style="width: ${percentage}%">
                    <span class="chart-value">${value}</span>
                </div>
            </div>
        `;
    }).join('');
}

function renderUsageChart(data) {
    const maxValue = Math.max(...data.map(d => d[1]));
    return data.map(([label, value]) => {
        // Pretty name logic could go here
        const displayLabel = label.split('.').pop(); // Simple package name
        const percentage = (value / maxValue) * 100;

        // Format time (e.g. 90 -> 1h 30m)
        const hrs = Math.floor(value / 60);
        const mins = value % 60;
        const timeStr = hrs > 0 ? `${hrs}h ${mins}m` : `${mins}m`;

        return `
            <div class="chart-bar">
                <div class="chart-label" title="${label}">${displayLabel}</div>
                <div class="chart-bar-fill" style="width: ${percentage}%">
                    <span class="chart-value">${timeStr}</span>
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
        'VIDEO_BLOCKED': '🎥',
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

// --- DEBUG / TESTING ONLY ---
window.injectMockData = function () {
    console.log("Injecting mock data for verification...");

    const mockStats = {
        totalBlocks: 15,
        criticalBlocks: 3,
        imageBlocks: 10,
        videoBlocks: 2,
        urlBlocks: 3,
        topBlockedDomains: { "pornhub_com": 5, "xamster_com": 2, "random_adult_site_net": 1 },
        topReasons: { "Nudity detected": 8, "Explicit content": 4, "Blocked Domain": 3 },
        appUsage: {
            "com.instagram.android": 95, // Social > 60
            "com.roblox.client": 120,    // Games > 60
            "com.google.android.youtube": 45, // Video
            "com.android.chrome": 30, // Browser
            "com.calculator": 5
        }
    };

    updateTodayStats(mockStats);
    updateChartsData(mockStats);

    // Also simulate device info
    updateDeviceInfo({
        deviceName: "Mock Device (Pixel 7)",
        childName: "Alex",
        lastSeen: { toDate: () => new Date() },
        vpnEnabled: true,
        adminProtectionEnabled: true,
        settingsLockEnabled: false
    });

    alert("Mock data injected!");
};
