package com.eventrush.domain;

public record TicketPassenger(
        Long id,
        Long orderId,
        int sequence,
        String name,
        PassengerDocumentType documentType,
        String documentLast4
) {
}
