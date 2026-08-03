package com.bam.sshfs.ui.identities

import com.bam.sshfs.data.model.Identity

/** Why a draft identity can't be saved yet. */
enum class IdentityFormError { BLANK_NAME, BLANK_USERNAME, NO_CREDENTIAL }

/**
 * The editable state of one identity.
 *
 * A stored password is never read back into the UI, so [password] carries an
 * *intent* rather than the current value: `null` leaves the stored secret
 * untouched, blank clears it, anything else replaces it.
 */
data class IdentityForm(
    val id: Long = 0,
    val name: String = "",
    val username: String = "",
    val keyId: Long? = null,
    val password: String? = null,
    /** Whether the stored row already holds a password. */
    val hadPassword: Boolean = false,
) {
    /** Whether the identity will have a password once this draft is saved. */
    val willHavePassword: Boolean get() = password?.isNotEmpty() ?: hadPassword

    /** The first problem blocking a save, or null when the draft is valid. */
    fun validate(): IdentityFormError? = when {
        name.isBlank() -> IdentityFormError.BLANK_NAME
        username.isBlank() -> IdentityFormError.BLANK_USERNAME
        !willHavePassword && keyId == null -> IdentityFormError.NO_CREDENTIAL
        else -> null
    }

    companion object {
        /** Start a draft from a stored identity; the password stays behind. */
        fun of(identity: Identity) = IdentityForm(
            id = identity.id,
            name = identity.name,
            username = identity.username,
            keyId = identity.keyId,
            password = null,
            hadPassword = identity.passwordCiphertext != null,
        )
    }
}
