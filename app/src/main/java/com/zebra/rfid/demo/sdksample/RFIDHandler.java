package com.zebra.rfid.demo.sdksample;

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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handler class for RFID and Scanner operations using Zebra SDKs.
 * This class encapsulates the Zebra RFID API and Scanner Control API logic,
 * managing connections, inventory, and barcode scanning in background threads.
 */
class RFIDHandler implements Readers.RFIDReaderEventHandler {

    private static final String TAG = "RFID_SAMPLE";
    private Readers readers;
    private ArrayList<ReaderDevice> availableRFIDReaderList;
    private ReaderDevice readerDevice;
    private RFIDReader reader;
    private TextView textView;
    private EventHandler eventHandler;
    private MainActivity context;
    private SDKHandler sdkHandler;
    private ScannerHandler scannerHandler;
    private ArrayList<DCSScannerInfo> scannerList;
    private int scannerID;
    private final int MAX_POWER = 270;
    private final String readerName = "RFD4031-G10B700-WR";
    
    /** Executor service for running blocking SDK operations off the main thread. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Initializes the RFIDHandler with the activity context and sets up scanner handling.
     * 
     * @param activity The MainActivity instance for context and UI updates.
     */
    void onCreate(MainActivity activity) {
        context = activity;
        textView = activity.statusTextViewRFID;
        scannerList = new ArrayList<>();
        scannerHandler = new ScannerHandler(activity);
        InitSDK();
    }

    /**
     * Placeholder for Test1 functionality.
     * @return A string indicating the action.
     */
    public String Test1() { return "TO DO"; }
    
    /**
     * Placeholder for Test2 functionality.
     * @return A string indicating the action.
     */
    public String Test2() { return "TODO2"; }

    /**
     * Resets the reader's antenna and singulation settings to default values.
     * 
     * @return A message string indicating success or the error encountered.
     */
    public String Defaults() {
        if (!isReaderConnected()) return "Not connected";
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
        return "Default settings applied";
    }

    /**
     * Checks if the RFID reader is currently connected.
     * 
     * @return true if the reader object exists and is connected.
     */
    private boolean isReaderConnected() {
        return reader != null && reader.isConnected();
    }

    /**
     * Toggles the connection status of the RFID reader.
     * Disconnects if currently connected, or initiates a connection if not.
     */
    public void toggleConnection() {
        if (isReaderConnected()) {
            executor.execute(this::disconnect);
        } else {
            connectReader();
        }
    }

    /**
     * Called when the activity resumes. Attempts to reconnect the reader in the background.
     */
    void onResume() {
        executor.execute(() -> {
            String result = connect();
            if (context != null) {
                context.updateReaderStatus(result, isReaderConnected());
            }
        });
    }

    /**
     * Called when the activity pauses. Disconnects the reader.
     */
    void onPause() {
        disconnect();
    }

    /**
     * Cleans up resources when the activity is destroyed.
     */
    void onDestroy() {
        dispose();
        executor.shutdown();
    }

    /**
     * Initializes the RFID SDK by searching for available readers over USB and Bluetooth.
     */
    private void InitSDK() {
        Log.d(TAG, "InitSDK");
        if (readers == null) {
            executor.execute(() -> {
                InvalidUsageException exception = null;
                try {
                    readers = new Readers(context, ENUM_TRANSPORT.SERVICE_USB);
                    ArrayList<ReaderDevice> list = readers.GetAvailableRFIDReaderList();
                    availableRFIDReaderList = (list != null) ? new ArrayList<>(list) : new ArrayList<>();
                    
                    if (availableRFIDReaderList.isEmpty()) {
                        readers.setTransport(ENUM_TRANSPORT.BLUETOOTH);
                        list = readers.GetAvailableRFIDReaderList();
                        availableRFIDReaderList = (list != null) ? new ArrayList<>(list) : new ArrayList<>();
                    }
                } catch (InvalidUsageException e) {
                    exception = e;
                }
                
                final InvalidUsageException finalException = exception;
                if (context != null) {
                    context.runOnUiThread(() -> {
                        if (finalException != null) {
                            context.sendToast("Failed to get Available Readers\n" + finalException.getInfo());
                            readers = null;
                            context.updateReaderStatus("Failed to get Readers", false);
                        } else if (availableRFIDReaderList.isEmpty()) {
                            context.sendToast("No Available Readers to proceed");
                            readers = null;
                            context.updateReaderStatus("No Readers Found", false);
                        } else {
                            connectReader();
                        }
                    });
                }
            });
        } else {
            connectReader();
        }
    }

