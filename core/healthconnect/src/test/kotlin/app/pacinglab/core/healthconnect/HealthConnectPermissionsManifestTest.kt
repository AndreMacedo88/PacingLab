package app.pacinglab.core.healthconnect

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.isEmpty
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class HealthConnectPermissionsManifestTest {
    private val declared: Set<String> by lazy {
        val document = DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val nodes = document.getElementsByTagName("uses-permission")
        (0 until nodes.length).mapTo(mutableSetOf()) {
            nodes
                .item(it)
                .attributes
                .getNamedItemNS(ANDROID_NS, "name")
                .nodeValue
        }
    }

    @Test
    fun `every requested permission is declared in the manifest`() {
        assertThat(declared).containsAtLeast(*HealthConnectPermissions.all.toTypedArray())
    }

    @Test
    fun `the manifest declares nothing the app does not request`() {
        val requestedOnAnySdk = HealthConnectPermissions.all + HealthConnectPermissions.READ_EXERCISE_ROUTES

        assertThat(declared - requestedOnAnySdk).isEmpty()
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
