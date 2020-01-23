package inha.nslab.easytrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
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
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.server_google_client_id))
                .requestEmail()
                .build();
        signInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.googleSignInButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent signInIntent = signInClient.getSignInIntent();
                startActivityForResult(signInIntent, RC_SIGN_IN_WITH_GOOGLE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN_WITH_GOOGLE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    signInWithGoogleGrpc(account);
                    finish();
                }
                Log.e(TAG, "GoogleAuthActivity.onActivityResult: signed_in=Y");
            } catch (ApiException e) {
                Log.e(TAG, "GoogleAuthActivity.onActivityResult: Google sign in failure; message=" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void signInWithGoogleGrpc(final GoogleSignInAccount account) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
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
                Log.e(TAG, "GoogleAuthActivity.onCreate: grpc_signed_in=" + responseMessage.getDoneSuccessfully());

                if (responseMessage.getDoneSuccessfully()) {
                    finish();
                } else {
                    // technical issue, shouldn't happen
                    signInClient.signOut().addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Log.e(TAG, "GoogleAuthActivity.onComplete: grpc_signed_in failure; so signed out locally");
                        }
                    });
                }
            }
        });
        thread.start();
    }
}
