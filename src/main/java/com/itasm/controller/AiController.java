package com.itasm.controller;

import com.itasm.service.AiAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAgentService aiAgentService;

    public record ChatRequest(String prompt) {}
    public record ChatResponse(String response) {}

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String response = aiAgentService.processUserRequest(request.prompt());
        return new ChatResponse(response);
    }
}
