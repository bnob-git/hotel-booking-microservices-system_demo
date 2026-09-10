package com.hotel.account.service;

import com.hotel.account.dto.AccountResponse;
import com.hotel.account.dto.AuditEventRequest;
import com.hotel.account.dto.AuditEventType;
import com.hotel.account.kafka.AuditEventProducer;
import com.hotel.account.mainframe.AccountInquiryRequest;
import com.hotel.account.mainframe.AccountInquiryResponse;
import com.hotel.account.mainframe.AccountMainframeClient;
import com.hotel.account.mainframe.MainframeException;
import com.hotel.account.mapper.AccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountMainframeClient mainframeClient;

    @Spy
    private AccountMapper accountMapper = new AccountMapper();

    @Mock
    private AuditEventProducer auditEventProducer;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldReturnMappedAccountAndPublishAudit() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenReturn(new AccountInquiryResponse("00", "10004567    ", "C009991   ", "CHECKING  ", "OPEN      "));
        when(mainframeClient.backendName()).thenReturn("STUB_CICS_ACCTINQ");

        // Act
        AccountResponse response = accountService.getAccount("10004567", "alice");

        // Assert
        assertThat(response.accountId()).isEqualTo("10004567");
        assertThat(response.customerId()).isEqualTo("C009991");
        assertThat(response.type()).isEqualTo("CHECKING");
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.backend()).isEqualTo("STUB_CICS_ACCTINQ");

        ArgumentCaptor<AccountInquiryRequest> reqCaptor = ArgumentCaptor.forClass(AccountInquiryRequest.class);
        verify(mainframeClient).inquire(reqCaptor.capture());
        assertThat(reqCaptor.getValue().reqType()).isEqualTo("ACCTINQ");
        assertThat(reqCaptor.getValue().accountId()).isEqualTo("10004567");

        ArgumentCaptor<AuditEventRequest> auditCaptor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventProducer).send(auditCaptor.capture());
        AuditEventRequest audit = auditCaptor.getValue();
        assertThat(audit.getEventType()).isEqualTo(AuditEventType.ACCOUNT_INQUIRED);
        assertThat(audit.getServiceName()).isEqualTo("account-service");
        assertThat(audit.getActor()).isEqualTo("alice");
        assertThat(audit.getEntityType()).isEqualTo("ACCOUNT");
        assertThat(audit.getEntityId()).isEqualTo(10004567L);
    }

    @Test
    void shouldRejectAccountIdThatIsNotEightDigits() {

        assertThatThrownBy(() -> accountService.getAccount("ABC", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> accountService.getAccount("123456789", "alice"))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(mainframeClient, auditEventProducer);
    }

    @Test
    void shouldReturnNotFoundWhenCopybookReturnCodeIs23() {

        when(mainframeClient.inquire(any()))
                .thenReturn(new AccountInquiryResponse("23", "", "", "", ""));

        assertThatThrownBy(() -> accountService.getAccount("99999999", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(auditEventProducer);
    }

    @Test
    void shouldMapMainframeTimeoutToGatewayTimeout() {

        when(mainframeClient.inquire(any()))
                .thenThrow(new MainframeException("MF_TIMEOUT", "Mainframe timeout"));

        assertThatThrownBy(() -> accountService.getAccount("10004567", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void shouldNotFailRequestWhenAuditPublishFails() {

        when(mainframeClient.inquire(any()))
                .thenReturn(new AccountInquiryResponse("00", "10004567", "C009991", "CHECKING", "OPEN"));
        when(mainframeClient.backendName()).thenReturn("STUB_CICS_ACCTINQ");
        doThrow(new RuntimeException("kafka down")).when(auditEventProducer).send(any());

        AccountResponse response = accountService.getAccount("10004567", "alice");

        assertThat(response.accountId()).isEqualTo("10004567");
    }
}
