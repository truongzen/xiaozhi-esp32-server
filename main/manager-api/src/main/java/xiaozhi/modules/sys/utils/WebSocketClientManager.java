package xiaozhi.modules.sys.utils;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.util.StopWatch;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.utils.DateUtils;

/**
 * WebSocketClientResource: supports the try-with-resources pattern
 */
@Slf4j
public class WebSocketClientManager implements Closeable {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Global callback thread pool
    private static final ExecutorService CALLBACK_EXECUTOR = Executors
            .newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
                private final AtomicInteger cnt = new AtomicInteger();

                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ws-callback-" + cnt.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

    private volatile WebSocketSession session;
    private final BlockingQueue<String> textMessageQueue;
    private final BlockingQueue<byte[]> binaryMessageQueue;
    private final CompletableFuture<Void> errorFuture;
    private final long maxSessionDuration;
    private final TimeUnit maxSessionDurationUnit;

    private volatile Consumer<String> onText;
    private volatile Consumer<byte[]> onBinary;
    private volatile Consumer<Throwable> onError;

    private final int queueCapacity;

    // Private constructor, only called by the Builder
    private WebSocketClientManager(Builder b) {
        this.maxSessionDuration = b.maxSessionDuration;
        this.maxSessionDurationUnit = b.maxSessionDurationUnit;
        this.queueCapacity = b.queueCapacity;
        this.textMessageQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.binaryMessageQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.errorFuture = new CompletableFuture<>();
    }

    public static WebSocketClientManager build(Builder b)
            throws InterruptedException, ExecutionException, TimeoutException, IOException {
        WebSocketClientManager ws = new WebSocketClientManager(b);
        StandardWebSocketClient client = new StandardWebSocketClient();
        CompletableFuture<WebSocketSession> future = client.execute(ws.new InternalHandler(b.uri), b.headers,
                URI.create(b.uri));
        WebSocketSession sess = future.get(b.connectTimeout, b.connectUnit);
        if (sess == null || !sess.isOpen()) {
            throw new IOException("Handshake failed or session not open");
        }
        // Set buffer sizes
        sess.setTextMessageSizeLimit(b.bufferSize);
        sess.setBinaryMessageSizeLimit(b.bufferSize);
        ws.session = sess;
        return ws;
    }


    /**
     * Send text
     */
    public void sendText(String text) throws IOException {
        session.sendMessage(new TextMessage(text));
    }

    public void sendBinary(byte[] data) throws IOException {
        session.sendMessage(new BinaryMessage(data));
    }

    public void sendJson(Object payload) throws IOException {
        String json = OBJECT_MAPPER.writeValueAsString(payload);
        session.sendMessage(new TextMessage(json));
    }

    private <T> List<T> listenerCustom(
            BlockingQueue<T> queue,
            Predicate<T> predicate)
            throws InterruptedException, TimeoutException, ExecutionException {
        List<T> collected = new ArrayList<>();
        long deadline = System.currentTimeMillis() + maxSessionDurationUnit.toMillis(maxSessionDuration);

        while (true) {
            if (errorFuture.isDone()) {
                errorFuture.get();
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new TimeoutException("Timed out waiting for batch messages");
            }

            T msg = queue.poll(remaining, TimeUnit.MILLISECONDS);
            if (msg == null) {
                throw new TimeoutException("Timed out waiting for batch messages");
            }

            collected.add(msg);
            if (predicate.test(msg)) {
                break;
            }
        }
        close();
        return collected;
    }

    private <T> List<T> listenerCustomWithoutClose(
            BlockingQueue<T> queue,
            Predicate<T> predicate)
            throws InterruptedException, TimeoutException, ExecutionException {
        List<T> collected = new ArrayList<>();
        long deadline = System.currentTimeMillis() + maxSessionDurationUnit.toMillis(maxSessionDuration);

        while (true) {
            if (errorFuture.isDone()) {
                errorFuture.get();
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new TimeoutException("Timed out waiting for batch messages");
            }

            T msg = queue.poll(remaining, TimeUnit.MILLISECONDS);
            if (msg == null) {
                throw new TimeoutException("Timed out waiting for batch messages");
            }

            collected.add(msg);
            if (predicate.test(msg)) {
                break;
            }
        }
        // Do not call close(), keep the connection open
        return collected;
    }

    /**
     * Synchronously receive multiple messages until the predicate is true, or throw on timeout;
     * 
     * @return list of all messages received while listening
     */
    public List<String> listener(Predicate<String> predicate)
            throws InterruptedException, TimeoutException, ExecutionException {
        return listenerCustom(textMessageQueue, predicate);
    }

    /**
     * Synchronously receive multiple messages until the predicate is true, or throw on timeout;
     * Does not close the connection automatically; suitable for sending multiple messages on the same connection
     * 
     * @return list of all messages received while listening
     */
    public List<String> listenerWithoutClose(Predicate<String> predicate)
            throws InterruptedException, TimeoutException, ExecutionException {
        return listenerCustomWithoutClose(textMessageQueue, predicate);
    }

