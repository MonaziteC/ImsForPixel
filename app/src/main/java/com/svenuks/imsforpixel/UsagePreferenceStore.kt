package com.svenuks.imsforpixel

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Original preferences survive keeper death. This directory is excluded from device backups. */
internal class UsagePreferenceStore(context: Context) : UsagePreferenceExperiment.Journal {
    private val file = AtomicFile(File(GuardianStore.root(context), "usage-originals.json"))
    private fun read(): JSONObject {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return JSONObject()
        return JSONObject(file.readFully().toString(Charsets.UTF_8)).also { data ->
            for (key in data.keys()) {
                require(key.matches(Regex("[a-f0-9]{64}")) && data.getInt(key) in 0..2) {
                    "Invalid original preference record"
                }
            }
        }
    }
    private fun write(data: JSONObject) {
        val stream = file.startWrite()
        try { stream.write(data.toString().toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    override fun original(identity: String): Int? = read().let { if (it.has(identity)) it.getInt(identity) else null }
    override fun save(identity: String, value: Int) {
        require(identity.matches(Regex("[a-f0-9]{64}")) && value in 0..2)
        val data = read()
        if (!data.has(identity)) write(data.put(identity, value))
    }
    override fun remove(identity: String) { write(read().apply { remove(identity) }) }
    fun pendingCount() = runCatching { read().length() }.getOrDefault(-1)
}
