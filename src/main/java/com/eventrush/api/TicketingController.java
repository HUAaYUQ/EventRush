package com.eventrush.api;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.PassengerDocumentType;
import com.eventrush.domain.TicketOrder;
import com.eventrush.service.AsyncGrabService;
import com.eventrush.service.TicketingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
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
        return ticketingService.grabTicket(
                request.userId(),
                request.sessionId(),
                request.ticketCategoryId(),
                request.quantity(),
                request.passengerName(),
                request.passengerDocumentType(),
                request.passengerDocumentLast4()
        );
    }

    @PostMapping("/orders/grab-async")
    AsyncGrabService.GrabResult grabTicketAsync(@Valid @RequestBody AsyncGrabTicketRequest request) {
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

    @GetMapping("/users/{userId}/orders")
    List<TicketOrder> listUserOrders(@PathVariable Long userId) {
        return ticketingService.listOrdersByUser(userId);
    }

    @GetMapping("/users/{userId}/orders/{orderId}")
    TicketOrder getUserOrder(@PathVariable Long userId, @PathVariable Long orderId) {
        return ticketingService.getOrderForUser(userId, orderId);
    }

    @PostMapping("/users/{userId}/orders/{orderId}/pay")
    ElectronicTicket payUserOrder(@PathVariable Long userId, @PathVariable Long orderId) {
        return ticketingService.payOrderForUser(userId, orderId);
    }

    @GetMapping("/users/{userId}/orders/{orderId}/ticket")
    ElectronicTicket getUserOrderTicket(@PathVariable Long userId, @PathVariable Long orderId) {
        return ticketingService.getTicketByOrderIdForUser(userId, orderId);
    }

    @GetMapping("/tickets/{ticketCode}")
    ElectronicTicket getTicket(@PathVariable String ticketCode) {
        return ticketingService.getTicket(ticketCode);
    }

    @GetMapping("/users/{userId}/tickets/{ticketCode}")
    ElectronicTicket getUserTicket(@PathVariable Long userId, @PathVariable String ticketCode) {
        return ticketingService.getTicketForUser(userId, ticketCode);
    }

    @PostMapping("/tickets/verify")
    ElectronicTicket verifyTicket(@Valid @RequestBody VerifyTicketRequest request) {
        return ticketingService.verifyTicket(request.ticketCode(), request.verifierId());
    }

    record GrabTicketRequest(
            @NotNull(message = "userId is required") Long userId,
            @NotNull(message = "sessionId is required") Long sessionId,
            @NotNull(message = "ticketCategoryId is required") Long ticketCategoryId,
            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "quantity must be 1")
            @Max(value = 1, message = "quantity must be 1") Integer quantity,
            @NotBlank(message = "passengerName is required")
            @Size(min = 2, max = 30, message = "passengerName length must be between 2 and 30") String passengerName,
            @NotNull(message = "passengerDocumentType is required") PassengerDocumentType passengerDocumentType,
            @NotBlank(message = "passengerDocumentLast4 is required")
            @Pattern(regexp = "[A-Za-z0-9]{4}", message = "passengerDocumentLast4 must contain 4 letters or digits")
            String passengerDocumentLast4
    ) {
    }

    record AsyncGrabTicketRequest(
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
