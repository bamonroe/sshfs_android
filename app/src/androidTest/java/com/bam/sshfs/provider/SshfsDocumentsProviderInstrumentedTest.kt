package com.bam.sshfs.provider

import android.content.ContentResolver
import android.database.Cursor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bam.sshfs.data.model.Host
import com.bam.sshfs.net.ConnectionManager
import com.bam.sshfs.net.SafRoots
import java.io.FileNotFoundException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the `DocumentsProvider` the way the file picker does — through
 * `ContentResolver` and `DocumentsContract`, across a binder.
 *
 * Nothing here calls the provider class directly: the contract SAF actually holds
 * us to is the cursors, the document ids, and the descriptors that come back out
 * of the resolver, and only a real binder round trip exercises those.
 */
@RunWith(AndroidJUnit4::class)
class SshfsDocumentsProviderInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val resolver: ContentResolver get() = context.contentResolver

    private val host = Host(id = 4242, name = "test-box", address = "127.0.0.1", createdAt = 0)
    private val remote = FakeSftpSession()
    private val rootId = DocumentId(host.id, "/").toString()

    @Before
    fun publishAFakeRoot() {
        remote.putDirectory("/sub")
        remote.putFile("/hello.txt", "hello there")
        ConnectionManager.get(context).adopt(host, "/", remote)
    }

    @After
    fun dropTheRoot() {
        ConnectionManager.get(context).disconnect(host.id)
    }

    @Test
    fun theConnectedHostAppearsAsARoot() {
        resolver.query(SafRoots.uri, null, null, null, null)!!.use { cursor ->
            val titles = cursor.collect(DocumentsContract.Root.COLUMN_TITLE)
            assertTrue("roots were $titles", titles.contains("test-box"))
        }
    }

    @Test
    fun aDisconnectedHostIsNotListed() {
        ConnectionManager.get(context).disconnect(host.id)

        resolver.query(SafRoots.uri, null, null, null, null)!!.use { cursor ->
            assertFalse(cursor.collect(DocumentsContract.Root.COLUMN_TITLE).contains("test-box"))
        }
    }

    @Test
    fun listingTheRootReturnsItsChildren() {
        childrenOf(rootId).use { cursor ->
            val names = cursor.collect(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            assertEquals(setOf("sub", "hello.txt"), names.toSet())
        }
    }

    @Test
    fun aDocumentCarriesItsSizeAndMimeType() {
        val uri = DocumentsContract.buildDocumentUri(
            SafRoots.AUTHORITY,
            DocumentId(host.id, "/hello.txt").toString(),
        )

        resolver.query(uri, null, null, null, null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                11L,
                cursor.getLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)),
            )
            assertEquals("text/plain", cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)))
        }
        assertEquals("text/plain", resolver.getType(uri))
    }

    @Test
    fun openingADocumentStreamsItsContents() {
        val uri = DocumentsContract.buildDocumentUri(
            SafRoots.AUTHORITY,
            DocumentId(host.id, "/hello.txt").toString(),
        )

        val read = resolver.openInputStream(uri)!!.use { String(it.readBytes()) }

        assertEquals("hello there", read)
    }

    @Test
    fun writingThroughTheDescriptorReachesTheRemote() {
        val uri = DocumentsContract.buildDocumentUri(
            SafRoots.AUTHORITY,
            DocumentId(host.id, "/hello.txt").toString(),
        )

        resolver.openOutputStream(uri, "wt")!!.use { it.write("rewritten".toByteArray()) }

        assertEquals("rewritten", remote.contentsOf("/hello.txt"))
    }

    @Test
    fun createRenameAndDeleteRoundTrip() {
        val created = DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUri(SafRoots.AUTHORITY, rootId),
            "text/plain",
            "notes.txt",
        )!!
        assertTrue(remote.exists("/notes.txt"))

        val renamed = DocumentsContract.renameDocument(resolver, created, "kept.txt")!!
        assertTrue(remote.exists("/kept.txt"))
        assertFalse(remote.exists("/notes.txt"))

        assertTrue(DocumentsContract.deleteDocument(resolver, renamed))
        assertFalse(remote.exists("/kept.txt"))
    }

    @Test
    fun creatingAnExistingNameDoesNotOverwriteIt() {
        DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUri(SafRoots.AUTHORITY, rootId),
            "text/plain",
            "hello.txt",
        )!!

        assertEquals("hello there", remote.contentsOf("/hello.txt"))
        assertTrue(remote.exists("/hello (1).txt"))
    }

    @Test
    fun aMissingDocumentIsNotFoundRatherThanACrash() {
        val uri = DocumentsContract.buildDocumentUri(
            SafRoots.AUTHORITY,
            DocumentId(host.id, "/nope.txt").toString(),
        )

        assertNull(resolver.query(uri, null, null, null, null))
        assertThrows(FileNotFoundException::class.java) { resolver.openInputStream(uri) }
    }

    @Test
    fun aDocumentIdForADisconnectedHostIsNotFound() {
        val uri = DocumentsContract.buildDocumentUri(
            SafRoots.AUTHORITY,
            DocumentId(9999, "/hello.txt").toString(),
        )

        assertNull(resolver.query(uri, null, null, null, null))
    }

    private fun childrenOf(documentId: String): Cursor = resolver.query(
        DocumentsContract.buildChildDocumentsUri(SafRoots.AUTHORITY, documentId),
        null,
        null,
        null,
        null,
    )!!

    /** Every value of [column] in this cursor, from the top. */
    private fun Cursor.collect(column: String): List<String> = buildList {
        val index = getColumnIndexOrThrow(column)
        moveToPosition(-1)
        while (moveToNext()) add(getString(index))
    }
}
