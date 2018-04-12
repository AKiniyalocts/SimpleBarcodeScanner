package com.bobekos.bobek.scanner

import android.content.Context
import android.graphics.Rect
import android.hardware.Camera
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import com.bobekos.bobek.scanner.overlay.BarcodeOverlay
import com.bobekos.bobek.scanner.overlay.BarcodeRectOverlay
import com.bobekos.bobek.scanner.overlay.Optional
import com.bobekos.bobek.scanner.scanner.BarcodeScanner
import com.bobekos.bobek.scanner.scanner.BarcodeScannerConfig
import com.bobekos.bobek.scanner.scanner.Size
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.barcode.Barcode
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject


class BarcodeView : FrameLayout {

    private var overlayDisposable: Disposable? = null

    private var drawOverlay: BarcodeOverlay? = null

    private val config by lazy {
        BarcodeScannerConfig()
    }

    companion object {
        val overlaySubject: PublishSubject<Optional<Barcode>> = PublishSubject.create<Optional<Barcode>>()
    }

    private val xScaleFactor by lazy {
        width.toFloat().div(Math.min(config.previewSize.width, config.previewSize.height))
    }

    private val yScaleFactor by lazy {
        height.toFloat().div(Math.max(config.previewSize.width, config.previewSize.height))
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
    }

    //region public
    fun getObservable(): Observable<Barcode> {
        return getSurfaceObservable()
                .flatMap { BarcodeScanner(context, cameraView.holder, config, it).getObservable() }
                .subscribeOn(Schedulers.io())
    }

    fun setPreviewSize(width: Int, height: Int): BarcodeView {
        config.previewSize = Size(width, height)

        return this
    }

    fun setAutoFocus(enabled: Boolean): BarcodeView {
        config.isAutoFocus = enabled

        return this
    }

    fun setBarcodeFormats(vararg formats: Int): BarcodeView {
        config.barcodeFormat = formats.sum()

        return this
    }

    fun setFacing(facing: Int): BarcodeView {
        config.facing = facing

        return this
    }

    fun drawOverlay(overlay: BarcodeOverlay? = BarcodeRectOverlay(context)): BarcodeView {
        drawOverlay = overlay
        config.drawOverLay = true

        return this
    }

    fun setFlash(enabled: Boolean): BarcodeView {
        config.useFlash = enabled

        return this
    }
    //endregion

    //region private
    private fun getSurfaceObservable(): Observable<Boolean> {
        return Observable.create<Boolean> { emitter ->
            cameraView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {

                }

                override fun surfaceDestroyed(holder: SurfaceHolder?) {
                    overlayDisposable?.dispose()

                    if (!emitter.isDisposed) {
                        emitter.onNext(false)
                    }
                }

                override fun surfaceCreated(holder: SurfaceHolder?) {
                    loadCameraSettings()

                    if (drawOverlay != null) {
                        startOverlay()
                    }

                    if (holder != null && !emitter.isDisposed) {
                        emitter.onNext(true)
                    }
                }
            })
        }
    }


    private fun startOverlay() {
        removeView(drawOverlay as View)
        addView(drawOverlay as View, FrameLayout.LayoutParams(width, height))

        overlayDisposable = overlaySubject
                .observeOn(AndroidSchedulers.mainThread())
                .filter { drawOverlay != null }
                .subscribe(
                        { result ->
                            drawOverlay?.let { overlay ->
                                when (result) {
                                    is Optional.Some -> {
                                        overlay.onUpdate(
                                                calculateOverlayView(result.element.boundingBox),
                                                result.element.displayValue)
                                    }
                                    is Optional.None -> {
                                        overlay.onUpdate()
                                    }
                                }

                                if (isFacingFront() && overlay is View) {
                                    (overlay as View).scaleX = -1f
                                }
                            }
                        },
                        {
                            drawOverlay?.onUpdate(Rect())
                        })
    }

    private fun loadCameraSettings() {
        val cameraId = getCameraIdByFacing()
        if (cameraId == -1) {
            throw NullPointerException("Could not find camera for selected facing")
        }

        try {
            val camera = Camera.open(cameraId)
            config.previewSize = getValidPreviewSize(camera)

            camera.release()
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
    }

    private fun getCameraIdByFacing(): Int {
        val cameraInfo = Camera.CameraInfo()
        for (i in 0..Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == config.facing) {
                return i
            }
        }

        return -1
    }

    private fun isFacingFront(): Boolean {
        return config.facing == CameraSource.CAMERA_FACING_FRONT
    }

    private fun getValidPreviewSize(camera: Camera): Size {
        val supportedPreviewSize = camera.parameters.supportedPreviewSizes

        var result = config.previewSize
        var minDiff = Int.MAX_VALUE

        supportedPreviewSize.forEach {
            val diff = Math.abs(it.width - width) +
                    Math.abs(it.height - height)
            if (diff < minDiff) {
                result = Size(it.width, it.height)
                minDiff = diff
            }
        }

        return result
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
    //endregion
}