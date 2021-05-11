package org.openhab.binding.openwrt.internal.ubus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@NonNullByDefault
public class JsonRpc {
    private final Logger log = LoggerFactory.getLogger(JsonRpc.class);

    private int id = 1;
    private final URI uri;
    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public JsonRpc(URI uri) {
        this.uri = uri;
    }

    public static class Params {
        final JsonElement element;

        private Params(Collection<JsonElement> params) {
            JsonArray array = new JsonArray(params.size());
            params.forEach(array::add);
            this.element = array;
        }

        private Params(Map<String, JsonElement> params) {
            JsonObject object = new JsonObject();
            params.forEach(object::add);
            this.element = object;
        }

        public static Params byPosition(Collection<JsonElement> params) {
            return new Params(params);
        }

        public static Params byName(Map<String, JsonElement> params) {
            return new Params(params);
        }
    }

    private final HttpResponse.BodyHandler<JsonElement> jsonRpcBodyHandler = responseInfo -> HttpResponse.BodySubscribers
            .mapping(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), string -> {
                log.warn("Received response with payload '{}'", string);
                return Objects.requireNonNull(gson.fromJson(string, JsonElement.class));
            });

    public CompletableFuture<JsonElement> call(String method, @Nullable Params params) {
        JsonObject payload = new JsonObject();
        payload.addProperty("jsonrpc", "2.0");
        payload.addProperty("id", id++);
        payload.addProperty("method", method);
        if (params != null) {
            payload.add("params", params.element);
        }
        String body = payload.toString();
        log.warn("Sending request with payload '{}'", payload);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json").build();

        return client.sendAsync(req, jsonRpcBodyHandler).thenApply(JsonRpc::extractResult);
    }

    private static JsonElement extractResult(HttpResponse<JsonElement> response) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException(response.toString());
        }
        JsonElement body = response.body();
        if (!body.isJsonObject()) {
            throw new IllegalStateException(response.toString());
        }
        JsonObject bodyObject = body.getAsJsonObject();
        JsonObject error = bodyObject.getAsJsonObject("error");
        if (error != null) {
            throw new JsonRcpException(error.get("code").getAsInt(), error.get("message").getAsString(), null);
        }
        return bodyObject.get("result");
    }

    public static class JsonRcpException extends RuntimeException {
        public JsonRcpException(int code, String message, @Nullable Exception parent) {
            super(String.format("%s: %s", code, message), parent);
        }
    }
}
