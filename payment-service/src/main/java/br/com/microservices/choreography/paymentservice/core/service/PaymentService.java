package br.com.microservices.choreography.paymentservice.core.service;

import br.com.microservices.choreography.paymentservice.config.exception.ValidationException;
import br.com.microservices.choreography.paymentservice.core.enums.EPaymentStatus;
import br.com.microservices.choreography.paymentservice.core.enums.ESagaStatus;
import br.com.microservices.choreography.paymentservice.core.model.Payment;
import br.com.microservices.choreography.paymentservice.core.repository.PaymentRepository;
import br.com.microservices.choreography.paymentservice.core.dto.Event;
import br.com.microservices.choreography.paymentservice.core.dto.History;
import br.com.microservices.choreography.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.choreography.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentService {
    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";
    private static final Double REDUCE_SUM_VALUE = 0.0;
    private final static double MIN_AMOUNT_VALUE = 0.1;

    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final PaymentRepository paymentRepository;

    public void realizeAndPersistPayment(Event event) {
        try{
            checkForExistingPayment(event);
            createPaymentRecordAndUpdateEvent(event);
            var payment = findPaymentByOrderIdAndTransactionId(event);
            validateAmount(payment.getTotalAmount());
            changePaymentToSuccess(payment);
            handleSuccess(event);
        } catch (Exception ex) {
            log.error("Error trying to validate product: ", ex);
            handlePaymentFailure(event, ex.getMessage());
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void createPaymentRecordAndUpdateEvent(Event event) {
        var totalItems = calculateTotalItems(event);
        var totalAmount = calculateTotalAmount(event);

        var payment = Payment
                .builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .totalItems(totalItems)
                .totalAmount(totalAmount)
                .build();
        save(payment);
        setEventAmountItems(event, payment);
    }

    private int calculateTotalItems(Event event) {
        return event.getPayload().getProducts()
                .stream()
                .map(orderProduct -> orderProduct.getQuantity())
                .reduce(REDUCE_SUM_VALUE.intValue(), Integer::sum);
    }

    private double calculateTotalAmount(Event event) {
        return event.getPayload().getProducts()
                .stream()
                .map(orderProduct -> orderProduct.getQuantity() * orderProduct.getProduct().getUnitValue())
                .reduce(REDUCE_SUM_VALUE, Double::sum);
    }


    private void checkForExistingPayment(Event event) {
        if (paymentRepository.existsByOrderIdAndTransactionId(
                event.getPayload().getId(),
                event.getTransactionId()
        )) {
            throw new ValidationException("There's another transactionId for this payment.");
        }
    }

    private Payment findPaymentByOrderIdAndTransactionId(Event event) {
        return paymentRepository.findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .orElseThrow(() -> new ValidationException("Payment not found by orderId and transactionId"));
    }

    private void setEventAmountItems(Event event, Payment payment) {
        event.getPayload().setTotalItems(payment.getTotalItems());
        event.getPayload().setTotalAmount(payment.getTotalAmount());
    }

    private void validateAmount(double totalAmount) {
        if (totalAmount < MIN_AMOUNT_VALUE) {
            throw new ValidationException("The minimum amount value is 0.1");
        }
    }

    private void changePaymentToSuccess(Payment payment) {
        payment.setStatus(EPaymentStatus.SUCCESS);
        save(payment);
    }

    private void handleSuccess(Event event) {
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Payment realized successfully!");
    }

    private void addHistory(Event event, String message) {
        var history = History
                .builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();

        event.addHistory(history);
    }

    private void handlePaymentFailure(Event event, String message) {
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to realize payment: ".concat(message));
    }

    public void realizeRefund(Event event) {
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);
        try{
            changePaymentStatusToRefund(event);
            addHistory(event, "Rollback executed on payment.");
        } catch(Exception ex) {
            addHistory(event, "Rollback not executed for payment: ".concat(ex.getMessage()));
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void changePaymentStatusToRefund(Event event) {
        var payment = findPaymentByOrderIdAndTransactionId(event);
        payment.setStatus(EPaymentStatus.REFUND);
        setEventAmountItems(event, payment);
        save(payment);
    }

    private void save(Payment payment) {
        paymentRepository.save(payment);
    }
}
