package chairosoft.softzone;

import static chairosoft.softzone.SoftZoneTransmitter.*;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.TextView;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * This is the main activity for the app.
 */
public class SoftZoneReceiverActivity extends Activity
{
    // Constants
    public static final String APP_NAME = "SoftZoneReceiver";
    public static final int CONNECTION_TIMEOUT_MS = 500;
    public static final int REQUEST_CODE_PAIRING = 1;
    
    // Instance Methods
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.e(APP_NAME, "SoftZoneReceiverActivity.onCreate()");
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.main);
        
        String lowLatencyMessage = this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
            ? "Device supports low latency audio"
            : "Device does not support low latency audio";
        ((TextView)this.findViewById(R.id.textLowLatency)).setText(lowLatencyMessage);
        
        AudioReceivingThread activeThread = AudioReceivingThread.activeThread;
        if (activeThread != null)
        {
            if (activeThread.activity != null && activeThread.activity != this)
            {
                activeThread.activity.finish();
                activeThread.activity = this;
            }
            ((TextView)this.findViewById(R.id.textSelectedName)).setText(activeThread.name);
            ((TextView)this.findViewById(R.id.textSelectedHost)).setText(activeThread.host);
            ((TextView)this.findViewById(R.id.textSelectedPort)).setText("" + activeThread.port);
            ((EditText)this.findViewById(R.id.editBufferLengthMs)).setText("" + activeThread.bufferLengthMs);
            ((TextView)this.findViewById(R.id.textStreamStatus)).setText(activeThread.statusText + ".");
        }
    }
    
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
        Log.e(APP_NAME, "SoftZoneReceiverActivity.onRestoreInstanceState()");
        super.onRestoreInstanceState(savedInstanceState);
    }
    
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        Log.e(APP_NAME, "SoftZoneReceiverActivity.onSaveInstanceState()");
        super.onSaveInstanceState(savedInstanceState);
    }
    
    @Override
    public void onRestart()
    {
        Log.e(APP_NAME, "SoftZoneReceiverActivity.onRestart()");
        super.onRestart();
    }
    
    @Override
    public void onStart()
    {
        Log.e(APP_NAME, "SoftZoneReceiverActivity.onStart()");
        super.onStart();
    }
    
    @Override
    public void onResume()
    {
        Log.e(APP_NAME, "SoftZoneReceiverActivity.onResume()");
        super.onResume();
    }
    
    @Override
    public void onPause()
    {
        Log.e(APP_NAME, "SoftZoneReceiverActivity.onPause()");
        super.onPause();
    }
    
    @Override
    public void onStop()
    {
        Log.e(APP_NAME, "SoftZoneReceiverActivity.onStop()");
        super.onStop();
    }
    
    @Override
    public void onDestroy()
    {
        Log.e(APP_NAME, "SoftZoneReceiverActivity.onDestroy()");
        super.onDestroy();
    }
    
    public void selectTransmitter(View view)
    {
        Intent intent = new Intent(this, TransmitterSelectionActivity.class);
        this.startActivityForResult(intent, REQUEST_CODE_PAIRING);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_CODE_PAIRING)
        {
            if (resultCode == RESULT_OK)
            {
                String selectedName = TransmitterSelectionActivity.getSelectedName(data);
                String selectedHost = TransmitterSelectionActivity.getSelectedHost(data);
                int selectedPort = TransmitterSelectionActivity.getSelectedPort(data);
                
                ((TextView)this.findViewById(R.id.textSelectedName)).setText(selectedName);
                ((TextView)this.findViewById(R.id.textSelectedHost)).setText(selectedHost);
                ((TextView)this.findViewById(R.id.textSelectedPort)).setText("" + selectedPort);
            }
        }
    }
    
    public void startStreaming(View view)
    {
        String errorMessage = null;
        try
        {
            String selectedName = ((TextView)this.findViewById(R.id.textSelectedName)).getText().toString();
            String selectedHost = ((TextView)this.findViewById(R.id.textSelectedHost)).getText().toString();
            String selectedPort = ((TextView)this.findViewById(R.id.textSelectedPort)).getText().toString();
            String selectedBufferLength = ((EditText)this.findViewById(R.id.editBufferLengthMs)).getText().toString();
            
            errorMessage = "Invalid port.";
            int selectedPortNumber = Integer.parseInt(selectedPort);
            
            errorMessage = "Invalid buffer length ms.";
            short selectedBufferLengthMs = Short.parseShort(selectedBufferLength);
            
            errorMessage = "Error starting stream.";
            AudioReceivingThread audioReceivingThread = new AudioReceivingThread(
                this, 
                selectedName, 
                selectedHost, 
                selectedPortNumber, 
                selectedBufferLengthMs
            );
            audioReceivingThread.start();
        }
        catch (Exception ex)
        {
            Log.e(APP_NAME, ex.getMessage());
            if (errorMessage == null)
            {
                throw new RuntimeException(ex);
            }
            
            Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
            toast.show();
        }
    }
    
    public void stopStreaming(View view)
    {
        AudioReceivingThread.stopFlag = true;
    }
    
    
    //////////////////////////
    // Static Inner Classes //
    //////////////////////////
    
    /**
     * An AudioTrack specific to SoftZone.
     */
    public static class SoftZoneAudioTrack extends AudioTrack implements AutoCloseable
    {
        // Constants
        public static final int PROTOCOL_CHANNEL = 
            Protocol.AUDIO_CHANNELS == 2 
                ? AudioFormat.CHANNEL_OUT_STEREO 
                : Protocol.AUDIO_CHANNELS == 1 
                    ? AudioFormat.CHANNEL_OUT_MONO
                    : AudioFormat.CHANNEL_INVALID;
        public static final int PROTOCOL_ENCODING = 
            Protocol.AUDIO_SAMPLE_SIZE_IN_BYTES == 2 
                ? AudioFormat.ENCODING_PCM_16BIT 
                : Protocol.AUDIO_SAMPLE_SIZE_IN_BYTES == 1 
                    ? AudioFormat.ENCODING_PCM_8BIT
                    : AudioFormat.ENCODING_INVALID;
        
        // Instance Fields
        public final int requestedBufferSizeInBytes;
        
        // Constructor
        public SoftZoneAudioTrack(int bufferLengthMs)
        {
            super(
                AudioManager.STREAM_MUSIC,
                Protocol.AUDIO_SAMPLES_PER_SECOND,
                PROTOCOL_CHANNEL,
                PROTOCOL_ENCODING,
                Protocol.AUDIO_BUFFER_SIZE_FOR_LENGTH_MS(bufferLengthMs),
                AudioTrack.MODE_STREAM
            );
            this.requestedBufferSizeInBytes = Protocol.AUDIO_BUFFER_SIZE_FOR_LENGTH_MS(bufferLengthMs);
        }
        
        // Instance Methods
        @Override
        public void close() throws IOException
        {
            this.stop();
            this.flush();
            this.release(); 
        }
    }
    
    /**
     * This thread takes care of updating the monitors for
     * currently playing audio.
     */
    public static class MonitorUpdatingThread extends Thread
    {
        // Constants
        public static final int MONITOR_BUFFER_LENGTH = 4;
        public static final int MONITOR_LEFT_INDEX = 1;
        public static final int MONITOR_RIGHT_INDEX = 3;
        public static final int MIN_DB = (int)(20 * Math.log10(1 / 128.0));
        public static final int MAX_DB_UNADJUSTED = -MIN_DB;
        
        // Instance Fields
        public final PipedInputStream levelIn;
        public final AudioReceivingThread audioReceivingThread;
        
        // Constructor
        public MonitorUpdatingThread(PipedInputStream _levelIn, AudioReceivingThread _audioReceivingThread)
        {
            this.levelIn = _levelIn;
            this.audioReceivingThread = _audioReceivingThread;
        }
        
        // Static Methods
        public static int getLinearLevel(byte sampleHighByte)
        {
            int amplitude = Math.abs((int)sampleHighByte);
            if (amplitude < 0) { amplitude = 0; }
            else if (amplitude > 128) { amplitude = 128; }
            return amplitude;
        }
        
        public static int getDecibelLevel(int linearLevel)
        {
            if (linearLevel == 0) { linearLevel = 1; }
            double db = 20 * Math.log10(linearLevel / 128.0);
            int level = MAX_DB_UNADJUSTED + (int)db;
            return (int)(level * 128.0 / MAX_DB_UNADJUSTED);
        }
        
        // Instance Methods
        @Override 
        public void run()
        {
            final AudioReceivingThread masterThread = this.audioReceivingThread;
            try
            {
                byte[] levelBuffer = new byte[MONITOR_BUFFER_LENGTH];
                boolean ever = true;
                for (;ever;)
                {
                    int bytesRead = levelIn.read(levelBuffer, 0, MONITOR_BUFFER_LENGTH);
                    if (bytesRead < MONITOR_BUFFER_LENGTH) { continue; }
                    
                    final int leftLevelLinear = MonitorUpdatingThread.getLinearLevel(levelBuffer[MONITOR_LEFT_INDEX]);
                    final int rightLevelLinear = MonitorUpdatingThread.getLinearLevel(levelBuffer[MONITOR_RIGHT_INDEX]);
                    final int leftLevelDecibel = MonitorUpdatingThread.getDecibelLevel(leftLevelLinear);
                    final int rightLevelDecibel = MonitorUpdatingThread.getDecibelLevel(rightLevelLinear);
                    masterThread.activity.runOnUiThread(new Runnable() { @Override public void run()
                    {
                        ProgressBar progressBarLeftLevel = (ProgressBar)masterThread.activity.findViewById(R.id.progressBarLeftLevel);
                        ProgressBar progressBarRightLevel = (ProgressBar)masterThread.activity.findViewById(R.id.progressBarRightLevel);
                        progressBarLeftLevel.setProgress(leftLevelLinear);
                        progressBarRightLevel.setProgress(rightLevelLinear);
                    }});
                }
            }
            catch (Exception ex)
            {
                Log.e(APP_NAME, ex.toString());
            }
        }
    }
    
    
    /**
     * This thread receives and plays audio from the selected server.
     */
    public static class AudioReceivingThread extends Thread
    {
        // Locks
        public static final Object statusLock = new Object();
        
        // Static Variables
        protected static volatile byte status = Protocol.STATUS_READY;
        public static volatile boolean stopFlag = true;
        public static volatile AudioReceivingThread activeThread = null;
        
        // Instance Fields
        public volatile SoftZoneReceiverActivity activity;
        public final String name;
        public final String host;
        public final int port;
        public final short bufferLengthMs;
        public final String statusText;
        
        // Constructor
        public AudioReceivingThread(SoftZoneReceiverActivity _activity, String _name, String _host, int _port, short _bufferLengthMs)
        {
            this.activity = _activity;
            this.name = _name;
            this.host = _host;
            this.port = _port;
            this.bufferLengthMs = _bufferLengthMs;
            
            String label_streaming_with_buffer_size = this.activity.getString(R.string.label_streaming_with_buffer_size);
            this.statusText = String.format("%s %sms", label_streaming_with_buffer_size, this.bufferLengthMs);
        }
        
        // Instance Methods
        @Override
        public void run()
        {
            final AudioReceivingThread self = this;
            synchronized (AudioReceivingThread.statusLock)
            {
                if (AudioReceivingThread.status != Protocol.STATUS_READY) 
                {
                    return;
                }
                AudioReceivingThread.status = Protocol.STATUS_BUSY;
                AudioReceivingThread.stopFlag = false;
                AudioReceivingThread.activeThread = this;
            }
            
            // open connection
            Socket socket = new Socket();
            InetSocketAddress serverAddress = new InetSocketAddress(this.host, this.port);
            try
            {
                socket.connect(serverAddress, CONNECTION_TIMEOUT_MS);
            }
            catch (Exception ex)
            {
                Log.e(APP_NAME, ex.getMessage());
                
                final String message = String.format("Connection timeout (%sms)", CONNECTION_TIMEOUT_MS);
                this.activity.runOnUiThread(new Runnable() { @Override public void run()
                {
                    Toast toast = Toast.makeText(self.activity, message, Toast.LENGTH_SHORT);
                    toast.show();
                }});
                
                synchronized (AudioReceivingThread.statusLock)
                {
                    AudioReceivingThread.status = Protocol.STATUS_READY;
                    AudioReceivingThread.stopFlag = true;
                    AudioReceivingThread.activeThread = null;
                }
                return;
            }
            
            // start streaming
            try ( SoftZoneAudioTrack audioTrack = new SoftZoneAudioTrack(this.bufferLengthMs)
                ; InputStream serverIn = socket.getInputStream()
                ; OutputStream serverOut = socket.getOutputStream()
                ; PipedInputStream levelIn = new PipedInputStream()
                ; PipedOutputStream levelOut = new PipedOutputStream(levelIn)
            )
            {
                // change status
                this.activity.runOnUiThread(new Runnable() { @Override public void run()
                {
                    ((TextView)self.activity.findViewById(R.id.textStreamStatus)).setText(self.statusText);
                }});
                
                // monitor levels
                Thread monitorThread = new MonitorUpdatingThread(levelIn, this);
                monitorThread.start();
                
                // make audio track ready
                byte[] buffer = new byte[audioTrack.requestedBufferSizeInBytes];
                audioTrack.play();
                
                // handshake with server
                serverOut.write(Protocol.REQUEST_STREAMING());
                byte response = (byte)serverIn.read();
                if (response != Protocol.STREAMING_SERVER_ACCEPT)
                {
                    String message = "Server rejected connection.";
                    throw new IOException(message);
                }
                
                // send buffer length (ms)
                Protocol.STREAMING_WRITE_BUFFER_LENGTH_MS(serverOut, this.bufferLengthMs);
                
                long readTimeTotal = 0;
                long writeTimeTotal = 0;
                long numberOfLoops = 0;
                // long totalBytesRead = 0;
                // long startTimeNanos = System.nanoTime();
                try
                {
                    while (!AudioReceivingThread.stopFlag)
                    {
                        while (serverIn.available() > buffer.length)
                        {
                            serverIn.skip(buffer.length);  // in case the client drifts behind the server
                        }
                        long preRead = System.nanoTime();
                        int bytesRead = serverIn.read(buffer, 0, buffer.length);
                        long readTime = System.nanoTime() - preRead;
                        if (bytesRead < 0) { break; }
                        long preWrite = System.nanoTime();
                        audioTrack.write(buffer, 0, bytesRead);
                        if (bytesRead >= MonitorUpdatingThread.MONITOR_BUFFER_LENGTH)
                        {
                            levelOut.write(buffer, 0, MonitorUpdatingThread.MONITOR_BUFFER_LENGTH);
                        }
                        long writeTime = System.nanoTime() - preWrite;
                        readTimeTotal += readTime;
                        writeTimeTotal += writeTime;
                        ++numberOfLoops;
                        // totalBytesRead += bytesRead;
                    }
                    final String message = "Stream stopped.";
                    this.activity.runOnUiThread(new Runnable() { @Override public void run()
                    {
                        Toast toast = Toast.makeText(self.activity, message, Toast.LENGTH_SHORT);
                        toast.show();
                    }});
                }
                finally
                {
                    // // for 2 bytes per channel-sample, 2 channel-samples per frame, and 48000 frames per second:
                    // // we would expect 192000 bytes per second
                    // long totalTimeNanos = System.nanoTime() - startTimeNanos;
                    // Log.e(APP_NAME, "bytes per second = " + 1000*1000*1000.*totalBytesRead / totalTimeNanos);
                    Log.e(APP_NAME, "average read time  (ms) = " + readTimeTotal / (1000*1000. * numberOfLoops));
                    Log.e(APP_NAME, "average write time (ms) = " + writeTimeTotal / (1000*1000. * numberOfLoops));
                }
            }
            catch (Exception ex)
            {
                Log.e(APP_NAME, ex.getMessage());
                
                final String errorMessage = "An error occurred while streaming.";
                this.activity.runOnUiThread(new Runnable() { @Override public void run()
                {
                    Toast toast = Toast.makeText(self.activity, errorMessage, Toast.LENGTH_SHORT);
                    toast.show();
                }});
            }
            finally
            {
                synchronized (AudioReceivingThread.statusLock)
                {
                    AudioReceivingThread.status = Protocol.STATUS_READY;
                    AudioReceivingThread.stopFlag = true;
                    AudioReceivingThread.activeThread = null;
                }
                
                // clear monitor levels and status
                this.activity.runOnUiThread(new Runnable() { @Override public void run()
                {
                    ProgressBar progressBarLeftLevel = (ProgressBar)self.activity.findViewById(R.id.progressBarLeftLevel);
                    ProgressBar progressBarRightLevel = (ProgressBar)self.activity.findViewById(R.id.progressBarRightLevel);
                    progressBarLeftLevel.setProgress(0);
                    progressBarRightLevel.setProgress(0);
                    ((TextView)self.activity.findViewById(R.id.textStreamStatus)).setText(R.string.label_not_streaming);
                }});
            }
        }
    }
}
