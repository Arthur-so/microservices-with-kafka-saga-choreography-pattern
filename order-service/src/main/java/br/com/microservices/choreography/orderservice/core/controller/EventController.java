package br.com.microservices.choreography.orderservice.core.controller;

import br.com.microservices.choreography.orderservice.core.dto.EventFilters;
import br.com.microservices.choreography.orderservice.core.service.EventService;
import br.com.microservices.choreography.orderservice.core.document.Event;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/event")
public class EventController {

    private final EventService eventService;

    @GetMapping
    public Event findByFilters(EventFilters eventFilters) {
        return eventService.findByFilters(eventFilters);
    }

    @GetMapping("all")
    public List<Event> findAll() {
        return eventService.findAll();
    }
}
