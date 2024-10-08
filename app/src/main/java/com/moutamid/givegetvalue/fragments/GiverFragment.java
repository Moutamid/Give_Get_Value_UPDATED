package com.moutamid.givegetvalue.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.budiyev.android.codescanner.CodeScanner;
import com.fxn.stash.Stash;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.moutamid.givegetvalue.Constants;
import com.moutamid.givegetvalue.R;
import com.moutamid.givegetvalue.databinding.FragmentGiverBinding;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class GiverFragment extends Fragment {
    private CodeScanner mCodeScanner;
    FragmentGiverBinding binding;
    String server_serverName = "Server";
    String server_serverip = "";
    Context context;

    public static String extractedValue = "";
    public static String extractedType = "";
    String status = "receiver";
    public static float currentBalance = 0;

    ServerSocket server_serverSocket;
    Socket server_clientSocket;
    PrintStream server_ps = null;
    BufferedReader server_br = null;
    ImageView server_qrCodeImageView;
    String server_message;


    String client_ip, client_name;
    Socket client_clientSocket;
    PrintStream client_ps = null;

    private static final String TAG = "GiverFragment";

    public GiverFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for requireContext() fragment
        binding = FragmentGiverBinding.inflate(getLayoutInflater(), container, false);
        context = requireContext();

        client_clientSocket = new Socket();
        server_serverip = Constants.getIPAddress(true);
        Log.d(TAG, "onCreateView: " + server_serverip);

        ArrayAdapter<CharSequence> spinAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.type_array, R.layout.dropdown_layout);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.typeSpinner.setAdapter(spinAdapter);

        currentBalance = Stash.getFloat(binding.typeSpinner.getSelectedItem().toString().trim(), 0);

        binding.typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedItem = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "onItemSelected: " + selectedItem);
                float value = Stash.getFloat(selectedItem, 0);
                binding.balance.setText("Balance for " + selectedItem + ": " + String.format("%.2f", value));
                if (value > 0) binding.give.setVisibility(View.VISIBLE);
                else binding.give.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mCodeScanner = new CodeScanner(requireContext(), binding.scannerView);
        mCodeScanner.setDecodeCallback(result -> {
            Log.d(TAG, "onCreate: " + result);
            String[] res = result.getText().split(", ");
            HashMap<String, String> dataMap = new HashMap<>();

            // Split each key-value pair and store in HashMap
            for (String pair : res) {
                String[] entry = pair.split(": ");
                if (entry.length == 2) {
                    dataMap.put(entry[0].trim(), entry[1].trim());
                }
            }

            extractedType = dataMap.get("Type");
            extractedValue = dataMap.get("Value");
            client_ip = dataMap.get("ServerIP");
            client_name = dataMap.get("ServerName");
            status = dataMap.get("Status");

            float current = Stash.getFloat(extractedType, 0);
            float requested = Float.parseFloat(extractedValue);
            if (current < requested) {
                Toast.makeText(requireContext(), "Insufficient balance", Toast.LENGTH_SHORT).show();
            } else {
                Thread sc = new Thread(new client_StartCommunication());
                sc.start();
                currentBalance = Stash.getFloat(extractedType, 0);
                if (status.equals(Constants.GIVER)) {
                    currentBalance += Float.parseFloat(extractedValue);
                } else {
                    currentBalance -= Float.parseFloat(extractedValue);
                }
                requireActivity().runOnUiThread(() -> {
                    mCodeScanner.stopPreview();
                    binding.scannerLayout.setVisibility(View.GONE);
                    binding.close.setVisibility(View.GONE);
                });
            }
        });

        binding.scan.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
                ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, 1);
            } else {
                mCodeScanner.startPreview();
                binding.close.setVisibility(View.VISIBLE);
                binding.scannerLayout.setVisibility(View.VISIBLE);
            }
        });

        binding.close.setOnClickListener(v -> {
            binding.qrCodeLayout.setVisibility(View.GONE);
            binding.scannerLayout.setVisibility(View.GONE);
            binding.qrCode.setImageResource(0);
            mCodeScanner.stopPreview();
            binding.close.setVisibility(View.GONE);
        });

        binding.give.setOnClickListener(v -> {
            if (valid()) {
                binding.scannerLayout.setVisibility(View.GONE);
                String valueType = binding.typeSpinner.getSelectedItem().toString();
                float value = Stash.getFloat(valueType, 0);
                float entered = Float.parseFloat(binding.amount.getEditText().getText().toString());
                if (value < entered) {
                    Toast.makeText(requireContext(), "Entered amount exceed the current balance", Toast.LENGTH_SHORT).show();
                } else giveAmount();
            }
        });

        binding.request.setOnClickListener(v -> {
            if (valid()) {
                binding.scannerLayout.setVisibility(View.GONE);
                requestAmount();
            }
        });

        return binding.getRoot();
    }

    private void showConfirmationAlert() {
        Log.d(TAG, "showConfirmationAlert: ");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirmation Alert")
                .setMessage("Do you really want to do this transaction?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> {
                    dialog.dismiss();
                    binding.scannerLayout.setVisibility(View.GONE);
                    binding.close.setVisibility(View.GONE);
                    binding.qrCodeLayout.setVisibility(View.GONE);
                    server_message = "yes_confirm";
                    Thread sendThread = new Thread(new server_SendThread());
                    sendThread.start();
                    Stash.put(binding.typeSpinner.getSelectedItem().toString().trim(), currentBalance);
                })
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void requestAmount() {
        String valueToGive = binding.amount.getEditText().getText().toString();
        String valueType = binding.typeSpinner.getSelectedItem().toString().trim();
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        String qrData = "Type: " + valueType + ", Value: " + valueToGive + ", Timestamp: " + date + ", Status: " + Constants.REQUEST + ", ServerName: " + server_serverName + ", ServerIP: " + Constants.getIPAddress(true);
        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? getResources().getColor(R.color.black) : getResources().getColor(R.color.grey));
                }
            }
            binding.qrCode.setImageBitmap(bmp);
            binding.qrCodeLayout.setVisibility(View.VISIBLE);
            binding.close.setVisibility(View.VISIBLE);

