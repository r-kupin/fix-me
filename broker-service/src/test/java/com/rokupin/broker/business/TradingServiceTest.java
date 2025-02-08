package com.rokupin.broker.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rokupin.broker.events.BrokerEvent;
import com.rokupin.broker.model.StocksStateMessage;
import com.rokupin.broker.service.TradingServiceImpl;
import com.rokupin.model.fix.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TradingServiceTest {

    private final String routerId = "R0000";
    private final String brokerId = "B0000";
    private final String exchngId = "E0000";
    private final String instrument = "TEST0";
    private final StocksStateMessage emptyStateUpdate =
            new StocksStateMessage(new HashMap<>());
    private final String stockJson =
            """
                        {
                            "E00001": { "TEST1": 100, "TEST2": 200 },
                            "E00002": { "TEST3": 300 }
                        }
                    """;
    private final StocksStateMessage fullStateUpdate = new StocksStateMessage(
            Map.of("E00001", Map.of("TEST1", 100, "TEST2", 200),
                    "E00002", Map.of("TEST3", 300))
    );
    @Mock
    private ApplicationEventPublisher eventPublisher;
    private ObjectMapper objectMapper;
    private TradingServiceImpl tradingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tradingService = new TradingServiceImpl(eventPublisher, objectMapper);
    }

    // GET_STATE

    @Test
    void testGetState_onClientConnectedRouterNoState() throws Exception {
        // --- ARRANGEMENT
        // service needs to know router id to ask it for update
        tradingService.setAssignedId(brokerId);
        tradingService.setRouterId(routerId);

        // --- ACTION
        String actualState = tradingService.getState();

        // --- ASSERTION
        // should return empty state
        assertEquals(actualState, objectMapper.writeValueAsString(emptyStateUpdate));
        // should also request router for update
        BrokerEvent<FixStateUpdateRequest> expectedUpdateRequestEvent =
                new BrokerEvent<>(new FixStateUpdateRequest(brokerId, routerId));
        verify(eventPublisher, times(1)).publishEvent(expectedUpdateRequestEvent);
    }

    // HANDLE_MESSAGE_FROM_ROUTER

    @Test
    void testHandleMessageFromRouter_onEmptyInitialStateUpdate() throws Exception {
        // --- ARRANGEMENT
        // mock connection to the router without state
        FixIdAssignationStockState initialMessage =
                new FixIdAssignationStockState(routerId, brokerId, "{}");

        // --- ACTION
        tradingService.handleMessageFromRouter(initialMessage.asFix());

        // --- ASSERTION
        // IDs are extracted from message
        assertEquals(brokerId, tradingService.getAssignedId());
        assertEquals(routerId, tradingService.getRouterId());
        // Posted update to clients (empty state)
        BrokerEvent<StocksStateMessage> expectedClientUpdateEvent =
                new BrokerEvent<>(emptyStateUpdate);
        verify(eventPublisher, times(1)).publishEvent(expectedClientUpdateEvent);
    }

    @Test
    void testHandleMessageFromRouter_onInitialStateUpdate() throws Exception {
        // --- ARRANGEMENT
        // mock connection to the router with state
        FixIdAssignationStockState initialMessage =
                new FixIdAssignationStockState(routerId, brokerId, stockJson);

        // --- ACTION
        tradingService.handleMessageFromRouter(initialMessage.asFix());

        // --- ASSERTION
        // Posted update to clients (with state)
        BrokerEvent<StocksStateMessage> expectedEvent =
                new BrokerEvent<>(fullStateUpdate);
        verify(eventPublisher, times(1)).publishEvent(expectedEvent);
    }

    @Test
    void testHandleMessageFromRouter_onFollowingStateUpdate() throws Exception {
        // --- ARRANGEMENT
        // mock update from router
        FixStockStateReport stateReport =
                new FixStockStateReport(routerId, stockJson);

        // --- ACTION
        tradingService.handleMessageFromRouter(stateReport.asFix());

        // --- ASSERTION
        // Posted update to clients (with state)
        BrokerEvent<StocksStateMessage> expectedEvent =
                new BrokerEvent<>(fullStateUpdate);
        verify(eventPublisher, times(1)).publishEvent(expectedEvent);
    }

    @Test
    void testHandleMessageFromRouter_onTradingResponse() throws Exception {
        FixResponse response = new FixResponse(
                exchngId,
                brokerId,
                "1",
                instrument,
                FixRequest.SIDE_BUY,
                50,
                FixResponse.MSG_ORD_FILLED,
                0
        );

        // --- ACTION
        tradingService.handleMessageFromRouter(response.asFix());

        // --- ASSERTION
        // Verify the event is published
        BrokerEvent<FixResponse> expectedEvent = new BrokerEvent<>(response);
        verify(eventPublisher, times(1)).publishEvent(expectedEvent);
    }

    @Test
    void testHandleMessageFromClient_onValidRequest() throws Exception {
        // --- ARRANGEMENT
        ClientTradingRequest clientMsg = new ClientTradingRequest(
                exchngId,
                instrument,
                "buy",
                100
        );
        tradingService.setAssignedId(brokerId);
        tradingService.setRouterId(routerId);

        // --- ACTION
        String response = tradingService.handleMessageFromClient(clientMsg, "1");

        // --- ASSERTION
        // Should publish the request as a BrokerEvent
        FixRequest expectedRequest = new FixRequest(clientMsg);
        expectedRequest.setSender(brokerId);
        expectedRequest.setSenderSubId("1");
        verify(eventPublisher, times(1))
                .publishEvent(new BrokerEvent<>(expectedRequest));

        // Should return an empty string (indicating success)
        assertEquals("", response);
    }

    @Test
    void testHandleMessageFromClient_onInvalidRequest() {
        // --- ARRANGEMENT
        ClientTradingRequest invalidClientMsg = new ClientTradingRequest(
                null, // no way
                instrument,
                "buy",
                100
        );
        tradingService.setAssignedId(brokerId);
        tradingService.setRouterId(routerId);

        // --- ACTION
        String response = tradingService.handleMessageFromClient(invalidClientMsg, "1");

        // --- ASSERTION
        // No events should be published
        verify(eventPublisher, never()).publishEvent(any());

        // Should return an error message
        assertFalse(response.isEmpty());
    }

}
