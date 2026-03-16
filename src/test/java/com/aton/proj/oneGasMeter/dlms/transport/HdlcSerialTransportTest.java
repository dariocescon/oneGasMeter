package com.aton.proj.oneGasMeter.dlms.transport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdlcSerialTransportTest {

    /** Builds a simple HDLC frame: 0x7E + payload + 0x7E. */
    private static byte[] hdlcFrame(byte... payload) {
        byte[] frame = new byte[payload.length + 2];
        frame[0] = HdlcSerialTransport.HDLC_FLAG;
        System.arraycopy(payload, 0, frame, 1, payload.length);
        frame[frame.length - 1] = HdlcSerialTransport.HDLC_FLAG;
        return frame;
    }

    @Test
    void connectSetsConnectedTrue() throws IOException {
        HdlcSerialTransport t = new HdlcSerialTransport(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream());

        assertThat(t.isConnected()).isFalse();
        t.connect();
        assertThat(t.isConnected()).isTrue();
    }

    @Test
    void disconnectSetsConnectedFalse() throws IOException {
        HdlcSerialTransport t = new HdlcSerialTransport(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream());

        t.connect();
        t.disconnect();
        assertThat(t.isConnected()).isFalse();
    }

    @Test
    void writeFlushesAllBytesToOutputStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HdlcSerialTransport t = new HdlcSerialTransport(
                new ByteArrayInputStream(new byte[0]), out);

        byte[] data = {0x7E, 0x01, 0x02, 0x7E};
        t.write(data);

        assertThat(out.toByteArray()).isEqualTo(data);
    }

    @Test
    void readReturnsSingleCompleteHdlcFrame() throws IOException {
        byte[] frame = hdlcFrame((byte) 0x01, (byte) 0x02, (byte) 0x03);
        HdlcSerialTransport t = new HdlcSerialTransport(
                new ByteArrayInputStream(frame),
                new ByteArrayOutputStream());

        byte[] result = t.read();
        assertThat(result).isEqualTo(frame);
    }

    @Test
    void readSkipsLeadingInterFrameFillFlags() throws IOException {
        // Stream: 0x7E 0x7E 0x7E <frame>
        byte[] frame = hdlcFrame((byte) 0xAB, (byte) 0xCD);
        byte[] stream = new byte[frame.length + 3];
        stream[0] = 0x7E;
        stream[1] = 0x7E;
        stream[2] = 0x7E;
        System.arraycopy(frame, 0, stream, 3, frame.length);

        HdlcSerialTransport t = new HdlcSerialTransport(
                new ByteArrayInputStream(stream),
                new ByteArrayOutputStream());

        byte[] result = t.read();
        assertThat(result).isEqualTo(frame);
    }

    @Test
    void readReturnFirstFrameWhenMultipleFramesPresent() throws IOException {
        byte[] frame1 = hdlcFrame((byte) 0x01);
        byte[] frame2 = hdlcFrame((byte) 0x02);
        byte[] stream = new byte[frame1.length + frame2.length];
        System.arraycopy(frame1, 0, stream, 0, frame1.length);
        System.arraycopy(frame2, 0, stream, frame1.length, frame2.length);

        HdlcSerialTransport t = new HdlcSerialTransport(
                new ByteArrayInputStream(stream),
                new ByteArrayOutputStream());

        byte[] result = t.read();
        assertThat(result).isEqualTo(frame1);
    }

    @Test
    void readThrowsIOExceptionWhenStreamEndsBeforeEndFlag() {
        // Frame with only start flag and data, no end flag
        byte[] incomplete = {0x7E, 0x01, 0x02};
        HdlcSerialTransport t = new HdlcSerialTransport(
                new ByteArrayInputStream(incomplete),
                new ByteArrayOutputStream());

        assertThatThrownBy(t::read)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("complete HDLC frame");
    }

    @Test
    void readThrowsIOExceptionOnEmptyStream() {
        HdlcSerialTransport t = new HdlcSerialTransport(
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream());

        assertThatThrownBy(t::read)
                .isInstanceOf(IOException.class);
    }

    @Test
    void hdlcFlagConstantIs0x7E() {
        assertThat(HdlcSerialTransport.HDLC_FLAG).isEqualTo((byte) 0x7E);
    }
}
