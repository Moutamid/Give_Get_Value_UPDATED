package com.moutamid.givegetvalue.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.fxn.stash.Stash;
import com.moutamid.givegetvalue.Constants;
import com.moutamid.givegetvalue.MainActivity;
import com.moutamid.givegetvalue.R;
import com.moutamid.givegetvalue.databinding.FragmentMasterBinding;

import java.util.Arrays;

public class MasterFragment extends Fragment {
    FragmentMasterBinding binding;
    private static final String TAG = "MasterFragment";
    public MasterFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentMasterBinding.inflate(getLayoutInflater(), container, false);

        ArrayAdapter<CharSequence> spinAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.type_array, R.layout.dropdown_layout);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.typeSpinner.setAdapter(spinAdapter);

        binding.typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedItem = parent.getItemAtPosition(position).toString().trim();
                float value = Stash.getFloat(selectedItem, 0);
                binding.balance.setText("Balance for " + selectedItem + ": " + String.format("%.2f", value));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        binding.checkPassword.setOnClickListener(v -> {
            String str = binding.password.getEditText().getText().toString().trim();
            if (!str.isEmpty()){
                boolean isMatch = Arrays.asList(Constants.PASSWORDS).contains(str);
                if (isMatch) {
                    binding.passwordLayout.setVisibility(View.GONE);
                    binding.mainLayout.setVisibility(View.VISIBLE);
                    binding.password.getEditText().setText("");
                } else {
                    Toast.makeText(requireContext(), "Password not match", Toast.LENGTH_SHORT).show();
                }
            }
        });

        binding.add.setOnClickListener(v -> {
            if (valid()) {
                String value = binding.amount.getEditText().getText().toString();
                String valueType = binding.typeSpinner.getSelectedItem().toString().trim();
                float valueToAdd = Float.parseFloat(value);
                addValueToBalance(valueType, valueToAdd);
            }
        });

        return binding.getRoot();
    }

    private void addValueToBalance(String valueType, float valueToAdd) {
        float categoryBalance = Stash.getFloat(valueType, 0);
        Log.d(TAG, "categoryBalance: " + categoryBalance);
        categoryBalance += valueToAdd;
        Stash.put(valueType, categoryBalance);
        String formattedBalance = String.format("%.2f", categoryBalance);
        Log.d(TAG, "addValueToBalance: " + valueToAdd);
        binding.balance.setText("Balance for " + valueType + ": " + formattedBalance);
        Toast.makeText(requireContext(), "Balance Added for " + valueType, Toast.LENGTH_SHORT).show();
//        MainActivity activity = (MainActivity) requireActivity();
//        activity.viewPager.setCurrentItem(0);
    }

    private boolean valid() {
        if (binding.amount.getEditText().getText().toString().isEmpty()) {
            binding.amount.getEditText().setError("Amount is empty");
            return false;
        }
        if (binding.typeSpinner.getSelectedItem().toString().equals("Select Type")) {
            Toast.makeText(requireContext(), "Please select type", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        binding.passwordLayout.setVisibility(View.VISIBLE);
        binding.mainLayout.setVisibility(View.GONE);
    }
}