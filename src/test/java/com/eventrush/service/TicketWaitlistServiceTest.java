package com.eventrush.service;

import com.eventrush.domain.EventSession;
import com.eventrush.domain.PassengerDocumentType;
import com.eventrush.domain.TicketPassenger;
import com.eventrush.domain.TicketWaitlistRequest;
import com.eventrush.domain.WaitlistStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketWaitlistServiceTest {

    @Test
    void expiresWaitingRequestAfterSessionStarts() {
        EventCatalogService catalogService = mock(EventCatalogService.class);
        TicketingService ticketingService = mock(TicketingService.class);
        TicketWaitlistRepository repository = mock(TicketWaitlistRepository.class);
        TicketWaitlistService service = new TicketWaitlistService(catalogService, ticketingService, repository);
        LocalDateTime now = LocalDateTime.now();
        TicketWaitlistRequest waiting = request(WaitlistStatus.WAITING, now);
        TicketWaitlistRequest expired = request(WaitlistStatus.EXPIRED, now);

        when(repository.findById(1L)).thenReturn(Optional.of(waiting), Optional.of(expired));
        when(catalogService.getSession(101L)).thenReturn(new EventSession(
                101L, 1L, now.minusMinutes(1), now.plusHours(1), List.of()));
        when(catalogService.isWaitlistEnabled(101L)).thenReturn(true);
        when(repository.markExpiredIfWaiting(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(true);

        assertThat(service.getForUser(77L, 1L).status()).isEqualTo(WaitlistStatus.EXPIRED);
        verify(repository).markExpiredIfWaiting(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    private TicketWaitlistRequest request(WaitlistStatus status, LocalDateTime now) {
        return new TicketWaitlistRequest(
                1L,
                77L,
                1L,
                101L,
                1002L,
                39900,
                1,
                List.of(new TicketPassenger(
                        1L, 1L, 1, "候补用户", PassengerDocumentType.ID_CARD, "0077")),
                status,
                0,
                null,
                now.minusMinutes(10),
                now,
                null,
                null,
                status == WaitlistStatus.EXPIRED ? now : null,
                null
        );
    }
}
