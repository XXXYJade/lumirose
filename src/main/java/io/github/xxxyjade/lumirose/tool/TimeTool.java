package io.github.xxxyjade.lumirose.tool;

import io.github.xxxyjade.lumirose.tool.args.EmptyParameters;
import io.github.xxxyjade.lumirose.tool.args.ToolParameters;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class TimeTool implements Tool{

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "查询当前时间。";
    }

    @Override
    public ToolParameters parameters() {
        return new EmptyParameters();
    }

    @Override
    public Class<? extends ToolParameters> parametersClass() {
        return EmptyParameters.class;
    }

    @Override
    public String execute(ToolParameters parameters) throws IOException {
        ZoneId zone = ZoneId.systemDefault();
        return ZonedDateTime.now()
                .withZoneSameInstant(zone)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
