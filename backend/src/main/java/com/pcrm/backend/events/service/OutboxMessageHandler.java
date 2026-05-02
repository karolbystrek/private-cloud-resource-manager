package com.pcrm.backend.events.service;

import com.pcrm.backend.events.domain.OutboxMessage;

public interface OutboxMessageHandler {

    String topic();

    void handle(OutboxMessage message);
}
