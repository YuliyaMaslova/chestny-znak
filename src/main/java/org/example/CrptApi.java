package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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

    private static final URI API_URI = URI.create("http://localhost:8081/api/v3/lk/documents/create");
    private final Semaphore requestSemaphore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TimeUnit timeUnit;
    private final Timer timer;


    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestSemaphore = new Semaphore(requestLimit);
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.timeUnit = timeUnit;
        this.timer = new Timer();

    }

    public void createDocument(Document document, String signature) {
        acquireRequestPermit();

        long resetInterval = TimeUnit.MILLISECONDS.convert(1, timeUnit);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                requestSemaphore.release();
            }
        }, resetInterval);

        sendCreateDocumentRequest(document, signature);

    }

    private void acquireRequestPermit() {
        try {
            requestSemaphore.acquire();//используется для ограничения количества одновременных запросов к API
            // захватывает разрешение у семафора

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted");

        }
    }

    private void sendCreateDocumentRequest(Document document, String signature) {
        try {

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
        }
    }


    @JsonSerialize
    public record Document(
            @JsonProperty("description")
            Description description,
            @JsonProperty("doc_id")
            String docId,
            @JsonProperty("doc_status")
            String docStatus,
            @JsonProperty("doc_type")
            String docType,
            int number,

            boolean importRequest,
            @JsonProperty("owner_inn")
            String ownerInn,
            @JsonProperty("participant_inn")
            String participantInn,
            @JsonProperty("producer_inn")
            String producerInn,
            @JsonProperty("production_date")
            LocalDate productionDate,
            @JsonProperty("production_type")
            String productionType,
            List<Product> products,
            @JsonProperty("reg_date")
            LocalDate regDate,
            @JsonProperty("reg_number")
            String regNumber
    ) {
    }

    @JsonSerialize
    public record Description(
            String participantInn
    ) {
    }

    @JsonSerialize
    public record Product(
            @JsonProperty("certificate_document")
            String certificateDocument,
            @JsonProperty("certificate_document_date")
            LocalDate certificateDocumentDate,
            @JsonProperty("certificate_document_number")
            String certificateDocumentNumber,
            @JsonProperty("owner_inn")
            String ownerInn,
            @JsonProperty("producer_inn")
            String producerInn,
            @JsonProperty("production_date")
            LocalDate productionDate,
            @JsonProperty("tnved_code")
            String tnvedCode,
            @JsonProperty("uit_code")
            String uitCode,
            @JsonProperty("uitu_code")
            String uituCode
    ) {
    }
}



