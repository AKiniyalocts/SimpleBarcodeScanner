package com.bobekos.bobek.scanner

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.vision.barcode.Barcode
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject


class BarcodeView : FrameLayout {

    private var disposables: CompositeDisposable = CompositeDisposable()

    private var listener: BarcodeListener? = null

    private var isAutoFocus = true

    private var drawOverlay: BarcodeOverlay? = null

    private var previewSize = Size(640, 480)

    private lateinit var observable: Observable<Barcode>

    companion object {
        val overlaySubject: PublishSubject<Rect> = PublishSubject.create<Rect>()
        private val scannerSubject: PublishSubject<Barcode> = PublishSubject.create()
    }

    private val xScaleFactor by lazy {
        width.toFloat().div(Math.min(previewSize.width, previewSize.height))
    }

    private val yScaleFactor by lazy {
        height.toFloat().div(Math.max(previewSize.width, previewSize.height))
    }

    private val cameraView by lazy {
        SurfaceView(context)
    }

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        addView(cameraView)

        cameraView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {

            }

            override fun surfaceDestroyed(p0: SurfaceHolder?) {
                disposables.clear()
            }

            override fun surfaceCreated(p0: SurfaceHolder?) {
                start()

                if (drawOverlay != null) {
                    startOverlay()
                }
            }
        })
    }

    fun setPreviewSize(width: Int, height: Int): BarcodeView {
        previewSize = Size(width, height)

        return this
    }

    fun setAutoFocus(enabled: Boolean): BarcodeView {
        isAutoFocus = enabled

        return this
    }

    fun drawOverlay(overlay: BarcodeOverlay? = BarcodeRectOverlay(context)): BarcodeView {
        drawOverlay = overlay

        return this
    }

    fun addBarcodeListener(l: BarcodeListener) {
        listener = l
    }

    private fun startOverlay() {
        addView(drawOverlay as View, FrameLayout.LayoutParams(width, height))

        disposables.add(
                overlaySubject
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            if (drawOverlay != null) {
                                drawOverlay?.onUpdate(calculateOverlayView(it))
                            }
                        }
        )
    }

    private fun start() {
        val scanner = BarcodeScanner(context, cameraView.holder)
        observable = scanner.getObservable(previewSize)

//        disposables.add(scanner.getObservable(previewSize)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(
//                        {
//                            listener?.onBarcodeDetected(it)
//                        },
//                        {
//                            listener?.onError(it)
//                        }
//                ))
    }

    fun getObservable(): Observable<Barcode> {
        return scannerSubject.flatMap { observable }.subscribeOn(Schedulers.io())
    }

    private fun calculateOverlayView(barcodeRect: Rect): Rect {
        val rect = Rect(barcodeRect)

        return rect.also {
            it.left = translateX(rect.left)
            it.top = translateY(rect.top)
            it.right = translateX(rect.right)
            it.bottom = translateY(rect.bottom)
        }
    }

    private fun translateX(x: Int): Int {
        return (x * xScaleFactor).toInt()
    }

    private fun translateY(y: Int): Int {
        return (y * yScaleFactor).toInt()
    }

    interface BarcodeListener {
        fun onBarcodeDetected(barcode: Barcode) {

        }

        fun onError(throwable: Throwable) {

        }
    }
}