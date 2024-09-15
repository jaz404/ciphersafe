package com.ciphersafe;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
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

import androidx.work.Data;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import android.view.Window;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_SIGN_IN = 1001;
    private ListView passwordListView;

    private AppDatabase db;
    ImageView cloudImage, downloadImage, addPasswordImage;
    private static final String KEY_LAUNCH_COUNT = "LaunchCount";

    private GoogleSignInClient googleSignInClient;
    private Drive googleDriveService;
    TextView cipherSafeTextView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean isPolicyAccepted = sharedPreferences.getBoolean("PolicyAccepted", false);
        boolean isAuthenticated = sharedPreferences.getBoolean("IsAuthenticated", false);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("IsAuthenticated", false);
        editor.apply();

        setContentView(R.layout.activity_main);
        initializeViews();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        setupDatabase();
        if (!isPolicyAccepted) {
            // If not accepted, show the policy acceptance fragment
            showPolicyAcceptanceFragment();
        } else {
            // Check the length of the password list
            if (isPasswordListEmpty()) {
                onFirstUse();
            } else {
                authenticateAppStart();
            }
        }
    }
    private boolean isPasswordListEmpty() {
        setupDatabase();

        List<Password> passwords = db.passwordDao().getAll();
        int passwordListSize = passwords.size();

        Log.d("MainActivity", "Password list size: " + passwordListSize);
        Log.d("MainActivity", ""+passwords.isEmpty());

        return passwords.isEmpty();
    }
    private void onFirstUse() {
        // Perform the first-use setup tasks
        setupPasswordList();
        setupButtonListeners();
        passwordListView.setVisibility(View.VISIBLE);
        checkGoogleDriveSignInStatus();
    }
    private void showPolicyAcceptanceFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, new PolicyAcceptanceFragment());
        fragmentTransaction.commit();
    }
    public void onPolicyAccepted() {
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("PolicyAccepted", true);
        editor.apply();
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Optional: Move the authentication logic here if it's more reliable
        //passwordListView.setVisibility(View.GONE);
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
    }
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .requestEmail()
                .build();
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);

        String storedPassword = sharedPreferences.getString("EXCEL_PASSWORD", null);

        googleSignInClient = GoogleSignIn.getClient(this, gso);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            setupGoogleDriveService();

        } else {
            promptSignIn();
        }
    }

    private void checkGoogleDriveSignInStatus() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);

        String storedPassword = sharedPreferences.getString("EXCEL_PASSWORD", null);

        if (account != null) {
            // User is signed in
            cloudImage.setImageResource(R.drawable.cloudenable); // Set image indicating connected
        } else {
            // User is not signed in
            cloudImage.setImageResource(R.drawable.clouddisable); // Set image indicating not connected
        }
    }
    private void promptForPasswordAndStore() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Set Password for the File");

        // Inflate a custom view that includes a TextInputLayout for better design
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_set_password, null);
        builder.setView(dialogView);

        // Initialize TextInputLayout and EditText for password input
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputEditText passwordEditText = dialogView.findViewById(R.id.input_password);

        // Set up the dialog buttons
        builder.setPositiveButton("OK", null); // Set to null for manual validation handling
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                String password = passwordEditText.getText().toString().trim();

                // Validate the password input
                if (password.isEmpty()) {
                    passwordLayout.setError("Password cannot be empty");
                } else {
                    passwordLayout.setError(null); // Clear error

                    // Store the password securely in SharedPreferences
                    SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
                    sharedPreferences.edit().putString("EXCEL_PASSWORD", password).apply();
                    Toast.makeText(this, "Password saved successfully", Toast.LENGTH_SHORT).show();

                    // Start the backup worker now that the password is stored
                    startBackupWorker(password);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
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
                    .setApplicationName("CipherSafe")
                    .build();

            SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
            String storedPassword = sharedPreferences.getString("EXCEL_PASSWORD", null);

            if (storedPassword == null) {
                // Prompt for the password since it's not stored
                promptForPasswordAndStore();
            } else {
                // Start backup with stored password
                startBackupWorker(storedPassword);
            }
        }
    }


    private void initializeViews() {
        passwordListView = findViewById(R.id.password_list);
        cloudImage = findViewById(R.id.cloud_image);
        downloadImage = findViewById(R.id.downloadasexcel);
        addPasswordImage = findViewById(R.id.addpassword);
        cipherSafeTextView = findViewById(R.id.app_title);
        Toast.makeText(this, "Click on CipherSafe to open the User Manual", Toast.LENGTH_SHORT).show();

    }

    private void setupDatabase() {
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "passwords-db")
                .allowMainThreadQueries()
                .build();
    }

    private void startBackupWorker(String password) {
        saveDebugLogToFile("backup worker initiated");

        Data inputData = new Data.Builder()
                .putString("EXCEL_PASSWORD", password)
                .build();

        PeriodicWorkRequest backupWorkRequest =
                new PeriodicWorkRequest.Builder(BackupWorker.class, 15, TimeUnit.MINUTES)
                        .setInputData(inputData)
                        .build();

        WorkManager.getInstance(this).enqueue(backupWorkRequest);
    }
    private void saveDebugLogToFile(String logMessage) {
        String fileName = "BackupWorkerLog.txt"; // Single file to append logs

        ContentResolver resolver = getApplicationContext().getContentResolver();
        Uri externalContentUri = MediaStore.Files.getContentUri("external");

        // Query the Downloads folder to check if the file already exists
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
        String[] selectionArgs = new String[]{fileName};

        Uri fileUri = null;

        try (Cursor cursor = resolver.query(externalContentUri, new String[]{MediaStore.MediaColumns._ID}, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                // File exists, retrieve its URI
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                fileUri = Uri.withAppendedPath(externalContentUri, String.valueOf(id));
            }
        }

        if (fileUri == null) {
            // File doesn't exist, create a new one
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CipherSafe");

            fileUri = resolver.insert(externalContentUri, values);
        }

        try (OutputStream os = resolver.openOutputStream(fileUri, "wa")) { // "wa" is for write-append mode
            if (os != null) {
                os.write((logMessage + "\n").getBytes()); // Append log message with newline
                os.flush();
                Log.d("BackupWorker", "Debug log saved/updated in Downloads folder");
            }
        } catch (Exception e) {
            Log.e("BackupWorker", "Failed to save/update debug log", e);
        }
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
        if (position < adapter.getCount()) {  // Use adapter.getCount() instead of passwordDataList.size()
            String[] selectedAccountData = (String[]) adapter.getItem(position);
            if (selectedAccountData.length > 0) {
                showPasswordDetails(selectedAccountData[0]);  // Show details using account name
            } else {
                Log.e("MainActivity", "Selected account data array is empty or has insufficient elements.");
            }
        } else {
            Log.e("MainActivity", "Invalid position: " + position + " for adapter of size " + adapter.getCount());
        }
    });
}




