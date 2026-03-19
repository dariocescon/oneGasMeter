package com.aton.proj.oneGasMeter.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlmsInboundServerTest {

    @Mock
    private ApplicationEventPublisher publisher;

    @Test
    void stop_withoutStart_doesNotThrow() {
        DlmsInboundServerConfig config = DlmsInboundServerConfig.builder().build();
        DlmsInboundServer server = new DlmsInboundServer(config, publisher, port -> {
            throw new IOException("should not be called");
        });

        // stop() before start() must not throw NPE or any other exception
        assertThatCode(server::stop).doesNotThrowAnyException();
    }

    @Test
    void acceptLoop_bindsToConfiguredPort() throws Exception {
        int expectedPort = 9876;
        DlmsInboundServerConfig config = DlmsInboundServerConfig.builder()
                .listenPort(expectedPort)
                .build();

        CountDownLatch bound = new CountDownLatch(1);
        AtomicInteger capturedPort = new AtomicInteger(-1);

        ServerSocket mockSS = mock(ServerSocket.class);
        when(mockSS.isClosed()).thenReturn(true); // exit the while loop immediately

        DlmsInboundServer server = new DlmsInboundServer(config, publisher, port -> {
            capturedPort.set(port);
            bound.countDown();
            return mockSS;
        });

        server.start();

        assertThat(bound.await(3, TimeUnit.SECONDS))
                .as("ServerSocketFactory should be called within 3 seconds")
                .isTrue();
        assertThat(capturedPort.get()).isEqualTo(expectedPort);

        server.stop();
    }

    @Test
    void acceptLoop_logsError_whenBindFails() throws Exception {
        DlmsInboundServerConfig config = DlmsInboundServerConfig.builder()
                .listenPort(4059)
                .build();

        CountDownLatch factoryCalled = new CountDownLatch(1);

        // Factory simulates address-already-in-use
        DlmsInboundServer server = new DlmsInboundServer(config, publisher, port -> {
            factoryCalled.countDown();
            throw new IOException("Address already in use");
        });

        server.start();

        // The factory should be called; the accept loop should terminate gracefully
        assertThat(factoryCalled.await(3, TimeUnit.SECONDS))
                .as("Factory should be invoked even when bind fails")
                .isTrue();

        // Give the virtual thread a moment to terminate
        Thread.sleep(100);

        // stop() must be safe even after a failed bind
        assertThatCode(server::stop).doesNotThrowAnyException();
    }
}
