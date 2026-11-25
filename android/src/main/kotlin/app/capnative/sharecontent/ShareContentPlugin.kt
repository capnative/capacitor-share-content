package app.capnative.sharecontent

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@CapacitorPlugin(name = "ShareContent")
class ShareContentPlugin : Plugin() {
    private var receiver: BroadcastReceiver? = null
    private var presenting = false
    private var leftApp = false
    private var expectedNonce: String? = null
    private var chosenPackage: String? = null

    override fun load() {
        val registered = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val nonce = intent.getStringExtra(NONCE) ?: return
                val expected = expectedNonce ?: return
                if (!sameNonce(nonce, expected)) return
                val component = chosenComponent(intent) ?: return
                chosenPackage = component.packageName
                expectedNonce = null
            }
        }
        ContextCompat.registerReceiver(context, registered, IntentFilter(CHOSEN_ACTION), ContextCompat.RECEIVER_EXPORTED)
        receiver = registered
    }

    override fun handleOnStop() {
        super.handleOnStop()
        leftApp = true
    }

    override fun handleOnDestroy() {
        presenting = false
        expectedNonce = null
        receiver?.let { registered ->
            try {
                context.unregisterReceiver(registered)
            } catch (_: IllegalArgumentException) {
                // The receiver was already removed with the context.
            }
        }
        receiver = null
    }

    @PluginMethod
    fun canShare(call: PluginCall) {
        val result = JSObject()
        result.put("value", true)
        call.resolve(result)
    }

    @PluginMethod
    fun share(call: PluginCall) {
        if (presenting) {
            call.reject("Can't share while sharing is in progress", "SHARE_IN_PROGRESS")
            return
        }
        val prepared = try {
            SharePayloadParser.parse(call.data)
        } catch (error: ShareException) {
            call.reject(error.message, error.code)
            return
        }
        val send = try {
            sendIntent(prepared)
        } catch (error: ShareException) {
            call.reject(error.message, error.code)
            return
        }
        if (activity == null) {
            call.reject("Sharing is not available.", "UNAVAILABLE")
            return
        }
        val nonce = UUID.randomUUID().toString()
        expectedNonce = nonce
        chosenPackage = null
        leftApp = false
        val callback = Intent(CHOSEN_ACTION).setPackage(context.packageName).putExtra(NONCE, nonce)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            flags = flags or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        }
        val pending = PendingIntent.getBroadcast(context, 0, callback, flags)
        val chooser = Intent.createChooser(send, prepared.dialogTitle, pending.intentSender)
        chooser.addCategory(Intent.CATEGORY_DEFAULT)
        if (prepared.files.isNotEmpty()) {
            chooser.clipData = send.clipData
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        presenting = true
        try {
            startActivityForResult(call, chooser, "shareActivityResult")
        } catch (error: Exception) {
            presenting = false
            expectedNonce = null
            call.reject(error.message ?: "Sharing is not available.", "UNAVAILABLE")
        }
    }

    @ActivityCallback
    fun shareActivityResult(call: PluginCall, result: ActivityResult) {
        val departed = leftApp
        val chosen = chosenPackage
        presenting = false
        expectedNonce = null
        chosenPackage = null
        leftApp = false
        if (result.resultCode == Activity.RESULT_CANCELED && !departed) {
            call.reject("Share canceled", "SHARE_CANCELED")
            return
        }
        val payload = JSObject()
        payload.put("activityType", chosen ?: "")
        call.resolve(payload)
    }

    private fun sendIntent(prepared: PreparedShare): Intent {
        val send = Intent(if (prepared.files.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
        val message = when {
            prepared.text != null && prepared.url != null -> "${prepared.text} ${prepared.url}"
            prepared.text != null -> prepared.text
            prepared.url != null -> prepared.url
            else -> null
        }
        if (message != null) {
            send.putExtra(Intent.EXTRA_TEXT, message)
            send.type = "text/plain"
        }
        prepared.title?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
        if (prepared.files.isNotEmpty()) {
            attachFiles(send, prepared.files)
        }
        return send
    }

    private fun attachFiles(intent: Intent, files: List<String>) {
        val uris = ArrayList<Uri>(files.size)
        for (fileUrl in files) {
            val path = SharePayloadParser.localPath(fileUrl)
            val file = File(path)
            if (!file.isFile) {
                throw ShareException("INVALID_SHARE", "The file to share does not exist.")
            }
            val uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (_: IllegalArgumentException) {
                throw ShareException(
                    "INVALID_SHARE",
                    "This file is outside the folders the app is allowed to share. Add the folder to file_paths.xml.",
                )
            }
            uris.add(uri)
        }
        intent.type = if (uris.size > 1) "*/*" else mimeType(files.first())
        if (uris.size == 1) {
            intent.putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        val clip = ClipData.newRawUri("shared", uris.first())
        for (uri in uris.drop(1)) {
            clip.addItem(ClipData.Item(uri))
        }
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun mimeType(fileUrl: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileUrl).lowercase()
        if (extension.isEmpty()) return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    private fun chosenComponent(intent: Intent): ComponentName? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        }
    }

    private fun sameNonce(received: String, expected: String): Boolean {
        return MessageDigest.isEqual(received.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val CHOSEN_ACTION = "app.capnative.sharecontent.CHOSEN"
        private const val NONCE = "app.capnative.sharecontent.NONCE"
    }
}