//    private void loadPasswords() {
//        List<Password> passwords = db.passwordDao().getAll();
//
//        List<String[]> passwordDataList = new ArrayList<>();
//        for (Password password : passwords) {
//            passwordDataList.add(new String[]{password.accountName, password.username});
//        }
//
//        CustomAdapter adapter = (CustomAdapter) passwordListView.getAdapter();
//        if (adapter != null) {
//            adapter.updateData(passwordDataList);
//        } else {
//            Log.e("MainActivity", "Adapter is null when trying to notifyDataSetChanged()");
//        }
//    }
private void loadPasswords() {
    List<Password> passwords = db.passwordDao().getAll();

    List<String[]> passwordDataList = new ArrayList<>();
    for (Password password : passwords) {
        passwordDataList.add(new String[]{password.accountName, password.username});
    }

    CustomAdapter adapter = (CustomAdapter) passwordListView.getAdapter();
    if (adapter != null) {
        adapter.updateData(passwordDataList);  // Update the adapter's data and notify it to refresh
    } else {
        // If adapter is null, initialize it and set it to the ListView
        adapter = new CustomAdapter(this, passwordDataList);
        passwordListView.setAdapter(adapter);
    }
}


//    private void showPasswordDetails(String accountName) {
//        Password password = db.passwordDao().findByAccountName(accountName);
//
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle(accountName);
//
//        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_details, null);
//        TextView usernameTextView = dialogView.findViewById(R.id.username);
//        TextView passwordTextView = dialogView.findViewById(R.id.password);
//        TextView notesTextView = dialogView.findViewById(R.id.notes);
//
//        usernameTextView.setText(password.username);
//
//        String decryptedPassword;
//        try {
//            decryptedPassword = EncryptionUtils.decrypt(password.password);
//        } catch (Exception e) {
//            decryptedPassword = "[Decryption failed]";
//            Log.e("PasswordManager", "Decryption failed", e);
//        }
//        passwordTextView.setText(decryptedPassword);
//
//        notesTextView.setText(password.notes);
//
//        builder.setView(dialogView);
//        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
//        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
//
//        builder.show();
//    }

    public void showPasswordDetails(String accountName) {
        Password password = db.passwordDao().findByAccountName(accountName);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(accountName);

        // Inflate the custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password_details, null);
        //TextInputEditText accountNameEditText = dialogView.findViewById(R.id.dialog_account_name);
        TextInputEditText usernameEditText = dialogView.findViewById(R.id.dialog_username);
        TextInputEditText passwordEditText = dialogView.findViewById(R.id.dialog_password);
        TextInputEditText notesEditText = dialogView.findViewById(R.id.dialog_notes);

        // Set the values from the database
       // accountNameEditText.setText(password.accountName);
        usernameEditText.setText(password.username);

        String decryptedPassword;
        try {
            decryptedPassword = EncryptionUtils.decrypt(password.password);
        } catch (Exception e) {
            decryptedPassword = "[Decryption failed]";
            Log.e("PasswordManager", "Decryption failed", e);
        }
        passwordEditText.setText(decryptedPassword);
        notesEditText.setText(password.notes);

        // Set the custom view to the dialog
        builder.setView(dialogView);

        // Add Edit button
        builder.setNeutralButton("Edit", (dialog, which) -> {
            showEditPasswordDialog(password);
        });

        // Add Delete button
        builder.setNegativeButton("Delete", (dialog, which) -> {
            db.passwordDao().delete(password);
            loadPasswords();
            Toast.makeText(this, "Password deleted", Toast.LENGTH_SHORT).show();
        });

        // Add OK button
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

        builder.show();
    }


    private void showEditPasswordDialog(Password password) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Edit Password");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_password, null);
        builder.setView(dialogView);

        EditText accountNameEditText = dialogView.findViewById(R.id.dialog_account_name);
        EditText usernameEditText = dialogView.findViewById(R.id.dialog_username);
        EditText passwordEditText = dialogView.findViewById(R.id.dialog_password);
        EditText notesEditText = dialogView.findViewById(R.id.dialog_notes);

        // Pre-fill the fields with existing data
        accountNameEditText.setText(password.accountName);
        usernameEditText.setText(password.username);

        String decryptedPassword;
        try {
            decryptedPassword = EncryptionUtils.decrypt(password.password);
        } catch (Exception e) {
            decryptedPassword = "[Decryption failed]";
            Log.e("PasswordManager", "Decryption failed", e);
        }
        passwordEditText.setText(decryptedPassword);
        notesEditText.setText(password.notes);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String accountName = accountNameEditText.getText().toString();
            String username = usernameEditText.getText().toString();
            String passwordStr = passwordEditText.getText().toString();
            String notes = notesEditText.getText().toString();

            // Update the password entity
            password.accountName = accountName;
            password.username = username;
            try {
                password.password = EncryptionUtils.encrypt(passwordStr);
            } catch (Exception e) {
                Log.e("PasswordManager", "Encryption failed", e);
                Toast.makeText(this, "Failed to encrypt password", Toast.LENGTH_SHORT).show();
                return;
            }
            password.notes = notes;

            // Update the database
            db.passwordDao().update(password);

            // Refresh the list
            loadPasswords();

            Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.show();
    }


    private void addPassword(String accountName, String username, String password, String notes) {
        if (accountName == null || accountName.isEmpty()) {
            Toast.makeText(this, "Please enter the account name.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (username == null || username.isEmpty()) {
            Toast.makeText(this, "Please enter the username.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password == null || password.isEmpty()) {
            Toast.makeText(this, "Please enter the password.", Toast.LENGTH_SHORT).show();
            return;
        }

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

        // Update the adapter with the new data
        loadPasswords();
    }


    private void exportToExcel() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Set Password");
//
//        final EditText input = new EditText(this);
//        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
//        builder.setView(input);
//
//        builder.setPositiveButton("OK", (dialog, which) -> {
//            String password = input.getText().toString();
//            createExcelFile(password);
//        });
//        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
//
//        builder.show();
        SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        String password = sharedPreferences.getString("EXCEL_PASSWORD", null);

        if (password == null) {
            // If the password is not set, prompt the user to set it first
            promptForPasswordAndStore();
            return;
        }

        createExcelFile(password);
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
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CipherSafe");

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

//    private void showBackupOptionsDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Google Drive Backup");
//        boolean isBackupEnabled;
//        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
//        if (account != null) {
//            // User is signed in
//            isBackupEnabled = true;
//        } else {
//            // User is not signed in
//            isBackupEnabled = false;
//        }
//
//        // Set up the message
//        builder.setMessage("Would you like to enable Google Drive backups?");
//
//        // Set up the buttons
//        builder.setPositiveButton("Enable", (dialog, which) -> {
//            // Start the Google Drive sign-in process
//            setupGoogleSignIn();
//            dialog.dismiss();
//        });
//
//        builder.setNegativeButton("Disable", (dialog, which) -> {
//            if (isBackupEnabled) {
//                // Ensure googleSignInClient is initialized before attempting to sign out
//                if (googleSignInClient == null) {
//                    googleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN);
//                }
//
//                googleSignInClient.signOut()
//                        .addOnCompleteListener(this, task -> {
//                            // Handle sign-out
//                            cloudImage.setImageResource(R.drawable.clouddisable);
//                            Toast.makeText(MainActivity.this, "Google Drive backups disabled", Toast.LENGTH_SHORT).show();
//                        });
//            }
//        });
//
//        builder.setNeutralButton("Cancel", (dialog, which) -> {
//            dialog.dismiss();
//        });
//
//        AlertDialog dialog = builder.create();
//        dialog.show();
//    }
private void showBackupOptionsDialog() {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder.setTitle("Google Drive Backup");

    // Determine if backup is enabled
    GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
    boolean isBackupEnabled = (account != null); // Check if Google Drive backup is enabled

    // Inflate a custom view for the dialog message (optional, but can enhance visuals)
    View dialogView = getLayoutInflater().inflate(R.layout.dialog_backup_options, null);
    TextView messageTextView = dialogView.findViewById(R.id.backup_message);

    if (isBackupEnabled) {
        // If backup is enabled, offer the option to disable it
        messageTextView.setText("Google Drive backups are currently enabled. Would you like to disable them?");

        builder.setPositiveButton("Disable", (dialog, which) -> {
            // Ensure googleSignInClient is initialized before attempting to sign out
            if (googleSignInClient == null) {
                googleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN);
            }

            googleSignInClient.signOut()
                    .addOnCompleteListener(this, task -> {
                        // Handle sign-out
                        cloudImage.setImageResource(R.drawable.clouddisable);
                        Toast.makeText(MainActivity.this, "Google Drive backups disabled", Toast.LENGTH_SHORT).show();
                    });

            dialog.dismiss();
        });

    } else {
        // If backup is disabled, offer the option to enable it
        messageTextView.setText("Google Drive backups are currently disabled. Would you like to enable them?");

        builder.setPositiveButton("Enable", (dialog, which) -> {
            // Start the Google Drive sign-in process
            setupGoogleSignIn();
            dialog.dismiss();
        });
    }

    // Cancel button for both cases
    builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

    // Set the custom view for the dialog
    builder.setView(dialogView);

    // Show the dialog
    AlertDialog dialog = builder.create();
    dialog.show();
}

    private void authenticateAppStart() {
    //Log.d("MainActivity", "Auth should be done");

    SharedPreferences sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
    boolean isAuthenticated = sharedPreferences.getBoolean("IsAuthenticated", false);

    if (!isAuthenticated) {
        BiometricManager biometricManager = BiometricManager.from(this);

        // Check biometric capability and log the result
        int canAuthenticateResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        Log.d("MainActivity", "Can Authenticate Result: " + canAuthenticateResult);

        if (canAuthenticateResult != BiometricManager.BIOMETRIC_SUCCESS) {
            Log.w("FaceAuth", "Face authentication is not available on this device");
            Toast.makeText(this, "Face authentication is not available. Falling back to PIN/pattern.", Toast.LENGTH_LONG).show();
        }

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Authenticate to access the app")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |  // Strong biometrics like fingerprint or face
                                BiometricManager.Authenticators.BIOMETRIC_WEAK |    // Weaker biometrics
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL   // PIN, pattern, or password
                )
                .build();

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Log.e("FaceAuth", "Authentication error: " + errorCode + " " + errString);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                        Toast.makeText(MainActivity.this, "No face data enrolled. Please set up face recognition in your device settings.", Toast.LENGTH_LONG).show();
                    }
                    finish(); // Close the app if authentication fails
                });
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                runOnUiThread(() -> {
                    if (result.getAuthenticationType() == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                        Toast.makeText(MainActivity.this, "Face authentication succeeded", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Device credential authentication succeeded", Toast.LENGTH_SHORT).show();
                    }
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean("IsAuthenticated", true);
                    editor.apply();

                    setupDatabase();
                    setupPasswordList();
                    setupButtonListeners();
                    passwordListView.setVisibility(View.VISIBLE);
                    checkGoogleDriveSignInStatus();
                });
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Log.e("FaceAuth", "Authentication failed");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Face authentication failed. Please try again or use your PIN/pattern.", Toast.LENGTH_SHORT).show();
                });
            }
        });

        try {
            Log.d("MainActivity", "Starting biometric prompt");
            biometricPrompt.authenticate(promptInfo);
        } catch (Exception e) {
            Log.e("FaceAuth", "Exception during authentication", e);
            Toast.makeText(this, "Error during authentication: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish(); // Close the app if authentication fails
        }
    } else {
        Log.d("MainActivity", "Already authenticated, initializing app");
        setupDatabase();
        setupPasswordList();
        setupButtonListeners();
        passwordListView.setVisibility(View.VISIBLE);
        checkGoogleDriveSignInStatus();
    }
}


    private void setupButtonListeners() {
        addPasswordImage.setOnClickListener(v -> showAddPasswordDialog());

        //exportPasswordsButton.setOnClickListener(v -> authenticateAndExportToExcel());
        downloadImage.setOnClickListener(v -> exportToExcel());

        cloudImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //setupGoogleSignIn();
                showBackupOptionsDialog();
            }
        });
        cipherSafeTextView.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, UserManualActivity.class);
                startActivity(intent);
            });
    }
    private void showAddPasswordDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Add Password");

        // Inflate the dialog layout with Material TextInputLayouts
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_password, null);
        builder.setView(dialogView);

        // Initialize the TextInputLayouts and EditTexts
        TextInputLayout accountNameLayout = dialogView.findViewById(R.id.layout_account_name);
        TextInputLayout usernameLayout = dialogView.findViewById(R.id.layout_username);
        TextInputLayout passwordLayout = dialogView.findViewById(R.id.layout_password);
        TextInputLayout notesLayout = dialogView.findViewById(R.id.layout_notes);

        EditText accountNameEditText = dialogView.findViewById(R.id.dialog_account_name);
        EditText usernameEditText = dialogView.findViewById(R.id.dialog_username);
        EditText passwordEditText = dialogView.findViewById(R.id.dialog_password);
        EditText notesEditText = dialogView.findViewById(R.id.dialog_notes);

        // Automatically focus the first field
        accountNameEditText.requestFocus();

        // Set up the dialog buttons
        builder.setPositiveButton("Add", null);  // Set to null to override later
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Button addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            addButton.setOnClickListener(view -> {
                // Get the input values
                String accountName = accountNameEditText.getText().toString().trim();
                String username = usernameEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();
                String notes = notesEditText.getText().toString().trim();

                // Validate inputs and show errors in TextInputLayouts
                if (accountName.isEmpty()) {
                    accountNameLayout.setError("Please enter the account name");
                } else if (username.isEmpty()) {
                    usernameLayout.setError("Please enter the username");
                } else if (password.isEmpty()) {
                    passwordLayout.setError("Please enter the password");
                } else {
                    // Clear any previous errors
                    accountNameLayout.setError(null);
                    usernameLayout.setError(null);
                    passwordLayout.setError(null);

                    // Add the password to the database
                    addPassword(accountName, username, password, notes);
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }



//    private void authenticateAndAddPassword() {
//        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
//                .setTitle("Biometric Authentication")
//                .setSubtitle("Authenticate to add password")
//                .setNegativeButtonText("Cancel")
//                .build();
//
//        Executor executor = ContextCompat.getMainExecutor(this);
//        BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
//            @Override
//            public void onAuthenticationError(int errorCode, CharSequence errString) {
//                super.onAuthenticationError(errorCode, errString);
//                Toast.makeText(MainActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
//            }
//
//            @Override
//            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
//                super.onAuthenticationSucceeded(result);
//                //addPassword();
//                passwordListView.setVisibility(View.VISIBLE);
//            }
//
//            @Override
//            public void onAuthenticationFailed() {
//                super.onAuthenticationFailed();
//                Toast.makeText(MainActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
//            }
//        });
//
//        biometricPrompt.authenticate(promptInfo);
//    }

   // private void authenticateAndExportToExcel() {
//        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
//                .setTitle("Biometric Authentication")
//                .setSubtitle("Authenticate to export passwords")
//                .setNegativeButtonText("Cancel")
//                .build();
//
//        Executor executor = ContextCompat.getMainExecutor(this);
//        BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
//            @Override
//            public void onAuthenticationError(int errorCode, CharSequence errString) {
//                super.onAuthenticationError(errorCode, errString);
//                Toast.makeText(MainActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
//            }
//
//            @Override
//            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
//                super.onAuthenticationSucceeded(result);
//                exportToExcel();
//                passwordListView.setVisibility(View.VISIBLE);
//            }
//
//            @Override
//            public void onAuthenticationFailed() {
//                super.onAuthenticationFailed();
//                Toast.makeText(MainActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
//            }
//        });
//
//        biometricPrompt.authenticate(promptInfo);
  //      exportToExcel();
 //   }
}
