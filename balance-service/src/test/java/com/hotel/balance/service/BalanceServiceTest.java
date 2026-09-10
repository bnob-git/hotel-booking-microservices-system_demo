package com.hotel.balance.service;

import com.hotel.balance.dto.AuditEventRequest;
import com.hotel.balance.dto.AuditEventType;
import com.hotel.balance.dto.BalanceResponse;
import com.hotel.balance.kafka.AuditEventProducer;
import com.hotel.balance.mainframe.BalanceInquiryRequest;
import com.hotel.balance.mainframe.BalanceInquiryResponse;
import com.hotel.balance.mainframe.BalanceMainframeClient;
import com.hotel.balance.mainframe.MainframeException;
import com.hotel.balance.mapper.BalanceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final BigDecimal AVAILABLE = new BigDecimal("12450.75");
    private static final BigDecimal LEDGER = new BigDecimal("12600.75");

    @Mock
    private BalanceMainframeClient mainframeClient;

    @Spy
    private BalanceMapper balanceMapper = new BalanceMapper();

    @Mock
    private AuditEventProducer auditEventProducer;

    @InjectMocks
    private BalanceService balanceService;

    @Test
    void shouldReturnMappedBalanceAndPublishAudit() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenReturn(new BalanceInquiryResponse("00", "10004567    ", AVAILABLE, LEDGER, "USD"));
        when(mainframeClient.backendName()).thenReturn("STUB_CICS_BALINQ");
        when(auditEventProducer.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        // Act
        BalanceResponse response = balanceService.getBalance("10004567", "alice");

        // Assert
        assertThat(response.accountId()).isEqualTo("10004567");
        assertThat(response.available()).isEqualByComparingTo(AVAILABLE);
        assertThat(response.ledger()).isEqualByComparingTo(LEDGER);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.backend()).isEqualTo("STUB_CICS_BALINQ");

        ArgumentCaptor<BalanceInquiryRequest> reqCaptor = ArgumentCaptor.forClass(BalanceInquiryRequest.class);
        verify(mainframeClient).inquire(reqCaptor.capture());
        assertThat(reqCaptor.getValue().reqType()).isEqualTo("BALINQ");
        assertThat(reqCaptor.getValue().accountId()).isEqualTo("10004567");

        ArgumentCaptor<AuditEventRequest> auditCaptor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventProducer).send(auditCaptor.capture());
        AuditEventRequest audit = auditCaptor.getValue();
        assertThat(audit.getEventType()).isEqualTo(AuditEventType.BALANCE_INQUIRED);
        assertThat(audit.getServiceName()).isEqualTo("balance-service");
        assertThat(audit.getActor()).isEqualTo("alice");
        assertThat(audit.getEntityType()).isEqualTo("BALANCE");
        assertThat(audit.getEntityId()).isEqualTo(10004567L);
        assertThat(audit.getPayload()).containsEntry("currency", "USD");
    }

    @Test
    void shouldRejectAccountIdThatIsNotEightDigits() {

        // Act / Assert
        assertThatThrownBy(() -> balanceService.getBalance("ABC", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> balanceService.getBalance("123456789", "alice"))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(mainframeClient, auditEventProducer);
    }

    @Test
    void shouldReturnNotFoundWhenCopybookReturnCodeIs23() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenReturn(new BalanceInquiryResponse("23", "", null, null, ""));

        // Act / Assert
        assertThatThrownBy(() -> balanceService.getBalance("99999999", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(auditEventProducer);
    }

    @Test
    void shouldReturnBadGatewayWhenCopybookReturnCodeIsUnexpected() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenReturn(new BalanceInquiryResponse("12", "", null, null, ""));

        // Act / Assert
        assertThatThrownBy(() -> balanceService.getBalance("10004567", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));

        verifyNoInteractions(auditEventProducer);
    }

    @Test
    void shouldMapMainframeTimeoutToGatewayTimeout() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenThrow(new MainframeException("MF_TIMEOUT", "Mainframe timeout"));

        // Act / Assert
        assertThatThrownBy(() -> balanceService.getBalance("10004567", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void shouldNotFailRequestWhenAuditPublishFails() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenReturn(new BalanceInquiryResponse("00", "10004567", AVAILABLE, LEDGER, "USD"));
        when(mainframeClient.backendName()).thenReturn("STUB_CICS_BALINQ");
        doThrow(new RuntimeException("kafka down")).when(auditEventProducer).send(any());

        // Act
        BalanceResponse response = balanceService.getBalance("10004567", "alice");

        // Assert
        assertThat(response.accountId()).isEqualTo("10004567");
    }

    @Test
    void shouldNotFailRequestWhenAuditDeliveryFailsAsynchronously() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenReturn(new BalanceInquiryResponse("00", "10004567", AVAILABLE, LEDGER, "USD"));
        when(mainframeClient.backendName()).thenReturn("STUB_CICS_BALINQ");
        when(auditEventProducer.send(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker timeout")));

        // Act
        BalanceResponse response = balanceService.getBalance("10004567", "alice");

        // Assert
        assertThat(response.accountId()).isEqualTo("10004567");
    }
}
