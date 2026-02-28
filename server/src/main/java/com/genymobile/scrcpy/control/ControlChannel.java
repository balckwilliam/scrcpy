package com.genymobile.scrcpy.control;

import android.net.LocalSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ControlChannel {

    private final ControlMessageReader reader;
    private final DeviceMessageWriter writer;

    public ControlChannel(InputStream in, OutputStream out) {
        reader = new ControlMessageReader(in);
        writer = new DeviceMessageWriter(out);
    }

    public ControlChannel(LocalSocket controlSocket) throws IOException {
        this(controlSocket.getInputStream(), controlSocket.getOutputStream());
    }

    public ControlMessage recv() throws IOException {
        return reader.read();
    }

    public void send(DeviceMessage msg) throws IOException {
        writer.write(msg);
    }
}
