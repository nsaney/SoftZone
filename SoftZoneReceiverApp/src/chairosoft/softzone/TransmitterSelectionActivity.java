package chairosoft.softzone;

import static chairosoft.softzone.SoftZoneTransmitter.*;
import static chairosoft.softzone.SoftZoneTransmitter.ReceiverUtilities.*;
import static chairosoft.softzone.SoftZoneReceiverActivity.*;

import android.app.Activity;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.TextView;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This activity allows the user to (search for and) select 
 * a SoftZone Transmitter (IP and port) to receive from.
 */
public class TransmitterSelectionActivity extends Activity implements ScanningUserInterface
{
    // Constants
    public static final int SCAN_WORKER_THREAD_COUNT = 16;
    public static final int SCAN_CONNECTION_TIMEOUT_MS = 500;
    
    // Instance Fields
    private final TransmitterSelectionActivity self = this;
    
    // Static Methods
    public static String getLocalAddressPrefix(Activity activity) throws UnknownHostException
    {
        WifiManager wifiManager = (WifiManager)activity.getSystemService(WIFI_SERVICE);
        int localHostIp = wifiManager.getConnectionInfo().getIpAddress();
        
        byte[] localHostIpBytes = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(localHostIp)
            .array();
            
        return String.format("%s.%s.%s.", localHostIpBytes[0] & 0xff, (int)localHostIpBytes[1] & 0xff, (int)localHostIpBytes[2] & 0xff);
    }
    
