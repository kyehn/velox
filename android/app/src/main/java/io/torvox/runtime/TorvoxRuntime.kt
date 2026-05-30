package io.torvox.runtime

import android.content.Context
import android.view.Surface
import dagger.hilt.android.qualifiers.ApplicationContext
import io.torvox.bridge.TorvoxBridge
import io.torvox.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class RuntimeState(
    val isRunning: Boolean = false,
    val title: String = "Torvox",
    val rows: Int = 24,
    val cols: Int = 80,
)

@Singleton
class TorvoxRuntime
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _state = MutableStateFlow(RuntimeState())
        val state: StateFlow<RuntimeState> = _state.asStateFlow()

        private var bridge: TorvoxBridge? = null
        private var renderThread: Thread? = null
        private var running = false

        suspend fun start(
            surface: Surface,
            width: Int,
            height: Int,
        ) {
            if (running) return

            val config =
                io.torvox.bridge.TerminalConfig(
                    shell = io.torvox.bridge.Shell.SystemDefault,
                    rows = 24u,
                    cols = 80u,
                    scrollbackLines = 50000u,
                )

            bridge = TorvoxBridge(config)

            val windowPtr = getNativeWindowPtr(surface)
            bridge?.setNativeWindow(windowPtr)

            val cellWidth = 8f
            val cellHeight = 16f
            val cols = (width / cellWidth).toInt().coerceIn(20, 300)
            val rows = (height / cellHeight).toInt().coerceIn(5, 100)

            bridge?.resize(rows.toUInt(), cols.toUInt())

            running = true
            _state.value =
                RuntimeState(
                    isRunning = true,
                    rows = rows,
                    cols = cols,
                )

            renderThread =
                Thread({
                    while (running) {
                        try {
                            bridge?.render()
                            Thread.sleep(16)
                        } catch (_: Exception) {
                            break
                        }
                    }
                }, "TorvoxRender").apply {
                    isDaemon = true
                    start()
                }
        }

        fun stop() {
            running = false
            renderThread?.join(1000)
            renderThread = null
            bridge?.releaseSurface()
            bridge = null
            _state.value = RuntimeState()
        }

        fun resize(
            rows: Int,
            cols: Int,
        ) {
            bridge?.resize(rows.toUInt(), cols.toUInt())
            _state.value = _state.value.copy(rows = rows, cols = cols)
        }

        fun destroy() {
            stop()
            scope.cancel()
        }

        private fun getNativeWindowPtr(surface: Surface): Long {
            val method = surface.javaClass.getMethod("getNativeWindow")
            return method.invoke(surface) as Long
        }
    }
