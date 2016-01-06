/* 
 * Nicholas Saney
 * 
 * Created: January 01, 2016
 * 
 * SoftZoneReceiverProgram.java 
 * SoftZoneReceiverProgram main and auxiliary methods
 */

package chairosoft.softzone;

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

import java.awt.image.*;
import javax.imageio.*;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
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
    
    public static final String LABEL_SELECT_TRANSMITTER = "Select Transmitter";
    public static final String LABEL_NOT_STREAMING = "Not currently streaming.";
    public static final String LABEL_STREAMING_WITH_BUFFER_SIZE = "Currently streaming. Buffer size =";
    
    public static final int BUFFER_LENGTH_MS_DEFAULT = 30;
    public static final int BUFFER_LENGTH_MS_MIN = 0;
    public static final int BUFFER_LENGTH_MS_MAX = 10000;
    public static final int BUFFER_LENGTH_MS_STEP = 1;
    
    
    //////////////////////
    // Static Variables //
    //////////////////////
    
    public static volatile String selectedHost = "";
    public static volatile int selectedPort = -1;
    
    
    /////////////////////////
    // Dynamic UI Elements //
    /////////////////////////
    
    public static class DynamicUI
    {
        private DynamicUI() { throw new UnsupportedOperationException(); }
        
        public static JLabel labelSelectedName;
        public static JLabel labelSelectedHost;
        public static JLabel labelSelectedPort;
        public static JPanel panelLeftMonitor;
        public static JPanel panelRightMonitor;
        public static JLabel labelStreamStatus;
        
        public static JDialog    dialogSelectTransmitter;
        public static JTextField textFieldServerName;
        public static JPanel     panelScanProgress;
    }
    
    
    /////////////////
    // Main Method //
    /////////////////
    
    public static void main(String[] args)
    {
        // Create main GUI
        JFrame frame = new JFrame();
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
                buttonSelectTransmitter.addActionListener(e ->
                {
                    // show Selecty Transmitter dialog
                    DynamicUI.dialogSelectTransmitter.setVisible(true);
                });
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
                JSpinner spinnerBufferLengthMs = new JSpinner(
                    new SpinnerNumberModel(
                        BUFFER_LENGTH_MS_DEFAULT, 
                        BUFFER_LENGTH_MS_MIN,
                        BUFFER_LENGTH_MS_MAX,
                        BUFFER_LENGTH_MS_STEP
                    )
                );
                c.gridx = 1;
                panelReceiver.add(spinnerBufferLengthMs, c);
                
                
                //// Next Row
                c.gridy += 1;
                
                // buttonStartStreaming
                JButton buttonStartStreaming = new JButton("Start Streaming");
                buttonStartStreaming.addActionListener(e ->
                {
                    System.out.println("start streaming??");
                });
                c.gridx = 0;
                panelReceiver.add(buttonStartStreaming, c);
                
                // buttonStopStreaming
                JButton buttonStopStreaming = new JButton("Stop Streaming");
                buttonStopStreaming.addActionListener(e ->
                {
                    System.out.println("stop streaming??");
                });
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
                    
                    // panelLeftMonitor
                    DynamicUI.panelLeftMonitor = new JPanel();
                    DynamicUI.panelLeftMonitor.setOpaque(true);
                    DynamicUI.panelLeftMonitor.setBackground(Color.BLACK);
                    mc.gridy = 0;
                    mc.gridx = 1;
                    mc.weightx = 1.0;
                    panelMonitors.add(DynamicUI.panelLeftMonitor, mc);
                    
                    // labelRightMonitor
                    JLabel labelRightMonitor = new JLabel("R");
                    mc.gridy = 1;
                    mc.gridx = 0;
                    mc.weightx = 0.0;
                    panelMonitors.add(labelRightMonitor, mc);
                    
                    // panelRightMonitor
                    DynamicUI.panelRightMonitor = new JPanel();
                    DynamicUI.panelRightMonitor.setOpaque(true);
                    DynamicUI.panelRightMonitor.setBackground(Color.BLACK);
                    mc.gridy = 1;
                    mc.gridx = 1;
                    mc.weightx = 1.0;
                    panelMonitors.add(DynamicUI.panelRightMonitor, mc);
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
            frame.getContentPane().add(panelReceiver);
        }
        
        // Set up main GUI
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setTitle(APP_NAME);
        frame.setResizable(false); // set this before pack() to avoid change in window size
        frame.setLocationRelativeTo(null); // center on screen
        frame.pack();
        
        
        // Create transmitter selection GUI
        DynamicUI.dialogSelectTransmitter = new JDialog(frame, true);
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
                
                
                //// Next Row
                c.gridy += 1;
                
                // buttonSelectServer
                JButton buttonSelectServer = new JButton("Select");
                buttonSelectServer.addActionListener(e ->
                {
                    System.out.println("select server??");
                });
                c.gridx = 0;
                panelTransmitterSelection.add(buttonSelectServer, c);
                
                // buttonCancelSelection
                JButton buttonCancelSelection = new JButton("Cancel");
                buttonCancelSelection.addActionListener(e ->
                {
                    System.out.println("cancel selection??");
                });
                c.gridx = 1;
                panelTransmitterSelection.add(buttonCancelSelection, c);
                
                
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
                DynamicUI.textFieldServerName = new JTextField("(Select a Server)");
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
                    JTextField textFieldServerHost = new JTextField(18);
                    sc.gridy = 1;
                    sc.gridx = 0;
                    sc.weightx = 3.0;
                    panelServer.add(textFieldServerHost, sc);
                    
                    // textFieldServerPort
                    JTextField textFieldServerPort = new JTextField(6);
                    sc.gridy = 1;
                    sc.gridx = 1;
                    sc.weightx = 1.0;
                    panelServer.add(textFieldServerPort, sc);
                }
                c.gridx = 0;
                c.gridwidth = totalGridWidth;
                panelTransmitterSelection.add(panelServer, c);
                c.gridwidth = defaultGridWidth;
                
                
                //// Next Row
                c.gridy += 1;
                
                // buttonStartScanning
                // buttonStopScanning
                
                
                //// Next Row
                c.gridy += 1;
                
                // panelScanProgress
                
                
                //// Next Row
                c.gridy += 1;
                
                // List of transmitters
            }
            DynamicUI.dialogSelectTransmitter.getContentPane().add(panelTransmitterSelection);
        }
        
        // Set up transmitter selection GUI
        DynamicUI.dialogSelectTransmitter.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        DynamicUI.dialogSelectTransmitter.setTitle(LABEL_SELECT_TRANSMITTER);
        DynamicUI.dialogSelectTransmitter.setResizable(false);
        DynamicUI.dialogSelectTransmitter.setLocationRelativeTo(null);
        DynamicUI.dialogSelectTransmitter.pack();
        
        
        // Set Icon
        try {
            InputStream iconStream = SoftZoneReceiverProgram.class.getResourceAsStream(ICON_LOCATION);
            BufferedImage iconImage = ImageIO.read(iconStream);
            frame.setIconImage(iconImage);
            DynamicUI.dialogSelectTransmitter.setIconImage(iconImage);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
        // Show main GUI
        frame.setVisible(true);
    }
    
}
