package io.github.xxxyjade.lumirose.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("查询指定城市的天气预报")
public class WeatherTool implements Tool {

    @JsonPropertyDescription("城市名称，如 北京")
    public String city;

    public Object execute() {
        return "城市：" + city + "，天气：晴，25°C";
    }
}
