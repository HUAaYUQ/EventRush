package com.eventrush.domain;

import java.util.List;

public record TicketRefundResult(
        TicketOrder order,
        List<ElectronicTicket> tickets,
        int newlyRefundedQuantity,
        long newlyRefundedAmountCents
) {
}
