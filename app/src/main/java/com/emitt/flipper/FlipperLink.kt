package com.emitt.flipper

import com.emitt.R
import com.emitt.data.UiText
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat

import com.emitt.data.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class FoundFlipper(val name: String, val address: String, val bonded: Boolean, val rssi: Int?)

sealed interface FlipperState {
    data object Idle : FlipperState
    data class Connecting(val name: String, val step: UiText) : FlipperState
    data class Ready(val name: String, val address: String, val info: Map<String, String>) : FlipperState
    data class Failed(val reason: UiText) : FlipperState
}

/** [status] is the Flipper's PB.CommandStatus when the error came from the Flipper itself. */
class FlipperException(val text: UiText, val status: Int = 0) : Exception("Flipper error ${text.id} $status")

/**
 * The Bluetooth LE link to a Flipper Zero. It speaks the Flipper's RPC over its serial GATT service
 * (firmware targets/f7/ble_glue/services/serial_service.c): we write frames to RX, the Flipper
 * answers on TX by indication, and the flow-control characteristic says how much it can buffer.
 */
@SuppressLint("MissingPermission") // The Flipper screen asks for Bluetooth permissions before using any of this
object FlipperLink {
    private val SERVICE = UUID.fromString("8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000")
    private val TX = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e61fe0000")
    private val RX = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e62fe0000")
    private val FLOW = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e63fe0000")
    private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Bytes of file data per write request, as the official app uses. */
    private const val FILE_CHUNK = 512

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<FlipperState>(FlipperState.Idle)
    val state: StateFlow<FlipperState> = _state.asStateFlow()
    private val _found = MutableStateFlow<List<FoundFlipper>>(emptyList())
    val found: StateFlow<List<FoundFlipper>> = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private val gattLock = Mutex()
    private val rpcLock = Mutex()
    private var pendingGatt: CompletableDeferred<Any?>? = null
    private val freeBytes = AtomicInteger(0)
    private val flowSignal = Channel<Unit>(Channel.CONFLATED)
    // Start high and random: if another app shares the link, our command ids won't collide with its
    private val nextId = AtomicInteger(kotlin.random.Random.nextInt(10_000, 60_000))
    private var otherAppConnected = false
    var verboseScan = false
    /**
     * Device objects from Flipper advertisements, by address. Custom firmware (Momentum, etc.) can
     * change the Flipper's Bluetooth name and address, so a stored pairing may point at an address the
     * Flipper no longer uses; only an address seen advertising is worth dialling.
     */
    private val advertising = ConcurrentHashMap<String, BluetoothDevice>()

    /** Flippers advertise service 0x3080 + body colour (0x3081 black, 0x3082 white, 0x3083 clear). */
    private fun isFlipperUuid(u: android.os.ParcelUuid) = u.uuid.toString().let { it.startsWith("0000308") && it.endsWith("-0000-1000-8000-00805f9b34fb") }

    private fun looksLikeFlipper(name: String?, uuids: List<android.os.ParcelUuid>?) =
        uuids.orEmpty().any(::isFlipperUuid) || name?.startsWith("Flipper", ignoreCase = true) == true

    private fun log(msg: String) = android.util.Log.i("EMITT/Flipper", msg)
    private val pendingRpc = ConcurrentHashMap<Int, Pair<MutableList<FlipperProto.Main>, CompletableDeferred<List<FlipperProto.Main>>>>()
    private var inBuf = ByteArray(4096)
    private var inLen = 0

