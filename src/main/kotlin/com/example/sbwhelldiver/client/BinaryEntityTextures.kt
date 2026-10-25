package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.security.MessageDigest
import java.util.HexFormat

/** Only validated 0/255-alpha textures can avoid translucent face sorting.
 * Comparing compressed asset hashes is cheap and does not allocate decoded images.
 * Resource-pack replacements with any other content retain the native material. */
object BinaryEntityTextures {
    private var textures: Set<ResourceLocation> = emptySet()
    fun contains(texture: ResourceLocation) = texture in textures
    fun reload(resources: ResourceManager) {
        val manifest = resources.getResource(ResourceLocation(MOD_ID,"models/entity_cutout_textures.json")).orElse(null)
        if(manifest==null){textures=emptySet();return}
        val entries=manifest.openAsReader().use{JsonParser.parseReader(it).asJsonObject}
        val valid=HashSet<ResourceLocation>()
        val digest=MessageDigest.getInstance("SHA-256")
        val buffer=ByteArray(65536)
        for((name,hash) in entries.entrySet()) {
            val location=ResourceLocation.tryParse(name) ?: continue
            val resource=resources.getResource(location).orElse(null) ?: continue
            digest.reset()
            resource.open().use { stream ->
                while(true){val count=stream.read(buffer);if(count<0)break;digest.update(buffer,0,count)}
            }
            if(HexFormat.of().formatHex(digest.digest())==hash.asString)valid.add(location)
        }
        textures=valid
    }
}
