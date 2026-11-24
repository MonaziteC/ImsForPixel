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
                require(key.matches(Regex("[a-f0-9]{64}"))) {
                    "Invalid original preference record"
                }
                record(data, key)
            }
        }
    }
    private fun write(data: JSONObject) {
        val stream = file.startWrite()
        try { stream.write(data.toString().toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    private fun record(data: JSONObject, identity: String): Pair<Int, Set<Int>> {
        val raw = data.get(identity)
        // Migrate data-test1..5 receipts: their only app-written mode was 2.
        if (raw is Int && raw in 0..2) return raw to setOf(2)
        val row = raw as? JSONObject ?: error("Invalid preference receipt")
        val original = row.getInt("original")
        val values = row.getJSONArray("owned")
        val owned = (0 until values.length()).map { values.getInt(it) }.toSet()
        require(original in 0..2 && owned.isNotEmpty() && owned.all { it in 0..2 })
        return original to owned
    }
    override fun original(identity: String): Int? = read().let { if (it.has(identity)) record(it, identity).first else null }
    override fun owns(identity: String, value: Int): Boolean = read().let { it.has(identity) && value in record(it, identity).second }
    override fun save(identity: String, value: Int, target: Int) {
        require(identity.matches(Regex("[a-f0-9]{64}")) && value in 0..2 && target in 0..2)
        val data = read()
        val old = if (data.has(identity)) record(data, identity) else value to emptySet()
        val row = JSONObject().put("original", old.first)
            .put("owned", org.json.JSONArray((old.second + target).sorted()))
        write(data.put(identity, row))
    }
    override fun remove(identity: String) { write(read().apply { remove(identity) }) }
    fun pendingCount() = runCatching { read().length() }.getOrDefault(-1)
}
