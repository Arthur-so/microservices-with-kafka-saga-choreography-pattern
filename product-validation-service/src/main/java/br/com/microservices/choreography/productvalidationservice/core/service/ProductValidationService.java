package br.com.microservices.choreography.productvalidationservice.core.service;

import br.com.microservices.choreography.productvalidationservice.config.exception.ValidationException;
import br.com.microservices.choreography.productvalidationservice.core.dto.Event;
import br.com.microservices.choreography.productvalidationservice.core.dto.History;
import br.com.microservices.choreography.productvalidationservice.core.enums.ESagaStatus;
import br.com.microservices.choreography.productvalidationservice.core.model.Validation;
import br.com.microservices.choreography.productvalidationservice.core.repository.ProductRepository;
import br.com.microservices.choreography.productvalidationservice.core.repository.ValidationRepository;
import br.com.microservices.choreography.productvalidationservice.core.dto.OrderProducts;
import br.com.microservices.choreography.productvalidationservice.core.saga.SagaExecutionController;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.springframework.util.ObjectUtils.isEmpty;

@Slf4j
@Service
@AllArgsConstructor
public class ProductValidationService {

    private static final String CURRENT_SOURCE = "PRODUCT_VALIDATION_SERVICE";
    private final ProductRepository productRepository;
    private final ValidationRepository validationRepository;
    private final SagaExecutionController sagaExecutionController;

    public void handleEventAndPersistValidation(Event event) {
        try{
            checkForExistingValidation(event);
            createValidationRecord(event, true);
            handleSuccess(event);
        } catch (Exception ex) {
            log.error("Error trying to validate product: ", ex);
            handleValidationFailure(event, ex.getMessage());
        }
        sagaExecutionController.handleSaga(event);
    }

    private void createValidationRecord(Event event, boolean success) {
        var validation = Validation
                .builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .success(success)
                .build();

        validationRepository.save(validation);
    }

    private void handleSuccess(Event event) {
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Products are validated successfully!");
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

    private void checkForExistingValidation(Event event) {
        validateOrderProducts(event);
        if (validationRepository.existsByOrderIdAndTransactionId(
                event.getOrderId(), event.getTransactionId())) {
            throw new ValidationException("There's another transactionId for this validation.");
        }
        event.getPayload().getProducts().forEach(orderProduct -> {
            validateProduct(orderProduct);
        });
    }

    private void validateProduct(OrderProducts orderProduct) {
        if (isEmpty(orderProduct.getProduct()) || isEmpty(orderProduct.getProduct().getCode())) {
            throw new ValidationException("Product must be informed!");
        }

        if (!productRepository.existsByCode(orderProduct.getProduct().getCode())) {
            throw new ValidationException("Product does not exists in database!");
        }
    }

    private void validateOrderProducts(Event event) {
        if (isEmpty(event.getPayload()) || isEmpty(event.getPayload().getProducts())) {
            throw new ValidationException("Product list is empty!");
        }
        if (isEmpty(event.getId()) || isEmpty(event.getTransactionId())) {
            throw new ValidationException("OrderID and TransactionID must be informed!");
        }
    }

    private void handleValidationFailure(Event event, String message) {
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to validate products: ".concat(message));
    }

    public void rollBackEvent(Event event) {
        changeValidationToFail(event);
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Rollback executed on product validation!");
        sagaExecutionController.handleSaga(event);
    }

    private void changeValidationToFail(Event event) {
        validationRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .ifPresentOrElse(validation -> {
                    validation.setSuccess(false);
                    validationRepository.save(validation);
                },
                () -> createValidationRecord(event, false));
    }
}
