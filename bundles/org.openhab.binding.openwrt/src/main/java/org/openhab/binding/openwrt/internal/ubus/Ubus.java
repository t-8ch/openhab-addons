package org.openhab.binding.openwrt.internal.ubus;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@NonNullByDefault
public class Ubus {
    private final UbusTransport transport;
    private final static String NULL_SESSION = "00000000000000000000000000000000";
    private @Nullable String session = null;
    private final Auth auth;
    private final Gson gson = new Gson();

    public Ubus(UbusTransport transport, Auth auth) {
        this.transport = transport;
        this.auth = auth;
    }

    public CompletableFuture<JsonElement> call(String object, String method) {
        return call(object, method, Map.of());
    }

    public <T> CompletableFuture<T> call(Class<T> clazz, String object, String method) {
        return call(clazz, object, method, Map.of());
    }

    public <T> CompletableFuture<T> call(Class<T> clazz, String object, String method,
            Map<String, JsonElement> params) {
        return call(object, method, params).thenApply(e -> gson.fromJson(e, clazz));
    }

    public CompletableFuture<JsonElement> call(String object, String method, Map<String, JsonElement> params) {
        CompletableFuture<String> sessionId;
        if (session == null) {
            sessionId = login();
        } else {
            sessionId = CompletableFuture.completedFuture(session);
        }
        return sessionId.thenComposeAsync(s -> callInternal(s, object, method, params));
    }

    private CompletableFuture<JsonElement> callInternal(String sessionParam, String object, String method,
            Map<String, JsonElement> params) {
        return transport.call(sessionParam, object, method, params).thenApply(r -> {
            JsonArray array = r.getAsJsonArray();
            int code = array.get(0).getAsInt();
            Optional<UbusError> error = UbusError.fromCode(code);
            error.ifPresent(e -> {
                throw e.toException();
            });
            return array.get(1);
        });
    }

    private CompletableFuture<String> login() {
        return callInternal(NULL_SESSION, "session", "login",
                Map.of("username", new JsonPrimitive(auth.username), "password", new JsonPrimitive(auth.password)))
                        .thenApply(res -> res.getAsJsonObject().get("ubus_rpc_session").getAsString());
    }

    public static class Auth {
        private final String username;
        private final String password;

        public Auth(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    public static class UbusException extends RuntimeException {
        public UbusException(UbusError error) {
            super(error.toString());
        }
    }

    public enum UbusError {
        InvalidCommand(1),
        InvalidArgument(2),
        MethodNotFound(3),
        NotFound(4),
        NoData(5),
        PermissionDenied(6),
        Timeout(7),
        NotSupported(8),
        UnknownError(9),
        ConnectionFailed(10);

        private final int code;

        UbusError(int code) {
            this.code = code;
        }

        public UbusException toException() {
            return new UbusException(this);
        }

        private static Optional<UbusError> fromCode(int code) {
            if (code == 0) {
                return Optional.empty();
            }
            for (UbusError e : UbusError.values()) {
                if (e.code == code) {
                    return Optional.of(e);
                }
            }
            throw new IllegalArgumentException(String.valueOf(code));
        }
    }
}
