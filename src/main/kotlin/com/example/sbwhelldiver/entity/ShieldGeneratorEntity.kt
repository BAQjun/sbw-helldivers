package com.example.sbwhelldiver.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/** A separate, server-owned shield pool. Broken shields never recharge, including after loading. */
class ShieldGeneratorEntity(type:EntityType<*>,level:Level):AbstractHellpodEntity(type,level) {
    val shieldHealth get()=entityData.get(SHIELD_HEALTH)
    val shieldBroken get()=entityData.get(BROKEN)
    val brokenAt get()=entityData.get(BROKEN_AT)
    val shieldHits get()=entityData.get(HITS)
    val shieldOnline get()=phase==Phase.ACTIVE && !isWreck && !shieldBroken && shieldHealth>0f
    val shieldAge get()=phaseAge
    fun shieldCenter(partial:Float=1f)=getPosition(partial).add(0.0,CENTER_HEIGHT,0.0)
    fun emitter(partial:Float=1f)=getPosition(partial).add(0.0,1.57,0.0)
    override fun defineSynchedData(builder: SynchedEntityData.Builder){super.defineSynchedData(builder);builder.define(SHIELD_HEALTH,CAPACITY);builder.define(BROKEN,false);builder.define(BROKEN_AT,-1L);builder.define(HITS,CompoundTag())}
    override fun canAddPassenger(passenger:Entity)=false
    override fun tick(){super.tick();if(level().isClientSide)com.example.sbwhelldiver.client.ShieldGeneratorClient.update(this)}
    override fun tickActive(){if(shieldOnline)entityData.set(SHIELD_HEALTH,(shieldHealth+REGEN_PER_TICK).coerceAtMost(CAPACITY))}
    override fun shouldRetract()=activeTicks>=LIFETIME_TICKS
    /** Returns true only when this live shield absorbed a projectile at its surface. */
    fun absorb(amount:Float,point:Vec3):Boolean {
        if(level().isClientSide || !shieldOnline || !amount.isFinite())return false
        entityData.set(SHIELD_HEALTH,(shieldHealth-amount.coerceAtLeast(0f)).coerceAtLeast(0f))
        val data=shieldHits.copy();val hits=data.getList("hits",10);val hit=CompoundTag();val local=point.subtract(shieldCenter())
        hit.putDouble("x",local.x);hit.putDouble("y",local.y);hit.putDouble("z",local.z);hit.putLong("time",level().gameTime)
        hits.add(hit);while(hits.size>8)hits.removeAt(0);data.put("hits",hits);data.putInt("serial",data.getInt("serial")+1);entityData.set(HITS,data)
        if(shieldHealth<=0f)breakShield()
        return true
    }
    private fun breakShield(){if(shieldBroken)return;entityData.set(BROKEN,true);entityData.set(SHIELD_HEALTH,0f);entityData.set(BROKEN_AT,level().gameTime)}
    override fun hurt(source:DamageSource,amount:Float):Boolean {
        if(level().isClientSide || isRemoved || isWreck || phase in listOf(Phase.DONE,Phase.SPENT) || !amount.isFinite() || amount<=0 || isInvulnerableTo(source))return false
        val accepted=if(source.`is`(DamageTypeTags.IS_EXPLOSION) || source.entity is Player){
            lastDamageSource=source;lastDamageStamp=level().gameTime;source.entity?.let {lastAttackerUUID=it.stringUUID}
            val damage=if(source.`is`(DamageTypeTags.IS_EXPLOSION))amount*.5f else getDamageModifier().compute(this,source,amount)
            onHurt(damage,source.entity,true);true
        }else {val before=health;super.hurt(source,amount)||health<before}
        if(accepted && health<=0f)destroy()
        return accepted
    }
    override fun destroy(){if(phase in listOf(Phase.DESTROYED,Phase.SPENT,Phase.DONE))return;breakShield();isWreck=true;health=1f;super.destroy();health=1f;enterPhase(Phase.DESTROYED)}
    override fun onHurt(amount:Float,attacker:Entity?,send:Boolean){if((!send && amount>0f && !isWreck)||phase in listOf(Phase.SPENT,Phase.DONE))return;super.onHurt(amount,attacker,send)}
    override fun remove(reason:Entity.RemovalReason){
        if(reason==Entity.RemovalReason.DISCARDED && phase==Phase.DESTROYED && isWreck && health<=-getMaxHealth()){
            if(!level().isClientSide){isWreck=false;health=getMaxHealth();enterPhase(Phase.SPENT)};return
        };super.remove(reason)
    }
    override fun addAdditionalSaveData(tag:CompoundTag){super.addAdditionalSaveData(tag);tag.putFloat("ShieldHealth",shieldHealth);tag.putBoolean("ShieldBroken",shieldBroken);tag.putLong("ShieldBrokenAt",brokenAt)}
    override fun readAdditionalSaveData(tag:CompoundTag){super.readAdditionalSaveData(tag);val hp=if(tag.contains("ShieldHealth"))tag.getFloat("ShieldHealth").coerceIn(0f,CAPACITY) else CAPACITY;entityData.set(SHIELD_HEALTH,hp);entityData.set(BROKEN,tag.getBoolean("ShieldBroken")||hp<=0);entityData.set(BROKEN_AT,if(tag.contains("ShieldBrokenAt"))tag.getLong("ShieldBrokenAt") else -1L)}
    companion object {
        const val RADIUS=15.0
        const val CENTER_HEIGHT=1.25
        const val CAPACITY=640f
        const val REGEN_PER_TICK=3.2f
        const val LIFETIME_TICKS=800
        private val SHIELD_HEALTH=SynchedEntityData.defineId(ShieldGeneratorEntity::class.java,EntityDataSerializers.FLOAT)
        private val BROKEN=SynchedEntityData.defineId(ShieldGeneratorEntity::class.java,EntityDataSerializers.BOOLEAN)
        private val BROKEN_AT=SynchedEntityData.defineId(ShieldGeneratorEntity::class.java,EntityDataSerializers.LONG)
        private val HITS=SynchedEntityData.defineId(ShieldGeneratorEntity::class.java,EntityDataSerializers.COMPOUND_TAG)
    }
}
