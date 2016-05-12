/* 
 * Nicholas Saney
 * 
 * Created: January 01, 2016
 * 
 * SoftZoneReceiverProgram.java 
 * SoftZoneReceiverProgram main and auxiliary methods
 */

package chairosoft.softzone;

import static chairosoft.softzone.SoftZoneTransmitter.*;
import static chairosoft.softzone.SoftZoneTransmitter.ReceiverUtilities.*;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;

import java.util.function.Function;
import java.util.function.Predicate;

import java.awt.*;
import javax.swing.*;

import java.awt.event.*;
import javax.swing.event.*;

import java.awt.image.*;
import javax.imageio.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class SoftZoneReceiverProgram
{
    ///////////////
    // Constants //
    ///////////////
    
    public static final String APP_NAME = "SoftZone Receiver";
    public static final String ICON_LOCATION = "/img/soft_zone_receiver_icon_2.png";
    public static final int APP_MARGIN_VERTICAL = 15;
    public static final int APP_MARGIN_HORIZONTAL = 20;
    public static final int APP_SCAN_BUTTON_BOX_HEIGHT = 96;
    
    public static final String LABEL_SELECT_TRANSMITTER = "Select Transmitter";
    public static final String LABEL_NOT_STREAMING = "Not currently streaming.";
    public static final String LABEL_STREAMING_WITH_BUFFER_SIZE = "Currently streaming. Buffer size =";
    
    public static final int BUFFER_LENGTH_MS_DEFAULT = 30;
    public static final int BUFFER_LENGTH_MS_MIN = 0;
    public static final int BUFFER_LENGTH_MS_MAX = 10000;
    public static final int BUFFER_LENGTH_MS_STEP = 1;
    
    public static final int STREAM_CONNECTION_TIMEOUT_MS = 500;
    
    public static final int SCAN_WORKER_THREAD_COUNT = 16;
    public static final int SCAN_CONNECTION_TIMEOUT_MS = 500;
    
    public static final Color PROGRESS_BAR_COLOR = Color.decode("#2682bd");
    
    public static final StreamingPlaybackUserInterface STREAMING_PLAYBACK_USER_INTERFACE = new DesktopStreamingPlaybackUserInterface();
    public static final ScanningUserInterface SCANNING_USER_INTERFACE = new DesktopScanningUserInterface();
    
    
    //////////////////////
    // Static Variables //
    //////////////////////
    
    
    /////////////////////////
    // Dynamic UI Elements //
    /////////////////////////
    
    public static class DynamicUI
    {
        private DynamicUI() { throw new UnsupportedOperationException(); }
        
        // Main GUI
        public static JFrame mainFrame;
        public static JLabel labelSelectedName;
        public static JLabel labelSelectedHost;
        public static JLabel labelSelectedPort;
        
        public static SpinnerNumberModel spinnerModelBufferLengthMs;
        public static JSpinner           spinnerBufferLengthMs;
        
        public static JProgressPanel progressPanelLeftMonitor;
        public static JProgressPanel progressPanelRightMonitor;
        public static JLabel         labelStreamStatus;
        
        // Select Transmitter
        public static JDialog    dialogSelectTransmitter;
        public static JTextField textFieldServerName;
        public static JTextField textFieldServerHost;
        public static JTextField textFieldServerPort;
        
        public static JProgressPanel progressPanelScanMonitor;
        
        public static Box panelTransmitterButtonBox;
    }
    
    
    /////////////////
    // Main Method //
    /////////////////
    
    public static void main(String[] args)
    {
        // Create main GUI
        DynamicUI.mainFrame = new JFrame();
        {
            JPanel panelReceiver = new JPanel(new GridBagLayout());
            {
                panelReceiver.setBorder(
                    BorderFactory.createEmptyBorder(
                        APP_MARGIN_VERTICAL, 
                        APP_MARGIN_HORIZONTAL, 
                        APP_MARGIN_VERTICAL, 
                        APP_MARGIN_HORIZONTAL
                    )
                );
                
                GridBagConstraints c = new GridBagConstraints();
                c.gridy = 0;
                c.insets = new Insets(1, 1, 1, 1);
                int defaultFill = c.fill = GridBagConstraints.BOTH;
                int defaultGridWidth = c.gridwidth = 1;
                int totalGridWidth = 2;
                
                
                //// Next Row
                c.gridy += 1;
                
                // Select Transmitter button
                JButton buttonSelectTransmitter = new JButton(LABEL_SELECT_TRANSMITTER);
                buttonSelectTransmitter.addActionListener(SELECT_TRANSMITTER_LISTENER);
                c.gridx = 0;
                c.gridwidth = totalGridWidth;
                panelReceiver.add(buttonSelectTransmitter, c);
                c.gridwidth = defaultGridWidth;
                
                
                //// Next Row
                c.gridy += 1;
                
                // Selected Transmitter label
                JLabel labelSelectedTransmitter = new JLabel("Selected Transmitter: ");
                c.gridx = 0;
                panelReceiver.add(labelSelectedTransmitter, c);
                
                // labelSelectedName
                DynamicUI.labelSelectedName = new JLabel("No Transmitter Selected");
                c.gridx = 1;
                panelReceiver.add(DynamicUI.labelSelectedName, c);
                
                
                //// Next Row
                c.gridy += 1;
                
                // selected info panel
                JPanel panelSelectedInfo = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
                {
                    // labelSelectedHost
                    DynamicUI.labelSelectedHost = new JLabel("host");
                    panelSelectedInfo.add(DynamicUI.labelSelectedHost);
                    
                    // labelSelectionSeparator
                    JLabel labelSelectionSeparator = new JLabel(":");
                    panelSelectedInfo.add(labelSelectionSeparator);
                    
                    // labelSelectedPort
                    DynamicUI.labelSelectedPort = new JLabel("port");
                    panelSelectedInfo.add(DynamicUI.labelSelectedPort);
                }
                c.gridx = 1;
                panelReceiver.add(panelSelectedInfo, c);
                
                
                //// Next Row
                c.gridy += 1;
                
                // Buffer Length (ms) label
                JLabel labelBufferLengthMs = new JLabel("Buffer Length (ms): ");
                c.gridx = 0;
                panelReceiver.add(labelBufferLengthMs, c);
                
                // editBufferLengthMs
                DynamicUI.spinnerModelBufferLengthMs = new SpinnerNumberModel(
                    BUFFER_LENGTH_MS_DEFAULT, 
                    BUFFER_LENGTH_MS_MIN,
                    BUFFER_LENGTH_MS_MAX,
                    BUFFER_LENGTH_MS_STEP
                );
                DynamicUI.spinnerBufferLengthMs = new JSpinner(DynamicUI.spinnerModelBufferLengthMs);
                c.gridx = 1;
                panelReceiver.add(DynamicUI.spinnerBufferLengthMs, c);
                
                
                //// Next Row
                c.gridy += 1;
                
                // buttonStartStreaming
                JButton buttonStartStreaming = new JButton("Start Streaming");
                buttonStartStreaming.addActionListener(START_STREAMING_LISTENER);
                c.gridx = 0;
                panelReceiver.add(buttonStartStreaming, c);
                
                // buttonStopStreaming
                JButton buttonStopStreaming = new JButton("Stop Streaming");
                buttonStopStreaming.addActionListener(STOP_STREAMING_LISTENER);
                c.gridx = 1;
                panelReceiver.add(buttonStopStreaming, c);
                
                
                //// Next Row
                c.gridy += 1;
                
                // monitors
                JPanel panelMonitors = new JPanel(new GridBagLayout());
                {
                    GridBagConstraints mc = new GridBagConstraints();
                    mc.fill = GridBagConstraints.BOTH;
                    mc.insets = new Insets(1, 1, 1, 1);
                    
                    // labelLeftMonitor
                    JLabel labelLeftMonitor = new JLabel("L");
                    mc.gridy = 0;
                    mc.gridx = 0;
                    mc.weightx = 0.0;
                    panelMonitors.add(labelLeftMonitor, mc);
                    
                    // progressPanelLeftMonitor
                    DynamicUI.progressPanelLeftMonitor = new JProgressPanel();
                    {
                        DynamicUI.progressPanelLeftMonitor.setProgressMinimum(0);
                        DynamicUI.progressPanelLeftMonitor.setProgressMaximum(128);
                        DynamicUI.progressPanelLeftMonitor.setForeground(PROGRESS_BAR_COLOR);
                    }
                    mc.gridy = 0;
                    mc.gridx = 1;
                    mc.weightx = 1.0;
                    panelMonitors.add(DynamicUI.progressPanelLeftMonitor, mc);
                    
                    // labelRightMonitor
                    JLabel labelRightMonitor = new JLabel("R");
                    mc.gridy = 1;
                    mc.gridx = 0;
                    mc.weightx = 0.0;
                    panelMonitors.add(labelRightMonitor, mc);
                    
                    // progressPanelRightMonitor
                    DynamicUI.progressPanelRightMonitor = new JProgressPanel();
                    {
                        DynamicUI.progressPanelRightMonitor.setProgressMinimum(0);
                        DynamicUI.progressPanelRightMonitor.setProgressMaximum(128);
                        DynamicUI.progressPanelRightMonitor.setForeground(PROGRESS_BAR_COLOR);
                    }
                    mc.gridy = 1;
                    mc.gridx = 1;
                    mc.weightx = 1.0;
                    panelMonitors.add(DynamicUI.progressPanelRightMonitor, mc);
                }
                c.gridx = 0;
                c.gridwidth = totalGridWidth;
                panelReceiver.add(panelMonitors, c);
                c.gridwidth = defaultGridWidth;
                
                //// Next Row
                c.gridy += 1;
                
                // labelStreamStatus
                DynamicUI.labelStreamStatus = new JLabel(LABEL_NOT_STREAMING);
                c.gridx = 0;
                c.gridwidth = totalGridWidth;
                panelReceiver.add(DynamicUI.labelStreamStatus, c);
                c.gridwidth = defaultGridWidth;
                
            }
            DynamicUI.mainFrame.getContentPane().add(panelReceiver);
        }
        
        // Set up main GUI
        DynamicUI.mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        DynamicUI.mainFrame.setTitle(APP_NAME);
        DynamicUI.mainFrame.setResizable(false); // set this before pack() to avoid change in window size
        DynamicUI.mainFrame.pack();
        DynamicUI.mainFrame.setLocationRelativeTo(null); // center on screen
        
        
        // Create transmitter selection GUI
        DynamicUI.dialogSelectTransmitter = new JDialog(DynamicUI.mainFrame, true);
        {
            JPanel panelTransmitterSelection = new JPanel(new GridBagLayout());
            {
                panelTransmitterSelection.setBorder(
                    BorderFactory.createEmptyBorder(
                        APP_MARGIN_VERTICAL, 
                        APP_MARGIN_HORIZONTAL, 
                        APP_MARGIN_VERTICAL, 
                        APP_MARGIN_HORIZONTAL
                    )
                );
                
                GridBagConstraints c = new GridBagConstraints();
                c.gridy = 0;
                c.insets = new Insets(1, 1, 1, 1);
                int defaultFill = c.fill = GridBagConstraints.BOTH;
                int defaultGridWidth = c.gridwidth = 1;
                int totalGridWidth = 2;
                double defaultWeightX = c.weightx;
                double defaultWeightY = c.weighty;
                
                
                //// Next Row
                c.gridy += 1;
                
                // buttonSelectServer
                JButton buttonSelectServer = new JButton("Select");
                buttonSelectServer.addActionListener(SELECT_SERVER_LISTENER);
                c.gridx = 0;
                c.weightx = 1.0;
                panelTransmitterSelection.add(buttonSelectServer, c);
                
                // buttonCancelSelection
                JButton buttonCancelSelection = new JButton("Cancel");
                buttonCancelSelection.addActionListener(CANCEL_SELECTION_LISTENER);
                c.gridx = 1;
                c.weightx = 1.0;
                panelTransmitterSelection.add(buttonCancelSelection, c);
                c.weightx = defaultWeightX;
                
                
                //// Next Row
                c.gridy += 1;
                
                // labelServerName
                JLabel labelServerName = new JLabel("Server Name");
                c.gridx = 0;
                c.gridwidth = totalGridWidth;
                panelTransmitterSelection.add(labelServerName, c);
                c.gridwidth = defaultGridWidth;
                
                
                //// Next Row
                c.gridy += 1;
                
                // DynamicUI.textFieldServerName
                DynamicUI.textFieldServerName = new JTextField(" ");
                {
                    DynamicUI.textFieldServerName.setEditable(false);
                }
                c.gridx = 0;
                c.gridwidth = totalGridWidth;
                panelTransmitterSelection.add(DynamicUI.textFieldServerName, c);
                c.gridwidth = defaultGridWidth;
                
                
                //// Next Row
                c.gridy += 1;
                
                // server
                JPanel panelServer = new JPanel(new GridBagLayout());
                {
                    GridBagConstraints sc = new GridBagConstraints();
                    sc.fill = GridBagConstraints.BOTH;
                    sc.insets = new Insets(1, 1, 1, 1);
                    
                    // labelServerHost
                    JLabel labelServerHost = new JLabel("Server Host");
                    sc.gridy = 0;
                    sc.gridx = 0;
                    sc.weightx = 3.0;
                    panelServer.add(labelServerHost, sc);
                    
                    // labelServerPort
                    JLabel labelServerPort = new JLabel("Port");
                    sc.gridy = 0;
                    sc.gridx = 1;
                    sc.weightx = 1.0;
                    panelServer.add(labelServerPort, sc);
                    
                    // textFieldServerHost
                    DynamicUI.textFieldServerHost = new JTextField(18);
                    DynamicUI.textFieldServerHost.addKeyListener(MANUAL_ENTRY_KEY_LISTENER);
                    sc.gridy = 1;
                    sc.gridx = 0;
                    sc.weightx = 3.0;
                    panelServer.add(DynamicUI.textFieldServerHost, sc);
                    
                    // textFieldServerPort
                    DynamicUI.textFieldServerPort = new JTextField(6);
                    DynamicUI.textFieldServerPort.addKeyListener(MANUAL_ENTRY_KEY_LISTENER);
                    DynamicUI.textFieldServerPort.setText("" + Protocol.CONNECTION_SERVER_PORT);
                    sc.gridy = 1;
                    sc.gridx = 1;
                    sc.weightx = 1.0;
                    panelServer.add(DynamicUI.textFieldServerPort, sc);
                }
                c.gridx = 0;
                c.gridwidth = totalGridWidth;
                panelTransmitterSelection.add(panelServer, c);
                c.gridwidth = defaultGridWidth;
                
                
                //// Next Row
                c.gridy += 1;
                
                // buttonStartScanning
                JButton buttonStartScanning = new JButton("Start Scan");
                buttonStartScanning.addActionListener(START_SCANNING_LISTENER);
                c.gridx = 0;
                c.weightx = 1.0;
                panelTransmitterSelection.add(buttonStartScanning, c);
                
                // buttonStopScanning
                JButton buttonStopScanning = new JButton("Stop Scan");
                buttonStopScanning.addActionListener(STOP_SCANNING_LISTENER);
                c.gridx = 1;
                c.weightx = 1.0;
                panelTransmitterSelection.add(buttonStopScanning, c);
                c.weightx = defaultWeightX;
                
                
                //// Next Row
                c.gridy += 1;
                
                // panelMonitorWrapper
                JPanel panelMonitorWrapper = new JPanel(new GridBagLayout());
                {
                    GridBagConstraints mc = new GridBagConstraints();
                    mc.fill = GridBagConstraints.BOTH;
                    mc.insets = new Insets(1, 1, 1, 1);
                    
                    // labelScanMontior
                    JLabel labelScanMontior = new JLabel(" ");
                    mc.gridy = 0;
                    mc.gridx = 0;
                    mc.weightx = 0.0;
                    panelMonitorWrapper.add(labelScanMontior, mc);
                    
                    // progressPanelScanMonitor
                    DynamicUI.progressPanelScanMonitor = new JProgressPanel();
                    {
                        DynamicUI.progressPanelScanMonitor.setProgressMinimum(0);
                        DynamicUI.progressPanelScanMonitor.setProgressMaximum(256);
                    }
                    mc.gridy = 0;
                    mc.gridx = 1;
                    mc.weightx = 1.0;
                    panelMonitorWrapper.add(DynamicUI.progressPanelScanMonitor, mc);
                }
                c.gridx = 0;
                c.gridwidth = totalGridWidth;
                panelTransmitterSelection.add(panelMonitorWrapper, c);
                c.gridwidth = defaultGridWidth;
                
                
                //// Next Row
                c.gridy += 1;
                
                // panelTransmitterButtonBoxWrapper
                JScrollPane panelTransmitterButtonBoxWrapper = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                {
                    // panelTransmitterButtonBox
                    DynamicUI.panelTransmitterButtonBox = Box.createVerticalBox();
                    panelTransmitterButtonBoxWrapper.setViewportView(DynamicUI.panelTransmitterButtonBox);
                    
                    // scroll pane size
                    panelTransmitterButtonBoxWrapper.setPreferredSize(new Dimension(1, APP_SCAN_BUTTON_BOX_HEIGHT));
                }
                c.gridx = 0;
                c.gridwidth = totalGridWidth;
                panelTransmitterSelection.add(panelTransmitterButtonBoxWrapper, c);
                c.gridwidth = defaultGridWidth;
                
            }
            DynamicUI.dialogSelectTransmitter.getContentPane().add(panelTransmitterSelection);
        }
        
        // Set up transmitter selection GUI
        DynamicUI.dialogSelectTransmitter.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        DynamicUI.dialogSelectTransmitter.addComponentListener(TRANSMITTER_SELECTION_COMPONENT_LISTENER);
        DynamicUI.dialogSelectTransmitter.setTitle(LABEL_SELECT_TRANSMITTER);
        DynamicUI.dialogSelectTransmitter.setResizable(false);
        DynamicUI.dialogSelectTransmitter.pack();
        DynamicUI.dialogSelectTransmitter.setLocationRelativeTo(null);
        
        
        // Set Icon
        try {
            InputStream iconStream = SoftZoneReceiverProgram.class.getResourceAsStream(ICON_LOCATION);
            BufferedImage iconImage = ImageIO.read(iconStream);
            DynamicUI.mainFrame.setIconImage(iconImage);
            DynamicUI.dialogSelectTransmitter.setIconImage(iconImage);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
        // Show main GUI
        DynamicUI.mainFrame.setVisible(true);
    }
    
    
    ///////////////
    // Listeners //
    ///////////////
    
    // Main GUI
    private static final ActionListener SELECT_TRANSMITTER_LISTENER = e ->
    {
        // show Select Transmitter dialog
        DynamicUI.dialogSelectTransmitter.setVisible(true);
    };
    
    private static final ActionListener START_STREAMING_LISTENER = e ->
    {
        String errorMessage = null;
        try
        {
            String selectedName = DynamicUI.labelSelectedName.getText();
            String selectedHost = DynamicUI.labelSelectedHost.getText();
            String selectedPort = DynamicUI.labelSelectedPort.getText();
            Number selectedBufferLength = DynamicUI.spinnerModelBufferLengthMs.getNumber();
            
            errorMessage = "Invalid port.";
            int selectedPortNumber = Integer.parseInt(selectedPort);
            
            errorMessage = "Invalid buffer length ms.";
            short selectedBufferLengthMs = selectedBufferLength.shortValue();
            
            errorMessage = "Error starting stream.";
            StreamingPlaybackMasterThread audioReceivingThread = new StreamingPlaybackMasterThread(
                STREAMING_PLAYBACK_USER_INTERFACE, 
                selectedName, 
                selectedHost, 
                selectedPortNumber, 
                selectedBufferLengthMs,
                STREAM_CONNECTION_TIMEOUT_MS
            );
            audioReceivingThread.start();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            if (errorMessage == null)
            {
                throw new RuntimeException(ex);
            }
            STREAMING_PLAYBACK_USER_INTERFACE.showShortPopupMessage(errorMessage);
        }
    };
    
    private static final ActionListener STOP_STREAMING_LISTENER = e ->
    {
        StreamingPlaybackMasterThread.stopPlayback();
    };
    
    // Transmitter Selection GUI
    private static final ComponentListener TRANSMITTER_SELECTION_COMPONENT_LISTENER = new ComponentAdapter()
    {
        @Override public void componentShown(ComponentEvent e)
        {
            // reset server host field
            DynamicUI.textFieldServerName.setText("");
            DynamicUI.textFieldServerHost.requestFocusInWindow();
            String localAddressPrefix = "";
            try
            {
                localAddressPrefix = SCANNING_USER_INTERFACE.getLocalAddressPrefix();
            }
            catch (Exception ex)
            {
                // do nothing (use empty string)
            }
            DynamicUI.textFieldServerHost.setText(localAddressPrefix);
            DynamicUI.textFieldServerHost.setCaretPosition(localAddressPrefix.length());
        }
        
        @Override public void componentHidden(ComponentEvent e)
        {
            ScanningMasterThread.stopScanning();
        }
    };
    
    private static final ActionListener SELECT_SERVER_LISTENER = e ->
    {
        Component errorComponent = null;
        String errorMessage = null;
        try
        {
            String name = DynamicUI.textFieldServerName.getText().trim();
            String host = DynamicUI.textFieldServerHost.getText().trim();
            String portText = DynamicUI.textFieldServerPort.getText().trim();
            
            errorComponent = DynamicUI.textFieldServerHost;
            errorMessage = "Invalid server IP address.";
            if (host == null || host.length() == 0)
            {
                throw new IllegalStateException(errorMessage);
            }
            InetAddress hostNetAddress = InetAddress.getByName(host);
            String hostAddressText = hostNetAddress.getHostAddress();
            
            errorComponent = DynamicUI.textFieldServerPort;
            errorMessage = "Invalid server port.";
            int port = Integer.parseInt(portText);
            
            errorComponent = null;
            errorMessage = null;
            DynamicUI.labelSelectedName.setText(name);
            DynamicUI.labelSelectedHost.setText(hostAddressText);
            DynamicUI.labelSelectedPort.setText(portText);
            DynamicUI.dialogSelectTransmitter.setVisible(false);
        }
        catch (Exception ex)
        {
            if (errorComponent != null)
            {
                errorComponent.requestFocusInWindow();
            }
            
            if (errorMessage == null)
            {
                throw new RuntimeException(ex);
            }
            else
            {
                System.err.println(errorMessage);
                STREAMING_PLAYBACK_USER_INTERFACE.showShortPopupMessage(errorMessage);
            }
        }
    };
    
    private static final ActionListener CANCEL_SELECTION_LISTENER = e ->
    {
        DynamicUI.dialogSelectTransmitter.setVisible(false);
    };
    
    private static final ActionListener START_SCANNING_LISTENER = e ->
    {
        try
        {
            String portText = DynamicUI.textFieldServerPort.getText();
            int port = Integer.parseInt(portText);
            ScanningMasterThread scanningThread = new ScanningMasterThread(
                SCANNING_USER_INTERFACE, 
                port, 
                SCAN_WORKER_THREAD_COUNT, 
                SCAN_CONNECTION_TIMEOUT_MS
            );
            scanningThread.start();
        }
        catch (Exception ex)
        {
            final String errorMessage = "Invalid Port";
            DynamicUI.textFieldServerPort.requestFocusInWindow();
            STREAMING_PLAYBACK_USER_INTERFACE.showShortPopupMessage(errorMessage);
        }
    };
    
    private static final ActionListener STOP_SCANNING_LISTENER = e -> 
    {
        ScanningMasterThread.stopScanning();
    };
    
    private static final KeyListener MANUAL_ENTRY_KEY_LISTENER = new KeyAdapter()
    {
        @Override public void keyPressed(KeyEvent e)
        {
            if (e.getKeyCode() == KeyEvent.VK_ENTER)
            {
                SELECT_SERVER_LISTENER.actionPerformed(null);
                return;
            }
            DynamicUI.textFieldServerName.setText("Manual Entry");
        }
    };
    
    // private static final ActionListener ASDF = ;
    
    
    //////////////////////////////
    // Receiver Implementations //
    //////////////////////////////
    
    public static class DesktopStreamingPlaybackDevice extends StreamingPlaybackDevice
    {
        // Instance Fields
        protected AudioFormat mixFormat = null;
        protected SourceDataLine mixLine = null;
        protected boolean mixLineIsActive = false;
        
        // Constructors
        public DesktopStreamingPlaybackDevice(int bufferLengthMs)
        {
            super(Protocol.AUDIO_BUFFER_SIZE_FOR_LENGTH_MS(bufferLengthMs));
            
            // create mix format
            this.mixFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, 
                Protocol.AUDIO_SAMPLES_PER_SECOND, 
                Protocol.AUDIO_SAMPLE_SIZE_IN_BYTES * 8, 
                Protocol.AUDIO_CHANNELS, 
                Protocol.AUDIO_FRAME_SIZE_IN_BYTES, 
                Protocol.AUDIO_SAMPLES_PER_SECOND,
                Protocol.AUDIO_IS_BIG_ENDIAN
            );
            
            SourceDataLine _mixLine = null;
            try
            {
                _mixLine = AudioSystem.getSourceDataLine(this.mixFormat);
            }
            catch (LineUnavailableException ex)
            {
                STREAMING_PLAYBACK_USER_INTERFACE.showShortPopupMessage("Error creating audio device.");
            }
            this.mixLine = _mixLine;
        }
        
        // Instance Methods
        @Override
        public void close() throws IOException
        {
            this.mixLineIsActive = false;
            if (this.mixLine != null)
            {
                this.mixLine.stop();
                this.mixLine.close();
            }
        }
        
        @Override
        public void initialize()
        {
            if (this.mixLine == null) { return; }
            try
            {
                this.mixLine.open(this.mixFormat, this.requestedBufferSizeInBytes);
                this.mixLine.start();
            }
            catch (LineUnavailableException ex) 
            {
                STREAMING_PLAYBACK_USER_INTERFACE.showShortPopupMessage("Error initializing audio device.");
            }
            this.mixLineIsActive = true;
        }
        
        @Override
        public int write(byte[] buffer, int offset, int length)
        {
            int result = -1;
            if (this.mixLineIsActive) 
            {
                result = this.mixLine.write(buffer, offset, length); 
            }
            return (result > -1) ? result : -1;
        }
    }
    
    public static class DesktopStreamingPlaybackUserInterface implements StreamingPlaybackUserInterface
    {
        @Override
        public StreamingPlaybackDevice getPlaybackDevice(int bufferLengthMs)
        {
            return new DesktopStreamingPlaybackDevice(bufferLengthMs);
        }
        
        @Override
        public void updateMonitors(byte leftSampleHighByte, byte rightSampleHighByte)
        {
            final int leftLevelLinear = MonitorUpdatingThread.getLinearLevel(leftSampleHighByte);
            final int rightLevelLinear = MonitorUpdatingThread.getLinearLevel(rightSampleHighByte);
            //final int leftLevelDecibel = MonitorUpdatingThread.getDecibelLevel(leftLevelLinear);
            //final int rightLevelDecibel = MonitorUpdatingThread.getDecibelLevel(rightLevelLinear);
            DynamicUI.progressPanelLeftMonitor.setProgressCurrentValue(leftLevelLinear);
            DynamicUI.progressPanelRightMonitor.setProgressCurrentValue(rightLevelLinear);
        }
        
        @Override
        public void handleExceptionDuringMonitorUpdating(Exception ex)
        {
            ex.printStackTrace();
        }
        
        @Override
        public String getStatusText(int bufferLengthMs)
        {
            String label_streaming_with_buffer_size = "Currently streaming. Buffer size =";
            return String.format("%s %sms", label_streaming_with_buffer_size, bufferLengthMs);
        }
        
        @Override
        public void setStatusText(final String statusText)
        {
            SwingUtilities.invokeLater(() -> DynamicUI.labelStreamStatus.setText(statusText));
        }
        
        @Override
        public void handleExceptionDuringSocketConnection(Exception ex)
        {
            ex.printStackTrace();
            
            final String message = String.format("Connection timeout (%sms)", STREAM_CONNECTION_TIMEOUT_MS);
            this.showShortPopupMessage(message);
        }
        
        @Override
        public void showShortPopupMessage(final String message)
        {
            // TODO: a better solution (less intrusive than a modal popup)
            SwingUtilities.invokeLater(() ->
            {
                JOptionPane.showMessageDialog(DynamicUI.mainFrame, message);
            });
        }
        
        @Override
        public void logStreamingStatistics(double bytesPerSecond, double averageReadTime, double averageWriteTime)
        {
            System.err.println("bytes per second = " + bytesPerSecond);
            System.err.println("average read time  (ms) = " + averageReadTime);
            System.err.println("average write time (ms) = " + averageWriteTime);
        }
        
        @Override
        public void handleExceptionDuringStreaming(Exception ex)
        {
            ex.printStackTrace();
        }
        
        @Override
        public void clearMonitorLevelsAndStatus()
        {
            DynamicUI.progressPanelLeftMonitor.setProgressCurrentValue(0);
            DynamicUI.progressPanelRightMonitor.setProgressCurrentValue(0);
        }

    }
    
    public static class DesktopScanningUserInterface implements ScanningUserInterface
    {
        @Override
        public void handleExceptionDuringScanWorkerExecution(Exception ex)
        {
            ex.printStackTrace();
        }
        
        @Override
        public void incrementScanProgressBy(final int amount)
        {
            DynamicUI.progressPanelScanMonitor.incrementProgressCurrentValueBy(amount);
        }
        
        @Override
        public String getLocalAddressPrefix() throws Exception
        {
            byte[] localHostIpBytes = InetAddress.getLocalHost().getAddress();
            return String.format("%s.%s.%s.", localHostIpBytes[0] & 0xff, (int)localHostIpBytes[1] & 0xff, (int)localHostIpBytes[2] & 0xff);
        }
        
        @Override
        public void handleExceptionDuringScanStart(Exception ex)
        {
            ex.printStackTrace();
            final String errorMessage = "Error starting scan";
            STREAMING_PLAYBACK_USER_INTERFACE.showShortPopupMessage(errorMessage);
        }
        
        @Override
        public void resetScanReadyZonesAndProgress()
        {
            DynamicUI.progressPanelScanMonitor.setProgressToMinimum();
            SwingUtilities.invokeLater(() ->
            {
                DynamicUI.panelTransmitterButtonBox.removeAll();
                DynamicUI.panelTransmitterButtonBox.revalidate();
            });
        }
        
        @Override
        public void addReadyScanResult(final ScanResult scanResult)
        {
            SwingUtilities.invokeLater(() ->
            {
                JButton buttonResult = new JButton();
                {
                    buttonResult.setText(String.format("%s (%s)", scanResult.name, scanResult.host));
                    buttonResult.addActionListener(e -> 
                    {
                        DynamicUI.textFieldServerHost.setText(scanResult.host);
                        DynamicUI.textFieldServerHost.requestFocusInWindow();
                        DynamicUI.textFieldServerName.setText(scanResult.name);
                    });
                    buttonResult.setAlignmentX(JComponent.CENTER_ALIGNMENT);
                }
                DynamicUI.panelTransmitterButtonBox.add(buttonResult);
                DynamicUI.panelTransmitterButtonBox.revalidate();
            });
        }
        
        @Override
        public void handleInterruptedExceptionDuringButtonCreation(InterruptedException ex)
        {
            ex.printStackTrace();
        }
        
        @Override
        public void handleExceptionDuringWorkerThreadJoin(Exception ex)
        {
            ex.printStackTrace();
            final String errorMessage = "Error while scanning";
            STREAMING_PLAYBACK_USER_INTERFACE.showShortPopupMessage(errorMessage);
        }
        
        @Override
        public void showScanFinishedSuccessfully()
        {
            final String message = "Finished Scan";
            STREAMING_PLAYBACK_USER_INTERFACE.showShortPopupMessage(message);
            DynamicUI.progressPanelScanMonitor.setProgressToMaximum();
        }
    }
    
    
    ////////////////////
    // JProgressPanel //
    ////////////////////
    
    public static class JProgressPanel extends JPanel
    {
        // Constants
        public static final int PROGRESS_MINIMUM_DEFAULT = 0;
        public static final int PROGRESS_MAXIMUM_DEFAULT = 100;
        public static final int PROGRESS_CURRENT_DEFAULT = 0;
        public static final Color FOREGROUND_DEFAULT = Color.BLUE;
        public static final Color BACKGROUND_DEFAULT = Color.BLACK;
        
        // Instance Properties
        protected volatile int progressMinimum = PROGRESS_MINIMUM_DEFAULT;
        public synchronized int getProgressMinimum() { return this.progressMinimum; }
        public synchronized void setProgressMinimum(int value) { this.progressMinimum = value; }
        
        protected volatile int progressMaximum = PROGRESS_MAXIMUM_DEFAULT;
        public synchronized int getProgressMaximum() { return this.progressMaximum; }
        public synchronized void setProgressMaximum(int value) { this.progressMaximum = value; }
        
        protected volatile int progressCurrentValue = PROGRESS_CURRENT_DEFAULT;
        public synchronized int getProgressCurrentValue() { return this.progressCurrentValue; }
        public synchronized void setProgressCurrentValue(int value) 
        {
            if (value < this.progressMinimum) { value = this.progressMinimum; }
            else if (value > this.progressMaximum) { value = this.progressMaximum; }
            this.progressCurrentValue = value;
            this.repaint();
        }
        public synchronized void incrementProgressCurrentValueBy(int value)
        {
            this.setProgressCurrentValue(this.progressCurrentValue + value);
        }
        public synchronized void setProgressToMinimum()
        {
            this.setProgressCurrentValue(this.progressMinimum);
        }
        public synchronized void setProgressToMaximum()
        {
            this.setProgressCurrentValue(this.progressMaximum);
        }
        
        // Constructor
        public JProgressPanel()
        {
            this.setOpaque(true);
            this.setBackground(BACKGROUND_DEFAULT);
            this.setForeground(FOREGROUND_DEFAULT);
        }
        
        // Instance Methods
        @Override
        public Dimension getPreferredSize()
        {
            return new Dimension(1, 1);
        }
        
        @Override
        public void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            
            int x = 0;
            int y = 0;
            int totalWidth = this.getWidth();
            int adjustedProgressCurrentValue = this.progressCurrentValue - this.progressMinimum;
            int adjustedProgressMaximum = this.progressMaximum - this.progressMinimum;
            if (adjustedProgressMaximum == 0) { adjustedProgressMaximum = 1; }
            int progressWidth = totalWidth * adjustedProgressCurrentValue / adjustedProgressMaximum;
            int totalHeight = this.getHeight();
            
            Graphics2D g2d = (Graphics2D)g.create();
            try
            {
                g2d.setColor(this.getForeground());
                g2d.fillRect(x, y, progressWidth, totalHeight);
            }
            finally
            {
                g2d.dispose();
            }
            //DynamicUI.labelStreamStatus.setText(String.format("g2d.drawRect(%s, %s, %s, %s)", x, y, progressWidth, totalHeight));
        }
    }
}
