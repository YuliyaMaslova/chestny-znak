package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final URI AS_URU = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create");

    private final Lock lock;
    private final AtomicInteger requestCount;
    private final int requestLimit;
    private final Duration timeInterval;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.lock = new ReentrantLock();
        this.requestCount = new AtomicInteger(0);
        this.requestLimit = requestLimit;
        this.timeInterval = Duration.ofSeconds(timeUnit.toSeconds(1));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void createDocument(Document document, String signature) {
        boolean acquired = false;
        try {
            acquired = lock.tryLock();

            if (acquired) {
                if (requestCount.get() >= requestLimit) {
                    lock.lockInterruptibly();
                }
            }

            sendCreateDocumentRequest(document, signature);

            requestCount.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (acquired) {
                scheduleResetRequestCount();
                lock.unlock();
            }
        }
    }

    private void sendCreateDocumentRequest(Document document, String signature) {
        try {
            String requestBody = objectMapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(AS_URU)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + signature)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Document created successfully");
            } else {
                System.err.println("Error creating document. Error code: " + response.statusCode());
            }
        } catch (JsonProcessingException e) {
            System.err.println("Error when serializing document to JSON: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("An error has occurred: " + e.getMessage());
        }

    }

    private void scheduleResetRequestCount() {
        // Планирование сброса счетчика через заданный интервал времени
        Thread resetThread = new Thread(() -> {
            try {
                Thread.sleep(timeInterval.toMillis());
                requestCount.set(0);
                lock.unlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        resetThread.start();
    }

    private record Document(
            Description description,
            String docId,
            String docStatus,
            String docType,
            int number,
            boolean importRequest,
            String ownerInn,
            String participantInn,
            String producerInn,
            LocalDate productionDate,
            String productionType,
            List<Product> products,
            LocalDate regDate,
            String regNumber
    ) {
    }

    private record Description(
            String participantInn
    ) {
    }

    private record Product(
            String certificateDocument,
            LocalDate certificateDocumentDate,
            String certificateDocumentNumber,
            String ownerInn,
            String producerInn,
            LocalDate productionDate,
            String tnvedCode,
            String uitCode,
            String uituCode
    ) {
    }
}



