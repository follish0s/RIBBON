package org.embeddedt.embeddium.impl;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.embeddedt.embeddium.impl.compatibility.environment.probe.GraphicsAdapterProbe;
import org.embeddedt.embeddium.impl.compatibility.workarounds.Workarounds;
import net.neoforged.fml.loading.FMLEnvironment;

public class EmbeddiumPreLaunch {
    public static void onPreLaunch() {
        if(FMLEnvironment.dist == Dist.CLIENT) {
            GraphicsAdapterProbe.findAdapters();
            Workarounds.init();
        }
    }
}