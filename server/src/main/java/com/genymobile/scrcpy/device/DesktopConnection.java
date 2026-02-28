package com.genymobile.scrcpy.device;

import com.genymobile.scrcpy.control.ControlChannel;
import com.genymobile.scrcpy.util.IO;
import com.genymobile.scrcpy.util.StringUtils;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public final class DesktopConnection implements Closeable {

    private static final int DEVICE_NAME_FIELD_LENGTH = 64;

    private static final String SOCKET_NAME_PREFIX = "scrcpy";

    // Unix socket mode (null in TCP mode)
    private final LocalSocket videoSocket;
    private final LocalSocket audioSocket;
    private final LocalSocket controlSocket;

    // FileDescriptors for data streaming (derived from sockets in Unix mode,
    // or raw accepted TCP FDs in TCP mode)
    private final FileDescriptor videoFd;
    private final FileDescriptor audioFd;

    // TCP control FD (null in Unix mode); kept for shutdown/close
    private final FileDescriptor tcpControlFd;

    private final ControlChannel controlChannel;

    // Unix socket mode constructor
    private DesktopConnection(LocalSocket videoSocket, LocalSocket audioSocket, LocalSocket controlSocket) throws IOException {
        this.videoSocket = videoSocket;
        this.audioSocket = audioSocket;
        this.controlSocket = controlSocket;
        this.tcpControlFd = null;

        videoFd = videoSocket != null ? videoSocket.getFileDescriptor() : null;
        audioFd = audioSocket != null ? audioSocket.getFileDescriptor() : null;
        controlChannel = controlSocket != null ? new ControlChannel(controlSocket) : null;
    }

    // TCP socket mode constructor
    private DesktopConnection(FileDescriptor videoFd, FileDescriptor audioFd, FileDescriptor controlFd) throws IOException {
        this.videoSocket = null;
        this.audioSocket = null;
        this.controlSocket = null;
        this.tcpControlFd = controlFd;

        this.videoFd = videoFd;
        this.audioFd = audioFd;
        controlChannel = (controlFd != null)
                ? new ControlChannel(newInputStream(controlFd), newOutputStream(controlFd))
                : null;
    }

    private static LocalSocket connect(String abstractName) throws IOException {
        LocalSocket localSocket = new LocalSocket();
        localSocket.connect(new LocalSocketAddress(abstractName));
        return localSocket;
    }

    private static String getSocketName(int scid) {
        if (scid == -1) {
            // If no SCID is set, use "scrcpy" to simplify using scrcpy-server alone
            return SOCKET_NAME_PREFIX;
        }

        return SOCKET_NAME_PREFIX + String.format("_%08x", scid);
    }

    public static DesktopConnection open(int scid, boolean tunnelForward, boolean video, boolean audio, boolean control, boolean sendDummyByte)
            throws IOException {
        return open(scid, tunnelForward, video, audio, control, sendDummyByte, 0);
    }

    public static DesktopConnection open(int scid, boolean tunnelForward, boolean video, boolean audio, boolean control, boolean sendDummyByte,
            int tcpPort) throws IOException {
        if (tcpPort > 0) {
            return openTcp(tcpPort, video, audio, control, sendDummyByte);
        }

        String socketName = getSocketName(scid);

        LocalSocket videoSocket = null;
        LocalSocket audioSocket = null;
        LocalSocket controlSocket = null;
        try {
            if (tunnelForward) {
                try (LocalServerSocket localServerSocket = new LocalServerSocket(socketName)) {
                    if (video) {
                        videoSocket = localServerSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            videoSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                    if (audio) {
                        audioSocket = localServerSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            audioSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                    if (control) {
                        controlSocket = localServerSocket.accept();
                        if (sendDummyByte) {
                            // send one byte so the client may read() to detect a connection error
                            controlSocket.getOutputStream().write(0);
                            sendDummyByte = false;
                        }
                    }
                }
            } else {
                if (video) {
                    videoSocket = connect(socketName);
                }
                if (audio) {
                    audioSocket = connect(socketName);
                }
                if (control) {
                    controlSocket = connect(socketName);
                }
            }
        } catch (IOException | RuntimeException e) {
            if (videoSocket != null) {
                videoSocket.close();
            }
            if (audioSocket != null) {
                audioSocket.close();
            }
            if (controlSocket != null) {
                controlSocket.close();
            }
            throw e;
        }

        return new DesktopConnection(videoSocket, audioSocket, controlSocket);
    }

    /**
     * Open a TCP server socket on the given port and accept incoming connections.
     * This allows the scrcpy client to connect directly via IP without ADB tunneling.
     */
    private static DesktopConnection openTcp(int tcpPort, boolean video, boolean audio, boolean control, boolean sendDummyByte)
            throws IOException {
        FileDescriptor serverFd = null;
        FileDescriptor videoFd = null;
        FileDescriptor audioFd = null;
        FileDescriptor controlFd = null;
        try {
            serverFd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, OsConstants.IPPROTO_TCP);
            Os.setsockopt(serverFd, OsConstants.SOL_SOCKET, OsConstants.SO_REUSEADDR, 1);
            Os.bind(serverFd, InetAddress.getByName("0.0.0.0"), tcpPort);
            Os.listen(serverFd, 5);

            if (video) {
                videoFd = Os.accept(serverFd, null);
                if (sendDummyByte) {
                    Os.write(videoFd, new byte[]{0}, 0, 1);
                    sendDummyByte = false;
                }
            }
            if (audio) {
                audioFd = Os.accept(serverFd, null);
                if (sendDummyByte) {
                    Os.write(audioFd, new byte[]{0}, 0, 1);
                    sendDummyByte = false;
                }
            }
            if (control) {
                controlFd = Os.accept(serverFd, null);
                if (sendDummyByte) {
                    Os.write(controlFd, new byte[]{0}, 0, 1);
                    sendDummyByte = false;
                }
            }
        } catch (ErrnoException e) {
            closeQuietly(videoFd);
            closeQuietly(audioFd);
            closeQuietly(controlFd);
            throw new IOException(e);
        } finally {
            closeQuietly(serverFd);
        }

        return new DesktopConnection(videoFd, audioFd, controlFd);
    }

    private static void closeQuietly(FileDescriptor fd) {
        if (fd != null) {
            try {
                Os.close(fd);
            } catch (ErrnoException e) {
                // ignore
            }
        }
    }

    /** Create a non-owning InputStream reading from a FileDescriptor via Os.read(). */
    private static InputStream newInputStream(FileDescriptor fd) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                byte[] buf = {0};
                int n = read(buf, 0, 1);
                return n < 0 ? -1 : (buf[0] & 0xff);
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                try {
                    int n = Os.read(fd, buf, off, len);
                    return n == 0 ? -1 : n;
                } catch (ErrnoException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    /** Create a non-owning OutputStream writing to a FileDescriptor via Os.write(). */
    private static OutputStream newOutputStream(FileDescriptor fd) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] buf, int off, int len) throws IOException {
                try {
                    while (len > 0) {
                        int n = Os.write(fd, buf, off, len);
                        if (n <= 0) {
                            throw new IOException("Os.write() returned " + n);
                        }
                        off += n;
                        len -= n;
                    }
                } catch (ErrnoException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    private LocalSocket getFirstSocket() {
        if (videoSocket != null) {
            return videoSocket;
        }
        if (audioSocket != null) {
            return audioSocket;
        }
        return controlSocket;
    }

    private FileDescriptor getFirstFd() {
        if (videoFd != null) {
            return videoFd;
        }
        if (audioFd != null) {
            return audioFd;
        }
        return tcpControlFd;
    }

    public void shutdown() throws IOException {
        if (videoSocket != null) {
            videoSocket.shutdownInput();
            videoSocket.shutdownOutput();
        } else {
            shutdownFdQuietly(videoFd);
        }
        if (audioSocket != null) {
            audioSocket.shutdownInput();
            audioSocket.shutdownOutput();
        } else {
            shutdownFdQuietly(audioFd);
        }
        if (controlSocket != null) {
            controlSocket.shutdownInput();
            controlSocket.shutdownOutput();
        } else {
            shutdownFdQuietly(tcpControlFd);
        }
    }

    private static void shutdownFdQuietly(FileDescriptor fd) {
        if (fd != null) {
            try {
                Os.shutdown(fd, OsConstants.SHUT_RDWR);
            } catch (ErrnoException e) {
                // ignore
            }
        }
    }

    public void close() throws IOException {
        if (videoSocket != null) {
            videoSocket.close();
        }
        if (audioSocket != null) {
            audioSocket.close();
        }
        if (controlSocket != null) {
            controlSocket.close();
        }
        // TCP mode: close raw FDs (videoFd and audioFd are the accepted TCP FDs)
        if (tcpControlFd != null) {
            closeQuietly(videoFd);
            closeQuietly(audioFd);
            closeQuietly(tcpControlFd);
        }
    }

    public void sendDeviceMeta(String deviceName) throws IOException {
        byte[] buffer = new byte[DEVICE_NAME_FIELD_LENGTH];

        byte[] deviceNameBytes = deviceName.getBytes(StandardCharsets.UTF_8);
        int len = StringUtils.getUtf8TruncationIndex(deviceNameBytes, DEVICE_NAME_FIELD_LENGTH - 1);
        System.arraycopy(deviceNameBytes, 0, buffer, 0, len);
        // byte[] are always 0-initialized in java, no need to set '\0' explicitly

        FileDescriptor fd;
        if (tcpControlFd != null) {
            // TCP mode: use first available FD
            fd = getFirstFd();
        } else {
            // Unix socket mode: use first socket's FD
            fd = getFirstSocket().getFileDescriptor();
        }
        IO.writeFully(fd, buffer, 0, buffer.length);
    }

    public FileDescriptor getVideoFd() {
        return videoFd;
    }

    public FileDescriptor getAudioFd() {
        return audioFd;
    }

    public ControlChannel getControlChannel() {
        return controlChannel;
    }
}
