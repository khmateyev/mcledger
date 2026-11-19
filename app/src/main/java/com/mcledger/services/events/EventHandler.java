package com.mcledger.services.events;

import com.mcledger.dtos.EventRequest;
import com.mcledger.dtos.EventResult;

public interface EventHandler {
    EventResult handle(EventRequest request);
}
