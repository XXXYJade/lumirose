package io.github.xxxyjade.lumirose.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.time.ZonedDateTime;

@JsonClassDescription("查询时间和时区")
public class TimeTool implements Tool{

    public String placeHoler;

    public Object execute() {
        return ZonedDateTime.now();
    }
}
