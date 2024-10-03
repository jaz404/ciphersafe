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

/**
 * PolicyAcceptanceFragment is a fragment that prompts the user to accept the privacy policy of the application.
 * It includes a checkbox for accepting the policy, a button to view the policy, and a continue button to proceed after acceptance.
 */
public class PolicyAcceptanceFragment extends Fragment {

    private CheckBox acceptCheckBox;
    private Button viewPolicyButton, continueButton;

    /**
     * Called to have the fragment instantiate its user interface view. In this case, it inflates the policy acceptance layout
     * and sets up the behavior for the "View Policy" and "Continue" buttons.
     *
     * @param inflater  The LayoutInflater object that can be used to inflate views in the fragment.
     * @param container The parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return The View for the fragment's UI, or null.
     */
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

                // Remove the PolicyAcceptanceFragment and navigate to the main content or fragment
                removeFragment();
            } else {
                // Show a message if the checkbox is not checked
                Toast.makeText(getContext(), "Please accept the privacy policy to continue.", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    /**
     * Removes the PolicyAcceptanceFragment and optionally replaces it with another fragment or starts the main activity.
     * If the policy is accepted, this method is used to proceed to the main content of the app.
     */
    private void removeFragment() {
        if (isAdded()) {
            FragmentManager fragmentManager = getParentFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

            // Remove the PolicyAcceptanceFragment
            fragmentTransaction.remove(this);
            fragmentTransaction.commit();

            // Notify the MainActivity that the policy has been accepted
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).onPolicyAccepted();
            }

            // Optionally replace with another fragment or start the main activity
            Intent intent = new Intent(getActivity(), MainActivity.class);
            startActivity(intent);
        }
    }
}
