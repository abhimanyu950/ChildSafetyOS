package com.childsafety.os.cloud

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Authentication for ChildSafetyOS.
 * 
 * Supports:
 * - Anonymous Auth: Automatic sign-in for device binding
 * - Email/Password Auth: Optional parent login for dashboard access
 */
object AuthManager {
    
    private const val TAG = "AuthManager"
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
    // Current authenticated user
    val currentUser: FirebaseUser?
        get() = auth.currentUser
    
    // Check if user is authenticated (anonymous or signed in)
    val isAuthenticated: Boolean
        get() = auth.currentUser != null
    
    // Check if user is anonymous
    val isAnonymous: Boolean
        get() = auth.currentUser?.isAnonymous == true
    
    // Get user ID (UID)
    val userId: String?
        get() = auth.currentUser?.uid
    
    /**
     * Initialize authentication - sign in anonymously if not already authenticated.
     * This should be called on app start.
     */
    suspend fun initAuth(): Boolean {
        return try {
            if (auth.currentUser == null) {
                // No user - sign in anonymously
                val result = auth.signInAnonymously().await()
                Log.i(TAG, "Anonymous sign-in successful. UID: ${result.user?.uid}")
                true
            } else {
                // Already signed in
                Log.i(TAG, "Already authenticated. UID: ${auth.currentUser?.uid}, Anonymous: ${auth.currentUser?.isAnonymous}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            false
        }
    }
    
    /**
     * Sign in with email and password (for parent login).
     * If user is currently anonymous, this will link the accounts.
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val currentUser = auth.currentUser
            
            if (currentUser != null && currentUser.isAnonymous) {
                // Link anonymous account to email account
                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                currentUser.linkWithCredential(credential).await()
                Log.i(TAG, "Anonymous account linked to email: $email")
                AuthResult.Success("Account linked successfully")
            } else {
                // Regular sign in
                auth.signInWithEmailAndPassword(email, password).await()
                Log.i(TAG, "Email sign-in successful: $email")
                AuthResult.Success("Signed in successfully")
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            // Email already in use - try regular sign in
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                AuthResult.Success("Signed in successfully")
            } catch (e2: Exception) {
                AuthResult.Error("Email already in use with different credentials")
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("Invalid email or password")
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            AuthResult.Error(e.message ?: "Sign-in failed")
        }
    }
    
    /**
     * Create a new parent account with email and password.
     */
    suspend fun createAccount(email: String, password: String): AuthResult {
        return try {
            val currentUser = auth.currentUser
            
            if (currentUser != null && currentUser.isAnonymous) {
                // Link anonymous account to new email account
                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                currentUser.linkWithCredential(credential).await()
                Log.i(TAG, "Anonymous account upgraded to email: $email")
                AuthResult.Success("Account created successfully")
            } else {
                // Create new account
                auth.createUserWithEmailAndPassword(email, password).await()
                Log.i(TAG, "Account created: $email")
                AuthResult.Success("Account created successfully")
            }
        } catch (e: com.google.firebase.auth.FirebaseAuthWeakPasswordException) {
            AuthResult.Error("Password is too weak (min 6 characters)")
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            AuthResult.Error("Email already in use")
        } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
            AuthResult.Error("Invalid email format")
        } catch (e: Exception) {
            Log.e(TAG, "Account creation failed", e)
            AuthResult.Error(e.message ?: "Account creation failed")
        }
    }
    
    /**
     * Sign out the current user.
     * Note: This will create a new anonymous account on next app start.
     */
    fun signOut() {
        auth.signOut()
        Log.i(TAG, "User signed out")
    }
    
    /**
     * Send password reset email.
     */
    suspend fun sendPasswordReset(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Log.i(TAG, "Password reset email sent to: $email")
            AuthResult.Success("Password reset email sent")
        } catch (e: Exception) {
            Log.e(TAG, "Password reset failed", e)
            AuthResult.Error(e.message ?: "Failed to send password reset email")
        }
    }
    
    /**
     * Delete the current user's account and all associated data.
     * This is for DPDP/GDPR compliance.
     */
    suspend fun deleteAccount(): AuthResult {
        return try {
            val user = auth.currentUser ?: return AuthResult.Error("No user logged in")
            
            // First delete Firestore data
            FirebaseManager.deleteAllData { success ->
                if (success) {
                    Log.i(TAG, "User data deleted from Firestore")
                }
            }
            
            // Then delete the auth account
            user.delete().await()
            Log.i(TAG, "User account deleted")
            AuthResult.Success("Account and data deleted")
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            AuthResult.Error("Please sign in again before deleting account")
        } catch (e: Exception) {
            Log.e(TAG, "Account deletion failed", e)
            AuthResult.Error(e.message ?: "Account deletion failed")
        }
    }
}

/**
 * Result of an authentication operation.
 */
sealed class AuthResult {
    data class Success(val message: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
