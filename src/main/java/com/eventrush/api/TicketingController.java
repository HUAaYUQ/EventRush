package com.eventrush.api;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.PassengerDocumentType;
import com.eventrush.domain.TicketOrder;
import com.eventrush.domain.TicketPassenger;
import com.eventrush.domain.TicketRefundResult;
import com.eventrush.service.AsyncGrabService;
import com.eventrush.service.TicketingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
                request.passengers().stream()
                        .map(passenger -> new TicketPassenger(
                                null,
                                null,
                                0,
                                passenger.name(),
                                passenger.documentType(),
                                passenger.documentLast4()
                        ))
                        .toList()
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
    List<ElectronicTicket> payOrder(@PathVariable Long orderId) {
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
    List<ElectronicTicket> payUserOrder(@PathVariable Long userId, @PathVariable Long orderId) {
        return ticketingService.payOrderForUser(userId, orderId);
    }

    @PostMapping("/users/{userId}/orders/{orderId}/refunds")
    TicketRefundResult refundUserOrder(
            @PathVariable Long userId,
            @PathVariable Long orderId,
            @Valid @RequestBody RefundTicketsRequest request
    ) {
        return ticketingService.refundTicketsForUser(userId, orderId, request.ticketCodes());
    }

    @GetMapping("/users/{userId}/orders/{orderId}/tickets")
    List<ElectronicTicket> getUserOrderTickets(@PathVariable Long userId, @PathVariable Long orderId) {
        return ticketingService.getTicketsByOrderIdForUser(userId, orderId);
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
            @NotEmpty(message = "passengers is required")
            @Size(max = 5, message = "passengers size must be between 1 and 5")
            List<@Valid PassengerRequest> passengers
    ) {
    }

    record PassengerRequest(
            @NotBlank(message = "passenger name is required")
            @Size(min = 2, max = 30, message = "passenger name length must be between 2 and 30") String name,
            @NotNull(message = "passenger documentType is required") PassengerDocumentType documentType,
            @NotBlank(message = "passenger documentLast4 is required")
            @Pattern(regexp = "[A-Za-z0-9]{4}", message = "passenger documentLast4 must contain 4 letters or digits")
            String documentLast4
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

    record RefundTicketsRequest(
            @NotEmpty(message = "ticketCodes is required")
            @Size(max = 5, message = "ticketCodes size must be between 1 and 5")
            List<@NotBlank(message = "ticketCode is required") String> ticketCodes
    ) {
    }
}
