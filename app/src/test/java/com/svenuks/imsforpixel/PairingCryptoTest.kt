package com.svenuks.imsforpixel

import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import com.flyfish233.crypto.spake2.Spake2Context
import com.flyfish233.crypto.spake2.Spake2Role
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory

/** Plain JVM crypto tests, without Robolectric's substituted security providers.
 * Real Android pairing and the minified APK remain device checks. */
class PairingCryptoTest {
    @get:Rule val temp = TemporaryFolder()
    @After fun resetTestIdentity() { runCatching { KadbCert.clear() } }

    @Test fun persistedAdbIdentityIsReusedAndItsCertificateVerifies() {
        val key = File(temp.root, "test-identity.pem")
        fun configure() = KadbCert.configure(OkioFilePrivateKeyStore(key.absolutePath.toPath()),
            KadbCertPolicy(), emptyList())
        configure()
        val first = KadbCert.ensureReady()
        assertTrue(key.exists())
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(first.certificatePem))
        certificate.verify(certificate.publicKey)
        // Exercise BC's dynamic JCA registration as well as the host's provider.
        val fallback = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
            .getDeclaredConstructor().newInstance() as java.security.Provider
        val fallbackCertificate = CertificateFactory.getInstance("X.509", fallback)
            .generateCertificate(ByteArrayInputStream(first.certificatePem))
        fallbackCertificate.verify(fallbackCertificate.publicKey, fallback)
        configure()
        val loaded = KadbCert.ensureReady()
        assertTrue("Reload must reuse the stored key", first.privateKeyPem.contentEquals(loaded.privateKeyPem))
        // Kadb can issue a fresh certificate around the saved key when reconfigured.
        // The ADB identity's key must stay stable; certificate serials need not.
        val loadedCertificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(loaded.certificatePem))
        assertArrayEquals(certificate.publicKey.encoded, loadedCertificate.publicKey.encoded)
        loadedCertificate.verify(loadedCertificate.publicKey)
    }

    private fun exchange(alicePassword: String, bobPassword: String): Pair<ByteArray, ByteArray> {
        val client = "test-client".toByteArray()
        val server = "test-server".toByteArray()
        Spake2Context(Spake2Role.Alice, client, server).use { alice ->
            Spake2Context(Spake2Role.Bob, server, client).use { bob ->
                val a = alice.generateMessage(alicePassword.toByteArray())
                val b = bob.generateMessage(bobPassword.toByteArray())
                return alice.processMessage(b) to bob.processMessage(a)
            }
        }
    }
    @Test fun spake2PeersDeriveTheSameKeyWithTheSameCode() {
        val (a, b) = exchange("123456", "123456")
        assertTrue(a.isNotEmpty())
        assertTrue("SPAKE2 keys must agree", a.contentEquals(b))
    }
    @Test fun differentPairingCodesDoNotDeriveTheSameKey() {
        val (a, b) = exchange("123456", "654321")
        assertFalse("Different passwords must not agree", a.contentEquals(b))
    }
}
