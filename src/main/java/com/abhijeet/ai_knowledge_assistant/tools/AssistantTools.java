package com.abhijeet.ai_knowledge_assistant.tools;

import com.abhijeet.ai_knowledge_assistant.domain.Memory;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.repository.ConversationRepository;
import com.abhijeet.ai_knowledge_assistant.repository.MemoryRepository;
import org.springframework.ai.tool.annotation.Tool;

import java.time.Instant;
import java.util.List;

/**
 * Tools the model can call during a request.
 *
 * The instance is created per request with the signed-in user bound to it, so a
 * tool can never read another user's data even if the model asks for it.
 */
public class AssistantTools {

    private final User user;
    private final MemoryRepository memories;
    private final ConversationRepository conversations;

    public AssistantTools(User user,
                          MemoryRepository memories,
                          ConversationRepository conversations) {
        this.user = user;
        this.memories = memories;
        this.conversations = conversations;
    }

    @Tool(description = "Return the current date and time in ISO-8601 format")
    public String currentTime() {
        return Instant.now().toString();
    }

    @Tool(description = "List the facts the user has saved in their memory, newest first")
    public List<String> listMemories() {
        return memories.findByUserOrderByCreatedAtDesc(user).stream()
                .map(Memory::getContent)
                .toList();
    }

    @Tool(description = "Count how many conversations the user has started")
    public int conversationCount() {
        return conversations.findByUserOrderByUpdatedAtDesc(user).size();
    }
}