//            if (server_clientSocket != null) {
//                Log.d(TAG, "requestAmount: IF");
//                new Thread(() -> {
//                    try {
//                        server_clientSocket = server_serverSocket.accept();
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        Log.d(TAG, "giveAmount: Error " + e.getMessage());
//                    }
//                }).start();
//                server_updateMessage("yes_confirm");
//            } else {
//                Log.d(TAG, "requestAmount: ELSE");
//                Thread serverThread = new Thread(new server_ServerThread());
//                serverThread.start();
//            }

            Thread serverThread = new Thread(new server_ServerThread());
            serverThread.start();

            currentBalance = Stash.getFloat(valueType, 0);
            currentBalance += Float.parseFloat(valueToGive);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void giveAmount() {
        String valueToGive = binding.amount.getEditText().getText().toString();
        String valueType = binding.typeSpinner.getSelectedItem().toString().trim();
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String qrData = "Type: " + valueType + ", Value: " + valueToGive + ", Timestamp: " + date + ", Status: " + Constants.GIVER + ", ServerName: " + server_serverName + ", ServerIP: " + server_serverip;

        currentBalance = Stash.getFloat(valueType, 0);

        QRCodeWriter writer = new QRCodeWriter();
        try {
            BitMatrix bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 512, 512);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? getResources().getColor(R.color.black) : getResources().getColor(R.color.grey));
                }
            }
            binding.qrCode.setImageBitmap(bmp);
            binding.qrCodeLayout.setVisibility(View.VISIBLE);
            binding.close.setVisibility(View.VISIBLE);

