/* 
 * Nicholas Saney
 * 
 * Created: October 29, 2015
 * 
 * SoftZoneTransmitter.java 
 * SoftZoneTransmitter main and auxiliary methods
 */

package chairosoft.softzone;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import java.util.function.Function;
import java.util.function.Predicate;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class SoftZoneTransmitter
{
    /////////////////
    // Main Method //
    /////////////////
    
    public static void main(String[] args)
    {
        System.out.println("Start Time:");
        System.out.println(new java.util.Date());
        System.out.println("");
        
        try
        {
            SoftZoneTransmitter.doMain(args);
        }
        catch (Throwable ex)
        {
            ex.printStackTrace();
        }
    }
    
    
    /////////////////////////////
    // Non-Main Static Methods //
    /////////////////////////////
    
    public static int getUserInputInt(Scanner scanner, 
                                      String prompt, 
                                      int minInclusive, 
                                      int maxExclusive, 
                                      Predicate<Integer> validator, 
                                      Function<Integer,String> invalidMessageGetter
    )
    {
        boolean isValid = false;
        Integer result = null;
        while (!isValid)
        {
            System.out.print(prompt);
            String line = scanner.nextLine();
            try
            {
                result = Integer.parseInt(line);
                if (minInclusive <= result && result < maxExclusive)
                {
                    isValid = true;
                    if (validator != null && !validator.test(result))
                    {
                        isValid = false;
                        String message = invalidMessageGetter == null
                            ? "Invalid value."
                            : String.format("Invalid value: %s", invalidMessageGetter.apply(result));
                        System.out.println(message);
                    }
                }
                else
                {
                    System.out.printf("Value must be between %d (inclusive) and %d (exclusive). \n", minInclusive, maxExclusive);
                }
            }
            catch (Exception ex)
            {
                System.err.println(ex);
            }
        }
        return result;
    }
    
    public static void logActivity(Socket clientSocket, String description)
    {
        java.util.Date timestamp = new java.util.Date();
        String remoteHost = clientSocket.getInetAddress().getHostAddress();
        System.out.printf("[%s][from %15s] %s \n", timestamp, remoteHost, description);
    }
    
    public static void doMain(String[] args) throws Throwable
    {
        int choice = -1;
        Scanner scanner = new Scanner(System.in);
        
        // get mixer info
        final Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        if (mixerInfos.length == 0)
        {
            throw new IllegalStateException("No available mixers.");
        }
        System.out.println("Please select the recording mixer you would like to use: ");
        for (int i = 0; i < mixerInfos.length; ++i)
        {
            System.out.printf("[%2d] %s %s \n", i, mixerInfos[i].getDescription(), mixerInfos[i].getName());
        }
        Predicate<Integer> validator = new Predicate<Integer>() { @Override public boolean test(Integer i) 
        {
            return AudioSystem.getMixer(mixerInfos[i]).getTargetLineInfo().length > 0; 
        }};
        Function<Integer,String> invalidMessageGetter = new Function<Integer,String>() { @Override public String apply(Integer i) 
        {
            return "Chosen mixer (" + i + ") has no recording capability."; 
        }};
        choice = SoftZoneTransmitter.getUserInputInt(scanner, "> ", 0, mixerInfos.length, validator, invalidMessageGetter);
        Mixer.Info chosenMixerInfo = mixerInfos[choice];
        System.out.printf("You chose: %s %s \n", chosenMixerInfo.getDescription(), chosenMixerInfo.getName());
        
        // get mixer
        Mixer chosenMixer = AudioSystem.getMixer(chosenMixerInfo);
        chosenMixer.open();
        System.out.println("Mixer opened.");
        System.out.println("");
        
        // get target line info
        Line.Info[] targetLineInfos = chosenMixer.getTargetLineInfo();
        System.out.println("Please select the target line you would like to record from: ");
        for (int i = 0; i < targetLineInfos.length; ++i)
        {
            System.out.printf("[%2d] %s \n", i, targetLineInfos[i]);
        }
        choice = SoftZoneTransmitter.getUserInputInt(scanner, "> ", 0, targetLineInfos.length, null, null);
        Line.Info chosenTargetLineInfo = targetLineInfos[choice];
        System.out.println("You chose: " + chosenTargetLineInfo);
        System.out.println("");
        
        // get source mixer info
        System.out.println("Please select the source mixer you would like to play back locally to: ");
        System.out.println("[-1] No source mixer (null)");
        for (int i = 0; i < mixerInfos.length; ++i)
        {
            System.out.printf("[%2d] %s %s \n", i, mixerInfos[i].getDescription(), mixerInfos[i].getName());
        }
        Predicate<Integer> sourceValidator = new Predicate<Integer>() { @Override public boolean test(Integer i) 
        {
            return i == -1 || AudioSystem.getMixer(mixerInfos[i]).getSourceLineInfo().length > 0; 
        }};
        Function<Integer,String> sourceInvalidMessageGetter = new Function<Integer,String>() { @Override public String apply(Integer i) 
        {
            return "Chosen mixer (" + i + ") has no playback capability."; 
        }};
        choice = SoftZoneTransmitter.getUserInputInt(scanner, "> ", -1, mixerInfos.length, sourceValidator, sourceInvalidMessageGetter);
        Mixer.Info chosenSourceMixerInfo = (choice == -1) ? null : mixerInfos[choice];
        System.out.println("You chose: " + chosenSourceMixerInfo);
        System.out.println("");
        
        
        // open Server Socket
        try (ServerSocket serverSocket = new ServerSocket(Protocol.CONNECTION_SERVER_PORT))
        {
            System.out.println("Server socket open on port " + serverSocket.getLocalPort());
            System.out.println("Server addresses:");
            
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
            {
                String name = ni.getName();
                for (InetAddress ia : Collections.list(ni.getInetAddresses())) //(a.hasMoreElements())
                {
                    if (ia.isLoopbackAddress() || ia.isLinkLocalAddress()) { continue; }
                    String addr = ia.getHostAddress();
                    System.out.printf("%-10s - %-40s\n", name, addr);
                }
            }
            
            // accept Client Sockets
            boolean ever = true;
            for (;ever;)
            {
                Socket clientSocket = serverSocket.accept();
                RequestHandler handler = new RequestHandler(clientSocket, chosenMixer, chosenTargetLineInfo, chosenSourceMixerInfo);
                handler.start();
            }
        }
    }
    
    
    //////////////////////////
    // Static Inner Classes //
    //////////////////////////
    
    /**
     * This class details the constants used in 
     * the SoftZone communication protocol.
     */
    public static class Protocol
    {
        // Constructor
        private Protocol() { throw new UnsupportedOperationException(); }
        
        // Audio format
        public static final int AUDIO_SAMPLES_PER_SECOND = 48000;
        public static final int AUDIO_SAMPLE_SIZE_IN_BYTES = 2;
        public static final int AUDIO_SAMPLE_SIZE_IN_BITS = 8 * AUDIO_SAMPLE_SIZE_IN_BYTES;
        public static final int AUDIO_CHANNELS = 2;
        public static final int AUDIO_FRAME_SIZE_IN_BYTES = AUDIO_CHANNELS * AUDIO_SAMPLE_SIZE_IN_BYTES;
        public static final int AUDIO_FRAMES_PER_SECOND = AUDIO_SAMPLES_PER_SECOND;
        public static final int AUDIO_FRAMES_PER_MILLISECOND = AUDIO_FRAMES_PER_SECOND / 1000;
        public static final boolean AUDIO_IS_PCM_SIGNED = true;
        public static final boolean AUDIO_IS_BIG_ENDIAN = false;
        public static int AUDIO_BUFFER_SIZE_FOR_LENGTH_MS(int bufferLengthMs)
        {
            int bufferSize = bufferLengthMs * Protocol.AUDIO_FRAMES_PER_MILLISECOND * Protocol.AUDIO_FRAME_SIZE_IN_BYTES;
            bufferSize -= (bufferSize % Protocol.AUDIO_FRAME_SIZE_IN_BYTES);
            return bufferSize;
        }
        
        // Connection Info
        public static final int CONNECTION_SERVER_PORT = 2468;
        
        // Request type
        private static final byte[] _REQUEST_STATUS = { 1, 1 };
        public static byte[] REQUEST_STATUS() { return Arrays.copyOf(_REQUEST_STATUS, _REQUEST_STATUS.length); }
        private static final byte[] _REQUEST_STREAMING = { 1, 2 };
        public static byte[] REQUEST_STREAMING() { return Arrays.copyOf(_REQUEST_STREAMING, _REQUEST_STREAMING.length); }
        
        // Status
        public static final byte STATUS_ERROR = -1;
        public static final byte STATUS_READY = 0;
        public static final byte STATUS_BUSY = 1;
        public static final Charset STATUS_NAME_CHARSET = StandardCharsets.US_ASCII;
        public static final int STATUS_NAME_MAX_LENGTH = 32;
        
        // Streaming
        public static final byte STREAMING_SERVER_REJECT = 1;
        public static final byte STREAMING_SERVER_ACCEPT = 2;
        public static void STREAMING_WRITE_BUFFER_LENGTH_MS(OutputStream out, short bufferLengthMs) throws IOException
        {
            byte[] writeBuffer = new byte[]
            {
                (byte)(bufferLengthMs & 0xff),
                (byte)((bufferLengthMs >> 8) & 0xff)
            };
            out.write(writeBuffer);
            out.flush();
        }
        public static short STREAMING_READ_BUFFER_LENGTH_MS(InputStream in) throws IOException
        {
            byte[] readBuffer = new byte[2];
            int bytesRead = in.read(readBuffer);
            if (bytesRead < 2) { throw new IOException("Did not read enough bytes for buffer length milliseconds value."); }
            return (short)
            (
                (readBuffer[0] & 0xff) 
                | ((readBuffer[1] & 0xff) << 8)
            );
        }
        
    }
    
    /**
     * This thread handles requests that come to the transmitter.
     */
    public static class RequestHandler extends Thread
    {
        // Static Fields
        public static final AudioFormat DESKTOP_AUDIO_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, 
            Protocol.AUDIO_SAMPLES_PER_SECOND, 
            Protocol.AUDIO_SAMPLE_SIZE_IN_BITS, 
            Protocol.AUDIO_CHANNELS, 
            Protocol.AUDIO_FRAME_SIZE_IN_BYTES, 
            Protocol.AUDIO_FRAMES_PER_SECOND, 
            Protocol.AUDIO_IS_BIG_ENDIAN
        );
        
        // Instance Fields
        public final Socket clientSocket;
        public final Mixer mixer;
        public final Line.Info targetLineInfo;
        public final Mixer.Info sourceMixerInfo;
        
        // Constructor
        public RequestHandler(Socket _clientSocket, Mixer _mixer, Line.Info _targetLineInfo, Mixer.Info _sourceMixerInfo)
        {
            this.clientSocket = _clientSocket;
            this.mixer = _mixer;
            this.targetLineInfo = _targetLineInfo;
            this.sourceMixerInfo = _sourceMixerInfo;
        }
        
        // Instance Methods
        public void logActivity(String descriptionFormat, Object... formatParameters)
        {
            SoftZoneTransmitter.logActivity(this.clientSocket, String.format(descriptionFormat, formatParameters));
        }            
        
        @Override
        public void run()
        {
            try
            {
                this.handleRequest();
            }
            catch (Throwable ex)
            {
                ex.printStackTrace();
            }
        }
        
        public void handleRequest() throws Throwable
        {
            try ( InputStream clientIn = this.clientSocket.getInputStream()
                ; OutputStream clientOut = this.clientSocket.getOutputStream()
            )
            {
                // get request (two bytes)
                byte[] request = new byte[2];
                int bytesRead = clientIn.read(request);
                if (bytesRead < request.length) 
                {
                    String message = String.format("Not enough bytes returned from reading (expected %d but got %d).", request.length, bytesRead);
                    throw new IOException(message);
                }
                
                // log request
                this.logActivity("Received Request: [%s %s]", request[0], request[1]);
                
                // branch on request
                if (Arrays.equals(Protocol.REQUEST_STATUS(), request))
                {
                    this.handleRequestStatus(clientIn, clientOut);
                }
                else if (Arrays.equals(Protocol.REQUEST_STREAMING(), request))
                {
                    this.handleRequestStreaming(clientIn, clientOut);
                }
            }
        }
        
        
        // Caster RequestHandler Status 
        private static volatile byte CURRENT_STATUS = Protocol.STATUS_READY;
        
        // Request Handler Methods
        public void handleRequestStatus(InputStream clientIn, OutputStream clientOut) throws IOException
        {
            clientOut.write(RequestHandler.CURRENT_STATUS);
            String name = "Generic SoftZone";
            try
            {
                name = InetAddress.getLocalHost().getHostName();
            }
            catch (Exception ex)
            {
                this.logActivity("%s", ex);
            }
            
            byte[] nameBytes = name.getBytes(Protocol.STATUS_NAME_CHARSET);
            if (nameBytes.length > Protocol.STATUS_NAME_MAX_LENGTH)
            {
                nameBytes = Arrays.copyOf(nameBytes, Protocol.STATUS_NAME_MAX_LENGTH);
            }
            
            clientOut.write(nameBytes);
            this.logActivity("Handled Request: Status");
        }
        
        public void handleRequestStreaming(final InputStream clientIn, OutputStream clientOut) throws IOException
        {
            if (RequestHandler.CURRENT_STATUS != Protocol.STATUS_READY) 
            {
                clientOut.write(Protocol.STREAMING_SERVER_REJECT);
                this.logActivity("Handled Request: Streaming (server rejected)");
                return; 
            }
            
            RequestHandler.CURRENT_STATUS = Protocol.STATUS_BUSY;
            try
            {
                clientOut.write(Protocol.STREAMING_SERVER_ACCEPT);
                this.logActivity("Started Handling Request: Streaming (server accepted)");
                this.logActivity("SO_SNDBUF = %d and SO_RCVBUF = %d", this.clientSocket.getSendBufferSize(), this.clientSocket.getReceiveBufferSize());
                this.clientSocket.setTcpNoDelay(true);
                this.logActivity("TCP_NODELAY = %s", this.clientSocket.getTcpNoDelay());
                
                short bufferLengthMs = Protocol.STREAMING_READ_BUFFER_LENGTH_MS(clientIn);
                this.logActivity("Buffer length requested (ms) = %s", bufferLengthMs);
                
                // get target line
                try (TargetDataLine targetDataLine = (TargetDataLine)this.mixer.getLine(this.targetLineInfo))
                {
                    // open target line
                    int bufferSize = Protocol.AUDIO_BUFFER_SIZE_FOR_LENGTH_MS(bufferLengthMs);
                    targetDataLine.open(DESKTOP_AUDIO_FORMAT, bufferSize);

                    byte[] buffer = new byte[targetDataLine.getBufferSize()];
                    this.logActivity("Target line open.");
                    this.logActivity("Buffer size (bytes) = %s ", buffer.length);
                    
                    try
                    {
                        long readTimeTotal = 0;
                        long writeTimeTotal = 0;
                        long numberOfLoops = 0;
                        // long totalBytesRead = 0;
                        // long startTimeNanos = System.nanoTime();
                        try
                        {
                            if (this.sourceMixerInfo == null)
                            {
                                targetDataLine.start();
                                while (true)
                                {
                                    long preRead = System.nanoTime();
                                    int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                                    long readTime = System.nanoTime() - preRead;
                                    if (bytesRead < 0) { break; }
                                    long preWrite = System.nanoTime();
                                    clientOut.write(buffer, 0, bytesRead);
                                    long writeTime = System.nanoTime() - preWrite;
                                    readTimeTotal += readTime;
                                    writeTimeTotal += writeTime;
                                    ++numberOfLoops;
                                    // totalBytesRead += bytesRead;
                                }
                            }
                            else
                            {
                                try (SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(DESKTOP_AUDIO_FORMAT, this.sourceMixerInfo))
                                {
                                    sourceDataLine.open();
                                    sourceDataLine.start();
                                    targetDataLine.start();
                                    while (true)
                                    {
                                        long preRead = System.nanoTime();
                                        int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                                        long readTime = System.nanoTime() - preRead;
                                        if (bytesRead < 0) { break; }
                                        long preWrite = System.nanoTime();
                                        clientOut.write(buffer, 0, bytesRead);
                                        sourceDataLine.write(buffer, 0, bytesRead);
                                        long writeTime = System.nanoTime() - preWrite;
                                        readTimeTotal += readTime;
                                        writeTimeTotal += writeTime;
                                        ++numberOfLoops;
                                        // totalBytesRead += bytesRead;
                                    }
                                }
                            }
                        }
                        finally
                        {
                            // // for 2 bytes per channel-sample, 2 channel-samples per frame, and 48000 frames per second:
                            // // we would expect 192000 bytes per second
                            // long totalTimeNanos = System.nanoTime() - startTimeNanos;
                            // this.logActivity("bytes per second = %s", 1000*1000*1000.*totalBytesRead / totalTimeNanos);
                            this.logActivity("average read time  = %sms", readTimeTotal / (1000*1000. * numberOfLoops));
                            this.logActivity("average write time = %sms", writeTimeTotal / (1000*1000. * numberOfLoops));
                        }
                    }
                    catch (Exception ex)
                    {
                        this.logActivity("%s", ex);
                        this.logActivity("Finished Handling Request: Streaming (connection severed)");
                        System.out.println("");
                    }
                }
                catch (Exception ex)
                {
                    this.logActivity("%s", ex);
                    this.logActivity("Finished Handling Request: Streaming (target line error)");
                    System.out.println("");
                }
            }
            finally
            {
                RequestHandler.CURRENT_STATUS = Protocol.STATUS_READY;
            }
        }
    }
    
    /**
     * A collection of utilities for receiver applications to use.
     * Includes code to scan for transmitters.
     */
    public static class ReceiverUtilities
    {
        // Constructor
        private ReceiverUtilities() { throw new UnsupportedOperationException(); }
        
        // Static Inner Classes (Scanning)
        /**
         * A user interface that communicates with a ScanningMasterThread.
         */
        public static interface ScanningUserInterface
        {
            void handleExceptionDuringScanWorkerExecution(Exception ex);
            void incrementScanProgressBy(int amount);
            String getLocalAddressPrefix() throws Exception;
            void handleExceptionDuringScanStart(Exception ex);
            void resetScanReadyZonesAndProgress();
            void addReadyScanResult(ScanResult scanResult);
            void handleInterruptedExceptionDuringButtonCreation(InterruptedException ex);
            void handleExceptionDuringWorkerThreadJoin(Exception ex);
            void showScanFinishedSuccessfully();
        }
        
        /**
         * The result of a scan for a transmitter.
         */
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
        
        /**
         * A thread that takes an unscanned host, scans for it, and 
         * reports a scan result for ready hosts.
         */
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
                        socket.connect(socketAddress, this.masterThread.scanConnectionTimeoutMs);
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
                        this.masterThread.userInterface.handleExceptionDuringScanWorkerExecution(ex);
                    }
                    finally
                    {
                        this.masterThread.userInterface.incrementScanProgressBy(1);
                    }
                }
            }
        }
        
        /**
         * A thread that orchestrates the creation and utilization of 
         * several ScanningWorkerThread objects in order to communicate 
         * the scan results to a ScanningUserInterface.
         */
        public static class ScanningMasterThread extends Thread
        {
            // Locks
            private static final Object statusLock = new Object();
            
            // Static Variables
            private static volatile byte status = Protocol.STATUS_READY;
            private static volatile boolean stopFlag = true;
            
            // Instance Fields
            public final ScanningUserInterface userInterface;
            public final int port;
            public final int scanWorkerThreadCount;
            public final int scanConnectionTimeoutMs;
            
            // Constructor
            public ScanningMasterThread(ScanningUserInterface _userInterface, 
                                        int                   _port, 
                                        int                   _scanWorkerThreadCount, 
                                        int                   _scanConnectionTimeoutMs
            )
            {
                this.userInterface = _userInterface;
                this.port = _port;
                this.scanWorkerThreadCount = _scanWorkerThreadCount;
                this.scanConnectionTimeoutMs = _scanConnectionTimeoutMs;
            }
            
            // Static Methods
            public static void stopScanning() { ScanningMasterThread.stopFlag = true; }
            
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
                        localAddressPrefix = this.userInterface.getLocalAddressPrefix();
                    }
                    catch (Exception ex)
                    {
                        this.userInterface.handleExceptionDuringScanStart(ex);
                        return;
                    }
                    
                    ScanningMasterThread.status = Protocol.STATUS_BUSY;
                    ScanningMasterThread.stopFlag = false;
                }
                
                // clear zone button list
                this.userInterface.resetScanReadyZonesAndProgress();
                
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
                ArrayList<Thread> workerThreads = new ArrayList<>(this.scanWorkerThreadCount);
                for (int i = 0; i < this.scanWorkerThreadCount; ++i)
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
                            final ScanResult scanResult = readyHosts.poll(self.scanConnectionTimeoutMs, TimeUnit.MILLISECONDS);
                            if (scanResult == null) { continue; }
                            
                            // add to zone list
                            self.userInterface.addReadyScanResult(scanResult);
                        }
                        catch (InterruptedException ex)
                        {
                            self.userInterface.handleInterruptedExceptionDuringButtonCreation(ex);
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
                    this.userInterface.handleExceptionDuringWorkerThreadJoin(ex);
                }
                finally
                {
                    synchronized (ScanningMasterThread.statusLock)
                    {
                        ScanningMasterThread.status = Protocol.STATUS_READY;
                        ScanningMasterThread.stopFlag = true;
                    }
                    this.userInterface.showScanFinishedSuccessfully();
                }
            }
        }
        
        // Static Inner Classes (Streaming)
        /**
         * A user interface that communicates with a StreamingPlaybackMasterThread.
         */
        public static interface StreamingPlaybackUserInterface
        {
            public StreamingPlaybackDevice getPlaybackDevice(int bufferLengthMs);
            public void updateMonitors(byte leftSampleHighByte, byte rightSampleHighByte);
            public void handleExceptionDuringMonitorUpdating(Exception ex);
            public String getStatusText(int bufferLengthMs);
            public void setStatusText(String statusText);
            public void handleExceptionDuringSocketConnection(Exception ex);
            public void showShortPopupMessage(String message);
            public void logStreamingStatistics(double bytesPerSecond, double averageReadTime, double averageWriteTime);
            public void handleExceptionDuringStreaming(Exception ex);
            public void clearMonitorLevelsAndStatus();
        }
        
        /**
         * A playback device used for streaming.
         */
        public static abstract class StreamingPlaybackDevice implements AutoCloseable
        {
            // Instance Fields
            public final int requestedBufferSizeInBytes;
            
            // Constructor
            public StreamingPlaybackDevice(int _requestedBufferSizeInBytes)
            {
                this.requestedBufferSizeInBytes = _requestedBufferSizeInBytes;
            }
            
            // Instance Methods
            public abstract void initialize();
            public abstract int write(byte[] buffer, int offset, int length);
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
            public final StreamingPlaybackMasterThread masterThread;
            
            // Constructor
            public MonitorUpdatingThread(PipedInputStream _levelIn, StreamingPlaybackMasterThread _masterThread)
            {
                this.levelIn = _levelIn;
                this.masterThread = _masterThread;
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
                try
                {
                    byte[] levelBuffer = new byte[MONITOR_BUFFER_LENGTH];
                    boolean ever = true;
                    for (;ever;)
                    {
                        int bytesRead = levelIn.read(levelBuffer, 0, MONITOR_BUFFER_LENGTH);
                        if (bytesRead < MONITOR_BUFFER_LENGTH) { continue; }
                        
                        this.masterThread.userInterface.updateMonitors(levelBuffer[MONITOR_LEFT_INDEX], levelBuffer[MONITOR_RIGHT_INDEX]);
                    }
                }
                catch (Exception ex)
                {
                    this.masterThread.userInterface.handleExceptionDuringMonitorUpdating(ex);
                }
            }
        }
        
        
        /**
         * This thread receives and plays audio from the selected server.
         */
        public static class StreamingPlaybackMasterThread extends Thread
        {
            // Locks
            public static final Object statusLock = new Object();
            
            // Static Variables
            private static volatile byte status = Protocol.STATUS_READY;
            private static volatile boolean stopFlag = true;
            private static volatile StreamingPlaybackMasterThread activeThread = null;
            
            // Instance Fields
            public volatile StreamingPlaybackUserInterface userInterface;
            public final String name;
            public final String host;
            public final int port;
            public final short bufferLengthMs;
            public final String statusText;
            public final int connectionTimeoutMs;
            
            // Constructor
            public StreamingPlaybackMasterThread(StreamingPlaybackUserInterface _userInterface, 
                                                 String                         _name, 
                                                 String                         _host, 
                                                 int                            _port, 
                                                 short                          _bufferLengthMs, 
                                                 int                            _connectionTimeoutMs
            )
            {
                this.userInterface = _userInterface;
                this.name = _name;
                this.host = _host;
                this.port = _port;
                this.bufferLengthMs = _bufferLengthMs;
                this.statusText = this.userInterface.getStatusText(this.bufferLengthMs);
                this.connectionTimeoutMs = _connectionTimeoutMs;
            }
            
            // Static Methods
            public static void stopPlayback() { StreamingPlaybackMasterThread.stopFlag = true; }
            public static StreamingPlaybackMasterThread getActiveThread() { return StreamingPlaybackMasterThread.activeThread; }
            
            // Instance Methods
            @Override
            public void run()
            {
                final StreamingPlaybackMasterThread self = this;
                synchronized (StreamingPlaybackMasterThread.statusLock)
                {
                    if (StreamingPlaybackMasterThread.status != Protocol.STATUS_READY) 
                    {
                        return;
                    }
                    StreamingPlaybackMasterThread.status = Protocol.STATUS_BUSY;
                    StreamingPlaybackMasterThread.stopFlag = false;
                    StreamingPlaybackMasterThread.activeThread = this;
                }
                
                // open connection
                Socket socket = new Socket();
                InetSocketAddress serverAddress = new InetSocketAddress(this.host, this.port);
                try
                {
                    socket.connect(serverAddress, this.connectionTimeoutMs);
                }
                catch (Exception ex)
                {
                    this.userInterface.handleExceptionDuringSocketConnection(ex);
                    
                    synchronized (StreamingPlaybackMasterThread.statusLock)
                    {
                        StreamingPlaybackMasterThread.status = Protocol.STATUS_READY;
                        StreamingPlaybackMasterThread.stopFlag = true;
                        StreamingPlaybackMasterThread.activeThread = null;
                    }
                    return;
                }
                
                // start streaming
                try ( StreamingPlaybackDevice audioDevice = this.userInterface.getPlaybackDevice(this.bufferLengthMs)
                    ; InputStream serverIn = socket.getInputStream()
                    ; OutputStream serverOut = socket.getOutputStream()
                    ; PipedInputStream levelIn = new PipedInputStream()
                    ; PipedOutputStream levelOut = new PipedOutputStream(levelIn)
                )
                {
                    // change status text
                    this.userInterface.setStatusText(this.statusText);
                    
                    // monitor levels
                    Thread monitorThread = new MonitorUpdatingThread(levelIn, this);
                    monitorThread.start();
                    
                    // make audio track ready
                    byte[] buffer = new byte[audioDevice.requestedBufferSizeInBytes];
                    audioDevice.initialize();
                    
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
                    long totalBytesRead = 0;
                    long startTimeNanos = System.nanoTime();
                    try
                    {
                        while (!StreamingPlaybackMasterThread.stopFlag)
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
                            audioDevice.write(buffer, 0, bytesRead);
                            if (bytesRead >= MonitorUpdatingThread.MONITOR_BUFFER_LENGTH)
                            {
                                levelOut.write(buffer, 0, MonitorUpdatingThread.MONITOR_BUFFER_LENGTH);
                            }
                            long writeTime = System.nanoTime() - preWrite;
                            readTimeTotal += readTime;
                            writeTimeTotal += writeTime;
                            ++numberOfLoops;
                            totalBytesRead += bytesRead;
                        }
                        final String message = "Stream stopped.";
                        this.userInterface.showShortPopupMessage(message);
                    }
                    finally
                    {
                        // for 2 bytes per channel-sample, 2 channel-samples per frame, and 48000 frames per second:
                        // we would expect 192000 bytes per second
                        long totalTimeNanos = System.nanoTime() - startTimeNanos;
                        double bytesPerSecond = 1000*1000*1000.*totalBytesRead / totalTimeNanos;
                        double averageReadTime = readTimeTotal / (1000*1000. * numberOfLoops);
                        double averageWriteTime = writeTimeTotal / (1000*1000. * numberOfLoops);
                        this.userInterface.logStreamingStatistics(bytesPerSecond, averageReadTime, averageWriteTime);
                    }
                }
                catch (Exception ex)
                {
                    this.userInterface.handleExceptionDuringStreaming(ex);
                    
                    final String errorMessage = "An error occurred while streaming.";
                    this.userInterface.showShortPopupMessage(errorMessage);
                }
                finally
                {
                    synchronized (StreamingPlaybackMasterThread.statusLock)
                    {
                        StreamingPlaybackMasterThread.status = Protocol.STATUS_READY;
                        StreamingPlaybackMasterThread.stopFlag = true;
                        StreamingPlaybackMasterThread.activeThread = null;
                    }
                    
                    // clear monitor levels and status
                    this.userInterface.clearMonitorLevelsAndStatus();
                }
            }
        }
        
    }
    // end ReceiverUtilities
    
}
