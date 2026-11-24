package com.abhijeet.ai_knowledge_assistant.service;

import com.abhijeet.ai_knowledge_assistant.domain.ChatMessage;
import com.abhijeet.ai_knowledge_assistant.domain.Conversation;
import com.abhijeet.ai_knowledge_assistant.domain.Memory;
import com.abhijeet.ai_knowledge_assistant.domain.User;
import com.abhijeet.ai_knowledge_assistant.rag.RagPrompt;
import com.abhijeet.ai_knowledge_assistant.rag.SourceHit;
import com.abhijeet.ai_knowledge_assistant.repository.ChatMessageRepository;
import com.abhijeet.ai_knowledge_assistant.repository.ConversationRepository;
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
import java.util.function.Consumer;

/**
 * Builds the model request from the system prompt, recent history and the new message.
 */
@Service
public class ChatService {

    private static final int HISTORY_WINDOW = 12;

    private final ChatModel chatModel;
    private final ChatMessageRepository messages;
    private final MemoryRepository memories;
    private final ConversationRepository conversations;
    private final ByokService byok;
    private final RagService rag;

    public ChatService(ChatModel chatModel,
                       ChatMessageRepository messages,
                       MemoryRepository memories,
                       ConversationRepository conversations,
                       ByokService byok,
                       RagService rag) {
        this.chatModel = chatModel;
        this.messages = messages;
        this.memories = memories;
        this.conversations = conversations;
        this.byok = byok;
        this.rag = rag;
    }

    /** A reply and the passages it was grounded on (empty when documents were not used). */
    public record Reply(String text, List<SourceHit> sources) {

        /** The text saved to history: the answer plus a Sources footnote when documents were used. */
        public String persisted() {
            return text + RagPrompt.footnote(text, sources);
        }
    }

    /**
     * Passages from this user's own documents (and the public sample) for the question, or none
     * when documents are not requested. The owner filter is applied by RagService.
     */
    public List<SourceHit> sourcesFor(User user, String question, boolean useDocuments) {
        if (!useDocuments || question == null || question.isBlank()) {
            return List.of();
        }
        return rag.search(question, user.getUsername(), RagService.TOP_K);
    }

    /** The user's own key when they have one, otherwise the shared key. */
    private ChatModel modelFor(User user) {
        ChatModel own = byok.modelFor(user);
        return own != null ? own : chatModel;
    }

    /** Non-streaming reply (kept for simple clients). */
    public String reply(User user, Conversation conversation) {
        return reply(user, conversation, List.of()).text();
    }

    public Reply reply(User user, Conversation conversation, List<SourceHit> sources) {
        ChatResponse response = modelFor(user).call(new Prompt(buildPrompt(user, conversation, sources)));
        String text = response.getResult().getOutput().getText();
        return new Reply(text == null || text.isBlank() ? "(no response)" : text, sources);
    }

    /**
     * Streaming reply. Each token fragment is passed to {@code onChunk} as it
     * arrives, and the full text is returned once the stream completes so the
     * caller can persist it.
     */
    public String streamReply(User user, Long conversationId, Consumer<String> onChunk) {
        return streamReply(user, conversationId, List.of(), onChunk).text();
    }

    public Reply streamReply(User user, Long conversationId, List<SourceHit> sources, Consumer<String> onChunk) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation not found"));

        StringBuilder full = new StringBuilder();

        for (ChatResponse response : modelFor(user).stream(new Prompt(buildPrompt(user, conversation, sources))).toIterable()) {
            if (response == null || response.getResult() == null) {
                continue;
            }
            String chunk = response.getResult().getOutput().getText();
            if (chunk != null && !chunk.isEmpty()) {
                full.append(chunk);
                onChunk.accept(chunk);
            }
        }

        return new Reply(full.toString(), sources);
    }

    private List<Message> buildPrompt(User user, Conversation conversation, List<SourceHit> sources) {
        List<Message> prompt = new ArrayList<>();
        String system = systemPrompt(user);
        if (!sources.isEmpty()) {
            system += RagPrompt.instructions(sources);
        }
        prompt.add(new SystemMessage(system));

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
        return prompt;
    }

    private String systemPrompt(User user) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful, concise assistant inside a portfolio demo application. ")
                .append("Answer in the user's language. Use markdown when it improves readability.\n");

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
