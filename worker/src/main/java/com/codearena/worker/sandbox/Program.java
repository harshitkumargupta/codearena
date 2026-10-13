package com.codearena.worker.sandbox;

import com.codearena.common.Language;
import java.util.Map;

public record Program(Language language, Map<String, byte[]> files) {

    public Program {
        files = Map.copyOf(files);
    }
}
