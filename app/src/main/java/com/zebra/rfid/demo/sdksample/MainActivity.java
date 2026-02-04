package com.zebra.rfid.demo.sdksample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
 * This activity handles the UI and user interactions for connecting to a reader,
 * performing inventory, and scanning barcodes.
 */
public class MainActivity extends AppCompatActivity implements RFIDHandler.ResponseHandlerInterface {

    private static final String TAG = "MainActivity";
    // DataWedge intent actions and extras
    private static final String DW_INTENT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION";
    private static final String DW_BARCODE_ACTION = "com.symbol.datawedge.api.ACTION";
    private static final String DW_BARCODE_EXTRA = "com.symbol.datawedge.data_string";
    private static final String DW_STATUS_EXTRA = "com.symbol.datawedge.api.RESULT_GET_STATUS";
    private static final String DW_VERSION_EXTRA = "com.symbol.datawedge.api.RESULT_GET_VERSION_INFO";

    private final BroadcastReceiver dataWedgeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DW_INTENT_ACTION.equals(action) || DW_BARCODE_ACTION.equals(action)) {
                // Barcode data
                if (intent.hasExtra(DW_BARCODE_EXTRA)) {
                    String barcode = intent.getStringExtra(DW_BARCODE_EXTRA);
                    barcodeData(barcode);
                }
                // Status info
                if (intent.hasExtra(DW_STATUS_EXTRA)) {
                    String status = intent.getStringExtra(DW_STATUS_EXTRA);
                    sendToast("DW Status: " + status);
                }
                // Version info
                if (intent.hasExtra(DW_VERSION_EXTRA)) {
                    String version = intent.getStringExtra(DW_VERSION_EXTRA);
                    sendToast("DW Version: " + version);
                }
            }
        }
    };
    
    /** TextView to display RFID connection and operation status. */
    public TextView statusTextViewRFID;
    
    /** ListView to display scanned RFID tag data. */
    private ListView tagListView;
    
    /** Adapter for the tag list. */
    private ArrayAdapter<String> tagAdapter;
    
    /** List to hold tag strings for the adapter. */
    private final ArrayList<String> tagList = new ArrayList<>();
    
    /** TextView to display barcode scan results. */
    private TextView scanResult;

    /** Buttons for RFID Inventory control. */
    private Button btnStart;
    private Button btnStop;
    
    /** Button for Barcode Scanning. */
    private Button btnScan;
    
    /** Handler for RFID and Scanner related operations. */
    private RFIDHandler rfidHandler;
    
    /** Set to track unique tags discovered during the current session. */
    private final HashSet<String> tagSet = new HashSet<>();
    
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 100;


    /**
     * Create DataWedge profile to receive barcode data and disable RFID plug-in
     */
    private void createDataWedgeProfile() {
        final String PROFILE_NAME = "RFIDSampleProfile";
        final String PACKAGE_NAME = getPackageName();
        final String ACTIVITY_NAME = getClass().getName();

        // 1. Create profile
        Bundle bCreateProfile = new Bundle();
        bCreateProfile.putString("com.symbol.datawedge.api.CREATE_PROFILE", PROFILE_NAME);
        sendDataWedgeCommand("com.symbol.datawedge.api.ACTION", bCreateProfile);

        // 2. Configure profile: barcode enabled, RFID disabled, intent output
        Bundle bConfig = new Bundle();
        bConfig.putString("PROFILE_NAME", PROFILE_NAME);
        bConfig.putString("PROFILE_ENABLED", "true");
        bConfig.putString("CONFIG_MODE", "UPDATE");

        // Barcode input plugin
        Bundle barcodeProps = new Bundle();
        barcodeProps.putString("PLUGIN_NAME", "BARCODE");
        barcodeProps.putString("RESET_CONFIG", "true");
        Bundle barcodeParams = new Bundle();
        barcodeParams.putString("scanner_selection_by_identifier", "AUTO");
        barcodeProps.putBundle("PARAM_LIST", barcodeParams);

        // RFID input plugin (disable)
        Bundle rfidProps = new Bundle();
        rfidProps.putString("PLUGIN_NAME", "RFID");
        rfidProps.putString("RESET_CONFIG", "true");
        Bundle rfidParams = new Bundle();
        rfidParams.putString("rfid_input_enabled", "false");
        rfidProps.putBundle("PARAM_LIST", rfidParams);

        // Intent output plugin
        Bundle intentProps = new Bundle();
        intentProps.putString("PLUGIN_NAME", "INTENT");
        intentProps.putString("RESET_CONFIG", "true");
        Bundle intentParams = new Bundle();
        intentParams.putString("intent_output_enabled", "true");
        intentParams.putString("intent_action", "com.symbol.datawedge.api.ACTION");
        intentParams.putString("intent_delivery", "2"); // 2 = broadcast
        intentProps.putBundle("PARAM_LIST", intentParams);

        ArrayList<Bundle> pluginConfig = new ArrayList<>();
        pluginConfig.add(barcodeProps);
        pluginConfig.add(rfidProps);
        pluginConfig.add(intentProps);
        bConfig.putParcelableArrayList("PLUGIN_CONFIG", pluginConfig);

        // Associate app with profile
        Bundle appAssoc = new Bundle();
        appAssoc.putString("PACKAGE_NAME", PACKAGE_NAME);
        appAssoc.putStringArray("ACTIVITY_LIST", new String[]{"*"});
        ArrayList<Bundle> appList = new ArrayList<>();
        appList.add(appAssoc);
        bConfig.putParcelableArrayList("APP_LIST", appList);

        sendDataWedgeCommand("com.symbol.datawedge.api.ACTION", bConfig);
    }

    private void sendDataWedgeCommand(String action, Bundle extras) {
        Intent i = new Intent();
        i.setAction(action);
        if (extras != null) i.putExtras(extras);
        sendBroadcast(i);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Programmatically create DataWedge profile for barcode, disable RFID
        createDataWedgeProfile();

        // Register DataWedge broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(DW_INTENT_ACTION);
        filter.addAction(DW_BARCODE_ACTION);
        registerReceiver(dataWedgeReceiver, filter);

        statusTextViewRFID = findViewById(R.id.textViewStatusrfid);
        if (statusTextViewRFID != null) {
            statusTextViewRFID.setOnClickListener(v -> {
                if (rfidHandler != null) {
                    rfidHandler.toggleConnection();
                }
            });
        }

        scanResult = findViewById(R.id.scanResult);

        // Initialize ListView and Adapter
        tagListView = findViewById(R.id.tag_list);
        tagAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, tagList);
        if (tagListView != null) {
            tagListView.setAdapter(tagAdapter);
        }

        btnStart = findViewById(R.id.TestButton);
        btnStop = findViewById(R.id.TestButton2);
        btnScan = findViewById(R.id.scan);

        // Initially inventory is not running and reader is not connected
        if (btnStart != null) btnStart.setEnabled(false);
        if (btnStop != null) btnStop.setEnabled(false);

        // Initially disable scan button until session established
        if (btnScan != null) btnScan.setEnabled(false);

        rfidHandler = new RFIDHandler();
        checkPermissionsAndInit();
    }

    /**
     * Updates the reader status UI with appropriate colors.
     * @param status The status message to display.
     * @param isConnected Whether the reader is connected.
     */
    public void updateReaderStatus(String status, boolean isConnected) {
        runOnUiThread(() -> {
            if (statusTextViewRFID != null) {
                statusTextViewRFID.setText(status);
                if (isConnected) {
                    statusTextViewRFID.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
                    if (btnStart != null) btnStart.setEnabled(true);
                } else {
                    statusTextViewRFID.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
                    if (btnStart != null) btnStart.setEnabled(false);
                    if (btnStop != null) btnStop.setEnabled(false);
                }
            }
        });
    }

    /**
     * Checks for necessary Bluetooth permissions and initializes the RFID handler.
     * Required for Android 12 (API 31) and higher.
     */
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
                rfidHandler.onCreate(this);
            } else {
                Toast.makeText(this, "Bluetooth Permissions not granted", Toast.LENGTH_SHORT).show();
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
        rfidHandler.onPause();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        rfidHandler.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        rfidHandler.onDestroy();
        unregisterReceiver(dataWedgeReceiver);
    }

    /**
     * Toggles the enabled state of the Inventory control buttons.
     * @param isRunning True if inventory is currently running.
     */
    private void toggleInventoryButtons(boolean isRunning) {
        runOnUiThread(() -> {
            if (btnStart != null) btnStart.setEnabled(!isRunning);
            if (btnStop != null) btnStop.setEnabled(isRunning);
        });
    }

    /**
     * Enables or disables the scan button.
     * @param enabled True to enable the button.
     */
    public void setScanButtonEnabled(boolean enabled) {
        runOnUiThread(() -> {
            if (btnScan != null) {
                btnScan.setEnabled(enabled);
            }
        });
    }

    /**
     * Called when the Start Inventory button is clicked.
     * @param view The view that was clicked.
     */
    public void StartInventory(View view) {
        toggleInventoryButtons(true);
        clearTagData();
        rfidHandler.performInventory();
    }

    private void clearTagData() {
        runOnUiThread(() -> {
            tagSet.clear();
            tagList.clear();
            if (tagAdapter != null) {
                tagAdapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * Called when the Scan Code button is clicked.
     * @param view The view that was clicked.
     */
    public void scanCode(View view) {
        rfidHandler.scanCode();
    }

    /**
     * Called when the Test button is clicked.
     * @param view The view that was clicked.
     */
    public void testFunction(View view) {
        rfidHandler.testFunction();
    }

    /**
     * Called when the Stop Inventory button is clicked.
     * @param view The view that was clicked.
     */
    public void StopInventory(View view) {
        toggleInventoryButtons(false);
        rfidHandler.stopInventory();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void handleTagdata(TagData[] tagData) {
        if (tagData == null || tagData.length == 0) return;

        final ArrayList<String> newTags = new ArrayList<>();
        for (TagData tag : tagData) {
            if (tag == null) continue;
            String tagId = tag.getTagID();
            if (tagId != null && !tagSet.contains(tagId)) {
                tagSet.add(tagId);
                newTags.add(tagId + " (RSSI: " + tag.getPeakRSSI() + ")");
            }
        }
        
        if (!newTags.isEmpty()) {
            final int totalUniqueTags = tagSet.size();
            runOnUiThread(() -> {
                // Ensure list modification and notifyDataSetChanged happen together on UI thread
                tagList.addAll(0, newTags); 
                if (tagAdapter != null) {
                    tagAdapter.notifyDataSetChanged();
                }
                
                // Update status with unique tag count
                if (statusTextViewRFID != null && statusTextViewRFID.getText() != null) {
                    String statusStr = statusTextViewRFID.getText().toString();
                    if (statusStr.contains("Connected")) {
                        String[] parts = statusStr.split("\n");
                        String currentStatus = parts.length > 0 ? parts[0] : statusStr;
                        statusTextViewRFID.setText(currentStatus + "\nUnique Tags: " + totalUniqueTags);
                    }
                }
            });
        }
    }

    @Override
    public void handleTriggerPress(boolean pressed) {
        toggleInventoryButtons(pressed);
        if (pressed) {
            clearTagData();
            rfidHandler.performInventory();
        } else {
            rfidHandler.stopInventory();
        }
    }

    @Override
    public void barcodeData(String val) {
        runOnUiThread(() -> {
            if (scanResult != null) {
                scanResult.setText(String.format("Scan Result : %s", val != null ? val : ""));
            }
        });
    }

    @Override
    public void sendToast(String val) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, val, Toast.LENGTH_SHORT).show());
    }
}
