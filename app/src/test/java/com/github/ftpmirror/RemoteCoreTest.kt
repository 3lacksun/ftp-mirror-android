package com.github.ftpmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteCoreTest {

    @Test
    fun protocolDefaultPortsRemainStable() {
        assertEquals(21, RemoteProtocol.FTP.defaultPort)
        assertEquals(21, RemoteProtocol.FTPS_EXPLICIT.defaultPort)
        assertEquals(990, RemoteProtocol.FTPS_IMPLICIT.defaultPort)
        assertEquals(22, RemoteProtocol.SFTP.defaultPort)
        assertEquals(445, RemoteProtocol.SMB.defaultPort)
        assertEquals(443, RemoteProtocol.WEBDAV.defaultPort)
    }

    @Test
    fun remotePathNormalisationRejectsTraversal() {
        assertEquals("/alpha/beta", RemotePaths.normalise("//alpha/./beta/"))
        assertThrows(IllegalArgumentException::class.java) {
            RemotePaths.normalise("/alpha/../escape")
        }
    }

    @Test
    fun sftpRequiresPinnedSha256HostKey() {
        val pair = pair(protocol = RemoteProtocol.SFTP, hostKey = "")
        assertThrows(IllegalArgumentException::class.java) {
            EndpointValidator.validate(pair)
        }
    }

    @Test
    fun sftpAcceptsValidSha256HostKeyShape() {
        val pair = pair(
            protocol = RemoteProtocol.SFTP,
            hostKey = "SHA256:${"A".repeat(43)}"
        )
        EndpointValidator.validate(pair)
    }

    @Test
    fun webDavRejectsEmbeddedCredentials() {
        val pair = pair(
            protocol = RemoteProtocol.WEBDAV,
            host = "https://user:secret@example.com"
        )
        assertThrows(IllegalArgumentException::class.java) {
            EndpointValidator.validate(pair)
        }
    }

    @Test
    fun smbRequiresShareName() {
        val pair = pair(protocol = RemoteProtocol.SMB, remoteDir = "/")
        assertThrows(IllegalArgumentException::class.java) {
            EndpointValidator.validate(pair)
        }
    }

    private fun pair(
        protocol: RemoteProtocol,
        host: String = "example.com",
        remoteDir: String = "/mirror",
        hostKey: String = ""
    ) = SyncPair(
        id = "test",
        name = "Test",
        treeUri = "content://test/tree",
        host = host,
        port = protocol.defaultPort,
        username = "user",
        password = "secret",
        remoteDir = remoteDir,
        passive = true,
        mode = SyncPair.MODE_TWO_WAY,
        enabled = true,
        protocol = protocol.storedValue,
        hostKeySha256 = hostKey
    )
}