//            if (server_clientSocket != null) {
//                Log.d(TAG, "giveAmount: IF");
//                new Thread(() -> {
//                    try {
//                        server_clientSocket = server_serverSocket.accept();
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        Log.d(TAG, "giveAmount: Error " + e.getMessage());
//                    }
//                }).start();
//                server_updateMessage("yes_confirm");
//            } else {
//                Log.d(TAG, "giveAmount: ELSE");
//                Thread serverThread = new Thread(new server_ServerThread());
//                serverThread.start();
//            }

            Thread serverThread = new Thread(new server_ServerThread());
            serverThread.start();

            currentBalance -= Float.parseFloat(valueToGive);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void server_updateMessage(String str) {
        Log.d(TAG, "server_updateMessage: " + str);
        Handler server_handler = new Handler(Looper.getMainLooper());
        server_handler.post(() -> {
            if (str.equals("show_giver_confirmation") || str.equals("yes_confirm")) {
                Log.d(TAG, "showConfirmationAlert: show_giver_confirmation");
                showConfirmationAlert();
            } else client_updateData();
        });
    }

    class server_ServerThread implements Runnable {
        @Override
        public void run() {
            try {
                Log.d(TAG, "run: server_ServerThread");
                if (server_serverSocket == null) {
                    server_serverSocket = new ServerSocket(55555);
                }
                server_clientSocket = server_serverSocket.accept();
                // Accepts the client connection
                server_ps = new PrintStream(server_clientSocket.getOutputStream());
                server_br = new BufferedReader(new InputStreamReader(server_clientSocket.getInputStream()));
                server_ps.println("Hello from " + server_serverName + "!");
                server_updateMessage("Client connected!");
                while (true) {
                    String receivedMessage = server_br.readLine();
                    Log.d(TAG, "run: receivedMessage  " + receivedMessage);
                    if (receivedMessage.equalsIgnoreCase("exit")) {
                        server_updateMessage("Client disconnected!");
                        break;
                    }
                    server_updateMessage(receivedMessage);
                }

            } catch (Exception e) {
                server_updateMessage("Error in connection");
                e.printStackTrace();
            }
        }
    }

    class server_SendThread implements Runnable {
        @Override
        public void run() {
            try {
                Log.d(TAG, "run: server_SendThread");
                server_message = "yes_confirm";  // Clear the input field
                server_message = server_serverName + ": " + server_message;
                server_ps.println(server_message);  // Send message to client
                server_ps.flush();
                server_updateMessage(server_message);  // Update the server's chat display
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void client_updateChatMessage(String str) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Log.d(TAG, "client_updateChatMessage func : " + str);
            if (str.equals("Server: yes_confirm")) {
                Log.d(TAG, "Updating DATA");
                client_updateData();
            }
        });
    }

    class client_StartCommunication implements Runnable {

        @Override
        public void run() {
            try {
                Log.d(TAG, "run: client_StartCommunication");
                InetSocketAddress inetAddress = new InetSocketAddress(client_ip, 55555);
                client_clientSocket = new Socket();
                client_clientSocket.connect(inetAddress, 7000);
                client_ps = new PrintStream(client_clientSocket.getOutputStream());
                client_updateChatMessage("Connected to " + client_ip + " !!\n");
                client_ps.println("j01ne6:" + client_name);
                Log.d(TAG, "client_updateChatMessage  j01ne6:" + client_name);
                BufferedReader br = new BufferedReader(new InputStreamReader(client_clientSocket.getInputStream()));
                client_ps.println("show_giver_confirmation");
                Log.d(TAG, "client_updateChatMessage: show_giver_confirmation");
                while (true) {
                    final String str = br.readLine();
                    Log.d(TAG, "client_updateChatMessage: client Recived " + str);
                    if (str.equalsIgnoreCase("exit")) {
                        client_updateChatMessage("Server Closed the Connection! | client_updateChatMessage");
                        Thread.sleep(2000);
                        break;
                    }
                    client_updateChatMessage(str);
                }
            } catch (final Exception e) {
                client_updateChatMessage("Not able to connect!" + e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (Exception ignored) {

                }
            }
        }
    }


    private void client_updateData() {
        Stash.put(extractedType, currentBalance);
        binding.balance.setText("Balance for " + extractedType + ": " + String.format("%.2f", currentBalance));
        binding.scannerLayout.setVisibility(View.GONE);
        binding.close.setVisibility(View.GONE);
        binding.qrCodeLayout.setVisibility(View.GONE);

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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mCodeScanner.startPreview();
                binding.scannerLayout.setVisibility(View.VISIBLE);
                binding.close.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(binding.getRoot().getContext(), "Permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mCodeScanner.stopPreview();
    }

}