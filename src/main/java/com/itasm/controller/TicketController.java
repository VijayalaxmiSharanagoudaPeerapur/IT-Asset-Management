package com.itasm.controller;

import com.itasm.domain.TicketPriority;
import com.itasm.domain.TicketStatus;
import com.itasm.dto.PageResponse;
import com.itasm.dto.ticket.*;
import com.itasm.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "4. Service / Ticket Management")
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    @Operation(summary = "Search tickets (employees see their own)")
    public PageResponse<TicketResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return PageResponse.of(ticketService.search(q, status, priority,
                PageRequest.of(page, size, Sort.by("id").descending())));
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return ticketService.get(id);
    }

    @GetMapping("/me")
    public PageResponse<TicketResponse> myTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(ticketService.myTickets(PageRequest.of(page, size, Sort.by("id").descending())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@Valid @RequestBody TicketRequest request) {
        return ticketService.create(request);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Track ticket status: OPEN → IN_PROGRESS → RESOLVED → CLOSED")
    public TicketResponse status(@PathVariable Long id, @Valid @RequestBody TicketStatusRequest request) {
        return ticketService.changeStatus(id, request);
    }

    @PostMapping("/{id}/assign")
    public TicketResponse assign(@PathVariable Long id, @Valid @RequestBody AssignTicketRequest request) {
        return ticketService.assign(id, request);
    }
}
