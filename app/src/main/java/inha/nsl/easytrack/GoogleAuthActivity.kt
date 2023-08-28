package inha.nsl.easytrack

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import kotlinx.android.synthetic.main.activity_google_auth.*
import java.util.*
import java.util.concurrent.TimeUnit

class GoogleAuthActivity : AppCompatActivity() {
    private lateinit var signInClient: GoogleSignInClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_google_auth)

        // Google login client setup
        val googleSignInOptions = GoogleSignInOptions.Builder()
            .requestProfile()
            .requestEmail()
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()
        signInClient = GoogleSignIn.getClient(this, googleSignInOptions)
        googleSignInButton.setOnClickListener {
            continueWithGoogleAccountButton.visibility = View.GONE
            googleSignInButton.isEnabled = false
            val account = GoogleSignIn.getLastSignedInAccount(this)
            if (account == null || account.isExpired)
                startGoogleAuthenticationActivity()
            else {
                signInClient.signOut().addOnSuccessListener {
                    continueWithGoogleAccountButton.visibility = View.GONE
                    googleSignInButton.isEnabled = true
                    setGoogleSignInButtonText(googleSignInButton, "Sign in")
                    startGoogleAuthenticationActivity()
                }.addOnFailureListener {
                    val acc = GoogleSignIn.getLastSignedInAccount(this)!!
                    continueWithGoogleAccountButton.visibility = View.VISIBLE
                    googleSignInButton.isEnabled = true
                    setGoogleSignInButtonText(
                        continueWithGoogleAccountButton,
                        "Continue as: " + acc.email
                    )
                    setGoogleSignInButtonText(googleSignInButton, "Sign in with different account")
                }
            }
        }
        continueWithGoogleAccountButton.setOnClickListener {
            val account = GoogleSignIn.getLastSignedInAccount(this)!!
            continueWithGoogleAccountButton.isEnabled = false
            googleSignInButton.isEnabled = false
            val thread = Thread {
                try {
                    val channel = ManagedChannelBuilder.forAddress(
                        getString(R.string.grpc_host),
                        getString(R.string.grpc_port).toInt()
                    ).usePlaintext().build()
                    val stub = ETServiceGrpc.newBlockingStub(channel)
                    val requestMessage =
                        EtService.LoginWithGoogle.Request.newBuilder().setIdToken(account.idToken)
                            .build()
                    val responseMessage = stub.loginWithGoogle(requestMessage)
                    try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    if (responseMessage.success) runOnUiThread {
                        val result = Intent("etAuthResult")
                        result.putExtra("resultFieldNames", "name,email,userId,sessionKey")
                        result.putExtra("name", account.displayName)
                        result.putExtra("email", account.email)
                        result.putExtra("userId", responseMessage.userId)
                        result.putExtra("sessionKey", responseMessage.sessionKey)
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    } else  // technical issue, shouldn't happen
                        runOnUiThread {
                            signInClient.signOut().addOnCompleteListener {
                                val result = Intent("etAuthResult")
                                result.putExtra("resultFieldNames", "note")
                                result.putExtra(
                                    "note",
                                    String.format(
                                        Locale.getDefault(),
                                        "please contact the EasyTrack developers with the following details: success=%b, sessionKey=%d",
                                        responseMessage.success,
                                        responseMessage.sessionKey
                                    )
                                )
                                setResult(Activity.RESULT_CANCELED, result)
                                finish()
                            }
                        }
                } catch (e: StatusRuntimeException) {
                    runOnUiThread {
                        val result = Intent("etAuthResult")
                        result.putExtra("resultFieldNames", "exception_message,exception_details")
                        result.putExtra("exception_message", e.message)
                        result.putExtra("exception_details", e.toString())
                        setResult(Activity.RESULT_CANCELED, result)
                        finish()
                    }
                }
            }
            thread.start()
            thread.join()
        }
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null || account.isExpired) {
            setGoogleSignInButtonText(googleSignInButton, "Sign in")
            continueWithGoogleAccountButton.visibility = View.GONE
            startGoogleAuthenticationActivity()
        } else {
            setGoogleSignInButtonText(googleSignInButton, "Sign in with different account")
            continueWithGoogleAccountButton.visibility = View.VISIBLE
            setGoogleSignInButtonText(
                continueWithGoogleAccountButton,
                "Continue as: ${account.email}"
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN_WITH_GOOGLE) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    Thread {
                        try {
                            val channel = ManagedChannelBuilder.forAddress(
                                getString(R.string.grpc_host),
                                getString(R.string.grpc_port).toInt()
                            ).usePlaintext().build()
                            val stub = ETServiceGrpc.newBlockingStub(channel)
                            val requestMessage = EtService.LoginWithGoogle.Request.newBuilder()
                                .setIdToken(account.idToken).build()
                            val responseMessage = stub.loginWithGoogle(requestMessage)
                            channel.shutdown()
                            if (responseMessage.success) runOnUiThread {
                                val result = Intent("etAuthResult")
                                result.putExtra("resultFieldNames", "name,email,userId,sessionKey")
                                result.putExtra("name", account.displayName)
                                result.putExtra("email", account.email)
                                result.putExtra("userId", responseMessage.userId)
                                result.putExtra("sessionKey", responseMessage.sessionKey)
                                setResult(Activity.RESULT_OK, result)
                                finish()
                            } else  // technical issue, shouldn't happen
                                runOnUiThread {
                                    signInClient.signOut().addOnCompleteListener {
                                        val result = Intent("etAuthResult")
                                        result.putExtra("resultFieldNames", "note")
                                        result.putExtra(
                                            "note",
                                            String.format(
                                                Locale.getDefault(),
                                                "please contact the EasyTrack developers with the following details: success=%b, sessionKey=%d",
                                                responseMessage.success,
                                                responseMessage.sessionKey
                                            )
                                        )
                                        setResult(Activity.RESULT_CANCELED, result)
                                        finish()
                                    }
                                }
                        } catch (e: StatusRuntimeException) {
                            runOnUiThread {
                                val result = Intent("etAuthResult")
                                result.putExtra(
                                    "resultFieldNames",
                                    "exception_message,exception_details"
                                )
                                result.putExtra("exception_message", e.message)
                                result.putExtra("exception_details", e.toString())
                                setResult(Activity.RESULT_CANCELED, result)
                                finish()
                            }
                        }
                    }.start()
                } else {
                    val result = Intent("etAuthResult")
                    result.putExtra("resultFieldNames", "N/A")
                    setResult(Activity.RESULT_FIRST_USER, result)
                    finish()
                }
            } catch (e: ApiException) {
                val result = Intent("etAuthResult")
                result.putExtra("resultFieldNames", "exception_message,exception_details")
                result.putExtra("exception_message", e.message)
                result.putExtra("exception_details", e.toString())
                setResult(Activity.RESULT_CANCELED, result)
                finish()
            }
        }
    }

    private fun setGoogleSignInButtonText(button: SignInButton, text: String) {
        for (child in button.children) {
            if (child is TextView) {
                child.text = text
                return
            }
        }
    }

    private fun startGoogleAuthenticationActivity() {
        val intent = signInClient.signInIntent
        intent.flags = 0
        startActivityForResult(intent, RC_SIGN_IN_WITH_GOOGLE)
    }

    companion object {
        private const val RC_SIGN_IN_WITH_GOOGLE = 101
    }
}
