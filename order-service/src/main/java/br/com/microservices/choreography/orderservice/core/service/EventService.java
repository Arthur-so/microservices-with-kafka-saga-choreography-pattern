package br.com.microservices.choreography.orderservice.core.service;

import br.com.microservices.choreography.orderservice.core.dto.EventFilters;
import br.com.microservices.choreography.orderservice.config.exception.ValidationException;
import br.com.microservices.choreography.orderservice.core.document.Event;
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
        event.setOrderId(event.getPayload().getId());
        event.setCreatedAt(LocalDateTime.now());
        save(event);
        log.info("Order {} with saga notified! TransactionId: {}", event.getOrderId(), event.getTransactionId());
    }

    public Event save(Event event) {
        return eventRepository.save(event);
    }
}
