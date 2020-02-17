package inha.nslab.easytrack;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

public class GoogleAuthActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN_WITH_GOOGLE = 101;
    // private static String TAG = "ET_AUTH_EXAMPLE_APP";
    private GoogleSignInClient signInClient;
    private SignInButton signInButton;
    private SignInButton continueWithGoogleAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_google_auth);

        signInButton = findViewById(R.id.googleSignInButton);
        continueWithGoogleAccountButton = findViewById(R.id.continueWithGoogleAccountButton);

        // Google login client setup
        GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.server_google_client_id))
                .requestEmail()
                .build();
        signInClient = GoogleSignIn.getClient(this, googleSignInOptions);

        signInButton.setOnClickListener(view -> {
            continueWithGoogleAccountButton.setVisibility(View.GONE);
            signInButton.setEnabled(false);
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account == null)
                startGoogleAuthenticationActivity();
            else
                signInClient.signOut().addOnSuccessListener(v -> {
                    continueWithGoogleAccountButton.setVisibility(View.GONE);
                    setGoogleSignInButtonText(signInButton, "Sign in");
                    startGoogleAuthenticationActivity();
                }).addOnFailureListener(e -> {
                    GoogleSignInAccount _account = GoogleSignIn.getLastSignedInAccount(this);
                    assert _account != null;
                    continueWithGoogleAccountButton.setVisibility(View.VISIBLE);
                    signInButton.setEnabled(true);
                    setGoogleSignInButtonText(continueWithGoogleAccountButton, "Continue as: " + _account.getEmail());
                    setGoogleSignInButtonText(signInButton, "Sign in with different account");
                });
        });
        continueWithGoogleAccountButton.setOnClickListener(view -> {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            assert account != null;
            continueWithGoogleAccountButton.setEnabled(false);
            signInButton.setEnabled(false);
            new Thread(() -> {
                try {
                    ManagedChannel channel = ManagedChannelBuilder.forAddress(
                            getString(R.string.grpc_host),
                            Integer.parseInt(getString(R.string.grpc_port))
                    ).usePlaintext().build();

                    ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                    EtService.LoginWithGoogleIdTokenRequestMessage requestMessage = EtService.LoginWithGoogleIdTokenRequestMessage.newBuilder()
                            .setIdToken(account.getIdToken())
                            .build();
                    EtService.LoginResponseMessage responseMessage = stub.loginWithGoogleId(requestMessage);

                    try {
                        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (responseMessage.getDoneSuccessfully())
                        runOnUiThread(() -> {
                            Intent result = new Intent("etAuthResult");
                            result.putExtra("fields", "idToken,fullName,email,userId");
                            result.putExtra("idToken", account.getIdToken());
                            result.putExtra("fullName", account.getEmail());
                            result.putExtra("email", account.getEmail());
                            result.putExtra("userId", responseMessage.getUserId());
                            setResult(Activity.RESULT_OK, result);
                            finish();
                        });
                    else
                        // technical issue, shouldn't happen
                        runOnUiThread(() -> signInClient.signOut().addOnCompleteListener((Void) -> {
                            Intent result = new Intent("etAuthResult");
                            result.putExtra("fields", "note");
                            result.putExtra("note", String.format(Locale.getDefault(), "please contact the EasyTrack developers with the following details: success=%b, userId=%d", responseMessage.getDoneSuccessfully(), responseMessage.getUserId()));
                            setResult(Activity.RESULT_CANCELED, result);
                            finish();
                        }));
                } catch (StatusRuntimeException e) {
                    runOnUiThread(() -> {
                        Intent result = new Intent("etAuthResult");
                        result.putExtra("fields", "exception_message,exception_details");
                        result.putExtra("exception_message", e.getMessage());
                        result.putExtra("exception_details", e.toString());
                        setResult(Activity.RESULT_CANCELED, result);
                        finish();
                    });
                }
            }).start();
        });

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            setGoogleSignInButtonText(signInButton, "Sign in");
            continueWithGoogleAccountButton.setVisibility(View.GONE);
            startGoogleAuthenticationActivity();
        } else {
            setGoogleSignInButtonText(signInButton, "Sign in with different account");
            continueWithGoogleAccountButton.setVisibility(View.VISIBLE);
            setGoogleSignInButtonText(continueWithGoogleAccountButton, "Continue with: " + account.getEmail());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN_WITH_GOOGLE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    new Thread(() -> {
                        try {
                            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                                    getString(R.string.grpc_host),
                                    Integer.parseInt(getString(R.string.grpc_port))
                            ).usePlaintext().build();

                            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                            EtService.LoginWithGoogleIdTokenRequestMessage requestMessage = EtService.LoginWithGoogleIdTokenRequestMessage.newBuilder()
                                    .setIdToken(account.getIdToken())
                                    .build();
                            EtService.LoginResponseMessage responseMessage = stub.loginWithGoogleId(requestMessage);

                            try {
                                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            if (responseMessage.getDoneSuccessfully())
                                runOnUiThread(() -> {
                                    Intent result = new Intent("etAuthResult");
                                    result.putExtra("fields", "idToken,fullName,email,userId");
                                    result.putExtra("idToken", account.getIdToken());
                                    result.putExtra("fullName", account.getEmail());
                                    result.putExtra("email", account.getEmail());
                                    result.putExtra("userId", responseMessage.getUserId());
                                    setResult(Activity.RESULT_OK, result);
                                    finish();
                                });
                            else
                                // technical issue, shouldn't happen
                                runOnUiThread(() -> signInClient.signOut().addOnCompleteListener((Void) -> {
                                    Intent result = new Intent("etAuthResult");
                                    result.putExtra("fields", "note");
                                    result.putExtra("note", String.format(Locale.getDefault(), "please contact the EasyTrack developers with the following details: success=%b, userId=%d", responseMessage.getDoneSuccessfully(), responseMessage.getUserId()));
                                    setResult(Activity.RESULT_CANCELED, result);
                                    finish();
                                }));
                        } catch (StatusRuntimeException e) {
                            runOnUiThread(() -> {
                                Intent result = new Intent("etAuthResult");
                                result.putExtra("fields", "exception_message,exception_details");
                                result.putExtra("exception_message", e.getMessage());
                                result.putExtra("exception_details", e.toString());
                                setResult(Activity.RESULT_CANCELED, result);
                                finish();
                            });
                        }
                    }).start();
                } else {
                    Intent result = new Intent("etAuthResult");
                    result.putExtra("fields", "N/A");
                    setResult(Activity.RESULT_FIRST_USER, result);
                    finish();
                }
            } catch (ApiException e) {
                Intent result = new Intent("etAuthResult");
                result.putExtra("fields", "exception_message,exception_details");
                result.putExtra("exception_message", e.getMessage());
                result.putExtra("exception_details", e.toString());
                setResult(Activity.RESULT_CANCELED, result);
                finish();
            }
        }
    }

    private void setGoogleSignInButtonText(SignInButton button, String text) {
        for (int n = 0; n < button.getChildCount(); n++) {
            View view = button.getChildAt(n);
            if (view instanceof TextView) {
                ((TextView) view).setText(text);
                return;
            }
        }
    }

    private void startGoogleAuthenticationActivity() {
        Intent intent = signInClient.getSignInIntent();
        intent.setFlags(0);
        startActivityForResult(intent, RC_SIGN_IN_WITH_GOOGLE);
    }
}
