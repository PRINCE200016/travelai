package com.tripmind.planner.ai;

import java.util.Optional;

/** A language model that returns a JSON document for a prompt. */
public interface LlmClient {
    boolean enabled();

    /** Returns raw JSON text, or empty on any failure/timeout. Never throws. */
    Optional<String> json(String prompt);
}
