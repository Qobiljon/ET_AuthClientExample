package inha.nslab.easytrack;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import static inha.nslab.easytrack.MainActivity.RC_SIGN_IN_WITH_GOOGLE;
import static inha.nslab.easytrack.MainActivity.TAG;

public class GoogleAuthActivity extends AppCompatActivity {
    private GoogleSignInClient signInClient;

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

        findViewById(R.id.googleSignInButton).setOnClickListener(view -> startActivityForResult(signInClient.getSignInIntent(), RC_SIGN_IN_WITH_GOOGLE));
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
                        EtService.LoginWithGoogleIdRequestMessage requestMessage = EtService.LoginWithGoogleIdRequestMessage.newBuilder()
                                .setIdToken(account.getIdToken())
                                .build();
                        EtService.LoginWithGoogleIdResponseMessage responseMessage = stub.loginWithGoogleId(requestMessage);

                        channel.shutdown();

                        if (responseMessage.getDoneSuccessfully()) {
                            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt("userId", responseMessage.getUserId());
                            editor.putString("email", account.getEmail());
                            editor.putBoolean("isParticipant", responseMessage.getIsParticipant());
                            editor.putString("idToken", account.getIdToken());
                            editor.apply();
                            finish();
                        } else {
                            // technical issue, shouldn't happen
                            signInClient.signOut();
                        }
                    }).start();
                }
            } catch (ApiException e) {
                Log.e(TAG, "GoogleAuthActivity.onActivityResult: Google sign in failure; message=" + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
