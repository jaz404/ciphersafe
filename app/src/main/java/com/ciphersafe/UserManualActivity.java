package com.ciphersafe;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class UserManualActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_manual);
        Button closeButton = findViewById(R.id.close_button);

        TextView userManualText = findViewById(R.id.user_manual_text);

        String userManualContent = "User Manual for CipherSafe Password Manager App\n" +
                "1. Introduction\n" +
                "CipherSafe is a secure and intuitive password manager designed to help you store and manage your passwords. This app allows you to encrypt your passwords, back them up to Google Drive, and export them to an Excel file. It also includes biometric authentication for added security.\n" +
                "\n" +
                "2. Getting Started\n" +
                "2.1. Installation\n" +
                "Download and install the CipherSafe app from the app store.\n" +
                "2.2. First Launch\n" +
                "Upon first launching the app, you will be prompted to accept the Privacy Policy. The app will not allow access until you accept the policy.\n" +
                "After accepting the policy, you will need to set up the app, including database initialization and Google Drive connection.\n" +
                "2.3. Authentication\n" +
                "Biometric authentication (fingerprint or face recognition) is required to access the app after the initial setup. The app will prompt for biometric authentication every time it is opened after the first launch.\n" +
                "3. Managing Passwords\n" +
                "3.1. Adding a New Password\n" +
                "Tap the \"+\" icon at the bottom of the screen to add a new password.\n" +
                "A dialog will appear asking you to input the following details:\n" +
                "Account Name (required)\n" +
                "Username (required)\n" +
                "Password (required)\n" +
                "Notes (optional)\n" +
                "If any of the required fields are empty, the app will display a Toast notification asking you to fill in the missing information. The dialog will remain open until all required fields are filled.\n" +
                "Once all required fields are completed, tap \"Add\" to save the password.\n" +
                "3.2. Viewing Password Details\n" +
                "Your stored passwords are displayed in a list. Tap on any password in the list to view its details.\n" +
                "The details dialog will show:\n" +
                "Account Name (required, max 30 characters)\n" +
                "Username (required, max 50 characters)\n" +
                "Decrypted Password (required, max 50 characters)\n" +
                "Notes (optional, max 500 characters)\n" +
                "You can edit or delete the password directly from this dialog.\n" +
                "3.3. Editing a Password\n" +
                "In the password details dialog, tap the \"Edit\" button to modify the password details.\n" +
                "After editing, tap \"Save\" to update the password.\n" +
                "3.4. Deleting a Password\n" +
                "In the password details dialog, tap the \"Delete\" button to remove the password from the app.\n" +
                "4. Backup and Restore\n" +
                "4.1. Google Drive Backup\n" +
                "To enable Google Drive backups, tap the cloud icon at the top of the main screen.\n" +
                "The app will prompt you to sign in with your Google account and set a password for the backup.\n" +
                "Once connected, the cloud icon will indicate the status of the Google Drive connection.\n" +
                "4.2. Exporting Passwords to Excel\n" +
                "Tap the download icon at the top of the main screen to export your passwords to an Excel file.\n" +
                "You will be prompted to set a password to encrypt the Excel file.\n" +
                "The file will be saved in your device's \"Download\" directory and can also be uploaded to Google Drive if connected.\n" +
                "5. Security Features\n" +
                "5.1. Biometric Authentication\n" +
                "The app supports biometric authentication (fingerprint or face recognition) to provide an additional layer of security.\n" +
                "You can enable this feature during the initial setup or later through the app settings.\n" +
                "5.2. Encryption\n" +
                "All passwords are stored securely using encryption, ensuring that your sensitive data is protected.\n" +
                "6. Settings and Customization\n" +
                "6.1. Managing Privacy Policy\n" +
                "You can view the Privacy Policy at any time through the app’s settings.\n" +
                "The app will ensure that you have accepted the Privacy Policy before using the app.\n" +
                "6.2. Periodic Backup\n" +
                "The app supports periodic backups of your password data to Google Drive. This feature can be customized or disabled in the settings.\n" +
                "7. Troubleshooting\n" +
                "7.1. Forgotten Password\n" +
                "If you forget the password used for Google Drive backups, you will need to set a new password through the app settings.\n" +
                "7.2. Biometric Authentication Issues\n" +
                "Ensure that biometric authentication is enabled on your device and that your biometric data is properly enrolled.\n" +
                "If biometric authentication fails, you can use device credentials (PIN/Pattern) to access the app.";

        userManualText.setText(userManualContent);
        closeButton.setOnClickListener(v -> finish());
    }
}
