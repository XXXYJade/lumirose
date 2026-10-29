import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;
import io.github.xxxyjade.lumirose.repository.InMemoryMessageRepository;
import io.github.xxxyjade.lumirose.tool.TimeTool;
import io.github.xxxyjade.lumirose.tool.ToolRegistry;
import io.github.xxxyjade.lumirose.tool.WeatherTool;

public class Test004 {
    public static void main(String[] args) throws Exception {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey("sk-ff93e101a0a34224b4b9b75b12656c87")
                .build();

        ToolRegistry toolRegistry = ToolRegistry.builder()
                .addTool(TimeTool.class)
                .addTool(WeatherTool.class)
                .build();

        LumiRoseAgent lumiRoseAgent = LumiRoseAgent.builder()
                .client(client)
                .model("deepseek-flash")
                .toolRegistry(toolRegistry)
                .repository(new InMemoryMessageRepository())
                .build();
        System.out.println(lumiRoseAgent.run("衢州今天天气怎么样？"));

    }
}
