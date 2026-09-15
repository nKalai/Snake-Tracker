package android.net

import android.os.Parcel

/**
 * Minimal [Uri] for JVM unit tests: the units under test only hand the picked
 * destination to the export gateway and never dereference it, so every
 * accessor is an unused stub. Declared in the `android.net` package because
 * the [Uri] constructor is package-private, and `Uri.parse` / `Uri.fromFile`
 * are "not mocked" against the unit-test stub jar.
 */
object TestUri : Uri() {
    override fun getScheme(): String = "content"
    override fun getEncodedSchemeSpecificPart(): String = "test"
    override fun getSchemeSpecificPart(): String = "test"
    override fun getEncodedAuthority(): String = "test"
    override fun getAuthority(): String = "test"
    override fun getEncodedUserInfo(): String = "test"
    override fun getUserInfo(): String = "test"
    override fun getHost(): String = "test"
    override fun getPort(): Int = -1
    override fun getEncodedPath(): String = "/backup.json"
    override fun getPath(): String = "/backup.json"
    override fun getEncodedQuery(): String? = null
    override fun getQuery(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getFragment(): String? = null
    override fun getPathSegments(): List<String> = listOf("backup.json")
    override fun getLastPathSegment(): String = "backup.json"
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false

    // Identity semantics: StateFlow.setValue compares the old and new value
    // with equals, and the stub jar's Uri.equals is 'not mocked'.
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
    override fun toString(): String = "content://test/backup.json"
    override fun buildUpon(): Uri.Builder = unused()
    override fun writeToParcel(out: Parcel, flags: Int) = unused()
    override fun describeContents(): Int = 0
}

private fun unused(): Nothing = throw UnsupportedOperationException("unused in JVM tests")
