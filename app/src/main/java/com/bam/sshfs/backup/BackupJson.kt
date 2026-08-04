package com.bam.sshfs.backup

import com.bam.sshfs.data.model.KeyOrigin
import com.bam.sshfs.data.model.KeyType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The backup document's JSON encoding — the plaintext that [BackupCrypto] seals.
 *
 * JSON rather than a Room dump so a backup outlives the schema it came from: a
 * missing optional field reads back as its default instead of failing the restore.
 * Absent values are written as JSON `null` (not omitted) so the file is readable by
 * eye, which matters for the config-only variant that ships unencrypted.
 */
object BackupJson {

    fun encode(document: BackupDocument): String = JSONObject().apply {
        put("version", document.version)
        put("createdAt", document.createdAt)
        put("keys", JSONArray(document.keys.map(::keyToJson)))
        put("identities", JSONArray(document.identities.map(::identityToJson)))
        put("hosts", JSONArray(document.hosts.map(::hostToJson)))
    }.toString(2)

    /**
     * Parse a document written by [encode].
     *
     * @throws BackupFormatException on malformed JSON, a missing required field, or a
     *   `version` this build doesn't know how to read.
     */
    fun decode(text: String): BackupDocument = try {
        val root = JSONObject(text)
        val version = root.optInt("version", BackupDocument.FORMAT_VERSION)
        if (version > BackupDocument.FORMAT_VERSION) {
            throw BackupFormatException(
                "This backup was written by a newer version of the app (format $version).",
            )
        }
        BackupDocument(
            version = version,
            createdAt = root.optLong("createdAt"),
            keys = root.array("keys").map(::keyFromJson),
            identities = root.array("identities").map(::identityFromJson),
            hosts = root.array("hosts").map(::hostFromJson),
        )
    } catch (e: JSONException) {
        throw BackupFormatException("The backup file is not in the expected format.", e)
    }

    private fun keyToJson(key: BackupKey) = JSONObject().apply {
        put("id", key.id)
        put("name", key.name)
        put("type", key.type.name)
        put("privateKey", key.privateKey)
        put("publicKey", key.publicKey)
        put("hasPassphrase", key.hasPassphrase)
        put("passphrase", key.passphrase ?: JSONObject.NULL)
        put("origin", key.origin.name)
        put("createdAt", key.createdAt)
    }

    private fun keyFromJson(json: JSONObject) = BackupKey(
        id = json.getLong("id"),
        name = json.getString("name"),
        type = json.enum("type", KeyType.entries, KeyType.ED25519),
        privateKey = json.optString("privateKey"),
        publicKey = json.optString("publicKey"),
        hasPassphrase = json.optBoolean("hasPassphrase"),
        passphrase = json.stringOrNull("passphrase"),
        origin = json.enum("origin", KeyOrigin.entries, KeyOrigin.IMPORTED),
        createdAt = json.optLong("createdAt"),
    )

    private fun identityToJson(identity: BackupIdentity) = JSONObject().apply {
        put("id", identity.id)
        put("name", identity.name)
        put("username", identity.username)
        put("password", identity.password ?: JSONObject.NULL)
        put("keyId", identity.keyId ?: JSONObject.NULL)
        put("createdAt", identity.createdAt)
    }

    private fun identityFromJson(json: JSONObject) = BackupIdentity(
        id = json.getLong("id"),
        name = json.getString("name"),
        username = json.optString("username"),
        password = json.stringOrNull("password"),
        keyId = json.longOrNull("keyId"),
        createdAt = json.optLong("createdAt"),
    )

    private fun hostToJson(host: BackupHost) = JSONObject().apply {
        put("id", host.id)
        put("name", host.name)
        put("address", host.address)
        put("port", host.port)
        put("defaultIdentityId", host.defaultIdentityId ?: JSONObject.NULL)
        put("remoteRoot", host.remoteRoot)
        put("extraArgs", host.extraArgs)
        put("createdAt", host.createdAt)
    }

    private fun hostFromJson(json: JSONObject) = BackupHost(
        id = json.getLong("id"),
        name = json.getString("name"),
        address = json.optString("address"),
        port = json.optInt("port", com.bam.sshfs.data.model.DEFAULT_SSH_PORT),
        defaultIdentityId = json.longOrNull("defaultIdentityId"),
        remoteRoot = json.optString("remoteRoot", "."),
        extraArgs = json.optString("extraArgs"),
        createdAt = json.optLong("createdAt"),
    )

    private fun JSONObject.array(name: String): List<JSONObject> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name)

    private fun JSONObject.longOrNull(name: String): Long? =
        if (isNull(name)) null else optLong(name)

    /** Unknown enum names fall back rather than failing the whole restore. */
    private fun <E : Enum<E>> JSONObject.enum(name: String, values: List<E>, fallback: E): E {
        val raw = optString(name)
        return values.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
    }
}
