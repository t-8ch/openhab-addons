package org.openhab.binding.openwrt.internal.ubus;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@NonNullByDefault
public class Ubus {
    private final UbusTransport transport;
    private final static String NULL_SESSION = "00000000000000000000000000000000";
    private String session = NULL_SESSION;
    private final Auth auth;

    public Ubus(UbusTransport transport, Auth auth) {
        this.transport = transport;
        this.auth = auth;
    }

    private CompletableFuture<JsonElement> call(String object, String method) {
        return call(object, method, Map.of());
    }

    private CompletableFuture<JsonElement> call(String object, String method, Map<String, JsonElement> params) {
        return transport.call(session, object, method, params).thenApply(r -> {
            JsonArray array = r.getAsJsonArray();
            int code = array.get(0).getAsInt();
            Optional<UbusError> error = UbusError.fromCode(code);
            error.ifPresent(e -> {
                throw e.toException();
            });
            return array.get(1);
        });
    }

    public CompletableFuture<Void> login() {
        session = NULL_SESSION;
        return call("session", "login",
                Map.of("username", new JsonPrimitive(auth.username), "password", new JsonPrimitive(auth.password)))
                        .thenApply(res -> {
                            session = res.getAsJsonObject().get("ubus_rpc_session").getAsString();
                            return null;
                        });
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
