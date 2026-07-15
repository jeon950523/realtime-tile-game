package com.realtimetilegame.websocket.presentation;

import com.realtimetilegame.websocket.presentation.dto.RealtimeHealthMessage;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class SystemHealthMessageController {

    @MessageMapping("/system.health.ping")
    @SendTo("/topic/system.health")
    public RealtimeHealthMessage ping() {
        return RealtimeHealthMessage.up();
    }
}
