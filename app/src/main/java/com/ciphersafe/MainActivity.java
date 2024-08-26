package com.ciphersafe;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.View;

import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.room.Room;

import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SIGN_IN = 1001;
    private ListView passwordListView;
    private AppDatabase db;
//    private List<String> passwordList;
//    private ArrayAdapter<String> adapter;
    ImageView cloudImage, downloadImage, addPasswordImage;

    private GoogleSignInClient googleSignInClient;
    private Drive googleDriveService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isPolicyAccepted = sharedPreferences.getBoolean("PolicyAccepted", false);
        setContentView(R.layout.activity_main);
        initializeViews();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        if (!isPolicyAccepted) {
            // If not accepted, show the policy acceptance fragment

            fragmentTransaction.replace(R.id.fragment_container, new PolicyAcceptanceFragment());

        } else {


            setupDatabase();
            setupPasswordList();
            setupButtonListeners();

            authenticateAppStart();
            checkGoogleDriveSignInStatus();
             }

        fragmentTransaction.commit();

    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            setupGoogleDriveService();
            startBackupWorker();
        } else {
            promptSignIn();
        }
    }

    private void checkGoogleDriveSignInStatus() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            // User is signed in
            cloudImage.setImageResource(R.drawable.cloudenable); // Set image indicating connected
        } else {
            // User is not signed in
            cloudImage.setImageResource(R.drawable.clouddisable); // Set image indicating not connected
        }
    }
    private void promptSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, REQUEST_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            setupGoogleDriveService();
            cloudImage.setImageResource(R.drawable.cloudenable);
            Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show();

        } catch (ApiException e) {
            Log.w("GoogleSignIn", "signInResult:failed code=" + e.getStatusCode());
        }
    }

    private void setupGoogleDriveService() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    this, Collections.singleton(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccount(account.getAccount());
            googleDriveService = new Drive.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory.getDefaultInstance(), // Changed from JacksonFactory to GsonFactory
                    credential)
                    .setApplicationName("PasswordManager")
                    .build();
        }
    }


    private void initializeViews() {
        //accountNameEditText = findViewById(R.id.account_name);
        //usernameEditText = findViewById(R.id.username);
        //passwordEditText = findViewById(R.id.password);
        //notesEditText = findViewById(R.id.notes);
        //ddPasswordButton = findViewById(R.id.add_password);
        //exportPasswordsButton = findViewById(R.id.export_button);
        passwordListView = findViewById(R.id.password_list);
        cloudImage = findViewById(R.id.cloud_image);
        downloadImage = findViewById(R.id.downloadasexcel);
        addPasswordImage = findViewById(R.id.addpassword);



    }

    private void setupDatabase() {
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "passwords-db")
                .allowMainThreadQueries()
                .build();
    }

    private void startBackupWorker() {
        PeriodicWorkRequest backupWorkRequest =
                new PeriodicWorkRequest.Builder(BackupWorker.class, 7, TimeUnit.DAYS)
                        .build();

        WorkManager.getInstance(this).enqueue(backupWorkRequest);
    }
private void setupPasswordList() {
    List<Password> passwords = db.passwordDao().getAll();

    List<String[]> passwordDataList = new ArrayList<>();
    for (Password password : passwords) {
        passwordDataList.add(new String[]{password.accountName, password.username});
    }

    // Initialize the adapter with the data
    CustomAdapter adapter = new CustomAdapter(this, passwordDataList);
    passwordListView.setAdapter(adapter);

    passwordListView.setOnItemClickListener((parent, view, position, id) -> {
        String[] selectedAccountData = passwordDataList.get(position);
        showPasswordDetails(selectedAccountData[0]);  // Show details using account name
    });
}



    private void loadPasswords() {
        List<Password> passwords = db.passwordDao().getAll();

        List<String[]> passwordDataList = new ArrayList<>();
        for (Password password : passwords) {
            passwordDataList.add(new String[]{password.accountName, password.username});
        }

        CustomAdapter adapter = (CustomAdapter) passwordListView.getAdapter();
        if (adapter != null) {
            adapter.updateData(passwordDataList);
        } else {
            Log.e("MainActivity", "Adapter is null when trying to notifyDataSetChanged()");
        }
    }


    private void showPasswordDetails(String accountName) {
        Password password = db.passwordDao().findByAccountName(accountName);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(accountName);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_details, null);
        TextView usernameTextView = dialogView.findViewById(R.id.username);
        TextView passwordTextView = dialogView.findViewById(R.id.password);
        TextView notesTextView = dialogView.findViewById(R.id.notes);

        usernameTextView.setText(password.username);

        String decryptedPassword;
        try {
            decryptedPassword = EncryptionUtils.decrypt(password.password);
        } catch (Exception e) {
            decryptedPassword = "[Decryption failed]";
            Log.e("PasswordManager", "Decryption failed", e);
        }
        passwordTextView.setText(decryptedPassword);

        notesTextView.setText(password.notes);

        builder.setView(dialogView);
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.show();
    }

