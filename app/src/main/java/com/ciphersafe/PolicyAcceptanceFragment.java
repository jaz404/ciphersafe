package com.ciphersafe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

public class PolicyAcceptanceFragment extends Fragment {

    private CheckBox acceptCheckBox;
    private Button viewPolicyButton, continueButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_policy_acceptance, container, false);

        acceptCheckBox = view.findViewById(R.id.acceptCheckBox);
        viewPolicyButton = view.findViewById(R.id.viewPolicyButton);
        continueButton = view.findViewById(R.id.continueButton);

        // Handle View Policy button click
        viewPolicyButton.setOnClickListener(v -> {
            // Replace this fragment with the WebView fragment to display the privacy policy
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new PolicyWebViewFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // Handle Continue button click
        continueButton.setOnClickListener(v -> {
            if (acceptCheckBox.isChecked()) {
                // Save the consent in SharedPreferences
                SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean("PolicyAccepted", true);
                editor.apply();

                // Remove the PolicyAcceptanceFragment and navigate to MainFragment or main content
                removeFragment();
            } else {
                Toast.makeText(getContext(), "Please accept the privacy policy to continue.", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void removeFragment() {
        if (isAdded()) {
            FragmentManager fragmentManager = getParentFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            // Remove the PolicyAcceptanceFragment
            fragmentTransaction.remove(this);
            fragmentTransaction.commit();

            // Optionally replace with another fragment or activity
            // Example: Replace with MainFragment
            Intent intent = new Intent(getActivity(), MainActivity.class);
            startActivity(intent);
        }
    }
}
