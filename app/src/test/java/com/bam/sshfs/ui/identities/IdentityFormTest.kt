package com.bam.sshfs.ui.identities

import com.bam.sshfs.data.model.Identity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityFormTest {

    private fun valid() = IdentityForm(name = "Work", username = "bam", password = "hunter2")

    @Test
    fun `a named identity with a password is valid`() {
        assertNull(valid().validate())
    }

    @Test
    fun `a key alone is credential enough`() {
        assertNull(IdentityForm(name = "Work", username = "bam", keyId = 3).validate())
    }

    @Test
    fun `name and username are required`() {
        assertEquals(IdentityFormError.BLANK_NAME, valid().copy(name = "  ").validate())
        assertEquals(IdentityFormError.BLANK_USERNAME, valid().copy(username = "").validate())
    }

    @Test
    fun `neither password nor key is rejected`() {
        assertEquals(
            IdentityFormError.NO_CREDENTIAL,
            IdentityForm(name = "Work", username = "bam").validate(),
        )
    }

    @Test
    fun `an untouched password field keeps the stored secret`() {
        val form = IdentityForm(name = "Work", username = "bam", hadPassword = true)
        assertTrue(form.willHavePassword)
        assertNull(form.validate())
    }

    @Test
    fun `clearing the only credential is rejected`() {
        val form = IdentityForm(name = "Work", username = "bam", hadPassword = true, password = "")
        assertFalse(form.willHavePassword)
        assertEquals(IdentityFormError.NO_CREDENTIAL, form.validate())
    }

    @Test
    fun `editing a stored identity never loads its password`() {
        val identity = Identity(
            id = 7,
            name = "Work",
            username = "bam",
            passwordCiphertext = "cipher",
            keyId = 2,
            createdAt = 0,
        )
        val form = IdentityForm.of(identity)
        assertEquals(7L, form.id)
        assertEquals(2L, form.keyId)
        assertNull(form.password)
        assertTrue(form.hadPassword)
    }
}