    // ---- Scanning ----

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val advName = result.scanRecord?.deviceName
            val uuids = result.scanRecord?.serviceUuids
            if (verboseScan) log("adv ${result.device.address} name=$advName uuids=$uuids")
            if (!looksLikeFlipper(advName ?: result.device.name, uuids)) return
            val d = result.device
            advertising[d.address] = d
            val f = FoundFlipper(advName ?: d.name ?: "Flipper", d.address, d.bondState == BluetoothDevice.BOND_BONDED, result.rssi)
            // Anything advertising now goes to the top; paired-but-silent entries stay below
            _found.value = (_found.value.filterNot { it.address == f.address } + f)
                .sortedWith(compareByDescending<FoundFlipper> { it.rssi != null }.thenByDescending { it.rssi ?: -999 })
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    fun startScan(context: Context) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) {
            _state.value = FlipperState.Failed(UiText(R.string.fl_bt_off))
            return
        }
        // Paired Flippers show up straight away (marked not in range until they advertise)
        advertising.clear()
        _found.value = adapter.bondedDevices.orEmpty()
            .filter { looksLikeFlipper(it.name, it.uuids?.toList()) }
            .map { FoundFlipper(it.name ?: "Flipper", it.address, true, null) }
        val scanner = adapter.bluetoothLeScanner ?: return
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        _scanning.value = true
        scope.launch {
            delay(15_000)
            stopScan(context)
        }
    }

    fun stopScan(context: Context) {
        if (!_scanning.value) return
        runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
    }

    // ---- Connection ----

    suspend fun connect(context: Context, address: String, name: String) {
        stopScan(context)
        disconnect()
        otherAppConnected = false
        val app = context.applicationContext
        val bm = app.getSystemService(BluetoothManager::class.java)
        try {
            // Make sure it's actually advertising at this address (a few seconds at most)
            if (!advertising.containsKey(address)) {
                _state.value = FlipperState.Connecting(name, UiText(R.string.fl_step_looking))
                bm.adapter.bluetoothLeScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
                withTimeoutOrNull(6_000) { while (!advertising.containsKey(address)) delay(100) }
                runCatching { bm.adapter.bluetoothLeScanner?.stopScan(scanCallback) }
            }
            val busy = bm.getConnectedDevices(BluetoothProfile.GATT).firstOrNull { it.address == address }
            val device = advertising[address] ?: busy ?: throw FlipperException(
                if (advertising.isNotEmpty()) UiText(R.string.fl_not_in_range)
                else UiText(R.string.fl_cant_see),
            )
            log("connect ${device.address} '$name' bond=${device.bondState} advertising=${advertising.containsKey(address)}")
            if (busy != null) {
                log("already connected at the BLE level (another app?)")
                otherAppConnected = true
            }
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                _state.value = FlipperState.Connecting(name, UiText(R.string.fl_step_pairing))
                bond(app, device)
                log("bonded")
            }
            _state.value = FlipperState.Connecting(name, UiText(R.string.fl_step_connecting))
            val connected = CompletableDeferred<Any?>()
            pendingGatt = connected
            gatt = device.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
            withTimeout(20_000) { connected.await() }
            log("gatt connected")

            _state.value = FlipperState.Connecting(name, UiText(R.string.fl_step_setup))
            gattOp { gatt!!.requestMtu(512) }
            log("mtu=$mtu")
            gattOp { gatt!!.discoverServices() }
            log("services: ${gatt!!.services.map { it.uuid }}")
            val svc = gatt!!.getService(SERVICE) ?: throw FlipperException(UiText(R.string.fl_not_flipper))
            rx = svc.getCharacteristic(RX)
            val tx = svc.getCharacteristic(TX)
            val flow = svc.getCharacteristic(FLOW)
            subscribe(tx, indicate = true)
            subscribe(flow, indicate = false)
            log("subscribed")
            val initial = gattOp { readCharacteristic(flow) } as? ByteArray
            freeBytes.set(initial?.let(::u32) ?: 1024)
            log("flow control free=${freeBytes.get()}")

            _state.value = FlipperState.Connecting(name, UiText(R.string.fl_step_info))
            val info = deviceInfo()
            log("device info: ${info.size} keys")
            val stable = device.address
            Settings(app).apply { flipperAddress = stable; flipperName = name }
            _state.value = FlipperState.Ready(info["hardware_name"]?.let { "Flipper $it" } ?: name, stable, info)
        } catch (e: Exception) {
            log("connect failed: $e")
            if (otherAppConnected && e !is FlipperException) {
                disconnect()
                _state.value = FlipperState.Failed(UiText(R.string.fl_other_app))
                return
            }
            disconnect()
            _state.value = FlipperState.Failed(
                when (e) {
                    is FlipperException -> e.text
                    is kotlinx.coroutines.TimeoutCancellationException -> UiText(R.string.fl_timeout)
                    else -> UiText(R.string.fl_failed)
                },
            )
        }
    }

    fun disconnect() {
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        rx = null
        pendingGatt?.completeExceptionally(FlipperException(UiText(R.string.fl_disconnected)))
        pendingGatt = null
        pendingRpc.values.forEach { it.second.completeExceptionally(FlipperException(UiText(R.string.fl_disconnected))) }
        pendingRpc.clear()
        inLen = 0
        if (_state.value !is FlipperState.Failed) _state.value = FlipperState.Idle
    }

    private suspend fun bond(context: Context, device: BluetoothDevice) {
        val done = CompletableDeferred<Int>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                @Suppress("DEPRECATION")
                val d: BluetoothDevice? = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (d?.address != device.address) return
                when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                    BluetoothDevice.BOND_BONDED -> done.complete(BluetoothDevice.BOND_BONDED)
                    BluetoothDevice.BOND_NONE -> done.complete(BluetoothDevice.BOND_NONE)
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            if (!device.createBond()) throw FlipperException(UiText(R.string.fl_pair_start))
            val result = withTimeoutOrNull(90_000) { done.await() }
            if (result != BluetoothDevice.BOND_BONDED) throw FlipperException(UiText(R.string.fl_pair_failed))
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private suspend fun subscribe(c: BluetoothGattCharacteristic, indicate: Boolean) {
        val g = gatt ?: throw FlipperException(UiText(R.string.fl_not_connected))
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(CCCD) ?: return
        val value = if (indicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gattOp {
            if (Build.VERSION.SDK_INT >= 33) {
                writeDescriptor(d, value) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                d.value = value
                @Suppress("DEPRECATION")
                writeDescriptor(d)
            }
        }
    }

    /** Runs one GATT operation and waits for its callback; Android allows only one in flight. */
    private suspend fun gattOp(start: BluetoothGatt.() -> Boolean): Any? = gattLock.withLock {
        val g = gatt ?: throw FlipperException(UiText(R.string.fl_not_connected))
        val d = CompletableDeferred<Any?>()
        pendingGatt = d
        if (!g.start()) {
            log("gatt op refused")
            throw FlipperException(UiText(R.string.fl_refused))
        }
        withTimeout(10_000) { d.await() }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            log("connection state=$newState status=$status")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> pendingGatt?.complete(null)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasReady = _state.value is FlipperState.Ready
                    disconnect()
                    if (wasReady) _state.value = FlipperState.Failed(UiText(R.string.fl_dropped))
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu
            pendingGatt?.complete(null)
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            pendingGatt?.complete(null)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            log("descriptor write ${d.characteristic.uuid} status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) pendingGatt?.complete(null)
            else pendingGatt?.completeExceptionally(FlipperException(UiText(R.string.fl_subscribe, status)))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) pendingGatt?.complete(null)
            else pendingGatt?.completeExceptionally(FlipperException(UiText(R.string.fl_write, status)))
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            pendingGatt?.complete(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33) pendingGatt?.complete(c.value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) =
            changed(c.uuid, value)

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33) changed(c.uuid, c.value)
        }
    }

    private fun changed(uuid: UUID, value: ByteArray) {
        when (uuid) {
            FLOW -> { freeBytes.set(u32(value)); flowSignal.trySend(Unit) }
            TX -> synchronized(this) {
                if (inLen + value.size > inBuf.size) inBuf = inBuf.copyOf(maxOf(inBuf.size * 2, inLen + value.size))
                System.arraycopy(value, 0, inBuf, inLen, value.size)
                inLen += value.size
                val (frames, used) = FlipperProto.drain(inBuf, inLen)
                if (used > 0) {
                    System.arraycopy(inBuf, used, inBuf, 0, inLen - used)
                    inLen -= used
                }
                frames.forEach(::dispatch)
            }
        }
    }

    private fun dispatch(m: FlipperProto.Main) {
        log("rx frame id=${m.commandId} status=${m.status} field=${m.field} next=${m.hasNext}")
        val (parts, done) = pendingRpc[m.commandId] ?: return
        parts += m
        if (!m.hasNext) {
            pendingRpc.remove(m.commandId)
            done.complete(parts)
        }
    }

    private fun u32(b: ByteArray): Int =
        if (b.size < 4) 0 else (b[0].toInt() and 0xFF shl 24) or (b[1].toInt() and 0xFF shl 16) or (b[2].toInt() and 0xFF shl 8) or (b[3].toInt() and 0xFF)

    // ---- RPC ----

    /** Sends a request (in one or more frames sharing a command id) and collects the full response. */
    private suspend fun rpc(field: Int, bodies: List<ByteArray>, timeoutMs: Long = 15_000, progress: (Float) -> Unit = {}): List<FlipperProto.Main> =
        rpcLock.withLock {
            val id = nextId.getAndIncrement()
            val done = CompletableDeferred<List<FlipperProto.Main>>()
            pendingRpc[id] = mutableListOf<FlipperProto.Main>() to done
            bodies.forEachIndexed { i, body ->
                send(FlipperProto.frame(id, field, body, hasNext = i < bodies.lastIndex))
                progress((i + 1f) / bodies.size)
            }
            val parts = withTimeout(timeoutMs) { done.await() }
            parts.firstOrNull { it.status != FlipperProto.OK }?.let {
                throw FlipperException(UiText(FlipperProto.statusNames[it.status] ?: R.string.fl_error, it.status), it.status)
            }
            parts
        }

    /** Writes [frame] to RX in pieces that fit the MTU and the Flipper's free buffer. */
    private suspend fun send(frame: ByteArray) {
        val c = rx ?: throw FlipperException(UiText(R.string.fl_not_connected))
        log("tx frame ${frame.size} bytes, free=${freeBytes.get()}")
        var off = 0
        while (off < frame.size) {
            while (freeBytes.get() <= 0) {
                withTimeoutOrNull(5_000) { flowSignal.receive() } ?: throw FlipperException(UiText(R.string.fl_stalled))
            }
            val n = minOf(frame.size - off, mtu - 3, 243, freeBytes.get())
            val piece = frame.copyOfRange(off, off + n)
            gattOp {
                if (Build.VERSION.SDK_INT >= 33) {
                    writeCharacteristic(c, piece, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    c.value = piece
                    @Suppress("DEPRECATION")
                    writeCharacteristic(c)
                }
            }
            freeBytes.addAndGet(-n)
            off += n
        }
    }

    suspend fun deviceInfo(): Map<String, String> =
        rpc(FlipperProto.DEVICE_INFO_REQUEST, listOf(FlipperProto.empty()))
            .filter { it.field == FlipperProto.DEVICE_INFO_RESPONSE }
            .associate { FlipperProto.parseDeviceInfo(it.body) }

    suspend fun list(path: String): List<FlipperProto.StorageFile> =
        rpc(FlipperProto.LIST_REQUEST, listOf(FlipperProto.pathRequest(path)))
            .filter { it.field == FlipperProto.LIST_RESPONSE }
            .flatMap { FlipperProto.parseList(it.body) }

    /** Total and free bytes on the SD card. */
    suspend fun sdInfo(): Pair<Long, Long> =
        rpc(FlipperProto.INFO_REQUEST, listOf(FlipperProto.pathRequest("/ext")))
            .firstOrNull { it.field == FlipperProto.INFO_RESPONSE }?.let { FlipperProto.parseInfo(it.body) } ?: (0L to 0L)

    suspend fun mkdirs(path: String) {
        try {
            rpc(FlipperProto.MKDIR_REQUEST, listOf(FlipperProto.pathRequest(path)))
        } catch (e: FlipperException) {
            if (e.status != FlipperProto.ERROR_STORAGE_EXIST) throw e
        }
    }

    /** Writes [data] to [path] on the Flipper (overwriting), creating the folder first. */
    suspend fun writeFile(path: String, data: ByteArray, progress: (Float) -> Unit = {}) {
        mkdirs(path.substringBeforeLast('/'))
        val chunks = if (data.isEmpty()) listOf(ByteArray(0)) else data.toList().chunked(FILE_CHUNK).map { it.toByteArray() }
        rpc(FlipperProto.WRITE_REQUEST, chunks.map { FlipperProto.writeRequest(path, it) }, timeoutMs = 30_000, progress = progress)
    }

    /** Makes the Flipper beep and flash, to find it or check the link. */
    suspend fun alert() {
        rpc(FlipperProto.ALERT_REQUEST, listOf(FlipperProto.empty()))
    }

    fun isReady() = _state.value is FlipperState.Ready
}
