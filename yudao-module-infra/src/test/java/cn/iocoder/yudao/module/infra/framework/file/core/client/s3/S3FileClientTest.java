package cn.iocoder.yudao.module.infra.framework.file.core.client.s3;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3FileClientTest {

    @Test
    void shouldRemoveBucketPathFromMinioDomain() {
        String endpoint = S3FileClient.normalizePresignerEndpoint(
                "http://192.77.108.247:9000/cnpc", "cnpc", false);

        assertEquals("http://192.77.108.247:9000", endpoint);
    }

    @Test
    void shouldRemoveBucketHostForVirtualHostedDomain() {
        String endpoint = S3FileClient.normalizePresignerEndpoint(
                "https://images.s3.example.com", "images", false);

        assertEquals("https://s3.example.com", endpoint);
    }

    @Test
    void shouldNormalizeTrailingSlashForPathStyleDomain() {
        String endpoint = S3FileClient.normalizePresignerEndpoint(
                "http://minio.internal:9000/images/", "images", true);

        assertEquals("http://minio.internal:9000", endpoint);
    }

    @Test
    void shouldGenerateMinioPutUrlWithoutDuplicatedBucketPath() {
        S3FileClientConfig config = new S3FileClientConfig();
        config.setEndpoint("http://192.77.108.247:9000");
        config.setDomain("http://192.77.108.247:9000/cnpc");
        config.setBucket("cnpc");
        config.setAccessKey("test-access-key");
        config.setAccessSecret("test-access-secret");
        config.setEnablePathStyleAccess(false);
        config.setEnablePublicAccess(true);
        S3FileClient client = new S3FileClient(1L, config);
        client.init();

        URI uploadUri = URI.create(client.presignPutUrl("drill-images/example.jpg"));

        assertEquals("/cnpc/drill-images/example.jpg", uploadUri.getPath());
    }

}
