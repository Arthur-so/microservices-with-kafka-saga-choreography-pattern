package br.com.microservices.choreography.orderservice.core.service;

import br.com.microservices.choreography.orderservice.core.document.History;
import br.com.microservices.choreography.orderservice.core.document.Order;
import br.com.microservices.choreography.orderservice.core.dto.EventFilters;
import br.com.microservices.choreography.orderservice.config.exception.ValidationException;
import br.com.microservices.choreography.orderservice.core.document.Event;
import br.com.microservices.choreography.orderservice.core.enums.ESagaStatus;
import br.com.microservices.choreography.orderservice.core.repository.EventRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class EventService {

    private static final String CURRENT_SERVICE = "ORDER_SERVICE";
    private final EventRepository eventRepository;

    public Event findByFilters(EventFilters filters) {
        validateEmptyFilters(filters);

        if (!filters.getOrderId().isEmpty()) {
            return findByOrderId(filters.getOrderId());
        }
        else {
            return findByTransactionId(filters.getTransactionId());
        }
    }

    private void validateEmptyFilters(EventFilters filters) {
        if (filters.getOrderId().isEmpty() && filters.getTransactionId().isEmpty()) {
            throw new ValidationException("OrderID or TransactionID must be informed.");
        }
    }

    private Event findByTransactionId(String transactionIdFilter) {
        return eventRepository
                .findTop1ByTransactionIdOrderByCreatedAtDesc(transactionIdFilter)
                .orElseThrow(
                        () -> new ValidationException("Event not found by transactionId."));
    }

    private Event findByOrderId(String orderIdFilter) {
        return eventRepository
                .findTop1ByOrderIdOrderByCreatedAtDesc(orderIdFilter)
                .orElseThrow(
                        () -> new ValidationException("Event not found by orderId."));
    }


    public List<Event> findAll() {
        return eventRepository.findAllByOrderByCreatedAtDesc();
    }

    public void notifyEnding(Event event) {
        event.setSource(CURRENT_SERVICE);
        event.setOrderId(event.getPayload().getId());
        event.setCreatedAt(LocalDateTime.now());
        setEndingHistory(event);
        save(event);
        log.info("Order {} with saga notified! TransactionId: {}", event.getOrderId(), event.getTransactionId());
    }

    private void setEndingHistory(Event event) {
        if (ESagaStatus.SUCCESS.equals(event.getStatus())) {
            log.info("SAGA FINISHED SUCCESSFULLY FOR EVENT {}!", event.getId());
            addHistory(event, "Saga finished successfully!");
        } else {
            log.info("SAGA FINISHED WITH ERRORS FOR EVENT {}!", event.getId());
            addHistory(event, "Saga finished with errors!");
        }
    }

    public Event createEvent(Order order) {
        var event = Event
                .builder()
                .source(CURRENT_SERVICE)
                .status(ESagaStatus.SUCCESS)
                .orderId(order.getId())
                .transactionId(order.getTransactionId())
                .payload(order)
                .createdAt(LocalDateTime.now())
                .build();
        addHistory(event, "Saga started!");
        return save(event);
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

    public Event save(Event event) {
        return eventRepository.save(event);
    }
}
