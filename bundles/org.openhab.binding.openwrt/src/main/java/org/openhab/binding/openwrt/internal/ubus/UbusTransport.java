package org.openhab.binding.openwrt.internal.ubus;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.JsonElement;

@NonNullByDefault({})
public interface UbusTransport {
    CompletableFuture<JsonElement> call(String session, String object, String method, Map<String, JsonElement> params);
}
