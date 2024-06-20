package br.com.microservices.choreography.orderservice.core.repository;

import br.com.microservices.choreography.orderservice.core.document.Event;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends MongoRepository<Event, String> {

    Optional<Event> findTop1ByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<Event> findTop1ByTransactionIdOrderByCreatedAtDesc(String transactionId);

    List<Event> findAllByOrderByCreatedAtDesc();
}
