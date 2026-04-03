package com.tripmind.controller;

import com.tripmind.dto.ChatRequest;
import com.tripmind.dto.ChatResponse;
import com.tripmind.model.ChatHistory;
import com.tripmind.model.ChatMessageLog;
import com.tripmind.repository.ChatHistoryRepository;
import com.tripmind.repository.ChatMessageLogRepository;
import com.tripmind.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ChatHistoryRepository chatHistoryRepo;
    private final ChatMessageLogRepository chatMessageLogRepository;

    public ChatController(ChatService chatService, ChatHistoryRepository chatHistoryRepo, ChatMessageLogRepository chatMessageLogRepository) {
        this.chatService = chatService;
        this.chatHistoryRepo = chatHistoryRepo;
        this.chatMessageLogRepository = chatMessageLogRepository;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        log.info("Incoming validate state flow -> origin={}, budget={}, duration={}, travelers={}, type={}", 
                 request.getOriginCity(), request.getBudget(), request.getDuration(), request.getTravelersCount(), request.getTravelType());
                 
        Object attrUserId = httpRequest.getAttribute("userId");
        if (attrUserId instanceof Long && request.getUserId() == null) {
            request.setUserId((Long) attrUserId);
        }
        ChatResponse response = chatService.processChat(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chat/history/{userId}")
    public ResponseEntity<List<ChatHistory>> getHistory(@PathVariable Long userId, HttpServletRequest httpRequest) {
        Object attrUserId = httpRequest.getAttribute("userId");
        if (!(attrUserId instanceof Long) || !attrUserId.equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        List<ChatHistory> history = chatHistoryRepo.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/chat/message")
    public ResponseEntity<?> logMessage(@RequestBody ChatMessageLog messageLog, HttpServletRequest httpRequest) {
        if (messageLog.getMessage() == null || messageLog.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }
        if (messageLog.getSender() == null || messageLog.getSender().isBlank()) {
            messageLog.setSender("user");
        }
        Object attrUserId = httpRequest.getAttribute("userId");
        if (messageLog.getUserId() == null && attrUserId instanceof Long) {
            messageLog.setUserId((Long) attrUserId);
        }
        return ResponseEntity.ok(chatMessageLogRepository.save(messageLog));
    }

    @GetMapping("/admin/chat-messages")
    public ResponseEntity<List<ChatMessageLog>> getAllChatMessages() {
        return ResponseEntity.ok(chatMessageLogRepository.findAllByOrderByCreatedAtDesc());
    }
}
