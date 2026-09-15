package com.vag.vcdsandroid.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * High-speed hardware-accelerated Canvas scope view for Boost & N75 telemetry.
 */
class LiveGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maxPoints = 120
    private val boostReqHistory = ArrayDeque<Float>(maxPoints)
    private val boostActHistory = ArrayDeque<Float>(maxPoints)
    private val n75History = ArrayDeque<Float>(maxPoints)

    private val paintGrid = Paint().apply {
        color = Color.parseColor("#262D3D")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val paintGridDashed = Paint().apply {
        color = Color.parseColor("#1B2230")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B949E")
        textSize = 26f
    }

    private val paintBoostReq = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39C5CF") // Cyan
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val paintBoostAct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3FB950") // Neon Green
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val paintN75 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0883E") // Orange
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
    }

    private val paintLegendReq = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39C5CF")
        textSize = 22f
        style = Paint.Style.FILL
    }

    private val paintLegendAct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3FB950")
        textSize = 22f
        style = Paint.Style.FILL
    }

    private val paintLegendN75 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0883E")
        textSize = 22f
        style = Paint.Style.FILL
    }

    private val pathBoostReq = Path()
    private val pathBoostAct = Path()
    private val pathN75 = Path()

    fun addTelemetryPoint(boostReq: Float, boostAct: Float, n75: Float) {
        if (boostReqHistory.size >= maxPoints) {
            boostReqHistory.removeFirst()
            boostActHistory.removeFirst()
            n75History.removeFirst()
        }
        boostReqHistory.addLast(boostReq)
        boostActHistory.addLast(boostAct)
        n75History.addLast(n75)
        postInvalidateOnAnimation()
    }

    fun clear() {
        boostReqHistory.clear()
        boostActHistory.clear()
        n75History.clear()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Background
        canvas.drawColor(Color.parseColor("#0F141C"))

        // Draw grid lines (1000, 1500, 2000, 2500, 3000 mbar)
        val minBoost = 900f
        val maxBoost = 2700f
        val boostRange = maxBoost - minBoost

        val gridLevels = floatArrayOf(1000f, 1500f, 2000f, 2500f)
        for (lvl in gridLevels) {
            val y = h - ((lvl - minBoost) / boostRange) * h
            canvas.drawLine(0f, y, w, y, paintGridDashed)
            canvas.drawText("${lvl.toInt()} mbar", 16f, y - 6f, paintText)
        }

        // Draw border
        canvas.drawRect(0f, 0f, w, h, paintGrid)

        if (boostActHistory.size < 2) return

        val stepX = w / (maxPoints - 1)
        val count = boostActHistory.size
        val startOffset = (maxPoints - count) * stepX

        pathBoostReq.reset()
        pathBoostAct.reset()
        pathN75.reset()

        val reqList = boostReqHistory.toList()
        val actList = boostActHistory.toList()
        val n75List = n75History.toList()

        val hasValidReq = reqList.any { it > 0f }
        val hasValidAct = actList.any { it > 0f }
        val hasValidN75 = n75List.any { it > 0f }

        var firstReq = true
        var firstAct = true
        var firstN75 = true

        for (i in 0 until count) {
            val x = startOffset + i * stepX

            if (hasValidAct) {
                val act = actList[i]
                val yAct = (h - ((act - minBoost) / boostRange) * h).coerceIn(0f, h)
                if (firstAct) {
                    pathBoostAct.moveTo(x, yAct)
                    firstAct = false
                } else {
                    pathBoostAct.lineTo(x, yAct)
                }
            }

            if (hasValidReq) {
                val req = reqList[i]
                val yReq = (h - ((req - minBoost) / boostRange) * h).coerceIn(0f, h)
                if (firstReq) {
                    pathBoostReq.moveTo(x, yReq)
                    firstReq = false
                } else {
                    pathBoostReq.lineTo(x, yReq)
                }
            }

            if (hasValidN75) {
                val n75 = n75List[i]
                val yN75 = (h - (n75 / 100f) * h).coerceIn(0f, h)
                if (firstN75) {
                    pathN75.moveTo(x, yN75)
                    firstN75 = false
                } else {
                    pathN75.lineTo(x, yN75)
                }
            }
        }

        if (hasValidReq) canvas.drawPath(pathBoostReq, paintBoostReq)
        if (hasValidAct) canvas.drawPath(pathBoostAct, paintBoostAct)
        if (hasValidN75) canvas.drawPath(pathN75, paintN75)

        // Legend
        val legendY = 32f
        if (hasValidReq) canvas.drawText("● Target", w - 340f, legendY, paintLegendReq)
        if (hasValidAct) canvas.drawText("● MAP (abs)", w - 220f, legendY, paintLegendAct)
        if (hasValidN75) canvas.drawText("● N75 %", w - 90f, legendY, paintLegendN75)
    }
}
