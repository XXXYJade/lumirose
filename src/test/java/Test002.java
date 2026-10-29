import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.github.xxxyjade.lumirose.agent.LumiRoseAgent;

public class Test002 {
    public static void main(String[] args) throws Exception {

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey("sk-ff93e101a0a34224b4b9b75b12656c87")
                .build();

        LumiRoseAgent lumiRoseAgent = LumiRoseAgent.builder()
                .client(client)
                .model("deepseek-flash")
                .build();

        System.out.println(lumiRoseAgent.run("今天是几号？"));
    }
}
