package org.eclipse.dataspaceconnector.demo.assetindex;

import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.eclipse.dataspaceconnector.junit.launcher.EdcExtension;
import org.eclipse.dataspaceconnector.spi.types.TypeManager;
import org.eclipse.dataspaceconnector.spi.types.domain.asset.Asset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdcExtension.class)
public class DemoAssetIndexExtensionTest {

    @Test
    void insertAndRetrieveAssets(OkHttpClient httpClient, TypeManager typeManager) {
        AssetsClient assetsClient = new AssetsClient(httpClient, typeManager);
        Map<String, String> requestBody = Map.of("id", "anId", "path", "/a/valid/path");
        assetsClient.post(requestBody);

        List<Asset> assets = assetsClient.getAll();

        assertThat(assets).hasSize(1).element(0)
                .matches(it -> "anId".equals(it.getId()))
                .matches(it -> "/a/valid/path".equals(it.getProperties().get("path")));
    }

    private static class AssetsClient {

        private final OkHttpClient httpClient;
        private final TypeManager typeManager;
        private final String url = "http://localhost:8181/api/assets";

        public AssetsClient(OkHttpClient httpClient, TypeManager typeManager) {
            this.httpClient = httpClient;
            this.typeManager = typeManager;
        }

        public void post(Map<String, String> requestData) {
            RequestBody requestBody = RequestBody.create(typeManager.writeValueAsBytes(requestData), MediaType.get("application/json"));
            Request request = new Request.Builder().url(url)
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }

        public List<Asset> getAll() {
            Request request = new Request.Builder().url(url).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                return typeManager.readValue(response.body().string(), new TypeReference<>() {});
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }
    }
}
