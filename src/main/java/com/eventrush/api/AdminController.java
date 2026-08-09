package com.eventrush.api;

import com.eventrush.domain.ElectronicTicket;
import com.eventrush.domain.TicketOrder;
import com.eventrush.service.TicketingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
class AdminController {

    private final TicketingService ticketingService;

    AdminController(TicketingService ticketingService) {
        this.ticketingService = ticketingService;
    }

    @GetMapping("/users/{userId}/orders")
    List<TicketOrder> listOrdersByUser(@PathVariable Long userId) {
        return ticketingService.listOrdersByUser(userId);
    }

    @GetMapping("/orders/{orderId}/ticket")
    ElectronicTicket getTicketByOrderId(@PathVariable Long orderId) {
        return ticketingService.getTicketByOrderId(orderId);
    }

    @GetMapping("/tickets/{ticketCode}")
    ElectronicTicket getTicket(@PathVariable String ticketCode) {
        return ticketingService.getTicket(ticketCode);
    }
}
