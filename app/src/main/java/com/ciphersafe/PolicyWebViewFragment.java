package com.ciphersafe;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * PolicyWebViewFragment is a fragment that displays the privacy policy text loaded from an asset file.
 * It provides a close button to allow the user to return to the previous fragment.
 */
public class PolicyWebViewFragment extends Fragment {

    private TextView privacyPolicyTextView;
    private Button closePolicyButton;

    /**
     * Called to have the fragment instantiate its user interface view. In this case, it inflates the privacy policy layout,
     * loads the privacy policy text from an asset file, and handles user interactions for the close button.
     *
     * @param inflater  The LayoutInflater object that can be used to inflate views in the fragment.
     * @param container The parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return The View for the fragment's UI, or null.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_policy_webview, container, false);

        privacyPolicyTextView = view.findViewById(R.id.privacyPolicyTextView);
        closePolicyButton = view.findViewById(R.id.closePolicyButton);

        // Load the privacy policy text from the assets folder
        String privacyPolicy = loadPrivacyPolicyFromAssets();
        privacyPolicyTextView.setText(privacyPolicy);

        // Handle the Close button click
        closePolicyButton.setOnClickListener(v -> {
            // Return to the previous fragment
            getParentFragmentManager().popBackStack();
        });

        return view;
    }

    /**
     * Loads the privacy policy text from the "privacypolicy.txt" file stored in the assets folder.
     *
     * @return A string containing the privacy policy text, or an empty string if an error occurs.
     */
    private String loadPrivacyPolicyFromAssets() {
        StringBuilder text = new StringBuilder();
        try {
            // Open the privacy policy text file from the assets folder
            InputStream inputStream = requireContext().getAssets().open("privacypolicy.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;

            // Read each line of the privacy policy and append it to the StringBuilder
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
            reader.close();
        } catch (IOException e) {
            // Handle any IOExceptions that may occur during file reading
            e.printStackTrace();
        }

        return text.toString();
    }
}
