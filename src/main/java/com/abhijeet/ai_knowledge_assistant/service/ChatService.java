package com.abhijeet.ai_knowledge_assistant.service;

import com.abhijeet.ai_knowledge_assistant.domain.ChatMessage;
import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import com.abhijeet.ai_knowledge_assistant.domain.Memory;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.ChatMessageRepository;
import com.abhijeet.ai_knowledge_assistant.repository.MemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds the model request from three layers:
 *
 *   1. System prompt  - behaviour plus the user's saved memories
 *   2. Conversation   - the last N messages, so the thread has continuity
 *   3. New message    - already persisted by the caller before this runs
 *
 * Memories are explicit user-authored facts, not silently inferred ones:
 * predictable, free to maintain, and easy for the user to correct.
 */
@Service
public class ChatService {

    private static final int HISTORY_WINDOW = 12;

    private final ChatModel chatModel;
    private final ChatMessageRepository messages;
    private final MemoryRepository memories;

    public ChatService(ChatModel chatModel,
                       ChatMessageRepository messages,
                       MemoryRepository memories) {
        this.chatModel = chatModel;
        this.messages = messages;
        this.memories = memories;
    }

    public String reply(User user, Conversation conversation) {
        List<Message> prompt = new ArrayList<>();
        prompt.add(new SystemMessage(systemPrompt(user)));

        List<ChatMessage> history = new ArrayList<>(
                messages.findTop12ByConversationOrderByIdDesc(conversation));
        Collections.reverse(history);

        for (ChatMessage message : history) {
            if ("user".equals(message.getRole())) {
                prompt.add(new UserMessage(message.getContent()));
            } else {
                prompt.add(new AssistantMessage(message.getContent()));
            }
        }

        ChatResponse response = chatModel.call(new Prompt(prompt));
        String text = response.getResult().getOutput().getText();
        return text == null || text.isBlank() ? "(no response)" : text;
    }

    private String systemPrompt(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful, concise assistant inside a portfolio demo application. ")
                .append("Answer in the user's language. Use markdown only when it improves readability.\n");

        List<Memory> saved = memories.findByUserOrderByCreatedAtDesc(user);
        if (!saved.isEmpty()) {
            sb.append("\nFacts you remember about the user:\n");
            for (Memory memory : saved) {
                sb.append("- ").append(memory.getContent()).append('\n');
            }
            sb.append("Use these naturally when relevant. ")
                    .append("Never mention that you keep a list of memories.\n");
        }
        return sb.toString();
    }
}
