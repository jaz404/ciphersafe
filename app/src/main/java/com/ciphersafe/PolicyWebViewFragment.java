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

public class PolicyWebViewFragment extends Fragment {

    private TextView privacyPolicyTextView;
    private Button closePolicyButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_policy_webview, container, false);

        privacyPolicyTextView = view.findViewById(R.id.privacyPolicyTextView);
        closePolicyButton = view.findViewById(R.id.closePolicyButton);

        // Load the privacy policy text
        String privacyPolicy = loadPrivacyPolicyFromAssets();
        privacyPolicyTextView.setText(privacyPolicy);

        // Handle Close button click
        closePolicyButton.setOnClickListener(v -> {
            // Return to previous fragment
            getParentFragmentManager().popBackStack();
        });

        return view;
    }

    private String loadPrivacyPolicyFromAssets() {
        StringBuilder text = new StringBuilder();
        try {
            InputStream inputStream = requireContext().getAssets().open("privacypolicy.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text.toString();
    }
}
