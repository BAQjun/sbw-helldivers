package com.example.sbwhelldiver.client

import java.util.Random
import kotlin.math.*

/** Deterministic cosmetic geometry; independent of damage and target selection. */
object TeslaLightning {
    data class Point(val x:Double,val y:Double,val z:Double) {
        operator fun plus(b:Point)=Point(x+b.x,y+b.y,z+b.z)
        operator fun minus(b:Point)=Point(x-b.x,y-b.y,z-b.z)
        operator fun times(s:Double)=Point(x*s,y*s,z*s)
        fun dot(b:Point)=x*b.x+y*b.y+z*b.z
        fun cross(b:Point)=Point(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
        fun length()=sqrt(dot(this))
        fun unit()=this*(1.0/length().coerceAtLeast(1e-9))
    }
    data class Strand(val points:List<Point>,val width:Double,val strength:Double,val tapered:Boolean)

    private fun path(a:Point,b:Point,seed:Long,roughness:Double):List<Point> {
        val axis=b-a;val length=axis.length()
        if(length<1e-6)return listOf(a,b)
        val rng=Random(seed);val direction=axis.unit()
        val u=direction.cross(if(abs(direction.y)>.9)Point(1.0,0.0,0.0) else Point(0.0,1.0,0.0)).unit()
        val v=direction.cross(u).unit()
        var points=listOf(a,b);var displacement=min(1.25,length*.105)*roughness
        val levels=ceil(log2((length/.26).coerceAtLeast(2.0))).toInt().coerceIn(2,7)
        repeat(levels) {
            val next=ArrayList<Point>(points.size*2-1)
            for(i in 0 until points.lastIndex) {
                val left=points[i];val right=points[i+1];val t=.4+rng.nextDouble()*.2
                next.add(left)
                next.add(left+(right-left)*t+u*((rng.nextDouble()*2-1)*displacement)+v*((rng.nextDouble()*2-1)*displacement))
            }
            next.add(b);points=next;displacement*=.48
        }
        return points
    }

    fun bolt(a:Point,b:Point,seed:Long,branches:Boolean=true,roughness:Double=1.0):List<Strand> {
        val points=path(a,b,seed,roughness);val result=mutableListOf(Strand(points,1.0,1.0,false))
        val length=(b-a).length()
        if(!branches || length<1.0)return result
        val rng=Random(seed xor 0x61AB93L);val direction=(b-a).unit()
        repeat((length/6).toInt().coerceIn(2,5)) {
            val i=(points.lastIndex*(.17+rng.nextDouble()*.63)).toInt().coerceIn(1,points.lastIndex-1)
            val start=points[i];val outward=Point(rng.nextDouble()-.5,rng.nextDouble()-.35,rng.nextDouble()-.5).unit()
            val reach=(length*(.06+rng.nextDouble()*.09)).coerceIn(.35,3.0)
            val end=start+(direction*.55+outward*.8).unit()*reach
            val fork=path(start,end,rng.nextLong(),.75)
            result.add(Strand(fork,.32+rng.nextDouble()*.15,.5+rng.nextDouble()*.2,true))
            if(it%2==0 && reach>.9) {
                val root=fork[fork.size/2]
                result.add(Strand(path(root,root+outward*(reach*.4),rng.nextLong(),.7),.16,.36,true))
            }
        }
        return result
    }

    /** Open, intersecting filaments instead of a stack of regular polygon rings. */
    fun corona(seed:Long,progress:Double):List<Strand> {
        val rng=Random(seed);val radius=.35+1.25*progress;val result=mutableListOf<Strand>()
        fun direction():Point {
            val y=rng.nextDouble()*2-1;val a=rng.nextDouble()*PI*2;val r=sqrt(1-y*y)
            return Point(cos(a)*r,y,sin(a)*r)
        }
        repeat(7) {
            val u=direction();val v=u.cross(direction()).unit();val start=rng.nextDouble()*PI*2
            val span=1.8+rng.nextDouble()*2.4
            val points=(0..20).map { i ->
                val angle=start+span*i/20;val jitter=radius*(.88+rng.nextDouble()*.24)
                (u*cos(angle)+v*sin(angle))*jitter
            }
            val detailed=points.zipWithNext().flatMapIndexed { i,(a,b) ->
                val segment=path(a,b,rng.nextLong(),.42)
                if(i==0)segment else segment.drop(1)
            }
            result.add(Strand(detailed,.6,.55+progress*.35,false))
        }
        repeat(3) {
            val end=direction()*(radius*(1.5+rng.nextDouble()*.55))
            result.addAll(bolt(Point(0.0,0.0,0.0),end,rng.nextLong(),false,.8).map { it.copy(width=.5,strength=.65,tapered=true) })
        }
        return result
    }

    /** A short, radial electrical rupture at each struck unit, with forked sparks. */
    fun impactBurst(seed:Long):List<Strand> {
        val rng=Random(seed)
        val result=mutableListOf<Strand>()
        repeat(9) { i ->
            val y=-.75+1.5*(i+.5)/9
            val angle=i*PI*(3-sqrt(5.0))+rng.nextDouble()*.45
            val radial=sqrt(1-y*y)
            val direction=Point(cos(angle)*radial,y,sin(angle)*radial)
            val points=path(Point(0.0,0.0,0.0),direction*(.85+rng.nextDouble()*.7),rng.nextLong(),.85)
            result.add(Strand(points,.62,.9,true))
            if(i%3==0) {
                val root=points[points.size/2]
                val end=root+Point(-direction.z,.4,direction.x)*.5
                result.add(Strand(path(root,end,rng.nextLong(),.75),.30,.65,true))
            }
        }
        return result
    }

    /** Broken, irregular spirals from the terminal down the exposed mast and lower column.
     * Positions are relative to the terminal; never used for target selection or damage. */
    fun columnArcs(seed:Long,height:Double):List<Strand> {
        val rng=Random(seed)
        val drop=height.coerceIn(.5,4.0)
        return (0..2).map { strand ->
            val start=rng.nextDouble()*PI*2
            val turns=1.35+rng.nextDouble()*.75
            val sign=if(strand==1)-1.0 else 1.0
            val points=mutableListOf(Point(0.0,0.0,0.0))
            for(i in 0..84) {
                val t=i/84.0
                val down=.13+(drop-.13)*t
                // Highest-detail model: upper prongs reach .18m; lower column .31m.
                val surface=.24+.12*((down-2.5)/.4).coerceIn(0.0,1.0)
                val radius=surface+(rng.nextDouble()-.5)*.038
                val angle=start+sign*turns*PI*2*t+.12*sin(t*37+strand)+.06*(rng.nextDouble()-.5)
                points.add(Point(cos(angle)*radius,-down+(rng.nextDouble()-.5)*.025,sin(angle)*radius))
            }
            Strand(points,.68-strand*.12,.9-strand*.15,false)
        }
    }
}
