const functions = require('firebase-functions');
const admin = require('firebase-admin');
const nodemailer = require('nodemailer');

admin.initializeApp();

// Email service configuration
// OPTION 1: Gmail (Free, 500 emails/day)
// You need to use App Password, not your regular Gmail password
// Generate at: https://myaccount.google.com/apppasswords
const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: '',  // Gmail configured
        pass: ''        // App Password (spaces removed)
    }
});

// OPTION 2: SendGrid (Recommended for production - 100 free emails/day)
// Uncomment below and comment out Gmail config above
/*
const sgMail = require('@sendgrid/mail');
sgMail.setApiKey(functions.config().sendgrid.key);
*/

/**
 * Sends email alert when new critical explicit content alert is created.
 * 
 * Triggers: When document is added to 'alerts' collection
 * Action: Sends email to parent with detailed incident report
 */
exports.sendAlertEmail = functions.firestore
    .document('alerts/{alertId}')
    .onCreate(async (snapshot, context) => {
        const alertId = context.params.alertId;
        const alert = snapshot.data();

        console.log(`Processing alert ${alertId}:`, {
            type: alert.type,
            deviceId: alert.deviceId,
            pornScore: alert.mlScores?.porn
        });

        // Skip if already sent (failsafe)
        if (alert.emailSent) {
            console.log('Email already sent for this alert, skipping');
            return null;
        }

        try {
            // Get parent email from device document
            const deviceDoc = await admin.firestore()
                .collection('devices')
                .doc(alert.deviceId)
                .get();

            if (!deviceDoc.exists) {
                console.error('Device not found:', alert.deviceId);
                await snapshot.ref.update({
                    emailError: 'Device document not found',
                    emailAttemptTimestamp: admin.firestore.FieldValue.serverTimestamp()
                });
                return null;
            }

            const deviceData = deviceDoc.data();
            const parentEmail = deviceData.parentEmail;

            if (!parentEmail) {
                console.error('No parent email configured for device:', alert.deviceId);
                await snapshot.ref.update({
                    emailError: 'No parent email configured',
                    emailAttemptTimestamp: admin.firestore.FieldValue.serverTimestamp()
                });
                return null;
            }

            console.log('Sending alert email to:', parentEmail);

            // OPTION 1: Send via Gmail/Nodemailer
            const mailOptions = {
                from: '"ChildSafetyOS Alerts" <noreply@childsafetyos.com>',
                to: parentEmail,
                subject: alert.emailSubject,
                html: alert.emailBody
            };

            const info = await transporter.sendMail(mailOptions);
            console.log('Email sent successfully:', info.messageId);

            // OPTION 2: Send via SendGrid (uncomment if using SendGrid)
            /*
            await sgMail.send({
              to: parentEmail,
              from: 'alerts@childsafetyos.com', // Must be verified sender in SendGrid
              subject: alert.emailSubject,
              html: alert.emailBody
            });
            console.log('Email sent via SendGrid');
            */

            // Mark as sent
            await snapshot.ref.update({
                emailSent: true,
                emailSentTimestamp: admin.firestore.FieldValue.serverTimestamp(),
                emailProvider: 'gmail', // or 'sendgrid'
                emailMessageId: info.messageId || null
            });

            console.log('Alert processed successfully');
            return null;

        } catch (error) {
            console.error('Error sending alert email:', error);

            // Log error to Firestore for debugging
            await snapshot.ref.update({
                emailError: error.message,
                emailAttemptTimestamp: admin.firestore.FieldValue.serverTimestamp(),
                emailStackTrace: error.stack
            });

            // Don't throw - let function complete to avoid retries
            return null;
        }
    });

/**
 * Test function to verify Cloud Functions deployment.
 * Call via: firebase functions:call testFunction
 */
exports.testFunction = functions.https.onRequest((req, res) => {
    res.send('ChildSafetyOS Cloud Functions are working! ✅');
});
