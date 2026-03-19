package com.aton.proj.oneGasMeter.server;

import com.aton.proj.oneGasMeter.dlms.DlmsMeterClient;

import java.time.Instant;

/**
 * Spring application event published when a meter establishes an inbound DLMS session.
 * <p>
 * Published <em>synchronously</em> by {@link MeterSessionHandler} while the DLMS
 * association with the meter is active.  Listeners may use the supplied
 * {@link DlmsMeterClient} to perform GET / SET / ACTION operations on the meter
 * during the callback.
 * </p>
 *
 * <h3>Important: client lifecycle</h3>
 * <p>
 * The {@link DlmsMeterClient} contained in this event is only valid during the
 * synchronous execution of the listener.  Retaining a reference to it after the
 * listener returns will result in operations on a disconnected client.
 * </p>
 *
 * @param meterIp     IPv4/IPv6 address of the connected meter
 * @param connectedAt timestamp when the meter socket was accepted
 * @param client      active, connected {@link DlmsMeterClient} — valid only during
 *                    the event listener callback
 */
public record MeterSessionEvent(
        String meterIp,
        Instant connectedAt,
        DlmsMeterClient client) {
}
