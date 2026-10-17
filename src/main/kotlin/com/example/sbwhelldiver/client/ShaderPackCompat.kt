package com.example.sbwhelldiver.client

/** Optional Iris/Oculus API lookup. No shader loader is required to run the mod.
 * Query the API at render time so toggling shader packs and resource reloads work.
 */
object ShaderPackCompat {
 private data class Api(val instance:Any,val active:java.lang.reflect.Method,val shadow:java.lang.reflect.Method?)
 private val api:Api? by lazy {
  sequenceOf("net.irisshaders.iris.api.v0.IrisApi","net.coderbot.iris.api.v0.IrisApi").mapNotNull { name ->
   runCatching {
    val type=Class.forName(name)
    Api(type.getMethod("getInstance").invoke(null),type.getMethod("isShaderPackInUse"),runCatching{type.getMethod("isRenderingShadowPass")}.getOrNull())
   }.getOrNull()
  }.firstOrNull()
 }
 fun active():Boolean=api?.let{runCatching{it.active.invoke(it.instance) as Boolean}.getOrDefault(false)}?:false
 fun shadowPass():Boolean=api?.let{runCatching{it.shadow?.invoke(it.instance) as? Boolean?:false}.getOrDefault(false)}?:false
}
