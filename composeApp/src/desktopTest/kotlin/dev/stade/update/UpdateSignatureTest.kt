package dev.stade.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun asset(name: String, size: Long) =
    UpdateAsset(name, "https://example.invalid/$name", size, null)

class UpdateSignatureTest {

    @Test
    fun theSignatureForTheChosenInstallerIsFound() {
        val assets = listOf(
            asset("Stade-0.3.2.exe", 200_000_000),
            asset("Stade-0.3.2.exe.sig", 128),
            asset("stade_0.3.2-1_amd64.deb", 200_000_000),
            asset("stade_0.3.2-1_amd64.deb.sig", 128)
        )
        val exe = selectAsset(assets, InstallerKind.Exe)!!
        assertEquals("Stade-0.3.2.exe.sig", signatureAssetFor(assets, exe)?.name)

        val deb = selectAsset(assets, InstallerKind.Deb)!!
        assertEquals("stade_0.3.2-1_amd64.deb.sig", signatureAssetFor(assets, deb)?.name)
    }

    @Test
    fun anotherPlatformsSignatureIsNeverAccepted() {
        val assets = listOf(
            asset("Stade-0.3.2.exe", 200_000_000),
            asset("stade_0.3.2-1_amd64.deb.sig", 128)
        )
        val exe = selectAsset(assets, InstallerKind.Exe)!!
        assertNull(signatureAssetFor(assets, exe), "a .deb signature must not vouch for the .exe")
    }

    @Test
    fun aMissingSignatureYieldsNothing() {
        val assets = listOf(asset("Stade-0.3.2.exe", 200_000_000))
        val exe = selectAsset(assets, InstallerKind.Exe)!!
        assertNull(signatureAssetFor(assets, exe))
    }

    @Test
    fun anOversizedSignatureIsRejected() {
        val assets = listOf(
            asset("Stade-0.3.2.exe", 200_000_000),
            asset("Stade-0.3.2.exe.sig", MAX_UPDATE_SIGNATURE_BYTES.toLong() + 1)
        )
        val exe = selectAsset(assets, InstallerKind.Exe)!!
        assertNull(signatureAssetFor(assets, exe))
    }

    @Test
    fun aSignatureFileIsNeverMistakenForAnInstaller() {
        val assets = listOf(asset("Stade-0.3.2.exe.sig", 128))
        assertNull(selectAsset(assets, InstallerKind.Exe), "a .sig must never be run as an installer")
    }

    @Test
    fun signatureEnforcementFollowsTheConfiguredKey() {
        assertEquals(
            UPDATE_SIGNING_PUBLIC_KEY.length == 64,
            updateSignatureRequired,
            "enforcement must switch on exactly when a 32-byte key is configured"
        )
        if (UPDATE_SIGNING_PUBLIC_KEY.isEmpty()) {
            assertTrue(!updateSignatureRequired, "no key configured yet means digest-only verification")
        }
    }
}
