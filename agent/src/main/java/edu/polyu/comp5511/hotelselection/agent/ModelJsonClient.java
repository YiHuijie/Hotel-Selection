package edu.polyu.comp5511.hotelselection.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

final class ModelJsonClient {
    private static final Logger log = LoggerFactory.getLogger(ModelJsonClient.class);
    private final ChatModel model;
    private final ObjectMapper json;

    ModelJsonClient(ChatModel model, ObjectMapper json) {
        this.model = model;
        this.json = json;
    }

    JsonNode call(String system, Object data, ModelCallCounter counter) {
        String input;
        try {
            input = json.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化模型输入。", exception);
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            String prompt = attempt == 0 ? system : system
                    + "\n上次输出不是有效的单个 JSON 对象。请重新生成，只输出符合要求的 JSON 对象。";
            try {
                counter.increment();
                ChatResponse response = model.call(new Prompt(List.of(
                        new SystemMessage(prompt), new UserMessage(input))));
                if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                        || response.getResult().getOutput().getText() == null
                        || response.getResult().getOutput().hasToolCalls()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型未返回有效 JSON。");
                }
                JsonNode result = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(response.getResult().getOutput().getText());
                if (result == null || !result.isObject()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型未返回 JSON 对象。");
                }
                return result;
            } catch (JsonProcessingException exception) {
                if (attempt == 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型返回的 JSON 无效。", exception);
                }
                log.warn("模型返回的 JSON 无效，正在重试一次。");
            } catch (NonTransientAiException | TransientAiException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "模型服务暂时不可用，请稍后重试。", exception);
            }
        }
        throw new IllegalStateException("模型 JSON 重试未返回结果。");
    }
}
