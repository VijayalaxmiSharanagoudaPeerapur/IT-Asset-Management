package com.itasm.service;

import com.itasm.dto.employee.EmployeeResponse;
import com.itasm.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AiAgentService {

    private final ChatClient chatClient;
    private final CurrentUser currentUser;
    private final EmployeeService employeeService;
    private final AssetService assetService;
    private final TicketService ticketService;

    public AiAgentService(ChatClient.Builder chatClientBuilder,
                          CurrentUser currentUser,
                          EmployeeService employeeService,
                          AssetService assetService,
                          TicketService ticketService) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
        this.currentUser = currentUser;
        this.employeeService = employeeService;
        this.assetService = assetService;
        this.ticketService = ticketService;
    }

    public String processUserRequest(String userPrompt) {
        String username = currentUser.requireUser().getUsername();
        log.info("Processing AI request for user: {}", username);

        StringBuilder contextBuilder = new StringBuilder();
        try {
            EmployeeResponse emp = employeeService.getMe();
            contextBuilder.append("\nActive User Context:\n");
            contextBuilder.append("- Name: ").append(emp.firstName()).append(" ").append(emp.lastName()).append(" (Code: ").append(emp.employeeCode()).append(")\n");
            contextBuilder.append("- Department: ").append(emp.department()).append(", Designation: ").append(emp.designation()).append("\n");
            contextBuilder.append("- Role: ").append(currentUser.requireUser().getRole()).append("\n");
        } catch (Exception ignored) {}

        try {
            var assets = assetService.myAssets();
            if (assets != null && !assets.isEmpty()) {
                contextBuilder.append("- Assigned Assets: ");
                for (var a : assets) {
                    contextBuilder.append(a.brand()).append(" ").append(a.model())
                            .append(" [Tag: ").append(a.assetTag()).append(", Status: ").append(a.status()).append("]; ");
                }
                contextBuilder.append("\n");
            }
        } catch (Exception ignored) {}

        try {
            var tickets = ticketService.myTickets(Pageable.ofSize(5));
            if (tickets != null && tickets.hasContent()) {
                contextBuilder.append("- Recent Tickets: ");
                for (var t : tickets.getContent()) {
                    contextBuilder.append(t.ticketNumber()).append(" [").append(t.priority()).append(" - ").append(t.status())
                            .append("]: ").append(t.title()).append("; ");
                }
                contextBuilder.append("\n");
            }
        } catch (Exception ignored) {}

        String systemPrompt = """
            You are a helpful and intelligent IT Asset Management Assistant for ITASM.
            Your role is to help employees and administrators manage IT assets, software licenses, and support tickets.
            Answer questions directly using the provided Active User Context when relevant.
            If the user asks about their assets, tickets, or profile, summarize them clearly and pleasantly.
            Keep your responses concise, professional, and natural.
            """ + contextBuilder.toString();

        try {
            return runPrompt(systemPrompt, userPrompt, username, null);
        } catch (Exception e) {
            log.error("Error during AI processing: ", e);
            if (e.getMessage() != null && (e.getMessage().contains("503") || e.getMessage().contains("UNAVAILABLE"))) {
                try {
                    log.info("Primary model unavailable. Retrying with fallback model gemini-flash-lite-latest...");
                    return runPrompt(systemPrompt, userPrompt, username, "gemini-flash-lite-latest");
                } catch (Exception ex2) {
                    log.error("Fallback AI processing failed: ", ex2);
                    return "The AI assistant is temporarily busy due to high traffic on Google's API. Please try asking again in a few moments.";
                }
            }
            return "I encountered an error while processing your request: " + e.getMessage();
        }
    }

    private String runPrompt(String systemPrompt, String userPrompt, String username, String overrideModel) {
        var promptSpec = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .advisors(a -> a.param("chat_memory_conversation_id", username));

        if (overrideModel != null) {
            promptSpec.options(org.springframework.ai.openai.OpenAiChatOptions.builder().withModel(overrideModel).build());
        }
        return promptSpec.call().content();
    }
}
