package com.example.sbwhelldiver.client

/** Frame-time interpolation; reversing an unfinished transition preserves its current position. */
internal class HudSlide {
    private var started=0L
    private var from=0.0
    private var shown=false
    fun value(now:Long):Double {
        val t=((now-started)/180_000_000.0).coerceIn(0.0,1.0)
        val eased=1.0-(1.0-t)*(1.0-t)*(1.0-t)
        return from+((if(shown)1.0 else 0.0)-from)*eased
    }
    fun target(show:Boolean,now:Long) {
        if(show==shown)return
        from=value(now);started=now;shown=show
    }
}
