package io.github.xxxyjade.lumirose.tool;

public interface Tool {

    default String key(){
        return this.getClass().getSimpleName();
    }

    Object execute();
}
