package inha.nslab.easytrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

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

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            // not signed in (N)
            Log.e(TAG, "MainActivity.onCreate: signed_in=N");
            logTextView.setText(getString(R.string.account, "N/A", -1));
            openLoginActivity();
        } else {
            // previously signed in (Y)
            Log.e(TAG, "MainActivity.onCreate: signed_in=Y");
            SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            logTextView.setText(getString(R.string.account, account.getEmail(), prefs.getInt("userId", -1)));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_OPEN_AUTH_ACTIVITY) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account == null) {
                // not signed in (N)
                Log.e(TAG, "MainActivity.onActivityResult: signed_in=N");
                openLoginActivity();
            } else {
                // signed in (Y)
                Log.e(TAG, "MainActivity.onActivityResult: signed_in=Y");
                SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
                logTextView.setText(getString(R.string.account, account.getEmail(), prefs.getInt("userId", -1)));
            }
        }
    }

    public void logout(View view) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.server_google_client_id))
                    .requestEmail()
                    .build();
            GoogleSignInClient signInClient = GoogleSignIn.getClient(this, gso);
            signInClient.signOut().addOnCompleteListener(this, new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    Log.e(TAG, "MainActivity.logout: signed_in=N");
                    logTextView.setText(getString(R.string.account, "N/A", -1));

                    SharedPreferences prefs = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.clear();
                    editor.apply();

                    openLoginActivity();
                }
            });
        }
    }

    private void openLoginActivity() {
        Intent intent = new Intent(this, GoogleAuthActivity.class);
        startActivityForResult(intent, RC_OPEN_AUTH_ACTIVITY);
    }
}
