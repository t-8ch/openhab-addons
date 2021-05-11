package org.openhab.binding.openwrt.internal;

import static org.openhab.binding.openwrt.internal.OpenWrtBindingConstants.THING_TYPE_DEVICE;
import static org.openhab.binding.openwrt.internal.OpenWrtBindingConstants.THING_TYPE_WIFI_DEVICE;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.openwrt.internal.handler.device.OpenWrtDeviceHandler;
import org.openhab.binding.openwrt.internal.handler.wifidevice.OpenWrtWifiDeviceHandler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;

@NonNullByDefault
@Component(configurationPid = "binding.openwrt", service = ThingHandlerFactory.class)
public class OpenWrtHandlerFactory extends BaseThingHandlerFactory {
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_DEVICE,
            THING_TYPE_WIFI_DEVICE);

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_DEVICE.equals(thingTypeUID)) {
            return new OpenWrtDeviceHandler((Bridge) thing);
        }
        if (THING_TYPE_WIFI_DEVICE.equals(thingTypeUID)) {
            return new OpenWrtWifiDeviceHandler((Bridge) thing);
        }

        return null;
    }
}
