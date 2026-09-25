package dev.stade.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun asset(name: String, size: Long = 1024, sha: String? = null) =
    UpdateAsset(name, "https://example.invalid/$name", size, sha)

class UpdateCheckTest {

    @Test
    fun aHigherVersionIsOffered() {
        assertTrue(isNewerVersion("0.3.1", "0.3.2"))
        assertTrue(isNewerVersion("0.3.1", "0.4.0"))
        assertTrue(isNewerVersion("0.9.9", "1.0.0"))
        assertTrue(isNewerVersion("0.3.1", "v0.3.2"))
    }

    @Test
    fun theSameOrOlderVersionIsNotOffered() {
        assertFalse(isNewerVersion("0.3.1", "0.3.1"))
        assertFalse(isNewerVersion("0.3.1", "v0.3.1"))
        assertFalse(isNewerVersion("0.3.1", "0.3.0"))
        assertFalse(isNewerVersion("1.0.0", "0.9.9"))
    }

    @Test
    fun numericSegmentsCompareNumericallyNotAlphabetically() {
        assertTrue(isNewerVersion("0.3.9", "0.3.10"), "0.3.10 must beat 0.3.9")
        assertTrue(isNewerVersion("0.9.0", "0.10.0"))
        assertFalse(isNewerVersion("0.3.10", "0.3.9"))
    }

    @Test
    fun aShorterVersionIsOlderThanItsOwnPatchRelease() {
        assertTrue(isNewerVersion("0.3", "0.3.1"))
        assertFalse(isNewerVersion("0.3.1", "0.3"))
        assertEquals(0, compareVersions("0.3.0", "0.3.0"))
    }

    @Test
    fun theRightInstallerIsPickedPerPlatform() {
        val assets = listOf(
            asset("Stade-0.3.2.exe"),
            asset("stade_0.3.2-1_amd64.deb"),
            asset("stade-0.3.2-1.x86_64.rpm"),
            asset("Stade-0.3.2.apk")
        )
        assertEquals("Stade-0.3.2.exe", selectAsset(assets, installerKindFor("Windows 11"))?.name)
        assertEquals("stade_0.3.2-1_amd64.deb", selectAsset(assets, installerKindFor("Linux"))?.name)
        assertEquals("stade-0.3.2-1.x86_64.rpm", selectAsset(assets, InstallerKind.Rpm)?.name)
    }

    @Test
    fun theAndroidPackageIsNeverOfferedToDesktop() {
        val onlyApk = listOf(asset("Stade-0.3.2.apk"))
        for (os in listOf("Windows 11", "Linux", "Mac OS X")) {
            assertNull(selectAsset(onlyApk, installerKindFor(os)), "apk must not be offered on $os")
        }
    }

    @Test
    fun aMissingInstallerForThisPlatformYieldsNothing() {
        val noDmg = listOf(asset("Stade-0.3.2.exe"), asset("stade_0.3.2-1_amd64.deb"))
        assertNull(selectAsset(noDmg, installerKindFor("Mac OS X")))
        assertNull(selectAsset(noDmg, installerKindFor("SomeFutureOS")))
    }

    @Test
    fun anImplausiblySizedAssetIsRejected() {
        assertNull(selectAsset(listOf(asset("Stade.exe", size = 0)), InstallerKind.Exe))
        assertNull(
            selectAsset(listOf(asset("Stade.exe", size = MAX_UPDATE_ASSET_BYTES + 1)), InstallerKind.Exe),
            "an oversized asset must not be downloaded"
        )
    }

    @Test
    fun theDigestIsNormalisedAndValidated() {
        val good = "a".repeat(64)
        assertEquals(good, expectedSha256(asset("x.exe", sha = "sha256:$good")))
        assertEquals(good, expectedSha256(asset("x.exe", sha = good.uppercase())))
        assertNull(expectedSha256(asset("x.exe", sha = "sha256:abc")))
        assertNull(expectedSha256(asset("x.exe", sha = null)))
    }
}
