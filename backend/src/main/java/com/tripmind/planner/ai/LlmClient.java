package com.tripmind.planner.ai;

import java.util.Optional;

/** A language model that returns a JSON document for a prompt. */
public interface LlmClient {
    boolean enabled();

    /** Returns raw JSON text, or empty on any failure/timeout. Never throws. */
    Optional<String> json(String prompt);

    /** Short reason the most recent call failed or was degraded, or null. For diagnostics only. */
    default String lastError() { return null; }

    /** The model that produced the most recent successful reply, or null. */
    default String lastModel() { return null; }
}
