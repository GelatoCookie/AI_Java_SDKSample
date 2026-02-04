package com.zebra.rfid.demo.sdksample;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import com.zebra.rfid.api3.Antennas;
import com.zebra.rfid.api3.ENUM_TRANSPORT;
import com.zebra.rfid.api3.HANDHELD_TRIGGER_EVENT_TYPE;
import com.zebra.rfid.api3.INVENTORY_STATE;
import com.zebra.rfid.api3.IRFIDLogger;
import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RfidEventsListener;
import com.zebra.rfid.api3.RfidReadEvents;
import com.zebra.rfid.api3.RfidStatusEvents;
import com.zebra.rfid.api3.SESSION;
import com.zebra.rfid.api3.SL_FLAG;
import com.zebra.rfid.api3.STATUS_EVENT_TYPE;
import com.zebra.rfid.api3.TagData;
import com.zebra.scannercontrol.DCSSDKDefs;
import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.SDKHandler;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handler class for RFID operations.
 * This class encapsulates the Zebra RFID API logic.
 * Feb 01, 2026 Create non-blocking uiHandler for connecting
 */
class RFIDHandler implements Readers.RFIDReaderEventHandler {
    // Helper for selecting a reader from available devices
    private RFIDReader selectReader(ArrayList<ReaderDevice> availableReaders) {
        if (availableReaders == null || availableReaders.isEmpty()) return null;
        if (availableReaders.size() == 1) {
            ReaderDevice singleDevice = availableReaders.get(0);
            return (singleDevice != null) ? singleDevice.getRFIDReader() : null;
        } else {
            for (ReaderDevice device : availableReaders) {
                if (device != null && device.getName() != null && device.getName().startsWith(READER_NAME_PREFIX)) {
                    return device.getRFIDReader();
                }
            }
        }
        return null;
    }

    // Helper for populating scanner list
    private void populateScannerList(ArrayList<DCSScannerInfo> availableScanners) {
        if (scannerList != null) {
            scannerList.clear();
        } else {
            scannerList = new ArrayList<>();
        }
        if (availableScanners != null) {
            for (DCSScannerInfo scanner : availableScanners) {
                if (scanner != null) {
                    scannerList.add(scanner);
                }
            }
        }
    }

    // Helper for establishing scanner sessions
    private void establishScannerSessions() {
        if (reader != null && reader.isConnected()) {
            String hostName = reader.getHostName();
            for (DCSScannerInfo device : scannerList) {
                if (device != null && device.getScannerName() != null && hostName != null && device.getScannerName().contains(hostName)) {
                    try {
                        sdkHandler.dcssdkEstablishCommunicationSession(device.getScannerID());
                        scannerID = device.getScannerID();
                    } catch (Exception e) {
                        Log.e(TAG, "Error establishing scanner session", e);
                    }
                }
            }
        }
    }

    private static final String TAG = "RFID_SAMPLE";
    private static final int MAX_POWER = 270;
    // String constants for repeated literals
    private static final String READER_NAME_PREFIX = "RFD";
    private static final String CONNECTING_STATUS = "Connecting...";
    private static final String ERROR_GETTING_READERS = "Error getting available readers";
    private static final String FAILED_TO_FIND_READER = "Failed to find reader";
    private static final String DEFAULT_SETTINGS_APPLIED = "Default settings applied";
    private static final String CONNECTION_FAILED = "Connection failed: ";
    private static final String ERROR_DURING_DISCONNECT = "Error during disconnect";
    private static final String ERROR_DURING_DISPOSE = "Error during dispose";
    private static final String CONNECTED_PREFIX = "Connected: ";
    private static final String DISCONNECTED = "Disconnected";

    private Readers readers;
    private RFIDReader reader;
    private EventHandler eventHandler;
    private MainActivity context;
    private SDKHandler sdkHandler;
    private ScannerHandler scannerHandler;
    private ArrayList<DCSScannerInfo> scannerList;
    private int scannerID;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private int connectionTimer = 0;
    private final Runnable timerRunnable = () -> {
        if (context != null) {
            context.updateReaderStatus(CONNECTING_STATUS + " " + connectionTimer++ + "s", false);
            uiHandler.postDelayed(this.timerRunnable, 1000);
        }
    };
    /** Executor for background tasks. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Initializes the RFIDHandler with the activity context.
     * @param activity The MainActivity context.
     */
    void onCreate(MainActivity activity) {
        context = activity;
        scannerList = new ArrayList<>();
        scannerHandler = new ScannerHandler(activity);
        initSDK();
    }

