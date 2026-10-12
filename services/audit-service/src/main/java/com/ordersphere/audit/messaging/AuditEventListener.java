package com.ordersphere.audit.messaging;

import com.ordersphere.audit.chain.AuditChainService;
import com.ordersphere.audit.domain.RecordedEvent;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Records every message on the shared event exchange. Takes the raw {@link Message} rather than a
 * typed payload so nothing has to be deserialized into an event class (see RecordedEventMapper). A
 * failure to store is rethrown: the listener retries and then dead-letters the message, it is never
 * silently skipped.
 */
@Component
public class AuditEventListener {

  private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

  private final RecordedEventMapper mapper;
  private final AuditChainService chain;
  private final Clock clock;

  public AuditEventListener(RecordedEventMapper mapper, AuditChainService chain, Clock clock) {
    this.mapper = mapper;
    this.chain = chain;
    this.clock = clock;
  }

  @RabbitListener(queues = RabbitConfig.EVENTS_QUEUE)
  public void onEvent(Message message) {
    RecordedEvent event = mapper.map(message, clock.instant());
    chain
        .append(event)
        .ifPresentOrElse(
            entry ->
                log.debug("Recorded {} as audit entry {}", entry.getEventType(), entry.getSeq()),
            () -> log.debug("Event {} already recorded, skipping", event.eventId()));
  }
}