private void addPassword(String accountName, String username, String password, String notes) {
    String encryptedPassword = null;
    try {
        encryptedPassword = EncryptionUtils.encrypt(password);
    } catch (Exception e) {
        Log.e("PasswordManager", "Encryption failed", e);
    }
    if (encryptedPassword == null) {
        Toast.makeText(this, "Failed to encrypt password", Toast.LENGTH_SHORT).show();
        return;
    }

    Password passwordEntity = new Password();
    passwordEntity.accountName = accountName;
    passwordEntity.username = username;
    passwordEntity.password = encryptedPassword;
    passwordEntity.notes = notes;

    db.passwordDao().insert(passwordEntity);


    loadPasswords();

}

    private void exportToExcel() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Password");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String password = input.getText().toString();
            createExcelFile(password);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void createExcelFile(String password) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Passwords");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Account Name");
        headerRow.createCell(1).setCellValue("Username");
        headerRow.createCell(2).setCellValue("Password");
        headerRow.createCell(3).setCellValue("Notes");

        List<Password> passwords = db.passwordDao().getAll();
        int rowIndex = 1;
        for (Password passwordObj : passwords) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(passwordObj.accountName);
            row.createCell(1).setCellValue(passwordObj.username);
            String decryptedPassword = null;
            try {
                decryptedPassword = EncryptionUtils.decrypt(passwordObj.password);
            } catch (Exception e) {
                Log.e("PasswordManager", "Decryption failed", e);
            }
            row.createCell(2).setCellValue(decryptedPassword != null ? decryptedPassword : "[Decryption failed]");
            row.createCell(3).setCellValue(passwordObj.notes);
        }

        // Generate a unique filename using timestamp
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = "Passwords_" + timestamp + ".xlsx";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/");

        ContentResolver resolver = getContentResolver();
        Uri externalContentUri = MediaStore.Files.getContentUri("external");
        Uri uri = resolver.insert(externalContentUri, values);

        try (POIFSFileSystem fs = new POIFSFileSystem();
             OutputStream os = resolver.openOutputStream(uri)) {

            EncryptionInfo info = new EncryptionInfo(EncryptionMode.standard);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword(password);

            try (OutputStream encOS = encryptor.getDataStream(fs)) {
                workbook.write(encOS);
            }

            fs.writeFilesystem(os);
            Toast.makeText(this, "Exported to Excel successfully as " + fileName, Toast.LENGTH_SHORT).show();

            // Now upload the file to Google Drive
            // uploadFileToGoogleDrive(uri);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to export", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBackupOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Google Drive Backup");
        boolean isBackupEnabled;
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            // User is signed in
            isBackupEnabled = true;
        } else {
            // User is not signed in
            isBackupEnabled = false;
        }

        // Set up the message
        builder.setMessage("Would you like to enable Google Drive backups?");

        // Set up the buttons
        builder.setPositiveButton("Enable", (dialog, which) -> {
            // Start the Google Drive sign-in process
            setupGoogleSignIn();
            dialog.dismiss();
        });

        builder.setNegativeButton("Disable", (dialog, which) -> {

            if (isBackupEnabled) {
                googleSignInClient.signOut()
                        .addOnCompleteListener(this, task -> {
                            // Handle sign-out
                            cloudImage.setImageResource(R.drawable.clouddisable);
                            Toast.makeText(MainActivity.this, "Google Drive backups disabled", Toast.LENGTH_SHORT).show();
                        });
            }
        });

        builder.setNeutralButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void authenticateAppStart() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Authenticate to access the app")
                .setNegativeButtonText("Cancel")
                .build();

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(MainActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                initializeViews();
                setupDatabase();
                setupPasswordList();
                setupButtonListeners();
                passwordListView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(MainActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });

        biometricPrompt.authenticate(promptInfo);
    }


    private void setupButtonListeners() {
        //addPasswordButton.setOnClickListener(v -> authenticateAndAddPassword());
        //addPasswordImage.setOnClickListener(v -> authenticateAndAddPassword());
        addPasswordImage.setOnClickListener(v -> showAddPasswordDialog());

        //exportPasswordsButton.setOnClickListener(v -> authenticateAndExportToExcel());
        downloadImage.setOnClickListener(v -> authenticateAndExportToExcel());

        cloudImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //setupGoogleSignIn();
                showBackupOptionsDialog();
            }
        });
    }
    private void showAddPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Password");

        // Inflate the dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_password, null);
        builder.setView(dialogView);

        // Initialize the EditTexts in the dialog
        EditText accountNameEditText = dialogView.findViewById(R.id.dialog_account_name);
        EditText usernameEditText = dialogView.findViewById(R.id.dialog_username);
        EditText passwordEditText = dialogView.findViewById(R.id.dialog_password);
        EditText notesEditText = dialogView.findViewById(R.id.dialog_notes);

        // Set up the dialog buttons
        builder.setPositiveButton("Add", (dialog, which) -> {
            // Get the input values
            String accountName = accountNameEditText.getText().toString();
            String username = usernameEditText.getText().toString();
            String password = passwordEditText.getText().toString();
            String notes = notesEditText.getText().toString();

            // Call the method to add password
            addPassword(accountName, username, password, notes);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void authenticateAndAddPassword() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Authenticate to add password")
                .setNegativeButtonText("Cancel")
                .build();

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(MainActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                //addPassword();
                passwordListView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(MainActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });

        biometricPrompt.authenticate(promptInfo);
    }

    private void authenticateAndExportToExcel() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Authenticate to export passwords")
                .setNegativeButtonText("Cancel")
                .build();

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(MainActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                exportToExcel();
                passwordListView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(MainActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });

        biometricPrompt.authenticate(promptInfo);
    }
}
