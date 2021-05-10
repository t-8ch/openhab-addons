package org.openhab.binding.openwrt;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.openhab.binding.openwrt.internal.ubus.Ubus;
import org.openhab.binding.openwrt.internal.ubus.UbusTransportHttp;

class UbusTest {
    @Test
    void foo() throws Exception {
        Ubus ubus = new Ubus(new UbusTransportHttp(URI.create("http://192.168.1.1/ubus")),
                new Ubus.Auth("root", "\\KfP3}En`,bM/pald(Spu^6*Q"));
        ubus.login().get();
    }
}