    public List<byte[]> listenerBinary(Predicate<byte[]> predicate)
            throws InterruptedException, TimeoutException, ExecutionException {
        return listenerCustom(binaryMessageQueue, predicate);
    }

    /**
     * Register the text callback
     */
    public WebSocketClientManager onText(Consumer<String> c) {
        this.onText = c;
        return this;
    }

    /**
     * Register the binary callback
     */
    public WebSocketClientManager onBinary(Consumer<byte[]> c) {
        this.onBinary = c;
        return this;
    }

    /**
     * Register the error callback
     */
    public WebSocketClientManager onError(Consumer<Throwable> c) {
        this.onError = c;
        return this;
    }

    /**
     * Close the session; called automatically by try-with-resources / finally
     */
    @Override
    public void close() {
        try {
            if (session != null && session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        } catch (IOException ignored) {
        }
        textMessageQueue.clear();
        binaryMessageQueue.clear();
        errorFuture.completeExceptionally(new IOException("WebSocket closed"));
    }

    private class InternalHandler extends AbstractWebSocketHandler {
        private final String targetUri;
        private final StopWatch stopWatch;

        InternalHandler(String targetUri) {
            this.targetUri = targetUri;
            this.stopWatch = new StopWatch();
        }

        /**
         * Callback when the connection is established
         */
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            // Save the session
            WebSocketClientManager.this.session = session;
            this.stopWatch.start();
            log.info("WS connected, target URI: {}, connect time: {}", targetUri,
                    DateUtils.getDateTimeNow(DateUtils.DATE_TIME_MILLIS_PATTERN));
        }

        /**
         * Handle a text message
         */
        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            String payload = message.getPayload();
            // Enqueue
            textMessageQueue.offer(payload);
            // Invoke the user-registered onText callback
            if (onText != null) {
                CALLBACK_EXECUTOR.submit(() -> onText.accept(payload));
            }
        }

        /**
         * Handle a binary message
         */
        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
            ByteBuffer buf = message.getPayload();
            byte[] data = new byte[buf.remaining()];
            buf.get(data);
            // Enqueue
            binaryMessageQueue.offer(data);
            // Invoke the user-registered onBinary callback
            if (onBinary != null) {
                CALLBACK_EXECUTOR.submit(() -> onBinary.accept(data));
            }
        }

        /**
         * Callback on transport error
         */
        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            super.handleTransportError(session, exception);
            // Keep the original logic: complete errorFuture, invoke onError, close the session, notify connection failure asynchronously
            errorFuture.completeExceptionally(exception);
            if (onError != null) {
                CALLBACK_EXECUTOR.submit(() -> onError.accept(exception));
            }
            session.close(CloseStatus.SERVER_ERROR);
        }

        /**
         * Callback when the connection is closed
         */
        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            super.afterConnectionClosed(session, status);
            if (stopWatch.isRunning()) {
                stopWatch.stop();
            }
            log.info("WS connection closed, target URI: {}, close time: {}, total duration: {}s, close reason: {}",
                    targetUri, DateUtils.getDateTimeNow(DateUtils.DATE_TIME_MILLIS_PATTERN),
                    DateUtils.millsToSecond(stopWatch.getTotalTimeMillis()),status);
        }

    }

    public static class Builder {
        private String uri; // Target WS URI
        private long connectTimeout = 3; // Connection wait time
        private TimeUnit connectUnit = TimeUnit.SECONDS; // Connection wait time unit
        private long maxSessionDuration = 5; // Maximum session duration, default 5 seconds
        private TimeUnit maxSessionDurationUnit = TimeUnit.SECONDS; // Maximum session duration unit
        private int queueCapacity = 100; // Message queue capacity
        private int bufferSize = 8 * 1024; // Default 8 KB
        private WebSocketHttpHeaders headers; // Request headers

        /**
         * Target WS URI
         */
        public Builder uri(String uri) {
            this.uri = Objects.requireNonNull(uri);
            return this;
        }

        public Builder headers(WebSocketHttpHeaders h) {
            this.headers = h;
            return this;
        }

        public Builder connectTimeout(long t, TimeUnit u) {
            this.connectTimeout = t;
            this.connectUnit = u;
            return this;
        }

        public Builder maxSessionDuration(long t, TimeUnit u) {
            this.maxSessionDuration = t;
            this.maxSessionDurationUnit = u;
            return this;
        }

        public Builder queueCapacity(int c) {
            this.queueCapacity = c;
            return this;
        }
        public Builder bufferSize(int c) {
            this.bufferSize = c;
            return this;
        }

        public WebSocketClientManager build()
                throws InterruptedException, ExecutionException, TimeoutException, IOException {
            return WebSocketClientManager.build(this);
        }

    }
}
