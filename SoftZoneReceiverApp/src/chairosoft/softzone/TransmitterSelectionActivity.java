package chairosoft.softzone;

import static chairosoft.softzone.SoftZoneTransmitter.*;
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
public class TransmitterSelectionActivity extends Activity
{
    // Constants
    public static final int SCAN_WORKER_THREAD_COUNT = 16;
    public static final int SCAN_CONNECTION_TIMEOUT_MS = 500;
    
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
        ScanningMasterThread.stopFlag = true;
    }
    
    public void startScan(View view)
    {
        try
        {
            EditText editTentativePort = (EditText)this.findViewById(R.id.editTentativePort);
            String portText = editTentativePort.getText().toString().trim();
            int port = Integer.parseInt(portText);
            ScanningMasterThread scanningThread = new ScanningMasterThread(this, port);
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
        ScanningMasterThread.stopFlag = true;
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
    
    
    //////////////////////////
    // Static Inner Classes //
    //////////////////////////
    
    public static class ScanResult
    {
        // Instance Fields
        public final String host;
        public final String name;
        
        // Constructor
        public ScanResult(String _host, String _name)
        {
            this.host = _host;
            this.name = _name;
        }
    }
    
    public static class ScanningWorkerThread extends Thread
    {
        // Instance Fields
        protected final ConcurrentLinkedQueue<String> unscannedHosts;
        protected final LinkedBlockingQueue<ScanResult> readyHosts;
        protected final ScanningMasterThread masterThread;
        
        // Constructor
        public ScanningWorkerThread(ConcurrentLinkedQueue<String>   _unscannedHosts, 
                                    LinkedBlockingQueue<ScanResult> _readyHosts,
                                    ScanningMasterThread            _masterThread
        )
        {
            this.unscannedHosts = _unscannedHosts;
            this.readyHosts = _readyHosts;
            this.masterThread = _masterThread;
        }
        
        // Instance Methods
        @Override 
        public void run()
        {
            final ScanningWorkerThread self = this;
            while (!ScanningMasterThread.stopFlag)
            {
                String host = this.unscannedHosts.poll();
                if (host == null) { break; }
                
                Socket socket = new Socket();
                InetSocketAddress socketAddress = new InetSocketAddress(host, this.masterThread.port);
                try
                {
                    socket.connect(socketAddress, SCAN_CONNECTION_TIMEOUT_MS);
                    try ( InputStream serverIn = socket.getInputStream()
                        ; OutputStream serverOut = socket.getOutputStream()
                    )
                    {
                        serverOut.write(Protocol.REQUEST_STATUS());
                        byte[] statusBuffer = new byte[1];
                        int statusBytesRead = serverIn.read(statusBuffer);
                        if (statusBytesRead > 0)
                        {
                            byte[] nameBuffer = new byte[Protocol.STATUS_NAME_MAX_LENGTH];
                            int nameBytesRead = serverIn.read(nameBuffer);
                            if (nameBytesRead > 0 && statusBuffer[0] == Protocol.STATUS_READY)
                            {
                                String name = new String(nameBuffer, 0, nameBytesRead, Protocol.STATUS_NAME_CHARSET);
                                ScanResult result = new ScanResult(host, name);
                                this.readyHosts.offer(result);
                            }
                        }
                    }
                }
                catch (SocketTimeoutException ex)
                {
                    // do nothing
                }
                catch (Exception ex)
                {
                    Log.e(APP_NAME, ex.toString());
                }
                finally
                {
                    this.masterThread.activity.runOnUiThread(new Runnable() { @Override public void run()
                    {
                        ProgressBar progressBarScan = (ProgressBar)self.masterThread.activity.findViewById(R.id.progressBarScan);
                        progressBarScan.incrementProgressBy(1);
                    }});
                }
            }
        }
    }
    
    public static class ScanningMasterThread extends Thread
    {
        // Locks
        public static final Object statusLock = new Object();
        
        // Static Variables
        protected static volatile byte status = Protocol.STATUS_READY;
        public static volatile boolean stopFlag = true;
        
        // Instance Fields
        public final TransmitterSelectionActivity activity;
        public final int port;
        
        // Constructor
        public ScanningMasterThread(TransmitterSelectionActivity _activity, int _port)
        {
            this.activity = _activity;
            this.port = _port;
        }
        
        // Instance Methods
        @Override
        public void run()
        {
            final ScanningMasterThread self = this;
            String localAddressPrefix = null;
            synchronized (ScanningMasterThread.statusLock)
            {
                if (ScanningMasterThread.status != Protocol.STATUS_READY)
                {
                    return;
                }
                
                try 
                {
                    // get local address prefix
                    localAddressPrefix = TransmitterSelectionActivity.getLocalAddressPrefix(this.activity);
                }
                catch (Exception ex)
                {
                    Log.e(APP_NAME, ex.getMessage());
                    final String errorMessage = "Error starting scan";
                    this.activity.runOnUiThread(new Runnable() { @Override public void run()
                    {
                        Toast toast = Toast.makeText(self.activity, errorMessage, Toast.LENGTH_SHORT);
                        toast.show();
                    }});
                    return;
                }
                
                ScanningMasterThread.status = Protocol.STATUS_BUSY;
                ScanningMasterThread.stopFlag = false;
            }
            
            // clear zone button list
            this.activity.runOnUiThread(new Runnable() { @Override public void run()
            {
                LinearLayout linearLayoutReadyZones = (LinearLayout)self.activity.findViewById(R.id.linearLayoutReadyZones);
                linearLayoutReadyZones.removeAllViews();
                ProgressBar progressBarScan = (ProgressBar)self.activity.findViewById(R.id.progressBarScan);
                progressBarScan.setProgress(0);
            }});
            
            
            // in queue
            ConcurrentLinkedQueue<String> unscannedHosts = new ConcurrentLinkedQueue<>();
            for (int i = 0; i < 255; ++i)
            {
                String nextUnscannedHost = localAddressPrefix + i;
                unscannedHosts.add(nextUnscannedHost);
            }
            
            // out queue
            final LinkedBlockingQueue<ScanResult> readyHosts = new LinkedBlockingQueue<>();
            
            // create and start worker threads
            ArrayList<Thread> workerThreads = new ArrayList<>(SCAN_WORKER_THREAD_COUNT);
            for (int i = 0; i < SCAN_WORKER_THREAD_COUNT; ++i)
            {
                Thread workerThread = new ScanningWorkerThread(unscannedHosts, readyHosts, this);
                workerThreads.add(workerThread);
                workerThread.start();
            }
            
            // create buttons for each ready host
            Thread buttonCreatorThread = new Thread() { @Override public void run() 
            {
                while (!ScanningMasterThread.stopFlag)
                {
                    try 
                    {
                        final ScanResult scanResult = readyHosts.poll(SCAN_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        if (scanResult == null) { continue; }
                        
                        // add to zone list
                        Log.e(APP_NAME, String.format("Found %s (%s)", scanResult.host, scanResult.name));
                        self.activity.runOnUiThread(new Runnable() { @Override public void run()
                        {
                            LinearLayout linearLayoutReadyZones = (LinearLayout)self.activity.findViewById(R.id.linearLayoutReadyZones);
                            Button buttonResult = new Button(self.activity);
                            buttonResult.setText(String.format("%s (%s)", scanResult.name, scanResult.host));
                            buttonResult.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v)
                            {
                                EditText editTentativeHost = (EditText)self.activity.findViewById(R.id.editTentativeHost);
                                EditText editTentativeName = (EditText)self.activity.findViewById(R.id.editTentativeName);
                                
                                editTentativeHost.setText(scanResult.host);
                                editTentativeName.setText(scanResult.name);
                            }});
                            linearLayoutReadyZones.addView(buttonResult);
                        }});
                    }
                    catch (InterruptedException ex)
                    {
                        Log.e(APP_NAME, ex.toString());
                        break;
                    }
                }
            }};
            buttonCreatorThread.start();
            
            try
            {
                // join worker threads
                for (Thread workerThread : workerThreads)
                {
                    workerThread.join();
                }
            }
            catch (Exception ex)
            {
                Log.e(APP_NAME, ex.getMessage());
                final String errorMessage = "Error while scanning";
                this.activity.runOnUiThread(new Runnable() { @Override public void run()
                {
                    Toast toast = Toast.makeText(self.activity, errorMessage, Toast.LENGTH_SHORT);
                    toast.show();
                }});
            }
            finally
            {
                synchronized (ScanningMasterThread.statusLock)
                {
                    ScanningMasterThread.status = Protocol.STATUS_READY;
                    ScanningMasterThread.stopFlag = true;
                }
                final String message = "Finished Scan";
                this.activity.runOnUiThread(new Runnable() { @Override public void run()
                {
                    Toast toast = Toast.makeText(self.activity, message, Toast.LENGTH_SHORT);
                    toast.show();
                    ProgressBar progressBarScan = (ProgressBar)self.activity.findViewById(R.id.progressBarScan);
                    progressBarScan.setProgress(progressBarScan.getMax());
                }});
            }
        }
    }
}
