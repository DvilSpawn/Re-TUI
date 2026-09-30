package ohi.andre.consolelauncher.wallpaper.nagomi

import android.graphics.*
import kotlin.math.*

/** Adapted from Nagomi lotus-leaves.ts, duckweed.ts, duckweed-geometry.ts and
 * settings/definition.ts. Copyright 2026 Mayank Kadam. Geometry is baked once;
 * the original drift/sway transforms run on the shared animation clock.
 */
internal class NagomiPlants {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val path = Path()
    private val leafPalettes = arrayOf(
        intArrayOf(0x5f9d78,0x76aa84,0x487c66,0x3f705e,0x4f866b),
        intArrayOf(0x568f6f,0x6ca17b,0x416f5b,0x386653,0x497d63))
    private val flowerPalettes = arrayOf(
        intArrayOf(0xf29aaa,0xffc4cc,0xffe1e2,0xf2bd45,0xb96d31),
        intArrayOf(0xe985ac,0xfab7ce,0xffdce6,0xf5c64b,0xbd7330))
    private val weedPalettes = arrayOf(
        intArrayOf(0x6fc94f,0x9be66c,0x45963e,0xc3ee75),
        intArrayOf(0x83d35b,0xb1ed76,0x549e43,0xd0f28a))
    // Original placements in Nagomi's 480 x 270 design space.
    private val leaves = arrayOf(
        doubleArrayOf(-3.0,37.0,22.0,.35,.2,0.0), doubleArrayOf(76.0,17.0,16.0,2.15,1.4,1.0),
        doubleArrayOf(431.0,18.0,23.0,2.75,2.2,0.0), doubleArrayOf(476.0,88.0,17.0,4.25,3.3,1.0),
        doubleArrayOf(460.0,151.0,23.0,.95,4.6,0.0), doubleArrayOf(488.0,216.0,20.0,3.55,5.4,1.0),
        doubleArrayOf(395.0,252.0,22.0,5.3,.9,0.0), doubleArrayOf(113.0,260.0,28.0,4.65,2.8,1.0),
        doubleArrayOf(31.0,230.0,19.0,1.85,4.1,0.0), doubleArrayOf(140.0,10.0,21.0,.7,5.9,1.0),
        doubleArrayOf(330.0,7.0,14.0,3.85,1.8,0.0), doubleArrayOf(447.0,57.0,20.0,5.65,3.8,1.0),
        doubleArrayOf(82.0,76.0,32.0,1.25,4.9,0.0), doubleArrayOf(414.0,194.0,23.0,4.85,2.5,1.0),
        doubleArrayOf(444.0,224.0,20.0,2.85,2.5,1.0))
    private val flowers = arrayOf(
        doubleArrayOf(12.0,5.4,.5,-.5,.25,0.0), doubleArrayOf(13.0,5.0,-.8,.3,.75,1.0),
        doubleArrayOf(7.0,4.8,2.0,-1.0,.45,0.0), doubleArrayOf(9.0,4.5,-1.0,.5,.15,1.0))
    private val patches = arrayOf(
        doubleArrayOf(28.0,45.0,30.0,42.0,.3,0.0), doubleArrayOf(102.0,17.0,22.0,28.0,1.7,1.0),
        doubleArrayOf(447.0,34.0,77.0,138.0,2.8,0.0), doubleArrayOf(470.0,116.0,25.0,34.0,4.1,1.0),
        doubleArrayOf(451.0,225.0,31.0,44.0,5.3,0.0), doubleArrayOf(378.0,259.0,22.0,29.0,.9,1.0),
        doubleArrayOf(71.0,244.0,29.0,40.0,3.4,0.0), doubleArrayOf(13.0,168.0,64.0,200.0,4.8,0.0))
    private val leafImages = leaves.map(::leaf)
    private val flowerImages = flowers.map(::flower)
    private val weedImages = patches.mapIndexed(::weed)
    private val lotusShadow = PorterDuffColorFilter(0xff0a2b26.toInt(), PorterDuff.Mode.SRC_IN)
    private val weedShadow = PorterDuffColorFilter(0xff123b2d.toInt(), PorterDuff.Mode.SRC_IN)

