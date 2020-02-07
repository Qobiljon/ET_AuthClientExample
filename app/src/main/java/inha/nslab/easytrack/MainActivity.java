package inha.nslab.easytrack;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.util.Calendar;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class MainActivity extends AppCompatActivity {

    static final String TAG = "ET_AUTH_EXAMPLE_APP";
    static final int RC_OPEN_AUTH_ACTIVITY = 100;
    static final int RC_SIGN_IN_WITH_GOOGLE = 101;
    private TextView logTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logTextView = findViewById(R.id.logTextView);

        SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
        if (prefs.getInt("userId", -1) == -1) {
            logTextView.setText(getString(R.string.account, "N/A", -1, false));
            startActivityForResult(new Intent(this, GoogleAuthActivity.class), RC_OPEN_AUTH_ACTIVITY);
        } else {
            new Thread(() -> {
                ManagedChannel channel = ManagedChannelBuilder.forAddress(
                        getString(R.string.grpc_server_ip),
                        Integer.parseInt(getString(R.string.grpc_server_port))
                ).usePlaintext().build();

                String idToken = prefs.getString("idToken", null);

                ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                EtService.LoginWithGoogleIdRequestMessage requestMessage = EtService.LoginWithGoogleIdRequestMessage.newBuilder()
                        .setIdToken(idToken)
                        .build();
                EtService.LoginWithGoogleIdResponseMessage responseMessage = stub.loginWithGoogleId(requestMessage);
                if (responseMessage.getDoneSuccessfully())
                    runOnUiThread(() -> logTextView.setText(getString(
                            R.string.account,
                            prefs.getString("email", null),
                            prefs.getInt("userId", -1),
                            prefs.getBoolean("isParticipant", false)
                    )));
                else
                    runOnUiThread(() -> {
                        logTextView.setText(getString(R.string.account, "N/A", -1, false));
                        startActivityForResult(new Intent(this, GoogleAuthActivity.class), RC_OPEN_AUTH_ACTIVITY);
                    });
            }).start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_OPEN_AUTH_ACTIVITY) {
            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);

            if (prefs.getInt("userId", -1) == -1)
                logTextView.setText(getString(R.string.account, "N/A", -1, false));
            else
                logTextView.setText(getString(
                        R.string.account,
                        prefs.getString("email", null),
                        prefs.getInt("userId", -1),
                        prefs.getBoolean("isParticipant", false)
                ));
        }
    }

    public void logoutClick(View view) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.server_google_client_id))
                    .requestEmail()
                    .build();
            GoogleSignInClient signInClient = GoogleSignIn.getClient(this, gso);
            signInClient.signOut().addOnCompleteListener(this, task -> {
                logTextView.setText(getString(R.string.account, "N/A", -1, false));

                SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.clear();
                editor.apply();

                startActivityForResult(new Intent(this, GoogleAuthActivity.class), RC_OPEN_AUTH_ACTIVITY);
            });
        }
    }

    public void submitDataClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);
            int dataSource = 0;
            String values = "0.0,1.0,2.0";
            long timestamp = Calendar.getInstance().getTimeInMillis();

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.SubmitDataRequestMessage requestMessage = EtService.SubmitDataRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .setDataSource(dataSource)
                    .setValues(values)
                    .setTimestamp(timestamp)
                    .build();
            EtService.DefaultResponseMessage responseMessage = stub.submitData(requestMessage);

            if (responseMessage.getDoneSuccessfully())
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Data submitted successfully", Toast.LENGTH_SHORT).show());
            else
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to submit data", Toast.LENGTH_SHORT).show());

            channel.shutdown();
        }).start();
    }

    public void submitHeartbeatClick(View view) {
        new Thread(() -> {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(
                    getString(R.string.grpc_server_ip),
                    Integer.parseInt(getString(R.string.grpc_server_port))
            ).usePlaintext().build();

            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            int userId = prefs.getInt("userId", -1);
            String email = prefs.getString("email", null);

            ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
            EtService.SubmitHeartbeatRequestMessage requestMessage = EtService.SubmitHeartbeatRequestMessage.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .build();
            EtService.DefaultResponseMessage responseMessage = stub.submitHeartbeat(requestMessage);

            if (responseMessage.getDoneSuccessfully())
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Heartbeat submitted successfully", Toast.LENGTH_SHORT).show());
            else
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to submit heartbeat", Toast.LENGTH_SHORT).show());

            channel.shutdown();
        }).start();
    }
}
