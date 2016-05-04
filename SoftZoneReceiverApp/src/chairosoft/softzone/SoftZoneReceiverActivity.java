package chairosoft.softzone;

import static chairosoft.softzone.SoftZoneTransmitter.*;
import static chairosoft.softzone.SoftZoneTransmitter.ReceiverUtilities.*;

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
public class SoftZoneReceiverActivity extends Activity implements StreamingPlaybackUserInterface
{
    // Constants
    public static final String APP_NAME = "SoftZoneReceiver";
    public static final int CONNECTION_TIMEOUT_MS = 500;
    public static final int REQUEST_CODE_PAIRING = 1;
    
    // Instance Fields
    private final SoftZoneReceiverActivity self = this;
    
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
        
        StreamingPlaybackMasterThread activeThread = StreamingPlaybackMasterThread.getActiveThread();
        if (activeThread != null)
        {
            if (activeThread.userInterface != null && activeThread.userInterface != this)
            {
                ((Activity)activeThread.userInterface).finish();
                activeThread.userInterface = this;
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
            StreamingPlaybackMasterThread audioReceivingThread = new StreamingPlaybackMasterThread(
                this, 
                selectedName, 
                selectedHost, 
                selectedPortNumber, 
                selectedBufferLengthMs,
                CONNECTION_TIMEOUT_MS
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
        StreamingPlaybackMasterThread.stopPlayback();
    }
    
    
    //////////////////////////
    // Static Inner Classes //
    //////////////////////////
    /**
     * An Android-specific implementation of StreamingPlaybackDevice.
     */
    public static class AndroidStreamingPlaybackDevice extends StreamingPlaybackDevice
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
        protected final AudioTrack audioTrack;
        
        // Constructors
        public AndroidStreamingPlaybackDevice(int bufferLengthMs)
        {
            super(Protocol.AUDIO_BUFFER_SIZE_FOR_LENGTH_MS(bufferLengthMs));
            this.audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                Protocol.AUDIO_SAMPLES_PER_SECOND,
                PROTOCOL_CHANNEL,
                PROTOCOL_ENCODING,
                this.requestedBufferSizeInBytes,
                AudioTrack.MODE_STREAM
            );
        }
        
        // Instance Methods
        @Override
        public void close() throws IOException
        {
            this.audioTrack.stop();
            this.audioTrack.flush();
            this.audioTrack.release(); 
        }
        
        @Override
        public void initialize()
        {
            this.audioTrack.play();
        }
        
        @Override
        public int write(byte[] buffer, int offset, int length)
        {
            int result = this.audioTrack.write(buffer, offset, length);
            return (result > -1) ? result : -1;
        }
    }
    
    
    ////////////////////////////////////////////
    // StreamingPlaybackUserInterface methods //
    ////////////////////////////////////////////
    
    @Override
    public StreamingPlaybackDevice getPlaybackDevice(int bufferLengthMs)
    {
        return new AndroidStreamingPlaybackDevice(bufferLengthMs);
    }
    
    @Override
    public void updateMonitors(byte leftSampleHighByte, byte rightSampleHighByte)
    {
        final int leftLevelLinear = MonitorUpdatingThread.getLinearLevel(leftSampleHighByte);
        final int rightLevelLinear = MonitorUpdatingThread.getLinearLevel(rightSampleHighByte);
        //final int leftLevelDecibel = MonitorUpdatingThread.getDecibelLevel(leftLevelLinear);
        //final int rightLevelDecibel = MonitorUpdatingThread.getDecibelLevel(rightLevelLinear);
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            ProgressBar progressBarLeftLevel = (ProgressBar)self.findViewById(R.id.progressBarLeftLevel);
            ProgressBar progressBarRightLevel = (ProgressBar)self.findViewById(R.id.progressBarRightLevel);
            progressBarLeftLevel.setProgress(leftLevelLinear);
            progressBarRightLevel.setProgress(rightLevelLinear);
        }});
    }
    
    @Override
    public void handleExceptionDuringMonitorUpdating(Exception ex)
    {
        Log.e(APP_NAME, ex.toString());
    }
    
    @Override
    public String getStatusText(int bufferLengthMs)
    {
        String label_streaming_with_buffer_size = this.getString(R.string.label_streaming_with_buffer_size);
        return String.format("%s %sms", label_streaming_with_buffer_size, bufferLengthMs);
    }
    
    @Override
    public void setStatusText(final String statusText)
    {
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            ((TextView)self.findViewById(R.id.textStreamStatus)).setText(statusText);
        }});
    }
    
    @Override
    public void handleExceptionDuringSocketConnection(Exception ex)
    {
        Log.e(APP_NAME, ex.getMessage());
        
        final String message = String.format("Connection timeout (%sms)", CONNECTION_TIMEOUT_MS);
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            Toast toast = Toast.makeText(self, message, Toast.LENGTH_SHORT);
            toast.show();
        }});
    }
    
    @Override
    public void showShortPopupMessage(final String message)
    {
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            Toast toast = Toast.makeText(self, message, Toast.LENGTH_SHORT);
            toast.show();
        }});
    }
    
    @Override
    public void logStreamingStatistics(double bytesPerSecond, double averageReadTime, double averageWriteTime)
    {
        Log.e(APP_NAME, "bytes per second = " + bytesPerSecond);
        Log.e(APP_NAME, "average read time  (ms) = " + averageReadTime);
        Log.e(APP_NAME, "average write time (ms) = " + averageWriteTime);
    }
    
    @Override
    public void handleExceptionDuringStreaming(Exception ex)
    {
        Log.e(APP_NAME, ex.getMessage());
    }
    
    @Override
    public void clearMonitorLevelsAndStatus()
    {
        this.runOnUiThread(new Runnable() { @Override public void run()
        {
            ProgressBar progressBarLeftLevel = (ProgressBar)self.findViewById(R.id.progressBarLeftLevel);
            ProgressBar progressBarRightLevel = (ProgressBar)self.findViewById(R.id.progressBarRightLevel);
            progressBarLeftLevel.setProgress(0);
            progressBarRightLevel.setProgress(0);
            ((TextView)self.findViewById(R.id.textStreamStatus)).setText(R.string.label_not_streaming);
        }});
    }
}