    private fun color(rgb: Int) { paint.color = rgb or 0xff000000.toInt() }
    private fun sprite(radius: Double, draw: (Canvas) -> Unit): Bitmap {
        val size = ceil(radius+8).toInt()*2
        return Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it); c.translate(size/2f,size/2f); draw(c)
        }
    }
    private fun polygon(c: Canvas, xy: DoubleArray, rgb: Int) {
        path.rewind(); path.moveTo(xy[0].toFloat(),xy[1].toFloat())
        for (i in 2 until xy.size step 2) path.lineTo(xy[i].toFloat(),xy[i+1].toFloat())
        path.close(); color(rgb); c.drawPath(path,paint)
    }
    private fun circle(c: Canvas, x: Double, y: Double, r: Double, rgb: Int, segments: Int=8) {
        polygon(c,DoubleArray(segments*2) { i -> if (i%2==0) x+cos((i/2)*2*PI/segments)*r else y+sin((i/2)*2*PI/segments)*r },rgb)
    }
    private fun edge(r: Double, a: Double, phase: Double): DoubleArray {
        val wobble=1+sin(a*3+phase)*.035+cos(a*5-phase)*.025
        return doubleArrayOf(cos(a)*r*wobble,sin(a)*r*.92*wobble)
    }
    private fun mix(a: Int,b: Int,t: Double): Int = Color.rgb(
        (((a shr 16 and 255)*(1-t))+(b shr 16 and 255)*t).toInt(),
        (((a shr 8 and 255)*(1-t))+(b shr 8 and 255)*t).toInt(),
        (((a and 255)*(1-t))+(b and 255)*t).toInt())
    private fun leaf(s: DoubleArray): Bitmap = sprite(s[2]*1.18) { c ->
        val r=s[2]*1.18; val p=leafPalettes[s[5].toInt()]; val start=s[3]+.3; val span=2*PI-.6
        fun edgeColor(a: Double): Int {
            val tone=(.5+cos(a+2.2)*.42+sin(a*3+s[4]*.7)*.045).coerceIn(0.0,1.0)
            return if (tone<.5) mix(p[2],p[0],tone*2) else mix(p[0],p[1],(tone-.5)*2)
        }
        color(0xffffff)
        repeat(24) { i ->
            val a=start+i/24.0*span; val b=start+(i+1)/24.0*span
            val ea=edge(r,a,s[4]); val eb=edge(r,b,s[4])
            c.drawVertices(Canvas.VertexMode.TRIANGLES,6,
                floatArrayOf(0f,0f,ea[0].toFloat(),ea[1].toFloat(),eb[0].toFloat(),eb[1].toFloat()),0,null,0,
                intArrayOf(p[4] or 0xff000000.toInt(),edgeColor(a),edgeColor(b)),0,null,0,0,paint)
        }
        circle(c,0.0,0.0,max(1.0,r*.075),p[4])
        color(p[3]); paint.strokeWidth=1f
        for (i in 1..5) { val end=edge(r*.68,start+i/6.0*span,s[4]); c.drawLine(0f,0f,end[0].toFloat(),end[1].toFloat(),paint) }
    }
    private fun flower(s: DoubleArray): Bitmap = sprite(s[1]*2.38) { c ->
        val r=s[1]*2.38; val p=flowerPalettes[s[5].toInt()]
        fun ring(count: Int,length: Double,width: Double,offset: Double,primary: Int) {
            repeat(count) { i ->
                val a=s[4]+offset+i.toDouble()/count*2*PI; val dx=cos(a); val dy=sin(a)
                polygon(c,doubleArrayOf(dx*r*.12-dy*r*width,dy*r*.12+dx*r*width,
                    dx*r*length,dy*r*length,dx*r*.12+dy*r*width,dy*r*.12-dx*r*width),if(i%3==0)p[2] else primary)
            }
        }
        ring(8,1.0,.22,0.0,p[0]); ring(6,.66,.19,PI/6,p[1])
        circle(c,0.0,0.0,r*.28,p[4]); circle(c,0.0,0.0,r*.18,p[3])
    }
    private fun random(seed: Int): Double { val v=sin(seed*12.9898+78.233)*43758.5453; return v-floor(v) }
    private fun weed(index: Int,s: DoubleArray): Bitmap = sprite(s[2]+5) { c ->
        val p=weedPalettes[s[5].toInt()]
        repeat(s[3].toInt()) { i ->
            val seed=index*1013+i*37+11; val d=s[2]*random(seed+1).pow(.68); val a=random(seed+2)*PI*2
            val x=cos(a)*d; val y=sin(a)*d*.74; val r=1.05+random(seed+3)*2.3; val angle=random(seed+4)*PI*2
            fun ellipse(ex: Double,ey: Double,radius: Double,rotation: Double,rgb: Int) {
                val points=DoubleArray(14)
                repeat(7) { j ->
                    val lx=cos(j/7.0*PI*2)*radius; val ly=sin(j/7.0*PI*2)*radius*.76
                    points[j*2]=ex+lx*cos(rotation)-ly*sin(rotation); points[j*2+1]=ey+lx*sin(rotation)+ly*cos(rotation)
                }
                polygon(c,points,rgb)
            }
            val tone=random(seed+6)
            ellipse(x,y,r,angle,if(tone<.24)p[1] else if(tone>.82)p[2] else p[0])
            if(r>1.55) circle(c,x-cos(angle)*r*.18,y-sin(angle)*r*.18,max(.22,r*.14),p[3],5)
            if(random(seed+7)<.42) ellipse(x+cos(angle+.8)*r*.92,y+sin(angle+.8)*r*.92,r*.72,angle+1.15,p[1])
        }
    }
    private fun draw(c: Canvas,image: Bitmap,x: Double,y: Double,rotation: Double,scale: Double=1.0) {
        c.save(); c.translate(x.toFloat(),y.toFloat()); c.rotate(Math.toDegrees(rotation).toFloat()); c.scale(scale.toFloat(),scale.toFloat())
        c.drawBitmap(image,-image.width/2f,-image.height/2f,paint); c.restore()
    }
    fun drawLotusShadows(c: Canvas,time: Double) = drawLeaves(c,time,true)
    private fun drawLeaves(c: Canvas,time: Double,shadow: Boolean) {
        paint.colorFilter=if(shadow)lotusShadow else null; paint.alpha=if(shadow)128 else 255
        leaves.forEachIndexed { i,s ->
            val x=s[0]/480*c.width+sin(time*.12+s[4])*.7
            val y=s[1]/270*c.height+cos(time*.15+s[4]*1.3)*.55
            val pulse=1+sin(time*.11+s[4])*.012
            draw(c,leafImages[i],x+if(shadow)4.8 else 0.0,y+if(shadow)10.4 else 0.0,
                sin(time*.085+s[4])*.055,pulse*if(shadow)1.02 else 1.0)
        }
        paint.colorFilter=null; paint.alpha=255
    }
    fun drawSurface(c: Canvas,time: Double) {
        // Duckweed shadows sit on the water, then leaves and flowers occlude swimming fish.
        for(shadow in listOf(true,false)) {
            paint.colorFilter=if(shadow)weedShadow else null; paint.alpha=if(shadow)61 else 255
            patches.forEachIndexed { i,s ->
                draw(c,weedImages[i],s[0]/480*c.width+sin(time*.1+s[4])*5.55+if(shadow)1.4 else 0.0,
                    s[1]/270*c.height+cos(time*.13+s[4]*1.4)*5.42+if(shadow)5.1 else 0.0,sin(time*.075+s[4])*.045)
            }
        }
        paint.colorFilter=null; paint.alpha=255
        drawLeaves(c,time,false)
        flowers.forEachIndexed { i,f ->
            val s=leaves[f[0].toInt()]
            draw(c,flowerImages[i],s[0]/480*c.width+sin(time*.12+s[4])*.7+f[2],
                s[1]/270*c.height+cos(time*.15+s[4]*1.3)*.55+f[3],sin(time*.12+s[4])*.04)
        }
    }
}
