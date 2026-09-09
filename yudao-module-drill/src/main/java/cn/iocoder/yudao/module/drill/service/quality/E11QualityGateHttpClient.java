package cn.iocoder.yudao.module.drill.service.quality;

import cn.iocoder.yudao.module.drill.config.E11QualityGateProperties;
import cn.iocoder.yudao.module.drill.service.quality.model.QualityImageInput;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
public class E11QualityGateHttpClient {

    private final E11QualityGateProperties properties;
    private final RestTemplate restTemplate;
    private final RestTemplate healthRestTemplate;
    private final FormHttpMessageConverter formConverter = new FormHttpMessageConverter();

    public E11QualityGateHttpClient(E11QualityGateProperties properties,
                                    @Qualifier("e11QualityGateRestTemplate") RestTemplate restTemplate,
                                    @Qualifier("e11QualityGateHealthRestTemplate") RestTemplate healthRestTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.healthRestTemplate = healthRestTemplate;
    }

    public Response ready() {
        return healthRestTemplate.execute(uri("/health/ready"), HttpMethod.GET, null, this::extract);
    }

    public Response predict(QualityImageInput image, double confidence, int minDetections, String requestId) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(image.mimeType()));
        form.add("file", new HttpEntity<>(
                new NamedByteArrayResource(image.content(), safeFilename(image.originalName())), fileHeaders));
        form.add("confidence", Double.toString(confidence));
        form.add("minDetections", Integer.toString(minDetections));
        form.add("requestId", requestId);
        return restTemplate.execute(uri("/v1/e11/predict"), HttpMethod.POST, request -> {
            request.getHeaders().setContentType(MediaType.MULTIPART_FORM_DATA);
            request.getHeaders().set("trace-id", requestId);
            formConverter.write(form, MediaType.MULTIPART_FORM_DATA, request);
        }, this::extract);
    }

    private Response extract(ClientHttpResponse response) throws IOException {
        int limit = properties.getMaxResponseBytes() == null ? 5 * 1024 * 1024
                : properties.getMaxResponseBytes();
        if (limit < 1) {
            throw new IllegalArgumentException("drill.e11-quality-gate.max-response-bytes must be positive");
        }
        byte[] body;
        try (InputStream input = response.getBody()) {
            body = input.readNBytes(limit + 1);
        }
        if (body.length > limit) {
            throw new ResponseTooLargeException(limit);
        }
        return new Response(response.getStatusCode().value(),
                new String(body, StandardCharsets.UTF_8).trim());
    }

    private URI uri(String path) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("drill.e11-quality-gate.base-url must not be blank");
        }
        return URI.create(baseUrl.replaceAll("/+$", "") + path);
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image";
        }
        String normalized = filename.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return name.isBlank() ? "image" : name;
    }

    public record Response(int statusCode, String body) {
    }

    public static final class ResponseTooLargeException extends RuntimeException {
        ResponseTooLargeException(int limit) {
            super("E11 response exceeds " + limit + " bytes");
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
