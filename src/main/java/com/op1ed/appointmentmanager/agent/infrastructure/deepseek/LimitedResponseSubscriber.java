package com.op1ed.appointmentmanager.agent.infrastructure.deepseek;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

// 限制实际接收的字节数，而不只信任 Content-Length 或模型的 max_tokens。
final class LimitedResponseSubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private final int maximumBytes;
    private Flow.Subscription subscription;
    private long received;

    LimitedResponseSubscriber(int maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
    @Override public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        delegate.onSubscribe(subscription);
    }
    @Override public void onNext(List<ByteBuffer> buffers) {
        for (ByteBuffer buffer : buffers) {
            received += buffer.remaining();
        }
        if (received > maximumBytes) {
            subscription.cancel();
            delegate.onError(new IOException("Model response size limit exceeded"));
        } else {
            delegate.onNext(buffers);
        }
    }
    @Override public void onError(Throwable throwable) { delegate.onError(throwable); }
    @Override public void onComplete() { delegate.onComplete(); }
}
