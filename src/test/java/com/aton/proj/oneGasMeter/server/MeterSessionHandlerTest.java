package com.aton.proj.oneGasMeter.server;

import com.aton.proj.oneGasMeter.dlms.DlmsMeterClient;
import com.aton.proj.oneGasMeter.exception.DlmsCommunicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeterSessionHandlerTest {

    @Mock
    private Socket socket;
    @Mock
    private DlmsMeterClient client;
    @Mock
    private ApplicationEventPublisher publisher;
    @Mock
    private MeterSessionHandler.SessionFactory sessionFactory;

    private DlmsInboundServerConfig config;

    @BeforeEach
    void setUp() throws Exception {
        config = DlmsInboundServerConfig.builder().build();
        InetAddress addr = InetAddress.getByName("192.168.1.10");
        when(socket.getInetAddress()).thenReturn(addr);
    }

    @Test
    void run_happyPath_connectsPublishesEventAndDisconnects() throws Exception {
        when(sessionFactory.create(socket, config)).thenReturn(client);
        doNothing().when(client).connect();

        MeterSessionHandler handler = new MeterSessionHandler(socket, config, publisher, sessionFactory);
        handler.run();

        verify(client).connect();

        ArgumentCaptor<MeterSessionEvent> eventCaptor = ArgumentCaptor.forClass(MeterSessionEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        MeterSessionEvent event = eventCaptor.getValue();
        assertThat(event.meterIp()).isEqualTo("192.168.1.10");
        assertThat(event.connectedAt()).isNotNull();
        assertThat(event.client()).isSameAs(client);

        verify(client).disconnect();
        verify(socket, never()).close();
    }

    @Test
    void run_factoryThrowsIOException_closesRawSocketInstead() throws Exception {
        when(sessionFactory.create(socket, config)).thenThrow(new IOException("stream error"));

        MeterSessionHandler handler = new MeterSessionHandler(socket, config, publisher, sessionFactory);
        handler.run();

        verify(socket).close();
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void run_connectThrowsDlmsCommunicationException_disconnectsClientNotSocket() throws Exception {
        when(sessionFactory.create(socket, config)).thenReturn(client);
        doThrow(new DlmsCommunicationException("handshake failed")).when(client).connect();

        MeterSessionHandler handler = new MeterSessionHandler(socket, config, publisher, sessionFactory);
        handler.run();

        verify(client).disconnect();
        verify(socket, never()).close();
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void run_eventPublisherThrowsRuntimeException_disconnectStillCalled() throws Exception {
        when(sessionFactory.create(socket, config)).thenReturn(client);
        doNothing().when(client).connect();
        doThrow(new RuntimeException("listener failed")).when(publisher).publishEvent(org.mockito.ArgumentMatchers.any());

        MeterSessionHandler handler = new MeterSessionHandler(socket, config, publisher, sessionFactory);
        handler.run();

        // disconnect must still be called despite the exception in publishEvent
        verify(client).disconnect();
    }

    @Test
    void run_meterIpFromSocketInetAddress() throws Exception {
        InetAddress specific = InetAddress.getByName("10.20.30.40");
        when(socket.getInetAddress()).thenReturn(specific);
        when(sessionFactory.create(socket, config)).thenReturn(client);
        doNothing().when(client).connect();

        MeterSessionHandler handler = new MeterSessionHandler(socket, config, publisher, sessionFactory);
        handler.run();

        ArgumentCaptor<MeterSessionEvent> cap = ArgumentCaptor.forClass(MeterSessionEvent.class);
        verify(publisher).publishEvent(cap.capture());
        assertThat(cap.getValue().meterIp()).isEqualTo("10.20.30.40");
    }
}
