package org.openhab.binding.openwrt.internal.ubus;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

@NonNullByDefault({})
public class UbusTransportHttp implements UbusTransport {
    private final JsonRpc rpc;

    public UbusTransportHttp(URI url) {
        rpc = new JsonRpc(url);
    }

    @Override
    public CompletableFuture<JsonElement> call(String session, String object, String method,
            Map<String, JsonElement> params) {
        JsonObject p = new JsonObject();
        params.forEach(p::add);
        return rpc.call("call", JsonRpc.Params.byPosition(
                List.of(new JsonPrimitive(session), new JsonPrimitive(object), new JsonPrimitive(method), p)));
    }
}
