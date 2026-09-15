package android.net

import android.os.Parcel

/**
 * Minimal [Uri] for JVM unit tests: the import slice's tests use it purely as
 * the identity token for the picked backup source — the gateway fake asserts
 * it receives exactly this Uri (the `readFrom` assertion) and nothing ever
 * dereferences it — so every content accessor throws instead of fabricating
 * values, and only the identity semantics the tests need are implemented.
 * (The unit-test stub jar declares all of [Uri]'s accessors abstract, so each
 * one is overridden — with a failure, not an invented value.) Declared in the
 * `android.net` package because the [Uri] constructor is package-private, and
 * `Uri.parse` / `Uri.fromFile` are "not mocked" against the stub jar.
 */
object TestUri : Uri() {
    // Content accessors: never called by these tests, and failing loudly
    // beats fabricated values that would let a future test silently
    // dereference the Uri.
    override fun getScheme(): String = unused()
    override fun getEncodedSchemeSpecificPart(): String = unused()
    override fun getSchemeSpecificPart(): String = unused()
    override fun getAuthority(): String = unused()
    override fun getEncodedAuthority(): String = unused()
    override fun getEncodedUserInfo(): String = unused()
    override fun getUserInfo(): String = unused()
    override fun getHost(): String = unused()
    override fun getPort(): Int = unused()
    override fun getEncodedPath(): String = unused()
    override fun getPath(): String = unused()
    override fun getEncodedQuery(): String = unused()
    override fun getQuery(): String = unused()
    override fun getEncodedFragment(): String = unused()
    override fun getFragment(): String = unused()
    override fun getPathSegments(): List<String> = unused()
    override fun getLastPathSegment(): String = unused()
    override fun isHierarchical(): Boolean = unused()
    override fun isRelative(): Boolean = unused()

    // Identity semantics: StateFlow.setValue compares the old and new value
    // with equals, and the stub jar's Uri.equals is 'not mocked'.
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
    override fun toString(): String = "content://test/backup.json"

    // Parcelable / builder plumbing the type requires; unused in JVM tests.
    override fun buildUpon(): Uri.Builder = unused()
    override fun writeToParcel(out: Parcel, flags: Int) = unused()
    override fun describeContents(): Int = 0
}

private fun unused(): Nothing =
    throw UnsupportedOperationException("TestUri is an identity token; its content is deliberately not mocked")