    /** Placeholder for test functionality. */
    public void testFunction() {}

    /**
     * Initiates the connection process for the RFID reader in a background thread.
     */
    private void connectReader() {
        if (context != null) {
            context.showProgressDialog("Searching and connecting to reader...");
        }

        final long startTime = System.currentTimeMillis();
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (context != null) {
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    context.showProgressDialog("Searching and connecting to reader... " + elapsed + "s");
                }
            }
        }, 1000, 1000);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (context != null) {
                        context.updateReaderStatus("Connecting...", false);
                    }

                    synchronized (RFIDHandler.this) {
                        if (!isReaderConnected()) {
                            GetAvailableReader();
                            String result = (reader != null) ? connect() : "Failed to find reader";
                            
                            if (context != null) {
                                if (isReaderConnected()) {
                                    long totalTime = (System.currentTimeMillis() - startTime) / 1000;
                                    result += " (" + totalTime + "s)";
                                }
                                context.updateReaderStatus(result, isReaderConnected());
                            }
                        } else {
                            if (context != null) {
                                context.updateReaderStatus("Connected: " + reader.getHostName(), true);
                            }
                        }
                    }
                } finally {
                    timer.cancel();
                    timer.purge();
                }
            }
        });
    }

    /**
     * Populates the available reader list and selects a reader to connect to.
     */
    private synchronized void GetAvailableReader() {
        if (readers != null) {
            readers.attach(this);
            try {
                ArrayList<ReaderDevice> availableReaders = readers.GetAvailableRFIDReaderList();
                if (availableReaders != null && !availableReaders.isEmpty()) {
                    availableRFIDReaderList = new ArrayList<>(availableReaders);
                    if (availableRFIDReaderList.size() == 1) {
                        readerDevice = availableReaders.get(0);
                        reader = readerDevice.getRFIDReader();
                    } else {
                        for (ReaderDevice device : availableRFIDReaderList) {
                            if (device != null && device.getName() != null && device.getName().startsWith(readerName)) {
                                readerDevice = device;
                                reader = readerDevice.getRFIDReader();
                                break;
                            }
                        }
                    }
                }
            } catch (InvalidUsageException e) {
                Log.e(TAG, "Error getting available readers", e);
            }
        }
    }

    /**
     * SDK Callback triggered when a new RFID reader is detected.
     */
    @Override
    public void RFIDReaderAppeared(ReaderDevice readerDevice) {
        connectReader();
    }

    /**
     * SDK Callback triggered when an RFID reader becomes unavailable.
     */
    @Override
    public void RFIDReaderDisappeared(ReaderDevice readerDevice) {
        if (context != null) context.sendToast("RFIDReaderDisappeared: " + readerDevice.getName());
        if (reader != null && readerDevice != null && readerDevice.getName().equals(reader.getHostName())) {
            disconnect();
        }
    }

    /**
     * Establishes a connection to the selected reader and configures events and scanner SDK.
     * 
     * @return Status message string.
     */
    private synchronized String connect() {
        if (reader != null) {
            try {
                if (!reader.isConnected()) {
                    reader.connect();
                    ConfigureReader();
                    if (reader.getHostName().startsWith("TC22R") || reader.getHostName().startsWith("RFID")){
                        //setup DW for TC22R and EM45, TC53e-RFID
                    }
                    else {
                        setupScannerSDK();
                    }
                    if (reader.isConnected()) {
                        return "Connected: " + reader.getHostName();
                    }
                } else {
                    return "Connected: " + reader.getHostName();
                }
            } catch (InvalidUsageException e) {
                Log.e(TAG, "Connection failed InvalidUsageException: " + e.getMessage());
                return "Connection failed InvalidUsageException: " + e.getMessage();
            } catch (OperationFailureException e) {
                Log.e(TAG, "Connection failed: " +  e.getResults());
                return "Connection failed: " + e.getResults();
            }
        }
        return "Disconnected";
    }

    /**
     * Configures the RFID reader to notify for handheld trigger, tag reads, and disconnects.
     */
    private void ConfigureReader() {
        IRFIDLogger.getLogger("SDKSampleApp").EnableDebugLogs(true);
        if (reader.isConnected()) {
            try {
                if (eventHandler == null) eventHandler = new EventHandler();
                reader.Events.addEventsListener(eventHandler);
                reader.Events.setHandheldEvent(true);
                reader.Events.setTagReadEvent(true);
                reader.Events.setAttachTagDataWithReadEvent(false);
                reader.Events.setReaderDisconnectEvent(true);
            } catch (InvalidUsageException | OperationFailureException e) {
                Log.e(TAG, "Configuration failed", e);
            }
        }
    }

    /**
     * Initializes the Zebra Scanner SDK and attempts to establish a session with the reader's scanner.
     */
    public void setupScannerSDK() {
        if (sdkHandler == null) {
            sdkHandler = new SDKHandler(context);
            sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_USB_CDC);
            sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL);
            sdkHandler.dcssdkSetDelegate(scannerHandler);
            int notifications_mask = DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value |
                    DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value |
                    DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value |
                    DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value |
                    DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value;
            sdkHandler.dcssdkSubsribeForEvents(notifications_mask);
        }

        ArrayList<DCSScannerInfo> availableScanners = (ArrayList<DCSScannerInfo>) sdkHandler.dcssdkGetAvailableScannersList();
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

    /**
     * Disconnects the RFID reader and terminates the scanner session.
     */
    private synchronized void disconnect() {
        try {
            if (reader != null) {
                if (eventHandler != null) reader.Events.removeEventsListener(eventHandler);
                if (sdkHandler != null) {
                    sdkHandler.dcssdkTerminateCommunicationSession(scannerID);
                }
                reader.disconnect();
                if (context != null)
                    context.updateReaderStatus("Disconnected", false);
                reader.Dispose();
                reader = null;
                sdkHandler = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during disconnect", e);
        }
    }

    /**
     * Completely releases reader and sdk resources.
     */
    private synchronized void dispose() {
        disconnect();
        try {
            if (readers != null) {
                readers.Dispose();
                readers = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during dispose", e);
        }
    }

    /**
     * Starts an inventory operation on the connected reader.
     */
    synchronized void performInventory() {
        try {
            if (reader != null && reader.isConnected()) reader.Actions.Inventory.perform();
        } catch (InvalidUsageException | OperationFailureException e) {
            Log.e(TAG, "Error performing inventory", e);
        }
    }

    /**
     * Stops a running inventory operation.
     */
    synchronized void stopInventory() {
        try {
            if (reader != null && reader.isConnected()) reader.Actions.Inventory.stop();
        } catch (InvalidUsageException | OperationFailureException e) {
            Log.e(TAG, "Error stopping inventory", e);
        }
    }

    /**
     * Triggers the barcode scanner to perform a scan.
     */
    public void scanCode() {
        String in_xml = "<inArgs><scannerID>" + scannerID + "</scannerID></inArgs>";
        executor.execute(() -> executeCommand(DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_DEVICE_PULL_TRIGGER, in_xml, new StringBuilder(), scannerID));
    }

    /**
     * Helper to execute a command on the Zebra Scanner SDK.
     */
    private boolean executeCommand(DCSSDKDefs.DCSSDK_COMMAND_OPCODE opCode, String inXML, StringBuilder outXML, int scannerID) {
        if (sdkHandler != null) {
            if (outXML == null) outXML = new StringBuilder();
            DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(opCode, inXML, outXML, scannerID);
            return result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS;
        }
        return false;
    }

    /**
     * Internal listener for RFID reader events such as tag data and status updates.
     */
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

    /**
     * Interface to be implemented by the UI to handle SDK events.
     */
    interface ResponseHandlerInterface {
        /** Called when RFID tags are read. */
        void handleTagdata(TagData[] tagData);
        /** Called when the reader's hardware trigger is pressed or released. */
        void handleTriggerPress(boolean pressed);
        /** Called when barcode data is received. */
        void barcodeData(String val);
        /** Utility to display toast messages. */
        void sendToast(String val);
        /** Shows a non-blocking progress dialog on the UI. */
        void showProgressDialog(String message);
        /** Dismisses the progress dialog from the UI. */
        void dismissProgressDialog();
    }
}
