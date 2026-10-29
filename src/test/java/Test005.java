import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.tool.TimeTool;
import io.github.xxxyjade.lumirose.tool.ToolRegistry;
import io.github.xxxyjade.lumirose.tool.WeatherTool;

public class Test005 {
    public static void main(String[] args) throws Exception {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl("https://api.soflash.top")
                .apiKey("sk-e21d66417e5752f6e43ee18cbb9cb50a128042228ec5d68e3a3310e5af504f99")
                .build();

        ToolRegistry toolRegistry = ToolRegistry.builder()
                .addTool(WeatherTool.class)
                .addTool(TimeTool.class)
                .build();

        LumiRoseAgent lumiRoseAgent = LumiRoseAgent.builder()
                .client(client)
                .model("gpt-6.1-sol")
                .systemMessage("你是一个天气助手，回答简洁。")
                .toolRegistry(toolRegistry)
                .repository(new InMemoryMessageRepository())
                .maxTurns(10)
                .build();

        long conversationId = 1001L;

        System.out.println("===== 开始流式输出 =====");
        lumiRoseAgent.stream(conversationId, "衢州今天天气怎么样？")
                .doOnSubscribe(s -> System.out.println("[subscribe] 流已建立"))
                .doOnNext(chunk -> {                 // 每个文本增量到达
                    System.out.print(chunk);
                    System.out.flush();               // 实时输出必须 flush
                })
                .doOnComplete(() -> System.out.println("\n===== 流正常结束 ====="))
                .doOnError(e -> System.err.println("\n===== 流出错: " + e.getMessage() + " ====="))
                .doFinally(signal -> System.out.println("[finally] 终止信号: " + signal))
                .blockLast();                         // main 里阻塞等待；服务端代码不要 block
    }
}
