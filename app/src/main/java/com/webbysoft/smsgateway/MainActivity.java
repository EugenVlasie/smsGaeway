package com.webbysoft.smsgateway;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView idTextView;
    EditText idEditText;
    Button changeIdButton;
    Button stopRestartTaskButton;
    Boolean shouldSetId;
    private String[] permissions = {Manifest.permission.READ_SMS, Manifest.permission
            .SEND_SMS};
    private boolean shouldStartTask = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        idTextView = findViewById(R.id.idTextView);
        idEditText = findViewById(R.id.idEditText);
        changeIdButton = findViewById(R.id.mainButton);
        stopRestartTaskButton = findViewById(R.id.stopRestartTask);

        getDataFromSharedPreferences();
        setOnClickListeners();
    }

    private void getDataFromSharedPreferences() {
        SharedPreferences preferences = getSharedPreferences("smsGatewayPrefsName", MODE_PRIVATE);
        String deviceId = preferences.getString("deviceId", "null");
        shouldStartTask = preferences.getBoolean("shouldStartTask", true);

        if (shouldStartTask)
            stopRestartTaskButton.setText(R.string.start);
        else
            stopRestartTaskButton.setText(R.string.stop);

        if (deviceId.equals("null")) {
            idEditText.setVisibility(View.VISIBLE);
            idTextView.setVisibility(View.GONE);
            stopRestartTaskButton.setVisibility(View.GONE);
            changeIdButton.setText(R.string.confirm);
            shouldSetId = true;
        } else {
            idEditText.setVisibility(View.GONE);
            idTextView.setVisibility(View.VISIBLE);
            stopRestartTaskButton.setVisibility(View.VISIBLE);
            idTextView.setText(deviceId);
            changeIdButton.setText(R.string.schimba_id);
            shouldSetId = false;
        }
    }

    private void setOnClickListeners() {
        changeIdButton.setOnClickListener(v -> {
            if (shouldSetId) {
                if (idEditText.getText().length() <= 5) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage("Id-ul trebuie sa aiba minimum 5 caractere!")
                            .setPositiveButton("Ok", (dialog, id) -> {
                            });

                    final AlertDialog alertDialog = builder.create();

                    alertDialog.show();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage("Adaugi " + String.valueOf(idEditText.getText()) + " ca id-ul telefonului?")
                            .setPositiveButton("Da", (dialog, id) -> {
                                SharedPreferences preferences = getSharedPreferences("smsGatewayPrefsName", MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                editor.putString("deviceId", String.valueOf(idEditText.getText()));
                                editor.apply();
                                idEditText.setVisibility(View.GONE);
                                idTextView.setVisibility(View.VISIBLE);
                                stopRestartTaskButton.setVisibility(View.VISIBLE);
                                idTextView.setText(String.valueOf(idEditText.getText()));
                                idEditText.setText("");
                                changeIdButton.setText(R.string.schimba_id);
                                shouldSetId = false;
                            })
                            .setNegativeButton("Nu", (dialog, id) -> {
                            });

                    final AlertDialog alertDialog = builder.create();

                    alertDialog.show();
                }
            } else {
                if (!shouldStartTask) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage("Trebuie sa opresti trimiterea de SMS-uri ca sa poti schimba id-ul!")
                            .setPositiveButton("Ok", (dialog, id) -> {
                            });

                    final AlertDialog alertDialog = builder.create();

                    alertDialog.show();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage("Esti sigur ca vrei sa schimbi id-ul telefonului?")
                            .setPositiveButton("Da", (dialog, id) -> {
                                idEditText.setVisibility(View.VISIBLE);
                                idTextView.setVisibility(View.GONE);
                                stopRestartTaskButton.setVisibility(View.GONE);
                                idEditText.setText(String.valueOf(idTextView.getText()));
                                changeIdButton.setText(R.string.confirm);
                                shouldSetId = true;
                            })
                            .setNegativeButton("Nu", (dialog, id) -> {
                            });

                    final AlertDialog alertDialog = builder.create();

                    alertDialog.show();
                }
            }
        });

        stopRestartTaskButton.setOnClickListener(v -> {
            if (shouldStartTask) {
                shouldStartTask = false;
                SharedPreferences preferences = getSharedPreferences("smsGatewayPrefsName", MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("shouldStartTask", shouldStartTask);
                editor.apply();
                verifyPermissions();
                stopRestartTaskButton.setText(R.string.stop);
            } else {
                shouldStartTask = true;
                SharedPreferences preferences = getSharedPreferences("smsGatewayPrefsName", MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("shouldStartTask", shouldStartTask);
                editor.apply();

                stopRestartTaskButton.setText(R.string.start);
                stopSmsService();
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    if (shouldShowRequestPermissionRationale(permissions[i])) {
                        new AlertDialog.Builder(this)
                                .setMessage("Your error message here")
                                .setPositiveButton("Allow", (dialog, which) -> requestMultiplePermissions())
                                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                                .create()
                                .show();
                    }
                    return;
                }
            }
            startSmsService();
        }
    }

    private void startSmsService() {
        Intent startIntent = new Intent(getApplicationContext(), SmsService.class);
        startIntent.setAction(SmsService.ACTION_START_FOREGROUND_SERVICE);
        startIntent.putExtra("userId", idTextView.getText());
        startService(startIntent);
    }

    private void stopSmsService() {
        Intent startIntent = new Intent(getApplicationContext(), SmsService.class);
        startIntent.setAction(SmsService.ACTION_STOP_FOREGROUND_SERVICE);
        startService(startIntent);
    }

    public void verifyPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (arePermissionsEnabled()) {
                startSmsService();
            } else {
                requestMultiplePermissions();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean arePermissionsEnabled() {
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestMultiplePermissions() {
        List<String> remainingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                remainingPermissions.add(permission);
            }
        }
        requestPermissions(remainingPermissions.toArray(new String[remainingPermissions.size()]), 101);
    }

}
