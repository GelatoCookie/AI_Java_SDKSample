package com.zebra.rfid.demo.sdksample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.zebra.rfid.api3.TagData;

import java.util.ArrayList;
import java.util.HashSet;


/**
 * Main Activity for the RFID Sample application.
 */
public class MainActivity extends AppCompatActivity implements RFIDHandler.ResponseHandlerInterface {

    private final ArrayList<String> tagList = new ArrayList<>();
    private final HashSet<String> tagSet = new HashSet<>();
    private RFIDHandler rfidHandler;
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 100;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String appName = getString(R.string.app_name);
        try {
            setTitle(appName + " (" + com.zebra.rfid.api3.BuildConfig.VERSION_NAME + ")");
        } catch (Exception e) {
            /* Exception intentionally ignored for compatibility with Java 8. */
            setTitle(appName);
        }

        TextView statusTextViewRFID = findViewById(R.id.textViewStatusrfid);
        if (statusTextViewRFID != null) {
            statusTextViewRFID.setOnClickListener(v -> {
                if (rfidHandler != null) {
                    rfidHandler.toggleConnection();
                }
            });
        }
        // ...existing code...
        ListView tagListView = findViewById(R.id.tag_list);
        ArrayAdapter<String> tagAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, tagList);
        if (tagListView != null) {
            tagListView.setAdapter(tagAdapter);
        }

        Button btnStart = findViewById(R.id.TestButton);
        Button btnStop = findViewById(R.id.TestButton2);
        Button btnScan = findViewById(R.id.scan);

        if (btnStart != null) btnStart.setEnabled(false);
        if (btnStop != null) btnStop.setEnabled(false);
        if (btnScan != null) btnScan.setEnabled(false);

        rfidHandler = new RFIDHandler();
        checkPermissionsAndInit();
    }

    public void updateReaderStatus(String status, boolean isConnected) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || status == null) return;
            TextView statusTextViewRFID = findViewById(R.id.textViewStatusrfid);
            if (statusTextViewRFID != null) {
                statusTextViewRFID.setText(status);
                int color = isConnected ? R.color.status_connected : R.color.status_disconnected;
                statusTextViewRFID.setTextColor(ContextCompat.getColor(this, color));
            }
            if (status.contains(getString(R.string.connecting))) {
                showProgressDialog(status);
            } else {
                dismissProgressDialog();
            }
        });
    }

    private void showProgressDialog(String message) {
        if (isFinishing() || isDestroyed()) return;
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void checkPermissionsAndInit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                        BLUETOOTH_PERMISSION_REQUEST_CODE);
            } else {
                rfidHandler.onCreate(this);
            }
        } else {
            rfidHandler.onCreate(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (rfidHandler != null) rfidHandler.onCreate(this);
            } else {
                Toast.makeText(this, getString(R.string.bluetooth_permissions_not_granted), Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (rfidHandler == null) return super.onOptionsItemSelected(item);
        
        String result;
        if (id == R.id.antenna_settings) {
            result = rfidHandler.Test1();
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.Singulation_control) {
            result = rfidHandler.Test2();
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.Default) {
            result = rfidHandler.Defaults();
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (rfidHandler != null) rfidHandler.onPause();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (rfidHandler != null) rfidHandler.onResume();
    }

    @Override
    protected void onDestroy() {
        dismissProgressDialog();
        if (rfidHandler != null) {
            rfidHandler.onDestroy();
        }
        super.onDestroy();
    }

    private void toggleInventoryButtons(boolean isRunning) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Button btnStart = findViewById(R.id.TestButton);
            Button btnStop = findViewById(R.id.TestButton2);
            if (btnStart != null) btnStart.setEnabled(!isRunning);
            if (btnStop != null) btnStop.setEnabled(isRunning);
        });
    }

    public void setScanButtonEnabled(boolean enabled) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Button btnScan = findViewById(R.id.scan);
            if (btnScan != null) {
                btnScan.setEnabled(enabled);
            }
        });
    }

    public void StartInventory(View view) {
        toggleInventoryButtons(true);
        clearTagData();
        if (rfidHandler != null) rfidHandler.performInventory();
    }

    private void clearTagData() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            tagSet.clear();
            tagList.clear();
            ListView tagListView = findViewById(R.id.tag_list);
            ArrayAdapter<String> tagAdapter = (ArrayAdapter<String>) (tagListView != null ? tagListView.getAdapter() : null);
            if (tagAdapter != null) {
                tagAdapter.notifyDataSetChanged();
            }
        });
    }

    public void scanCode(View view) {
        if (rfidHandler != null) rfidHandler.scanCode();
    }

    public void testFunction(View view) {
        if (rfidHandler != null) rfidHandler.testFunction();
    }

    public void StopInventory(View view) {
        toggleInventoryButtons(false);
        if (rfidHandler != null) rfidHandler.stopInventory();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void handleTagdata(TagData[] tagData) {
        if (tagData == null || tagData.length == 0) return;

        final ArrayList<String> newTags = collectNewTags(tagData);
        if (!newTags.isEmpty()) {
            final int totalUniqueTags = tagSet.size();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                updateTagListUI(newTags);
                updateStatusTextWithUniqueTags(totalUniqueTags);
            });
        }
    }

    private ArrayList<String> collectNewTags(TagData[] tagData) {
        ArrayList<String> newTags = new ArrayList<>();
        for (TagData tag : tagData) {
            if (tag == null) continue;
            String tagId = tag.getTagID();
            if (tagId != null && !tagSet.contains(tagId)) {
                tagSet.add(tagId);
                newTags.add(tagId + " (RSSI: " + tag.getPeakRSSI() + ")");
            }
        }
        return newTags;
    }

    private void updateTagListUI(ArrayList<String> newTags) {
        tagList.addAll(0, newTags);
        ListView tagListView = findViewById(R.id.tag_list);
        ArrayAdapter<String> tagAdapter = (ArrayAdapter<String>) (tagListView != null ? tagListView.getAdapter() : null);
        if (tagAdapter != null) {
            tagAdapter.notifyDataSetChanged();
        }
    }

    private void updateStatusTextWithUniqueTags(int totalUniqueTags) {
        TextView statusTextViewRFID = findViewById(R.id.textViewStatusrfid);
        if (statusTextViewRFID != null && statusTextViewRFID.getText() != null) {
            String statusStr = statusTextViewRFID.getText().toString();
            if (statusStr.contains(getString(R.string.connected))) {
                String[] parts = statusStr.split("\n");
                String currentStatus = parts.length > 0 ? parts[0] : statusStr;
                statusTextViewRFID.setText(currentStatus + "\n" + getString(R.string.unique_tags, totalUniqueTags));
            }
        }
    }

    @Override
    public void handleTriggerPress(boolean pressed) {
        toggleInventoryButtons(pressed);
        if (pressed) {
            clearTagData();
            if (rfidHandler != null) rfidHandler.performInventory();
        } else {
            if (rfidHandler != null) rfidHandler.stopInventory();
        }
    }

    @Override
    public void barcodeData(String val) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            TextView scanResult = findViewById(R.id.scanResult);
            if (scanResult != null) {
                scanResult.setText(getString(R.string.scan_result_label, val != null ? val : ""));
            }
        });
    }

    @Override
    public void sendToast(String val) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            Toast.makeText(MainActivity.this, val, Toast.LENGTH_SHORT).show();
        });
    }
}
