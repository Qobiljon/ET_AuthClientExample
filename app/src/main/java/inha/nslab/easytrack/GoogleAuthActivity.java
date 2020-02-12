package inha.nslab.easytrack;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

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

public class GoogleAuthActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN_WITH_GOOGLE = 101;
    private GoogleSignInClient signInClient;
    private static String TAG = "ET_AUTH_EXAMPLE_APP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_google_auth);

        // Google login client setup
        GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.server_google_client_id))
                .requestEmail()
                .build();
        signInClient = GoogleSignIn.getClient(this, googleSignInOptions);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null)
            signInClient.signOut();
        SignInButton signInButton = findViewById(R.id.googleSignInButton);
        signInButton.setOnClickListener(view -> startActivityForResult(signInClient.getSignInIntent(), RC_SIGN_IN_WITH_GOOGLE));
        startActivityForResult(signInClient.getSignInIntent(), RC_SIGN_IN_WITH_GOOGLE);
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
                        ManagedChannel channel = ManagedChannelBuilder.forAddress(
                                getString(R.string.grpc_server_ip),
                                Integer.parseInt(getString(R.string.grpc_server_port))
                        ).usePlaintext().build();

                        ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                        EtService.LoginWithGoogleIdTokenRequestMessage requestMessage = EtService.LoginWithGoogleIdTokenRequestMessage.newBuilder()
                                .setIdToken(account.getIdToken())
                                .build();
                        EtService.LoginResponseMessage responseMessage = stub.loginWithGoogleId(requestMessage);

                        try {
                            channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "GoogleAuthActivity.onActivityResult: gRPC channel shutdown() failure");
                            e.printStackTrace();
                        }

                        if (responseMessage.getDoneSuccessfully()) {
                            Intent result = new Intent("etAuthResult");
                            result.putExtra("idToken", account.getIdToken());
                            result.putExtra("fullName", account.getEmail());
                            result.putExtra("email", account.getEmail());
                            result.putExtra("userId", responseMessage.getUserId());
                            setResult(Activity.RESULT_OK, result);
                            finish();
                        } else {
                            // technical issue, shouldn't happen
                            signInClient.signOut();
                            Intent result = new Intent("etAuthResult");
                            result.putExtra("doneSuccessfully", false);
                            result.putExtra("note", String.format(Locale.getDefault(), "please contact the EasyTrack developers with the following details: success=%b, userId=%d", responseMessage.getDoneSuccessfully(), responseMessage.getUserId()));
                            setResult(Activity.RESULT_CANCELED, result);
                            finish();
                        }
                    }).start();
                } else {
                    Log.e(TAG, "GoogleAuthActivity.onActivityResult: Google sign in canceled by the user");
                    Intent result = new Intent("etAuthResult");
                    setResult(Activity.RESULT_FIRST_USER, result);
                    finish();
                }
            } catch (ApiException e) {
                Log.e(TAG, "GoogleAuthActivity.onActivityResult: Google sign in failure; message=" + e.getMessage());
                e.printStackTrace();

                Intent result = new Intent("etAuthResult");
                result.putExtra("exception_message", e.getMessage());
                result.putExtra("exception_details", e.toString());
                setResult(Activity.RESULT_CANCELED, result);
                finish();
            }
        }
    }
}
