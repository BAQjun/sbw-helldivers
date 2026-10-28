package com.example.sbwhelldiver.platform

import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLEnvironment
import java.util.function.Supplier

/** Keep client class references behind a lazy supplier on dedicated servers. */
object ClientDispatch {
    @JvmStatic fun unsafeRunWhenOn(dist: Dist, work: Supplier<Runnable>) {
        if (FMLEnvironment.dist == dist) work.get().run()
    }
}
