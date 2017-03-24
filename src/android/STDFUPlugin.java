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
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.FrameLayout;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class STDFUPlugin extends CordovaPlugin implements ManagerListener, NodeStateListener, FwUpgradeConsole.FwUpgradeCallback {
    private static final String TAG = "STDFUPlugin";
    
    private Node mNode;
    private Manager mManager;
    private FwUpgradeConsole mFwUpgradeConsole;
    
    private String mCurrentDeviceAddress;
    
    private Activity mActivity;
    private Context mCordovaContext;
    private CallbackContext mCallbackContext;
    
    private long mFwFileLength;
    private int mLatestVersionNumber;
    
    private STDFUView mUpdateView;
    
    private boolean updating = false;
    
    private String mFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/STDFU/BlueMS2_ST.bin";
    
    private Handler mTimeoutHandler = new Handler();
    
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        
        mManager = Manager.getSharedInstance();
        mManager.addListener(this);
        
        mActivity = cordova.getActivity();
        mCordovaContext = cordova.getActivity().getApplicationContext();
        
        //      Create STDFU folder if it doesn't exist
        File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/STDFU");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        mUpdateView = new STDFUView(mActivity);
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
        mCurrentDeviceAddress = message.getString(0);
        
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                
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
        });
    }
    
    private void initiateUpdate() {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                DownloadFirmwareTask dft = new DownloadFirmwareTask();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    dft.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "");
                } else {
                    dft.execute("");
                }
            }
        });
    }
    
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private BluetoothDevice getBluetoothDeviceWithAddress(String address) {
        final BluetoothManager bluetoothManager =
        (BluetoothManager) mCordovaContext.getSystemService(Context.BLUETOOTH_SERVICE);
        List<BluetoothDevice> devices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        
        if (devices.size() > 0) {
            for (BluetoothDevice device : devices) {
                if (device.getAddress().equals(address)) {
                    return device;
                }
            }
        } else {
            BluetoothAdapter adapter = bluetoothManager.getAdapter();
            BluetoothDevice device = adapter.getRemoteDevice(address);
            
            //            if (device != null) {
            return (device != null) ? device : null;
            //            }
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
            node.connect(mCordovaContext);
        }
    }
    
    /* NodeStateListener Methods */
    
    public void onStateChange(Node node, State newState, State prevState) {
        switch (newState) {
            case Connected:
                Log.v(TAG, "BlueSTSDKNodeStateConnected");
                mFwUpgradeConsole = FwUpgradeConsole.getFwUpgradeConsole(mNode);
                assert mFwUpgradeConsole != null;
                mFwUpgradeConsole.setLicenseConsoleListener(this);
                mFwUpgradeConsole.readVersion(FwUpgradeConsole.BOARD_FW);
                break;
            case Unreachable:
                Log.v(TAG, "BlueSTSDKNodeStateUnreachable");
                break;
            case Disconnecting:
                Log.v(TAG, "BlueSTSDKNodeStateDisconnecting");
                onLoadFwError(null, null, BLUESTSDK_FWUPGRADE_UPLOAD_ERROR_TRANSMISSION+2);
                break;
            case Lost:
                Log.v(TAG, "BlueSTSDKNodeStateLost");
                onLoadFwError(null, null, BLUESTSDK_FWUPGRADE_UPLOAD_ERROR_TRANSMISSION+2);
                break;
            default:
                Log.v(TAG, "State not handled");
        }
    }
    
    /* FwUpgradeConsole.FwUpgradeCallback */
    
    @Override
    public void onVersionRead(final FwUpgradeConsole fwUpgradeConsole, int i, FwVersion fwVersion) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                Log.d(TAG, "onVersionRead");
                FrameLayout parent = (FrameLayout) webView.getView().getParent();
                mUpdateView.showProgressView(parent);
                
                File file = new File(mFilePath);
                mFwFileLength = file.length();
                fwUpgradeConsole.loadFw(FwUpgradeConsole.BOARD_FW, new FwFileDescriptor(mCordovaContext.getContentResolver(), Uri.fromFile(file)));
            }
        });
    }
    
    @Override
    public void onLoadFwComplete(FwUpgradeConsole fwUpgradeConsole, FwFileDescriptor fwFileDescriptor) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                Log.d(TAG, "onLoadFwComplete");
                updating = false;
                
                FrameLayout parent = (FrameLayout)webView.getView().getParent();
                mUpdateView.removeOverlay(parent);
                
                PluginResult pr = new PluginResult(PluginResult.Status.OK, "updated");
                pr.setKeepCallback(true);
                mCallbackContext.sendPluginResult(pr);
            }
        });
    }
    
    @Override
    public void onLoadFwError(FwUpgradeConsole fwUpgradeConsole, FwFileDescriptor fwFileDescriptor, int i) {
        Log.d(TAG, "onLoadFwError");
        
        String message = "failed";
        
        if (i == BLUESTSDK_FWUPGRADE_UPLOAD_ERROR_TRANSMISSION + 2) {
            message = "connection failed";
        }
        
        PluginResult result = new PluginResult(Status.ERROR, message);
        result.setKeepCallback(true);
        mCallbackContext.sendPluginResult(result);
        
        FrameLayout parent = (FrameLayout)webView.getView().getParent();
        mUpdateView.removeOverlay(parent);
    }
    
    @Override
    public void onLoadFwProgressUpdate(FwUpgradeConsole fwUpgradeConsole, FwFileDescriptor fwFileDescriptor, final long l) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                if (updating == false) {
                    updating = true;
                }
                
                mTimeoutHandler.removeCallbacks(timeout);
                
                Log.d(TAG, "onLoadFwProgressUpdate");
                long percentage = ((mFwFileLength - l) * 100) / mFwFileLength;
                mUpdateView.updateProgressView((long)percentage);
                
                mTimeoutHandler.postDelayed(timeout, 5000);
            }
        });
    }
    
    
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
                
                if (device != null) {
                    int currentVersion = getIntegerFromStringWithRegex(device.getName(), "[0-9][0-9][0-9]");
                    
                    if (mLatestVersionNumber > currentVersion) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                        builder.setMessage("New update available. Would you like to update your device?");
                        builder.setCancelable(false);
                        
                        builder.setPositiveButton(
                                                  "Yes",
                                                  new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                FrameLayout parent = (FrameLayout) webView.getView().getParent();
                                mUpdateView.showOverlay(parent, "Preparing device for update. Please wait. This may take 5 minutes.");
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
                                PluginResult result = new PluginResult(PluginResult.Status.OK, "none");
                                result.setKeepCallback(true);
                                mCallbackContext.sendPluginResult(result);
                                dialog.cancel();
                            }
                        });
                        
                        AlertDialog alert = builder.create();
                        
                        alert.show();
                    }
                } else {
                    
                }
            } else {
                PluginResult pr = new PluginResult(PluginResult.Status.OK, "none");
                pr.setKeepCallback(true);
                mCallbackContext.sendPluginResult(pr);
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
                        sb.append(line).append("n");
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
                    if (mActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
                    FileOutputStream writer = new FileOutputStream( new File(mFilePath) );
                    
                    int current;
                    while ((current = bis.read()) != -1) {
                        writer.write( (byte) current);
                    }
                    
                    writer.close();
                    bis.close();
                    
                    return 1;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            return 1;
        }
    }
    
    /* Internal Tiemout handler */
    Runnable timeout = new Runnable() {
        @Override
        public void run() {
            onLoadFwError(null, null, BLUESTSDK_FWUPGRADE_UPLOAD_ERROR_TRANSMISSION);
        }
    };
}
