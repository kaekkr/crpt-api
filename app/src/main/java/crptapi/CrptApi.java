import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import lombok.Data;

public class CrptApi {
    private final HttpClient httpClient;
    private final Gson gson;
    private final RateLimiter rateLimiter;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.rateLimiter = new RateLimiter(timeUnit, requestLimit);
    }

    public static void main(String[] args) throws InterruptedException {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);
        Document document = new Document();

        int numberOfThreads = 10;
        Thread[] threads = new Thread[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                try {
                    api.createDocument(document, "test");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("All requests completed");
    }

    public void createDocument(Document document, String signature) throws InterruptedException {
        rateLimiter.acquire();

        JsonObject jsonObject = gson.toJsonTree(document).getAsJsonObject();
        String requestBody = gson.toJson(jsonObject);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://jsonplaceholder.typicode.com/posts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Response: " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class RateLimiter {
        private final long intervalMillis;
        private final int maxRequests;
        private final AtomicInteger requestCount;
        private final Lock lock;

        public RateLimiter(TimeUnit timeUnit, int maxRequests) {
            this.intervalMillis = timeUnit.toMillis(1);
            this.maxRequests = maxRequests;
            this.requestCount = new AtomicInteger(0);
            this.lock = new ReentrantLock();
        }

        public void acquire() throws InterruptedException {
            long start = System.currentTimeMillis();
            lock.lock();
            try {
                while (requestCount.get() >= maxRequests) {
                    long elapsed = System.currentTimeMillis() - start;
                    long waitTime = intervalMillis - elapsed % intervalMillis;
                    if (waitTime > 0) {
                        System.out.println("Rate limit exceeded. Waiting for " + waitTime + " ms.");
                        Thread.sleep(waitTime);
                    }
                    requestCount.set(0);
                }
                requestCount.incrementAndGet();
            } finally {
                lock.unlock();
            }
        }
    }

    @Data
    public static class Document {
        private String description;
        private String docId;
        private String docStatus;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
    }
}
