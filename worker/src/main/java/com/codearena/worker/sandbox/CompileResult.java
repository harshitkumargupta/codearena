package com.codearena.worker.sandbox;

public record CompileResult(Program program, SandboxResult failure) {

    static CompileResult success(Program program) {
        return new CompileResult(program, null);
    }

    static CompileResult failed(SandboxResult failure) {
        return new CompileResult(null, failure);
    }

    public boolean succeeded() {
        return program != null;
    }
}
