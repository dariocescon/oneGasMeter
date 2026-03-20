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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DlmsInboundServerTest {

    @Mock
    private ApplicationEventPublisher publisher;

    @Test
    void shutdown_withoutRun_doesNotThrow() {
        DlmsInboundServerConfig config = DlmsInboundServerConfig.builder().build();
        DlmsInboundServer server = new DlmsInboundServer(config, publisher, (port, backlog) -> {
            throw new IOException("should not be called");
        });

        // shutdown() before run() must not throw NPE or any other exception
        assertThatCode(server::shutdown).doesNotThrowAnyException();
    }

    @Test
    void run_bindsToConfiguredPort() throws Exception {
        int expectedPort = 9876;
        DlmsInboundServerConfig config = DlmsInboundServerConfig.builder()
                .listenPort(expectedPort)
                .build();

        // 'accepting' fires when accept() is actually entered — guarantees that
        // serverSocket and running are fully set before shutdown() is called
        CountDownLatch accepting = new CountDownLatch(1);
        CountDownLatch acceptWait = new CountDownLatch(1);
        AtomicInteger capturedPort = new AtomicInteger(-1);

        ServerSocket mockSS = mock(ServerSocket.class);
        when(mockSS.accept()).thenAnswer(inv -> {
            accepting.countDown();       // signal: we are blocked inside accept()
            try {
                acceptWait.await();      // wait to be released by close()
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("socket closed");
        });
        // close() unblocks accept() (mirrors real ServerSocket behaviour)
        doAnswer(inv -> { acceptWait.countDown(); return null; }).when(mockSS).close();

        DlmsInboundServer server = new DlmsInboundServer(config, publisher, (port, backlog) -> {
            capturedPort.set(port);
            return mockSS;
        });

        Thread serverThread = Thread.ofVirtual().name("test-server").start(server::run);

        // Wait until the server is genuinely blocked in accept(), then check port
        assertThat(accepting.await(3, TimeUnit.SECONDS))
                .as("Server should reach accept() within 3 seconds")
                .isTrue();
        assertThat(capturedPort.get()).isEqualTo(expectedPort);

        server.shutdown();
        serverThread.join(2000);
    }

    @Test
    void run_throwsRuntimeException_whenBindFails() {
        DlmsInboundServerConfig config = DlmsInboundServerConfig.builder()
                .listenPort(4059)
                .build();

        DlmsInboundServer server = new DlmsInboundServer(config, publisher, (port, backlog) -> {
            throw new IOException("Address already in use");
        });

        assertThatThrownBy(server::run)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot start DLMS inbound server");

        // shutdown() must be safe even after a failed bind
        assertThatCode(server::shutdown).doesNotThrowAnyException();
    }
}
