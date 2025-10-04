package com.dream.codegenerate.core.builder;

import lombok.Data;

@Data
public class BuildResult {
    private boolean success;
    private String message;
    private String errorOutput;

    public BuildResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public BuildResult(boolean success, String message, String errorOutput) {
        this.success = success;
        this.message = message;
        this.errorOutput = errorOutput;
    }
}
