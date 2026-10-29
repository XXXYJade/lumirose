import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.*;
import io.github.xxxyjade.lumirose.tool.WeatherTool;

import java.util.ArrayList;
import java.util.List;

public class Test001 {
    public static void main(String[] args) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl("https://api.soflash.top")
                .apiKey("sk-e21d66417e5752f6e43ee18cbb9cb50a128042228ec5d68e3a3310e5af504f99")
                .build();

        List<ChatCompletionMessageParam> messages = new ArrayList<>();
        ChatCompletionUserMessageParam userMessage = ChatCompletionUserMessageParam.builder()
                .content("衢州今天天气怎么样？")
                .build();
        ChatCompletionCreateParams.Builder paramBuilder = ChatCompletionCreateParams.builder()
                .model("gpt-6.1-sol")
                .addTool(WeatherTool.class);

        messages.add(ChatCompletionMessageParam.ofUser(userMessage));
        paramBuilder.addMessage(userMessage);
        ChatCompletionMessageParam chatCompletionMessageParam = ChatCompletionMessageParam.ofUser(userMessage);
        if (chatCompletionMessageParam.isUser()) {
            System.out.println("666666");
            System.out.println(chatCompletionMessageParam);
        }

        System.out.println("===============Messages Turn 1===============");
        System.out.println(messages);


        ChatCompletion chatCompletion = client.chat().completions().create(paramBuilder.build());
        ChatCompletionMessage assistantMessage = chatCompletion.choices().get(0).message();

        System.out.println("===============AssistantMessages content===============");
        System.out.println(assistantMessage.content());
        System.out.println("===============AssistantMessages toolCalls===============");
        System.out.println(assistantMessage.toolCalls());

        messages.add(ChatCompletionMessageParam.ofAssistant(assistantMessage.toParam()));
        paramBuilder.addMessage(assistantMessage);

        System.out.println("===============Messages Turn 2===============");
        System.out.println(messages);

        List<ChatCompletionMessageToolCall> toolCalls = assistantMessage.toolCalls().orElse(List.of());
        for (ChatCompletionMessageToolCall toolCall : toolCalls) {
            ChatCompletionMessageFunctionToolCall.Function function = toolCall.asFunction().function();
            Class<WeatherTool> functionParametersType = WeatherTool.class;
            Object res = function.arguments(functionParametersType).execute();

            ChatCompletionToolMessageParam toolMessage = ChatCompletionToolMessageParam.builder()
                    .toolCallId(toolCall.asFunction().id())
                    .contentAsJson(res)
                    .build();

            messages.add(ChatCompletionMessageParam.ofTool(toolMessage));
            paramBuilder.addMessage(toolMessage);
        }

        System.out.println("===============Messages Turn 3===============");
        System.out.println(messages);



        chatCompletion = client.chat().completions().create(paramBuilder.build());
        assistantMessage = chatCompletion.choices().get(0).message();


        messages.add(ChatCompletionMessageParam.ofAssistant(assistantMessage.toParam()));
        paramBuilder.addMessage(assistantMessage);

        System.out.println("===============Messages Turn 4===============");
        System.out.println(messages);
    }
}
