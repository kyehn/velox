package io.torvox.ui

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import io.torvox.bridge.Shell
import io.torvox.bridge.TerminalConfig
import io.torvox.bridge.TorvoxBridge

/**
 * SurfaceView that hosts the Rust wgpu rendering pipeline.
 * Manages the Surface lifecycle and passes ANativeWindow to Rust.
 */
class TerminalSurface
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : SurfaceView(context, attrs, defStyleAttr),
        SurfaceHolder.Callback {
        private var bridge: TorvoxBridge? = null
        private var rows: Int = 24
        private var cols: Int = 80

        init {
            holder.addCallback(this)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        fun initialize(config: TerminalConfig) {
            this.rows = config.rows.toInt()
            this.cols = config.cols.toInt()
            this.bridge = TorvoxBridge(config)
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            val nativeWindow = getNativeWindowPtr(holder)
            if (nativeWindow != 0L) {
                bridge?.setNativeWindow(nativeWindow)
            }
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            bridge?.resize(rows.toUInt(), cols.toUInt())
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            bridge?.releaseSurface()
        }

        fun getBridge(): TorvoxBridge? = bridge

        fun requestRender() {
            bridge?.render()
        }

        private external fun getNativeWindowPtr(holder: SurfaceHolder): Long

        companion object {
            init {
                System.loadLibrary("torvox_android")
            }
        }
    }
