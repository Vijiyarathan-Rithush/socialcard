package com.rvi.service;

import java.util.List;
import java.util.function.Consumer;

public interface IBatchService extends AutoCloseable
{
    List<String> readers() throws Exception;
    void start(String reader, String url, Consumer<BatchStatus> onStatus);
    void stop();
    void close();
}
