/**
 */
package com.vensi.STDFUPlugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.st.BlueSTSDK.Manager;
import com.st.BlueSTSDK.Manager.ManagerListener;
import com.st.BlueSTSDK.Node;
import com.st.BlueSTSDK.Node.State;
import com.st.BlueSTSDK.Node.NodeStateListener;
import com.st.BlueSTSDK.gui.fwUpgrade.fwUpgradeConsole.FwUpgradeConsole;
import com.st.BlueSTSDK.Utils.FwVersion;
import com.st.BlueSTSDK.gui.fwUpgrade.fwUpgradeConsole.util.FwFileDescriptor;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class STDFUPlugin extends CordovaPlugin implements ManagerListener, NodeStateListener {
    private static final String TAG = "STDFUPlugin";

    private Node mNode;
    private Manager mManager;
    private FwUpgradeConsole mFwUpgradeConsole;

    private String mCurrentDeviceAddress;
    private String mBinFilePath = Environment.getExternalStorageDirectory() + "/DFU/BlueMS2_ST.bin";

    private Context mCordovaContext;
    private CallbackContext mCallbackContext;

    private int mFwFileLength;
    private int mLatestVersionNumber;

    //    private STDFUView mUpdateView;

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        mManager = Manager.getSharedInstance();
        mManager.addListener(this);

        mCordovaContext = cordova.getActivity().getApplicationContext();

//      Create STDFU folder if it doesn't exist
        File dir = new File(Environment.getExternalStorageDirectory() + "/STDFU");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // TODO: initialize view maybe?
    }

    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (mCallbackContext == null) {
            mCallbackContext = callbackContext;
        }

        switch (action) {
            case "checkUpdate":
                checkUpdate(args);
                break;
            case "initiateUpdate":
                initiateUpdate();
                break;
        }

        return true;
    }

    private void checkUpdate(JSONArray message) throws JSONException{
        JSONObject request = new JSONObject(message.getString(0));
        mCurrentDeviceAddress = request.getString("uuid");

        try {
            // Async task to start web socket server
            DownloadVersionTask dvt = new DownloadVersionTask();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                dvt.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
            } else {
                dvt.execute("");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void initiateUpdate() {
        DownloadFirmwareTask dft = new DownloadFirmwareTask();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            dft.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
        } else {
            dft.execute("");
        }
    }

    private BluetoothDevice getBluetoothDeviceWithAddress(String address) {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) mCordovaContext.getSystemService(Context.BLUETOOTH_SERVICE);
        List<BluetoothDevice> devices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);

        for (BluetoothDevice device : devices) {
            if (device.getAddress().equals(address)) {
                return device;
            }
        }

        return null;
    }

    private int getIntegerFromStringWithRegex(String string, String regex) {
        // Create a Pattern object
        Pattern r = Pattern.compile(regex);

        // Now create matcher object.
        Matcher m = r.matcher(string);
        if (m.find()) {
            return Integer.parseInt(m.group(0));
        }

        return -1;
    }

    /* ManagerListener Methods */

    public void onDiscoveryChange(Manager m, boolean enabled) {

    }

    public void onNodeDiscovered(Manager m, Node node) {
        if (node.getTag().equals(mCurrentDeviceAddress)) {
            mNode = node;
            node.addNodeStateListener(this);
            node.connect();
        }
    }

    /* NodeStateListener Methods */


    public void onStateChange(Node node, State newState, State prevState) {
        switch (newState) {
            case Connected:
                Log.v(TAG, "BlueSTSDKNodeStateConnected");
                mFwUpgradeConsole = FwUpgradeConsole.getFwUpgradeConsole(mNode);
                mFwUpgradeConsole.setLicenseConsoleListener(mConsoleListener);
                mFwUpgradeConsole.readVersion(FwUpgradeConsole.BOARD_FW);
                break;
            case Unreachable:
                Log.v(TAG, "BlueSTSDKNodeStateUnreachable");
                break;
            case Disconnecting:
                Log.v(TAG, "BlueSTSDKNodeStateDisconnecting");
                break;
            case Lost:
                Log.v(TAG, "BlueSTSDKNodeStateLost");
                break;
            default:
                Log.v(TAG, "State not handled");
        }
    }

    /* */

    private FwUpgradeConsole.FwUpgradeCallback mConsoleListener = new FwUpgradeConsole.SimpleFwUpgradeCallback() {
        public void onVersionRead(final FwUpgradeConsole console,
                                  @FwUpgradeConsole.FirmwareType final int fwType,
                                  final FwVersion version) {
            console.loadFw(FwUpgradeConsole.BOARD_FW, new FwFileDescriptor(mCordovaContext.getContentResolver(), Uri.parse(mBinFilePath)));
        }
    };


    /* Task to download version */
    private class DownloadVersionTask extends AsyncTask<String, Object, String> {
        @Override
        protected String doInBackground(String... params) {
            if (downloadVersion() != -1) {
                return "success";
            }


            return "fail";
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.equals("success")) {
                BluetoothDevice device = getBluetoothDeviceWithAddress(mCurrentDeviceAddress);
                int currentVersion = getIntegerFromStringWithRegex(device.getName(), "[0-9][0-9][0-9]");

                if (mLatestVersionNumber >= currentVersion) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(mCordovaContext.getApplicationContext(), AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                    builder.setMessage("New update available. Would you like to update your device?");
                    builder.setCancelable(false);

                    builder.setPositiveButton(
                            "Yes",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    PluginResult result = new PluginResult(PluginResult.Status.OK, "approved");
                                    result.setKeepCallback(true);
                                    mCallbackContext.sendPluginResult(result);
                                    dialog.cancel();
                                }
                            });

                    builder.setNegativeButton(
                            "No",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });

                    AlertDialog alert = builder.create();

                    alert.show();
                }
            }
        }

        private int downloadVersion() {
            try {
                URL downloadUrl = new URL("https://s3.amazonaws.com/sttile.blueapp.io/test/version.json");
                HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    JSONObject jObj;
                    InputStream is = conn.getInputStream();

                    String line;
                    StringBuilder sb = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);

                    while ((line = reader.readLine()) != null) {
                        sb.append(line + "n");
                    }

                    is.close();

                    jObj = new JSONObject(sb.toString());
                    mLatestVersionNumber = Integer.parseInt(jObj.getString("version"));
                    return mLatestVersionNumber;
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }

            return -1;
        }
    }

    /* Task to download firmware */
    private class DownloadFirmwareTask extends AsyncTask<String, Object, String> {
        @Override
        protected String doInBackground(String... params) {
            if (downloadFirmware() != -1) {
                return "success";
            }

            return "fail";
        }

        @Override
        protected void onPostExecute(String result) {
            if (result.equals("success")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (mCordovaContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        mManager.startDiscovery();
                    }
                } else {
                    mManager.startDiscovery();
                }
            }
        }

        private int downloadFirmware() {
            try {
                URL downloadUrl = new URL("https://s3.amazonaws.com/sttile.blueapp.io/test/BlueMS2_ST.bin");
                HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedInputStream bis = new BufferedInputStream(is);
                    FileOutputStream fos = new FileOutputStream(new File(Environment.getExternalStorageDirectory() + "/DFU/BlueMS2_ST.bin"));

                    int current = 0;
                    while ((current = bis.read()) != -1) {
                        fos.write(current);
                    }

                    fos.close();

                    return 1;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return -1;
        }
    }
}