    public String Test1() { return "TO DO"; }
    public String Test2() { return "TODO2"; }

    /**
     * Resets the reader settings to defaults.
     * @return Success or error message.
     */
    public String Defaults() {
        if (!isReaderConnected()) return DISCONNECTED;
        try {
            Antennas.AntennaRfConfig config = reader.Config.Antennas.getAntennaRfConfig(1);
            config.setTransmitPowerIndex(MAX_POWER);
            config.setrfModeTableIndex(0);
            config.setTari(0);
            reader.Config.Antennas.setAntennaRfConfig(1, config);

            Antennas.SingulationControl singulationControl = reader.Config.Antennas.getSingulationControl(1);
            singulationControl.setSession(SESSION.SESSION_S0);
            singulationControl.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A);
            singulationControl.Action.setSLFlag(SL_FLAG.SL_ALL);
            reader.Config.Antennas.setSingulationControl(1, singulationControl);
        } catch (InvalidUsageException | OperationFailureException e) {
            Log.e(TAG, "Error in Defaults", e);
            return e.getMessage();
        }
        return DEFAULT_SETTINGS_APPLIED;
    }

    private boolean isReaderConnected() {
        return reader != null && reader.isConnected();
    }

    /**
     * Toggles the connection to the reader.
     * If connected, it disconnects. If disconnected, it attempts to connect.
     */
    public void toggleConnection() {
        if (isReaderConnected()) {
            executor.execute(this::disconnect);
        } else {
            connectReader();
        }
    }

    void onResume() {
        executor.execute(() -> {
            String result = connect();
            if (context != null) {
                context.updateReaderStatus(result, isReaderConnected());
            }
        });
    }

    void onPause() {
        disconnect();
    }

    void onDestroy() {
        dispose();
        executor.shutdown();
    }


    /**
     * Placeholder for test function. Not implemented.
     */
    public void testFunction() {
        // Not implemented. Add logic or remove if not needed.
        throw new UnsupportedOperationException("Not implemented");
    }

    private void connectReader() {
        // Offload the entire connection process to a background thread to keep UI responsive
        executor.execute(() -> {
            // Update UI to show connection is in progress
            if (context != null) {
                 context.updateReaderStatus(CONNECTING_STATUS, false);
            }
            synchronized (RFIDHandler.this) {
                if (!isReaderConnected()) {
                    getAvailableReader();
                    String result = (reader != null) ? connect() : FAILED_TO_FIND_READER;
                    // Update UI with the final result
                    if (context != null) {
                        context.updateReaderStatus(result, isReaderConnected());
                    }
                } else {
                    // Already connected, just update UI
                    if (context != null) {
                        context.updateReaderStatus(CONNECTED_PREFIX + reader.getHostName(), true);
                    }
                }
            }
        });
    }

    private synchronized void getAvailableReader() {
        if (readers != null) {
            readers.attach(this);
            try {
                ArrayList<ReaderDevice> availableReaders = readers.GetAvailableRFIDReaderList();
                reader = selectReader(availableReaders);
            } catch (InvalidUsageException e) {
                Log.e(TAG, ERROR_GETTING_READERS, e);
            }
        }
    }

    @Override
    public void RFIDReaderAppeared(ReaderDevice readerDevice) {
        connectReader();
    }

    @Override
    public void RFIDReaderDisappeared(ReaderDevice readerDevice) {
        if (context != null) context.sendToast("RFIDReaderDisappeared: " + readerDevice.getName());
        if (reader != null && readerDevice != null && readerDevice.getName().equals(reader.getHostName())) {
            disconnect();
        }
    }

    private synchronized String connect() {
        if (reader != null) {
            try {
                if (!reader.isConnected()) {
                    connectionTimer = 0;
                    uiHandler.post(timerRunnable);
                    long startTime = System.currentTimeMillis();
                    try {
                        reader.connect();
                    } finally {
                        uiHandler.removeCallbacks(timerRunnable);
                    }
                    long duration = System.currentTimeMillis() - startTime;
                    configureReader();
                    setupScannerSdk();
                    if (reader.isConnected()) {
                            return CONNECTED_PREFIX + reader.getHostName() + " (" + duration + " ms)";
                    }
                } else {
                        return CONNECTED_PREFIX + reader.getHostName();
                }
            } catch (InvalidUsageException e) {
                Log.e(TAG, CONNECTION_FAILED + e.getMessage());
                return CONNECTION_FAILED + e.getMessage();
            } catch (OperationFailureException e) {
                Log.e(TAG, CONNECTION_FAILED + e.getStatusDescription());
                return CONNECTION_FAILED + e.getStatusDescription();
            }

        }
        return DISCONNECTED;
    }


    public void setupScannerSdk() {
            // This method was previously called setupScannerSDK (case mismatch). Now unified as setupScannerSdk.
        if (sdkHandler == null) {
            sdkHandler = new SDKHandler(context);
            sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_USB_CDC);
            sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL);
            sdkHandler.dcssdkSetDelegate(scannerHandler);
            int notificationsMask = DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value |
                DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value |
                DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value |
                DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value |
                DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value;
            sdkHandler.dcssdkSubsribeForEvents(notificationsMask);
        }

        ArrayList<DCSScannerInfo> availableScanners = (ArrayList<DCSScannerInfo>) sdkHandler.dcssdkGetAvailableScannersList();
        populateScannerList(availableScanners);
        establishScannerSessions();
    }

    private synchronized void disconnect() {
        try {
            if (reader != null) {
                if (eventHandler != null) reader.Events.removeEventsListener(eventHandler);
                if (sdkHandler != null) {
                    sdkHandler.dcssdkTerminateCommunicationSession(scannerID);
                }
                reader.disconnect();
                if (context != null)
                    context.updateReaderStatus(DISCONNECTED, false);
                reader.Dispose();
                reader = null;
                sdkHandler = null;
            }
        } catch (Exception e) {
            Log.e(TAG, ERROR_DURING_DISCONNECT, e);
        }
    }

    private synchronized void dispose() {
        disconnect();
        try {
            if (readers != null) {
                readers.Dispose();
                readers = null;
            }
        } catch (Exception e) {
            Log.e(TAG, ERROR_DURING_DISPOSE, e);
        }
    }

    synchronized void performInventory() {
        try {
            if (reader != null && reader.isConnected()) reader.Actions.Inventory.perform();
        } catch (InvalidUsageException | OperationFailureException e) {
            Log.e(TAG, "Error performing inventory", e);
        }
    }

    synchronized void stopInventory() {
        try {
            if (reader != null && reader.isConnected()) reader.Actions.Inventory.stop();
        } catch (InvalidUsageException | OperationFailureException e) {
            Log.e(TAG, "Error stopping inventory", e);
        }
    }

    public void scanCode() {
        String inXml = "<inArgs><scannerID>" + scannerID + "</scannerID></inArgs>";
        executor.execute(() -> executeCommand(DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_DEVICE_PULL_TRIGGER, inXml, new StringBuilder(), scannerID));
    }

    private boolean executeCommand(DCSSDKDefs.DCSSDK_COMMAND_OPCODE opCode, String inXML, StringBuilder outXML, int scannerID) {
        if (sdkHandler != null) {
            if (outXML == null) outXML = new StringBuilder();
            DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(opCode, inXML, outXML, scannerID);
            return result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS;
        }
        return false;
    }

    public class EventHandler implements RfidEventsListener {
        @Override
        public void eventReadNotify(RfidReadEvents e) {
            if (reader == null) return;
            TagData[] myTags = reader.Actions.getReadTags(100);
            if (myTags != null && context != null) {
                executor.execute(() -> context.handleTagdata(myTags));
            }
        }

        @Override
        public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
            if (rfidStatusEvents == null || rfidStatusEvents.StatusEventData == null) return;
            STATUS_EVENT_TYPE eventType = rfidStatusEvents.StatusEventData.getStatusEventType();
            if (eventType == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
                if (rfidStatusEvents.StatusEventData.HandheldTriggerEventData != null) {
                    HANDHELD_TRIGGER_EVENT_TYPE triggerEvent = rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent();
                    boolean pressed = (triggerEvent == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED);
                    if (context != null) {
                        executor.execute(() -> context.handleTriggerPress(pressed));
                    }
                }
            }
            else if (eventType == STATUS_EVENT_TYPE.DISCONNECTION_EVENT) {
                executor.execute(() -> {
                    disconnect();
                    dispose();
                });
            }
        }
    }

    // Ensure method signatures for initSDK and configureReader exist
    private void initSDK() {
        // SDK initialization logic can be implemented here if needed
    }

    private void configureReader() {
        // Reader configuration logic can be implemented here if needed
    }

    interface ResponseHandlerInterface {
        void handleTagdata(TagData[] tagData);
        void handleTriggerPress(boolean pressed);
        void barcodeData(String val);
        void sendToast(String val);
    }
}
