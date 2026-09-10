package com.hotel.transaction.service;

import com.hotel.transaction.dto.AuditEventRequest;
import com.hotel.transaction.dto.AuditEventType;
import com.hotel.transaction.dto.TransactionsResponse;
import com.hotel.transaction.kafka.AuditEventProducer;
import com.hotel.transaction.mainframe.MainframeException;
import com.hotel.transaction.mainframe.TransactionHistoryRecord;
import com.hotel.transaction.mainframe.TransactionHistoryRequest;
import com.hotel.transaction.mainframe.TransactionHistoryResponse;
import com.hotel.transaction.mainframe.TransactionMainframeClient;
import com.hotel.transaction.mapper.TransactionMapper;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);

    @Mock
    private TransactionMainframeClient mainframeClient;

    @Spy
    private TransactionMapper transactionMapper = new TransactionMapper();

    @Mock
    private AuditEventProducer auditEventProducer;

    @InjectMocks
    private TransactionService transactionService;

    private static TransactionHistoryResponse sampleCopybook() {
        return new TransactionHistoryResponse("00", "10004567    ", List.of(
                new TransactionHistoryRecord("T1001     ", DATE, "ACH CREDIT PAYROLL  ", new BigDecimal("2500.00")),
                new TransactionHistoryRecord("T1002     ", DATE, "DEBIT CARD PURCHASE ", new BigDecimal("-42.19"))));
    }

    @Test
    void shouldReturnMappedTransactionsAndPublishAudit() {

        // Arrange
        when(mainframeClient.inquire(any())).thenReturn(sampleCopybook());
        when(mainframeClient.backendName()).thenReturn("STUB_MQ_TXNHIST");

        // Act
        TransactionsResponse response = transactionService.getTransactions("10004567", "alice");

        // Assert
        assertThat(response.accountId()).isEqualTo("10004567");
        assertThat(response.backend()).isEqualTo("STUB_MQ_TXNHIST");
        assertThat(response.transactions()).hasSize(2);
        assertThat(response.transactions().get(0).id()).isEqualTo("T1001");
        assertThat(response.transactions().get(0).date()).isEqualTo(DATE);
        assertThat(response.transactions().get(0).description()).isEqualTo("ACH CREDIT PAYROLL");
        assertThat(response.transactions().get(0).amount()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(response.transactions().get(1).amount()).isEqualByComparingTo(new BigDecimal("-42.19"));

        ArgumentCaptor<TransactionHistoryRequest> reqCaptor = ArgumentCaptor.forClass(TransactionHistoryRequest.class);
        verify(mainframeClient).inquire(reqCaptor.capture());
        assertThat(reqCaptor.getValue().reqType()).isEqualTo("TXNHIST");
        assertThat(reqCaptor.getValue().accountId()).isEqualTo("10004567");

        ArgumentCaptor<AuditEventRequest> auditCaptor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventProducer).send(auditCaptor.capture());
        AuditEventRequest audit = auditCaptor.getValue();
        assertThat(audit.getEventType()).isEqualTo(AuditEventType.TRANSACTIONS_INQUIRED);
        assertThat(audit.getServiceName()).isEqualTo("transaction-service");
        assertThat(audit.getActor()).isEqualTo("alice");
        assertThat(audit.getEntityType()).isEqualTo("TRANSACTIONS");
        assertThat(audit.getEntityId()).isEqualTo(10004567L);
        assertThat(audit.getPayload()).containsEntry("transactionCount", 2);
    }

    @Test
    void shouldRejectAccountIdThatIsNotEightDigits() {

        // Act / Assert
        assertThatThrownBy(() -> transactionService.getTransactions("ABC", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> transactionService.getTransactions("123456789", "alice"))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(mainframeClient, auditEventProducer);
    }

    @Test
    void shouldReturnNotFoundWhenCopybookReturnCodeIs23() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenReturn(new TransactionHistoryResponse("23", "", List.of()));

        // Act / Assert
        assertThatThrownBy(() -> transactionService.getTransactions("99999999", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(auditEventProducer);
    }

    @Test
    void shouldMapMainframeTimeoutToGatewayTimeout() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenThrow(new MainframeException("MF_TIMEOUT", "Mainframe timeout"));

        // Act / Assert
        assertThatThrownBy(() -> transactionService.getTransactions("10004567", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void shouldMapUnknownMainframeFaultToBadGateway() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenThrow(new MainframeException("MQ_CONNECT", "queue manager unavailable"));

        // Act / Assert
        assertThatThrownBy(() -> transactionService.getTransactions("10004567", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void shouldNotFailRequestWhenAuditPublishFails() {

        // Arrange
        when(mainframeClient.inquire(any())).thenReturn(sampleCopybook());
        when(mainframeClient.backendName()).thenReturn("STUB_MQ_TXNHIST");
        doThrow(new RuntimeException("kafka down")).when(auditEventProducer).send(any());

        // Act
        TransactionsResponse response = transactionService.getTransactions("10004567", "alice");

        // Assert
        assertThat(response.accountId()).isEqualTo("10004567");
        assertThat(response.transactions()).hasSize(2);
    }
}
