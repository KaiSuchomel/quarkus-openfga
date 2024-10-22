package io;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.openfga.client.AuthorizationModelClient;
import io.quarkiverse.openfga.client.OpenFGAClient;
import io.quarkiverse.openfga.client.StoreClient;
import io.quarkiverse.openfga.client.model.AuthorizationModelSchema;
import io.quarkiverse.openfga.client.model.ConditionalTupleKey;
import io.quarkiverse.openfga.client.model.RelationshipCondition;
import io.quarkiverse.openfga.client.model.Store;
import io.quarkiverse.openfga.client.model.TupleKey;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class GreetingResourceTest {

    @Inject
    ObjectMapper objectMapper;
    @Inject
    StoreClient storeClient;
    @Inject
    OpenFGAClient openFgaClient;

    @Test
    void shouldDoOpenFGA() throws Exception {
        //upload model 
        try (InputStream inputStream = getClass().getResourceAsStream("/model.json");) {
            AuthorizationModelSchema schema = objectMapper.readValue(inputStream, AuthorizationModelSchema.class);
            String storeName = "mytest";
            List<Store> existingStores = openFgaClient.listAllStores().await().indefinitely();
            String storeId = existingStores.stream()
                    .filter(s -> storeName.equals(s.getName()))
                    .findAny()
                    .map(Store::getId)
                    .orElseGet(() -> openFgaClient.createStore(storeName).await().indefinitely().getId());
            StoreClient storeClient = openFgaClient.store(storeId);
            storeClient.authorizationModels().create(schema).await().indefinitely();
        }
        AuthorizationModelClient defaultModel = storeClient.authorizationModels().defaultModel();
        defaultModel.write(ConditionalTupleKey.of("role:sample_update", "assignee", "user:Hans")).await().indefinitely();

        //upload types
        List<ConditionalTupleKey> readValue = List.of(
                ConditionalTupleKey.of("role:sample_update", "assignee", "user:Hans"),
                ConditionalTupleKey.of("role:sample_user", "assignee", "user:Hans"),
                ConditionalTupleKey.of("role:sample_admin", "assignee", "user:admin"),
                ConditionalTupleKey.of("role:sample_update", "plant", "plant:MyPlant"),
                ConditionalTupleKey.of("role:sample_user", "plant", "plant:OtherPlant"),
                ConditionalTupleKey.of("asset-category:Sample", "update", "role:sample_update#assignee", RelationshipCondition.of("samePlant", Map.of("plantA", "MyPlant"))),
                ConditionalTupleKey.of("asset-category:Sample", "read", "role:sample_user#assignee", RelationshipCondition.of("samePlant", Map.of("plantA", "OtherPlant"))),
                ConditionalTupleKey.of("asset-category:Sample", "delete", "role:sample_admin#assignee", RelationshipCondition.of("samePlant", Map.of("plantA", "MyPlant"))),
                ConditionalTupleKey.of("asset-category:Sample", "release", "role:sample_admin#assignee", RelationshipCondition.of("samePlant", Map.of("plantA", "MyPlant"))));

        defaultModel.write(readValue, List.of()).await().indefinitely();

        assertTrue(defaultModel
                .check(
                        TupleKey.of("role:sample_user", "plant", "plant:OtherPlant"))
                .await().indefinitely());
        assertFalse(defaultModel
                .check(
                        TupleKey.of("asset-category:Sample", "read", "user:admin"))
                .await().indefinitely());
        assertTrue(defaultModel
                .check(
                        TupleKey.of("asset-category:Sample", "read", "user:admin"),
                        List.of(),
                        Map.of("plantB", "MyPlant"))
                .await().indefinitely());
    }

    @Test
    void testHelloEndpoint() {
        given()
                .when().get("/hello")
                .then()
                .statusCode(200)
                .body(is("Hello from Quarkus REST"));
    }

}
