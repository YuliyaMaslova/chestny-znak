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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private static final URI API_URI = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create");
    private final Semaphore requestSemaphore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Timer timer;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestSemaphore = new Semaphore(requestLimit);
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.timer = new Timer();
        long resetInterval = timeUnit.toMillis(1);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                requestSemaphore.release(requestLimit - requestSemaphore.availablePermits());
            }
        }, resetInterval, resetInterval);
    }

    public void createDocument(Document document, String signature) {
        if (!acquireRequestPermit()) {
            System.err.println("Превышен лимит запросов. Запрос не выполнен.");
        }
        else {
            sendCreateDocumentRequest(document, signature);
        }

    }

    private boolean acquireRequestPermit() {
        try {
            requestSemaphore.acquire();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void sendCreateDocumentRequest(Document document, String signature) {
        try {
            if (!acquireRequestPermit()) {
                System.err.println("Превышен лимит запросов. Запрос не выполнен.");
                return;
            }

            String requestBody = objectMapper.writeValueAsString(document);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(API_URI)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + signature)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Документ успешно создан");
            } else {
                System.err.println("Ошибка при создании документа. Код ошибки: " + response.statusCode());
            }
        } catch (JsonProcessingException e) {
            System.err.println("Ошибка при сериализации документа в JSON: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Произошла ошибка: " + e.getMessage());
        } finally {
            requestSemaphore.release();
        }
    }


    record Document(
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

    record Description(
            String participantInn
    ) {
    }

     record Product(
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



