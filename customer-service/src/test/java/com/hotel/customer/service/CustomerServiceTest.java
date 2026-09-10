package com.hotel.customer.service;

import com.hotel.customer.dto.AuditEventRequest;
import com.hotel.customer.dto.AuditEventType;
import com.hotel.customer.dto.CustomerResponse;
import com.hotel.customer.kafka.AuditEventProducer;
import com.hotel.customer.mainframe.CustomerInquiryRequest;
import com.hotel.customer.mainframe.CustomerInquiryResponse;
import com.hotel.customer.mainframe.CustomerMainframeClient;
import com.hotel.customer.mainframe.MainframeException;
import com.hotel.customer.mapper.CustomerMapper;
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
class CustomerServiceTest {

    @Mock
    private CustomerMainframeClient mainframeClient;

    @Spy
    private CustomerMapper customerMapper = new CustomerMapper();

    @Mock
    private AuditEventProducer auditEventProducer;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void shouldReturnMappedCustomerAndPublishAudit() {

        // Arrange
        when(mainframeClient.inquire(any()))
                .thenReturn(new CustomerInquiryResponse("00", "C009991   ", "Jordan Example                ", "RETAIL    "));
        when(mainframeClient.backendName()).thenReturn("STUB_CICS_CUSTINQ");

        // Act
        CustomerResponse response = customerService.getCustomer("C009991", "alice");

        // Assert
        assertThat(response.customerId()).isEqualTo("C009991");
        assertThat(response.name()).isEqualTo("Jordan Example");
        assertThat(response.segment()).isEqualTo("RETAIL");
        assertThat(response.backend()).isEqualTo("STUB_CICS_CUSTINQ");

        ArgumentCaptor<CustomerInquiryRequest> reqCaptor = ArgumentCaptor.forClass(CustomerInquiryRequest.class);
        verify(mainframeClient).inquire(reqCaptor.capture());
        assertThat(reqCaptor.getValue().reqType()).isEqualTo("CUSTINQ");
        assertThat(reqCaptor.getValue().customerId()).isEqualTo("C009991");

        ArgumentCaptor<AuditEventRequest> auditCaptor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditEventProducer).send(auditCaptor.capture());
        AuditEventRequest audit = auditCaptor.getValue();
        assertThat(audit.getEventType()).isEqualTo(AuditEventType.CUSTOMER_INQUIRED);
        assertThat(audit.getServiceName()).isEqualTo("customer-service");
        assertThat(audit.getActor()).isEqualTo("alice");
        assertThat(audit.getEntityType()).isEqualTo("CUSTOMER");
        assertThat(audit.getEntityId()).isEqualTo(9991L);
    }

    @Test
    void shouldRejectCustomerIdThatDoesNotMatchTheBwPattern() {

        assertThatThrownBy(() -> customerService.getCustomer("ABC", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> customerService.getCustomer("C00999123", "alice"))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(mainframeClient, auditEventProducer);
    }

    @Test
    void shouldReturnNotFoundWhenCopybookReturnCodeIs23() {

        when(mainframeClient.inquire(any()))
                .thenReturn(new CustomerInquiryResponse("23", "", "", ""));

        assertThatThrownBy(() -> customerService.getCustomer("C999999", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(auditEventProducer);
    }

    @Test
    void shouldMapMainframeTimeoutToGatewayTimeout() {

        when(mainframeClient.inquire(any()))
                .thenThrow(new MainframeException("MF_TIMEOUT", "Mainframe timeout"));

        assertThatThrownBy(() -> customerService.getCustomer("C009991", "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void shouldNotFailRequestWhenAuditPublishFails() {

        when(mainframeClient.inquire(any()))
                .thenReturn(new CustomerInquiryResponse("00", "C009991", "Jordan Example", "RETAIL"));
        when(mainframeClient.backendName()).thenReturn("STUB_CICS_CUSTINQ");
        doThrow(new RuntimeException("kafka down")).when(auditEventProducer).send(any());

        CustomerResponse response = customerService.getCustomer("C009991", "alice");

        assertThat(response.customerId()).isEqualTo("C009991");
    }
}
