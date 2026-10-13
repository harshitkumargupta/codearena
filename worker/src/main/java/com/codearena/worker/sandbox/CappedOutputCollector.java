package com.codearena.worker.sandbox;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class CappedOutputCollector extends ResultCallback.Adapter<Frame> {

    private final int limitBytes;
    private final Runnable onLimitExceeded;
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private boolean limitExceeded;

    CappedOutputCollector(int limitBytes, Runnable onLimitExceeded) {
        this.limitBytes = limitBytes;
        this.onLimitExceeded = onLimitExceeded;
    }

    @Override
    public void onNext(Frame frame) {
        byte[] payload = frame.getPayload();
        boolean firstOverflow;
        synchronized (this) {
            ByteArrayOutputStream target = frame.getStreamType() == StreamType.STDERR ? stderr : stdout;
            int room = limitBytes - target.size();
            target.write(payload, 0, Math.clamp(payload.length, 0, Math.max(room, 0)));
            firstOverflow = payload.length > room && !limitExceeded;
            limitExceeded |= payload.length > room;
        }
        if (firstOverflow) {
            onLimitExceeded.run();
        }
    }

    synchronized boolean limitExceeded() {
        return limitExceeded;
    }

    synchronized String stdout() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    synchronized String stderr() {
        return stderr.toString(StandardCharsets.UTF_8);
    }
}
