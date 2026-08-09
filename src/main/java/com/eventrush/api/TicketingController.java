package com.eventrush.api;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.TicketOrder;
import com.eventrush.service.AsyncGrabService;
import com.eventrush.service.TicketingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class TicketingController {

    private final TicketingService ticketingService;
    private final AsyncGrabService asyncGrabService;

    TicketingController(TicketingService ticketingService, AsyncGrabService asyncGrabService) {
        this.ticketingService = ticketingService;
        this.asyncGrabService = asyncGrabService;
    }

    @PostMapping("/orders/grab")
    TicketOrder grabTicket(@Valid @RequestBody GrabTicketRequest request) {
        return ticketingService.grabTicket(request.userId(), request.sessionId(), request.ticketCategoryId());
    }

    @PostMapping("/orders/grab-async")
    AsyncGrabService.GrabResult grabTicketAsync(@Valid @RequestBody GrabTicketRequest request) {
        return asyncGrabService.submitGrab(request.userId(), request.sessionId(), request.ticketCategoryId());
    }

    @GetMapping("/orders/grab-requests/{requestId}")
    AsyncGrabService.GrabResult getGrabResult(@PathVariable String requestId) {
        return asyncGrabService.getResult(requestId);
    }

    @PostMapping("/orders/{orderId}/pay")
    ElectronicTicket payOrder(@PathVariable Long orderId) {
        return ticketingService.payOrder(orderId);
    }

    @GetMapping("/orders/{orderId}")
    TicketOrder getOrder(@PathVariable Long orderId) {
        return ticketingService.getOrder(orderId);
    }

    @GetMapping("/tickets/{ticketCode}")
    ElectronicTicket getTicket(@PathVariable String ticketCode) {
        return ticketingService.getTicket(ticketCode);
    }

    @PostMapping("/tickets/verify")
    ElectronicTicket verifyTicket(@Valid @RequestBody VerifyTicketRequest request) {
        return ticketingService.verifyTicket(request.ticketCode(), request.verifierId());
    }

    record GrabTicketRequest(
            @NotNull(message = "userId is required") Long userId,
            @NotNull(message = "sessionId is required") Long sessionId,
            @NotNull(message = "ticketCategoryId is required") Long ticketCategoryId
    ) {
    }

    record VerifyTicketRequest(
            @NotBlank(message = "ticketCode is required") String ticketCode,
            @NotNull(message = "verifierId is required") Long verifierId
    ) {
    }
}
