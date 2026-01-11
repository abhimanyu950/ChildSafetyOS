# Cloud Functions Deployment Guide

## 🚀 Quick Start

### Step 1: Configure Email Service

**Edit `functions/index.js`**:

```javascript
const transporter = nodemailer.createTransport({
  service: 'gmail',
  auth: {
    user: 'your-actual-email@gmail.com',  // Your Gmail
    pass: 'xxxx-xxxx-xxxx-xxxx'           // App Password (NOT your Gmail password)
  }
});
```

### Step 2: Get Gmail App Password

1. Go to https://myaccount.google.com/apppasswords
2. Sign in with your Gmail account
3. App name: "ChildSafetyOS"
4. Click "Create"
5. Copy the 16-character password (e.g., `abcd efgh ijkl mnop`)
6. Paste into `index.js` (remove spaces)

### Step 3: Install Dependencies

```bash
cd functions
npm install
```

### Step 4: Deploy to Firebase

```bash
firebase deploy --only functions
```

## ✅ Verify Deployment

After deployment succeeds, you'll see:
```
✔  Deploy complete!

Functions:
- sendAlertEmail(us-central1)
- testFunction(us-central1)
```

## 🧪 Test the Function

### Test 1: HTTP Test Function
```bash
curl https://us-central1-YOUR-PROJECT-ID.cloudfunctions.net/testFunction
```

Should return: `ChildSafetyOS Cloud Functions are working! ✅`

### Test 2: Trigger Real Alert

1. Run Android app
2. Search explicit content (porn >0.8)
3. Check Firestore Console → `alerts` collection
4. Within 1 minute, check parent email inbox
5. Alert document should update: `emailSent: true`

## 🐛 Troubleshooting

### "Authentication failed"
- Check Gmail App Password is correct (16 chars, no spaces)
- Verify 2FA is enabled on Gmail account

### "Device not found"
- Add parent email to device document:
```javascript
db.collection('devices').doc('YOUR_DEVICE_ID').update({
  parentEmail: 'parent@example.com'
});
```

### "No parent email configured"
- Same fix as above

### View Function Logs
```bash
firebase functions:log --only sendAlertEmail
```

## 📧 Alternative: SendGrid (Recommended for Production)

### Why SendGrid?
- 100 emails/day free (vs Gmail's 500)
- More reliable delivery
- Professional sender domain
- Better analytics

### Setup SendGrid

1. Sign up at https://sendgrid.com
2. Verify sender email/domain
3. Get API key
4. Install SendGrid:
```bash
npm install @sendgrid/mail
```

5. Set Firebase config:
```bash
firebase functions:config:set sendgrid.key="YOUR_API_KEY"
```

6. Update `index.js` (uncomment SendGrid section, comment Gmail)

7. Redeploy:
```bash
firebase deploy --only functions
```

## 📊 Monitor Email Delivery

### Check Alert Status in Firestore

**Successful**:
```javascript
{
  emailSent: true,
  emailSentTimestamp: "2026-01-11T02:45:00Z",
  emailProvider: "gmail",
  emailMessageId: "abc123@gmail.com"
}
```

**Failed**:
```javascript
{
  emailSent: false,
  emailError: "Invalid credentials",
  emailAttemptTimestamp: "2026-01-11T02:45:00Z"
}
```

## 🎯 Next Steps

1. ✅ Deploy Cloud Function
2. ✅ Test with sample alert
3. ✅ Verify parent receives email
4. ⏭️ Add parent email to device setup flow (Android app)
5. ⏭️ Display alerts in dashboard (already implemented in `email_alert_guide.md`)

## 🔒 Security Notes

- **Never commit** App Password or API keys to Git
- Use environment variables or Firebase Config
- Rotate passwords regularly
- Monitor function logs for suspicious activity

## 💰 Cost Estimates

**Free Tier** (Spark Plan):
- 125K invocations/month: FREE
- Network: 5GB/month: FREE
- Your usage: ~100 alerts/month = **$0/month**

**Paid Tier** (if needed):
- $0.40 per million invocations
- At 10K alerts/month: ~$0.004/month (essentially free)

---

**You're all set!** Deploy and test the function now. 🚀