    // Instance Methods
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.transmitter_selection);
        final EditText editTentativeName = (EditText)this.findViewById(R.id.editTentativeName);
        final EditText editTentativeHost = (EditText)this.findViewById(R.id.editTentativeHost);
        final EditText editTentativePort = (EditText)this.findViewById(R.id.editTentativePort);
        try
        {
            String localAddressPrefix = TransmitterSelectionActivity.getLocalAddressPrefix(this);
            editTentativeHost.setText(localAddressPrefix);
            editTentativeHost.setSelection(localAddressPrefix.length());
            
            editTentativePort.setText("" + Protocol.CONNECTION_SERVER_PORT);
        }
        catch (Exception ex)
        {
            editTentativeHost.setText("");
        }
        
        editTentativeName.setKeyListener(null);
        editTentativeHost.addTextChangedListener(new TextWatcher()
        {
            @Override 
            public void beforeTextChanged(CharSequence s, int start, int count, int after) 
            {
                // nothing here
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) 
            {
                editTentativeName.setText(R.string.label_manual_entry);
            }
            
            @Override
            public void afterTextChanged(Editable s)
            {
                // nothing here
            }
        });
    }
    
    @Override
    public void onPause()
    {
        super.onPause();
        ScanningMasterThread.stopScanning();
    }
    
    public void startScan(View view)
    {
        try
        {
            EditText editTentativePort = (EditText)this.findViewById(R.id.editTentativePort);
            String portText = editTentativePort.getText().toString().trim();
            int port = Integer.parseInt(portText);
            ScanningMasterThread scanningThread = new ScanningMasterThread(this, port, SCAN_WORKER_THREAD_COUNT, SCAN_CONNECTION_TIMEOUT_MS);
            scanningThread.start();
        }
        catch (Exception ex)
        {
            String errorMessage = "Invalid Port";
            Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
            toast.show();
        }
    }
    
    public void stopScan(View view)
    {
        ScanningMasterThread.stopScanning();
    }
    
    public static final String EXTRA_SELECTED_NAME = "selectedName";
    public static String getSelectedName(Intent resultIntent) { return resultIntent.getStringExtra(EXTRA_SELECTED_NAME); }
    
    public static final String EXTRA_SELECTED_HOST = "selectedHost";
    public static String getSelectedHost(Intent resultIntent) { return resultIntent.getStringExtra(EXTRA_SELECTED_HOST); }
    
    public static final String EXTRA_SELECTED_PORT = "selectedPort";
    public static int getSelectedPort(Intent resultIntent) { return resultIntent.getIntExtra(EXTRA_SELECTED_PORT, -1); }
    
    public static void putSelectedData(Intent resultIntent, String selectedName, String selectedHost, int selectedPort) 
    {
        resultIntent.putExtra(EXTRA_SELECTED_NAME, selectedName); 
        resultIntent.putExtra(EXTRA_SELECTED_HOST, selectedHost);
        resultIntent.putExtra(EXTRA_SELECTED_PORT, selectedPort);
    }
    
    public void selectServer(View view)
    {
        String errorMessage = null;
        try
        {
            Intent resultIntent = new Intent();
            EditText editTentativeName = (EditText)this.findViewById(R.id.editTentativeName);
            EditText editTentativeHost = (EditText)this.findViewById(R.id.editTentativeHost);
            EditText editTentativePort = (EditText)this.findViewById(R.id.editTentativePort);
            
            String name = editTentativeName.getText().toString().trim();
            String host = editTentativeHost.getText().toString().trim();
            String portText = editTentativePort.getText().toString().trim();
            
            errorMessage = "Invalid server IP address.";
            if (host  == null || host.length() == 0)
            {
                throw new IllegalStateException(errorMessage);
            }
            InetAddress hostNetAddress = InetAddress.getByName(host);
            
            errorMessage = "Invalid server port.";
            int port = Integer.parseInt(portText);
            
            errorMessage = null;
            TransmitterSelectionActivity.putSelectedData(resultIntent, name, host, port);
            this.setResult(RESULT_OK, resultIntent);
            this.finish();
        }
        catch (Exception ex)
        {
            if (errorMessage == null)
            {
                throw new RuntimeException(ex);
            }
            else
            {
                Log.e(APP_NAME, errorMessage);
                Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }
    
    public void cancelResult(View view)
    {
        this.setResult(RESULT_CANCELED);
        this.finish();
    }
    
    
    ///////////////////////////////////
    // ScanningUserInterface methods //
    ///////////////////////////////////
    
    @Override
    public void handleExceptionDuringScanWorkerExecution(Exception ex)
    {
        Log.e(APP_NAME, ex.toString());
    }
    
    @Override
    public void incrementScanProgressBy(final int amount)
    {
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            ProgressBar progressBarScan = (ProgressBar)self.findViewById(R.id.progressBarScan);
            progressBarScan.incrementProgressBy(amount);
        }});
    }
    
    @Override
    public String getLocalAddressPrefix() throws Exception
    {
        return TransmitterSelectionActivity.getLocalAddressPrefix(this);
    }
    
    @Override
    public void handleExceptionDuringScanStart(Exception ex)
    {
        Log.e(APP_NAME, ex.getMessage());
        final String errorMessage = "Error starting scan";
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            Toast toast = Toast.makeText(self, errorMessage, Toast.LENGTH_SHORT);
            toast.show();
        }});
    }
    
    @Override
    public void resetScanReadyZonesAndProgress()
    {
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            LinearLayout linearLayoutReadyZones = (LinearLayout)self.findViewById(R.id.linearLayoutReadyZones);
            linearLayoutReadyZones.removeAllViews();
            ProgressBar progressBarScan = (ProgressBar)self.findViewById(R.id.progressBarScan);
            progressBarScan.setProgress(0);
        }});
    }
    
    @Override
    public void addReadyScanResult(final ScanResult scanResult)
    {
        Log.e(APP_NAME, String.format("Found %s (%s)", scanResult.host, scanResult.name));
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            LinearLayout linearLayoutReadyZones = (LinearLayout)self.findViewById(R.id.linearLayoutReadyZones);
            Button buttonResult = new Button(self);
            buttonResult.setText(String.format("%s (%s)", scanResult.name, scanResult.host));
            buttonResult.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v)
            {
                EditText editTentativeHost = (EditText)self.findViewById(R.id.editTentativeHost);
                EditText editTentativeName = (EditText)self.findViewById(R.id.editTentativeName);
                
                editTentativeHost.setText(scanResult.host);
                editTentativeName.setText(scanResult.name);
            }});
            linearLayoutReadyZones.addView(buttonResult);
        }});
    }
    
    @Override
    public void handleInterruptedExceptionDuringButtonCreation(InterruptedException ex)
    {
        Log.e(APP_NAME, ex.toString());
    }
    
    @Override
    public void handleExceptionDuringWorkerThreadJoin(Exception ex)
    {
        Log.e(APP_NAME, ex.getMessage());
        final String errorMessage = "Error while scanning";
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            Toast toast = Toast.makeText(self, errorMessage, Toast.LENGTH_SHORT);
            toast.show();
        }});
    }
    
    @Override
    public void showScanFinishedSuccessfully()
    {
        final String message = "Finished Scan";
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            Toast toast = Toast.makeText(self, message, Toast.LENGTH_SHORT);
            toast.show();
            ProgressBar progressBarScan = (ProgressBar)self.findViewById(R.id.progressBarScan);
            progressBarScan.setProgress(progressBarScan.getMax());
        }});
    }
}
