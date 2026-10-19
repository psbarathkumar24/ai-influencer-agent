package com.aiinfluencer.service;

import com.aiinfluencer.domain.entity.VideoProject;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiScriptService {

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Value("${app.openai.model}")
    private String model;

    @Value("${app.openai.max-tokens}")
    private Integer maxTokens;

    /**
     * Generate video script using OpenAI based on user prompt
     */
    public String generateScript(VideoProject project) {
        try {
            log.info("Generating script for project ID: {} with prompt: {}",
                     project.getId(), project.getPrompt());

            OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(60));

            String systemPrompt = buildSystemPrompt(project);
            String userPrompt = buildUserPrompt(project);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(List.of(
                            new ChatMessage("system", systemPrompt),
                            new ChatMessage("user", userPrompt)
                    ))
                    .maxTokens(maxTokens)
                    .temperature(0.7)
                    .build();

            String script = service.createChatCompletion(request)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            log.info("Successfully generated script for project ID: {}. Script length: {} characters",
                     project.getId(), script.length());

            return script;

        } catch (Exception e) {
            log.error("Error generating script for project ID: {}", project.getId(), e);
            throw new RuntimeException("Failed to generate script: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(VideoProject project) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert video script writer for social media content. ");
        prompt.append("Create engaging, concise, and compelling video scripts that capture attention quickly. ");

        if (project.getVideoStyle() != null) {
            prompt.append("The video style is: ").append(project.getVideoStyle()).append(". ");
        }

        if (project.getTargetDurationSeconds() != null) {
            int minutes = project.getTargetDurationSeconds() / 60;
            int seconds = project.getTargetDurationSeconds() % 60;
            prompt.append("Target duration is approximately ")
                  .append(minutes).append(" minute").append(minutes != 1 ? "s" : "")
                  .append(" and ").append(seconds).append(" seconds. ");
        }

        prompt.append("Structure the script with:\n");
        prompt.append("- Hook: Grab attention in the first 3 seconds\n");
        prompt.append("- Introduction: Set the context\n");
        prompt.append("- Main Content: Deliver key points clearly\n");
        prompt.append("- Call to Action: Encourage engagement\n");
        prompt.append("Write in a natural, conversational tone suitable for voice narration.");

        return prompt.toString();
    }

    private String buildUserPrompt(VideoProject project) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a video script for the following topic:\n\n");
        prompt.append(project.getPrompt());

        if (project.getDescription() != null && !project.getDescription().isEmpty()) {
            prompt.append("\n\nAdditional context:\n").append(project.getDescription());
        }

        return prompt.toString();
    }

    /**
     * Refine or regenerate script based on user feedback
     */
    public String refineScript(String originalScript, String feedback) {
        try {
            log.info("Refining script based on feedback");

            OpenAiService service = new OpenAiService(apiKey, Duration.ofSeconds(60));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(List.of(
                            new ChatMessage("system",
                                "You are a video script editor. Revise the script based on the feedback provided."),
                            new ChatMessage("user",
                                "Original Script:\n" + originalScript +
                                "\n\nFeedback:\n" + feedback +
                                "\n\nPlease revise the script accordingly.")
                    ))
                    .maxTokens(maxTokens)
                    .temperature(0.7)
                    .build();

            String refinedScript = service.createChatCompletion(request)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            log.info("Successfully refined script");
            return refinedScript;

        } catch (Exception e) {
            log.error("Error refining script", e);
            throw new RuntimeException("Failed to refine script: " + e.getMessage(), e);
        }
    }
}
